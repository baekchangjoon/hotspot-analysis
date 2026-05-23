package io.github.baekchangjoon.hotspotanalysis.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Container for the {@code analysis:} block in the configuration file.
 */
public record AnalysisSection(
        @NotNull(message = "analysis.target is required") @Valid TargetConfig target,
        @NotNull(message = "analysis.window is required") @Valid WindowConfig window,
        @NotNull(message = "analysis.scope is required") @Valid ScopeConfig scope,
        @NotNull(message = "analysis.scoring is required") @Valid ScoringConfig scoring,
        @Valid ApiAnalysisConfig apiAnalysis,
        String jacocoReportPath
) {
    public AnalysisSection {
        if (apiAnalysis == null) {
            apiAnalysis = new ApiAnalysisConfig(false, ApiAnalysisConfig.SharedComponentMode.BOTH, java.util.List.of());
        }
    }

    public AnalysisSection(TargetConfig target, WindowConfig window, ScopeConfig scope, ScoringConfig scoring, ApiAnalysisConfig apiAnalysis) {
        this(target, window, scope, scoring, apiAnalysis, null);
    }

    public AnalysisSection(TargetConfig target, WindowConfig window, ScopeConfig scope, ScoringConfig scoring) {
        this(target, window, scope, scoring, new ApiAnalysisConfig(false, ApiAnalysisConfig.SharedComponentMode.BOTH, java.util.List.of()), null);
    }
}
