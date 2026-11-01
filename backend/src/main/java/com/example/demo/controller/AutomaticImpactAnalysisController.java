package com.example.demo.controller;

import com.example.demo.service.AutomaticImpactAnalysisService;

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
    public Map<String, Object> analyzeAutomaticImpact(
            @RequestBody AutomaticImpactRequest request) {

        return automaticImpactAnalysisService.analyze(
                request.changedClass(),
                request.sourceFiles()
        );
    }

    public record AutomaticImpactRequest(
            String changedClass,
            Map<String, String> sourceFiles
    ) {}
}