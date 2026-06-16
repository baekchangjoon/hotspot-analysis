# Zero-Config `analyze` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let `hotspot analyze` run with no config file by auto-detecting an `AnalysisConfig` from the project on disk, while `--config` keeps working unchanged.

**Architecture:** A new `ConfigSynthesizer` (`@Component`, symmetric to `ConfigLoader`) inspects the base path and returns a fully-populated `AnalysisConfig`. `AnalyzeCommand` makes `--config` optional, adds an optional positional `[path]` and a `--print-config` flag, validates flag combinations up front, then dispatches to either `ConfigLoader` (file) or `ConfigSynthesizer` (zero-config). `--print-config` serialises the synthesized config to YAML via a small output-tuned `ConfigSerializer`.

**Tech Stack:** Java 21, Spring Boot, Picocli, Jackson (YAML), JGit, JUnit 5 + AssertJ.

**Spec:** `docs/superpowers/specs/2026-06-16-zero-config-analyze-design.md`

---

## File Structure

**New files:**
- `src/main/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigSynthesisException.java` — unchecked exception for synthesis failures (not a git work tree, no Java sources).
- `src/main/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigSynthesizer.java` — detection rules → `AnalysisConfig`.
- `src/main/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigSerializer.java` — output-tuned YAML mapper; `serialize(AnalysisConfig) -> String`.
- `src/test/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigSynthesizerTest.java` — unit tests for detection rules.
- `src/test/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigSerializerTest.java` — round-trip serialise→`ConfigLoader` test.

**Modified files:**
- `src/main/java/io/github/baekchangjoon/hotspotanalysis/cli/AnalyzeCommand.java` — optional `--config`, positional `[path]`, `--print-config`, preflight validation, dispatch, stderr detection summary.
- `src/test/java/io/github/baekchangjoon/hotspotanalysis/cli/AnalyzeCommandTest.java` — update constructor call to inject the new collaborators; add flag-validation + zero-config cases.
- `src/test/java/io/github/baekchangjoon/hotspotanalysis/HotspotCliE2ETest.java` — outer-loop acceptance tests.
- `README.md`, `README.en.md` — zero-config quick start + limitations.

**Path-resolution facts (verified in repo):**
- `HotspotAnalyzer` resolves a relative `jacocoReportPath` against `repoRoot = target.path` (`HotspotAnalyzer.java:146`). So synthesized `jacocoReportPath` stays **relative**.
- `classpathDirectories` are passed to `CallGraphBuilder.buildCallGraphs(repoRoot, ...)` and resolved against `repoRoot`. So synthesized classpath dirs stay **relative**.
- `target.path` is set to the **absolute** base path.

---

## Task 1: Outer-loop E2E acceptance tests (red)

Author the acceptance tests first. They are expected to FAIL until the feature lands — do not weaken them. They live in the existing Spring-context E2E suite.

**Files:**
- Test: `src/test/java/io/github/baekchangjoon/hotspotanalysis/HotspotCliE2ETest.java`

- [ ] **Step 1: Add the zero-config E2E tests**

Append these methods inside the `HotspotCliE2ETest` class (it already imports `Git`, `PersonIdent`, `@TempDir`, `Files`, `Path`, `Instant`, `Date`, `TimeZone`, AssertJ, and has the `writeJava(Git, String, String, Instant)` helper).

Add imports: `java.io.IOException`, `java.time.Duration`, `java.util.stream.Stream`, `org.junit.jupiter.api.AfterEach`, `org.junit.jupiter.api.extension.ExtendWith`, `org.springframework.boot.test.system.CapturedOutput`, `org.springframework.boot.test.system.OutputCaptureExtension`.

Add the class-level annotation `@ExtendWith(OutputCaptureExtension.class)` next to `@SpringBootTest` so a `CapturedOutput` parameter can be injected.

> **Why dynamic timestamps:** zero-config uses `window.days = 365`, a window relative to *now*. Fixed 2026 dates would fall out of the window in a future year and silently empty the result. All zero-config fixtures commit at `Instant.now().minus(Duration.ofDays(1))`.

> **Why `@AfterEach` cleanup:** zero-config's default `output.path` is `./hotspot-report`, relative to the test process CWD (not `@TempDir`). The hook removes it after every test — including on assertion failure — so tests cannot leak state into one another.

```java
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
        // Default output dir is ./hotspot-report relative to CWD (cleaned by @AfterEach).
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
        // The detected include glob is asserted directly in ConfigSynthesizerTest.
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
            // a repo with a README but no src/main/java; writeJava sets a PersonIdent
            // so the commit does not depend on a global git identity (CI-safe).
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
        // Guarantee a clean precondition regardless of sibling-test ordering.
        deleteRecursively(Path.of("hotspot-report"));

        application.run("analyze", repoRoot.toString(), "--print-config");

        assertThat(application.getExitCode()).isZero();
        // YAML was printed to stdout...
        assertThat(output.getOut()).contains("analysis:").contains("LOCAL_GIT");
        // ...and no report directory was written in print-config mode.
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
        // coverage_multiplier column (index 8) must not be the neutral "1".
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
        // A real linked worktree (so JGit can read commits) needs the git CLI.
        // This is a git-based tool exercised in a git repo, so git is present;
        // a hand-written pointer file would not be an openable work tree.
        int rc = new ProcessBuilder("git", "-C", repoRoot.toString(),
                "worktree", "add", wt.toString(), "HEAD")
                .inheritIO().start().waitFor();
        assertThat(rc).isZero();
        assertThat(Files.isRegularFile(wt.resolve(".git"))).isTrue(); // .git is a FILE here

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
```

