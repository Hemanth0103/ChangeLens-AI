
package com.example.demo.controller;

import com.example.demo.service.AiResponseValidator;
import com.example.demo.service.AiRiskConsistencyValidator;
import com.example.demo.service.ChangeImpactService;
import com.example.demo.service.OllamaService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final OllamaService ollamaService;
    private final ChangeImpactService changeImpactService;
    private final AiResponseValidator aiResponseValidator;

    private final AiRiskConsistencyValidator riskValidator =
            new AiRiskConsistencyValidator();

    private static final Pattern METHOD_REFERENCE =
            Pattern.compile(
                    "\\b([A-Za-z_$][\\w$]*)\\s*\\(\\s*\\)"
            );

    private static final Pattern DIRECT_EXISTENCE_QUESTION =
            Pattern.compile(
                    "^\\s*does\\s+[A-Za-z_$][\\w$.]*"
                    + "\\s+(?:have|declare|contain)\\s+"
                    + "[A-Za-z_$][\\w$]*\\s*\\(\\s*\\)"
                    + "(?:\\s*,?\\s*and\\s+does\\s+"
                    + "[A-Za-z_$][\\w$.]*"
                    + "\\s+(?:have|declare|contain)\\s+"
                    + "[A-Za-z_$][\\w$]*\\s*\\(\\s*\\))*"
                    + "\\s*\\?\\s*$",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern EXISTENCE_QUESTION =
            Pattern.compile(
                    "\\b(?:does|do|has|have|exist|exists|declare|declares)\\b",
                    Pattern.CASE_INSENSITIVE
            );

    /*
     * Only these narrowly defined types of generated statements
     * may appear in the user-facing response.
     *
     * All other AI-generated text is hidden.
     *
     * This is intentionally an allowlist rather than a keyword
     * blacklist. A blacklist cannot reliably detect invented
     * method behavior.
     */
    private static final Pattern SAFE_TEST_SUGGESTION =
            Pattern.compile(
                    "^(?:please\\s+)?(?:consider\\s+)?"
                            + "(?:testing|test|review|check|inspect|verify)"
                            + "\\s+(?:the\\s+)?"
                            + "(?:dependent|impacted|affected|related)"
                            + "\\s+classes\\.?$",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern SAFE_METHOD_SUGGESTION =
            Pattern.compile(
                    "^(?:please\\s+)?(?:consider\\s+)?"
                            + "(?:testing|test|review|check|inspect|verify)"
                            + "\\s+"
                            + "([A-Za-z_$][\\w$]*)"
                            + "\\."
                            + "([A-Za-z_$][\\w$]*)"
                            + "\\(\\)"
                            + "\\.?$",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern SAFE_RISK_DESCRIPTION =
            Pattern.compile(
                    "^the\\s+structural\\s+risk\\s+is\\s+"
                            + "(?:low|medium|moderate|high)\\.$",
                    Pattern.CASE_INSENSITIVE
            );

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

        /*
         * Direct method-existence questions are answered
         * deterministically, without calling Ollama.
         */
        String verifiedMethodAnswer =
                answerMethodExistenceQuestion(
                        question,
                        allClasses,
                        classMethods
                );

        if (verifiedMethodAnswer != null) {
            return ResponseEntity.ok(
                    Map.of("answer", verifiedMethodAnswer)
            );
        }

        List<String> mentionedClasses = new ArrayList<>();

        for (String className : allClasses) {

            String shortName = simpleName(className);

            if (containsClassName(question, className)
                    || containsClassName(question, shortName)) {

                mentionedClasses.add(className);
            }
        }

        String changedClass =
                mentionedClasses.size() == 1
                        ? mentionedClasses.get(0)
                        : null;

        String verifiedImpact = "";

        if (changedClass != null) {
            verifiedImpact = calculateVerifiedImpact(
                    changedClass,
                    dependencies
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

                Ground your answers in the supplied project data.

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

            /*
             * Validation runs on the ORIGINAL generated answer.
             * This lets us detect unsupported method references
             * even when the generated explanation is hidden.
             */
            List<String> unverifiedMethods =
                    aiResponseValidator.findUnverifiedMethods(
                            aiAnswer,
                            classMethods
                    );

            String riskWarning = "";
            String verifiedRiskLevel = null;

            if (changedClass != null) {

                Map<String, Object> verifiedRisk =
                        changeImpactService.analyzeImpact(
                                changedClass,
                                dependencies
                        );

                verifiedRiskLevel = String.valueOf(
                        verifiedRisk.get("riskLevel")
                );

                if (riskValidator.hasContradiction(
                        aiAnswer,
                        verifiedRiskLevel
                )) {

                    riskWarning = """
                            ### Risk Classification Warning

                            The AI explanation conflicts with the
                            verified structural risk level: %s.

                            Use the verified impact analysis as
                            the authoritative result.

                            """.formatted(verifiedRiskLevel);
                }
            }

            String validationWarning = "";

            if (!unverifiedMethods.isEmpty()) {

                validationWarning = """
                        ### Unverified Method References

                        The AI response mentions method references
                        that were not confirmed by the uploaded
                        project's extracted method list:

                        %s

                        These references have been excluded from
                        the displayed AI suggestions.

                        Check the actual source code before
                        relying on them.

                        """.formatted(
                        formatUnverifiedMethods(unverifiedMethods)
                );
            }

            /*
             * Never insert the unrestricted aiAnswer directly
             * into the API response.
             */
            String safeSuggestions =
                    selectSafeSuggestions(
                            aiAnswer,
                            allClasses,
                            classMethods,
                            verifiedRiskLevel
                    );

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
                        safeSuggestions,
                        validationWarning + riskWarning
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
                        safeSuggestions,
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

    /*
     * This function accepts only a narrow set of generic
     * testing recommendations and verified risk descriptions.
     *
     * It does not try to infer whether arbitrary natural
     * language statements are true.
     */
    private String selectSafeSuggestions(
            String aiAnswer,
            Set<String> allClasses,
            Map<String, List<String>> classMethods,
            String verifiedRiskLevel) {

        List<String> accepted = new ArrayList<>();

        /*
         * Process complete lines. Multi-line explanations
         * and Markdown sections that do not match the
         * allowlist are discarded.
         */
        for (String line : aiAnswer.split("\\R")) {

            String candidate = line.trim();

            if (candidate.isEmpty()) {
                continue;
            }

            if (SAFE_TEST_SUGGESTION.matcher(candidate).matches()) {
                accepted.add(candidate);
                continue;
            }

            Matcher methodSuggestion =
                    SAFE_METHOD_SUGGESTION.matcher(candidate);

            if (methodSuggestion.matches()) {

                String referencedClass = methodSuggestion.group(1);
                String referencedMethod = methodSuggestion.group(2);

                String resolvedClass = null;

                for (String className : allClasses) {

                    if (className.equals(referencedClass)
                            || simpleName(className)
                            .equals(referencedClass)) {

                        if (resolvedClass != null) {
                            resolvedClass = null;
                            break;
                        }

                        resolvedClass = className;
                    }
                }

                if (resolvedClass != null) {

                    List<String> declaredMethods =
                            classMethods.get(resolvedClass);

                    if (declaredMethods != null
                            && declaredMethods.contains(
                                    referencedMethod
                            )) {

                        accepted.add(
                                "Consider testing `"
                                        + resolvedClass
                                        + "."
                                        + referencedMethod
                                        + "()`."
                        );
                    }
                }

                continue;
            }

            if (verifiedRiskLevel != null
                    && SAFE_RISK_DESCRIPTION
                    .matcher(candidate)
                    .matches()) {

                String lower =
                        candidate.toLowerCase(Locale.ROOT);

                boolean matchesRisk =
                        switch (verifiedRiskLevel
                                .toUpperCase(Locale.ROOT)) {

                            case "LOW" ->
                                    lower.contains("low");

                            case "MEDIUM" ->
                                    lower.contains("medium")
                                            || lower.contains("moderate");

                            case "HIGH" ->
                                    lower.contains("high");

                            default -> false;
                        };

                if (matchesRisk) {
                    accepted.add(candidate);
                }
            }
        }

        if (accepted.isEmpty()) {
            return """
                    The generated explanation was withheld because
                    the available project analysis cannot verify
                    its behavioral claims.

                    Verified class dependencies, impact results,
                    and declared method names remain available.

                    Review the relevant source code and test
                    affected classes before making changes.
                    """;
        }

        return String.join("\n\n", accepted)
                + "\n\n"
                + "*Other generated statements were withheld "
                + "because their behavior could not be verified.*";
    }

    private String calculateVerifiedImpact(
            String changedClass,
            Map<String, List<String>> dependencies) {

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
                            Collections.emptySet()
                    )) {

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

        return """
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

    private String answerMethodExistenceQuestion(
            String question,
            Set<String> allClasses,
            Map<String, List<String>> classMethods) {

        if (!DIRECT_EXISTENCE_QUESTION.matcher(question).matches()) {
            return null;
        }

        Matcher methodMatcher =
                METHOD_REFERENCE.matcher(question);

        List<String> results = new ArrayList<>();

        while (methodMatcher.find()) {

            String methodName = methodMatcher.group(1);

            String prefix =
                    question.substring(0, methodMatcher.start());

            String matchedClass = null;
            int bestPosition = -1;

            for (String className : allClasses) {

                String shortName = simpleName(className);

                int fullPosition =
                        lastClassPosition(prefix, className);

                int simplePosition =
                        lastClassPosition(prefix, shortName);

                int position =
                        Math.max(fullPosition, simplePosition);

                if (position > bestPosition) {
                    bestPosition = position;
                    matchedClass = className;
                }
            }

            if (matchedClass == null) {
                return null;
            }

            List<String> declaredMethods =
                    classMethods.get(matchedClass);

            if (declaredMethods == null) {

                results.add(
                        ("- **`%s.%s()`**: Method information is unavailable "
                                + "for this class in the uploaded analysis.")
                                .formatted(
                                        matchedClass,
                                        methodName
                                )
                );

            } else if (declaredMethods.contains(methodName)) {

                results.add(
                        ("- **`%s.%s()`**: This method name is listed "
                                + "among the extracted declared methods.")
                                .formatted(
                                        matchedClass,
                                        methodName
                                )
                );

            } else {

                String availableMethods =
                        declaredMethods.isEmpty()
                                ? "No declared methods were extracted."
                                : "Extracted declared methods: "
                                + formatMethodNames(declaredMethods);

                results.add(
                        ("- **`%s.%s()`**: This method is not listed "
                                + "among the extracted declared methods. %s")
                                .formatted(
                                        matchedClass,
                                        methodName,
                                        availableMethods
                                )
                );
            }
        }

        if (results.isEmpty()) {
            return null;
        }

        return """
                ## Verified Method Declaration Check

                %s

                *These results are based on extracted method
                declarations, not method bodies, inherited methods,
                or runtime behavior.*
                """.formatted(
                String.join("\n", results)
        );
    }

    private int lastClassPosition(
            String text,
            String className) {

        int lastPosition = -1;

        String lowerText =
                text.toLowerCase(Locale.ROOT);

        String lowerClass =
                className.toLowerCase(Locale.ROOT);

        int index = lowerText.indexOf(lowerClass);

        while (index >= 0) {

            int end = index + lowerClass.length();

            boolean validStart =
                    index == 0
                            || !Character.isJavaIdentifierPart(
                            lowerText.charAt(index - 1)
                    );

            boolean validEnd =
                    end == lowerText.length()
                            || !Character.isJavaIdentifierPart(
                            lowerText.charAt(end)
                    );

            if (validStart && validEnd) {
                lastPosition = index;
            }

            index = lowerText.indexOf(
                    lowerClass,
                    index + 1
            );
        }

        return lastPosition;
    }

    private String formatMethodNames(
            List<String> methods) {

        List<String> formatted = new ArrayList<>();

        for (String method : methods) {
            formatted.add("`" + method + "()`");
        }

        return String.join(", ", formatted);
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

        return lastClassPosition(question, className) >= 0;
    }

    private String simpleName(String qualifiedClass) {

        return qualifiedClass.substring(
                qualifiedClass.lastIndexOf('.') + 1
        );
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
