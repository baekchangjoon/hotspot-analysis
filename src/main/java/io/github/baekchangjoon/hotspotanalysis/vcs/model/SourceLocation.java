package io.github.baekchangjoon.hotspotanalysis.vcs.model;

import java.util.Objects;

/**
 * Where the analysed revision lives: the raw remote URL exactly as configured
 * in git (may carry credentials — never render it verbatim) and the full
 * commit id the analysis ran against.
 */
public record SourceLocation(String remoteUrl, String revision) {
    public SourceLocation {
        Objects.requireNonNull(remoteUrl, "remoteUrl");
        Objects.requireNonNull(revision, "revision");
    }
}
