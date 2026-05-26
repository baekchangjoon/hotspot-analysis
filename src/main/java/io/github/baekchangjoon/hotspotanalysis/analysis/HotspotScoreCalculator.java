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
            case COMPOSITE -> throw new UnsupportedOperationException("Use calculateComposite for COMPOSITE formula");
        };
    }

    public double calculateComposite(double decayedRevisions, double cognitiveComplexity, double coverage) {
        if (decayedRevisions < 0) {
            throw new IllegalArgumentException("decayedRevisions must be >= 0");
        }
        if (cognitiveComplexity < 0) {
            throw new IllegalArgumentException("cognitiveComplexity must be >= 0");
        }
        if (coverage < 0.0 || coverage > 1.0) {
            throw new IllegalArgumentException("coverage must be between 0.0 and 1.0");
        }
        return cognitiveComplexity * decayedRevisions * (1.0 / (coverage + 0.1));
    }

    public double simple(int revisions, int loc) {
        if (revisions < 0 || loc < 0) {
            throw new IllegalArgumentException(
                    "revisions and loc must both be >= 0 (was " + revisions + " / " + loc + ")");
        }
        return (double) revisions * loc;
    }

    public double composite(double cognitiveComplexity, double recencyDecay, double coverageMultiplier) {
        if (cognitiveComplexity < 0) {
            throw new IllegalArgumentException("cognitiveComplexity must be >= 0");
        }
        if (recencyDecay < 0) {
            throw new IllegalArgumentException("recencyDecay must be >= 0");
        }
        if (coverageMultiplier <= 0) {
            throw new IllegalArgumentException("coverageMultiplier must be > 0");
        }
        return cognitiveComplexity * recencyDecay * coverageMultiplier;
    }

    public double multiplier(java.util.OptionalDouble coverage) {
        if (coverage.isEmpty()) {
            return 1.0;
        }
        double cov = coverage.getAsDouble();
        if (cov < 0.0 || cov > 1.0) {
            throw new IllegalArgumentException("coverage must be in [0.0, 1.0] (was " + cov + ")");
        }
        return 1.0 / (cov + 0.1);
    }
}
