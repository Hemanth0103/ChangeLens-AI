package com.example.demo.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class JavaAnalysisService {

    public Map<String, Object> analyze(String sourceCode) {
        CompilationUnit unit = StaticJavaParser.parse(sourceCode);

        List<String> imports = unit.getImports().stream()
                .map(importDeclaration -> importDeclaration.getNameAsString())
                .toList();

        List<Map<String, Object>> classes = unit
                .findAll(ClassOrInterfaceDeclaration.class)
                .stream()
                .map(declaration -> {
                    Map<String, Object> classInfo = new LinkedHashMap<>();
                    classInfo.put("name", declaration.getNameAsString());
                    classInfo.put("interface", declaration.isInterface());
                    classInfo.put("methods", declaration.getMethods().stream()
                            .map(method -> method.getNameAsString())
                            .toList());
                    return classInfo;
                })
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("packageName", unit.getPackageDeclaration()
                .map(declaration -> declaration.getNameAsString())
                .orElse(""));
        result.put("imports", imports);
        result.put("classes", classes);

        return result;
    }
}
