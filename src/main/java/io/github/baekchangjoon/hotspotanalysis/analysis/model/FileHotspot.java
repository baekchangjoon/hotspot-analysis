package io.github.baekchangjoon.hotspotanalysis.analysis.model;

import java.util.Objects;

/**
 * Per-file hotspot result. Carries the four input factors (revisions,
 * recency decay, cognitive complexity, coverage multiplier) plus both
 * derived scores (Simple, Composite) in canonical order.
 */
public record FileHotspot(
        String path,
        int loc,
        int revisions,
        double simpleScore,
        double recencyDecay,
        double cognitiveComplexity,
        double coverageMultiplier,
        double compositeScore
) {
    public FileHotspot {
        Objects.requireNonNull(path, "path");
        if (path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (loc < 0 || revisions < 0) {
            throw new IllegalArgumentException(
                    "loc and revisions must both be >= 0 (was " + loc + " / " + revisions + ")");
        }
        if (simpleScore < 0 || recencyDecay < 0 || cognitiveComplexity < 0
                || coverageMultiplier <= 0 || compositeScore < 0) {
            throw new IllegalArgumentException(
                    "metric values must be non-negative; coverageMultiplier must be > 0");
        }
    }
}
