package io.github.baekchangjoon.hotspotanalysis.vcs.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable view of a single commit observed by a {@code VcsProvider}.
 *
 * <p>{@code hash} is the commit identifier (typically a full or abbreviated
 * SHA-1). {@code author} is a free-form display name. {@code committedAt} uses
 * commit time (not author time) so chronological windows are precise.
 * {@code changes} is a defensively-copied immutable list of per-file changes.</p>
 */
public record CommitRecord(
        String hash,
        String author,
        Instant committedAt,
        String message,
        List<FileChange> changes
) {

    public CommitRecord {
        Objects.requireNonNull(hash, "hash must not be null");
        Objects.requireNonNull(author, "author must not be null");
        Objects.requireNonNull(committedAt, "committedAt must not be null");
        Objects.requireNonNull(changes, "changes must not be null");
        if (hash.isBlank()) {
            throw new IllegalArgumentException("hash must not be blank");
        }
        message = message == null ? "" : message;
        changes = List.copyOf(changes);
    }
}
