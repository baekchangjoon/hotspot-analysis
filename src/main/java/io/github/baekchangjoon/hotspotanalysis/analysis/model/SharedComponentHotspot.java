package io.github.baekchangjoon.hotspotanalysis.analysis.model;

import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;

import java.util.List;
import java.util.Objects;

/**
 * Hotspot result for a shared component (e.g. service, repository method) accessed by multiple APIs.
 * Carries the four input factors plus both derived scores in canonical order.
 *
 * <p>{@code lineCoverage} carries the raw line-coverage ratio in
 * {@code [0.0, 1.0]} when a JaCoCo report is supplied, otherwise
 * {@code null}.</p>
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
        List<String> callingApis,
        Double lineCoverage
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
        if (lineCoverage != null && (lineCoverage < 0.0 || lineCoverage > 1.0)) {
            throw new IllegalArgumentException(
                    "lineCoverage must be in [0.0, 1.0] (was " + lineCoverage + ")");
        }
    }

    public SharedComponentHotspot(MethodSignature method,
                                  int loc, int revisions,
                                  double simpleScore, double recencyDecay,
                                  double cognitiveComplexity, double coverageMultiplier,
                                  double compositeScore, List<String> callingApis) {
        this(method, loc, revisions, simpleScore, recencyDecay,
                cognitiveComplexity, coverageMultiplier, compositeScore,
                callingApis, null);
    }
}
