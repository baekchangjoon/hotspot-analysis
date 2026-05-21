package io.github.baekchangjoon.hotspotanalysis.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class YamlOutputWriterTest {

    private final YamlOutputWriter writer = new YamlOutputWriter();

    @Test
    @DisplayName("emits a single hotspots.yml file with meta, fileHotspots, methodHotspots")
    void shouldEmitYamlFile(@TempDir Path tempDir) throws IOException {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);

        Path yaml = tempDir.resolve("hotspots.yml");
        assertThat(yaml).exists();
        String content = Files.readString(yaml);
        assertThat(content).contains("meta:");
        assertThat(content).contains("fileHotspots:");
        assertThat(content).contains("methodHotspots:");
        assertThat(content).contains("analyzedAt: \"2026-05-21T09:00:00Z\"");
        assertThat(content).contains("scoringFormula: \"SIMPLE\"");
    }

    @Test
    @DisplayName("yaml round-trips back to the structural shape we wrote")
    void shouldRoundTripYaml(@TempDir Path tempDir) throws IOException {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);

        ObjectMapper parser = new ObjectMapper(new YAMLFactory())
                .registerModule(new JavaTimeModule());
        Map<String, Object> parsed = parser.readValue(
                tempDir.resolve("hotspots.yml").toFile(), Map.class);

        assertThat(parsed).containsKeys("meta", "fileHotspots", "methodHotspots");
        Map<String, Object> meta = (Map<String, Object>) parsed.get("meta");
        assertThat(meta).containsEntry("totalCommits", 42);
        assertThat(meta).containsEntry("targetDescription", "LOCAL_GIT:/tmp/example");
    }
}
