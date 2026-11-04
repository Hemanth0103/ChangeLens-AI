
package com.example.demo;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/projects")
public class ProjectAnalysisController {

    private final JavaParser javaParser = new JavaParser();

    @PostMapping(
            value = "/analyze",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> analyzeProject(
            @RequestParam("files") MultipartFile[] files) {

        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No files uploaded"));
        }

        List<Map<String, Object>> analyzedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        Map<String, ClassInfo> projectClasses = new LinkedHashMap<>();
        Map<String, Set<String>> rawDependencies = new LinkedHashMap<>();

        // Verified method names extracted from Java source code.
        Map<String, List<String>> classMethods = new LinkedHashMap<>();

        for (MultipartFile file : files) {

            String filename = file.getOriginalFilename();

            if (filename == null ||
                    !filename.toLowerCase(Locale.ROOT).endsWith(".java")) {
                errors.add("Only Java files are supported: " + filename);
                continue;
            }

            try {
                String source = new String(
                        file.getBytes(),
                        StandardCharsets.UTF_8
                );

                ParseResult<CompilationUnit> parseResult =
                        javaParser.parse(source);

                if (!parseResult.isSuccessful() ||
                        parseResult.getResult().isEmpty()) {
                    errors.add("Unable to parse: " + filename);
                    continue;
                }

                CompilationUnit compilationUnit =
                        parseResult.getResult().get();

                String packageName = compilationUnit.getPackageDeclaration()
                        .map(declaration -> declaration.getNameAsString())
                        .orElse("");

                Map<String, String> imports = new LinkedHashMap<>();

                for (ImportDeclaration importDeclaration :
                        compilationUnit.getImports()) {

                    if (importDeclaration.isStatic() ||
                            importDeclaration.isAsterisk()) {
                        continue;
                    }

                    String importedName =
                            importDeclaration.getNameAsString();

                    String simpleName = importedName.substring(
                            importedName.lastIndexOf('.') + 1
                    );

                    imports.put(simpleName, importedName);
                }

                List<String> classes = new ArrayList<>();

                for (TypeDeclaration<?> type :
                        compilationUnit.getTypes()) {

                    String className = type.getNameAsString();

                    String fullName = packageName.isEmpty()
                            ? className
                            : packageName + "." + className;

                    classes.add(fullName);

                    if (projectClasses.containsKey(fullName)) {
                        errors.add("Duplicate class: " + fullName);
                        continue;
                    }

                    ClassInfo classInfo = new ClassInfo(
                            fullName,
                            packageName,
                            imports
                    );

                    projectClasses.put(fullName, classInfo);

                    // Extract declared methods for each class/interface.
                    if (type instanceof ClassOrInterfaceDeclaration declaration) {
                        List<String> methods = declaration.getMethods()
                                .stream()
                                .map(method -> method.getNameAsString())
                                .distinct()
                                .toList();

                        classMethods.put(fullName, methods);
                    } else {
                        classMethods.put(fullName, List.of());
                    }

                    Set<String> references = new LinkedHashSet<>();

                    for (ClassOrInterfaceType reference :
                            type.findAll(ClassOrInterfaceType.class)) {
                        references.add(reference.getNameWithScope());
                    }

                    rawDependencies.put(fullName, references);
                }

                Map<String, Object> fileResult =
                        new LinkedHashMap<>();

                fileResult.put("fileName", filename);
                fileResult.put("classes", classes);
                fileResult.put("packageName", packageName);

                analyzedFiles.add(fileResult);

            } catch (IOException exception) {
                errors.add("Unable to read: " + filename);
            }
        }

        Map<String, Set<String>> classesBySimpleName =
                new LinkedHashMap<>();

        for (String fullName : projectClasses.keySet()) {

            String simpleName = fullName.substring(
                    fullName.lastIndexOf('.') + 1
            );

            classesBySimpleName
                    .computeIfAbsent(
                            simpleName,
                            key -> new LinkedHashSet<>()
                    )
                    .add(fullName);
        }

        Map<String, List<String>> dependencies =
                new LinkedHashMap<>();

        int totalDependencies = 0;

        for (Map.Entry<String, ClassInfo> entry :
                projectClasses.entrySet()) {

            String sourceClass = entry.getKey();
            ClassInfo classInfo = entry.getValue();

            Set<String> resolvedDependencies =
                    new LinkedHashSet<>();

            for (String reference :
                    rawDependencies.getOrDefault(
                            sourceClass,
                            Collections.emptySet()
                    )) {

                String resolved = resolveReference(
                        reference,
                        classInfo,
                        projectClasses,
                        classesBySimpleName
                );

                if (resolved != null &&
                        !resolved.equals(sourceClass)) {
                    resolvedDependencies.add(resolved);
                }
            }

            List<String> dependencyList =
                    new ArrayList<>(resolvedDependencies);

            dependencies.put(sourceClass, dependencyList);

            totalDependencies += dependencyList.size();
        }

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("totalFiles", analyzedFiles.size());
        response.put("totalClasses", projectClasses.size());
        response.put("totalDependencies", totalDependencies);
        response.put("files", analyzedFiles);
        response.put("dependencies", dependencies);
        response.put("classMethods", classMethods);
        response.put("errors", errors);

        return ResponseEntity.ok(response);
    }

    private String resolveReference(
            String reference,
            ClassInfo classInfo,
            Map<String, ClassInfo> projectClasses,
            Map<String, Set<String>> classesBySimpleName) {

        // 1. Exact fully qualified class reference.
        if (projectClasses.containsKey(reference)) {
            return reference;
        }

        // 2. Explicit import from another package.
        String importedClass = classInfo.imports.get(reference);

        if (importedClass != null &&
                projectClasses.containsKey(importedClass)) {
            return importedClass;
        }

        // 3. Class in the same package.
        String samePackageClass =
                classInfo.packageName.isEmpty()
                        ? reference
                        : classInfo.packageName + "." + reference;

        if (projectClasses.containsKey(samePackageClass)) {
            return samePackageClass;
        }

        // 4. For default-package test files, match
        // a unique class name.
        if (classInfo.packageName.isEmpty()) {

            Set<String> matches =
                    classesBySimpleName.get(reference);

            if (matches != null && matches.size() == 1) {
                return matches.iterator().next();
            }
        }

        return null;
    }

    private static class ClassInfo {

        final String fullName;
        final String packageName;
        final Map<String, String> imports;

        ClassInfo(
                String fullName,
                String packageName,
                Map<String, String> imports) {

            this.fullName = fullName;
            this.packageName = packageName;
            this.imports = new LinkedHashMap<>(imports);
        }
    }
}
