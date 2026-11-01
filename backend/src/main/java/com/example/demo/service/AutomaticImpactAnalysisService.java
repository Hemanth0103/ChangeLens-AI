package com.example.demo.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AutomaticImpactAnalysisService {

    private final ChangeImpactService changeImpactService;

    public AutomaticImpactAnalysisService(
            ChangeImpactService changeImpactService) {
        this.changeImpactService = changeImpactService;
    }

    public Map<String, Object> analyze(
            String changedClass,
            Map<String, String> sourceFiles) {

        Map<String, List<String>> dependencies = new LinkedHashMap<>();

        for (String sourceCode : sourceFiles.values()) {

            CompilationUnit unit = StaticJavaParser.parse(sourceCode);

            for (ClassOrInterfaceDeclaration declaration :
                    unit.findAll(ClassOrInterfaceDeclaration.class)) {

                String className = declaration.getNameAsString();

                List<String> referencedTypes = declaration
                        .findAll(ClassOrInterfaceType.class)
                        .stream()
                        .map(type -> type.getNameAsString())
                        .distinct()
                        .toList();

                dependencies.put(className, referencedTypes);
            }
        }

        Map<String, Object> impact =
                changeImpactService.analyzeImpact(
                        changedClass, dependencies);

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("dependencies", dependencies);
        result.putAll(impact);

        return result;
    }
}