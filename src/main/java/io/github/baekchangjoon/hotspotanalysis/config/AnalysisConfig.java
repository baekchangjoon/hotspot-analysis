package io.github.baekchangjoon.hotspotanalysis.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Root of the YAML configuration tree. Composed of the analysis section and
 * the output section.
 *
 * <pre>{@code
 * analysis:
 *   target:   { ... }
 *   window:   { ... }
 *   scope:    { ... }
 *   scoring:  { ... }
 * output:
 *   formats:  [csv, md]
 *   path:     ./out
 *   topN:     50
 * }</pre>
 */
public record AnalysisConfig(
        @NotNull(message = "analysis section is required") @Valid AnalysisSection analysis,
        @NotNull(message = "output section is required") @Valid OutputConfig output
) {
}
