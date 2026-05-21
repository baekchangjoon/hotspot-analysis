package io.github.baekchangjoon.hotspotanalysis.parser;

import io.github.baekchangjoon.hotspotanalysis.parser.model.ApiMappingInfo;
import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaSourceParserTest {

    private final JavaSourceParser parser = new JavaSourceParser();

    @Test
    @DisplayName("extracts a single method with name, FQCN, line range, and parameters")
    void shouldExtractSingleMethod() {
        String source = """
                package com.example;

                public class Foo {
                    public int add(int a, int b) {
                        return a + b;
                    }
                }
                """;

        List<MethodInfo> methods = parser.parse(source);

        assertThat(methods).hasSize(1);
        MethodInfo only = methods.get(0);
        assertThat(only.signature().fullyQualifiedClassName()).isEqualTo("com.example.Foo");
        assertThat(only.signature().methodName()).isEqualTo("add");
        assertThat(only.signature().parameterTypes()).containsExactly("int", "int");
        assertThat(only.startLine()).isEqualTo(4);
        assertThat(only.endLine()).isEqualTo(6);
        assertThat(only.lineCount()).isEqualTo(3);
        assertThat(only.parameters()).extracting("name").containsExactly("a", "b");
    }

    @Test
    @DisplayName("extracts multiple methods with distinct line ranges")
    void shouldExtractMultipleMethods() {
        String source = """
                package x;

                public class Bar {
                    public void m1() {}
                    public int m2(String s) {
                        return s.length();
                    }
                }
                """;

        List<MethodInfo> methods = parser.parse(source);

        assertThat(methods).hasSize(2);
        assertThat(methods).extracting(m -> m.signature().methodName())
                .containsExactly("m1", "m2");
    }

    @Test
    @DisplayName("distinguishes overloaded methods by parameter types")
    void shouldDistinguishOverloads() {
        String source = """
                package x;
                public class Baz {
                    public void save(User u) {}
                    public void save(User u, Context c) {}
                }
                class User {}
                class Context {}
                """;

        List<MethodInfo> methods = parser.parse(source);

        List<String> canonicals = methods.stream()
                .filter(m -> m.signature().methodName().equals("save"))
                .map(m -> m.signature().toCanonicalString())
                .toList();
        assertThat(canonicals).containsExactlyInAnyOrder(
                "x.Baz#save(User)",
                "x.Baz#save(User, Context)");
    }

    @Test
    @DisplayName("resolves FQCN for methods inside an inner class")
    void shouldResolveInnerClassFqcn() {
        String source = """
                package p;
                public class Outer {
                    public static class Inner {
                        public int twice(int x) { return x * 2; }
                    }
                }
                """;

        List<MethodInfo> methods = parser.parse(source);

        assertThat(methods).hasSize(1);
        assertThat(methods.get(0).signature().fullyQualifiedClassName())
                .isEqualTo("p.Outer.Inner");
    }

    @Test
    @DisplayName("extracts custom methods declared inside a record (Java 21)")
    void shouldExtractMethodsInsideRecord() {
        String source = """
                package p;
                public record Point(int x, int y) {
                    public int magnitudeSquared() {
                        return x * x + y * y;
                    }
                }
                """;

        List<MethodInfo> methods = parser.parse(source);

        assertThat(methods).hasSize(1);
        assertThat(methods.get(0).signature().fullyQualifiedClassName()).isEqualTo("p.Point");
        assertThat(methods.get(0).signature().methodName()).isEqualTo("magnitudeSquared");
    }

    @Test
    @DisplayName("returns empty list for an interface with no default methods")
    void shouldReturnEmptyForEmptyInterface() {
        String source = """
                package p;
                public interface Marker {}
                """;

        List<MethodInfo> methods = parser.parse(source);

        assertThat(methods).isEmpty();
    }

    @Test
    @DisplayName("parses Java 21 switch expressions and pattern matching")
    void shouldParseSwitchExpression() {
        String source = """
                package p;
                public class Switcher {
                    public String describe(Object obj) {
                        return switch (obj) {
                            case Integer i -> "int " + i;
                            case String s  -> "string " + s;
                            default        -> "other";
                        };
                    }
                }
                """;

        List<MethodInfo> methods = parser.parse(source);

        assertThat(methods).hasSize(1);
        assertThat(methods.get(0).signature().methodName()).isEqualTo("describe");
        assertThat(methods.get(0).signature().parameterTypes()).containsExactly("Object");
    }

    @Test
    @DisplayName("parses a file from disk")
    void shouldParseFromFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Foo.java");
        Files.writeString(file, """
                package x;
                public class Foo {
                    public void greet() {}
                }
                """);

        List<MethodInfo> methods = parser.parse(file);

        assertThat(methods).hasSize(1);
        assertThat(methods.get(0).signature().methodName()).isEqualTo("greet");
    }

    @Test
    @DisplayName("raises SourceParseException for syntactically invalid source")
    void shouldFailOnInvalidSyntax() {
        String source = "this is not Java";

        assertThatThrownBy(() -> parser.parse(source))
                .isInstanceOf(SourceParseException.class);
    }

    @Test
    @DisplayName("raises SourceParseException when file does not exist")
    void shouldFailOnMissingFile(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("nope.java");

        assertThatThrownBy(() -> parser.parse(missing))
                .isInstanceOf(SourceParseException.class)
                .hasMessageContaining("nope.java");
    }

    @Test
    @DisplayName("extracts API mapping info for Spring controllers")
    void shouldExtractApiMappingInfoForSpringControllers() {
        String source = """
                package com.example.controller;
                
                import org.springframework.web.bind.annotation.*;
                import java.util.List;
                
                @RestController
                @RequestMapping("/api/v1/users")
                public class UserController {
                
                    @GetMapping
                    public List<String> list() { return List.of(); }
                    
                    @PostMapping("/{id}")
                    public String create(@PathVariable String id) { return null; }
                    
                    @RequestMapping(value = "/delete", method = RequestMethod.DELETE)
                    public void delete() {}
                }
                """;
                
        List<MethodInfo> methods = parser.parse(source);
        assertThat(methods).hasSize(3);
        
        MethodInfo listMethod = methods.stream().filter(m -> m.signature().methodName().equals("list")).findFirst().orElseThrow();
        assertThat(listMethod.apiMappings()).containsExactly(new ApiMappingInfo("GET", "/api/v1/users"));
        
        MethodInfo createMethod = methods.stream().filter(m -> m.signature().methodName().equals("create")).findFirst().orElseThrow();
        assertThat(createMethod.apiMappings()).containsExactly(new ApiMappingInfo("POST", "/api/v1/users/{id}"));
        
        MethodInfo deleteMethod = methods.stream().filter(m -> m.signature().methodName().equals("delete")).findFirst().orElseThrow();
        assertThat(deleteMethod.apiMappings()).containsExactly(new ApiMappingInfo("DELETE", "/api/v1/users/delete"));
    }
}
