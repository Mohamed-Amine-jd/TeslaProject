package com.TeslaProject.TeslaProject.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Liste de modèles à essayer dans l'ordre
    private final List<String> modelsToTry = List.of(
            "gemini-2.0-flash-lite",
            "gemini-2.0-flash",
            "gemini-2.5-flash",
            "gemini-2.5-pro"
    );

    public String analyzeImage(MultipartFile imageFile) throws Exception {
        String base64Image = Base64.getEncoder().encodeToString(imageFile.getBytes());
        String mimeType = imageFile.getContentType() != null
                ? imageFile.getContentType() : "image/jpeg";

        // Essayer chaque modèle jusqu'à ce qu'un fonctionne
        Exception lastException = null;
        for (String model : modelsToTry) {
            try {
                System.out.println("=== Essai avec le modèle: " + model + " ===");
                String result = callGeminiWithModel(model, base64Image, mimeType);
                System.out.println("=== Succès avec le modèle: " + model + " ===");
                return result;
            } catch (HttpClientErrorException.TooManyRequests e) {
                System.out.println("=== Modèle " + model + " quota épuisé, essai suivant... ===");
                lastException = e;
            } catch (HttpClientErrorException.NotFound e) {
                System.out.println("=== Modèle " + model + " non trouvé, essai suivant... ===");
                lastException = e;
            }
        }

        throw new RuntimeException("Tous les modèles ont échoué. Activez la facturation sur Google Cloud Console : https://console.cloud.google.com/billing", lastException);
    }

    private String callGeminiWithModel(String model, String base64Image, String mimeType) throws Exception {
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", """
    Tu es un expert en lecture de cartes grises de véhicules tunisiens.
    Analyse cette image de carte grise et extrait UNIQUEMENT ces deux informations :
    
    - Numéro d'immatriculation
    - Numéro de châssis (VIN)
    
    Réponds UNIQUEMENT dans ce format exact, rien d'autre :
    Numéro d'immatriculation : [valeur]
    Numéro de châssis (VIN) : [valeur]
    
    Si une information n'est pas visible ou lisible, écris : Non lisible
    Ne donne aucune explication supplémentaire.
""");
        Map<String, Object> inlineData = new HashMap<>();
        inlineData.put("mime_type", mimeType);
        inlineData.put("data", base64Image);

        Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("inline_data", inlineData);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(textPart, imagePart));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(content));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/";
        String fullUrl = baseUrl + model + ":generateContent?key=" + apiKey;

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(fullUrl, entity, String.class);

        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode candidates = root.path("candidates");

        if (candidates.isEmpty()) {
            throw new RuntimeException("Aucun résultat. Réponse: " + response.getBody());
        }

        return candidates.get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText("Analyse non disponible");
    }
}