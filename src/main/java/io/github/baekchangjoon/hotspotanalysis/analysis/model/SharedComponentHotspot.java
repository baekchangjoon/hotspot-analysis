package io.github.baekchangjoon.hotspotanalysis.analysis.model;

import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;

import java.util.List;
import java.util.Objects;

/**
 * Hotspot result for a shared component (e.g. service, repository method) accessed by multiple APIs.
 */
public record SharedComponentHotspot(
        MethodSignature method,
        int revisions,
        int loc,
        double score,
        List<String> callingApis
) {

    public SharedComponentHotspot {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(callingApis, "callingApis");
        if (revisions < 0 || loc < 0) {
            throw new IllegalArgumentException(
                    "revisions and loc must both be >= 0 (was " + revisions + " / " + loc + ")");
        }
        callingApis = List.copyOf(callingApis);
    }
}
