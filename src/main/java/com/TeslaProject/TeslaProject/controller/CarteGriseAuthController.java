package com.TeslaProject.TeslaProject.controller;

import com.TeslaProject.TeslaProject.Services.GeminiService;
import com.TeslaProject.TeslaProject.Services.OTPService;
import com.TeslaProject.TeslaProject.models.Client;
import com.TeslaProject.TeslaProject.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

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

    public CarteGriseAuthController(GeminiService geminiService, OTPService otpService, ClientRepository clientRepository) {
        this.geminiService = geminiService;
        this.otpService = otpService;
        this.clientRepository = clientRepository;
    }

    @PostMapping("/login-carte-grise")
    public ResponseEntity<?> loginWithCarteGrise(@RequestParam("image") MultipartFile file) {
        try {
            // ① ANALYSE OCR (Ton code original)
            System.out.println("=== Analyse carte grise ===");
            String geminiResult = geminiService.analyzeImage(file);
            System.out.println("Résultat Gemini brut : " + geminiResult);

            String matriculeBrut = extractValue(geminiResult, "immatriculation");
            String chassis       = extractValue(geminiResult, "châssis").trim();
            String matricule     = normaliserMatricule(matriculeBrut);

            if (matricule.equals("nonlisible") || chassis.equalsIgnoreCase("Non lisible")) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Lecture impossible."));
            }

            // ② LOGIN KEYCLOAK (Ton code original qui marche)
            System.out.println("Tentative Login Keycloak pour : " + matricule);
            String token = getKeycloakToken(matricule, chassis);
            System.out.println("✅ Token Keycloak récupéré");

            // ③ RECHERCHE MONGODB (Ajout sécurisé)
            // On utilise un try/catch ici pour que si MongoDB est en panne, le login ne crash pas totalement
            try {
                Optional<Client> clientOpt = clientRepository.findByMatricule(matricule);
                if (clientOpt.isPresent()) {
                    Client client = clientOpt.get();
                    return ResponseEntity.ok(Map.of(
                            "success", true,
                            "token", token,
                            "matricule", matricule,
                            "email", maskEmail(client.getEmail()),
                            "phone", maskPhone(client.getPhoneNumber()),
                            "requireOTP", true
                    ));
                }
            } catch (Exception mongoEx) {
                System.err.println("⚠️ Erreur MongoDB : " + mongoEx.getMessage());
                // Si MongoDB crash, on renvoie quand même le token mais avec un warning
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "token", token,
                        "warning", "Profil non trouvé dans MongoDB (Vérifiez votre connexion Atlas)"
                ));
            }

            return ResponseEntity.ok(Map.of("success", true, "token", token, "matricule", matricule));

        } catch (HttpClientErrorException.Unauthorized e) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Véhicule non reconnu ou châssis incorrect."));
        } catch (Exception e) {
            System.err.println("=== ERREUR GÉNÉRALE : " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> body) {
        String matricule = body.get("matricule");
        String method = body.getOrDefault("method", "email");
        try {
            Optional<Client> clientOpt = clientRepository.findByMatricule(matricule);
            if (clientOpt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Profil non trouvé"));
            Client client = clientOpt.get();
            String otp = otpService.generateAndSaveOTP(matricule);
            if ("sms".equalsIgnoreCase(method)) otpService.sendOTPSMS(client.getPhoneNumber(), otp);
            else otpService.sendOTPEmail(client.getEmail(), otp);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            System.err.println("Erreur send-otp : " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> body) {
        String matricule = body.get("matricule");
        String code = body.get("code");
        try {
            boolean ok = otpService.verifyOTP(matricule, code);
            if (ok) return ResponseEntity.ok(Map.of("success", true));
            return ResponseEntity.status(400).body(Map.of("success", false, "error", "Code invalide ou expiré"));
        } catch (Exception e) {
            System.err.println("Erreur verify-otp : " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // --- Garde tes méthodes privées exactement comme avant ---
    private String normaliserMatricule(String matriculeBrut) {
        if (matriculeBrut == null || matriculeBrut.isBlank()) return "nonlisible";
        String result = matriculeBrut.toLowerCase().trim().replace("تونس", "tu").replace("tunis", "tu").replaceAll("[\\s\\-_]+", "");
        return reordonnerMatricule(result);
    }

    private String reordonnerMatricule(String matricule) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(\\d+)([a-z]+)(\\d+)$");
        java.util.regex.Matcher matcher = pattern.matcher(matricule);
        if (matcher.matches()) {
            int n1 = Integer.parseInt(matcher.group(1));
            int n2 = Integer.parseInt(matcher.group(3));
            if (n1 > n2) return matcher.group(3) + matcher.group(2) + matcher.group(1);
        }
        return matricule;
    }

    private String getKeycloakToken(String username, String password) {
        String url = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", "angular-app");
        body.add("username", username);
        body.add("password", password);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
        return (String) response.getBody().get("access_token");
    }

    private String extractValue(String text, String key) {
        for (String line : text.split("\n")) {
            if (line.toLowerCase().contains(key.toLowerCase())) {
                String[] parts = line.split(":");
                if (parts.length > 1) return parts[parts.length - 1].trim();
            }
        }
        return "Non lisible";
    }

    private String maskEmail(String email) { return email.replaceAll("(^.{3})(.*)(@.*)", "$1***$3"); }
    private String maskPhone(String phone) { return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 2); }
}