- [ ] **Step 2: Run the new E2E tests to verify they fail (red)**

Run: `./gradlew test --tests 'io.github.baekchangjoon.hotspotanalysis.HotspotCliE2ETest' -q`
Expected: the 8 new tests FAIL (current `--config` is required → picocli exit 2 for bare `analyze <path>`; mutual-exclusion / print-config not implemented). Pre-existing E2E tests still PASS.

- [ ] **Step 3: Commit the red acceptance tests**

```bash
git add src/test/java/io/github/baekchangjoon/hotspotanalysis/HotspotCliE2ETest.java
git commit -m "test(e2e): zero-config analyze acceptance tests (red)"
```

---

## Task 2: `ConfigSynthesisException`

**Files:**
- Create: `src/main/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigSynthesisException.java`

- [ ] **Step 1: Create the exception**

```java
package io.github.baekchangjoon.hotspotanalysis.config;

/**
 * Thrown when zero-config synthesis cannot produce a usable configuration —
 * e.g. the base path is not a git work tree, or no Java sources were found.
 * Carries a human-readable, hint-bearing message for direct display on stderr.
 */
public class ConfigSynthesisException extends RuntimeException {
    public ConfigSynthesisException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigSynthesisException.java
git commit -m "feat(config): add ConfigSynthesisException"
```

---

## Task 3: `ConfigSynthesizer`

Builds the full `AnalysisConfig` from a base path. Test-driven, one behavior at a time.

**Files:**
- Create: `src/main/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigSynthesizer.java`
- Test: `src/test/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigSynthesizerTest.java`

- [ ] **Step 1: Write the failing unit tests**

