package io.github.baekchangjoon.hotspotanalysis.analysis.model;

import java.util.List;
import java.util.Objects;

/**
 * Top-level immutable result of a hotspot analysis run. Both lists are sorted
 * by descending score, and limited by {@code output.topN} if it was set.
 *
 * <p>{@code coverageBreakdown} carries the calculation trace behind every
 * coverage number (per-file and per-endpoint line counts); it is {@code null}
 * when no JaCoCo report was supplied.</p>
 */
public record AnalysisResult(
        List<FileHotspot> fileHotspots,
        List<MethodHotspot> methodHotspots,
        List<ApiHotspot> apiHotspots,
        List<SharedComponentHotspot> sharedComponents,
        AnalysisMeta meta,
        CoverageBreakdown coverageBreakdown
) {

    public AnalysisResult {
        Objects.requireNonNull(fileHotspots, "fileHotspots");
        Objects.requireNonNull(methodHotspots, "methodHotspots");
        Objects.requireNonNull(meta, "meta");
        fileHotspots = List.copyOf(fileHotspots);
        methodHotspots = List.copyOf(methodHotspots);
        if (apiHotspots == null) {
            apiHotspots = List.of();
        } else {
            apiHotspots = List.copyOf(apiHotspots);
        }
        if (sharedComponents == null) {
            sharedComponents = List.of();
        } else {
            sharedComponents = List.copyOf(sharedComponents);
        }
    }

    public AnalysisResult(
            List<FileHotspot> fileHotspots,
            List<MethodHotspot> methodHotspots,
            List<ApiHotspot> apiHotspots,
            List<SharedComponentHotspot> sharedComponents,
            AnalysisMeta meta
    ) {
        this(fileHotspots, methodHotspots, apiHotspots, sharedComponents, meta, null);
    }

    public AnalysisResult(
            List<FileHotspot> fileHotspots,
            List<MethodHotspot> methodHotspots,
            AnalysisMeta meta
    ) {
        this(fileHotspots, methodHotspots, List.of(), List.of(), meta, null);
    }
}
