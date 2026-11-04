
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

    public List<String> findUnverifiedMethods(
            String aiAnswer,
            Map<String, List<String>> classMethods) {

        if (aiAnswer == null || aiAnswer.isBlank()
                || classMethods == null || classMethods.isEmpty()) {
            return List.of();
        }

        Set<String> warnings = new TreeSet<>();

        // Check explicit ClassName.method() references.
        Matcher qualifiedMatcher =
                QUALIFIED_METHOD.matcher(aiAnswer);

        while (qualifiedMatcher.find()) {

            String classReference = qualifiedMatcher.group(1);
            String methodName = qualifiedMatcher.group(2);

            for (String verifiedClass : classMethods.keySet()) {

                String simpleName = simpleName(verifiedClass);

                if (!verifiedClass.equals(classReference)
                        && !simpleName.equals(classReference)) {
                    continue;
                }

                if (!isDeclared(methodName,
                        classMethods.get(verifiedClass))) {

                    warnings.add(
                            verifiedClass + "." + methodName + "()"
                    );
                }
            }
        }

        // Check unqualified method references only when
        // the surrounding sentence explicitly names one
        // project class.
        String[] sentences =
                aiAnswer.split("(?<=[.!?])\\s+|\\R+");

        for (String sentence : sentences) {

            if (sentence.isBlank()) {
                continue;
            }

            List<String> mentionedClasses =
                    findMentionedClasses(sentence, classMethods);

            // Ambiguous class context: do not guess.
            if (mentionedClasses.size() != 1) {
                continue;
            }

            String verifiedClass = mentionedClasses.get(0);

            Matcher unqualifiedMatcher =
                    UNQUALIFIED_METHOD.matcher(sentence);

            while (unqualifiedMatcher.find()) {

                String methodName = unqualifiedMatcher.group(1);

                if (IGNORED_NAMES.contains(methodName)) {
                    continue;
                }

                // Qualified references are already checked.
                int start = unqualifiedMatcher.start(1);

                if (start > 0 && sentence.charAt(start - 1) == '.') {
                    continue;
                }

                // A class constructor is not a method.
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

        return new ArrayList<>(warnings);
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
