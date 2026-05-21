package io.github.baekchangjoon.hotspotanalysis.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotNull;

/**
 * Hotspot scoring formula selection. Phase 1 supports only the simple
 * {@code revisions * loc} formula; additional formulas will be added later.
 */
public record ScoringConfig(
        @NotNull Formula formula
) {

    public enum Formula {
        SIMPLE;

        @JsonCreator
        public static Formula from(String raw) {
            if (raw == null) {
                return null;
            }
            return Formula.valueOf(raw.trim().toUpperCase());
        }
    }
}
