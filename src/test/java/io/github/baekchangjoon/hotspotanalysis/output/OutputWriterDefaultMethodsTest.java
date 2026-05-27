package io.github.baekchangjoon.hotspotanalysis.output;

import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisMeta;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.config.OutputConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression test for the {@link OutputWriter} interface default methods.
 *
 * <p>Earlier revisions of the interface introduced a latent infinite
 * recursion: the four-arg default delegated to the five-arg default, and
 * the five-arg default delegated back to the four-arg default. The
 * production writers all override both methods, so the bug never
 * triggered in the existing test surface — but a future writer that
 * overrides only the two-arg {@code write(result, outputDir)} method
 * would have stack-overflowed at runtime.</p>
 *
 * <p>This test exercises the default chain explicitly by defining a
 * minimal writer that only implements the two-arg method, and ensures
 * both the four-arg and five-arg defaults delegate to it cleanly.</p>
 */
class OutputWriterDefaultMethodsTest {

    /** Counts how many times the two-arg write was invoked. */
    private static final class CountingWriter implements OutputWriter {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public OutputConfig.OutputFormat format() {
            return OutputConfig.OutputFormat.CSV;
        }

        @Override
        public void write(AnalysisResult result, Path outputDir) {
            calls.incrementAndGet();
        }
    }

    private static AnalysisResult emptyResult() {
        return new AnalysisResult(
                List.of(), List.of(),
                new AnalysisMeta(Instant.parse("2026-05-27T00:00:00Z"),
                        "LOCAL_GIT:/tmp", 0, 0, 0));
    }

    @Test
    @DisplayName("four-arg default delegates to two-arg write without recursion")
    void fourArgDefaultDelegatesToTwoArg(@TempDir Path tempDir) {
        CountingWriter writer = new CountingWriter();
        OutputConfig cfg = new OutputConfig(List.of(OutputConfig.OutputFormat.CSV), tempDir.toString(), 0);

        assertThatCode(() -> writer.write(emptyResult(), tempDir, cfg, false))
                .doesNotThrowAnyException();
        assertThat(writer.calls).hasValue(1);
    }

    @Test
    @DisplayName("five-arg default with excludeCoverage delegates down to two-arg write without recursion")
    void fiveArgDefaultDelegatesWithoutRecursion(@TempDir Path tempDir) {
        CountingWriter writer = new CountingWriter();
        OutputConfig cfg = new OutputConfig(List.of(OutputConfig.OutputFormat.CSV), tempDir.toString(), 0);

        // Critical: this used to stack-overflow because the 5-arg default
        // called the 4-arg default which called the 5-arg default again.
        assertThatCode(() -> writer.write(emptyResult(), tempDir, cfg, /* apiEnabled */ true, /* excludeCoverage */ true))
                .doesNotThrowAnyException();
        assertThat(writer.calls).hasValue(1);
    }
}
