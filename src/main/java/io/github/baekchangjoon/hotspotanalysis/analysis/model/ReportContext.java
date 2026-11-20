package io.github.baekchangjoon.hotspotanalysis.analysis.model;

/**
 * Run-level facts the interactive HTML map needs but that are not hotspot rows:
 * where the analysed source lives (for "open on GitHub" links), the decay
 * half-life the scores were computed with, and the composite-score total over
 * <em>every</em> analysed file — taken before {@code output.topN} truncation so
 * "the top 3 files hold X% of the risk" stays exact when the lists are cut.
 *
 * <p>{@code sourceBlobBase} is a URL prefix ending in {@code /} to which a
 * repository-relative path can be appended (e.g.
 * {@code https://github.com/acme/app/blob/<sha>/}); it and {@code revision}
 * are {@code null} when the remote is unknown or not a recognised host.</p>
 */
public record ReportContext(
        String sourceBlobBase,
        String revision,
        int decayHalfLifeDays,
        double totalFileCompositeScore
) {
    public ReportContext {
        if (decayHalfLifeDays < 1) {
            throw new IllegalArgumentException(
                    "decayHalfLifeDays must be >= 1 (was " + decayHalfLifeDays + ")");
        }
        if (totalFileCompositeScore < 0) {
            throw new IllegalArgumentException(
                    "totalFileCompositeScore must be >= 0 (was " + totalFileCompositeScore + ")");
        }
    }
}
