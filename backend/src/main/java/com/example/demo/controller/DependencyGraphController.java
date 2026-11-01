package com.example.demo.controller;

import com.example.demo.service.DependencyGraphService;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analyze")
public class DependencyGraphController {

    private final DependencyGraphService dependencyGraphService;

    public DependencyGraphController(
            DependencyGraphService dependencyGraphService) {

        this.dependencyGraphService = dependencyGraphService;
    }

    @PostMapping("/graph")
    public Map<String, Object> generateGraph(
            @RequestBody Map<String, List<String>> dependencies) {

        return dependencyGraphService.buildGraph(dependencies);
    }
}