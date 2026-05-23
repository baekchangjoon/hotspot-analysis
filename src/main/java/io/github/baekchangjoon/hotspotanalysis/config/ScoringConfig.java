package io.github.baekchangjoon.hotspotanalysis.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotNull;

/**
 * Hotspot scoring formula selection. Phase 1 supports only the simple
 * {@code revisions * loc} formula; additional formulas will be added later.
 */
public record ScoringConfig(
        @NotNull Formula formula,
        Integer decayHalfLifeDays
) {
    public ScoringConfig {
        if (decayHalfLifeDays == null) {
            decayHalfLifeDays = 90;
        }
    }

    public ScoringConfig(Formula formula) {
        this(formula, 90);
    }

    public enum Formula {
        SIMPLE,
        COMPOSITE;

        @JsonCreator
        public static Formula from(String raw) {
            if (raw == null) {
                return null;
            }
            return Formula.valueOf(raw.trim().toUpperCase());
        }
    }
}
