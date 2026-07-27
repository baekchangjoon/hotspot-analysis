package io.github.baekchangjoon.hotspotanalysis.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveComplexityCalculatorTest {

    private final CognitiveComplexityCalculator calculator = new CognitiveComplexityCalculator();

    @Test
    @DisplayName("should report 0 for a simple method with no branching")
    void shouldReportZeroForSimpleMethod() {
        MethodDeclaration md = parseMethod("""
                public void hello() {
                    System.out.println("Hello, World!");
                    int a = 1 + 2;
                }
                """);
        assertThat(calculator.calculate(md)).isZero();
    }

    @Test
    @DisplayName("should report 1 for a single if statement")
    void shouldReportOneForSingleIf() {
        MethodDeclaration md = parseMethod("""
                public void test(int x) {
                    if (x > 0) {
                        System.out.println(x);
                    }
                }
                """);
        assertThat(calculator.calculate(md)).isEqualTo(1);
    }

    @Test
    @DisplayName("should apply nesting penalty for nested structures")
    void shouldApplyNestingPenalty() {
        // Outer if (1) + Nested for (1 + nesting 1 = 2) = Total 3
        MethodDeclaration md = parseMethod("""
                public void nested(int x) {
                    if (x > 0) {
                        for (int i = 0; i < x; i++) {
                            System.out.println(i);
                        }
                    }
                }
                """);
        assertThat(calculator.calculate(md)).isEqualTo(3);
    }

    @Test
    @DisplayName("should report 1 for ternary operator")
    void shouldReportOneForTernary() {
        MethodDeclaration md = parseMethod("""
                public int ternary(int x) {
                    return x > 0 ? 1 : 0;
                }
                """);
        assertThat(calculator.calculate(md)).isEqualTo(1);
    }

    @Test
    @DisplayName("should report complexity for logical AND / OR operators")
    void shouldReportForLogicalOperators() {
        MethodDeclaration md = parseMethod("""
                public boolean logic(boolean a, boolean b) {
                    return a && b;
                }
                """);
        assertThat(calculator.calculate(md)).isEqualTo(1);
    }

    @Test
    @DisplayName("should report complexity for try-catch clauses")
    void shouldReportForTryCatch() {
        // try (0) + catch (1) = 1
        MethodDeclaration md = parseMethod("""
                public void handle() {
                    try {
                        doSomething();
                    } catch (IOException e) {
                        log(e);
                    }
                }
                """);
        assertThat(calculator.calculate(md)).isEqualTo(1);
    }

    // Same language level as the production JavaSourceParser, so Java 21
    // idioms (switch expressions, records) parse in tests too.
    private static final JavaParser PARSER = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));

    private MethodDeclaration parseMethod(String code) {
        String wrapped = "class Dummy { " + code + " }";
        CompilationUnit cu = PARSER.parse(wrapped).getResult().orElseThrow();
        return cu.getClassByName("Dummy")
                .orElseThrow()
                .getMethods().get(0);
    }
    @Test
    @DisplayName("switch expressions count +1 like switch statements (Java 14+ idiom)")
    void shouldCountSwitchExpression() {
        // A modern switch EXPRESSION must not score 0 while the equivalent
        // switch STATEMENT scores 1 — otherwise files written in Java 21
        // idioms vanish from the composite ranking (cc=0 → composite 0).
        MethodDeclaration md = parseMethod("""
                public int area(int shape) {
                    return switch (shape) {
                        case 1 -> 10;
                        case 2 -> 20;
                        default -> 0;
                    };
                }
                """);
        assertThat(calculator.calculate(md)).isEqualTo(1);
    }

    @Test
    @DisplayName("nested structures inside a switch expression get the nesting penalty")
    void shouldNestInsideSwitchExpression() {
        // switch expr (1) + if nested one level inside it (1 + 1) = 3
        MethodDeclaration md = parseMethod("""
                public int f(int x) {
                    return switch (x) {
                        case 1 -> { if (x > 0) { yield 1; } yield 2; }
                        default -> 0;
                    };
                }
                """);
        assertThat(calculator.calculate(md)).isEqualTo(3);
    }

}
