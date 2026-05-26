package io.github.baekchangjoon.hotspotanalysis.analysis.model;

import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;

import java.util.List;
import java.util.Objects;

/**
 * Per-REST-API hotspot result. Carries the four input factors plus both
 * derived scores in canonical order.
 */
public record ApiHotspot(
        String httpMethod,
        String route,
        MethodSignature controllerMethod,
        int loc,
        int revisions,
        double simpleScore,
        double recencyDecay,
        double cognitiveComplexity,
        double coverageMultiplier,
        double compositeScore,
        List<MethodSignature> callGraph
) {
    public ApiHotspot {
        Objects.requireNonNull(httpMethod, "httpMethod");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(controllerMethod, "controllerMethod");
        callGraph = callGraph == null ? List.of() : List.copyOf(callGraph);
        if (loc < 0 || revisions < 0) {
            throw new IllegalArgumentException("loc and revisions must be >= 0");
        }
        if (coverageMultiplier <= 0) {
            throw new IllegalArgumentException("coverageMultiplier must be > 0");
        }
    }
}
