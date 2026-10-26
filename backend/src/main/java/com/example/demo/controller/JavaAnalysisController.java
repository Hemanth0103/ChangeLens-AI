package com.example.demo.controller;

import com.example.demo.service.JavaAnalysisService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/analyze")
public class JavaAnalysisController {

    private final JavaAnalysisService analysisService;

    public JavaAnalysisController(JavaAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping(
        value = "/java",
        consumes = MediaType.TEXT_PLAIN_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Map<String, Object> analyzeJava(@RequestBody String sourceCode) {
        return analysisService.analyze(sourceCode);
    }
}
