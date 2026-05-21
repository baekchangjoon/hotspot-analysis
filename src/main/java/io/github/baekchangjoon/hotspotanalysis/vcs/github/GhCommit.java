package io.github.baekchangjoon.hotspotanalysis.vcs.github;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Minimal DTO mirroring the commit payload returned by the GitHub commits API,
 * decoupling our domain model from the {@code github-api} library types.
 */
public record GhCommit(
        String sha,
        String author,
        Instant committedAt,
        String message,
        List<GhFileChange> files
) {

    public GhCommit {
        Objects.requireNonNull(sha, "sha");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(committedAt, "committedAt");
        message = message == null ? "" : message;
        files = files == null ? List.of() : List.copyOf(files);
    }
}
