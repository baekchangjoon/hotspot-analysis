package io.github.baekchangjoon.hotspotanalysis.parser.model;

import java.util.Objects;

/**
 * Name + declared type of a single method parameter, captured from the source.
 *
 * <p>The type string is whatever JavaParser renders for the parameter type
 * (e.g. {@code int}, {@code List<String>}, {@code java.util.Map<K, V>}).
 * Generic type erasure and import resolution are not performed in Phase 1.</p>
 */
public record ParameterInfo(String name, String type) {

    public ParameterInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }
}
