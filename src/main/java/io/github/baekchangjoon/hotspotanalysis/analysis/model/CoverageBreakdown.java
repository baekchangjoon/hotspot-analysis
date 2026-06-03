package io.github.baekchangjoon.hotspotanalysis.analysis.model;

import java.util.List;

/**
 * Calculation trace for every coverage number that appears in the reports.
 * Written to {@code coverage_breakdown.yml} when
 * {@code output.coverageBreakdown: true} and a JaCoCo report was supplied, so
 * a final value like {@code lineCoverage: 0.2778} can be audited down to which
 * file/method contributed how many covered/executable lines.
 *
 * <p>{@code lineCoverage} fields are {@code null} when {@code executableLines}
 * is 0 (no instrumentation data — the artifact contributes nothing to any
 * line-weighted aggregate).</p>
 */
public record CoverageBreakdown(
        String jacocoReportPath,
        List<FileCoverage> files,
        List<ApiCoverage> apis
) {
    public CoverageBreakdown {
        files = files == null ? List.of() : List.copyOf(files);
        apis = apis == null ? List.of() : List.copyOf(apis);
    }

    /** Whole-file counts behind the file-level lineCoverage/multiplier. */
    public record FileCoverage(
            String path,
            int coveredLines,
            int executableLines,
            Double lineCoverage
    ) {}

    /**
     * Per-endpoint aggregate plus the per-method contributions that feed it:
     * {@code lineCoverage = coveredLines / executableLines} where the sums run
     * over the non-excluded methods below.
     */
    public record ApiCoverage(
            String httpMethod,
            String route,
            int coveredLines,
            int executableLines,
            Double lineCoverage,
            Double coverageMultiplier,
            List<MethodContribution> methods
    ) {
        public ApiCoverage {
            methods = methods == null ? List.of() : List.copyOf(methods);
        }
    }

    /**
     * One method's contribution to an endpoint aggregate. {@code note} is
     * {@code null} for a plain contribution, or explains why the method adds
     * nothing ("no coverage data") / is excluded from the sums
     * ("excluded: shared component (SEPARATE)").
     */
    public record MethodContribution(
            String signature,
            String file,
            Integer startLine,
            Integer endLine,
            int coveredLines,
            int executableLines,
            Double coverage,
            String note
    ) {}
}
