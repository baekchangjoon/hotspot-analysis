package io.github.baekchangjoon.hotspotanalysis;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Boot full-context end-to-end test. Loads the same beans the CLI
 * uses in production, then dispatches {@code analyze} args through
 * {@link HotspotApplication#run(String...)} and asserts on the exit code
 * and output files.
 *
 * <p>This is the "happy path" companion to the more focused
 * {@code AnalyzeCommandTest} (which uses manually-constructed collaborators).
 * It validates that Spring DI wires every component in T1–T10 together
 * correctly.</p>
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class HotspotCliE2ETest {

    @Autowired
    HotspotApplication application;

    @Test
    @DisplayName("hotspot analyze --config <file> runs end-to-end with the real Spring context")
    void shouldRunAnalyzeEndToEndViaSpring(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Path outDir = tempDir.resolve("out");
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));

        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava(git, "src/main/java/com/example/Hot.java",
                    "package com.example; public class Hot { void m() {} }",
                    Instant.parse("2026-01-10T10:00:00Z"));
            writeJava(git, "src/main/java/com/example/Hot.java",
                    "package com.example; public class Hot {\n  void m() { int x = 1; }\n}",
                    Instant.parse("2026-01-11T10:00:00Z"));
            writeJava(git, "src/main/java/com/example/Cold.java",
                    "package com.example; public class Cold { void n() {} }",
                    Instant.parse("2026-01-12T10:00:00Z"));
        }

        Path configFile = tempDir.resolve("hotspot.yml");
        Files.writeString(configFile, """
                analysis:
                  target:
                    type: local-git
                    path: %s
                  window:
                    days: 3650
                  scope:
                    granularity:
                      - file
                      - method
                    include:
                      - "**/*.java"
                output:
                  formats:
                    - CSV
                    - YAML
                    - MD
                    - HTML
                  path: %s
                  topN: 0
                """.formatted(repoRoot, outDir));

        application.run("analyze", "--config", configFile.toString(), "--quiet");

        assertThat(application.getExitCode()).isZero();
        assertThat(Files.exists(outDir.resolve("file_hotspots.csv"))).isTrue();
        assertThat(Files.exists(outDir.resolve("method_hotspots.csv"))).isTrue();
        assertThat(Files.exists(outDir.resolve("hotspots.yml"))).isTrue();
        assertThat(Files.exists(outDir.resolve("hotspots.md"))).isTrue();
        assertThat(Files.exists(outDir.resolve("hotspots.html"))).isTrue();

        String csv = Files.readString(outDir.resolve("file_hotspots.csv"));
        assertThat(csv).contains("src/main/java/com/example/Hot.java");
        assertThat(csv).contains("src/main/java/com/example/Cold.java");
        // Unified model: both empty-body files have compositeScore=0, so ties
        // break alphabetically by path. Just confirm both rows are present.
        List<String> dataLines = csv.lines().skip(1).toList();
        assertThat(dataLines).hasSize(2);

        String html = Files.readString(outDir.resolve("hotspots.html"));
        assertThat(html).startsWith("<!DOCTYPE html>");
        assertThat(html).contains("Hot.java");
        assertThat(html).contains("Cold.java");
        assertThat(html).contains("Hotspot Analysis Report");
    }

    @Test
    @DisplayName("analyze emits all seven metrics in every report format (unified scoring model)")
    void analyzeEmitsAllSevenMetricsInEveryReport(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Path outDir = tempDir.resolve("out");
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));

        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava(git, "src/main/java/com/example/Hot.java",
                    """
                    package com.example;
                    public class Hot {
                      void m(int x) {
                        if (x > 0) {
                          for (int i = 0; i < x; i++) {}
                        }
                      }
                    }
                    """,
                    Instant.parse("2026-05-20T10:00:00Z"));
            writeJava(git, "src/main/java/com/example/Cold.java",
                    "package com.example; public class Cold { void n() {} }",
                    Instant.parse("2026-01-01T10:00:00Z"));
        }

        Path configFile = tempDir.resolve("hotspot.yml");
        Files.writeString(configFile, """
                analysis:
                  target:
                    type: local-git
                    path: %s
                  window:
                    since: 2016-05-23
                    until: 2026-05-23
                  scope:
                    granularity:
                      - file
                      - method
                    include:
                      - "**/*.java"
                  scoring:
                    decayHalfLifeDays: 90
                output:
                  formats:
                    - CSV
                    - YAML
                    - MD
                    - HTML
                  path: %s
                  topN: 0
                """.formatted(repoRoot, outDir));

        application.run("analyze", "--config", configFile.toString(), "--quiet");

        assertThat(application.getExitCode()).isZero();

        // CSV: assert exact canonical 7-metric header
        String csv = Files.readString(outDir.resolve("file_hotspots.csv"));
        String header = csv.lines().findFirst().orElseThrow();
        assertThat(header).isEqualTo(
                "simple_rank,composite_rank,path,loc,revisions,simple_score,recency_decay,"
                + "cognitive_complexity,coverage_multiplier,composite_score");

        // YAML: camelCase canonical keys
        assertThat(Files.readString(outDir.resolve("hotspots.yml")))
                .contains("compositeScore").contains("simpleScore");

        // Markdown: human-readable column headers
        assertThat(Files.readString(outDir.resolve("hotspots.md")))
                .contains("Composite Score").contains("Simple Score");

        // HTML: human-readable headers; no legacy "Scoring formula" meta-row
        assertThat(Files.readString(outDir.resolve("hotspots.html")))
                .contains("Composite Score")
                .contains("Simple Score")
                .doesNotContain("Scoring formula");
    }

    @Test
    @DisplayName("analyze with jacocoReportPath applies coverage multiplier (not neutral 1)")
    void shouldApplyCoverageMultiplierFromJacocoReport(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Path outDir = tempDir.resolve("out");
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));

        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava(git, "src/main/java/com/example/Hot.java",
                    "package com.example; public class Hot { void m() {} }",
                    Instant.parse("2026-01-10T10:00:00Z"));
            writeJava(git, "src/main/java/com/example/Hot.java",
                    "package com.example; public class Hot {\n  void m() { int x = 1; }\n}",
                    Instant.parse("2026-01-11T10:00:00Z"));
        }

        // Write a minimal valid jacoco.xml with 50% coverage for Hot.java
        // (line 3 covered, line 4 not covered → coverage = 0.5 → multiplier = 1/(0.5+0.1) ≈ 1.6667)
        Path jacocoXml = tempDir.resolve("jacoco.xml");
        Files.writeString(jacocoXml, """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
                <report name="t">
                  <package name="com/example">
                    <sourcefile name="Hot.java">
                      <line nr="3" mi="0" ci="2"/>
                      <line nr="4" mi="2" ci="0"/>
                    </sourcefile>
                  </package>
                </report>
                """);

        Path configFile = tempDir.resolve("hotspot.yml");
        Files.writeString(configFile, """
                analysis:
                  target:
                    type: local-git
                    path: %s
                  window:
                    days: 3650
                  scope:
                    granularity:
                      - file
                    include:
                      - "**/*.java"
                  jacocoReportPath: %s
                output:
                  formats:
                    - CSV
                  path: %s
                  topN: 0
                """.formatted(repoRoot, jacocoXml, outDir));

        application.run("analyze", "--config", configFile.toString(), "--quiet");

        assertThat(application.getExitCode()).isZero();

        String csv = Files.readString(outDir.resolve("file_hotspots.csv"));
        // Find the Hot.java row and verify coverage_multiplier is NOT "1" (neutral default)
        // Coverage=0.5 → multiplier=1/(0.5+0.1)≈1.6667, so the value should differ from "1"
        String hotRow = csv.lines()
                .filter(line -> line.contains("Hot.java"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Hot.java row not found in CSV"));

        // coverage_multiplier is at index 8 (0-based) in:
        // simple_rank,composite_rank,path,loc,revisions,simple_score,recency_decay,cognitive_complexity,coverage_multiplier,composite_score
        String[] cols = hotRow.split(",", -1);
        String coverageMultiplier = cols[8];
        assertThat(coverageMultiplier).isNotEqualTo("1");
    }

    @Test
    @DisplayName("analyze with scoring.excludeCoverage=true emits line_coverage at rightmost column and drops the multiplier from composite")
    void shouldEmitLineCoverageWhenExcludeCoverage(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Path outDir = tempDir.resolve("out");
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));

        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava(git, "src/main/java/com/example/Hot.java",
                    """
                    package com.example;
                    public class Hot {
                      void m(int x) { if (x > 0) { int y = x + 1; } }
                    }
                    """,
                    Instant.parse("2026-05-20T10:00:00Z"));
        }

        // Two-line JaCoCo report: line 3 covered, line 4 not. coverage = 0.5.
        Path jacocoXml = tempDir.resolve("jacoco.xml");
        Files.writeString(jacocoXml, """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
                <report name="t">
                  <package name="com/example">
                    <sourcefile name="Hot.java">
                      <line nr="3" mi="0" ci="2"/>
                      <line nr="4" mi="2" ci="0"/>
                    </sourcefile>
                  </package>
                </report>
                """);

        Path configFile = tempDir.resolve("hotspot.yml");
        Files.writeString(configFile, """
                analysis:
                  target:
                    type: local-git
                    path: %s
                  window:
                    since: 2016-05-23
                    until: 2026-05-23
                  scope:
                    granularity:
                      - file
                      - method
                    include:
                      - "**/*.java"
                  scoring:
                    decayHalfLifeDays: 90
                    excludeCoverage: true
                  jacocoReportPath: %s
                output:
                  formats:
                    - CSV
                    - YAML
                    - MD
                    - HTML
                  path: %s
                  topN: 0
                """.formatted(repoRoot, jacocoXml, outDir));

        application.run("analyze", "--config", configFile.toString(), "--quiet");
        assertThat(application.getExitCode()).isZero();

        // CSV header: line_coverage replaces coverage_multiplier at rightmost.
        String csv = Files.readString(outDir.resolve("file_hotspots.csv"));
        String header = csv.lines().findFirst().orElseThrow();
        assertThat(header).isEqualTo(
                "simple_rank,composite_rank,path,loc,revisions,simple_score,recency_decay,"
                + "cognitive_complexity,composite_score,line_coverage");
        String hotRow = csv.lines()
                .filter(line -> line.contains("Hot.java"))
                .findFirst().orElseThrow();
        // Rightmost cell is the raw coverage percentage, not 1.6667.
        assertThat(hotRow).endsWith(",50.0%");

        // YAML: lineCoverage at end, no coverageMultiplier.
        String yaml = Files.readString(outDir.resolve("hotspots.yml"));
        assertThat(yaml).contains("lineCoverage:");
        assertThat(yaml).doesNotContain("coverageMultiplier:");

        // Markdown: rightmost column header is Line Coverage.
        String md = Files.readString(outDir.resolve("hotspots.md"));
        assertThat(md).contains("Line Coverage |");
        assertThat(md).doesNotContain("Coverage Multiplier");

        // HTML: header order Composite Score → Line Coverage.
        String html = Files.readString(outDir.resolve("hotspots.html"));
        int compositeIdx = html.indexOf(">Composite Score<");
        int lineCovIdx = html.indexOf(">Line Coverage<");
        assertThat(compositeIdx).isPositive();
        assertThat(lineCovIdx).isGreaterThan(compositeIdx);
    }

    @Test
    @DisplayName("analyze with scoring.excludeCoverage=true and no JaCoCo report renders N/A at line_coverage column")
    void shouldEmitNAForLineCoverageWhenJacocoAbsent(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Path outDir = tempDir.resolve("out");
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));

        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava(git, "src/main/java/com/example/Hot.java",
                    "package com.example; public class Hot { void m() {} }",
                    Instant.parse("2026-05-20T10:00:00Z"));
        }

        Path configFile = tempDir.resolve("hotspot.yml");
        Files.writeString(configFile, """
                analysis:
                  target:
                    type: local-git
                    path: %s
                  window:
                    since: 2016-05-23
                    until: 2026-05-23
                  scope:
                    granularity:
                      - file
                    include:
                      - "**/*.java"
                  scoring:
                    excludeCoverage: true
                output:
                  formats:
                    - CSV
                  path: %s
                  topN: 0
                """.formatted(repoRoot, outDir));

        application.run("analyze", "--config", configFile.toString(), "--quiet");
        assertThat(application.getExitCode()).isZero();

        String csv = Files.readString(outDir.resolve("file_hotspots.csv"));
        String hotRow = csv.lines()
                .filter(line -> line.contains("Hot.java"))
                .findFirst().orElseThrow();
        assertThat(hotRow).endsWith(",N/A");
    }

    @Test
    @DisplayName("hotspot init --output <file> writes a working sample (exit 0)")
    void shouldInitConfig(@TempDir Path tempDir) {
        Path target = tempDir.resolve("hotspot.yml");

        application.run("init", "--output", target.toString());

        assertThat(application.getExitCode()).isZero();
        assertThat(Files.exists(target)).isTrue();
    }

    @AfterEach
    void cleanupCwdReport() throws IOException {
        deleteRecursively(Path.of("hotspot-report"));
    }

    @Test
    @DisplayName("zero-config: analyze <repo> with no --config runs end-to-end (single module)")
    void zeroConfigSingleModuleViaPositional(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava(git, "src/main/java/com/example/Hot.java",
                    "package com.example; public class Hot { void m() { int x = 1; } }",
                    Instant.now().minus(Duration.ofDays(1)));
        }

        application.run("analyze", repoRoot.toString(), "--quiet");

        assertThat(application.getExitCode()).isZero();
        assertThat(Files.exists(Path.of("hotspot-report").resolve("file_hotspots.csv"))).isTrue();
    }

    @Test
    @DisplayName("zero-config: multi-module repo is detected and run succeeds")
    void zeroConfigMultiModule(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve("moduleA/src/main/java/com/example"));
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava(git, "moduleA/src/main/java/com/example/Hot.java",
                    "package com.example; public class Hot { void m() { int x = 1; } }",
                    Instant.now().minus(Duration.ofDays(1)));
        }

        application.run("analyze", repoRoot.toString(), "--quiet");

        assertThat(application.getExitCode()).isZero();
    }

    @Test
    @DisplayName("zero-config: not a git work tree exits 1 with a clear hint")
    void zeroConfigNotAGitWorkTree(@TempDir Path tempDir) throws Exception {
        Path notARepo = tempDir.resolve("plain");
        Files.createDirectories(notARepo.resolve("src/main/java"));

        application.run("analyze", notARepo.toString(), "--quiet");

        assertThat(application.getExitCode()).isEqualTo(1);
    }

    @Test
    @DisplayName("zero-config: git repo with no Java sources exits 1 with a hint")
    void zeroConfigNoJavaSources(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot);
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava(git, "README.md", "hi", Instant.now().minus(Duration.ofDays(1)));
        }

        application.run("analyze", repoRoot.toString(), "--quiet");

        assertThat(application.getExitCode()).isEqualTo(1);
    }

    @Test
    @DisplayName("zero-config: --print-config prints reloadable YAML and writes no report")
    void zeroConfigPrintConfigRoundTrips(@TempDir Path tempDir, CapturedOutput output) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava(git, "src/main/java/com/example/Hot.java",
                    "package com.example; public class Hot { void m() { int x = 1; } }",
                    Instant.now().minus(Duration.ofDays(1)));
        }
        deleteRecursively(Path.of("hotspot-report"));

        application.run("analyze", repoRoot.toString(), "--print-config");

        assertThat(application.getExitCode()).isZero();
        assertThat(output.getOut()).contains("analysis:").contains("LOCAL_GIT");
        assertThat(Files.exists(Path.of("hotspot-report"))).isFalse();
    }

    @Test
    @DisplayName("zero-config: --config and a positional path are mutually exclusive")
    void zeroConfigMutuallyExclusive(@TempDir Path tempDir) throws Exception {
        Path cfg = tempDir.resolve("hotspot.yml");
        Files.writeString(cfg, "analysis: {}\noutput: {}\n");
        application.run("analyze", "--config", cfg.toString(), tempDir.toString());
        assertThat(application.getExitCode()).isEqualTo(1);
    }

    @Test
    @DisplayName("zero-config: JaCoCo report at the Gradle path is auto-detected")
    void zeroConfigDetectsJacoco(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        Files.createDirectories(repoRoot.resolve("build/reports/jacoco/test"));
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava(git, "src/main/java/com/example/Hot.java",
                    "package com.example;\npublic class Hot {\n  void m(int x) { if (x>0) { int y=x+1; } }\n}",
                    Instant.now().minus(Duration.ofDays(1)));
        }
        Files.writeString(repoRoot.resolve("build/reports/jacoco/test/jacocoTestReport.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
                <report name="t">
                  <package name="com/example">
                    <sourcefile name="Hot.java">
                      <line nr="3" mi="0" ci="2"/>
                      <line nr="4" mi="2" ci="0"/>
                    </sourcefile>
                  </package>
                </report>
                """);

        application.run("analyze", repoRoot.toString(), "--quiet");
        assertThat(application.getExitCode()).isZero();
        String csv = Files.readString(Path.of("hotspot-report").resolve("file_hotspots.csv"));
        String hotRow = csv.lines().filter(l -> l.contains("Hot.java")).findFirst().orElseThrow();
        assertThat(hotRow.split(",", -1)[8]).isNotEqualTo("1");
    }

    @Test
    @DisplayName("zero-config: a linked git worktree (.git is a file) is accepted end-to-end")
    void zeroConfigLinkedWorktree(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        try (Git git = Git.init().setDirectory(repoRoot.toFile()).call()) {
            writeJava(git, "src/main/java/com/example/Hot.java",
                    "package com.example; public class Hot { void m() { int x = 1; } }",
                    Instant.now().minus(Duration.ofDays(1)));
        }
        Path wt = tempDir.resolve("wt");
        int rc = new ProcessBuilder("git", "-C", repoRoot.toString(),
                "worktree", "add", wt.toString(), "HEAD")
                .inheritIO().start().waitFor();
        assertThat(rc).isZero();
        assertThat(Files.isRegularFile(wt.resolve(".git"))).isTrue();

        application.run("analyze", wt.toString(), "--quiet");

        assertThat(application.getExitCode()).isZero();
        assertThat(Files.exists(Path.of("hotspot-report").resolve("file_hotspots.csv"))).isTrue();
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        }
    }

    private static void writeJava(Git git, String relativePath, String body, Instant ts)
            throws Exception {
        Path workTree = git.getRepository().getWorkTree().toPath();
        Path file = workTree.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, body);
        git.add().addFilepattern(relativePath).call();
        PersonIdent ident = new PersonIdent(
                "alice", "alice@example.com",
                Date.from(ts), TimeZone.getTimeZone("UTC"));
        git.commit().setAuthor(ident).setCommitter(ident).setMessage("change").call();
    }
}
