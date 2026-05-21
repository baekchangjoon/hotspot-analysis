package io.github.baekchangjoon.hotspotanalysis.vcs.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents a single file's modification within a commit.
 *
 * <p>{@code path} is always the repository-root-relative POSIX path
 * of the file as it exists <em>after</em> the commit (the new name on a
 * rename). For {@link ChangeType#RENAMED rename} changes, {@code previousPath}
 * carries the prior name; for all other change types it must be {@code null}.</p>
 *
 * <p>{@code linesAdded} and {@code linesDeleted} are non-negative integers
 * counted from the unified diff against the commit's parent.</p>
 *
 * <p>{@code hunks} is an optional list of {@link DiffHunk}s — the new-file
 * line ranges that the diff inserted or replaced. When present, it lets
 * {@code RevisionsCalculator} count method-level revisions precisely. When
 * empty (e.g. from {@code GithubProvider} which does not parse patch text in
 * Phase 1), method-level revisions fall back to the file-level count.</p>
 */
public record FileChange(
        String path,
        String previousPath,
        int linesAdded,
        int linesDeleted,
        ChangeType type,
        List<DiffHunk> hunks
) {

    public FileChange {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(type, "type must not be null");
        if (path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (linesAdded < 0) {
            throw new IllegalArgumentException(
                    "linesAdded must be >= 0 (was " + linesAdded + ")");
        }
        if (linesDeleted < 0) {
            throw new IllegalArgumentException(
                    "linesDeleted must be >= 0 (was " + linesDeleted + ")");
        }
        if (type == ChangeType.RENAMED && (previousPath == null || previousPath.isBlank())) {
            throw new IllegalArgumentException(
                    "previousPath is required when type=RENAMED");
        }
        if (type != ChangeType.RENAMED && previousPath != null) {
            throw new IllegalArgumentException(
                    "previousPath must be null when type=" + type);
        }
        hunks = hunks == null ? List.of() : List.copyOf(hunks);
    }

    /**
     * Convenience constructor for callers that don't supply hunk-level info.
     * The resulting {@link FileChange} will have an empty {@code hunks} list,
     * which causes {@code RevisionsCalculator} to fall back to file-level
     * counts at method granularity.
     */
    public FileChange(String path,
                      String previousPath,
                      int linesAdded,
                      int linesDeleted,
                      ChangeType type) {
        this(path, previousPath, linesAdded, linesDeleted, type, List.of());
    }
}
