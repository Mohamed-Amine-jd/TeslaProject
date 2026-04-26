package com.TeslaProject.TeslaProject.controller;

import com.TeslaProject.TeslaProject.Services.GeminiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/public")
@CrossOrigin(origins = "http://localhost:4200")
public class CarteGriseAuthController {

    private final GeminiService geminiService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${keycloak.auth-server-url}")
    private String keycloakUrl;

    @Value("${keycloak.realm}")
    private String realm;

    public CarteGriseAuthController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping("/login-carte-grise")
    public ResponseEntity<?> loginWithCarteGrise(
            @RequestParam("image") MultipartFile file) {
        try {
            // ① Analyser avec Gemini
            System.out.println("=== Analyse carte grise ===");
            String geminiResult = geminiService.analyzeImage(file);
            System.out.println("Résultat Gemini brut : " + geminiResult);

            // ② Extraire matricule et châssis
            String matriculeBrut = extractValue(geminiResult, "immatriculation");
            String chassis       = extractValue(geminiResult, "châssis").trim();

            System.out.println("Matricule brut  : " + matriculeBrut);
            System.out.println("Châssis         : " + chassis);

            // ③ Normaliser le matricule
            String matricule = normaliserMatricule(matriculeBrut);
            System.out.println("Matricule final : " + matricule);

            if (matricule.equals("nonlisible") || chassis.equalsIgnoreCase("Non lisible")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Impossible de lire la carte grise. Réessayez avec une meilleure photo."
                ));
            }

            // ④ Login Keycloak
            String token = getKeycloakToken(matricule, chassis);

            return ResponseEntity.ok(Map.of(
                    "success",   true,
                    "matricule", matricule,
                    "chassis",   chassis,
                    "token",     token
            ));

        } catch (HttpClientErrorException.Unauthorized e) {
            System.err.println("=== 401 Keycloak : user non trouvé ===");
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "error", "Véhicule non reconnu. Votre carte grise n'est pas enregistrée dans le système."
            ));
        } catch (Exception e) {
            System.err.println("=== ERREUR : " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    private String normaliserMatricule(String matriculeBrut) {
        if (matriculeBrut == null || matriculeBrut.isBlank()) return "nonlisible";

        String result = matriculeBrut
                .toLowerCase()
                .trim()
                // ✅ Gouvernorats en arabe → abréviation
                .replace("تونس",    "tu")

                // ✅ Gouvernorats en français → abréviation
                .replace("tunis",      "tu")

                // ✅ Supprimer espaces, tirets, underscores
                .replaceAll("[\\s\\-_]+", "")
                .trim();

        // ✅ Réordonner si format inversé : "765tu190" → "190tu765"
        // Format tunisien : NUMERO + GOUVERNORAT + NUMERO
        // Gemini retourne parfois : 765 تونس 190 (inversé)
        result = reordonnerMatricule(result);

        System.out.println("Matricule après normalisation : " + result);
        return result;
    }

    // ✅ Réordonne le matricule si Gemini l'a retourné dans l'ordre arabe (droite à gauche)
// "765tu190" → "190tu765"
    private String reordonnerMatricule(String matricule) {
        // Cherche le pattern : chiffres + lettres + chiffres
        java.util.regex.Pattern pattern = java.util.regex.Pattern
                .compile("^(\\d+)([a-z]+)(\\d+)$");
        java.util.regex.Matcher matcher = pattern.matcher(matricule);

        if (matcher.matches()) {
            String num1 = matcher.group(1); // 765
            String gov  = matcher.group(2); // tu
            String num2 = matcher.group(3); // 190

            // Format standard tunisien : le plus petit numéro vient en premier
            // OU on détecte l'ordre arabe (RTL) en inversant
            int n1 = Integer.parseInt(num1);
            int n2 = Integer.parseInt(num2);

            // Si num1 > num2, c'est probablement inversé (ordre arabe RTL)
            if (n1 > n2) {
                String corrected = num2 + gov + num1;
                System.out.println("Matricule réordonné : " + matricule + " → " + corrected);
                return corrected;
            }
        }
        return matricule;
    }

    private String getKeycloakToken(String username, String password) {
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

        ResponseEntity<Map> response = restTemplate.postForEntity(
                url, request, Map.class);

        return (String) response.getBody().get("access_token");
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
}