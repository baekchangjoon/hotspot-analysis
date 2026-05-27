package io.github.baekchangjoon.hotspotanalysis.analysis.model;

import java.util.Objects;

/**
 * Per-file hotspot result. Carries the four input factors (revisions,
 * recency decay, cognitive complexity, coverage multiplier) plus both
 * derived scores (Simple, Composite) in canonical order.
 *
 * <p>{@code lineCoverage} carries the raw line-coverage ratio in
 * {@code [0.0, 1.0]} when a JaCoCo report is supplied, otherwise
 * {@code null}. It is reported at the rightmost column only when
 * {@code scoring.excludeCoverage=true}; in default mode the coverage
 * multiplier column is used instead.</p>
 */
public record FileHotspot(
        String path,
        int loc,
        int revisions,
        double simpleScore,
        double recencyDecay,
        double cognitiveComplexity,
        double coverageMultiplier,
        double compositeScore,
        Double lineCoverage
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
        if (lineCoverage != null && (lineCoverage < 0.0 || lineCoverage > 1.0)) {
            throw new IllegalArgumentException(
                    "lineCoverage must be in [0.0, 1.0] (was " + lineCoverage + ")");
        }
    }

    public FileHotspot(String path, int loc, int revisions,
                       double simpleScore, double recencyDecay,
                       double cognitiveComplexity, double coverageMultiplier,
                       double compositeScore) {
        this(path, loc, revisions, simpleScore, recencyDecay,
                cognitiveComplexity, coverageMultiplier, compositeScore, null);
    }
}
