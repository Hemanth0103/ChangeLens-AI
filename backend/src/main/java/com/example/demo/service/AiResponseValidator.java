
package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiResponseValidator {

    private static final Pattern QUALIFIED_METHOD =
            Pattern.compile(
                    "\\b([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)"
                            + "\\.([A-Za-z_$][\\w$]*)\\s*\\("
            );

    private static final Pattern UNQUALIFIED_METHOD =
            Pattern.compile(
                    "(?<![\\w$.])([A-Za-z_$][\\w$]*)\\s*\\("
            );

    private static final Set<String> IGNORED_NAMES = Set.of(
            "if", "for", "while", "switch", "catch",
            "return", "throw", "new", "super", "this",
            "println", "print", "printf",
            "equals", "hashCode", "toString",
            "getClass", "wait", "notify", "notifyAll"
    );

    /*
     * These patterns recognize common ways the AI says
     * a method is absent or has not been verified.
     *
     * This is heuristic language analysis, not a full
     * natural-language understanding system.
     */
    private static final List<Pattern> NEGATIVE_PATTERNS = List.of(
            Pattern.compile(
                    "\\b(?:does\\s+not|doesn't|do\\s+not|don't)"
                            + "\\s+(?:appear\\s+to\\s+)?"
                            + "(?:have|contain|define|declare|include|implement)\\b",
                    Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile(
                    "\\b(?:has\\s+no|have\\s+no|contains\\s+no)\\b",
                    Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile(
                    "\\b(?:not|never)\\s+"
                            + "(?:found|defined|declared|implemented|present|available)\\b",
                    Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile(
                    "\\b(?:cannot|can't|could\\s+not|couldn't)"
                            + "\\s+(?:find|verify|confirm|identify)\\b",
                    Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile(
                    "\\b(?:no\\s+evidence\\s+of|"
                            + "no\\s+such\\s+method)\\b",
                    Pattern.CASE_INSENSITIVE
            )
    );

    public List<String> findUnverifiedMethods(
            String aiAnswer,
            Map<String, List<String>> classMethods) {

        if (aiAnswer == null || aiAnswer.isBlank()
                || classMethods == null || classMethods.isEmpty()) {
            return List.of();
        }

        Set<String> warnings = new TreeSet<>();

        String[] sentences =
                aiAnswer.split("(?<=[.!?])\\s+|\\R+");

        for (String sentence : sentences) {

            if (sentence.isBlank()) {
                continue;
            }

            validateSentence(
                    sentence,
                    classMethods,
                    warnings
            );
        }

        return new ArrayList<>(warnings);
    }

    private void validateSentence(
            String sentence,
            Map<String, List<String>> classMethods,
            Set<String> warnings) {

        /*
         * Ignore sentences that clearly deny or cannot
         * verify method existence.
         *
         * We do not want to warn when the AI is already
         * communicating uncertainty or absence.
         */
        if (isNegativeMethodStatement(sentence)) {
            return;
        }

        validateQualifiedMethods(
                sentence,
                classMethods,
                warnings
        );

        validateUnqualifiedMethods(
                sentence,
                classMethods,
                warnings
        );
    }

    private void validateQualifiedMethods(
            String sentence,
            Map<String, List<String>> classMethods,
            Set<String> warnings) {

        Matcher matcher =
                QUALIFIED_METHOD.matcher(sentence);

        while (matcher.find()) {

            String classReference = matcher.group(1);
            String methodName = matcher.group(2);

            for (String verifiedClass : classMethods.keySet()) {

                String simpleName = simpleName(verifiedClass);

                if (!verifiedClass.equals(classReference)
                        && !simpleName.equals(classReference)) {
                    continue;
                }

                if (!isDeclared(
                        methodName,
                        classMethods.get(verifiedClass))) {

                    warnings.add(
                            verifiedClass + "." + methodName + "()"
                    );
                }
            }
        }
    }

    private void validateUnqualifiedMethods(
            String sentence,
            Map<String, List<String>> classMethods,
            Set<String> warnings) {

        List<String> mentionedClasses =
                findMentionedClasses(sentence, classMethods);

        // Do not guess when multiple classes are mentioned.
        if (mentionedClasses.size() != 1) {
            return;
        }

        String verifiedClass = mentionedClasses.get(0);

        Matcher matcher =
                UNQUALIFIED_METHOD.matcher(sentence);

        while (matcher.find()) {

            String methodName = matcher.group(1);

            if (IGNORED_NAMES.contains(methodName)) {
                continue;
            }

            int start = matcher.start(1);

            // Qualified references were already handled.
            if (start > 0
                    && sentence.charAt(start - 1) == '.') {
                continue;
            }

            // Do not treat constructors as methods.
            if (methodName.equals(simpleName(verifiedClass))) {
                continue;
            }

            if (!isDeclared(
                    methodName,
                    classMethods.get(verifiedClass))) {

                warnings.add(
                        verifiedClass + "." + methodName + "()"
                );
            }
        }
    }

    private boolean isNegativeMethodStatement(String sentence) {

        for (Pattern pattern : NEGATIVE_PATTERNS) {

            if (pattern.matcher(sentence).find()) {
                return true;
            }
        }

        return false;
    }

    private boolean isDeclared(
            String methodName,
            List<String> declaredMethods) {

        return declaredMethods != null
                && declaredMethods.contains(methodName);
    }

    private String simpleName(String qualifiedClass) {

        return qualifiedClass.substring(
                qualifiedClass.lastIndexOf('.') + 1
        );
    }

    private List<String> findMentionedClasses(
            String sentence,
            Map<String, List<String>> classMethods) {

        List<String> matches = new ArrayList<>();

        for (String verifiedClass : classMethods.keySet()) {

            String simpleName = simpleName(verifiedClass);

            Pattern classPattern = Pattern.compile(
                    "(?<![\\w$])(?:"
                            + Pattern.quote(verifiedClass)
                            + "|"
                            + Pattern.quote(simpleName)
                            + ")(?![\\w$])"
            );

            if (classPattern.matcher(sentence).find()) {
                matches.add(verifiedClass);
            }
        }

        return matches;
    }
}
