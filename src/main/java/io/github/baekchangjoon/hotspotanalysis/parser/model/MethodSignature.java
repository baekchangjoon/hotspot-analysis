package io.github.baekchangjoon.hotspotanalysis.parser.model;

import java.util.List;
import java.util.Objects;

/**
 * Disambiguates a method across overloads and class hierarchies using its
 * fully-qualified class name, method name, and parameter type list.
 *
 * <p>The canonical form rendered by {@link #toCanonicalString()} is also the
 * stable identifier used in output reports (CSV/YAML/MD).</p>
 */
public record MethodSignature(
        String fullyQualifiedClassName,
        String methodName,
        List<String> parameterTypes
) {

    public MethodSignature {
        Objects.requireNonNull(fullyQualifiedClassName, "fullyQualifiedClassName");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(parameterTypes, "parameterTypes");
        parameterTypes = List.copyOf(parameterTypes);
    }

    public String toCanonicalString() {
        return fullyQualifiedClassName + "#" + methodName
                + "(" + String.join(", ", parameterTypes) + ")";
    }
}
