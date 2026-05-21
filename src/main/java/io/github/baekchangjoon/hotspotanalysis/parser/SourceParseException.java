package io.github.baekchangjoon.hotspotanalysis.parser;

/**
 * Raised when a Java source file cannot be read or parsed by
 * {@link JavaSourceParser}.
 *
 * <p>Wraps both I/O failures and JavaParser-side parse problems behind a
 * single, project-local exception type.</p>
 */
public class SourceParseException extends RuntimeException {

    public SourceParseException(String message) {
        super(message);
    }

    public SourceParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
