package io.github.baekchangjoon.hotspotanalysis.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Configuration for Phase 2 RESTful API Hotspot Analysis.
 */
public record ApiAnalysisConfig(
        boolean enabled,
        @NotNull(message = "apiAnalysis.sharedComponentMode is required") SharedComponentMode sharedComponentMode,
        List<String> classpathDirectories
) {
    public ApiAnalysisConfig {
        if (classpathDirectories == null) {
            classpathDirectories = List.of();
        } else {
            classpathDirectories = List.copyOf(classpathDirectories);
        }
    }

    public enum SharedComponentMode {
        CUMULATIVE,
        SEPARATE,
        BOTH;

        @JsonCreator
        public static SharedComponentMode from(String raw) {
            if (raw == null) {
                return null;
            }
            try {
                return SharedComponentMode.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "\"" + raw + "\" is not one of " + java.util.Arrays.toString(values()));
            }
        }
    }
}
