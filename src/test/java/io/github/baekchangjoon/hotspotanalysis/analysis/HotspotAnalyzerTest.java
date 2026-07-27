package io.github.baekchangjoon.hotspotanalysis.analysis;

import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.FileHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.MethodHotspot;
import io.github.baekchangjoon.hotspotanalysis.config.AnalysisConfig;
import io.github.baekchangjoon.hotspotanalysis.config.AnalysisSection;
import io.github.baekchangjoon.hotspotanalysis.config.OutputConfig;
import io.github.baekchangjoon.hotspotanalysis.config.ScopeConfig;
import io.github.baekchangjoon.hotspotanalysis.config.ScoringConfig;
import io.github.baekchangjoon.hotspotanalysis.config.TargetConfig;
import io.github.baekchangjoon.hotspotanalysis.config.WindowConfig;
import io.github.baekchangjoon.hotspotanalysis.parser.JavaSourceParser;
import io.github.baekchangjoon.hotspotanalysis.vcs.VcsProviderFactory;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HotspotAnalyzerTest {

    private static final Instant T1 = Instant.parse("2026-01-10T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-11T10:00:00Z");
    private static final Instant T3 = Instant.parse("2026-01-12T10:00:00Z");
    private static final Instant T4 = Instant.parse("2026-01-13T10:00:00Z");

    @TempDir
    Path tempDir;

    Path repoRoot;
    HotspotAnalyzer analyzer;

    @BeforeEach
    void setUpAnalyzer() throws Exception {
        analyzer = new HotspotAnalyzer(
                new VcsProviderFactory(),
                new JavaSourceCollector(),
                new JavaSourceParser(),
                new RevisionsCalculator(),
                new LocCalculator(),
                new HotspotScoreCalculator());
        repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
    }

    @Test
    @DisplayName("ranks the most-edited file higher than the rarely-edited file")
    void shouldRankMostEditedFileFirst() throws Exception {
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            // Hot.java: edited 3 times
            writeJava("src/main/java/com/example/Hot.java",
                    "package com.example; public class Hot { void m1() {} }",
                    git, T1, "c1");
            writeJava("src/main/java/com/example/Hot.java",
                    "package com.example; public class Hot {\n  void m1() { int x = 1; }\n}",
                    git, T2, "c2");
            writeJava("src/main/java/com/example/Hot.java",
                    "package com.example; public class Hot {\n  void m1() { int x = 1; int y = 2; }\n}",
                    git, T3, "c3");

            // Cold.java: only one commit
            writeJava("src/main/java/com/example/Cold.java",
                    "package com.example; public class Cold { void m2() {} }",
                    git, T4, "c4");
        }

        AnalysisResult result = analyzer.analyze(configFor(repoRoot, 0));

        // With the unified scoring model both files have cognitiveComplexity 0
        // (single empty-body methods) → composite scores tie at 0.0 → ties
        // break alphabetically by path. Verify both files are present and that
        // Hot.java carries more revisions.
        assertThat(result.fileHotspots()).extracting(FileHotspot::path)
                .containsExactlyInAnyOrder(
                        "src/main/java/com/example/Hot.java",
                        "src/main/java/com/example/Cold.java");
        FileHotspot hot = result.fileHotspots().stream()
                .filter(h -> h.path().endsWith("Hot.java")).findFirst().orElseThrow();
        FileHotspot cold = result.fileHotspots().stream()
                .filter(h -> h.path().endsWith("Cold.java")).findFirst().orElseThrow();
        assertThat(hot.revisions()).isEqualTo(3);
        assertThat(cold.revisions()).isEqualTo(1);
        assertThat(hot.simpleScore()).isGreaterThan(cold.simpleScore());
    }

    @Test
    @DisplayName("simpleScore equals revisions * loc; all seven metric fields are populated")
    void shouldComputeAllSevenMetricsForTopFile() throws Exception {
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            String source = "package com.example;\npublic class Hot {\n  void m() {}\n}\n";
            writeJava("src/main/java/com/example/Hot.java", source, git, T1, "c1");
            writeJava("src/main/java/com/example/Hot.java",
                    source.replace("void m() {}", "void m() { int x=1; }"),
                    git, T2, "c2");
        }

        AnalysisResult result = analyzer.analyze(configFor(repoRoot, 0));

        FileHotspot top = result.fileHotspots().get(0);
        assertThat(top.revisions()).isEqualTo(2);
        assertThat(top.loc()).isEqualTo(4);  // 4 newline-terminated lines
        assertThat(top.simpleScore()).isEqualTo(8.0);

        // All four input factors and the composite score are populated.
        assertThat(top.recencyDecay()).isGreaterThanOrEqualTo(0.0);
        assertThat(top.cognitiveComplexity()).isGreaterThanOrEqualTo(0.0);
        assertThat(top.coverageMultiplier()).isEqualTo(1.0); // no jacoco → 1.0
        assertThat(top.compositeScore())
                .isEqualTo(top.cognitiveComplexity() * top.recencyDecay() * top.coverageMultiplier());

        // The list is sorted by compositeScore DESC.
        if (result.fileHotspots().size() >= 2) {
            assertThat(result.fileHotspots().get(0).compositeScore())
                    .isGreaterThanOrEqualTo(result.fileHotspots().get(1).compositeScore());
        }
    }

    @Test
    @DisplayName("limits results to topN when set")
    void shouldApplyTopNLimit() throws Exception {
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            for (int i = 1; i <= 5; i++) {
                writeJava("src/main/java/com/example/F" + i + ".java",
                        "package com.example; public class F" + i + " { void m() {} }",
                        git, T1.plusSeconds(i), "init " + i);
            }
        }

        AnalysisResult result = analyzer.analyze(configFor(repoRoot, 2));

        assertThat(result.fileHotspots()).hasSize(2);
    }

    @Test
    @DisplayName("produces method-level hotspots with correct line ranges and LOC")
    void shouldEmitMethodHotspots() throws Exception {
        String source = """
                package com.example;
                public class M {
                    public void a() {
                        int x = 1;
                    }
                    public void b() {
                        int y = 2;
                        int z = 3;
                    }
                }
                """;
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava("src/main/java/com/example/M.java", source, git, T1, "c1");
        }

        AnalysisResult result = analyzer.analyze(configFor(repoRoot, 0));

        assertThat(result.methodHotspots()).hasSize(2);
        assertThat(result.methodHotspots()).extracting(h -> h.signature().methodName())
                .containsExactlyInAnyOrder("a", "b");
        MethodHotspot methodB = result.methodHotspots().stream()
                .filter(h -> h.signature().methodName().equals("b"))
                .findFirst().orElseThrow();
        assertThat(methodB.loc()).isEqualTo(4); // lines 6..9
    }

    @Test
    @DisplayName("excludes files matching the exclude glob")
    void shouldRespectExcludeGlob() throws Exception {
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava("src/main/java/com/example/Foo.java",
                    "package com.example; public class Foo { void m() {} }", git, T1, "c1");
            Files.createDirectories(repoRoot.resolve("src/test/java/com/example"));
            writeJava("src/test/java/com/example/FooTest.java",
                    "package com.example; public class FooTest { void t() {} }", git, T2, "c2");
        }

        AnalysisConfig config = configWith(repoRoot, /*topN*/0,
                List.of("**/*.java"), List.of("**/test/**", "**/*Test.java"));

        AnalysisResult result = analyzer.analyze(config);

        assertThat(result.fileHotspots()).extracting(FileHotspot::path)
                .containsExactly("src/main/java/com/example/Foo.java");
    }

    @Test
    @DisplayName("missing jacocoReportPath → coverage disabled (multiplier 1.0), not a 10x penalty")
    void shouldNotPenalizeWhenJacocoReportMissing() throws Exception {
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava("src/main/java/com/example/Hot.java",
                    "package com.example;\npublic class Hot {\n  int m(int x){ if (x>0) return x; return 0; }\n}\n",
                    git, T1, "c1");
        }

        // jacocoReportPath points at a file that does not exist — the common
        // beginner mistake of copying the sample config verbatim. The analyzer
        // must NOT treat every artifact as 0% covered (multiplier 10); it should
        // disable coverage (multiplier 1.0) and warn.
        TargetConfig target = new TargetConfig(
                TargetConfig.TargetType.LOCAL_GIT, repoRoot.toString(), null);
        WindowConfig window = new WindowConfig(
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), null);
        ScopeConfig scope = new ScopeConfig(
                List.of(ScopeConfig.Granularity.FILE, ScopeConfig.Granularity.METHOD),
                List.of("**/*.java"), List.of());
        AnalysisSection section = new AnalysisSection(
                target, window, scope, new ScoringConfig(),
                null, "build/reports/jacoco/does-not-exist.xml");
        OutputConfig output = new OutputConfig(
                List.of(OutputConfig.OutputFormat.CSV), "./out", 0);

        AnalysisResult result = analyzer.analyze(new AnalysisConfig(section, output));

        assertThat(result.fileHotspots()).isNotEmpty();
        assertThat(result.fileHotspots())
                .allSatisfy(f -> assertThat(f.coverageMultiplier()).isEqualTo(1.0));
        assertThat(result.methodHotspots())
                .allSatisfy(m -> assertThat(m.coverageMultiplier()).isEqualTo(1.0));
    }

    @Test
    @DisplayName("all-zero jacoco report (no executed lines) → coverage disabled (multiplier 1.0), not a 10x penalty")
    void shouldNotPenalizeWhenJacocoReportHasNoCoveredLines() throws Exception {
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava("src/main/java/com/example/Hot.java",
                    "package com.example;\npublic class Hot {\n  int m(int x){ if (x>0) return x; return 0; }\n}\n",
                    git, T1, "c1");
        }

        // A structurally valid report whose lines are ALL ci="0" — what JaCoCo
        // emits when the report task runs without test execution data (stale
        // .exec, tests skipped). Trusting it would inflate every coverage
        // multiplier to its 1/0.1 = 10 maximum; the analyzer must instead
        // disable coverage (multiplier 1.0) and warn.
        Path report = repoRoot.resolve("jacoco-all-zero.xml");
        Files.writeString(report, """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="test">
                    <package name="com/example">
                        <sourcefile name="Hot.java">
                            <line nr="3" mi="4" ci="0" mb="2" cb="0"/>
                        </sourcefile>
                    </package>
                </report>
                """);

        TargetConfig target = new TargetConfig(
                TargetConfig.TargetType.LOCAL_GIT, repoRoot.toString(), null);
        WindowConfig window = new WindowConfig(
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), null);
        ScopeConfig scope = new ScopeConfig(
                List.of(ScopeConfig.Granularity.FILE, ScopeConfig.Granularity.METHOD),
                List.of("**/*.java"), List.of());
        AnalysisSection section = new AnalysisSection(
                target, window, scope, new ScoringConfig(),
                null, report.toString());
        OutputConfig output = new OutputConfig(
                List.of(OutputConfig.OutputFormat.CSV), "./out", 0);

        AnalysisResult result = analyzer.analyze(new AnalysisConfig(section, output));

        assertThat(result.fileHotspots()).isNotEmpty();
        assertThat(result.fileHotspots())
                .allSatisfy(f -> assertThat(f.coverageMultiplier()).isEqualTo(1.0));
        assertThat(result.methodHotspots())
                .allSatisfy(m -> assertThat(m.coverageMultiplier()).isEqualTo(1.0));
    }

    @Test
    @DisplayName("files absent from a partial jacoco report get multiplier 1.0 (not the 10x penalty) + a warning")
    void shouldNotPenalizeFilesAbsentFromPartialJacocoReport() throws Exception {
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava("src/main/java/com/example/Covered.java",
                    "package com.example;\npublic class Covered {\n  int m(int x){ if (x>0) return x; return 0; }\n}\n",
                    git, T1, "c1");
            writeJava("src/main/java/com/example/Absent.java",
                    "package com.example;\npublic class Absent {\n  int n(int x){ if (x>0) return x; return 0; }\n}\n",
                    git, T2, "c2");
        }

        // A partial report (e.g. one module of a multi-module build) mentions
        // only Covered.java. Files it does not mention must NOT be treated as
        // 0% covered — that would inflate their multiplier to 1/0.1 = 10 and
        // silently distort the ranking (exploratory finding F1).
        Path report = repoRoot.resolve("jacoco-partial.xml");
        Files.writeString(report, """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="test">
                    <package name="com/example">
                        <sourcefile name="Covered.java">
                            <line nr="3" mi="0" ci="4" mb="0" cb="0"/>
                        </sourcefile>
                    </package>
                </report>
                """);

        TargetConfig target = new TargetConfig(
                TargetConfig.TargetType.LOCAL_GIT, repoRoot.toString(), null);
        WindowConfig window = new WindowConfig(
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), null);
        ScopeConfig scope = new ScopeConfig(
                List.of(ScopeConfig.Granularity.FILE, ScopeConfig.Granularity.METHOD),
                List.of("**/*.java"), List.of());
        AnalysisSection section = new AnalysisSection(
                target, window, scope, new ScoringConfig(),
                null, report.toString());
        OutputConfig output = new OutputConfig(
                List.of(OutputConfig.OutputFormat.CSV), "./out", 0);

        java.io.ByteArrayOutputStream errCapture = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalErr = System.err;
        System.setErr(new java.io.PrintStream(errCapture));
        AnalysisResult result;
        try {
            result = analyzer.analyze(new AnalysisConfig(section, output));
        } finally {
            System.setErr(originalErr);
        }

        FileHotspot covered = result.fileHotspots().stream()
                .filter(f -> f.path().endsWith("Covered.java")).findFirst().orElseThrow();
        FileHotspot absent = result.fileHotspots().stream()
                .filter(f -> f.path().endsWith("Absent.java")).findFirst().orElseThrow();
        // Covered.java: 1/1 lines covered → multiplier 1/(1.0+0.1) ≈ 0.909
        assertThat(covered.coverageMultiplier()).isCloseTo(1.0 / 1.1, org.assertj.core.data.Offset.offset(1e-9));
        // Absent.java: no data → coverage unknown → multiplier 1.0, coverage null
        assertThat(absent.coverageMultiplier()).isEqualTo(1.0);
        assertThat(absent.lineCoverage()).isNull();
        // Method-level rows of the absent file get the same treatment.
        assertThat(result.methodHotspots().stream()
                .filter(m -> m.filePath().endsWith("Absent.java")))
                .allSatisfy(m -> assertThat(m.coverageMultiplier()).isEqualTo(1.0));
        // One explicit warning so the partial report does not pass silently.
        assertThat(errCapture.toString()).contains("not present in the JaCoCo report");
    }

    @Test
    @DisplayName("an unparseable .java file is skipped with a warning instead of aborting the whole run")
    void shouldSkipUnparseableFileWithWarning() throws Exception {
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava("src/main/java/com/example/Good.java",
                    "package com.example; public class Good { void g() { int a = 1; } }", git, T1, "c1");
            writeJava("src/main/java/com/example/Broken.java",
                    "package com.example; class Broken { void x( { if }}}", git, T2, "c2");
        }

        java.io.ByteArrayOutputStream errCapture = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalErr = System.err;
        System.setErr(new java.io.PrintStream(errCapture));
        AnalysisResult result;
        try {
            result = analyzer.analyze(configFor(repoRoot, 0));
        } finally {
            System.setErr(originalErr);
        }

        assertThat(result.fileHotspots()).extracting(FileHotspot::path)
                .containsExactly("src/main/java/com/example/Good.java");
        assertThat(errCapture.toString()).contains("Broken.java").contains("skip");
    }

    @Test
    @DisplayName("a shallow clone (truncated history) triggers a warning — revisions would be undercounted")
    void shouldWarnOnShallowClone() throws Exception {
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava("src/main/java/com/example/A.java",
                    "package com.example; public class A { void m() { int a = 1; } }", git, T1, "c1");
        }
        // CI checkouts (actions/checkout default depth=1) leave .git/shallow.
        Files.writeString(repoRoot.resolve(".git/shallow"),
                "0123456789012345678901234567890123456789\n");

        java.io.ByteArrayOutputStream errCapture = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalErr = System.err;
        System.setErr(new java.io.PrintStream(errCapture));
        try {
            analyzer.analyze(configFor(repoRoot, 0));
        } finally {
            System.setErr(originalErr);
        }
        assertThat(errCapture.toString()).contains("shallow");
    }

    @Test
    @DisplayName("jacoco line numbers beyond the file's length trigger a stale-report warning")
    void shouldWarnWhenJacocoLinesExceedFileLength() throws Exception {
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava("src/main/java/com/example/Hot.java",
                    "package com.example;\npublic class Hot {\n  int m(int x){ if (x>0) return x; return 0; }\n}\n",
                    git, T1, "c1");
        }
        // Line 100 does not exist in the 4-line file — the report was
        // generated against a different (older/newer) version of the source.
        Path report = repoRoot.resolve("jacoco-stale.xml");
        Files.writeString(report, """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="test">
                    <package name="com/example">
                        <sourcefile name="Hot.java">
                            <line nr="3" mi="0" ci="1" mb="0" cb="0"/>
                            <line nr="100" mi="2" ci="0" mb="0" cb="0"/>
                        </sourcefile>
                    </package>
                </report>
                """);

        TargetConfig target = new TargetConfig(
                TargetConfig.TargetType.LOCAL_GIT, repoRoot.toString(), null);
        WindowConfig window = new WindowConfig(
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), null);
        ScopeConfig scope = new ScopeConfig(
                List.of(ScopeConfig.Granularity.FILE),
                List.of("**/*.java"), List.of());
        AnalysisSection section = new AnalysisSection(
                target, window, scope, new ScoringConfig(),
                null, report.toString());
        OutputConfig output = new OutputConfig(
                List.of(OutputConfig.OutputFormat.CSV), "./out", 0);

        java.io.ByteArrayOutputStream errCapture = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalErr = System.err;
        System.setErr(new java.io.PrintStream(errCapture));
        try {
            analyzer.analyze(new AnalysisConfig(section, output));
        } finally {
            System.setErr(originalErr);
        }
        assertThat(errCapture.toString()).contains("beyond the file's length");
    }

    @Test
    @DisplayName("POSIX file names containing a backslash are reported verbatim and keep their git history")
    void shouldPreserveBackslashInPosixFileNames() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name").toLowerCase().contains("win"));
        // Legal on POSIX; a blanket replace('\\','/') would turn it into a
        // phantom path that no longer matches the git log, producing rows
        // with revisions=0 that contradict the method-level rows.
        String name = "Weird\\Name.java";
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava("src/main/java/com/example/" + name,
                    "package com.example; public class WeirdName { void m() { int a = 1; } }",
                    git, T1, "c1");
        }

        AnalysisResult result = analyzer.analyze(configFor(repoRoot, 0));

        FileHotspot f = result.fileHotspots().stream()
                .filter(h -> h.path().endsWith(name)).findFirst().orElseThrow();
        assertThat(f.revisions()).isEqualTo(1);
    }

    @Test
    @DisplayName("rejects github target with a clear remediation message in Phase 1")
    void shouldRejectGithubTargetInPhase1() {
        AnalysisConfig githubConfig = githubTargetConfig();

        assertThatThrownBy(() -> analyzer.analyze(githubConfig))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("local-git");
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static void writeJava(String relativePath, String body,
                                  Git git, Instant timestamp, String message) throws Exception {
        Path workTree = git.getRepository().getWorkTree().toPath();
        Path file = workTree.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, body);
        git.add().addFilepattern(relativePath).call();
        PersonIdent ident = new PersonIdent(
                "alice", "alice@example.com",
                Date.from(timestamp), TimeZone.getTimeZone("UTC"));
        git.commit().setAuthor(ident).setCommitter(ident).setMessage(message).call();
    }

    private static AnalysisConfig configFor(Path repoRoot, int topN) {
        return configWith(repoRoot, topN, List.of("**/*.java"), List.of());
    }

    private static AnalysisConfig configWith(Path repoRoot,
                                             int topN,
                                             List<String> includes,
                                             List<String> excludes) {
        TargetConfig target = new TargetConfig(
                TargetConfig.TargetType.LOCAL_GIT, repoRoot.toString(), null);
        WindowConfig window = new WindowConfig(
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), null);
        ScopeConfig scope = new ScopeConfig(
                List.of(ScopeConfig.Granularity.FILE, ScopeConfig.Granularity.METHOD),
                includes, excludes);
        ScoringConfig scoring = new ScoringConfig();
        AnalysisSection section = new AnalysisSection(target, window, scope, scoring);
        OutputConfig output = new OutputConfig(
                List.of(OutputConfig.OutputFormat.CSV),
                "./out",
                topN);
        return new AnalysisConfig(section, output);
    }

    private static AnalysisConfig githubTargetConfig() {
        TargetConfig target = new TargetConfig(
                TargetConfig.TargetType.GITHUB,
                null,
                new io.github.baekchangjoon.hotspotanalysis.config.GithubConfig(
                        "owner", "repo", "main", "token"));
        WindowConfig window = new WindowConfig(
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), null);
        ScopeConfig scope = new ScopeConfig(
                List.of(ScopeConfig.Granularity.FILE),
                List.of("**/*.java"), List.of());
        ScoringConfig scoring = new ScoringConfig();
        AnalysisSection section = new AnalysisSection(target, window, scope, scoring);
        OutputConfig output = new OutputConfig(
                List.of(OutputConfig.OutputFormat.CSV), "./out", 0);
        return new AnalysisConfig(section, output);
    }
}
