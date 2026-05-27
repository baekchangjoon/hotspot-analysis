package io.github.baekchangjoon.hotspotanalysis.analysis.model;

import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;

import java.util.List;
import java.util.Objects;

/**
 * Per-REST-API hotspot result. Carries the four input factors plus both
 * derived scores in canonical order.
 *
 * <p>{@code lineCoverage} carries the raw line-coverage ratio in
 * {@code [0.0, 1.0]} when a JaCoCo report is supplied, otherwise
 * {@code null}.</p>
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
        List<MethodSignature> callGraph,
        Double lineCoverage
) {
    public ApiHotspot {
        Objects.requireNonNull(httpMethod, "httpMethod");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(controllerMethod, "controllerMethod");
        if (httpMethod.isBlank()) {
            throw new IllegalArgumentException("httpMethod must not be blank");
        }
        if (route.isBlank()) {
            throw new IllegalArgumentException("route must not be blank");
        }
        callGraph = callGraph == null ? List.of() : List.copyOf(callGraph);
        if (loc < 0 || revisions < 0) {
            throw new IllegalArgumentException("loc and revisions must be >= 0");
        }
        if (coverageMultiplier <= 0) {
            throw new IllegalArgumentException("coverageMultiplier must be > 0");
        }
        if (lineCoverage != null && (lineCoverage < 0.0 || lineCoverage > 1.0)) {
            throw new IllegalArgumentException(
                    "lineCoverage must be in [0.0, 1.0] (was " + lineCoverage + ")");
        }
    }

    public ApiHotspot(String httpMethod, String route, MethodSignature controllerMethod,
                      int loc, int revisions,
                      double simpleScore, double recencyDecay,
                      double cognitiveComplexity, double coverageMultiplier,
                      double compositeScore, List<MethodSignature> callGraph) {
        this(httpMethod, route, controllerMethod, loc, revisions,
                simpleScore, recencyDecay, cognitiveComplexity,
                coverageMultiplier, compositeScore, callGraph, null);
    }
}
