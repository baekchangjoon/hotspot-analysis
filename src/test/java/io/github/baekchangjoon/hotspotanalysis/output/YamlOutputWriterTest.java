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
        assertThat(content).doesNotContain("scoringFormula");
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

    @Test
    @DisplayName("emits api_report.yml when API analysis is enabled and layout is STANDALONE or BOTH")
    void shouldEmitApiYamlFiles(@TempDir Path tempDir) throws IOException {
        writer.write(OutputWriterTestFixtures.sampleApiResult(), tempDir,
                new io.github.baekchangjoon.hotspotanalysis.config.OutputConfig(
                        java.util.List.of(io.github.baekchangjoon.hotspotanalysis.config.OutputConfig.OutputFormat.YAML),
                        tempDir.toString(),
                        0,
                        io.github.baekchangjoon.hotspotanalysis.config.OutputConfig.ApiLayout.BOTH),
                true);

        Path hotspotsYaml = tempDir.resolve("hotspots.yml");
        Path apiReportYaml = tempDir.resolve("api_report.yml");

        assertThat(hotspotsYaml).exists();
        assertThat(apiReportYaml).exists();

        ObjectMapper parser = new ObjectMapper(new YAMLFactory())
                .registerModule(new JavaTimeModule());
        
        Map<String, Object> parsedCombined = parser.readValue(hotspotsYaml.toFile(), Map.class);
        assertThat(parsedCombined).containsKeys("meta", "fileHotspots", "methodHotspots", "apiHotspots", "sharedComponents");

        Map<String, Object> parsedStandalone = parser.readValue(apiReportYaml.toFile(), Map.class);
        assertThat(parsedStandalone).containsKeys("meta", "apiHotspots", "sharedComponents");
        assertThat(parsedStandalone).doesNotContainKey("fileHotspots");
        assertThat(parsedStandalone).doesNotContainKey("methodHotspots");
    }
}
