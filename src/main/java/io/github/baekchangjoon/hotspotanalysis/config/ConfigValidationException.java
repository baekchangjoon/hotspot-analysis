package io.github.baekchangjoon.hotspotanalysis.config;

import java.util.List;

/**
 * Raised when a configuration file parses successfully but violates one or
 * more Bean Validation constraints or cross-field invariants.
 */
public class ConfigValidationException extends ConfigLoadException {

    private final List<String> violations;

    public ConfigValidationException(String message, List<String> violations) {
        super(message);
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
