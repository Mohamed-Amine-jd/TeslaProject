package com.TeslaProject.TeslaProject.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class GeminiService {

    private static final Pattern RETRY_IN_SECONDS = Pattern.compile("Please retry in ([\\d.]+)s", Pattern.CASE_INSENSITIVE);

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    /**
     * Comma-separated model ids (v1beta). On 404, next model. On 429, backoff then retry same model
     * (helps per-minute limits); then next model if still 429.
     */
    @Value("${gemini.api.models:gemini-2.5-flash,gemini-2.5-flash-lite,gemini-2.0-flash,gemini-2.0-flash-lite,gemini-1.5-flash-8b,gemini-3-flash-preview}")
    private String modelsConfig;

    /** Total HTTP calls per model on 429 before switching model (first try + retries). */
    @Value("${gemini.api.max-429-attempts-per-model:2}")
    private int max429AttemptsPerModel;

    /**
     * Max milliseconds to wait on 429 before retry (Google often suggests 30–60s; long waits block HTTP threads
     * and rarely help daily free-tier caps). Increase if you prefer stricter adherence to Retry-Info.
     */
    @Value("${gemini.api.max-retry-wait-ms:8000}")
    private long maxRetryWaitMs;

    private List<String> modelNames = new ArrayList<>();

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    void initModels() {
        modelNames = Arrays.stream(modelsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (modelNames.isEmpty()) {
            modelNames = List.of("gemini-2.0-flash");
        }
        if (max429AttemptsPerModel < 1) {
            max429AttemptsPerModel = 1;
        }
        if (maxRetryWaitMs < 1_000L) {
            maxRetryWaitMs = 1_000L;
        }
        if (maxRetryWaitMs > 120_000L) {
            maxRetryWaitMs = 120_000L;
        }
    }

    public String analyzeImage(MultipartFile imageFile) throws Exception {
        String base64Image = Base64.getEncoder().encodeToString(imageFile.getBytes());
        String mimeType = imageFile.getContentType() != null ? imageFile.getContentType() : "image/jpeg";

        Exception last = null;
        for (String modelName : modelNames) {
            try {
                Optional<String> result = tryModel(modelName, base64Image, mimeType);
                if (result.isPresent()) {
                    return result.get();
                }
            } catch (HttpClientErrorException e) {
                last = e;
                throw detailedFailure(modelName, e);
            } catch (HttpServerErrorException e) {
                last = e;
                if (e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE || e.getStatusCode() == HttpStatus.BAD_GATEWAY) {
                    System.err.println("=== Gemini model " + modelName + " server error, trying next ===");
                    continue;
                }
                throw detailedFailure(modelName, e);
            }
        }
        String hint = "Quota Gemini epuise sur ce projet (free tier : limites par minute et par jour). "
                + "Options : attendre (souvent 24h pour le plafond journalier), activer la facturation, "
                + "ou une autre cle API / projet : https://ai.google.dev/gemini-api/docs/rate-limits — "
                + "gemini.api.models, gemini.api.max-429-attempts-per-model, gemini.api.max-retry-wait-ms dans application.properties.";
        if (last != null) {
            throw new RuntimeException(hint + " Derniere erreur : " + shortErr(last), last);
        }
        throw new RuntimeException(hint);
    }

    /**
     * Tries one model: on 429 waits using Retry-Info / message then retries up to max429AttemptsPerModel.
     * Returns empty to try the next configured model.
     */
    private Optional<String> tryModel(String modelName, String base64Image, String mimeType) throws HttpClientErrorException, HttpServerErrorException {
        for (int attempt = 1; attempt <= max429AttemptsPerModel; attempt++) {
            try {
                System.out.println("=== Gemini OCR model=" + modelName + " attempt=" + attempt + "/" + max429AttemptsPerModel + " ===");
                String text = callGenerateContent(modelName, base64Image, mimeType);
                System.out.println("=== Gemini OCR success: " + modelName + " ===");
                return Optional.of(text);
            } catch (HttpClientErrorException e) {
                HttpStatus s = HttpStatus.resolve(e.getStatusCode().value());
                if (s == HttpStatus.NOT_FOUND) {
                    System.err.println("=== Gemini model " + modelName + " NOT_FOUND, next model ===");
                    return Optional.empty();
                }
                if (s == HttpStatus.TOO_MANY_REQUESTS) {
                    if (attempt < max429AttemptsPerModel) {
                        long suggestedMs = readSuggestedRetryDelayMs(e);
                        long waitMs = capRetryWait(suggestedMs);
                        if (waitMs < suggestedMs) {
                            System.out.println("=== Gemini 429: suggested wait " + suggestedMs + "ms capped to " + waitMs + "ms (gemini.api.max-retry-wait-ms=" + maxRetryWaitMs + ") ===");
                        }
                        System.err.println("=== Gemini 429 on " + modelName + " — waiting " + waitMs + "ms then retry " + (attempt + 1) + "/" + max429AttemptsPerModel + " ===");
                        sleepQuietly(waitMs);
                        continue;
                    }
                    System.err.println("=== Gemini 429 on " + modelName + " — attempts exhausted, next model ===");
                    return Optional.empty();
                }
                throw e;
            }
        }
        return Optional.empty();
    }

    private static void sleepQuietly(long waitMs) {
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during Gemini 429 backoff", ie);
        }
    }

    /**
     * Parses Google retry hint from JSON / "Please retry in Xs" (uncapped milliseconds).
     */
    private long readSuggestedRetryDelayMs(HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        if (body != null && !body.isBlank()) {
            Matcher m = RETRY_IN_SECONDS.matcher(body);
            if (m.find()) {
                return (long) (Double.parseDouble(m.group(1)) * 1000.0) + 300L;
            }
            try {
                JsonNode root = objectMapper.readTree(body);
                JsonNode details = root.path("error").path("details");
                if (details.isArray()) {
                    for (JsonNode d : details) {
                        String type = d.path("@type").asText("");
                        if (type.contains("RetryInfo")) {
                            String rd = d.path("retryDelay").asText("");
                            long fromProto = parseProtobufDurationToMs(rd);
                            if (fromProto > 0) {
                                return fromProto + 300L;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // fall through to default
            }
        }
        return 2_000L;
    }

    /** Clamp wait to [1s, gemini.api.max-retry-wait-ms] for responsiveness. */
    private long capRetryWait(long suggestedMs) {
        return Math.min(maxRetryWaitMs, Math.max(1_000L, suggestedMs));
    }

    /** Accepts e.g. "3s", "24.27s" from google.rpc.RetryInfo string form. */
    private static long parseProtobufDurationToMs(String retryDelay) {
        if (retryDelay == null || retryDelay.isBlank()) {
            return 0;
        }
        retryDelay = retryDelay.trim();
        if (retryDelay.endsWith("s")) {
            String num = retryDelay.substring(0, retryDelay.length() - 1).trim();
            return (long) (Double.parseDouble(num) * 1000.0);
        }
        return 0;
    }

    private static String shortErr(Exception e) {
        String m = e.getMessage();
        if (m == null) {
            return e.getClass().getSimpleName();
        }
        return m.length() > 600 ? m.substring(0, 600) + "…" : m;
    }

    private RuntimeException detailedFailure(String modelName, Exception e) {
        return new RuntimeException("L'analyse a echoue (modele " + modelName + "). " + shortErr(e), e);
    }

    private String callGenerateContent(String modelName, String base64Image, String mimeType) {
        String fullUrl = apiUrl + modelName + ":generateContent?key=" + apiKey;

        Map<String, Object> requestBody = new HashMap<>();
        String prompt = "Analyse cette carte grise tunisienne. Extrait l'immatriculation (ex: 190tu765) et le numéro de châssis. "
                + "Réponds exactement comme ceci :\n"
                + "immatriculation: [valeur]\n"
                + "châssis: [valeur]";

        requestBody.put("contents", List.of(Map.of(
                "parts", List.of(
                        Map.of("text", prompt),
                        Map.of("inline_data", Map.of("mime_type", mimeType, "data", base64Image))
                )
        )));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(fullUrl, entity, String.class);

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            return text.asText("");
        } catch (Exception parseEx) {
            throw new RuntimeException("Réponse Gemini illisible pour " + modelName, parseEx);
        }
    }
}
