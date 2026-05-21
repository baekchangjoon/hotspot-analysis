package io.github.baekchangjoon.hotspotanalysis.output;

import io.github.baekchangjoon.hotspotanalysis.config.OutputConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutputDispatcherTest {

    @Test
    @DisplayName("invokes every writer whose format is listed in OutputConfig")
    void shouldInvokeAllRequestedWriters(@TempDir Path tempDir) {
        OutputDispatcher dispatcher = new OutputDispatcher(List.of(
                new CsvOutputWriter(),
                new YamlOutputWriter(),
                new MarkdownOutputWriter()));
        OutputConfig output = new OutputConfig(
                List.of(OutputConfig.OutputFormat.CSV,
                        OutputConfig.OutputFormat.YAML,
                        OutputConfig.OutputFormat.MD),
                tempDir.toString(), 0);

        dispatcher.dispatch(OutputWriterTestFixtures.sampleResult(), output);

        assertThat(Files.exists(tempDir.resolve("file_hotspots.csv"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("method_hotspots.csv"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("hotspots.yml"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("hotspots.md"))).isTrue();
    }

    @Test
    @DisplayName("skips formats not present in OutputConfig.formats()")
    void shouldSkipUnlistedFormats(@TempDir Path tempDir) {
        OutputDispatcher dispatcher = new OutputDispatcher(List.of(
                new CsvOutputWriter(),
                new YamlOutputWriter(),
                new MarkdownOutputWriter()));
        OutputConfig output = new OutputConfig(
                List.of(OutputConfig.OutputFormat.CSV),
                tempDir.toString(), 0);

        dispatcher.dispatch(OutputWriterTestFixtures.sampleResult(), output);

        assertThat(Files.exists(tempDir.resolve("file_hotspots.csv"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("hotspots.yml"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("hotspots.md"))).isFalse();
    }

    @Test
    @DisplayName("rejects duplicate registrations for the same format")
    void shouldRejectDuplicateRegistrations() {
        assertThatThrownBy(() -> new OutputDispatcher(List.of(
                new CsvOutputWriter(),
                new CsvOutputWriter())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CSV");
    }
}
