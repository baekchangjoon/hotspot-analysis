package io.github.baekchangjoon.hotspotanalysis.analysis;

import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisMeta;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.FileHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.MethodHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.ApiHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.CoverageBreakdown;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.SharedComponentHotspot;
import io.github.baekchangjoon.hotspotanalysis.config.AnalysisConfig;
import io.github.baekchangjoon.hotspotanalysis.config.TargetConfig;
import io.github.baekchangjoon.hotspotanalysis.config.ApiAnalysisConfig;
import io.github.baekchangjoon.hotspotanalysis.coverage.JacocoReportParser;
import io.github.baekchangjoon.hotspotanalysis.parser.JavaSourceParser;
import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodInfo;
import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;
import io.github.baekchangjoon.hotspotanalysis.parser.model.ApiMappingInfo;
import io.github.baekchangjoon.hotspotanalysis.vcs.VcsProvider;
import io.github.baekchangjoon.hotspotanalysis.vcs.VcsProviderFactory;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.CommitRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Orchestrates an end-to-end hotspot analysis run using the unified
 * scoring model: every file and method always receives both the
 * Simple (revisions × LOC) and Composite (cognitive-complexity ×
 * recency-decay × coverage-multiplier) scores, plus the four input
 * factors that feed them.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Build the right {@link VcsProvider} for {@link TargetConfig}.</li>
 *   <li>Load commits inside the configured {@code window}.</li>
 *   <li>Walk the working tree, collect Java files matching the {@code scope}.</li>
 *   <li>Parse each file → list of {@link MethodInfo}.</li>
 *   <li>Compute revisions, LOC, recency-decayed revisions, cognitive
 *       complexity, and coverage multiplier for every file & method.</li>
 *   <li>Combine via {@link HotspotScoreCalculator} into both Simple and
 *       Composite scores.</li>
 *   <li>Sort by descending Composite score; apply {@code output.topN}
 *       if set.</li>
 *   <li>Return immutable {@link AnalysisResult}.</li>
 * </ol>
 *
 * <p><b>Phase 1 limitation</b>: only {@code local-git} target is fully wired
 * end-to-end. {@code github} target requires a follow-up that either clones
 * the repository or parses the GitHub raw API for source content. Until
 * then, a {@link UnsupportedOperationException} is raised with a clear
 * remediation hint.</p>
 */
@Component
public class HotspotAnalyzer {

    private static final JacocoReportParser.LineCounts ZERO_COVERAGE =
            new JacocoReportParser.LineCounts(0, 0);

    private final VcsProviderFactory providerFactory;
    private final JavaSourceCollector sourceCollector;
    private final JavaSourceParser sourceParser;
    private final RevisionsCalculator revisionsCalculator;
    private final LocCalculator locCalculator;
    private final HotspotScoreCalculator scoreCalculator;
    private final CallGraphBuilder callGraphBuilder;

    @Autowired
    public HotspotAnalyzer(VcsProviderFactory providerFactory,
                           JavaSourceCollector sourceCollector,
                           JavaSourceParser sourceParser,
                           RevisionsCalculator revisionsCalculator,
                           LocCalculator locCalculator,
                           HotspotScoreCalculator scoreCalculator) {
        this(providerFactory, sourceCollector, sourceParser, revisionsCalculator, locCalculator, scoreCalculator, new CallGraphBuilder());
    }

    public HotspotAnalyzer(VcsProviderFactory providerFactory,
                           JavaSourceCollector sourceCollector,
                           JavaSourceParser sourceParser,
                           RevisionsCalculator revisionsCalculator,
                           LocCalculator locCalculator,
                           HotspotScoreCalculator scoreCalculator,
                           CallGraphBuilder callGraphBuilder) {
        this.providerFactory = Objects.requireNonNull(providerFactory);
        this.sourceCollector = Objects.requireNonNull(sourceCollector);
        this.sourceParser = Objects.requireNonNull(sourceParser);
        this.revisionsCalculator = Objects.requireNonNull(revisionsCalculator);
        this.locCalculator = Objects.requireNonNull(locCalculator);
        this.scoreCalculator = Objects.requireNonNull(scoreCalculator);
        this.callGraphBuilder = Objects.requireNonNull(callGraphBuilder);
    }

