package io.github.baekchangjoon.hotspotanalysis.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the YAML loading and validation pipeline (parse → env-substitute → bean-validate).
 */
class ConfigLoaderTest {

    @Test
    @DisplayName("loads a complete local-git configuration with all fields populated")
    void shouldLoadCompleteLocalGitConfig(@TempDir Path tempDir) throws IOException {
        String yaml = """
                analysis:
                  target:
                    type: local-git
                    path: ./my-project
                  window:
                    since: "2025-11-21"
                    until: "2026-05-21"
                  scope:
                    granularity: [file, method]
                    include: ["src/main/java/**/*.java"]
                    exclude: ["**/generated/**"]
                output:
                  formats: [csv, md]
                  path: ./hotspot-report
                  topN: 50
                """;
        Path file = writeYaml(tempDir, yaml);

        AnalysisConfig config = newLoaderWithEnv(Map.of()).load(file);

        assertThat(config.analysis().target().type()).isEqualTo(TargetConfig.TargetType.LOCAL_GIT);
        assertThat(config.analysis().target().path()).isEqualTo("./my-project");
        assertThat(config.analysis().window().since()).isEqualTo(LocalDate.parse("2025-11-21"));
        assertThat(config.analysis().window().until()).isEqualTo(LocalDate.parse("2026-05-21"));
        assertThat(config.analysis().scope().granularity())
                .containsExactly(ScopeConfig.Granularity.FILE, ScopeConfig.Granularity.METHOD);
        assertThat(config.analysis().scope().include()).containsExactly("src/main/java/**/*.java");
        assertThat(config.analysis().scope().exclude()).containsExactly("**/generated/**");
        assertThat(config.analysis().scoring()).isNotNull();
        assertThat(config.analysis().scoring().decayHalfLifeDays()).isEqualTo(90);
        assertThat(config.output().formats())
                .containsExactly(OutputConfig.OutputFormat.CSV, OutputConfig.OutputFormat.MD);
        assertThat(config.output().path()).isEqualTo("./hotspot-report");
        assertThat(config.output().topN()).isEqualTo(50);
    }

    @Test
    @DisplayName("loads a github target configuration with env-substituted token")
    void shouldLoadGithubConfigWithEnvSubstitution(@TempDir Path tempDir) throws IOException {
        String yaml = """
                analysis:
                  target:
                    type: github
                    github:
                      owner: acme
                      repo: widget
                      branch: main
                      token: ${GITHUB_TOKEN}
                  window:
                    days: 180
                  scope:
                    granularity: [file]
                    include: ["**/*.java"]
                output:
                  formats: [csv]
                  path: ./out
                  topN: 0
                """;
        Path file = writeYaml(tempDir, yaml);

        AnalysisConfig config = newLoaderWithEnv(Map.of("GITHUB_TOKEN", "secret123")).load(file);

        assertThat(config.analysis().target().type()).isEqualTo(TargetConfig.TargetType.GITHUB);
        assertThat(config.analysis().target().github()).isNotNull();
        assertThat(config.analysis().target().github().owner()).isEqualTo("acme");
        assertThat(config.analysis().target().github().repo()).isEqualTo("widget");
        assertThat(config.analysis().target().github().branch()).isEqualTo("main");
        assertThat(config.analysis().target().github().token()).isEqualTo("secret123");
        assertThat(config.analysis().window().days()).isEqualTo(180);
    }

    @Test
    @DisplayName("fails when the configuration file does not exist")
    void shouldFailWhenFileMissing(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist.yml");

        assertThatThrownBy(() -> newLoaderWithEnv(Map.of()).load(missing))
                .isInstanceOf(ConfigLoadException.class)
                .hasMessageContaining("does-not-exist");
    }

    @Test
    @DisplayName("fails when a required field (target.type) is missing")
    void shouldFailOnMissingRequiredField(@TempDir Path tempDir) throws IOException {
        String yaml = """
                analysis:
                  target:
                    path: ./my-project
                  window:
                    days: 30
                  scope:
                    granularity: [file]
                    include: ["**/*.java"]
                output:
                  formats: [csv]
                  path: ./out
                  topN: 10
                """;
        Path file = writeYaml(tempDir, yaml);

        assertThatThrownBy(() -> newLoaderWithEnv(Map.of()).load(file))
                .isInstanceOf(ConfigValidationException.class);
    }

