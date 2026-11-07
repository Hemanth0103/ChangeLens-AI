
package com.example.demo.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        if (changedClass == null || changedClass.isBlank()) {
            throw new IllegalArgumentException(
                    "Changed class must not be empty");
        }

        if (sourceFiles == null || sourceFiles.isEmpty()) {
            throw new IllegalArgumentException(
                    "Source files must not be empty");
        }

        // Step 1: Parse source files once.
        List<CompilationUnit> parsedFiles = new ArrayList<>();

        for (Map.Entry<String, String> entry : sourceFiles.entrySet()) {

            String sourceCode = entry.getValue();

            if (sourceCode == null || sourceCode.isBlank()) {
                throw new IllegalArgumentException(
                        "Source file is empty: " + entry.getKey());
            }

            try {
                parsedFiles.add(
                        StaticJavaParser.parse(sourceCode)
                );
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(
                        "Unable to parse source file: "
                                + entry.getKey(),
                        exception
                );
            }
        }

        // Step 2: Collect classes defined in this project.
        Set<String> projectClasses = new LinkedHashSet<>();

        for (CompilationUnit unit : parsedFiles) {

            for (ClassOrInterfaceDeclaration declaration :
                    unit.findAll(ClassOrInterfaceDeclaration.class)) {

                projectClasses.add(
                        declaration.getNameAsString()
                );
            }
        }

        // Step 3: Build project-only dependencies.
        Map<String, List<String>> dependencies =
                new LinkedHashMap<>();

        for (CompilationUnit unit : parsedFiles) {

            for (ClassOrInterfaceDeclaration declaration :
                    unit.findAll(ClassOrInterfaceDeclaration.class)) {

                String className = declaration.getNameAsString();

                Set<String> referencedTypes =
                        new LinkedHashSet<>();

                for (ClassOrInterfaceType type :
                        declaration.findAll(
                                ClassOrInterfaceType.class)) {

                    String referencedClass =
                            type.getNameAsString();

                    // Only include types declared in the project.
                    if (!projectClasses.contains(referencedClass)) {
                        continue;
                    }

                    // A class should not depend on itself.
                    if (className.equals(referencedClass)) {
                        continue;
                    }

                    referencedTypes.add(referencedClass);
                }

                dependencies.put(
                        className,
                        new ArrayList<>(referencedTypes)
                );
            }
        }

        // Step 4: Calculate change impact and risk.
        Map<String, Object> impact =
                changeImpactService.analyzeImpact(
                        changedClass,
                        dependencies
                );

        // Step 5: Preserve the existing response structure.
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("dependencies", dependencies);
        result.putAll(impact);

        return result;
    }
}
