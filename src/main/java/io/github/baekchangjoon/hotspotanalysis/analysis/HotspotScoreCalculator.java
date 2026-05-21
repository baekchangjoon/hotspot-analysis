package io.github.baekchangjoon.hotspotanalysis.analysis;

import io.github.baekchangjoon.hotspotanalysis.config.ScoringConfig;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Applies the configured scoring formula to a pair of metrics
 * ({@code revisions}, {@code loc}).
 *
 * <p>Phase 1 supports {@link ScoringConfig.Formula#SIMPLE} only:
 * <pre>
 *     score = revisions × loc
 * </pre>
 * This is Adam Tornhill's "Your Code as a Crime Scene" original proxy — the
 * empirical strongest single predictor of bug density across multiple studies
 * (e.g. Tornhill 2015; Bird et al. 2009 reports change-coupling × size with
 * Spearman ρ ≈ 0.55 against defect counts).</p>
 *
 * <p>Future formulas (e.g. log-weighted, recency-decayed) will be added as
 * additional enum values in {@link ScoringConfig.Formula}.</p>
 */
@Component
public class HotspotScoreCalculator {

    public double calculate(int revisions, int loc, ScoringConfig.Formula formula) {
        Objects.requireNonNull(formula, "formula");
        if (revisions < 0) {
            throw new IllegalArgumentException(
                    "revisions must be >= 0 (was " + revisions + ")");
        }
        if (loc < 0) {
            throw new IllegalArgumentException(
                    "loc must be >= 0 (was " + loc + ")");
        }
        return switch (formula) {
            case SIMPLE -> (double) revisions * loc;
        };
    }
}
