package io.github.baekchangjoon.hotspotanalysis.cli;

import io.github.baekchangjoon.hotspotanalysis.analysis.HotspotAnalyzer;
import io.github.baekchangjoon.hotspotanalysis.analysis.JavaSourceCollector;
import io.github.baekchangjoon.hotspotanalysis.analysis.HotspotScoreCalculator;
import io.github.baekchangjoon.hotspotanalysis.analysis.LocCalculator;
import io.github.baekchangjoon.hotspotanalysis.analysis.RevisionsCalculator;
import io.github.baekchangjoon.hotspotanalysis.config.ConfigLoader;
import io.github.baekchangjoon.hotspotanalysis.config.EnvironmentVariableResolver;
import io.github.baekchangjoon.hotspotanalysis.output.CsvOutputWriter;
import io.github.baekchangjoon.hotspotanalysis.output.MarkdownOutputWriter;
import io.github.baekchangjoon.hotspotanalysis.output.OutputDispatcher;
import io.github.baekchangjoon.hotspotanalysis.output.YamlOutputWriter;
import io.github.baekchangjoon.hotspotanalysis.parser.JavaSourceParser;
import io.github.baekchangjoon.hotspotanalysis.vcs.VcsProviderFactory;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyzeCommandTest {

    @TempDir
    Path tempDir;

    Path repoRoot;
    Path outputDir;
    AnalyzeCommand command;

    @BeforeEach
    void setUp() throws Exception {
        repoRoot = tempDir.resolve("repo");
        outputDir = tempDir.resolve("out");
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

        OutputDispatcher dispatcher = new OutputDispatcher(List.of(
                new CsvOutputWriter(), new YamlOutputWriter(), new MarkdownOutputWriter()));
        HotspotAnalyzer analyzer = new HotspotAnalyzer(
                new VcsProviderFactory(),
                new JavaSourceCollector(),
                new JavaSourceParser(),
                new RevisionsCalculator(),
                new LocCalculator(),
                new HotspotScoreCalculator());
        command = new AnalyzeCommand(
                new ConfigLoader(new EnvironmentVariableResolver()),
                analyzer, dispatcher);
    }

    @Test
    @DisplayName("runs end-to-end, writes all three formats, exits 0")
    void shouldRunEndToEnd() throws Exception {
        Path configFile = writeConfig("local-git", repoRoot.toString(), outputDir.toString(),
                List.of("CSV", "YAML", "MD"));

        StringWriter sw = new StringWriter();
        CommandLine cli = new CommandLine(command);
        cli.setOut(new PrintWriter(sw));

        int exit = cli.execute("--config", configFile.toString());

        assertThat(exit).isZero();
        assertThat(Files.exists(outputDir.resolve("file_hotspots.csv"))).isTrue();
        assertThat(Files.exists(outputDir.resolve("method_hotspots.csv"))).isTrue();
        assertThat(Files.exists(outputDir.resolve("hotspots.yml"))).isTrue();
        assertThat(Files.exists(outputDir.resolve("hotspots.md"))).isTrue();
        assertThat(sw.toString()).contains("Hotspot analysis complete.");
        assertThat(sw.toString()).contains("Top file:");
    }

    @Test
    @DisplayName("returns exit code 1 when the config file does not exist")
    void shouldFailWhenConfigMissing() {
        StringWriter errWriter = new StringWriter();
        CommandLine cli = new CommandLine(command);
        cli.setErr(new PrintWriter(errWriter));

        int exit = cli.execute("--config", tempDir.resolve("does-not-exist.yml").toString());

        assertThat(exit).isEqualTo(1);
        assertThat(errWriter.toString()).contains("configuration file not found");
    }

    @Test
    @DisplayName("returns exit code 1 when the configuration is invalid")
    void shouldFailWhenConfigInvalid() throws Exception {
        Path configFile = tempDir.resolve("bad.yml");
        Files.writeString(configFile, "analysis:\n  scoring:\n    formula: SIMPLE\n");

        StringWriter errWriter = new StringWriter();
        CommandLine cli = new CommandLine(command);
        cli.setErr(new PrintWriter(errWriter));

        int exit = cli.execute("--config", configFile.toString());

        assertThat(exit).isEqualTo(1);
        assertThat(errWriter.toString()).contains("invalid configuration");
    }

    @Test
    @DisplayName("respects --quiet flag (no summary on stdout)")
    void shouldSuppressSummaryWhenQuiet() throws Exception {
        Path configFile = writeConfig("local-git", repoRoot.toString(), outputDir.toString(),
                List.of("CSV"));

        StringWriter sw = new StringWriter();
        CommandLine cli = new CommandLine(command);
        cli.setOut(new PrintWriter(sw));

        int exit = cli.execute("--config", configFile.toString(), "--quiet");

        assertThat(exit).isZero();
        assertThat(sw.toString()).isEmpty();
    }

    private Path writeConfig(String type, String repoPath, String outPath,
                             List<String> formats) throws Exception {
        String formatsYaml = formats.stream()
                .map(f -> "    - " + f)
                .reduce((a, b) -> a + "\n" + b).orElse("");
        String yaml = """
                analysis:
                  target:
                    type: %s
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
                %s
                  path: %s
                  topN: 0
                """.formatted(type, repoPath, formatsYaml, outPath);
        Path config = tempDir.resolve("hotspot.yml");
        Files.writeString(config, yaml);
        return config;
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
