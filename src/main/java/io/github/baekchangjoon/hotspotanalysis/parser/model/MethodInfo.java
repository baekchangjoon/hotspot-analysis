package io.github.baekchangjoon.hotspotanalysis.parser.model;

import java.util.List;
import java.util.Objects;

/**
 * Method declaration extracted from a Java source file.
 *
 * <p>{@code startLine} / {@code endLine} are 1-based inclusive line numbers
 * spanning the entire declaration (signature through closing brace), and
 * are the basis for the line-range × diff-hunk overlap calculation in T7.</p>
 */
public record MethodInfo(
        MethodSignature signature,
        int startLine,
        int endLine,
        List<ParameterInfo> parameters,
        List<ApiMappingInfo> apiMappings
) {

    public MethodInfo {
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(parameters, "parameters");
        if (startLine < 1) {
            throw new IllegalArgumentException(
                    "startLine must be >= 1 (was " + startLine + ")");
        }
        if (endLine < startLine) {
            throw new IllegalArgumentException(
                    "endLine must be >= startLine (was " + endLine + " < " + startLine + ")");
        }
        parameters = List.copyOf(parameters);
        if (apiMappings == null) {
            apiMappings = List.of();
        } else {
            apiMappings = List.copyOf(apiMappings);
        }
    }

    public MethodInfo(MethodSignature signature, int startLine, int endLine, List<ParameterInfo> parameters) {
        this(signature, startLine, endLine, parameters, List.of());
    }

    public int lineCount() {
        return endLine - startLine + 1;
    }
}
