package io.github.baekchangjoon.hotspotanalysis.vcs.model;

/**
 * The kind of change that a commit applied to a single file path.
 */
public enum ChangeType {
    /** The file was created by this commit. */
    ADDED,
    /** The file existed before and was changed in place. */
    MODIFIED,
    /** The file was removed by this commit. */
    DELETED,
    /** The file was moved or renamed; previousPath holds the old name. */
    RENAMED
}
