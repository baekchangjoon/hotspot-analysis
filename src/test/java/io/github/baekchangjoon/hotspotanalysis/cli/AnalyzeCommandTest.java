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
import io.github.baekchangjoon.hotspotanalysis.output.HtmlOutputWriter;
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
                new CsvOutputWriter(), new YamlOutputWriter(), new MarkdownOutputWriter(), new HtmlOutputWriter()));
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
        Files.writeString(configFile, "analysis: {}\n");

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

    @Test
    @DisplayName("--strict returns exit 3 when no commits fall inside the window")
    void shouldFailStrictWhenNoCommitsInWindow() throws Exception {
        Path configFile = writeConfigWithAbsoluteWindow(
                repoRoot.toString(), outputDir.toString(),
                List.of("CSV"), "**/*.java",
                "2020-01-01", "2020-12-31");

        StringWriter errWriter = new StringWriter();
        CommandLine cli = new CommandLine(command);
        cli.setErr(new PrintWriter(errWriter));

        int exit = cli.execute("--config", configFile.toString(), "--strict");

        assertThat(exit).isEqualTo(3);
        String stderr = errWriter.toString();
        assertThat(stderr).contains("--strict");
        assertThat(stderr).contains("Commits matching window: 0");
    }

    @Test
    @DisplayName("--strict returns exit 3 when no Java files match scope.include")
    void shouldFailStrictWhenNoFilesMatch() throws Exception {
        Path configFile = writeConfigWithAbsoluteWindow(
                repoRoot.toString(), outputDir.toString(),
                List.of("CSV"), "**/non-existent-folder/**/*.java",
                "2020-01-01", "2026-12-31");

        StringWriter errWriter = new StringWriter();
        CommandLine cli = new CommandLine(command);
        cli.setErr(new PrintWriter(errWriter));

        int exit = cli.execute("--config", configFile.toString(), "--strict");

        assertThat(exit).isEqualTo(3);
        String stderr = errWriter.toString();
        assertThat(stderr).contains("Files matching scope:   0");
        assertThat(stderr).contains("scope.include");
    }

    @Test
    @DisplayName("--strict still exits 0 when the analysis produced non-empty results")
    void shouldPassStrictWhenResultIsHealthy() throws Exception {
        Path configFile = writeConfig("local-git", repoRoot.toString(), outputDir.toString(),
                List.of("CSV"));

        StringWriter sw = new StringWriter();
        CommandLine cli = new CommandLine(command);
        cli.setOut(new PrintWriter(sw));

        int exit = cli.execute("--config", configFile.toString(), "--strict");

        assertThat(exit).isZero();
    }

    @Test
    @DisplayName("without --strict, empty result still exits 0 (backward compatible)")
    void shouldExitZeroWithoutStrictOnEmptyResult() throws Exception {
        Path configFile = writeConfigWithAbsoluteWindow(
                repoRoot.toString(), outputDir.toString(),
                List.of("CSV"), "**/*.java",
                "2020-01-01", "2020-12-31");

        StringWriter sw = new StringWriter();
        CommandLine cli = new CommandLine(command);
        cli.setOut(new PrintWriter(sw));

        int exit = cli.execute("--config", configFile.toString());

        assertThat(exit).isZero();
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

    private Path writeConfigWithAbsoluteWindow(String repoPath, String outPath,
                                               List<String> formats, String includeGlob,
                                               String since, String until) throws Exception {
        String formatsYaml = formats.stream()
                .map(f -> "    - " + f)
                .reduce((a, b) -> a + "\n" + b).orElse("");
        String yaml = """
                analysis:
                  target:
                    type: local-git
                    path: %s
                  window:
                    since: "%s"
                    until: "%s"
                  scope:
                    granularity:
                      - file
                      - method
                    include:
                      - "%s"
                output:
                  formats:
                %s
                  path: %s
                  topN: 0
                """.formatted(repoPath, since, until, includeGlob, formatsYaml, outPath);
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

    @Test
    @DisplayName("runs end-to-end with API analysis enabled, creates api hotspots reports, exits 0")
    void shouldRunEndToEndWithApiAnalysis() throws Exception {
        Path srcDir = repoRoot.resolve("src/main/java");
        Path comExample = srcDir.resolve("com/example");
        Path springDir = srcDir.resolve("org/springframework/web/bind/annotation");
        Files.createDirectories(springDir);
        Files.createDirectories(comExample);

        // Write Spring annotations
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

        // Write Controller and Service files into git repo, and commit them
        try (Git git = Git.open(repoRoot.toFile())) {
            writeJava(git, "src/main/java/com/example/MyService.java",
                    "package com.example; public class MyService { public void serve() {} }",
                    Instant.parse("2026-01-10T10:00:00Z"));
            writeJava(git, "src/main/java/com/example/MyController.java",
                    """
                    package com.example;
                    import org.springframework.web.bind.annotation.*;
                    @RestController
                    public class MyController {
                        private MyService service = new MyService();
                        @GetMapping("/test")
                        public void getTest() {
                            service.serve();
                        }
                    }
                    """,
                    Instant.parse("2026-01-11T10:00:00Z"));
        }

        // Compile them dynamically
        Path buildClasses = repoRoot.resolve("build/classes/java/main");
        compileJavaFiles(srcDir, buildClasses);

        // Write config
        Path configFile = writeApiConfig(
                repoRoot.toString(),
                outputDir.toString(),
                List.of("CSV", "YAML", "MD", "HTML"),
                "both",
                List.of("build/classes/java/main")
        );

        StringWriter sw = new StringWriter();
        CommandLine cli = new CommandLine(command);
        cli.setOut(new PrintWriter(sw));

        int exit = cli.execute("--config", configFile.toString());

        assertThat(exit).isZero();

        // Combined outputs
        assertThat(outputDir.resolve("file_hotspots.csv")).exists();
        assertThat(outputDir.resolve("method_hotspots.csv")).exists();
        assertThat(outputDir.resolve("hotspots.yml")).exists();
        assertThat(outputDir.resolve("hotspots.md")).exists();
        assertThat(outputDir.resolve("hotspots.html")).exists();

        // Standalone API outputs
        assertThat(outputDir.resolve("api_hotspots.csv")).exists();
        assertThat(outputDir.resolve("shared_components.csv")).exists();
        assertThat(outputDir.resolve("api_report.yml")).exists();
        assertThat(outputDir.resolve("api_report.md")).exists();
        assertThat(outputDir.resolve("api_report.html")).exists();

        String combinedHtml = Files.readString(outputDir.resolve("hotspots.html"));
        assertThat(combinedHtml).contains("REST API Hotspots");
        assertThat(combinedHtml).contains("Shared Components");
        assertThat(combinedHtml).contains("/test");
        // API and Shared sections now emit FQCN, Method, Parameters in separate cells
        assertThat(combinedHtml).contains("com.example.MyController");
        assertThat(combinedHtml).contains("getTest");
        assertThat(combinedHtml).contains("com.example.MyService");
        assertThat(combinedHtml).contains("serve");

        String standaloneHtml = Files.readString(outputDir.resolve("api_report.html"));
        assertThat(standaloneHtml).contains("REST API Hotspots");
        assertThat(standaloneHtml).contains("Shared Components");
        assertThat(standaloneHtml).doesNotContain("File Hotspots");
        assertThat(standaloneHtml).doesNotContain("Method Hotspots");
    }

    private Path writeApiConfig(String repoPath, String outPath, List<String> formats, String apiLayout, List<String> classpathDirs) throws Exception {
        String formatsYaml = formats.stream()
                .map(f -> "    - " + f)
                .reduce((a, b) -> a + "\n" + b).orElse("");
        String classpathYaml = classpathDirs.stream()
                .map(d -> "      - \"" + d + "\"")
                .reduce((a, b) -> a + "\n" + b).orElse("");
        String yaml = """
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
                  apiAnalysis:
                    enabled: true
                    sharedComponentMode: both
                    classpathDirectories:
                %s
                output:
                  formats:
                %s
                  path: %s
                  topN: 0
                  apiLayout: %s
                """.formatted(repoPath, classpathYaml, formatsYaml, outPath, apiLayout);
        Path config = tempDir.resolve("hotspot-api.yml");
        Files.writeString(config, yaml);
        return config;
    }

    private void compileJavaFiles(Path srcDir, Path destDir) throws Exception {
        Files.createDirectories(destDir);
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        javax.tools.StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

        List<java.io.File> files = Files.walk(srcDir)
                .filter(Files::isRegularFile)
                .map(Path::toFile)
                .toList();

        String classpath = System.getProperty("java.class.path");
        List<String> options = List.of("-d", destDir.toString(), "-classpath", classpath);
        Iterable<? extends javax.tools.JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(files);

        javax.tools.DiagnosticCollector<javax.tools.JavaFileObject> diagnostics = new javax.tools.DiagnosticCollector<>();
        javax.tools.JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
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
