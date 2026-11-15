package com.example.demo.controller;

import com.example.demo.service.AutomaticImpactAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/analyze")
public class AutomaticImpactAnalysisController {

    private final AutomaticImpactAnalysisService automaticImpactAnalysisService;

    public AutomaticImpactAnalysisController(
            AutomaticImpactAnalysisService automaticImpactAnalysisService) {

        this.automaticImpactAnalysisService = automaticImpactAnalysisService;
    }

    @PostMapping("/automatic-impact")
    public ResponseEntity<Map<String, Object>> analyzeAutomaticImpact(
            @RequestBody AutomaticImpactRequest request) {

        try {
            Map<String, Object> result =
                    automaticImpactAnalysisService.analyze(
                            request.changedClass(),
                            request.sourceFiles()
                    );

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException exception) {

            return ResponseEntity.badRequest().body(
                    Map.of("error", exception.getMessage())
            );
        }
    }

    public record AutomaticImpactRequest(
            String changedClass,
            Map<String, String> sourceFiles
    ) {}
}
