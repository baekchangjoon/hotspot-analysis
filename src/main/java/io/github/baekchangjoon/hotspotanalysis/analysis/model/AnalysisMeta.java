package io.github.baekchangjoon.hotspotanalysis.analysis.model;

import io.github.baekchangjoon.hotspotanalysis.config.ScoringConfig;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate metadata about a single analysis run. Embedded in
 * {@link AnalysisResult} and surfaced verbatim in YAML/MD reports.
 */
public record AnalysisMeta(
        Instant analyzedAt,
        String targetDescription,
        int totalCommits,
        int totalFiles,
        int totalMethods,
        ScoringConfig.Formula scoringFormula
) {

    public AnalysisMeta {
        Objects.requireNonNull(analyzedAt, "analyzedAt");
        Objects.requireNonNull(targetDescription, "targetDescription");
        Objects.requireNonNull(scoringFormula, "scoringFormula");
        if (totalCommits < 0 || totalFiles < 0 || totalMethods < 0) {
            throw new IllegalArgumentException(
                    "counts must be >= 0 (commits=" + totalCommits
                            + " files=" + totalFiles + " methods=" + totalMethods + ")");
        }
    }
}
