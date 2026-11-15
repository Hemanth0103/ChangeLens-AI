
package com.example.demo.controller;

import com.example.demo.service.AiRiskConsistencyValidator;
import com.example.demo.service.ChangeImpactService;
import com.example.demo.service.OllamaService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiImpactController {

    private final ChangeImpactService changeImpactService;
    private final OllamaService ollamaService;
    private final AiRiskConsistencyValidator riskValidator =
            new AiRiskConsistencyValidator();

    public AiImpactController(
            ChangeImpactService changeImpactService,
            OllamaService ollamaService) {

        this.changeImpactService = changeImpactService;
        this.ollamaService = ollamaService;
    }

    @PostMapping("/impact-explanation")
    public ResponseEntity<Map<String, Object>> explainImpact(
            @RequestBody ImpactRequest request) {

        if (request == null ||
                request.changedClass() == null ||
                request.changedClass().isBlank() ||
                request.dependencies() == null) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            "Valid changedClass and dependencies are required"
                    )
            );
        }

        Map<String, Object> analysis;

        try {
            analysis = changeImpactService.analyzeImpact(
                    request.changedClass(),
                    request.dependencies()
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            "Invalid impact analysis request"
                    )
            );
        }

        String prompt = """
                You are an expert Java software engineer assisting
                a static code analysis tool called ChangeLens AI.

                The following data was calculated by the application's
                dependency analysis engine.

                Changed class: %s

                Impacted classes: %s

                Direct impact count: %s

                Indirect impact count: %s

                Maximum dependency depth: %s

                Risk score: %s out of 100

                Risk level: %s

                Dependency map:

                %s

                Explain:

                1. Which classes may be affected by this change.

                2. Why they may be affected, distinguishing direct
                   dependencies from indirect dependencies.

                3. What regression tests developers should consider.

                4. What the structural risk score indicates.

                Rules:

                - Use only the supplied dependency data.
                - Do not invent classes, methods, or dependencies.
                - Treat impacts as potential risks, not confirmed bugs.
                - The risk score is a heuristic, not a probability.
                - The supplied risk score and risk level are authoritative.
                - Do not recalculate or change the verified risk level.
                - Keep your response concise and readable.

                """.formatted(
                request.changedClass(),
                analysis.get("impactedClasses"),
                analysis.get("directImpact"),
                analysis.get("indirectImpact"),
                analysis.get("maxDepth"),
                analysis.get("riskScore"),
                analysis.get("riskLevel"),
                request.dependencies()
        );

        try {
            String explanation =
                    ollamaService.generateExplanation(prompt);

            Map<String, Object> response = new LinkedHashMap<>();

            response.put("analysis", analysis);
            response.put("aiExplanation", explanation);

            String verifiedRiskLevel =
                    String.valueOf(analysis.get("riskLevel"));

            if (riskValidator.hasContradiction(
                    explanation,
                    verifiedRiskLevel)) {

                response.put(
                        "riskWarning",
                        "The AI explanation conflicts with the "
                                + "verified structural risk level: "
                                + verifiedRiskLevel
                                + ". Use the verified impact analysis "
                                + "as the authoritative result."
                );
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            return ResponseEntity.status(
                    HttpStatus.SERVICE_UNAVAILABLE
            ).body(
                    Map.of(
                            "error",
                            "AI explanation is currently unavailable",
                            "analysis",
                            analysis
                    )
            );
        }
    }

    public record ImpactRequest(
            String changedClass,
            Map<String, List<String>> dependencies
    ) {
    }
}
