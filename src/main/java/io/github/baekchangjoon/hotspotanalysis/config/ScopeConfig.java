package io.github.baekchangjoon.hotspotanalysis.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Scope of files considered for analysis and the granularity of the result.
 */
public record ScopeConfig(
        @NotEmpty(message = "scope.granularity must not be empty") List<Granularity> granularity,
        @NotEmpty(message = "scope.include must not be empty") List<String> include,
        List<String> exclude
) {

    public ScopeConfig {
        exclude = exclude == null ? List.of() : List.copyOf(exclude);
    }

    public enum Granularity {
        FILE,
        METHOD;

        @JsonCreator
        public static Granularity from(String raw) {
            if (raw == null) {
                return null;
            }
            return Granularity.valueOf(raw.trim().toUpperCase());
        }
    }
}
