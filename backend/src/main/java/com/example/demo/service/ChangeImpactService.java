package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ChangeImpactService {

    public Map<String, Object> analyzeImpact(
            String changedClass,
            Map<String, List<String>> dependencies) {

        Set<String> impactedClasses = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(changedClass);

        while (!queue.isEmpty()) {
            String currentClass = queue.poll();

            for (Map.Entry<String, List<String>> entry
                    : dependencies.entrySet()) {

                String className = entry.getKey();
                List<String> classDependencies = entry.getValue();

                if (classDependencies.contains(currentClass)
                        && !className.equals(changedClass)
                        && impactedClasses.add(className)) {

                    queue.add(className);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("changedClass", changedClass);
        result.put("impactedClasses", new ArrayList<>(impactedClasses));
        result.put("totalImpacted", impactedClasses.size());

        return result;
    }
}