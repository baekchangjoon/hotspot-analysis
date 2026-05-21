package io.github.baekchangjoon.hotspotanalysis.vcs.model;

/**
 * A contiguous line range in the post-commit (new) file that this commit's
 * diff modified (insert or replace). Lines are 1-based and inclusive on both
 * ends, matching git's {@code git log -L} convention.
 *
 * <p>Delete-only hunks (where the new file did not gain any line in place of
 * the removed lines) are not represented; they are dropped at the provider
 * layer. This is acceptable for hotspot scoring because deletion-only edits
 * inside a method are still counted at the file level — only the method-level
 * granularity loses a small amount of signal.</p>
 */
public record DiffHunk(int newStart, int newEnd) {

    public DiffHunk {
        if (newStart < 1) {
            throw new IllegalArgumentException(
                    "newStart must be >= 1 (was " + newStart + ")");
        }
        if (newEnd < newStart) {
            throw new IllegalArgumentException(
                    "newEnd must be >= newStart (was " + newEnd + " < " + newStart + ")");
        }
    }

    public boolean overlaps(int rangeStart, int rangeEnd) {
        return newStart <= rangeEnd && newEnd >= rangeStart;
    }
}
