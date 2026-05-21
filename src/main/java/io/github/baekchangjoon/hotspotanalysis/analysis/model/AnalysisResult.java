package io.github.baekchangjoon.hotspotanalysis.analysis.model;

import java.util.List;
import java.util.Objects;

/**
 * Top-level immutable result of a hotspot analysis run. Both lists are sorted
 * by descending score, and limited by {@code output.topN} if it was set.
 */
public record AnalysisResult(
        List<FileHotspot> fileHotspots,
        List<MethodHotspot> methodHotspots,
        List<ApiHotspot> apiHotspots,
        List<SharedComponentHotspot> sharedComponents,
        AnalysisMeta meta
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
            AnalysisMeta meta
    ) {
        this(fileHotspots, methodHotspots, List.of(), List.of(), meta);
    }
}
