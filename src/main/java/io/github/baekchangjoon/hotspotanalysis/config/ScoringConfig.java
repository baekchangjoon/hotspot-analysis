package io.github.baekchangjoon.hotspotanalysis.config;

/**
 * Scoring configuration for the unified scoring model.
 *
 * <p>The only configurable knob is the half-life (in days) for the
 * exponential recency-decay weight applied to each commit. The
 * Simple / Composite scores themselves are always computed; there is
 * no longer a {@code formula} toggle.</p>
 */
public record ScoringConfig(
        Integer decayHalfLifeDays
) {
    public ScoringConfig {
        if (decayHalfLifeDays == null) {
            decayHalfLifeDays = 90;
        }
        if (decayHalfLifeDays <= 0) {
            throw new IllegalArgumentException(
                    "decayHalfLifeDays must be > 0 (was " + decayHalfLifeDays + ")");
        }
    }

    public ScoringConfig() {
        this(90);
    }
}
