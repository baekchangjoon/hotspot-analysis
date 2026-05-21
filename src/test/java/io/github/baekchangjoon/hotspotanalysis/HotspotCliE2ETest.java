package io.github.baekchangjoon.hotspotanalysis;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

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
                  scoring:
                    formula: simple
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
        // Hot is touched twice, Cold once → Hot ranks first.
        List<String> dataLines = csv.lines().skip(1).toList();
        assertThat(dataLines).hasSize(2);
        assertThat(dataLines.get(0)).contains("Hot.java");
        assertThat(dataLines.get(1)).contains("Cold.java");

        String html = Files.readString(outDir.resolve("hotspots.html"));
        assertThat(html).startsWith("<!DOCTYPE html>");
        assertThat(html).contains("Hot.java");
        assertThat(html).contains("Cold.java");
        assertThat(html).contains("Hotspot Analysis Report");
    }

    @Test
    @DisplayName("hotspot init --output <file> writes a working sample (exit 0)")
    void shouldInitConfig(@TempDir Path tempDir) {
        Path target = tempDir.resolve("hotspot.yml");

        application.run("init", "--output", target.toString());

        assertThat(application.getExitCode()).isZero();
        assertThat(Files.exists(target)).isTrue();
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
