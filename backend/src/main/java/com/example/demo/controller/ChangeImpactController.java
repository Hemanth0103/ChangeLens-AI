package com.example.demo.controller;

import com.example.demo.service.ChangeImpactService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analyze")
public class ChangeImpactController {

    private final ChangeImpactService changeImpactService;

    public ChangeImpactController(ChangeImpactService changeImpactService) {
        this.changeImpactService = changeImpactService;
    }

    @PostMapping("/impact")
    public Map<String, Object> analyzeImpact(
            @RequestBody ImpactRequest request) {

        return changeImpactService.analyzeImpact(
                request.changedClass(),
                request.dependencies()
        );
    }

    public record ImpactRequest(
            String changedClass,
            Map<String, List<String>> dependencies
    ) {}
}