package io.github.baekchangjoon.hotspotanalysis.vcs.github;

import java.util.Objects;

/**
 * Minimal DTO mirroring the file-change payload returned by the GitHub commits
 * API, decoupling our domain model from the {@code github-api} library types.
 */
public record GhFileChange(
        String filename,
        String previousFilename,
        String status,
        int additions,
        int deletions
) {

    public GhFileChange {
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(status, "status");
        if (additions < 0 || deletions < 0) {
            throw new IllegalArgumentException(
                    "additions/deletions must be >= 0 (was " + additions + "/" + deletions + ")");
        }
    }
}
