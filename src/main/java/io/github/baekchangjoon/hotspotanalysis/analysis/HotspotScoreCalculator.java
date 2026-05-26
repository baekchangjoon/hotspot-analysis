package io.github.baekchangjoon.hotspotanalysis.analysis;

import org.springframework.stereotype.Component;

import java.util.OptionalDouble;

/**
 * Pure helpers for the unified scoring model.
 *
 * <ul>
 *   <li>{@link #simple(int, int)} — Adam Tornhill's original {@code revisions × loc}.</li>
 *   <li>{@link #composite(double, double, double)} — cognitive complexity ×
 *       recency decay × coverage multiplier.</li>
 *   <li>{@link #multiplier(OptionalDouble)} — {@code 1/(coverage + 0.1)} or 1.0
 *       when no coverage data was supplied.</li>
 * </ul>
 */
@Component
public class HotspotScoreCalculator {

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

    public double multiplier(OptionalDouble coverage) {
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
