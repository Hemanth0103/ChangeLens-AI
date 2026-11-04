
package com.example.demo.controller;

import com.example.demo.service.OllamaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiExplanationController {

    private final OllamaService ollamaService;

    public AiExplanationController(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    @PostMapping("/explain")
    public ResponseEntity<Map<String, String>> explain(
            @RequestBody Map<String, String> request) {

        String prompt = request.get("prompt");

        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Prompt cannot be empty")
            );
        }

        try {
            String explanation =
                    ollamaService.generateExplanation(prompt);

            return ResponseEntity.ok(
                    Map.of("explanation", explanation)
            );

        } catch (Exception e) {
            return ResponseEntity.status(
                    HttpStatus.SERVICE_UNAVAILABLE
            ).body(
                    Map.of(
                            "error",
                            "AI service is currently unavailable"
                    )
            );
        }
    }
}
