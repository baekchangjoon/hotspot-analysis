package io.github.baekchangjoon.hotspotanalysis.analysis;

/**
 * Raised when {@link LocCalculator} cannot read a file to count its lines.
 */
public class LocCalculationException extends RuntimeException {

    public LocCalculationException(String message, Throwable cause) {
        super(message, cause);
    }
}