    @Test
    @DisplayName("fails when an unknown enum value is provided for target.type")
    void shouldFailOnInvalidEnumValue(@TempDir Path tempDir) throws IOException {
        String yaml = """
                analysis:
                  target:
                    type: bitbucket
                    path: ./repo
                  window:
                    days: 30
                  scope:
                    granularity: [file]
                    include: ["**/*.java"]
                output:
                  formats: [csv]
                  path: ./out
                  topN: 0
                """;
        Path file = writeYaml(tempDir, yaml);

        assertThatThrownBy(() -> newLoaderWithEnv(Map.of()).load(file))
                .isInstanceOf(ConfigLoadException.class);
    }

    @Test
    @DisplayName("fails when window has neither (since,until) nor days")
    void shouldFailWhenWindowEmpty(@TempDir Path tempDir) throws IOException {
        String yaml = """
                analysis:
                  target:
                    type: local-git
                    path: ./repo
                  window: {}
                  scope:
                    granularity: [file]
                    include: ["**/*.java"]
                output:
                  formats: [csv]
                  path: ./out
                  topN: 0
                """;
        Path file = writeYaml(tempDir, yaml);

        assertThatThrownBy(() -> newLoaderWithEnv(Map.of()).load(file))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("window");
    }

    @Test
    @DisplayName("fails when window since is after until")
    void shouldFailWhenSinceAfterUntil(@TempDir Path tempDir) throws IOException {
        String yaml = """
                analysis:
                  target:
                    type: local-git
                    path: ./repo
                  window:
                    since: "2026-05-21"
                    until: "2025-11-21"
                  scope:
                    granularity: [file]
                    include: ["**/*.java"]
                output:
                  formats: [csv]
                  path: ./out
                  topN: 0
                """;
        Path file = writeYaml(tempDir, yaml);

        assertThatThrownBy(() -> newLoaderWithEnv(Map.of()).load(file))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("since");
    }

    @Test
    @DisplayName("fails when output.formats is empty")
    void shouldFailWhenOutputFormatsEmpty(@TempDir Path tempDir) throws IOException {
        String yaml = """
                analysis:
                  target:
                    type: local-git
                    path: ./repo
                  window:
                    days: 30
                  scope:
                    granularity: [file]
                    include: ["**/*.java"]
                output:
                  formats: []
                  path: ./out
                  topN: 0
                """;
        Path file = writeYaml(tempDir, yaml);

        assertThatThrownBy(() -> newLoaderWithEnv(Map.of()).load(file))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("formats");
    }

    @Test
    @DisplayName("fails when output.topN is negative")
    void shouldFailWhenTopNNegative(@TempDir Path tempDir) throws IOException {
        String yaml = """
                analysis:
                  target:
                    type: local-git
                    path: ./repo
                  window:
                    days: 30
                  scope:
                    granularity: [file]
                    include: ["**/*.java"]
                output:
                  formats: [csv]
                  path: ./out
                  topN: -5
                """;
        Path file = writeYaml(tempDir, yaml);

        assertThatThrownBy(() -> newLoaderWithEnv(Map.of()).load(file))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("topN");
    }

    @Test
    @DisplayName("fails when target.type=github but github block is missing")
    void shouldFailWhenGithubTargetMissingGithubBlock(@TempDir Path tempDir) throws IOException {
        String yaml = """
                analysis:
                  target:
                    type: github
                  window:
                    days: 30
                  scope:
                    granularity: [file]
                    include: ["**/*.java"]
                output:
                  formats: [csv]
                  path: ./out
                  topN: 0
                """;
        Path file = writeYaml(tempDir, yaml);

        assertThatThrownBy(() -> newLoaderWithEnv(Map.of()).load(file))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("github");
    }

    @Test
    @DisplayName("fails when target.type=local-git but path is missing")
    void shouldFailWhenLocalGitTargetMissingPath(@TempDir Path tempDir) throws IOException {
        String yaml = """
                analysis:
                  target:
                    type: local-git
                  window:
                    days: 30
                  scope:
                    granularity: [file]
                    include: ["**/*.java"]
                output:
                  formats: [csv]
                  path: ./out
                  topN: 0
                """;
        Path file = writeYaml(tempDir, yaml);

        assertThatThrownBy(() -> newLoaderWithEnv(Map.of()).load(file))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("path");
    }

    @Test
    @DisplayName("loads configuration with default apiAnalysis and apiLayout when omitted")
    void shouldLoadApiAnalysisConfigWithDefaults(@TempDir Path tempDir) throws IOException {
        String yaml = """
                analysis:
                  target:
                    type: local-git
                    path: ./my-project
                  window:
                    days: 30
                  scope:
                    granularity: [file]
                    include: ["**/*.java"]
                output:
                  formats: [csv]
                  path: ./out
                  topN: 10
                """;
        Path file = writeYaml(tempDir, yaml);

        AnalysisConfig config = newLoaderWithEnv(Map.of()).load(file);

        assertThat(config.analysis().apiAnalysis()).isNotNull();
        assertThat(config.analysis().apiAnalysis().enabled()).isFalse();
        assertThat(config.analysis().apiAnalysis().sharedComponentMode())
                .isEqualTo(ApiAnalysisConfig.SharedComponentMode.BOTH);
        assertThat(config.analysis().apiAnalysis().classpathDirectories()).isEmpty();
        assertThat(config.output().apiLayout()).isEqualTo(OutputConfig.ApiLayout.BOTH);
        assertThat(config.output().coverageBreakdown()).isFalse();
    }

