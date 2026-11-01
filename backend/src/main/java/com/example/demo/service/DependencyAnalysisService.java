package com.example.demo.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DependencyAnalysisService {

    public Map<String, Object> analyzeDependencies(String sourceCode) {

        CompilationUnit unit = StaticJavaParser.parse(sourceCode);

        List<Map<String, Object>> classes = unit
                .findAll(ClassOrInterfaceDeclaration.class)
                .stream()
                .map(declaration -> {

                    Map<String, Object> classInfo = new LinkedHashMap<>();

                    classInfo.put("className", declaration.getNameAsString());

                    List<String> dependencies = declaration
                            .findAll(ClassOrInterfaceType.class)
                            .stream()
                            .map(type -> type.getNameAsString())
                            .distinct()
                            .toList();

                    classInfo.put("dependencies", dependencies);

                    return classInfo;
                })
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("classes", classes);

        return result;
    }
}