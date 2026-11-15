
package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiRiskConsistencyValidator {

    private static final Pattern STRUCTURAL_RISK = Pattern.compile(
            "\\b(?:structural|overall|change|dependency)?\\s*"
                    + "risk\\s+(?:level\\s+)?(?:is|:|=)\\s*"
                    + "(low|medium|moderate|high)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern RISK_DESCRIPTION = Pattern.compile(
            "\\b(low|medium|moderate|high)\\s+"
                    + "(?:(?:structural|overall|change|dependency)\\s+)?"
                    + "risk\\b",
            Pattern.CASE_INSENSITIVE
    );

    public boolean hasContradiction(
            String explanation,
            String verifiedRiskLevel) {

        if (explanation == null || explanation.isBlank()
                || verifiedRiskLevel == null
                || verifiedRiskLevel.isBlank()) {
            return false;
        }

        String expected = normalize(verifiedRiskLevel);

        if (!expected.equals("LOW")
                && !expected.equals("MEDIUM")
                && !expected.equals("HIGH")) {
            return false;
        }

        return containsContradiction(
                STRUCTURAL_RISK.matcher(explanation), expected)
                || containsContradiction(
                RISK_DESCRIPTION.matcher(explanation), expected);
    }

    private boolean containsContradiction(
            Matcher matcher,
            String expected) {

        while (matcher.find()) {
            String actual = normalize(matcher.group(1));

            if (!actual.equals(expected)) {
                return true;
            }
        }

        return false;
    }

    private String normalize(String value) {
        String normalized =
                value.trim().toUpperCase(Locale.ROOT);

        if (normalized.equals("MODERATE")) {
            return "MEDIUM";
        }

        return normalized;
    }
}
