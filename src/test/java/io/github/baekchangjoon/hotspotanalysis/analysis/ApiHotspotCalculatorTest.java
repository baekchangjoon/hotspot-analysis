package io.github.baekchangjoon.hotspotanalysis.analysis;

import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.ApiHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.SharedComponentHotspot;
import io.github.baekchangjoon.hotspotanalysis.config.*;
import io.github.baekchangjoon.hotspotanalysis.parser.JavaSourceParser;
import io.github.baekchangjoon.hotspotanalysis.vcs.VcsProviderFactory;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ApiHotspotCalculatorTest {

    private static final Instant T1 = Instant.parse("2026-01-10T10:00:00Z");

    @TempDir
    Path tempDir;

    Path repoRoot;
    HotspotAnalyzer analyzer;

    @BeforeEach
    void setUp() throws Exception {
        analyzer = new HotspotAnalyzer(
                new VcsProviderFactory(),
                new JavaSourceCollector(),
                new JavaSourceParser(),
                new RevisionsCalculator(),
                new LocCalculator(),
                new HotspotScoreCalculator(),
                new CallGraphBuilder());
        repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
    }

    @Test
    @DisplayName("should compute API hotspots and shared components for different modes")
    void testApiHotspotsAndSharedComponents() throws Exception {
        Path srcDir = repoRoot.resolve("src/main/java/com/example");
        Path springDir = repoRoot.resolve("src/main/java/org/springframework/web/bind/annotation");
        Files.createDirectories(springDir);

        // Write Spring annotation mocks
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

        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            // Write Java source code
            writeJava(git, "src/main/java/com/example/MyController.java", """
                    package com.example;
                    import org.springframework.web.bind.annotation.*;
                    @RestController
                    public class MyController {
                        private final MyService service = new MyService();
                        @GetMapping("/api/a")
                        public void apiA() {
                            service.commonMethod();
                        }
                        @GetMapping("/api/b")
                        public void apiB() {
                            service.commonMethod();
                        }
                    }
                    """, T1, "c1");

            writeJava(git, "src/main/java/com/example/MyService.java", """
                    package com.example;
                    public class MyService {
                        public void commonMethod() {
                            int x = 1;
                            int y = 2;
                        }
                    }
                    """, T1, "c2");
        }

        // Compile
        Path destDir = repoRoot.resolve("build/classes/java/main");
        compileJavaFiles(repoRoot.resolve("src/main/java"), destDir);

        // 1. Test BOTH mode
        AnalysisConfig configBoth = apiConfigFor(repoRoot, ApiAnalysisConfig.SharedComponentMode.BOTH);
        AnalysisResult resultBoth = analyzer.analyze(configBoth);

        assertThat(resultBoth.apiHotspots()).hasSize(2);
        assertThat(resultBoth.sharedComponents()).hasSize(1);

        // In BOTH mode, commonMethod revisions/loc are added to both APIs.
        // MyController revisions for apiA: MyController touched in c1 -> 1 rev.
        // commonMethod: MyService touched in c2 -> 1 rev.
        // So cumulative revs should be 2.
        ApiHotspot apiA = resultBoth.apiHotspots().stream()
                .filter(a -> a.route().equals("/api/a")).findFirst().orElseThrow();
        assertThat(apiA.revisions()).isEqualTo(2);

        SharedComponentHotspot shared = resultBoth.sharedComponents().get(0);
        assertThat(shared.method().methodName()).isEqualTo("commonMethod");
        assertThat(shared.callingApis()).containsExactlyInAnyOrder("GET /api/a", "GET /api/b");

        // 2. Test SEPARATE mode
        AnalysisConfig configSeparate = apiConfigFor(repoRoot, ApiAnalysisConfig.SharedComponentMode.SEPARATE);
        AnalysisResult resultSeparate = analyzer.analyze(configSeparate);

        assertThat(resultSeparate.apiHotspots()).hasSize(2);
        assertThat(resultSeparate.sharedComponents()).hasSize(1);

        // In SEPARATE mode, commonMethod (shared) revisions/loc are excluded from individual APIs.
        // So each API should only have 1 revision (from MyController).
        ApiHotspot apiASep = resultSeparate.apiHotspots().stream()
                .filter(a -> a.route().equals("/api/a")).findFirst().orElseThrow();
        assertThat(apiASep.revisions()).isEqualTo(1);

        // 3. Test CUMULATIVE mode
        AnalysisConfig configCumulative = apiConfigFor(repoRoot, ApiAnalysisConfig.SharedComponentMode.CUMULATIVE);
        AnalysisResult resultCumulative = analyzer.analyze(configCumulative);

        assertThat(resultCumulative.apiHotspots()).hasSize(2);
        assertThat(resultCumulative.sharedComponents()).isEmpty(); // No shared components reported

        ApiHotspot apiACum = resultCumulative.apiHotspots().stream()
                .filter(a -> a.route().equals("/api/a")).findFirst().orElseThrow();
        assertThat(apiACum.revisions()).isEqualTo(2);
    }

    @Test
    @DisplayName("endpoint coverage is line-weighted across the call graph, not a mean of ratios")
    void endpointCoverageIsLineWeighted() throws Exception {
        Path springDir = repoRoot.resolve("src/main/java/org/springframework/web/bind/annotation");
        Files.createDirectories(springDir);
        Files.writeString(springDir.resolve("RestController.java"), """
                package org.springframework.web.bind.annotation;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME)
                public @interface RestController {}
                """);
        Files.writeString(springDir.resolve("GetMapping.java"), """
                package org.springframework.web.bind.annotation;
                import java.lang.annotation.*;
                @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
                public @interface GetMapping { String[] value() default {}; String[] path() default {}; }
                """);

        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava(git, "src/main/java/com/example/WController.java", """
                    package com.example;
                    import org.springframework.web.bind.annotation.*;
                    @RestController
                    public class WController {
                        private final WService svc = new WService();
                        @GetMapping("/api/w")
                        public void get() {
                            svc.small();
                            svc.big();
                        }
                    }
                    """, T1, "c1");
            // small(): body line 4 covered. big(): body lines 7..14 (8 lines) missed.
            writeJava(git, "src/main/java/com/example/WService.java", """
                    package com.example;
                    public class WService {
                        public void small() {
                            int a = 1;
                        }
                        public void big() {
                            int b = 1;
                            int c = 2;
                            int d = 3;
                            int e = 4;
                            int f = 5;
                            int g = 6;
                            int h = 7;
                            int i = 8;
                        }
                    }
                    """, T1, "c2");
        }
        compileJavaFiles(repoRoot.resolve("src/main/java"), repoRoot.resolve("build/classes/java/main"));

        // JaCoCo: small's 1 line covered, big's 8 lines all missed. Controller's
        // own lines are absent from the report (contribute nothing).
        Files.writeString(repoRoot.resolve("jacoco.xml"), """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="t">
                  <package name="com/example">
                    <sourcefile name="WService.java">
                      <line nr="4"  mi="0" ci="1"/>
                      <line nr="7"  mi="1" ci="0"/>
                      <line nr="8"  mi="1" ci="0"/>
                      <line nr="9"  mi="1" ci="0"/>
                      <line nr="10" mi="1" ci="0"/>
                      <line nr="11" mi="1" ci="0"/>
                      <line nr="12" mi="1" ci="0"/>
                      <line nr="13" mi="1" ci="0"/>
                      <line nr="14" mi="1" ci="0"/>
                    </sourcefile>
                  </package>
                </report>
                """);

        TargetConfig target = new TargetConfig(TargetConfig.TargetType.LOCAL_GIT, repoRoot.toString(), null);
        WindowConfig window = new WindowConfig(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), null);
        ScopeConfig scope = new ScopeConfig(
                List.of(ScopeConfig.Granularity.FILE, ScopeConfig.Granularity.METHOD),
                List.of("**/*.java"), List.of());
        ApiAnalysisConfig apiConfig = new ApiAnalysisConfig(
                true, ApiAnalysisConfig.SharedComponentMode.BOTH, List.of("build/classes/java/main"));
        AnalysisSection section = new AnalysisSection(
                target, window, scope, new ScoringConfig(), apiConfig, "jacoco.xml");
        OutputConfig output = new OutputConfig(
                List.of(OutputConfig.OutputFormat.CSV), "./out", 0, OutputConfig.ApiLayout.BOTH);

        AnalysisResult result = analyzer.analyze(new AnalysisConfig(section, output));

        ApiHotspot api = result.apiHotspots().stream()
                .filter(a -> a.route().equals("/api/w")).findFirst().orElseThrow();

        // Line-weighted: covered=1, executable=9 -> 1/9 ≈ 0.111.
        // A mean of per-method ratios would be (0.0 + 1.0 + 0.0)/3 ≈ 0.333 — the
        // big untested method would be masked. Assert we're at the weighted value.
        assertThat(api.lineCoverage()).isCloseTo(1.0 / 9.0, within(1e-6));
        assertThat(api.lineCoverage()).isLessThan(0.2); // would fail under mean-of-ratios (~0.33)

        // The calculation trace must reproduce the final value from raw counts.
        var bd = result.coverageBreakdown();
        assertThat(bd).isNotNull();
        var apiBd = bd.apis().stream()
                .filter(a -> a.route().equals("/api/w")).findFirst().orElseThrow();
        assertThat(apiBd.coveredLines()).isEqualTo(1);
        assertThat(apiBd.executableLines()).isEqualTo(9);
        assertThat(apiBd.lineCoverage()).isCloseTo(1.0 / 9.0, within(1e-9));
        assertThat(apiBd.methods()).anySatisfy(m -> {
            assertThat(m.signature()).contains("WService#small");
            assertThat(m.coveredLines()).isEqualTo(1);
            assertThat(m.executableLines()).isEqualTo(1);
        });
        assertThat(apiBd.methods()).anySatisfy(m -> {
            assertThat(m.signature()).contains("WService#big");
            assertThat(m.coveredLines()).isEqualTo(0);
            assertThat(m.executableLines()).isEqualTo(8);
        });
        assertThat(apiBd.methods()).anySatisfy(m -> {
            assertThat(m.signature()).contains("WController#get");
            assertThat(m.note()).contains("no coverage data");
        });
        // File-level counts back the file report's coverage.
        assertThat(bd.files()).anySatisfy(f -> {
            assertThat(f.path()).endsWith("WService.java");
            assertThat(f.coveredLines()).isEqualTo(1);
            assertThat(f.executableLines()).isEqualTo(9);
        });
    }

    @Test
    @DisplayName("a shared component whose file is absent from a partial jacoco report gets multiplier 1.0, not 10x")
    void sharedComponentAbsentFromPartialReportIsUnknownCoverage() throws Exception {
        writeTwoEndpointFixture();
        Path destDir = repoRoot.resolve("build/classes/java/main");
        compileJavaFiles(repoRoot.resolve("src/main/java"), destDir);

        // The report knows the CONTROLLER file only — MyService.java (which
        // hosts the shared commonMethod) is absent, e.g. another module's
        // report. Unknown coverage must never become the 10x penalty.
        Path report = repoRoot.resolve("jacoco-partial.xml");
        Files.writeString(report, """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="test">
                    <package name="com/example">
                        <sourcefile name="MyController.java">
                            <line nr="8" mi="0" ci="2" mb="0" cb="0"/>
                        </sourcefile>
                    </package>
                </report>
                """);

        TargetConfig target = new TargetConfig(TargetConfig.TargetType.LOCAL_GIT, repoRoot.toString(), null);
        WindowConfig window = new WindowConfig(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), null);
        ScopeConfig scope = new ScopeConfig(
                List.of(ScopeConfig.Granularity.FILE, ScopeConfig.Granularity.METHOD),
                List.of("**/*.java"), List.of());
        ApiAnalysisConfig apiConfig = new ApiAnalysisConfig(
                true, ApiAnalysisConfig.SharedComponentMode.BOTH, List.of("build/classes/java/main"));
        AnalysisSection section = new AnalysisSection(
                target, window, scope, new ScoringConfig(), apiConfig, report.toString());
        OutputConfig output = new OutputConfig(
                List.of(OutputConfig.OutputFormat.CSV), "./out", 0, OutputConfig.ApiLayout.BOTH);

        AnalysisResult result = analyzer.analyze(new AnalysisConfig(section, output));

        assertThat(result.sharedComponents()).hasSize(1);
        SharedComponentHotspot shared = result.sharedComponents().get(0);
        assertThat(shared.method().methodName()).isEqualTo("commonMethod");
        assertThat(shared.coverageMultiplier()).isEqualTo(1.0);
        assertThat(shared.lineCoverage()).isNull();
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

    private static void writeJava(Git git, String relativePath, String body, Instant timestamp, String message) throws Exception {
        Path workTree = git.getRepository().getWorkTree().toPath();
        Path file = workTree.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, body);
        git.add().addFilepattern(relativePath).call();
        PersonIdent ident = new PersonIdent("alice", "alice@example.com", Date.from(timestamp), TimeZone.getTimeZone("UTC"));
        git.commit().setAuthor(ident).setCommitter(ident).setMessage(message).call();
    }

    private static AnalysisConfig apiConfigFor(Path repoRoot, ApiAnalysisConfig.SharedComponentMode mode) {
        TargetConfig target = new TargetConfig(TargetConfig.TargetType.LOCAL_GIT, repoRoot.toString(), null);
        WindowConfig window = new WindowConfig(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), null);
        ScopeConfig scope = new ScopeConfig(
                List.of(ScopeConfig.Granularity.FILE, ScopeConfig.Granularity.METHOD),
                List.of("**/*.java"), List.of());
        ScoringConfig scoring = new ScoringConfig();
        ApiAnalysisConfig apiConfig = new ApiAnalysisConfig(true, mode, List.of("build/classes/java/main"));
        AnalysisSection section = new AnalysisSection(target, window, scope, scoring, apiConfig);
        OutputConfig output = new OutputConfig(
                List.of(OutputConfig.OutputFormat.CSV), "./out", 0, OutputConfig.ApiLayout.BOTH);
        return new AnalysisConfig(section, output);
    }

    private void writeTwoEndpointFixture() throws Exception {
        Path springDir = repoRoot.resolve("src/main/java/org/springframework/web/bind/annotation");
        Files.createDirectories(springDir);
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
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava(git, "src/main/java/com/example/MyController.java", """
                    package com.example;
                    import org.springframework.web.bind.annotation.*;
                    @RestController
                    public class MyController {
                        private final MyService service = new MyService();
                        @GetMapping("/api/a")
                        public void apiA() {
                            service.commonMethod();
                        }
                        @GetMapping("/api/b")
                        public void apiB() {
                            service.commonMethod();
                        }
                    }
                    """, T1, "c1");
            writeJava(git, "src/main/java/com/example/MyService.java", """
                    package com.example;
                    public class MyService {
                        public void commonMethod() {
                            int x = 1;
                            int y = 2;
                        }
                    }
                    """, T1, "c2");
        }
    }

}
