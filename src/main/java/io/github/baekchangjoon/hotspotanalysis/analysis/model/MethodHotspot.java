package io.github.baekchangjoon.hotspotanalysis.analysis.model;

import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;

import java.util.Objects;

/**
 * Per-method hotspot result. Carries the enclosing file path and the
 * declaration's line range so output writers can emit "go to source" hints.
 * Carries the four input factors plus both derived scores in canonical order.
 *
 * <p>{@code lineCoverage} carries the raw line-coverage ratio in
 * {@code [0.0, 1.0]} when a JaCoCo report is supplied, otherwise
 * {@code null}.</p>
 */
public record MethodHotspot(
        MethodSignature signature,
        String filePath,
        int startLine,
        int endLine,
        int loc,
        int revisions,
        double simpleScore,
        double recencyDecay,
        double cognitiveComplexity,
        double coverageMultiplier,
        double compositeScore,
        Double lineCoverage
) {
    public MethodHotspot {
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(filePath, "filePath");
        if (filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be blank");
        }
        if (startLine < 1 || endLine < startLine) {
            throw new IllegalArgumentException(
                    "invalid line range: [" + startLine + ", " + endLine + "]");
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

    public MethodHotspot(MethodSignature signature, String filePath,
                         int startLine, int endLine,
                         int loc, int revisions,
                         double simpleScore, double recencyDecay,
                         double cognitiveComplexity, double coverageMultiplier,
                         double compositeScore) {
        this(signature, filePath, startLine, endLine, loc, revisions,
                simpleScore, recencyDecay, cognitiveComplexity,
                coverageMultiplier, compositeScore, null);
    }
}
