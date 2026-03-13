package com.TeslaProject.TeslaProject.controller;

import com.TeslaProject.TeslaProject.models.ImageAnalysisResult;
import com.TeslaProject.TeslaProject.repository.ImageAnalysisRepository;
import com.TeslaProject.TeslaProject.Services.GeminiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gemini")
@CrossOrigin(origins = "http://localhost:4200")
public class GeminiController {

    private final GeminiService geminiService;
    private final ImageAnalysisRepository repository;

    @Autowired
    public GeminiController(GeminiService geminiService, ImageAnalysisRepository repository) {
        this.geminiService = geminiService;
        this.repository = repository;
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeImage(@RequestParam("image") MultipartFile file) {

        // Vérification fichier reçu
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Aucun fichier reçu"
            ));
        }

        System.out.println("=== Fichier reçu: " + file.getOriginalFilename()
                + " | Type: " + file.getContentType()
                + " | Taille: " + file.getSize() + " bytes ===");

        try {
            String result = geminiService.analyzeImage(file);
            System.out.println("=== Résultat Gemini obtenu avec succès ===");

            ImageAnalysisResult analysis = new ImageAnalysisResult();
            analysis.setImageName(file.getOriginalFilename());
            analysis.setImageType(file.getContentType());
            analysis.setImageSize(file.getSize());
            analysis.setAnalysisResult(result);
            analysis.setAnalyzedAt(LocalDateTime.now());
            repository.save(analysis);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("result", result);
            response.put("id", analysis.getId());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("=== ERREUR: " + e.getMessage() + " ===");
            e.printStackTrace();

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<ImageAnalysisResult>> getHistory() {
        return ResponseEntity.ok(repository.findAll());
    }
}