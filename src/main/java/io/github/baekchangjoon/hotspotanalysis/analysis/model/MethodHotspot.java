package io.github.baekchangjoon.hotspotanalysis.analysis.model;

import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;

import java.util.Objects;

/**
 * Per-method hotspot result. Carries the enclosing file path and the
 * declaration's line range so output writers can emit "go to source" hints.
 */
public record MethodHotspot(
        MethodSignature signature,
        String filePath,
        int startLine,
        int endLine,
        int revisions,
        int loc,
        double score,
        Double decayedRevisions,
        Double cognitiveComplexity,
        Double coverage
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
        if (revisions < 0 || loc < 0) {
            throw new IllegalArgumentException(
                    "revisions and loc must both be >= 0 (was " + revisions + " / " + loc + ")");
        }
    }

    public MethodHotspot(MethodSignature signature, String filePath, int startLine, int endLine, int revisions, int loc, double score) {
        this(signature, filePath, startLine, endLine, revisions, loc, score, null, null, null);
    }
}
