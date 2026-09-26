package io.github.baekchangjoon.hotspotanalysis.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.List;

/**
 * Configuration for Phase 2 RESTful API Hotspot Analysis.
 *
 * <p>{@code sharedComponentMode} defaults to {@link SharedComponentMode#BOTH}
 * when omitted, matching the documented default: a config that only sets
 * {@code apiAnalysis.enabled: true} must not fail validation.</p>
 */
public record ApiAnalysisConfig(
        boolean enabled,
        SharedComponentMode sharedComponentMode,
        List<String> classpathDirectories
) {
    public ApiAnalysisConfig {
        if (sharedComponentMode == null) {
            sharedComponentMode = SharedComponentMode.BOTH;
        }
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
