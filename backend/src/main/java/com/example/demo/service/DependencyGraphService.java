package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DependencyGraphService {

    public Map<String, Object> buildGraph(
            Map<String, List<String>> dependencies) {

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        // Create a node for each class and referenced dependency
        Set<String> allClasses = new LinkedHashSet<>();

        for (Map.Entry<String, List<String>> entry
                : dependencies.entrySet()) {

            allClasses.add(entry.getKey());

            if (entry.getValue() != null) {
                allClasses.addAll(entry.getValue());
            }
        }

        // Convert classes into graph nodes
        for (String className : allClasses) {

            Map<String, Object> node = new LinkedHashMap<>();

            node.put("id", className);
            node.put("label", className);

            nodes.add(node);
        }

        // Convert dependencies into graph edges
        for (Map.Entry<String, List<String>> entry
                : dependencies.entrySet()) {

            String sourceClass = entry.getKey();

            if (entry.getValue() == null) {
                continue;
            }

            for (String targetClass : entry.getValue()) {

                Map<String, Object> edge = new LinkedHashMap<>();

                edge.put("id", sourceClass + "->" + targetClass);
                edge.put("source", sourceClass);
                edge.put("target", targetClass);

                edges.add(edge);
            }
        }

        Map<String, Object> graph = new LinkedHashMap<>();

        graph.put("nodes", nodes);
        graph.put("edges", edges);

        return graph;
    }
}