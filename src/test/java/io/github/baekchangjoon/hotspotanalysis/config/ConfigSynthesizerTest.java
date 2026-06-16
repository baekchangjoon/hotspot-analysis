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
                .containsExactly("**/generated/**", "**/test/**",
                        "**/build/**", "build/**", "**/target/**", "target/**");
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
