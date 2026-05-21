package io.github.baekchangjoon.hotspotanalysis.analysis;

/**
 * Raised when {@link JavaSourceCollector} cannot enumerate sources under the
 * repository root.
 */
public class SourceScanException extends RuntimeException {

    public SourceScanException(String message, Throwable cause) {
        super(message, cause);
    }
}
