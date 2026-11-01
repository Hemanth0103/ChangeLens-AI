package com.example.demo.controller;

import com.example.demo.service.DependencyAnalysisService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/analyze")
public class DependencyAnalysisController {

    private final DependencyAnalysisService dependencyAnalysisService;

    public DependencyAnalysisController(
            DependencyAnalysisService dependencyAnalysisService) {
        this.dependencyAnalysisService = dependencyAnalysisService;
    }

    @PostMapping(
        value = "/dependencies",
        consumes = MediaType.TEXT_PLAIN_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Map<String, Object> analyzeDependencies(
            @RequestBody String sourceCode) {

        return dependencyAnalysisService.analyzeDependencies(sourceCode);
    }
}