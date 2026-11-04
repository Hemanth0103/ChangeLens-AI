
package com.example.demo.controller;

import com.example.demo.service.AiResponseValidator;
import com.example.demo.service.ChangeImpactService;
import com.example.demo.service.OllamaService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final OllamaService ollamaService;
    private final ChangeImpactService changeImpactService;
    private final AiResponseValidator aiResponseValidator;

    public AiChatController(
            OllamaService ollamaService,
            ChangeImpactService changeImpactService,
            AiResponseValidator aiResponseValidator) {

        this.ollamaService = ollamaService;
        this.changeImpactService = changeImpactService;
        this.aiResponseValidator = aiResponseValidator;
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {

        if (request == null
                || request.question() == null
                || request.question().isBlank()) {

            return ResponseEntity.badRequest().body(
                    Map.of("error", "Question cannot be empty")
            );
        }

        Map<String, List<String>> dependencies =
                request.dependencies() == null
                        ? Map.of()
                        : request.dependencies();

        Map<String, List<String>> classMethods =
                request.classMethods() == null
                        ? Map.of()
                        : request.classMethods();

        if (dependencies.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "No project dependencies provided")
            );
        }

        String question = request.question().trim();

        Set<String> allClasses = new TreeSet<>();

        for (Map.Entry<String, List<String>> entry
                : dependencies.entrySet()) {

            allClasses.add(entry.getKey());

            if (entry.getValue() != null) {
                allClasses.addAll(entry.getValue());
            }
        }

        List<String> mentionedClasses = new ArrayList<>();

        for (String className : allClasses) {

            String simpleName = className.substring(
                    className.lastIndexOf('.') + 1
            );

            if (containsClassName(question, className)
                    || containsClassName(question, simpleName)) {

                mentionedClasses.add(className);
            }
        }

        String changedClass = mentionedClasses.size() == 1
                ? mentionedClasses.get(0)
                : null;

        String verifiedImpact = "";

        if (changedClass != null) {

            Map<String, Object> impactResult =
                    changeImpactService.analyzeImpact(
                            changedClass,
                            dependencies
                    );

            Map<String, Set<String>> reverseDependencies =
                    new HashMap<>();

            for (Map.Entry<String, List<String>> entry
                    : dependencies.entrySet()) {

                if (entry.getValue() == null) {
                    continue;
                }

                for (String dependency : entry.getValue()) {

                    reverseDependencies
                            .computeIfAbsent(
                                    dependency,
                                    key -> new TreeSet<>()
                            )
                            .add(entry.getKey());
                }
            }

            Map<String, Integer> distances =
                    new LinkedHashMap<>();

            Queue<String> queue = new ArrayDeque<>();

            distances.put(changedClass, 0);
            queue.add(changedClass);

            while (!queue.isEmpty()) {

                String current = queue.poll();
                int nextDistance = distances.get(current) + 1;

                for (String dependent :
                        reverseDependencies.getOrDefault(
                                current,
                                Collections.emptySet())) {

                    if (!distances.containsKey(dependent)) {
                        distances.put(dependent, nextDistance);
                        queue.add(dependent);
                    }
                }
            }

            List<String> directClasses = new ArrayList<>();
            List<String> indirectClasses = new ArrayList<>();

            for (Map.Entry<String, Integer> entry
                    : distances.entrySet()) {

                if (entry.getKey().equals(changedClass)) {
                    continue;
                }

                if (entry.getValue() == 1) {
                    directClasses.add(entry.getKey());
                } else {
                    indirectClasses.add(entry.getKey());
                }
            }

            Collections.sort(directClasses);
            Collections.sort(indirectClasses);

            verifiedImpact = """
                    Changed class: %s
                    Directly impacted classes: %s
                    Indirectly impacted classes: %s
                    Total impacted: %s
                    Structural risk score: %s/100
                    Structural risk level: %s
                    """.formatted(
                    changedClass,
                    directClasses,
                    indirectClasses,
                    impactResult.get("totalImpacted"),
                    impactResult.get("riskScore"),
                    impactResult.get("riskLevel")
            );
        }

        StringBuilder verifiedMethods = new StringBuilder();

        for (String className : allClasses) {

            List<String> methods = classMethods.get(className);

            if (methods == null) {
                verifiedMethods.append(className)
                        .append(": method information unavailable\n");
            } else if (methods.isEmpty()) {
                verifiedMethods.append(className)
                        .append(": no declared methods extracted\n");
            } else {
                verifiedMethods.append(className)
                        .append(": ")
                        .append(methods)
                        .append("\n");
            }
        }

        String prompt = """
                You are ChangeLens AI, a Java dependency
                and change-impact analysis assistant.

                You must ground your answers in the
                supplied project analysis data.

                CRITICAL RULES:

                1. Never invent project classes, methods,
                   dependencies, or API endpoints.

                2. Only describe a method as existing when
                   it appears in the verified method list.

                3. Do not invent method parameters,
                   return types, method bodies, or behavior.

                4. The method list contains declared method
                   names only. It does not prove how those
                   methods are implemented or called.

                5. A dependency A -> B means A depends on B.

                6. If B changes, A may be impacted.

                7. Direct impact means dependency distance 1.

                8. Indirect impact means dependency distance
                   2 or more.

                9. The changed class is NOT an impacted dependent.

                10. Do not change the verified impact results.

                11. Structural risk is a heuristic score,
                    not the probability of a bug.

                12. If information is unavailable, explicitly
                    say that it cannot be determined.

                13. Do not assume that a class named Controller
                    exposes a specific HTTP endpoint.

                14. Recommend testing verified classes and
                    methods where appropriate, but distinguish
                    test suggestions from confirmed behavior.

                15. Do not repeat classes in multiple impact
                    categories.

                16. Answer clearly using Markdown.

                17. Do not claim to have inspected source code
                    bodies, runtime behavior, or test results.

                VERIFIED PROJECT CLASSES:
                %s

                VERIFIED DEPENDENCY MAP:
                %s

                VERIFIED DECLARED METHOD NAMES:
                %s

                VERIFIED IMPACT RESULTS:
                %s

                USER QUESTION:
                %s

                Provide a concise, evidence-based answer.
                """.formatted(
                allClasses,
                dependencies,
                verifiedMethods,
                verifiedImpact.isBlank()
                        ? "No single changed class identified."
                        : verifiedImpact,
                question
        );

        try {

            String aiAnswer =
                    ollamaService.generateExplanation(prompt);

            List<String> unverifiedMethods =
                    aiResponseValidator.findUnverifiedMethods(
                            aiAnswer,
                            classMethods
                    );

            String validationWarning = "";

            if (!unverifiedMethods.isEmpty()) {

                validationWarning = """

                        ### Unverified Method References

                        The AI response mentions method references
                        that were not confirmed by the uploaded
                        project's extracted method list:

                        %s

                        Treat these references as unverified.
                        Check the actual source code before
                        relying on them.

                        """.formatted(
                        formatUnverifiedMethods(unverifiedMethods)
                );
            }

            String answer;

            if (changedClass != null) {

                answer = """
                        ## Verified Impact Analysis

                        %s

                        ## AI-Generated Explanation and Suggestions

                        %s

                        %s

                        *AI suggestions are not verified method
                        behavior or confirmed test results.*
                        """.formatted(
                        formatVerifiedImpact(
                                changedClass,
                                verifiedImpact
                        ),
                        aiAnswer,
                        validationWarning
                );

            } else {

                answer = """
                        ## AI-Generated Explanation and Suggestions

                        %s

                        %s

                        *This explanation uses the extracted
                        dependency graph and available method names.
                        Method behavior has not been verified.*
                        """.formatted(
                        aiAnswer,
                        validationWarning
                );
            }

            return ResponseEntity.ok(
                    Map.of("answer", answer)
            );

        } catch (Exception exception) {

            return ResponseEntity.status(
                    HttpStatus.SERVICE_UNAVAILABLE
            ).body(
                    Map.of(
                            "error",
                            "AI assistant is currently unavailable"
                    )
            );
        }
    }

    private String formatUnverifiedMethods(
            List<String> unverifiedMethods) {

        StringBuilder result = new StringBuilder();

        for (String method : unverifiedMethods) {
            result.append("- `")
                    .append(method)
                    .append("`\n");
        }

        return result.toString();
    }

    private boolean containsClassName(
            String question,
            String className) {

        String lowerQuestion = question.toLowerCase(
                Locale.ROOT
        );

        String lowerClass = className.toLowerCase(
                Locale.ROOT
        );

        int index = lowerQuestion.indexOf(lowerClass);

        while (index >= 0) {

            int end = index + lowerClass.length();

            boolean validStart = index == 0
                    || !Character.isJavaIdentifierPart(
                            lowerQuestion.charAt(index - 1)
                    );

            boolean validEnd = end == lowerQuestion.length()
                    || !Character.isJavaIdentifierPart(
                            lowerQuestion.charAt(end)
                    );

            if (validStart && validEnd) {
                return true;
            }

            index = lowerQuestion.indexOf(
                    lowerClass,
                    index + 1
            );
        }

        return false;
    }

    private String formatVerifiedImpact(
            String changedClass,
            String verifiedImpact) {

        return """
                **Changed class:** `%s`

                ```text
                %s
                ```

                These results were calculated from the
                project's dependency graph, not generated by AI.
                """.formatted(
                changedClass,
                verifiedImpact.strip()
        );
    }

    public record ChatRequest(
            String question,
            Map<String, List<String>> dependencies,
            Map<String, List<String>> classMethods
    ) {
    }
}
