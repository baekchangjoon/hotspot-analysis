package io.github.baekchangjoon.hotspotanalysis.analysis.model;

import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;

import java.util.List;
import java.util.Objects;

/**
 * Hotspot result for a shared component (e.g. service, repository method) accessed by multiple APIs.
 * Carries the four input factors plus both derived scores in canonical order.
 */
public record SharedComponentHotspot(
        MethodSignature method,
        int loc,
        int revisions,
        double simpleScore,
        double recencyDecay,
        double cognitiveComplexity,
        double coverageMultiplier,
        double compositeScore,
        List<String> callingApis
) {
    public SharedComponentHotspot {
        Objects.requireNonNull(method, "method");
        callingApis = callingApis == null ? List.of() : List.copyOf(callingApis);
        if (loc < 0 || revisions < 0) {
            throw new IllegalArgumentException("loc and revisions must be >= 0");
        }
        if (coverageMultiplier <= 0) {
            throw new IllegalArgumentException("coverageMultiplier must be > 0");
        }
    }
}