    public AnalysisResult analyze(AnalysisConfig config) {
        Objects.requireNonNull(config, "config");
        TargetConfig target = config.analysis().target();
        if (target.type() != TargetConfig.TargetType.LOCAL_GIT) {
            throw new UnsupportedOperationException(
                    "Phase 1 CLI supports only target.type=local-git for end-to-end analysis."
                            + " Clone the GitHub repository to a local path and re-run with"
                            + " target.type=local-git.");
        }

        Path repoRoot = Path.of(target.path()).toAbsolutePath().normalize();
        VcsProvider provider = providerFactory.create(target);
        List<CommitRecord> commits = provider.loadCommits(config.analysis().window());

        List<Path> javaFiles = sourceCollector.collect(repoRoot, config.analysis().scope());
        Map<String, List<MethodInfo>> methodsByFile = parseAll(repoRoot, javaFiles);

        Map<String, Integer> fileRevisions =
                revisionsCalculator.calculateFileRevisions(commits);
        Map<MethodSignature, Integer> methodRevisions =
                revisionsCalculator.calculateMethodRevisions(commits, methodsByFile);
        Map<String, Integer> fileLoc =
                locCalculator.countLines(repoRoot, methodsByFile.keySet());

        Instant untilInstant = (config.analysis().window().until() != null)
                ? config.analysis().window().until().plusDays(1)
                        .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().minusNanos(1)
                : Instant.now();
        int halfLifeDays = config.analysis().scoring().decayHalfLifeDays();

        Map<String, Double> fileDecayed =
                revisionsCalculator.calculateFileDecayedRevisions(commits, halfLifeDays, untilInstant);
        Map<MethodSignature, Double> methodDecayed =
                revisionsCalculator.calculateMethodDecayedRevisions(commits, methodsByFile, halfLifeDays, untilInstant);

        boolean jacocoSupplied =
                config.analysis().jacocoReportPath() != null
                        && !config.analysis().jacocoReportPath().isEmpty();
        io.github.baekchangjoon.hotspotanalysis.coverage.JacocoReportParser jacocoParser =
                new io.github.baekchangjoon.hotspotanalysis.coverage.JacocoReportParser();
        if (jacocoSupplied) {
            Path p = Path.of(config.analysis().jacocoReportPath());
            if (!p.isAbsolute()) p = repoRoot.resolve(p);
            if (!java.nio.file.Files.isRegularFile(p)) {
                // A missing report must not silently mark every artifact as 0%
                // covered (which would inflate every coverage multiplier to its
                // 1/0.1 = 10 maximum). Warn and proceed as if no coverage was
                // supplied, so the scores stay trustworthy.
                System.err.println("WARNING: analysis.jacocoReportPath is set but no file exists at "
                        + p + " — proceeding WITHOUT coverage (multiplier = 1.0). "
                        + "Generate a JaCoCo XML report (e.g. './gradlew test jacocoTestReport') "
                        + "or remove analysis.jacocoReportPath.");
                jacocoSupplied = false;
            } else {
                jacocoParser.parse(p);
            }
        }

        boolean excludeCoverage = Boolean.TRUE.equals(config.analysis().scoring().excludeCoverage());

        List<FileHotspot> files = buildFileHotspots(
                methodsByFile, fileRevisions, fileLoc, fileDecayed, jacocoParser, jacocoSupplied, excludeCoverage);
        List<MethodHotspot> methods = buildMethodHotspots(
                methodsByFile, methodRevisions, methodDecayed, jacocoParser, jacocoSupplied, excludeCoverage);

        List<ApiHotspot> apiHotspots = new ArrayList<>();
        List<SharedComponentHotspot> sharedComponents = new ArrayList<>();
        List<CoverageBreakdown.ApiCoverage> apiCoverageBreakdown = new ArrayList<>();
        if (config.analysis().apiAnalysis() != null && config.analysis().apiAnalysis().enabled()) {
            buildApiAndShared(repoRoot, config, javaFiles, methodsByFile,
                    methodRevisions, methodDecayed, jacocoParser, jacocoSupplied, excludeCoverage,
                    apiHotspots, sharedComponents, apiCoverageBreakdown);
        }

        // Audit trail behind every reported coverage number. Built over the FULL
        // result (before topN truncation) so each value stays verifiable.
        CoverageBreakdown breakdown = null;
        if (jacocoSupplied) {
            List<CoverageBreakdown.FileCoverage> fileCoverages = methodsByFile.keySet().stream()
                    .sorted()
                    .map(path -> {
                        JacocoReportParser.LineCounts c = jacocoParser.getFileLineCounts(path);
                        Double cov = c.executable() == 0 ? null : (double) c.covered() / c.executable();
                        return new CoverageBreakdown.FileCoverage(path, c.covered(), c.executable(), cov);
                    })
                    .toList();
            apiCoverageBreakdown.sort(
                    Comparator.comparing(CoverageBreakdown.ApiCoverage::route)
                            .thenComparing(CoverageBreakdown.ApiCoverage::httpMethod));
            breakdown = new CoverageBreakdown(
                    config.analysis().jacocoReportPath(), fileCoverages, apiCoverageBreakdown);
        }

        int topN = config.output().topN();
        if (topN > 0) {
            files = takeTop(files, topN);
            methods = takeTop(methods, topN);
            if (!apiHotspots.isEmpty()) apiHotspots = takeTop(apiHotspots, topN);
            if (!sharedComponents.isEmpty()) sharedComponents = takeTop(sharedComponents, topN);
        }

        AnalysisMeta meta = new AnalysisMeta(
                Instant.now(),
                "LOCAL_GIT:" + repoRoot,
                commits.size(),
                methodsByFile.size(),
                countMethods(methodsByFile));
        return new AnalysisResult(files, methods, apiHotspots, sharedComponents, meta, breakdown);
    }

