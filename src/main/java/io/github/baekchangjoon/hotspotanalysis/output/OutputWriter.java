package io.github.baekchangjoon.hotspotanalysis.output;

import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.config.OutputConfig;

import java.nio.file.Path;

/**
 * Serialises an {@link AnalysisResult} into one or more files inside the
 * given output directory. Implementations are stateless Spring components,
 * one per {@link OutputConfig.OutputFormat}.
 */
public interface OutputWriter {

    /** The single format this writer handles. */
    OutputConfig.OutputFormat format();

    /**
     * Writes the report files for this format under {@code outputDir}.
     * Implementations may produce multiple files (e.g. CSV emits one file per
     * granularity).
     */
    void write(AnalysisResult result, Path outputDir);

    default void write(AnalysisResult result, Path outputDir, OutputConfig outputConfig, boolean apiEnabled) {
        write(result, outputDir, outputConfig, apiEnabled, false);
    }

    /**
     * Writes the report files. When {@code excludeCoverage=true}, the
     * coverage-multiplier column is replaced with a raw line-coverage column
     * at the rightmost position; otherwise the canonical 7-metric block is
     * emitted unchanged.
     */
    default void write(AnalysisResult result, Path outputDir, OutputConfig outputConfig,
                       boolean apiEnabled, boolean excludeCoverage) {
        write(result, outputDir, outputConfig, apiEnabled);
    }
}
