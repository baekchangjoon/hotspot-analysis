package io.github.baekchangjoon.hotspotanalysis.analysis.model;

import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;

import java.util.List;
import java.util.Objects;

/**
 * Per-REST-API hotspot result.
 */
public record ApiHotspot(
        String httpMethod,
        String route,
        MethodSignature controllerMethod,
        int revisions,
        int loc,
        double score,
        List<MethodSignature> callGraph
) {

    public ApiHotspot {
        Objects.requireNonNull(httpMethod, "httpMethod");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(controllerMethod, "controllerMethod");
        Objects.requireNonNull(callGraph, "callGraph");
        if (httpMethod.isBlank()) {
            throw new IllegalArgumentException("httpMethod must not be blank");
        }
        if (route.isBlank()) {
            throw new IllegalArgumentException("route must not be blank");
        }
        if (revisions < 0 || loc < 0) {
            throw new IllegalArgumentException(
                    "revisions and loc must both be >= 0 (was " + revisions + " / " + loc + ")");
        }
        callGraph = List.copyOf(callGraph);
    }
}
