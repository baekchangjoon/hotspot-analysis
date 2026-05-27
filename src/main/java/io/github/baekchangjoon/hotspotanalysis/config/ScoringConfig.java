package io.github.baekchangjoon.hotspotanalysis.config;

/**
 * Scoring configuration for the unified scoring model.
 *
 * <p>Knobs:
 * <ul>
 *   <li>{@code decayHalfLifeDays} — half-life (days) for the exponential
 *       recency-decay weight applied to each commit.</li>
 *   <li>{@code excludeCoverage} — when {@code true}, coverage is no longer
 *       part of the Composite Score (Composite = Cognitive Complexity ×
 *       Recency Decay), and reports surface the raw line-coverage
 *       percentage at the rightmost column instead of the
 *       coverage-multiplier column. When {@code false} (default), behaviour
 *       is unchanged: coverage flows into the Composite Score via
 *       {@code 1 / (line_coverage + 0.1)}.</li>
 * </ul>
 */
public record ScoringConfig(
        Integer decayHalfLifeDays,
        Boolean excludeCoverage
) {
    public ScoringConfig {
        if (decayHalfLifeDays == null) {
            decayHalfLifeDays = 90;
        }
        if (decayHalfLifeDays <= 0) {
            throw new IllegalArgumentException(
                    "decayHalfLifeDays must be > 0 (was " + decayHalfLifeDays + ")");
        }
        if (excludeCoverage == null) {
            excludeCoverage = Boolean.FALSE;
        }
    }

    public ScoringConfig() {
        this(90, Boolean.FALSE);
    }

    public ScoringConfig(Integer decayHalfLifeDays) {
        this(decayHalfLifeDays, Boolean.FALSE);
    }
}
