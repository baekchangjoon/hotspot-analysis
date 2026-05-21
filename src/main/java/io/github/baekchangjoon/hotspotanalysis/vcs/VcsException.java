package io.github.baekchangjoon.hotspotanalysis.vcs;

/**
 * Raised by a {@link VcsProvider} when the underlying repository cannot be
 * read (e.g. corrupt git directory, network error against a remote API,
 * authentication failure).
 */
public class VcsException extends RuntimeException {

    public VcsException(String message) {
        super(message);
    }

    public VcsException(String message, Throwable cause) {
        super(message, cause);
    }
}