```java
package io.github.baekchangjoon.hotspotanalysis.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigSynthesizerTest {

    private final ConfigSynthesizer synth = new ConfigSynthesizer();

    private static void initGitDir(Path repo) throws Exception {
        Files.createDirectories(repo.resolve(".git"));
    }

    @Test
    @DisplayName("single-module: include glob is src/main/java, target/window/output defaults set")
    void singleModule(@TempDir Path repo) throws Exception {
        initGitDir(repo);
        Files.createDirectories(repo.resolve("src/main/java"));

        AnalysisConfig cfg = synth.synthesize(repo);

        assertThat(cfg.analysis().scope().include())
                .containsExactly("src/main/java/**/*.java");
        assertThat(cfg.analysis().scope().exclude())
                .containsExactly("**/generated/**", "**/test/**", "**/build/**", "**/target/**");
        assertThat(cfg.analysis().scope().granularity())
                .containsExactly(ScopeConfig.Granularity.FILE, ScopeConfig.Granularity.METHOD);
        assertThat(cfg.analysis().target().type()).isEqualTo(TargetConfig.TargetType.LOCAL_GIT);
        assertThat(cfg.analysis().target().path()).isEqualTo(repo.toAbsolutePath().normalize().toString());
        assertThat(cfg.analysis().window().days()).isEqualTo(365);
        assertThat(cfg.analysis().scoring().decayHalfLifeDays()).isEqualTo(90);
        assertThat(cfg.output().formats()).containsExactly(
                OutputConfig.OutputFormat.CSV, OutputConfig.OutputFormat.YAML,
                OutputConfig.OutputFormat.MD, OutputConfig.OutputFormat.HTML);
        assertThat(cfg.output().path()).isEqualTo("./hotspot-report");
        assertThat(cfg.output().topN()).isEqualTo(50);
    }

    @Test
    @DisplayName("multi-module: include glob is **/src/main/java when base has no top-level sources")
    void multiModule(@TempDir Path repo) throws Exception {
        initGitDir(repo);
        Files.createDirectories(repo.resolve("moduleA/src/main/java"));

        AnalysisConfig cfg = synth.synthesize(repo);

        assertThat(cfg.analysis().scope().include())
                .containsExactly("**/src/main/java/**/*.java");
    }

    @Test
    @DisplayName("multi-module: finds nested group/module/src/main/java")
    void nestedGroupModule(@TempDir Path repo) throws Exception {
        initGitDir(repo);
        Files.createDirectories(repo.resolve("group/module/src/main/java"));

        AnalysisConfig cfg = synth.synthesize(repo);

        assertThat(cfg.analysis().scope().include())
                .containsExactly("**/src/main/java/**/*.java");
    }

    @Test
    @DisplayName(".git as a pointer file (worktree) is accepted")
    void gitAsPointerFile(@TempDir Path repo) throws Exception {
        Files.writeString(repo.resolve(".git"), "gitdir: /somewhere/.git/worktrees/x\n");
        Files.createDirectories(repo.resolve("src/main/java"));

        AnalysisConfig cfg = synth.synthesize(repo);

        assertThat(cfg.analysis().target().type()).isEqualTo(TargetConfig.TargetType.LOCAL_GIT);
    }

    @Test
    @DisplayName("not a git work tree → ConfigSynthesisException with hint")
    void notAGitWorkTree(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("src/main/java"));
        assertThatThrownBy(() -> synth.synthesize(dir))
                .isInstanceOf(ConfigSynthesisException.class)
                .hasMessageContaining("not a git work tree");
    }

    @Test
    @DisplayName("no Java sources → ConfigSynthesisException with hint")
    void noJavaSources(@TempDir Path repo) throws Exception {
        initGitDir(repo);
        Files.writeString(repo.resolve("README.md"), "hi");
        assertThatThrownBy(() -> synth.synthesize(repo))
                .isInstanceOf(ConfigSynthesisException.class)
                .hasMessageContaining("no Java sources");
    }

    @Test
    @DisplayName("module scan ignores build/ and target/ directories")
    void scanIgnoresBuildAndTarget(@TempDir Path repo) throws Exception {
        initGitDir(repo);
        // a stray src/main/java buried under build/ must NOT count as a module
        Files.createDirectories(repo.resolve("build/generated/src/main/java"));
        Files.createDirectories(repo.resolve("moduleA/src/main/java"));

        AnalysisConfig cfg = synth.synthesize(repo);

        assertThat(cfg.analysis().scope().include())
                .containsExactly("**/src/main/java/**/*.java");
    }

    @Test
    @DisplayName("JaCoCo Gradle path auto-detected; Maven is fallback; null when neither exists")
    void jacocoDetection(@TempDir Path repo) throws Exception {
        initGitDir(repo);
        Files.createDirectories(repo.resolve("src/main/java"));
        assertThat(synth.synthesize(repo).analysis().jacocoReportPath()).isNull();

        Files.createDirectories(repo.resolve("build/reports/jacoco/test"));
        Files.writeString(repo.resolve("build/reports/jacoco/test/jacocoTestReport.xml"), "<report/>");
        assertThat(synth.synthesize(repo).analysis().jacocoReportPath())
                .isEqualTo("build/reports/jacoco/test/jacocoTestReport.xml");
    }

    @Test
    @DisplayName("Spring detected from base build file enables API analysis")
    void springFromBaseBuildFile(@TempDir Path repo) throws Exception {
        initGitDir(repo);
        Files.createDirectories(repo.resolve("src/main/java"));
        Files.writeString(repo.resolve("build.gradle.kts"),
                "dependencies { implementation(\"org.springframework.boot:spring-boot-starter-web\") }");

        assertThat(synth.synthesize(repo).analysis().apiAnalysis().enabled()).isTrue();
    }

    @Test
    @DisplayName("Spring detected from a submodule build file enables API analysis")
    void springFromSubmoduleBuildFile(@TempDir Path repo) throws Exception {
        initGitDir(repo);
        Files.createDirectories(repo.resolve("web/src/main/java"));
        Files.writeString(repo.resolve("web/pom.xml"),
                "<project><dependencies><dependency><artifactId>spring-webmvc</artifactId></dependency></dependencies></project>");

        assertThat(synth.synthesize(repo).analysis().apiAnalysis().enabled()).isTrue();
    }

    @Test
    @DisplayName("no Spring marker → API analysis disabled")
    void noSpring(@TempDir Path repo) throws Exception {
        initGitDir(repo);
        Files.createDirectories(repo.resolve("src/main/java"));
        Files.writeString(repo.resolve("build.gradle"), "plugins { id 'java' }");

        assertThat(synth.synthesize(repo).analysis().apiAnalysis().enabled()).isFalse();
    }

    @Test
    @DisplayName("classpathDirectories include existing compiled-class dirs in priority order")
    void classpathDetection(@TempDir Path repo) throws Exception {
        initGitDir(repo);
        Files.createDirectories(repo.resolve("src/main/java"));
        Files.createDirectories(repo.resolve("build/classes/java/main"));
        Files.createDirectories(repo.resolve("build/libs"));

        assertThat(synth.synthesize(repo).analysis().apiAnalysis().classpathDirectories())
                .containsExactly("build/classes/java/main", "build/libs");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests 'io.github.baekchangjoon.hotspotanalysis.config.ConfigSynthesizerTest' -q`
Expected: FAIL — `ConfigSynthesizer` does not exist (compile error).

- [ ] **Step 3: Implement `ConfigSynthesizer`**