    private Map<String, List<MethodInfo>> parseAll(Path repoRoot, List<Path> javaFiles) {
        Map<String, List<MethodInfo>> result = new HashMap<>();
        for (Path file : javaFiles) {
            String relative = repoRoot.relativize(file).toString().replace('\\', '/');
            result.put(relative, sourceParser.parse(file));
        }
        return result;
    }

    private List<FileHotspot> buildFileHotspots(
            Map<String, List<MethodInfo>> methodsByFile,
            Map<String, Integer> fileRevisions,
            Map<String, Integer> fileLoc,
            Map<String, Double> fileDecayed,
            io.github.baekchangjoon.hotspotanalysis.coverage.JacocoReportParser jacoco,
            boolean jacocoSupplied,
            boolean excludeCoverage) {
        List<FileHotspot> out = new ArrayList<>();
        for (Map.Entry<String, List<MethodInfo>> e : methodsByFile.entrySet()) {
            String path = e.getKey();
            int revisions = fileRevisions.getOrDefault(path, 0);
            int loc = fileLoc.getOrDefault(path, 0);
            double simple = scoreCalculator.simple(revisions, loc);
            double decayed = fileDecayed.getOrDefault(path, 0.0);
            double cc = 0.0;
            for (MethodInfo m : e.getValue()) cc += m.cognitiveComplexity();
            Double rawCoverage = jacocoSupplied ? jacoco.getFileCoverage(path) : null;
            double mult = excludeCoverage
                    ? 1.0
                    : scoreCalculator.multiplier(
                            jacocoSupplied ? OptionalDouble.of(rawCoverage)
                                           : OptionalDouble.empty());
            double composite = excludeCoverage
                    ? scoreCalculator.composite(cc, decayed)
                    : scoreCalculator.composite(cc, decayed, mult);
            out.add(new FileHotspot(path, loc, revisions, simple, decayed, cc, mult, composite, rawCoverage));
        }
        out.sort(Comparator.comparingDouble(FileHotspot::compositeScore).reversed()
                .thenComparing(FileHotspot::path));
        return out;
    }

