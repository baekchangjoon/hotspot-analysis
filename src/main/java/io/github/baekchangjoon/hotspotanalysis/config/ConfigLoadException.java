package io.github.baekchangjoon.hotspotanalysis.config;

/**
 * Raised when a configuration file cannot be read or parsed.
 *
 * <p>Covers I/O failures, malformed YAML, unknown enum values, and missing
 * environment variable references. Bean-validation errors are reported via
 * the more specific {@link ConfigValidationException}.</p>
 */
public class ConfigLoadException extends RuntimeException {

    public ConfigLoadException(String message) {
        super(message);
    }

    public ConfigLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
