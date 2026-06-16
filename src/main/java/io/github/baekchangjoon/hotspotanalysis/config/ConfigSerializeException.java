package io.github.baekchangjoon.hotspotanalysis.config;

/**
 * Thrown when an {@link AnalysisConfig} cannot be rendered back to YAML (e.g.
 * for {@code analyze --print-config}). Distinct from
 * {@link ConfigSynthesisException} (which means the config could not be derived
 * from the project) so callers can tell the two failure modes apart.
 */
public class ConfigSerializeException extends RuntimeException {
    public ConfigSerializeException(String message, Throwable cause) {
        super(message, cause);
    }
}
