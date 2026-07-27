package io.github.baekchangjoon.hotspotanalysis.output;

import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.config.OutputConfig;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fan-out dispatcher: applies every {@link OutputWriter} whose format is
 * listed in {@link OutputConfig#formats()} to the same {@link AnalysisResult}.
 */
@Component
public class OutputDispatcher {

    private final Map<OutputConfig.OutputFormat, OutputWriter> writers;

    public OutputDispatcher(List<OutputWriter> writers) {
        Objects.requireNonNull(writers, "writers");
        Map<OutputConfig.OutputFormat, OutputWriter> byFormat =
                new EnumMap<>(OutputConfig.OutputFormat.class);
        for (OutputWriter writer : writers) {
            OutputWriter existing = byFormat.put(writer.format(), writer);
            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate OutputWriter registered for format " + writer.format());
            }
        }
        this.writers = Collections.unmodifiableMap(byFormat);
    }

    public void dispatch(AnalysisResult result, OutputConfig outputConfig) {
        dispatch(result, outputConfig, false, false);
    }

    public void dispatch(AnalysisResult result, OutputConfig outputConfig, boolean apiEnabled) {
        dispatch(result, outputConfig, apiEnabled, false);
    }

    public void dispatch(AnalysisResult result, OutputConfig outputConfig,
                         boolean apiEnabled, boolean excludeCoverage) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(outputConfig, "outputConfig");
        Path outputDir = Path.of(io.github.baekchangjoon.hotspotanalysis.config.TildeExpansion.expand(outputConfig.path())).toAbsolutePath().normalize();
        for (OutputConfig.OutputFormat fmt : outputConfig.formats()) {
            OutputWriter writer = writers.get(fmt);
            if (writer == null) {
                throw new OutputException("No OutputWriter registered for format " + fmt);
            }
            writer.write(result, outputDir, outputConfig, apiEnabled, excludeCoverage);
        }
        // Opt-in calculation trace; only meaningful when a JaCoCo report fed
        // the run (result carries a breakdown then).
        if (Boolean.TRUE.equals(outputConfig.coverageBreakdown())
                && result.coverageBreakdown() != null) {
            breakdownWriter.write(result.coverageBreakdown(), outputDir);
        }
    }

    private final CoverageBreakdownWriter breakdownWriter = new CoverageBreakdownWriter();
}