    private List<MethodHotspot> buildMethodHotspots(
            Map<String, List<MethodInfo>> methodsByFile,
            Map<MethodSignature, Integer> methodRevisions,
            Map<MethodSignature, Double> methodDecayed,
            io.github.baekchangjoon.hotspotanalysis.coverage.JacocoReportParser jacoco,
            boolean jacocoSupplied,
            boolean excludeCoverage) {
        List<MethodHotspot> out = new ArrayList<>();
        for (Map.Entry<String, List<MethodInfo>> e : methodsByFile.entrySet()) {
            String path = e.getKey();
            for (MethodInfo m : e.getValue()) {
                int revisions = methodRevisions.getOrDefault(m.signature(), 0);
                int loc = m.lineCount();
                double simple = scoreCalculator.simple(revisions, loc);
                double decayed = methodDecayed.getOrDefault(m.signature(), 0.0);
                double cc = m.cognitiveComplexity();
                Double rawCoverage = jacocoSupplied
                        ? jacoco.getMethodCoverage(path, m.startLine(), m.endLine())
                        : null;
                double mult = excludeCoverage
                        ? 1.0
                        : scoreCalculator.multiplier(
                                jacocoSupplied
                                        ? OptionalDouble.of(rawCoverage)
                                        : OptionalDouble.empty());
                double composite = excludeCoverage
                        ? scoreCalculator.composite(cc, decayed)
                        : scoreCalculator.composite(cc, decayed, mult);
                out.add(new MethodHotspot(
                        m.signature(), path, m.startLine(), m.endLine(),
                        loc, revisions, simple, decayed, cc, mult, composite, rawCoverage));
            }
        }
        out.sort(Comparator.comparingDouble(MethodHotspot::compositeScore).reversed()
                .thenComparing(h -> h.signature().toCanonicalString()));
        return out;
    }

