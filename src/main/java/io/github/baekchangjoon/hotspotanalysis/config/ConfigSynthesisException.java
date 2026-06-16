package io.github.baekchangjoon.hotspotanalysis.config;

/**
 * Thrown when zero-config synthesis cannot produce a usable configuration —
 * e.g. the base path is not a git work tree, or no Java sources were found.
 * Carries a human-readable, hint-bearing message for direct display on stderr.
 */
public class ConfigSynthesisException extends RuntimeException {
    public ConfigSynthesisException(String message) {
        super(message);
    }

    public ConfigSynthesisException(String message, Throwable cause) {
        super(message, cause);
    }
}
