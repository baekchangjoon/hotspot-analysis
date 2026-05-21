package io.github.baekchangjoon.hotspotanalysis.analysis;

import io.github.baekchangjoon.hotspotanalysis.analysis.CallGraphBuilder.CallGraphResult;
import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CallGraphBuilderTest {

    private final CallGraphBuilder callGraphBuilder = new CallGraphBuilder();

    @Test
    @DisplayName("should resolve static call graph including interfaces and concrete classes")
    void shouldResolveStaticCallGraph(@TempDir Path tempDir) throws Exception {
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        Path springDir = tempDir.resolve("src/main/java/org/springframework/web/bind/annotation");
        Files.createDirectories(springDir);

        // Write mock spring annotations
        Files.writeString(springDir.resolve("RestController.java"), """
                package org.springframework.web.bind.annotation;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface RestController {}
                """);

        Files.writeString(springDir.resolve("RequestMapping.java"), """
                package org.springframework.web.bind.annotation;
                import java.lang.annotation.*;
                @Target({ElementType.TYPE, ElementType.METHOD})
                @Retention(RetentionPolicy.RUNTIME)
                public @interface RequestMapping {
                    String[] value() default {};
                    String[] path() default {};
                    RequestMethod[] method() default {};
                }
                """);

        Files.writeString(springDir.resolve("RequestMethod.java"), """
                package org.springframework.web.bind.annotation;
                public enum RequestMethod {
                    GET, POST, PUT, DELETE, PATCH
                }
                """);

        Files.writeString(springDir.resolve("GetMapping.java"), """
                package org.springframework.web.bind.annotation;
                import java.lang.annotation.*;
                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface GetMapping {
                    String[] value() default {};
                    String[] path() default {};
                }
                """);

        Files.writeString(springDir.resolve("PostMapping.java"), """
                package org.springframework.web.bind.annotation;
                import java.lang.annotation.*;
                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface PostMapping {
                    String[] value() default {};
                    String[] path() default {};
                }
                """);

        Files.writeString(springDir.resolve("PathVariable.java"), """
                package org.springframework.web.bind.annotation;
                import java.lang.annotation.*;
                @Target(ElementType.PARAMETER)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface PathVariable {
                    String value() default "";
                }
                """);

        // 1. Write mock Spring Boot files
        Files.writeString(srcDir.resolve("UserController.java"), """
                package com.example;
                
                import org.springframework.web.bind.annotation.*;
                
                @RestController
                @RequestMapping("/users")
                public class UserController {
                    private final UserService userService = null;
                    
                    @GetMapping("/{id}")
                    public String getUser(@PathVariable String id) {
                        return userService.findUser(id);
                    }
                }
                """);

        Files.writeString(srcDir.resolve("UserService.java"), """
                package com.example;
                
                public interface UserService {
                    String findUser(String id);
                }
                """);

        Files.writeString(srcDir.resolve("UserServiceImpl.java"), """
                package com.example;
                
                public class UserServiceImpl implements UserService {
                    private final UserRepository userRepository = new UserRepository();
                    
                    @Override
                    public String findUser(String id) {
                        return userRepository.queryName(id);
                    }
                }
                """);

        Files.writeString(srcDir.resolve("UserRepository.java"), """
                package com.example;
                
                public class UserRepository {
                    public String queryName(String id) {
                        return "User-" + id;
                    }
                }
                """);

        // 2. Compile mock files
        Path destDir = tempDir.resolve("build/classes/java/main");
        compileJavaFiles(tempDir.resolve("src/main/java"), destDir);

        // 3. Run call graph builder
        List<Path> javaFiles = List.of(
                srcDir.resolve("UserController.java"),
                srcDir.resolve("UserService.java"),
                srcDir.resolve("UserServiceImpl.java"),
                srcDir.resolve("UserRepository.java")
        );

        CallGraphResult result = callGraphBuilder.buildCallGraphs(
                tempDir,
                javaFiles,
                List.of("build/classes/java/main")
        );

        // 4. Verify results
        Map<MethodSignature, List<MethodSignature>> graphs = result.callGraphs();
        MethodSignature controllerSig = new MethodSignature(
                "com.example.UserController", "getUser", List.of("String"));

        assertThat(graphs).containsKey(controllerSig);
        List<MethodSignature> calls = graphs.get(controllerSig);

        // Should resolve: UserController.getUser -> UserServiceImpl.findUser -> UserRepository.queryName
        assertThat(calls)
                .extracting(MethodSignature::toCanonicalString)
                .containsExactlyInAnyOrder(
                        "com.example.UserServiceImpl#findUser(String)",
                        "com.example.UserRepository#queryName(String)"
                );
    }

    @Test
    @DisplayName("should handle circular method calls gracefully without infinite loops")
    void shouldHandleCircularCallsGracefully(@TempDir Path tempDir) throws Exception {
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        Path springDir = tempDir.resolve("src/main/java/org/springframework/web/bind/annotation");
        Files.createDirectories(springDir);

        // Write mock spring annotations
        Files.writeString(springDir.resolve("RestController.java"), """
                package org.springframework.web.bind.annotation;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface RestController {}
                """);

        Files.writeString(springDir.resolve("GetMapping.java"), """
                package org.springframework.web.bind.annotation;
                import java.lang.annotation.*;
                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface GetMapping {
                    String[] value() default {};
                    String[] path() default {};
                }
                """);

        Files.writeString(srcDir.resolve("CycleController.java"), """
                package com.example;
                
                import org.springframework.web.bind.annotation.*;
                
                @RestController
                public class CycleController {
                    private final CycleService service = new CycleService();
                    
                    @GetMapping("/cycle")
                    public void start() {
                        service.callA();
                    }
                }
                """);

        Files.writeString(srcDir.resolve("CycleService.java"), """
                package com.example;
                
                public class CycleService {
                    public void callA() {
                        callB();
                    }
                    public void callB() {
                        callA();
                    }
                }
                """);

        Path destDir = tempDir.resolve("build/classes/java/main");
        compileJavaFiles(tempDir.resolve("src/main/java"), destDir);

        List<Path> javaFiles = List.of(
                srcDir.resolve("CycleController.java"),
                srcDir.resolve("CycleService.java")
        );

        CallGraphResult result = callGraphBuilder.buildCallGraphs(
                tempDir,
                javaFiles,
                List.of("build/classes/java/main")
        );

        Map<MethodSignature, List<MethodSignature>> graphs = result.callGraphs();
        MethodSignature controllerSig = new MethodSignature(
                "com.example.CycleController", "start", List.of());

        assertThat(graphs).containsKey(controllerSig);
        List<MethodSignature> calls = graphs.get(controllerSig);

        assertThat(calls)
                .extracting(MethodSignature::toCanonicalString)
                .containsExactlyInAnyOrder(
                        "com.example.CycleService#callA()",
                        "com.example.CycleService#callB()"
                );
    }

    private void compileJavaFiles(Path srcDir, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

        List<File> files = Files.walk(srcDir)
                .filter(Files::isRegularFile)
                .map(Path::toFile)
                .toList();

        String classpath = System.getProperty("java.class.path");
        List<String> options = List.of("-d", destDir.toString(), "-classpath", classpath);
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(files);

        javax.tools.DiagnosticCollector<JavaFileObject> diagnostics = new javax.tools.DiagnosticCollector<>();
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
        boolean success = task.call();
        if (!success) {
            for (var d : diagnostics.getDiagnostics()) {
                System.err.println("COMPILER ERROR: " + d.toString());
            }
            throw new RuntimeException("Dynamic compilation of test classes failed.");
        }
        fileManager.close();
    }
}
