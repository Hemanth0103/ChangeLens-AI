
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

        if (request == null
                || request.changedClass() == null
                || request.changedClass().isBlank()
                || request.dependencies() == null) {

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

        } catch (IllegalArgumentException exception) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            "Invalid impact analysis request"
                    )
            );
        }

        // These classifications come from the verified
        // structural dependency analysis, not from AI.

        Object directImpactedClasses =
                analysis.get("directImpactedClasses");

        Object indirectImpactedClasses =
                analysis.get("indirectImpactedClasses");

        String prompt = """
                You are an expert Java software engineer assisting
                a static code analysis tool called ChangeLens AI.

                The following results were calculated and verified
                by the application's dependency analysis engine.

                VERIFIED STRUCTURAL ANALYSIS

                Changed class:
                %s

                Directly impacted classes (distance = 1):
                %s

                Indirectly impacted classes (distance >= 2):
                %s

                All impacted classes:
                %s

                Direct impact count: %s
                Indirect impact count: %s
                Maximum dependency depth: %s

                Risk score: %s out of 100
                Risk level: %s

                Dependency map:
                %s

                IMPORTANT DEFINITIONS

                - The changed class is the starting point.
                  It must not be listed as an impacted class.

                - A directly impacted class depends on the
                  changed class through one dependency edge.

                - An indirectly impacted class depends on
                  the changed class through two or more edges.

                - The verified direct and indirect class lists
                  are authoritative.

                - Never move a class from the direct impact
                  list to the indirect impact list or vice versa.

                EXPLAIN THE FOLLOWING

                1. Changed class:
                   Identify the class being modified.

                2. Direct impact:
                   Explain which verified directly impacted
                   classes may be affected and why.

                3. Indirect impact:
                   Explain which verified indirectly impacted
                   classes may be affected and why.

                4. Recommended regression tests:
                   Suggest tests based only on the supplied
                   dependency relationships.

                5. Structural risk:
                   Explain the verified risk score and level.

                STRICT RULES

                - Use only the supplied dependency data.
                - Do not invent classes, methods, or dependencies.
                - Do not invent additional impacted classes.
                - Do not classify the changed class as impacted.
                - Preserve the verified direct impact list.
                - Preserve the verified indirect impact list.
                - Treat impacts as potential risks, not confirmed bugs.
                - The risk score is a heuristic, not a probability.
                - Do not recalculate or change the risk score.
                - Do not change the verified risk level.
                - Keep the explanation concise and readable.
                - Use clear Markdown headings and bullet points.

                """.formatted(
                request.changedClass(),
                directImpactedClasses,
                indirectImpactedClasses,
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

            Map<String, Object> response =
                    new LinkedHashMap<>();

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

        } catch (Exception exception) {

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