    @Test
    @DisplayName("loads configuration with explicit apiAnalysis and apiLayout values")
    void shouldLoadApiAnalysisConfigWithExplicitValues(@TempDir Path tempDir) throws IOException {
        String yaml = """
                analysis:
                  target:
                    type: local-git
                    path: ./my-project
                  window:
                    days: 30
                  scope:
                    granularity: [file]
                    include: ["**/*.java"]
                  apiAnalysis:
                    enabled: true
                    sharedComponentMode: cumulative
                    classpathDirectories:
                      - "target/classes"
                      - "lib/classes"
                output:
                  formats: [csv]
                  path: ./out
                  topN: 10
                  apiLayout: standalone
                  coverageBreakdown: true
                """;
        Path file = writeYaml(tempDir, yaml);

        AnalysisConfig config = newLoaderWithEnv(Map.of()).load(file);

        assertThat(config.analysis().apiAnalysis().enabled()).isTrue();
        assertThat(config.analysis().apiAnalysis().sharedComponentMode())
                .isEqualTo(ApiAnalysisConfig.SharedComponentMode.CUMULATIVE);
        assertThat(config.analysis().apiAnalysis().classpathDirectories())
                .containsExactly("target/classes", "lib/classes");
        assertThat(config.output().apiLayout()).isEqualTo(OutputConfig.ApiLayout.STANDALONE);
        assertThat(config.output().coverageBreakdown()).isTrue();
    }

    private static Path writeYaml(Path dir, String content) throws IOException {
        Path file = dir.resolve("hotspot.yml");
        Files.writeString(file, content);
        return file;
    }

    @Test
    @DisplayName("loads scoring.excludeCoverage=true from YAML")
    void shouldLoadExcludeCoverageFlag(@TempDir Path tempDir) throws IOException {
        String yaml = """
                analysis:
                  target:
                    type: local-git
                    path: /tmp/some-repo
                  window:
                    days: 30
                  scope:
                    granularity: [file]
                    include: ["src/main/java/**/*.java"]
                  scoring:
                    excludeCoverage: true
                output:
                  formats: [csv]
                  path: ./hotspot-report
                  topN: 0
                """;
        Path file = writeYaml(tempDir, yaml);

        AnalysisConfig config = newLoaderWithEnv(Map.of()).load(file);
        assertThat(config.analysis().scoring().excludeCoverage()).isTrue();
    }

    @Test
    @DisplayName("scoring.excludeCoverage defaults to false when omitted")
    void shouldDefaultExcludeCoverageToFalse(@TempDir Path tempDir) throws IOException {
        String yaml = """
                analysis:
                  target:
                    type: local-git
                    path: /tmp/some-repo
                  window:
                    days: 30
                  scope:
                    granularity: [file]
                    include: ["src/main/java/**/*.java"]
                output:
                  formats: [csv]
                  path: ./hotspot-report
                  topN: 0
                """;
        Path file = writeYaml(tempDir, yaml);

        AnalysisConfig config = newLoaderWithEnv(Map.of()).load(file);
        assertThat(config.analysis().scoring().excludeCoverage()).isFalse();
    }

    @Test
    @DisplayName("rejects legacy scoring.formula key with friendly migration message")
    void rejectsLegacyScoringFormulaKeyWithFriendlyMessage(@TempDir Path tempDir) throws IOException {
        String yaml = """
                analysis:
                  target:
                    type: local-git
                    path: /tmp/some-repo
                  window:
                    days: 365
                  scope:
                    granularity: [file]
                    include: ["src/main/java/**/*.java"]
                  scoring:
                    formula: simple
                output:
                  formats: [csv]
                  path: ./hotspot-report
                  topN: 0
                """;
        Path file = writeYaml(tempDir, yaml);

        assertThatThrownBy(() -> newLoaderWithEnv(Map.of()).load(file))
                .isInstanceOf(ConfigLoadException.class)
                .hasMessageContaining("scoring.formula has been removed")
                .hasMessageContaining("Delete this line");
    }

    private static ConfigLoader newLoaderWithEnv(Map<String, String> env) {
        return new ConfigLoader(new EnvironmentVariableResolver(env::get));
    }
}
