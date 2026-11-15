
package com.example.demo.controller;

import com.example.demo.service.ChangeImpactService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analyze")
public class ChangeImpactController {

    private final ChangeImpactService changeImpactService;

    public ChangeImpactController(
            ChangeImpactService changeImpactService) {
        this.changeImpactService = changeImpactService;
    }

    @PostMapping("/impact")
    public ResponseEntity<Map<String, Object>> analyzeImpact(
            @RequestBody ImpactRequest request) {

        if (request == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Impact request must not be null")
            );
        }

        try {
            Map<String, Object> result =
                    changeImpactService.analyzeImpact(
                            request.changedClass(),
                            request.dependencies()
                    );

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", exception.getMessage())
            );
        }
    }

    public record ImpactRequest(
            String changedClass,
            Map<String, List<String>> dependencies
    ) {
    }
}
