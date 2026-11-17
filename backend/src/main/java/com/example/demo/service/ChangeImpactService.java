
package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ChangeImpactService {

    public Map<String, Object> analyzeImpact(
            String changedClass,
            Map<String, List<String>> dependencies) {

        if (changedClass == null || changedClass.isBlank()) {
            throw new IllegalArgumentException(
                    "Changed class must not be empty");
        }

        if (dependencies == null) {
            throw new IllegalArgumentException(
                    "Dependencies must not be null");
        }

        for (Map.Entry<String, List<String>> entry
                : dependencies.entrySet()) {

            String className = entry.getKey();

            if (className == null || className.isBlank()) {
                throw new IllegalArgumentException(
                        "Dependency map contains an invalid class name");
            }

            List<String> classDependencies = entry.getValue();

            if (classDependencies == null) {
                throw new IllegalArgumentException(
                        "Dependencies for " + className
                                + " must not be null");
            }

            for (String dependency : classDependencies) {
                if (dependency == null || dependency.isBlank()) {
                    throw new IllegalArgumentException(
                            "Invalid dependency in class "
                                    + className);
                }
            }
        }

        Map<String, Set<String>> reverseDependencies =
                new HashMap<>();

        Set<String> allClasses = new HashSet<>();
        allClasses.add(changedClass);

        for (Map.Entry<String, List<String>> entry
                : dependencies.entrySet()) {

            String className = entry.getKey();
            allClasses.add(className);

            for (String dependency : entry.getValue()) {

                allClasses.add(dependency);

                reverseDependencies
                        .computeIfAbsent(
                                dependency,
                                key -> new TreeSet<>())
                        .add(className);
            }
        }

        Map<String, Integer> distances =
                new LinkedHashMap<>();

        Queue<String> queue = new ArrayDeque<>();

        distances.put(changedClass, 0);
        queue.add(changedClass);

        while (!queue.isEmpty()) {

            String currentClass = queue.poll();
            int currentDistance = distances.get(currentClass);

            for (String dependent :
                    reverseDependencies.getOrDefault(
                            currentClass,
                            Collections.emptySet())) {

                if (!distances.containsKey(dependent)) {

                    distances.put(
                            dependent,
                            currentDistance + 1);

                    queue.add(dependent);
                }
            }
        }

        List<String> impactedClasses = new ArrayList<>();

        // Verified impact classifications
        List<String> directImpactedClasses =
                new ArrayList<>();

        List<String> indirectImpactedClasses =
                new ArrayList<>();

        int directImpact = 0;
        int indirectImpact = 0;
        int maxDepth = 0;

        for (Map.Entry<String, Integer> entry :
                distances.entrySet()) {

            if (entry.getKey().equals(changedClass)) {
                continue;
            }

            impactedClasses.add(entry.getKey());

            int distance = entry.getValue();

            if (distance == 1) {

                directImpact++;
                directImpactedClasses.add(entry.getKey());

            } else {

                indirectImpact++;
                indirectImpactedClasses.add(entry.getKey());
            }

            maxDepth = Math.max(maxDepth, distance);
        }

        int totalImpacted = impactedClasses.size();

        int otherClasses = Math.max(
                0,
                allClasses.size() - 1);

        double projectReach = otherClasses == 0
                ? 0
                : (totalImpacted * 100.0) / otherClasses;

        // Transparent heuristic risk formula:
        //
        // Direct impact: up to 30 points
        // Indirect impact: up to 20 points
        // Dependency depth: up to 20 points
        // Project reach: up to 30 points

        double directScore =
                Math.min(directImpact * 10.0, 30.0);

        double indirectScore =
                Math.min(indirectImpact * 5.0, 20.0);

        double depthScore =
                Math.min(maxDepth * 5.0, 20.0);

        double reachScore =
                projectReach * 0.30;

        int riskScore = (int) Math.round(
                directScore
                        + indirectScore
                        + depthScore
                        + reachScore);

        riskScore = Math.min(100, riskScore);

        String riskLevel;

        if (riskScore >= 70) {
            riskLevel = "HIGH";
        } else if (riskScore >= 35) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "LOW";
        }

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put("changedClass", changedClass);

        result.put("impactedClasses", impactedClasses);

        // New verified class lists
        result.put(
                "directImpactedClasses",
                directImpactedClasses);

        result.put(
                "indirectImpactedClasses",
                indirectImpactedClasses);

        result.put("totalImpacted", totalImpacted);
        result.put("directImpact", directImpact);
        result.put("indirectImpact", indirectImpact);
        result.put("maxDepth", maxDepth);

        result.put(
                "projectReach",
                Math.round(projectReach * 100.0) / 100.0);

        result.put("riskScore", riskScore);
        result.put("riskLevel", riskLevel);

        return result;
    }
}
