package io.github.baekchangjoon.hotspotanalysis.output;

/**
 * Raised when an {@link OutputWriter} cannot write its report.
 */
public class OutputException extends RuntimeException {

    public OutputException(String message) {
        super(message);
    }

    public OutputException(String message, Throwable cause) {
        super(message, cause);
    }
}
