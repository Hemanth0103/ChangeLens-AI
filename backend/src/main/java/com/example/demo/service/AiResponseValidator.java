
package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiResponseValidator {

    private static final Pattern METHOD_REFERENCE =
            Pattern.compile(
                    "\\b([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)"
                    + "\\.([A-Za-z_$][\\w$]*)\\s*\\("
            );

    public List<String> findUnverifiedMethods(
            String aiAnswer,
            Map<String, List<String>> classMethods) {

        if (aiAnswer == null || aiAnswer.isBlank()
                || classMethods == null || classMethods.isEmpty()) {
            return List.of();
        }

        Set<String> warnings = new TreeSet<>();
        Matcher matcher = METHOD_REFERENCE.matcher(aiAnswer);

        while (matcher.find()) {

            String referencedClass = matcher.group(1);
            String referencedMethod = matcher.group(2);

            List<String> matchingClasses = new ArrayList<>();

            for (String verifiedClass : classMethods.keySet()) {

                String simpleName = verifiedClass.substring(
                        verifiedClass.lastIndexOf('.') + 1
                );

                if (verifiedClass.equals(referencedClass)
                        || simpleName.equals(referencedClass)) {
                    matchingClasses.add(verifiedClass);
                }
            }

            if (matchingClasses.isEmpty()) {
                continue;
            }

            for (String verifiedClass : matchingClasses) {

                List<String> methods =
                        classMethods.get(verifiedClass);

                if (methods == null
                        || !methods.contains(referencedMethod)) {

                    warnings.add(
                            verifiedClass + "."
                                    + referencedMethod + "()"
                    );
                }
            }
        }

        return new ArrayList<>(warnings);
    }
}
