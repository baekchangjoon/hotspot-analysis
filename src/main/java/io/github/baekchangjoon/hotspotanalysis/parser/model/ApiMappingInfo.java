package io.github.baekchangjoon.hotspotanalysis.parser.model;

import java.util.Objects;

/**
 * Metadata representing a single REST API mapping (HTTP method and route path).
 */
public record ApiMappingInfo(
        String httpMethod,
        String route
) {
    public ApiMappingInfo {
        Objects.requireNonNull(httpMethod, "httpMethod");
        Objects.requireNonNull(route, "route");
        httpMethod = httpMethod.trim().toUpperCase();
        route = normalizeRoute(route);
    }

    private static String normalizeRoute(String route) {
        String clean = route.trim().replace('\\', '/');
        if (!clean.startsWith("/")) {
            clean = "/" + clean;
        }
        if (clean.endsWith("/") && clean.length() > 1) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean.replaceAll("//+", "/");
    }
}
