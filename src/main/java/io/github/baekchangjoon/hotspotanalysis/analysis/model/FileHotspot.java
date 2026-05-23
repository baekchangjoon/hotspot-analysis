package io.github.baekchangjoon.hotspotanalysis.analysis.model;

import java.util.Objects;

/**
 * Per-file hotspot result: revisions × LOC → score (Phase 1 SIMPLE formula).
 */
public record FileHotspot(
        String path,
        int revisions,
        int loc,
        double score,
        Double decayedRevisions,
        Double cognitiveComplexity,
        Double coverage
) {

    public FileHotspot {
        Objects.requireNonNull(path, "path");
        if (path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (revisions < 0) {
            throw new IllegalArgumentException("revisions must be >= 0");
        }
        if (loc < 0) {
            throw new IllegalArgumentException("loc must be >= 0");
        }
    }

    public FileHotspot(String path, int revisions, int loc, double score) {
        this(path, revisions, loc, score, null, null, null);
    }
}
