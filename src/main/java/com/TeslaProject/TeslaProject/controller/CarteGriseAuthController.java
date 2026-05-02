package com.TeslaProject.TeslaProject.controller;

import com.TeslaProject.TeslaProject.Services.GeminiService;
import com.TeslaProject.TeslaProject.Services.OTPService;
import com.TeslaProject.TeslaProject.models.Client;
import com.TeslaProject.TeslaProject.repository.ClientRepository;
import com.TeslaProject.TeslaProject.util.MatriculeVariants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/public")
@CrossOrigin(origins = "http://localhost:4200")
public class CarteGriseAuthController {

    private final GeminiService geminiService;
    private final OTPService otpService;
    private final ClientRepository clientRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${keycloak.auth-server-url}")
    private String keycloakUrl;

    @Value("${keycloak.realm}")
    private String realm;

    public CarteGriseAuthController(GeminiService geminiService,
                                     OTPService otpService,
                                     ClientRepository clientRepository) {
        this.geminiService = geminiService;
        this.otpService = otpService;
        this.clientRepository = clientRepository;
    }

    // ============================================================
    // ENDPOINTS
    // ============================================================

    @PostMapping("/login-carte-grise")
    public ResponseEntity<?> loginWithCarteGrise(
            @RequestParam("image") MultipartFile file) {
        try {
            System.out.println("=== Analyse carte grise ===");
            String geminiResult = geminiService.analyzeImage(file);
            System.out.println("Résultat Gemini brut : " + geminiResult);

            String matriculeBrut = extractValue(geminiResult, "immatriculation");
            String chassis       = extractValue(geminiResult, "châssis").trim();
            String matricule     = normaliserMatricule(matriculeBrut);

            System.out.println("Matricule brut  : " + matriculeBrut);
            System.out.println("Châssis         : " + chassis);
            System.out.println("Matricule final : " + matricule);

            if (matricule.equals("nonlisible") || chassis.equalsIgnoreCase("Non lisible")) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Lecture impossible."
                ));
            }

            System.out.println("Tentative Login Keycloak pour : " + matricule);
            String token = getKeycloakToken(matricule, chassis);
            System.out.println("✅ Token Keycloak récupéré");

            try {
                Optional<Client> clientOpt = clientRepository
                    .findFirstByMatriculeIn(MatriculeVariants.aliases(matricule));
                if (clientOpt.isPresent()) {
                    Client client = clientOpt.get();
                    return ResponseEntity.ok(Map.of(
                        "success",    true,
                        "token",      token,
                        "matricule",  client.getMatricule(),
                        "chassis",    chassis,
                        "email",      maskEmail(client.getEmail()),
                        "phone",      maskPhone(client.getPhoneNumber()),
                        "requireOTP", true
                    ));
                }
            } catch (Exception mongoEx) {
                System.err.println("⚠️ Erreur MongoDB : " + mongoEx.getMessage());
                return ResponseEntity.ok(Map.of(
                    "success",  true,
                    "token",    token,
                    "matricule", matricule,
                    "chassis",  chassis,
                    "warning",  "Profil non trouvé dans MongoDB"
                ));
            }

            return ResponseEntity.ok(Map.of(
                "success",   true,
                "token",     token,
                "matricule", matricule,
                "chassis",   chassis
            ));

        } catch (HttpClientErrorException.Unauthorized e) {
            return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "error", "Véhicule non reconnu ou châssis incorrect."
            ));
        } catch (Exception e) {
            System.err.println("=== ERREUR GÉNÉRALE : " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/client-contact")
    public ResponseEntity<?> getClientContact(
            @RequestParam("matricule") String matriculeRaw) {
        if (matriculeRaw == null || matriculeRaw.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, "error", "Matricule requis"
            ));
        }
        try {
            String matricule = normaliserMatricule(matriculeRaw.trim());
            if ("nonlisible".equals(matricule)) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "error", "Matricule invalide"
                ));
            }
            Optional<Client> clientOpt = clientRepository
                .findFirstByMatriculeIn(MatriculeVariants.aliases(matricule));
            if (clientOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "Profil non trouvé pour cette immatriculation."
                ));
            }
            Client client = clientOpt.get();
            return ResponseEntity.ok(Map.of(
                "success",   true,
                "matricule", client.getMatricule(),
                "email",     maskEmail(client.getEmail()),
                "phone",     maskPhone(client.getPhoneNumber())
            ));
        } catch (Exception e) {
            System.err.println("Erreur client-contact : " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false, "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> body) {
        String matriculeRaw = body.get("matricule");
        String matricule = matriculeRaw != null
            ? normaliserMatricule(matriculeRaw.trim()) : "nonlisible";
        if ("nonlisible".equals(matricule)) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, "error", "Matricule invalide"
            ));
        }
        String method = body.getOrDefault("method", "email");
        try {
            Optional<Client> clientOpt = clientRepository
                .findFirstByMatriculeIn(MatriculeVariants.aliases(matricule));
            if (clientOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "error", "Profil non trouvé"
                ));
            }
            Client client = clientOpt.get();
            String otp = otpService.generateAndSaveOTP(client.getMatricule());
            if ("sms".equalsIgnoreCase(method)) {
                otpService.sendOTPSMS(client.getPhoneNumber(), otp);
            } else {
                otpService.sendOTPEmail(client.getEmail(), otp);
            }
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            System.err.println("Erreur send-otp : " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false, "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> body) {
        String matriculeRaw = body.get("matricule");
        String code    = body.get("code");
        String chassis = body.get("chassis");
        try {
            String matricule = matriculeRaw != null
                ? normaliserMatricule(matriculeRaw.trim()) : "nonlisible";
            if ("nonlisible".equals(matricule)) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "error", "Matricule invalide"
                ));
            }
            if (code == null || code.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "error", "Code requis"
                ));
            }
            if (!otpService.otpCodeValid(matricule, code.trim())) {
                return ResponseEntity.status(400).body(Map.of(
                    "success", false, "error", "Code invalide ou expiré"
                ));
            }

            Map<String, Object> out = new HashMap<>();
            out.put("success", true);

            if (chassis != null && !chassis.isBlank()) {
                try {
                    String token = getKeycloakToken(matricule, chassis.trim());
                    out.put("token", token);
                } catch (HttpClientErrorException e) {
                    if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                        return ResponseEntity.status(401).body(Map.of(
                            "success", false, "error", "Châssis incorrect"
                        ));
                    }
                    throw e;
                }
            }

            otpService.clearOtpForMatricule(matricule);
            return ResponseEntity.ok(out);

        } catch (Exception e) {
            System.err.println("Erreur verify-otp : " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false, "error", e.getMessage()
            ));
        }
    }

    // ============================================================
    // MÉTHODES PRIVÉES
    // ============================================================

    private String normaliserMatricule(String matriculeBrut) {
        if (matriculeBrut == null || matriculeBrut.isBlank()) return "nonlisible";

        String result = matriculeBrut
            .toLowerCase()
            .trim()
            // ✅ Arabe
            .replace("تونس",     "tu")
            // ✅ Français — tunisie AVANT tunis !
            .replace("tunisie",  "tu")
            .replace("tunis",    "tu")
            .replace("tun",   "tu")
            // ✅ Supprimer espaces, tirets, underscores
            .replaceAll("[\\s\\-_]+", "")
            .trim();

        return reordonnerMatricule(result);
    }

    private String reordonnerMatricule(String matricule) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern
            .compile("^(\\d+)([a-z]+)(\\d+)$");
        java.util.regex.Matcher matcher = pattern.matcher(matricule);
        if (matcher.matches()) {
            int n1 = Integer.parseInt(matcher.group(1));
            int n2 = Integer.parseInt(matcher.group(3));
            if (n1 > n2) {
                String corrected = matcher.group(3) + matcher.group(2) + matcher.group(1);
                System.out.println("Matricule réordonné : " + matricule + " → " + corrected);
                return corrected;
            }
        }
        return matricule;
    }

    private String getKeycloakToken(String username, String password) {
        System.out.println("=== LOGIN KEYCLOAK ===");
        System.out.println("  Username : '" + username + "'");
        System.out.println("  Password : '" + password + "'");

        String url = keycloakUrl + "/realms/" + realm
                   + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id",  "angular-app");
        body.add("username",   username);
        body.add("password",   password);

        HttpEntity<MultiValueMap<String, String>> request =
                new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url, request, Map.class);
            System.out.println("=== LOGIN SUCCÈS ===");
            return (String) response.getBody().get("access_token");
        } catch (HttpClientErrorException e) {
            System.err.println("=== LOGIN ÉCHEC : " + e.getStatusCode() + " ===");
            System.err.println("  Body : " + e.getResponseBodyAsString());
            throw e;
        }
    }

    private String extractValue(String text, String key) {
        for (String line : text.split("\n")) {
            if (line.toLowerCase().contains(key.toLowerCase())) {
                String[] parts = line.split(":");
                if (parts.length > 1) {
                    return parts[parts.length - 1].trim();
                }
            }
        }
        return "Non lisible";
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) return "";
        return email.replaceAll("(^.{3})(.*)(@.*)", "$1***$3");
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) return "";
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 2);
    }
}