```java
package io.github.baekchangjoon.hotspotanalysis.config;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Builds a fully-populated {@link AnalysisConfig} by inspecting a project on
 * disk, so {@code hotspot analyze} can run with no configuration file.
 *
 * <p>Symmetric to {@link ConfigLoader}: where the loader parses a YAML file,
 * the synthesizer derives the same record tree from filesystem signals. It has
 * no Spring collaborators; {@code @Component} only wires it into
 * {@code AnalyzeCommand}'s constructor injection.</p>
 */
@Component
public class ConfigSynthesizer {

    /** Max directory depth (below base) at which a module root may sit. */
    private static final int MAX_MODULE_DEPTH = 3; // group/module → depth 2
    private static final List<String> SKIP_DIRS =
            List.of("build", "target", ".git", "node_modules", ".gradle");
    private static final List<String> BUILD_FILES =
            List.of("build.gradle", "build.gradle.kts", "pom.xml");
    private static final List<String> SPRING_MARKERS =
            List.of("spring-boot-starter-web", "spring-webmvc", "spring-web");
    // Java NIO "**" does NOT match zero path segments, so "**/build/**" alone
    // would not exclude a ROOT-level build/ dir. Root-level variants are added
    // so generated/compiled output never leaks into the multi-module include.
    private static final List<String> EXCLUDES = List.of(
            "**/generated/**", "**/test/**",
            "**/build/**", "build/**",
            "**/target/**", "target/**");
    private static final List<String> CLASSPATH_CANDIDATES =
            List.of("build/classes/java/main", "target/classes", "build/libs");
    private static final List<String> JACOCO_CANDIDATES = List.of(
            "build/reports/jacoco/test/jacocoTestReport.xml",
            "target/site/jacoco/jacoco.xml");

    public AnalysisConfig synthesize(Path basePath) {
        Path base = basePath.toAbsolutePath().normalize();
        requireGitWorkTree(base);

        ModuleLayout layout = detectModules(base);

        TargetConfig target = new TargetConfig(
                TargetConfig.TargetType.LOCAL_GIT, base.toString(), null);
        WindowConfig window = new WindowConfig(null, null, 365);
        ScopeConfig scope = new ScopeConfig(
                List.of(ScopeConfig.Granularity.FILE, ScopeConfig.Granularity.METHOD),
                List.of(layout.includeGlob()),
                EXCLUDES);
        ScoringConfig scoring = new ScoringConfig(90, Boolean.FALSE);
        ApiAnalysisConfig api = new ApiAnalysisConfig(
                detectSpring(base, layout.moduleRoots()),
                ApiAnalysisConfig.SharedComponentMode.BOTH,
                detectClasspathDirs(base, layout.moduleRoots()));
        String jacoco = detectJacoco(base);

        AnalysisSection analysis = new AnalysisSection(
                target, window, scope, scoring, api, jacoco);
        OutputConfig output = new OutputConfig(
                List.of(OutputConfig.OutputFormat.CSV, OutputConfig.OutputFormat.YAML,
                        OutputConfig.OutputFormat.MD, OutputConfig.OutputFormat.HTML),
                "./hotspot-report", 50, OutputConfig.ApiLayout.BOTH, Boolean.FALSE);

        return new AnalysisConfig(analysis, output);
    }

    private void requireGitWorkTree(Path base) {
        // Accept .git as either a directory (normal checkout) or a pointer file
        // (linked worktree / submodule). JGit's Git.open resolves both.
        if (!Files.exists(base.resolve(".git"))) {
            throw new ConfigSynthesisException(
                    "not a git work tree: " + base
                            + ". Pass a path to a git repo, or use --config.");
        }
    }

    private ModuleLayout detectModules(Path base) {
        List<Path> roots = new ArrayList<>();
        collectModuleRoots(base, 0, roots);
        if (roots.isEmpty()) {
            throw new ConfigSynthesisException(
                    "no Java sources found under " + base
                            + " (looked for src/main/java). Pass a [path], or use"
                            + " --config for a custom scope.");
        }
        boolean single = roots.size() == 1 && roots.get(0).equals(base);
        String glob = single ? "src/main/java/**/*.java" : "**/src/main/java/**/*.java";
        return new ModuleLayout(glob, roots);
    }

    private void collectModuleRoots(Path dir, int depth, List<Path> roots) {
        if (Files.isDirectory(dir.resolve("src/main/java"))) {
            roots.add(dir);
            return; // a module root; do not descend further
        }
        if (depth >= MAX_MODULE_DEPTH) {
            return;
        }
        try (Stream<Path> children = Files.list(dir)) {
            children.filter(Files::isDirectory)
                    .filter(c -> !SKIP_DIRS.contains(c.getFileName().toString()))
                    .sorted()
                    .forEach(c -> collectModuleRoots(c, depth + 1, roots));
        } catch (IOException e) {
            // Surface as a clean CLI error (exit 1), not an uncaught crash.
            throw new ConfigSynthesisException(
                    "failed to scan for Java sources under " + dir + ": " + e.getMessage());
        }
    }

    private boolean detectSpring(Path base, List<Path> moduleRoots) {
        // LinkedHashSet dedups the single-module case where moduleRoots == [base].
        java.util.LinkedHashSet<Path> dirs = new java.util.LinkedHashSet<>();
        dirs.add(base);
        dirs.addAll(moduleRoots);
        for (Path dir : dirs) {
            for (String bf : BUILD_FILES) {
                Path f = dir.resolve(bf);
                if (Files.isRegularFile(f) && containsSpringMarker(f)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsSpringMarker(Path file) {
        try {
            String content = Files.readString(file);
            return SPRING_MARKERS.stream().anyMatch(content::contains);
        } catch (IOException e) {
            return false; // unreadable / non-UTF8 build file → treat as no marker
        }
    }

    private List<String> detectClasspathDirs(Path base, List<Path> moduleRoots) {
        // Resolved by CallGraphBuilder against repoRoot (= base), so paths are
        // returned relative to base with forward slashes. For multi-module
        // projects each module's compiled-class dir is probed (e.g.
        // moduleA/build/classes/java/main).
        List<String> dirs = new ArrayList<>();
        for (Path root : moduleRoots) {
            String prefix = base.relativize(root).toString().replace('\\', '/');
            for (String c : CLASSPATH_CANDIDATES) {
                if (Files.isDirectory(root.resolve(c))) {
                    dirs.add(prefix.isEmpty() ? c : prefix + "/" + c);
                }
            }
        }
        return dirs;
    }

    private String detectJacoco(Path base) {
        for (String c : JACOCO_CANDIDATES) {
            if (Files.isRegularFile(base.resolve(c))) {
                return c;
            }
        }
        return null;
    }

    private record ModuleLayout(String includeGlob, List<Path> moduleRoots) {
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test --tests 'io.github.baekchangjoon.hotspotanalysis.config.ConfigSynthesizerTest' -q`
Expected: PASS (all cases).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigSynthesizer.java \
        src/test/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigSynthesizerTest.java
