package io.github.baekchangjoon.hotspotanalysis.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard: the example template shipped with the binary must always
 * parse and validate cleanly. This prevents drift between the documented
 * sample and the actual schema.
 */
class HotspotExampleConfigTest {

    private static final String RESOURCE_PATH = "templates/hotspot.example.yml";

    @Test
    @DisplayName("templates/hotspot.example.yml parses and validates without errors")
    void shouldLoadExampleTemplate(@TempDir Path tempDir) throws IOException {
        Path copy = copyResourceToTempFile(tempDir);
        ConfigLoader loader = new ConfigLoader(
                new EnvironmentVariableResolver(Map.<String, String>of()::get));

        AnalysisConfig config = loader.load(copy);

        assertThat(config.analysis().target().type())
                .isEqualTo(TargetConfig.TargetType.LOCAL_GIT);
        assertThat(config.analysis().scope().granularity())
                .contains(ScopeConfig.Granularity.FILE, ScopeConfig.Granularity.METHOD);
        assertThat(config.output().formats()).isNotEmpty();
        assertThat(config.output().topN()).isGreaterThanOrEqualTo(0);
    }

    private Path copyResourceToTempFile(Path tempDir) throws IOException {
        try (InputStream in = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream(RESOURCE_PATH),
                "Resource not found on classpath: " + RESOURCE_PATH)) {
            Path target = tempDir.resolve("hotspot.example.yml");
            Files.copy(in, target);
            return target;
        }
    }
}
