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
        assertThat(reloaded.analysis().scoring().decayHalfLifeDays())
                .isEqualTo(original.analysis().scoring().decayHalfLifeDays());
        assertThat(reloaded.analysis().scoring().excludeCoverage())
                .isEqualTo(original.analysis().scoring().excludeCoverage());
        assertThat(reloaded.analysis().apiAnalysis().enabled())
                .isEqualTo(original.analysis().apiAnalysis().enabled());
        assertThat(reloaded.analysis().apiAnalysis().sharedComponentMode())
                .isEqualTo(original.analysis().apiAnalysis().sharedComponentMode());
    }
}