git commit -m "feat(config): ConfigSynthesizer auto-detects AnalysisConfig from disk"
```

---

## Task 4: `ConfigSerializer` (for `--print-config`)

**Files:**
- Create: `src/main/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigSerializer.java`
- Test: `src/test/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigSerializerTest.java`

- [ ] **Step 1: Write the failing round-trip test**

```java
package io.github.baekchangjoon.hotspotanalysis.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigSerializerTest {

    private final ConfigSerializer serializer = new ConfigSerializer();
    private final ConfigSynthesizer synth = new ConfigSynthesizer();
    private final ConfigLoader loader = new ConfigLoader(new EnvironmentVariableResolver());

    @Test
    @DisplayName("serialized config omits null fields and re-loads to an equivalent config")
    void roundTrips(@TempDir Path repo) throws Exception {
        Files.createDirectories(repo.resolve(".git"));
        Files.createDirectories(repo.resolve("src/main/java"));
        AnalysisConfig original = synth.synthesize(repo);

        String yaml = serializer.serialize(original);

        // null fields are omitted, not emitted as explicit nulls.
        assertThat(yaml).doesNotContain("jacocoReportPath");
        // @AssertTrue validation getters must NOT leak into the YAML.
        assertThat(yaml)
                .doesNotContain("pathPresentWhenLocalGit")
                .doesNotContain("githubPresentWhenGithubType")
                .doesNotContain("sinceNotAfterUntil")
                .doesNotContain("eitherRangeOrDays");

        Path file = repo.resolve("printed.yml");
        Files.writeString(file, yaml);
        AnalysisConfig reloaded = loader.load(file);

        assertThat(reloaded.analysis().target().path())
                .isEqualTo(original.analysis().target().path());
        assertThat(reloaded.analysis().scope().include())
                .isEqualTo(original.analysis().scope().include());
        assertThat(reloaded.analysis().window().days())
                .isEqualTo(original.analysis().window().days());
        assertThat(reloaded.output().topN()).isEqualTo(original.output().topN());
        assertThat(reloaded.output().formats()).isEqualTo(original.output().formats());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests 'io.github.baekchangjoon.hotspotanalysis.config.ConfigSerializerTest' -q`
Expected: FAIL — `ConfigSerializer` does not exist.

- [ ] **Step 3: Implement `ConfigSerializer`**

```java
package io.github.baekchangjoon.hotspotanalysis.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

/**
 * Serialises an {@link AnalysisConfig} back to YAML for {@code analyze
 * --print-config}. Output is tuned (not the loader's parse mapper): null fields
 * are omitted and dates are ISO-8601 strings, so the printed YAML re-loads
 * through {@link ConfigLoader} to an equivalent config.
 *
 * <p>Getter / is-getter auto-detection is disabled so the {@code @AssertTrue}
 * validation methods on the config records (e.g.
 * {@code TargetConfig.isPathPresentWhenLocalGit()},
 * {@code WindowConfig.isSinceNotAfterUntil()}) are NOT emitted as bogus YAML
 * keys that {@link ConfigLoader} would reject. Records are still serialised via
 * their canonical components, which Jackson introspects from record metadata
 * independently of getter visibility.</p>
 */
@Component
public class ConfigSerializer {

    private final ObjectMapper mapper = YAMLMapper.builder()
            .addModule(new JavaTimeModule())
            .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(MapperFeature.AUTO_DETECT_GETTERS)
            .disable(MapperFeature.AUTO_DETECT_IS_GETTERS)
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .build();

    public String serialize(AnalysisConfig config) {
        try {
            return mapper.writeValueAsString(config);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ConfigSynthesisException(
                    "failed to serialise synthesized config: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests 'io.github.baekchangjoon.hotspotanalysis.config.ConfigSerializerTest' -q`
Expected: PASS.

> If the round-trip assertion `doesNotContain("null")` trips on a legitimate
> substring, tighten it to assert specific absent keys (`jacocoReportPath`,
> `github`, `since`, `until`) instead. The core guarantee is that
> `loader.load(serialized)` succeeds and matches the originals asserted above.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigSerializer.java \
        src/test/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigSerializerTest.java
git commit -m "feat(config): ConfigSerializer renders synthesized config as reloadable YAML"
```

---

## Task 5: Wire zero-config into `AnalyzeCommand`

**Files:**
- Modify: `src/main/java/io/github/baekchangjoon/hotspotanalysis/cli/AnalyzeCommand.java`
- Modify: `src/test/java/io/github/baekchangjoon/hotspotanalysis/cli/AnalyzeCommandTest.java`

- [ ] **Step 1: Update `AnalyzeCommandTest` setup + add flag-validation tests (red)**

In `AnalyzeCommandTest.setUp()`, the command is built as:

```java
        command = new AnalyzeCommand(
                new ConfigLoader(new EnvironmentVariableResolver()),
                analyzer, dispatcher);
```

Replace that with the new 5-arg constructor:

```java
        command = new AnalyzeCommand(
                new ConfigLoader(new EnvironmentVariableResolver()),
                new io.github.baekchangjoon.hotspotanalysis.config.ConfigSynthesizer(),
                new io.github.baekchangjoon.hotspotanalysis.config.ConfigSerializer(),
                analyzer, dispatcher);
```

Then add these test methods to the class (it has `repoRoot`, `outputDir`, `tempDir`, the `writeJava` helper, and constructs `new CommandLine(command)`). Also add the CWD-output cleanup hook and import `org.junit.jupiter.api.AfterEach`, `java.io.IOException`, `java.util.stream.Stream`:

```java
    @AfterEach
    void cleanupCwdReport() throws IOException {
        Path report = Path.of("hotspot-report");
        if (!Files.exists(report)) return;
        try (Stream<Path> walk = Files.walk(report)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        }
    }

    @Test
    @DisplayName("zero-config: bare positional path runs and prints the detection summary")
    void zeroConfigPositionalRuns() throws Exception {
        StringWriter sw = new StringWriter();
        StringWriter ew = new StringWriter();
        CommandLine cli = new CommandLine(command);
        cli.setOut(new PrintWriter(sw));
        cli.setErr(new PrintWriter(ew));

        // No --quiet, so the stderr detection summary is printed.
        int exit = cli.execute(repoRoot.toString());

        assertThat(exit).isZero();
        assertThat(ew.toString()).contains("Detected (zero-config)");
        // Default ./hotspot-report (CWD-relative) is created; @AfterEach removes it.
        assertThat(Files.exists(Path.of("hotspot-report"))).isTrue();
    }

    @Test
    @DisplayName("--config and positional path are mutually exclusive (exit 1)")
    void configAndPathMutuallyExclusive() throws Exception {
        Path cfg = tempDir.resolve("hotspot.yml");
        Files.writeString(cfg, "x");
        StringWriter ew = new StringWriter();
        CommandLine cli = new CommandLine(command);
        cli.setErr(new PrintWriter(ew));

        int exit = cli.execute("--config", cfg.toString(), repoRoot.toString());

        assertThat(exit).isEqualTo(1);
        assertThat(ew.toString()).contains("mutually exclusive");
    }

    @Test
    @DisplayName("--print-config with --config is rejected (exit 1)")
    void printConfigWithConfigRejected() throws Exception {
        Path cfg = tempDir.resolve("hotspot.yml");
        Files.writeString(cfg, "x");
        StringWriter ew = new StringWriter();
        CommandLine cli = new CommandLine(command);
        cli.setErr(new PrintWriter(ew));

        int exit = cli.execute("--config", cfg.toString(), "--print-config");

        assertThat(exit).isEqualTo(1);
        assertThat(ew.toString()).contains("zero-config");
    }

    @Test
    @DisplayName("--print-config prints YAML and does not run analysis")
    void printConfigPrintsYaml() throws Exception {
        StringWriter sw = new StringWriter();
        CommandLine cli = new CommandLine(command);
        cli.setOut(new PrintWriter(sw));

        int exit = cli.execute(repoRoot.toString(), "--print-config");

        assertThat(exit).isZero();
        // Enums serialise via name(): LOCAL_GIT (underscore), not "local-git".
        assertThat(sw.toString()).contains("analysis:").contains("LOCAL_GIT");
        // print-config does not run analysis → no report dir.
        assertThat(Files.exists(Path.of("hotspot-report"))).isFalse();
    }
```

Run: `./gradlew test --tests 'io.github.baekchangjoon.hotspotanalysis.cli.AnalyzeCommandTest' -q`
Expected: FAIL to compile (5-arg constructor / `--print-config` / positional do not exist yet).

- [ ] **Step 2: Rewrite `AnalyzeCommand`**

Replace the whole class body with:

```java
package io.github.baekchangjoon.hotspotanalysis.cli;

import io.github.baekchangjoon.hotspotanalysis.analysis.HotspotAnalyzer;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.FileHotspot;
import io.github.baekchangjoon.hotspotanalysis.config.AnalysisConfig;
import io.github.baekchangjoon.hotspotanalysis.config.ConfigLoadException;
import io.github.baekchangjoon.hotspotanalysis.config.ConfigLoader;
import io.github.baekchangjoon.hotspotanalysis.config.ConfigSerializer;
import io.github.baekchangjoon.hotspotanalysis.config.ConfigSynthesisException;
import io.github.baekchangjoon.hotspotanalysis.config.ConfigSynthesizer;
import io.github.baekchangjoon.hotspotanalysis.output.OutputDispatcher;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * {@code hotspot analyze [path] [--config <file>]} subcommand.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>File mode</b> — {@code --config <file>} loads a YAML configuration
 *       (unchanged behaviour).</li>
 *   <li><b>Zero-config mode</b> — no {@code --config}; the configuration is
 *       synthesized from {@code [path]} (default: current directory) by
 *       {@link ConfigSynthesizer}.</li>
 * </ul>
 * {@code --print-config} (zero-config only) prints the synthesized YAML to
 * stdout and exits without analysing.</p>
 *
 * <p>Exit codes: {@code 0} success; {@code 1} fatal error (bad config,
 * unreadable repo, illegal flag combination, synthesis failure); {@code 3}
 * {@code --strict} with an empty result.</p>
 */
@Component
@Command(
        name = "analyze",
        description = "Analyse a repository and emit hotspot reports.",
        mixinStandardHelpOptions = true
)
public class AnalyzeCommand implements Callable<Integer> {

    static final int EXIT_OK = 0;
    static final int EXIT_FAILURE = 1;
    static final int EXIT_STRICT_EMPTY = 3;

    @Parameters(index = "0", arity = "0..1",
            description = "Repository to analyse in zero-config mode "
                    + "(default: current directory). Mutually exclusive with --config.")
    private Path path;

    @Option(names = {"-c", "--config"},
            description = "Path to the YAML configuration file. If omitted, the "
                    + "configuration is auto-detected from [path].")
    private Path configPath;

    @Option(names = {"--print-config"},
            description = "Print the auto-detected configuration as YAML and exit "
                    + "(zero-config mode only).")
    private boolean printConfig;

    @Option(names = {"-q", "--quiet"},
            description = "Suppress the summary output on stdout.")
    private boolean quiet;

    @Option(names = {"-s", "--strict"},
            description = "Exit with code 3 when the analysis result is empty "
                    + "(zero commits in window or zero files matching scope). "
                    + "Useful for CI to fail loudly instead of producing empty reports.")
    private boolean strict;

    @Spec
    private CommandSpec spec;

    private final ConfigLoader configLoader;
    private final ConfigSynthesizer configSynthesizer;
    private final ConfigSerializer configSerializer;
    private final HotspotAnalyzer analyzer;
    private final OutputDispatcher outputDispatcher;

    public AnalyzeCommand(ConfigLoader configLoader,
                          ConfigSynthesizer configSynthesizer,
                          ConfigSerializer configSerializer,
                          HotspotAnalyzer analyzer,
                          OutputDispatcher outputDispatcher) {
        this.configLoader = configLoader;
        this.configSynthesizer = configSynthesizer;
        this.configSerializer = configSerializer;
        this.analyzer = analyzer;
        this.outputDispatcher = outputDispatcher;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        // 1. Preflight validation (before any I/O).
        if (configPath != null && path != null) {
            err.println("ERROR: --config and [path] are mutually exclusive.");
            return EXIT_FAILURE;
        }
        if (configPath != null && printConfig) {
            err.println("ERROR: --print-config applies only to zero-config mode (remove --config).");
            return EXIT_FAILURE;
        }

        // 2. Build the config.
        AnalysisConfig config;
        boolean zeroConfig = configPath == null;
        try {
            if (zeroConfig) {
                Path base = (path != null) ? path : Path.of("").toAbsolutePath();
                config = configSynthesizer.synthesize(base);
                if (printConfig) {
                    out.print(configSerializer.serialize(config));
                    out.flush();
                    return EXIT_OK;
                }
                if (!quiet) {
                    printDetectionSummary(err, config);
                }
            } else {
                if (!Files.isRegularFile(configPath)) {
                    err.println("ERROR: configuration file not found: " + configPath);
                    return EXIT_FAILURE;
                }
                config = configLoader.load(configPath);
            }
        } catch (ConfigSynthesisException e) {
            err.println("ERROR: " + e.getMessage());
            return EXIT_FAILURE;
        } catch (ConfigLoadException e) {
            err.println("ERROR: invalid configuration: " + e.getMessage());
            return EXIT_FAILURE;
        }

        // 3. Analyse.
        try {
            AnalysisResult result = analyzer.analyze(config);
            boolean apiEnabled = config.analysis().apiAnalysis() != null
                    && config.analysis().apiAnalysis().enabled();
            boolean excludeCoverage = config.analysis().scoring() != null
                    && Boolean.TRUE.equals(config.analysis().scoring().excludeCoverage());
            outputDispatcher.dispatch(result, config.output(), apiEnabled, excludeCoverage);
            if (!quiet) {
                printSummary(out, result);
            }
            if (strict && isEmpty(result)) {
                printStrictFailure(err, result);
                return EXIT_STRICT_EMPTY;
            }
            return EXIT_OK;
        } catch (UnsupportedOperationException e) {
            err.println("ERROR: " + e.getMessage());
            return EXIT_FAILURE;
        } catch (RuntimeException e) {
            err.println("ERROR: analysis failed: " + e.getMessage());
            return EXIT_FAILURE;
        }
    }

    private static boolean isEmpty(AnalysisResult result) {
        return result.meta().totalCommits() == 0 || result.meta().totalFiles() == 0;
    }

    private static void printDetectionSummary(PrintWriter err, AnalysisConfig config) {
        String include = config.analysis().scope().include().get(0);
        boolean multiModule = include.startsWith("**/");
        String jacoco = config.analysis().jacocoReportPath();
        boolean api = config.analysis().apiAnalysis() != null
                && config.analysis().apiAnalysis().enabled();
        err.println("Detected (zero-config):");
        err.println("  Repo:           " + config.analysis().target().path() + " (.git found)");
        err.println("  Module layout:  " + (multiModule ? "multi-module (**/src/main/java)"
                : "single-module (src/main/java)"));
        err.println("  JaCoCo:         " + (jacoco != null ? jacoco : "none"));
        err.println("  API analysis:   " + (api ? "ON (spring-web detected)"
                : "OFF (no spring-web on build)"));
        err.println("  → run with --print-config to save this as hotspot.yml");
    }

    private static void printStrictFailure(PrintWriter err, AnalysisResult result) {
        err.println("ERROR: --strict was set but the analysis produced an empty result.");
        err.println("  Commits matching window: " + result.meta().totalCommits());
        err.println("  Files matching scope:   " + result.meta().totalFiles());
        err.println("  Methods extracted:      " + result.meta().totalMethods());
        err.println("Hints:");
        err.println("  - Multi-module projects need '**/' in scope.include "
                + "(e.g. '**/src/main/java/**/*.java').");
        err.println("  - Widen analysis.window.days or switch to absolute "
                + "since/until covering actual commit activity.");
        err.println("  - Verify analysis.target.path points at a directory "
                + "that contains a .git/ folder.");
    }

    private static void printSummary(PrintWriter out, AnalysisResult result) {
        out.println("Hotspot analysis complete.");
        out.println("  Target:      " + result.meta().targetDescription());
        out.println("  Commits:     " + result.meta().totalCommits());
        out.println("  Files:       " + result.meta().totalFiles());
        out.println("  Methods:     " + result.meta().totalMethods());
        if (!result.fileHotspots().isEmpty()) {
            FileHotspot top = result.fileHotspots().get(0);
            out.printf("  Top file:    %s (rev=%d, loc=%d, score=%.0f)%n",
                    top.path(), top.revisions(), top.loc(), top.simpleScore());
        }
    }
}
```

- [ ] **Step 3: Run `AnalyzeCommandTest`**

Run: `./gradlew test --tests 'io.github.baekchangjoon.hotspotanalysis.cli.AnalyzeCommandTest' -q`
Expected: PASS (existing + new cases).

- [ ] **Step 4: Run the outer-loop E2E suite (should now be green)**

Run: `./gradlew test --tests 'io.github.baekchangjoon.hotspotanalysis.HotspotCliE2ETest' -q`
Expected: PASS — all 8 zero-config acceptance tests plus the pre-existing ones.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/baekchangjoon/hotspotanalysis/cli/AnalyzeCommand.java \
        src/test/java/io/github/baekchangjoon/hotspotanalysis/cli/AnalyzeCommandTest.java
git commit -m "feat(cli): zero-config analyze (optional --config, [path], --print-config)"
```

---

## Task 6: Full regression + docs

**Files:**
- Modify: `README.md`, `README.en.md`

- [ ] **Step 1: Run the entire suite**

Run: `./gradlew test -q`
Expected: BUILD SUCCESSFUL, 0 failures. (If a pre-existing test asserted that `analyze` without `--config` fails as a usage error, update it to reflect zero-config — note it in the commit.)

- [ ] **Step 2: Update `README.md` (Korean) quick start**

In the "빠른 시작" section, before "2. 샘플 설정 생성", insert a zero-config subsection:

```markdown
### 1.5 설정 없이 바로 실행 (zero-config)

설정 파일을 만들기 전에, 저장소 루트에서 바로 실행할 수 있습니다. git 루트·
모듈 레이아웃(단일/멀티)·JaCoCo 리포트·Spring API 분석을 자동 감지합니다:

```bash
java -jar hotspot.jar analyze            # 현재 디렉터리
java -jar hotspot.jar analyze /path/to/repo
```

감지 결과를 설정 파일로 저장해 손보려면:

```bash
java -jar hotspot.jar analyze --print-config > hotspot.yml
```

> 제약: 저장소 **루트**에서 실행해야 합니다(하위 디렉터리는 오류 + 힌트).
> 멀티 모듈에서 서브모듈별 JaCoCo 리포트는 자동 합산하지 않습니다(루트 레벨만 탐지).
> 세밀한 제어가 필요하면 `--config`를 쓰세요.
```

- [ ] **Step 3: Update `README.en.md` quick start**

Insert the English equivalent before the "Generate a sample configuration" step:

```markdown
### 1.5 Run with no config (zero-config)

Before writing any config, run straight from the repository root. The git root,
module layout (single/multi), JaCoCo report, and Spring API analysis are
auto-detected:

```bash
java -jar hotspot.jar analyze            # current directory
java -jar hotspot.jar analyze /path/to/repo
```

To save the detected settings as a file you can then customise:

```bash
java -jar hotspot.jar analyze --print-config > hotspot.yml
```

> Limitations: run from the repository **root** (a subdirectory errors with a
> hint). Per-submodule JaCoCo reports in multi-module builds are not aggregated
> (only the root-level report is detected). Use `--config` for fine control.
```

- [ ] **Step 4: Commit**

```bash
git add README.md README.en.md
git commit -m "docs: document zero-config analyze and --print-config"
```

---

## Definition of Done

- [ ] All 8 E2E/acceptance tests in `HotspotCliE2ETest` pass (Task 1 list).
- [ ] `ConfigSynthesizerTest` and `ConfigSerializerTest` pass.
- [ ] `AnalyzeCommandTest` (existing + new) passes.
- [ ] `./gradlew test` is fully green.
- [ ] `README.md` and `README.en.md` document zero-config + `--print-config` + limitations.

## Self-Review Notes (for the implementer)

- **CWD-relative output in E2E**: zero-config default `output.path` is
  `./hotspot-report`, resolved against the process CWD (the gradle test working
  dir), not `@TempDir`. The E2E helpers clean it up via `deleteRecursively`.
  Tests that only need to assert detection (not output files) use
  `--print-config` to avoid writing to CWD.
- **Type names are fixed across tasks**: `ConfigSynthesizer.synthesize(Path)`,
  `ConfigSerializer.serialize(AnalysisConfig)`, `ConfigSynthesisException`,
  `AnalyzeCommand(ConfigLoader, ConfigSynthesizer, ConfigSerializer,
  HotspotAnalyzer, OutputDispatcher)`. Do not rename between tasks.
- **Relative paths**: synthesized `jacocoReportPath` and `classpathDirectories`
  are relative (resolved by `HotspotAnalyzer` against `target.path`);
  `target.path` is absolute.