    private void buildApiAndShared(
            Path repoRoot,
            AnalysisConfig config,
            List<Path> javaFiles,
            Map<String, List<MethodInfo>> methodsByFile,
            Map<MethodSignature, Integer> methodRevisions,
            Map<MethodSignature, Double> methodDecayed,
            io.github.baekchangjoon.hotspotanalysis.coverage.JacocoReportParser jacoco,
            boolean jacocoSupplied,
            boolean excludeCoverage,
            List<ApiHotspot> apiOut,
            List<SharedComponentHotspot> sharedOut,
            List<CoverageBreakdown.ApiCoverage> apiCovOut) {

        ApiAnalysisConfig apiConfig = config.analysis().apiAnalysis();
        CallGraphBuilder.CallGraphResult cgResult = callGraphBuilder.buildCallGraphs(
                repoRoot, javaFiles, apiConfig.classpathDirectories());

        // Per-method auxiliary maps: LOC, cognitive complexity, precomputed
        // coverage, source location, and API mappings.
        Map<MethodSignature, Integer> locMap = new HashMap<>();
        Map<MethodSignature, Double> methodCcs = new HashMap<>();
        Map<MethodSignature, Double> methodCovs = new HashMap<>();
        Map<MethodSignature, JacocoReportParser.LineCounts> methodLineCounts = new HashMap<>();
        Map<MethodSignature, String> methodFile = new HashMap<>();
        Map<MethodSignature, MethodInfo> methodInfos = new HashMap<>();
        Map<MethodSignature, List<ApiMappingInfo>> apiMappingsMap = new HashMap<>();
        for (Map.Entry<String, List<MethodInfo>> entry : methodsByFile.entrySet()) {
            String path = entry.getKey();
            for (MethodInfo m : entry.getValue()) {
                locMap.put(m.signature(), m.lineCount());
                methodCcs.put(m.signature(), (double) m.cognitiveComplexity());
                methodFile.put(m.signature(), path);
                methodInfos.put(m.signature(), m);
                if (jacocoSupplied) {
                    methodCovs.put(m.signature(),
                            jacoco.getMethodCoverage(path, m.startLine(), m.endLine()));
                    methodLineCounts.put(m.signature(),
                            jacoco.getMethodLineCounts(path, m.startLine(), m.endLine()));
                }
                if (m.apiMappings() != null && !m.apiMappings().isEmpty()) {
                    apiMappingsMap.put(m.signature(), m.apiMappings());
                }
            }
        }

        Map<MethodSignature, List<MethodSignature>> callGraphs = cgResult.callGraphs();
        Map<MethodSignature, List<String>> callingApisMap = new HashMap<>();
        for (Map.Entry<MethodSignature, List<MethodSignature>> entry : callGraphs.entrySet()) {
            MethodSignature apiMethod = entry.getKey();
            List<ApiMappingInfo> mappings = apiMappingsMap.get(apiMethod);
            if (mappings == null) continue;
            for (ApiMappingInfo mapping : mappings) {
                String apiSigStr = mapping.httpMethod() + " " + mapping.route();
                for (MethodSignature calledMethod : entry.getValue()) {
                    callingApisMap.computeIfAbsent(calledMethod, k -> new ArrayList<>()).add(apiSigStr);
                }
            }
        }

        Set<MethodSignature> sharedComponentSignatures = new HashSet<>();
        if (apiConfig.sharedComponentMode() == ApiAnalysisConfig.SharedComponentMode.SEPARATE ||
            apiConfig.sharedComponentMode() == ApiAnalysisConfig.SharedComponentMode.BOTH) {
            for (Map.Entry<MethodSignature, List<String>> entry : callingApisMap.entrySet()) {
                if (entry.getValue().size() >= 2) {
                    sharedComponentSignatures.add(entry.getKey());
                }
            }
        }

        // Shared components.
        for (MethodSignature sharedSig : sharedComponentSignatures) {
            int revs = methodRevisions.getOrDefault(sharedSig, 0);
            int loc = locMap.getOrDefault(sharedSig, 0);
            double simple = scoreCalculator.simple(revs, loc);
            double decayed = methodDecayed.getOrDefault(sharedSig, 0.0);
            double cc = methodCcs.getOrDefault(sharedSig, 0.0);
            Double rawCoverage = jacocoSupplied ? methodCovs.getOrDefault(sharedSig, 0.0) : null;
            double mult = excludeCoverage
                    ? 1.0
                    : (jacocoSupplied
                            ? scoreCalculator.multiplier(OptionalDouble.of(rawCoverage))
                            : 1.0);
            double composite = excludeCoverage
                    ? scoreCalculator.composite(cc, decayed)
                    : scoreCalculator.composite(cc, decayed, mult);
            List<String> callingApis = callingApisMap.getOrDefault(sharedSig, List.of());
            sharedOut.add(new SharedComponentHotspot(
                    sharedSig, loc, revs, simple, decayed, cc, mult, composite, callingApis, rawCoverage));
        }
        sharedOut.sort(Comparator.comparingDouble(SharedComponentHotspot::compositeScore).reversed()
                .thenComparing(sc -> sc.method().toCanonicalString()));

        // API hotspots — aggregate the controller method and all called methods along its call graph.
        for (Map.Entry<MethodSignature, List<MethodSignature>> entry : callGraphs.entrySet()) {
            MethodSignature controllerMethod = entry.getKey();
            List<ApiMappingInfo> mappings = apiMappingsMap.get(controllerMethod);
            if (mappings == null) continue;

            for (ApiMappingInfo mapping : mappings) {
                int apiRevs = methodRevisions.getOrDefault(controllerMethod, 0);
                int apiLoc = locMap.getOrDefault(controllerMethod, 0);
                double apiDecayed = methodDecayed.getOrDefault(controllerMethod, 0.0);
                double apiCc = methodCcs.getOrDefault(controllerMethod, 0.0);

                // Line-weighted coverage over the call graph: sum covered and
                // instrumented lines across all methods, then divide once. A
                // simple mean of per-method ratios would let a tiny fully-covered
                // helper offset a large untested method (Simpson's paradox).
                JacocoReportParser.LineCounts ctrlCov = jacocoSupplied
                        ? methodLineCounts.getOrDefault(controllerMethod, ZERO_COVERAGE) : ZERO_COVERAGE;
                int coveredSum = ctrlCov.covered();
                int execSum = ctrlCov.executable();

                List<CoverageBreakdown.MethodContribution> contributions = jacocoSupplied
                        ? new ArrayList<>() : null;
                if (contributions != null) {
                    contributions.add(contribution(
                            controllerMethod, ctrlCov, methodFile, methodInfos, null));
                }

                List<MethodSignature> filteredCallGraph = new ArrayList<>();
                for (MethodSignature calledMethod : entry.getValue()) {
                    boolean isShared = sharedComponentSignatures.contains(calledMethod);
                    boolean exclude = isShared && (apiConfig.sharedComponentMode()
                            == ApiAnalysisConfig.SharedComponentMode.SEPARATE);

                    if (!exclude) {
                        apiRevs += methodRevisions.getOrDefault(calledMethod, 0);
                        apiLoc += locMap.getOrDefault(calledMethod, 0);
                        apiDecayed += methodDecayed.getOrDefault(calledMethod, 0.0);
                        apiCc += methodCcs.getOrDefault(calledMethod, 0.0);
                        if (jacocoSupplied) {
                            JacocoReportParser.LineCounts c =
                                    methodLineCounts.getOrDefault(calledMethod, ZERO_COVERAGE);
                            coveredSum += c.covered();
                            execSum += c.executable();
                        }
                    }
                    if (contributions != null) {
                        contributions.add(contribution(
                                calledMethod,
                                methodLineCounts.getOrDefault(calledMethod, ZERO_COVERAGE),
                                methodFile, methodInfos,
                                exclude ? "excluded: shared component (SEPARATE)" : null));
                    }
                    filteredCallGraph.add(calledMethod);
                }

                double apiSimple = scoreCalculator.simple(apiRevs, apiLoc);
                Double avgCoverage = (jacocoSupplied && execSum > 0)
                        ? (double) coveredSum / execSum : null;
                double mult = excludeCoverage || avgCoverage == null
                        ? 1.0
                        : scoreCalculator.multiplier(OptionalDouble.of(avgCoverage));
                double apiComposite = excludeCoverage
                        ? scoreCalculator.composite(apiCc, apiDecayed)
                        : scoreCalculator.composite(apiCc, apiDecayed, mult);

                if (contributions != null) {
                    apiCovOut.add(new CoverageBreakdown.ApiCoverage(
                            mapping.httpMethod(),
                            mapping.route(),
                            coveredSum,
                            execSum,
                            avgCoverage,
                            avgCoverage == null ? null : 1.0 / (avgCoverage + 0.1),
                            contributions));
                }

                apiOut.add(new ApiHotspot(
                        mapping.httpMethod(),
                        mapping.route(),
                        controllerMethod,
                        apiLoc,
                        apiRevs,
                        apiSimple,
                        apiDecayed,
                        apiCc,
                        mult,
                        apiComposite,
                        filteredCallGraph,
                        avgCoverage));
            }
        }
        apiOut.sort(Comparator.comparingDouble(ApiHotspot::compositeScore).reversed()
                .thenComparing(ApiHotspot::route)
                .thenComparing(ApiHotspot::httpMethod));
    }

    /** One method's row in the per-endpoint coverage audit trail. */
    private static CoverageBreakdown.MethodContribution contribution(
            MethodSignature sig,
            JacocoReportParser.LineCounts counts,
            Map<MethodSignature, String> methodFile,
            Map<MethodSignature, MethodInfo> methodInfos,
            String note) {
        MethodInfo info = methodInfos.get(sig);
        String resolvedNote = note != null
                ? note
                : (counts.executable() == 0 ? "no coverage data — contributes nothing" : null);
        return new CoverageBreakdown.MethodContribution(
                sig.toCanonicalString(),
                methodFile.get(sig),
                info == null ? null : info.startLine(),
                info == null ? null : info.endLine(),
                counts.covered(),
                counts.executable(),
                counts.executable() == 0 ? null : (double) counts.covered() / counts.executable(),
                resolvedNote);
    }

    private static <T> List<T> takeTop(List<T> source, int n) {
        return source.size() <= n ? source : List.copyOf(source.subList(0, n));
    }

    private static int countMethods(Map<String, List<MethodInfo>> methodsByFile) {
        return methodsByFile.values().stream().mapToInt(List::size).sum();
    }
}
