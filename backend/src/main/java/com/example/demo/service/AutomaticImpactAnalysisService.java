
package com.example.demo.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
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

        if (changedClass == null || changedClass.isBlank()) {
            throw new IllegalArgumentException(
                    "Changed class must not be empty");
        }

        if (sourceFiles == null || sourceFiles.isEmpty()) {
            throw new IllegalArgumentException(
                    "Source files must not be empty");
        }

        List<CompilationUnit> parsedFiles = new ArrayList<>();

        // Step 1: Parse all source files.
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

        // Step 2: Collect fully qualified project class names.
        Set<String> projectClasses = new LinkedHashSet<>();

        Map<String, Set<String>> classesBySimpleName =
                new LinkedHashMap<>();

        for (CompilationUnit unit : parsedFiles) {

            for (ClassOrInterfaceDeclaration declaration :
                    unit.findAll(ClassOrInterfaceDeclaration.class)) {

                String fullName = getFullClassName(declaration);

                projectClasses.add(fullName);

                classesBySimpleName
                        .computeIfAbsent(
                                declaration.getNameAsString(),
                                key -> new LinkedHashSet<>()
                        )
                        .add(fullName);
            }
        }

        // Step 3: Build package-aware dependencies.
        Map<String, List<String>> dependencies =
                new LinkedHashMap<>();

        for (CompilationUnit unit : parsedFiles) {

            String packageName = unit.getPackageDeclaration()
                    .map(declaration -> declaration.getNameAsString())
                    .orElse("");

            Map<String, String> explicitImports =
                    new LinkedHashMap<>();

            for (ImportDeclaration importDeclaration :
                    unit.getImports()) {

                if (importDeclaration.isAsterisk()
                        || importDeclaration.isStatic()) {
                    continue;
                }

                String importedName =
                        importDeclaration.getNameAsString();

                String simpleName = importedName.substring(
                        importedName.lastIndexOf('.') + 1
                );

                explicitImports.put(simpleName, importedName);
            }

            for (ClassOrInterfaceDeclaration declaration :
                    unit.findAll(ClassOrInterfaceDeclaration.class)) {

                String className = getFullClassName(declaration);

                Set<String> referencedTypes =
                        new LinkedHashSet<>();

                for (ClassOrInterfaceType type :
                        declaration.findAll(
                                ClassOrInterfaceType.class)) {

                    String typeName = type.getNameAsString();

                    String resolvedName = resolveProjectType(
                            typeName,
                            packageName,
                            explicitImports,
                            projectClasses,
                            classesBySimpleName
                    );

                    if (resolvedName != null
                            && !resolvedName.equals(className)) {

                        referencedTypes.add(resolvedName);
                    }
                }

                dependencies.put(
                        className,
                        new ArrayList<>(referencedTypes)
                );
            }
        }

        // Step 4: Resolve the changed class.
        String resolvedChangedClass = changedClass;

        if (!projectClasses.contains(changedClass)) {

            Set<String> matches =
                    classesBySimpleName.getOrDefault(
                            changedClass,
                            Collections.emptySet()
                    );

            if (matches.size() == 1) {
                resolvedChangedClass = matches.iterator().next();
            } else if (matches.size() > 1) {
                throw new IllegalArgumentException(
                        "Ambiguous changed class: "
                                + changedClass
                                + ". Use a fully qualified name."
                );
            } else {
                throw new IllegalArgumentException(
                        "Changed class not found in project: "
                                + changedClass
                );
            }
        }

        // Step 5: Calculate impact.
        Map<String, Object> impact =
                changeImpactService.analyzeImpact(
                        resolvedChangedClass,
                        dependencies
                );

        // Step 6: Preserve response field structure.
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("dependencies", dependencies);
        result.putAll(impact);

        return result;
    }

    private String getFullClassName(
            ClassOrInterfaceDeclaration declaration) {

        return declaration.getFullyQualifiedName()
                .orElseGet(declaration::getNameAsString);
    }

    private String resolveProjectType(
            String typeName,
            String packageName,
            Map<String, String> explicitImports,
            Set<String> projectClasses,
            Map<String, Set<String>> classesBySimpleName) {

        // Fully qualified reference.
        if (projectClasses.contains(typeName)) {
            return typeName;
        }

        // Explicit import.
        String importedName = explicitImports.get(typeName);

        if (importedName != null) {
            return projectClasses.contains(importedName)
                    ? importedName
                    : null;
        }

        // Class in the same package.
        String samePackageName = packageName.isEmpty()
                ? typeName
                : packageName + "." + typeName;

        if (projectClasses.contains(samePackageName)) {
            return samePackageName;
        }

        // Resolve only when the name is unambiguous.
        Set<String> matches =
                classesBySimpleName.getOrDefault(
                        typeName,
                        Collections.emptySet()
                );

        if (matches.size() == 1) {
            return matches.iterator().next();
        }

        return null;
    }
}
