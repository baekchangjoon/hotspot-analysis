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
                new MarkdownOutputWriter(),
                new HtmlOutputWriter()));
        OutputConfig output = new OutputConfig(
                List.of(OutputConfig.OutputFormat.CSV,
                        OutputConfig.OutputFormat.YAML,
                        OutputConfig.OutputFormat.MD,
                        OutputConfig.OutputFormat.HTML),
                tempDir.toString(), 0);

        dispatcher.dispatch(OutputWriterTestFixtures.sampleResult(), output);

        assertThat(Files.exists(tempDir.resolve("file_hotspots.csv"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("method_hotspots.csv"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("hotspots.yml"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("hotspots.md"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("hotspots.html"))).isTrue();
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
    @DisplayName("writes coverage_breakdown.yml only when the flag is on and a breakdown exists")
    void shouldWriteCoverageBreakdownWhenEnabled(@TempDir Path tempDir) throws Exception {
        var base = OutputWriterTestFixtures.sampleResult();
        var breakdown = new io.github.baekchangjoon.hotspotanalysis.analysis.model.CoverageBreakdown(
                "build/jacoco.xml",
                List.of(new io.github.baekchangjoon.hotspotanalysis.analysis.model.CoverageBreakdown.FileCoverage(
                        "src/A.java", 5, 10, 0.5)),
                List.of(new io.github.baekchangjoon.hotspotanalysis.analysis.model.CoverageBreakdown.ApiCoverage(
                        "GET", "/api/x", 1, 9, 1.0 / 9.0, 1.0 / (1.0 / 9.0 + 0.1),
                        List.of(
                                new io.github.baekchangjoon.hotspotanalysis.analysis.model.CoverageBreakdown.MethodContribution(
                                        "com.example.A#m()", "src/A.java", 3, 5, 1, 1, 1.0, null),
                                new io.github.baekchangjoon.hotspotanalysis.analysis.model.CoverageBreakdown.MethodContribution(
                                        "com.example.B#big()", "src/B.java", 6, 20, 0, 8, 0.0, null)))));
        var result = new io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult(
                base.fileHotspots(), base.methodHotspots(), base.apiHotspots(),
                base.sharedComponents(), base.meta(), breakdown);

        OutputDispatcher dispatcher = new OutputDispatcher(List.of(new CsvOutputWriter()));

        // Default (flag off): no breakdown file even though the result carries one.
        OutputConfig off = new OutputConfig(
                List.of(OutputConfig.OutputFormat.CSV), tempDir.toString(), 0);
        dispatcher.dispatch(result, off);
        assertThat(Files.exists(tempDir.resolve("coverage_breakdown.yml"))).isFalse();

        // Flag on: file written with totals + per-method contributions.
        OutputConfig on = new OutputConfig(
                List.of(OutputConfig.OutputFormat.CSV), tempDir.toString(), 0,
                OutputConfig.ApiLayout.BOTH, Boolean.TRUE);
        dispatcher.dispatch(result, on);
        Path f = tempDir.resolve("coverage_breakdown.yml");
        assertThat(Files.exists(f)).isTrue();
        String yml = Files.readString(f);
        assertThat(yml)
                .contains("jacocoReport:")
                .contains("files:")
                .contains("apiHotspots:")
                .contains("coveredLines: 1")
                .contains("executableLines: 9")
                .contains("lineCoverage: 0.1111")
                .contains("6-20");
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
