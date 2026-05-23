package io.github.baekchangjoon.hotspotanalysis.analysis;

import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisMeta;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.FileHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.MethodHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.ApiHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.SharedComponentHotspot;
import io.github.baekchangjoon.hotspotanalysis.config.AnalysisConfig;
import io.github.baekchangjoon.hotspotanalysis.config.ScoringConfig;
import io.github.baekchangjoon.hotspotanalysis.config.TargetConfig;
import io.github.baekchangjoon.hotspotanalysis.config.ApiAnalysisConfig;
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
import java.util.Set;

/**
 * Orchestrates an end-to-end hotspot analysis run.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Build the right {@link VcsProvider} for {@link TargetConfig}.</li>
 *   <li>Load commits inside the configured {@code window}.</li>
 *   <li>Walk the working tree, collect Java files matching the {@code scope}.</li>
 *   <li>Parse each file → list of {@link MethodInfo}.</li>
 *   <li>Compute file-level and method-level revisions.</li>
 *   <li>Compute LOC per file (current working-tree value) and per method.</li>
 *   <li>Combine via {@link HotspotScoreCalculator} into score values.</li>
 *   <li>Sort by descending score, apply {@code output.topN} if set.</li>
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

        ScoringConfig.Formula formula = config.analysis().scoring().formula();
        List<FileHotspot> files;
        List<MethodHotspot> methods;

        io.github.baekchangjoon.hotspotanalysis.coverage.JacocoReportParser jacocoParser = new io.github.baekchangjoon.hotspotanalysis.coverage.JacocoReportParser();
        if (config.analysis().jacocoReportPath() != null && !config.analysis().jacocoReportPath().isEmpty()) {
            Path jacocoPath = Path.of(config.analysis().jacocoReportPath());
            if (!jacocoPath.isAbsolute()) {
                jacocoPath = repoRoot.resolve(jacocoPath);
            }
            jacocoParser.parse(jacocoPath);
        }

        Map<MethodSignature, Double> methodComplexities = new HashMap<>();
        Map<MethodSignature, Double> methodCoverages = new HashMap<>();
        for (Map.Entry<String, List<MethodInfo>> entry : methodsByFile.entrySet()) {
            String path = entry.getKey();
            for (MethodInfo m : entry.getValue()) {
                methodComplexities.put(m.signature(), (double) m.cognitiveComplexity());
                double cov = jacocoParser.getMethodCoverage(path, m.startLine(), m.endLine());
                methodCoverages.put(m.signature(), cov);
            }
        }

        if (formula == ScoringConfig.Formula.COMPOSITE) {
            Instant untilInstant = Instant.now();
            if (config.analysis().window().until() != null) {
                untilInstant = config.analysis().window().until().plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().minusNanos(1);
            }
            int halfLifeDays = config.analysis().scoring().decayHalfLifeDays() != null ? config.analysis().scoring().decayHalfLifeDays() : 90;

            Map<String, Double> fileDecayedRevisions = revisionsCalculator.calculateFileDecayedRevisions(commits, halfLifeDays, untilInstant);
            Map<MethodSignature, Double> methodDecayedRevisions = revisionsCalculator.calculateMethodDecayedRevisions(commits, methodsByFile, halfLifeDays, untilInstant);

            files = buildCompositeFileHotspots(methodsByFile, fileRevisions, fileLoc, fileDecayedRevisions, jacocoParser);
            methods = buildCompositeMethodHotspots(methodsByFile, methodRevisions, methodDecayedRevisions, jacocoParser);
        } else {
            files = buildFileHotspots(methodsByFile.keySet(), fileRevisions, fileLoc, formula);
            methods = buildMethodHotspots(methodsByFile, methodRevisions, formula);
        }

        List<ApiHotspot> apiHotspots = new ArrayList<>();
        List<SharedComponentHotspot> sharedComponents = new ArrayList<>();

        if (config.analysis().apiAnalysis() != null && config.analysis().apiAnalysis().enabled()) {
            ApiAnalysisConfig apiConfig = config.analysis().apiAnalysis();
            CallGraphBuilder.CallGraphResult cgResult = callGraphBuilder.buildCallGraphs(
                    repoRoot, javaFiles, apiConfig.classpathDirectories());

            Map<MethodSignature, Integer> locMap = new HashMap<>();
            Map<MethodSignature, List<ApiMappingInfo>> apiMappingsMap = new HashMap<>();
            for (List<MethodInfo> list : methodsByFile.values()) {
                for (MethodInfo m : list) {
                    locMap.put(m.signature(), m.lineCount());
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

            if (formula == ScoringConfig.Formula.COMPOSITE) {
                Instant untilInstant = Instant.now();
                if (config.analysis().window().until() != null) {
                    untilInstant = config.analysis().window().until().plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().minusNanos(1);
                }
                int halfLifeDays = config.analysis().scoring().decayHalfLifeDays() != null ? config.analysis().scoring().decayHalfLifeDays() : 90;
                Map<MethodSignature, Double> methodDecayedRevisions = revisionsCalculator.calculateMethodDecayedRevisions(commits, methodsByFile, halfLifeDays, untilInstant);

                for (MethodSignature sharedSig : sharedComponentSignatures) {
                    double decayedRevs = methodDecayedRevisions.getOrDefault(sharedSig, 0.0);
                    double complexity = methodComplexities.getOrDefault(sharedSig, 0.0);
                    double coverage = methodCoverages.getOrDefault(sharedSig, 0.0);
                    double score = scoreCalculator.calculateComposite(decayedRevs, complexity, coverage);
                    List<String> callingApis = callingApisMap.get(sharedSig);
                    sharedComponents.add(new SharedComponentHotspot(sharedSig, methodRevisions.getOrDefault(sharedSig, 0), locMap.getOrDefault(sharedSig, 0), score, callingApis));
                }
            } else {
                for (MethodSignature sharedSig : sharedComponentSignatures) {
                    int revs = methodRevisions.getOrDefault(sharedSig, 0);
                    int loc = locMap.getOrDefault(sharedSig, 0);
                    double score = scoreCalculator.calculate(revs, loc, formula);
                    List<String> callingApis = callingApisMap.get(sharedSig);
                    sharedComponents.add(new SharedComponentHotspot(sharedSig, revs, loc, score, callingApis));
                }
            }
            sharedComponents.sort(Comparator.comparingDouble(SharedComponentHotspot::score).reversed()
                    .thenComparing(sc -> sc.method().toCanonicalString()));

            if (formula == ScoringConfig.Formula.COMPOSITE) {
                Instant untilInstant = Instant.now();
                if (config.analysis().window().until() != null) {
                    untilInstant = config.analysis().window().until().plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().minusNanos(1);
                }
                int halfLifeDays = config.analysis().scoring().decayHalfLifeDays() != null ? config.analysis().scoring().decayHalfLifeDays() : 90;
                Map<MethodSignature, Double> methodDecayedRevisions = revisionsCalculator.calculateMethodDecayedRevisions(commits, methodsByFile, halfLifeDays, untilInstant);

                for (Map.Entry<MethodSignature, List<MethodSignature>> entry : callGraphs.entrySet()) {
                    MethodSignature controllerMethod = entry.getKey();
                    List<ApiMappingInfo> mappings = apiMappingsMap.get(controllerMethod);
                    if (mappings == null) continue;

                    for (ApiMappingInfo mapping : mappings) {
                        double apiDecayedRevs = methodDecayedRevisions.getOrDefault(controllerMethod, 0.0);
                        double apiComplexity = methodComplexities.getOrDefault(controllerMethod, 0.0);
                        double sumCoverage = methodCoverages.getOrDefault(controllerMethod, 0.0);
                        int count = 1;

                        int apiRevs = methodRevisions.getOrDefault(controllerMethod, 0);
                        int apiLoc = locMap.getOrDefault(controllerMethod, 0);

                        List<MethodSignature> filteredCallGraph = new ArrayList<>();

                        for (MethodSignature calledMethod : entry.getValue()) {
                            boolean isShared = sharedComponentSignatures.contains(calledMethod);
                            boolean exclude = isShared && (apiConfig.sharedComponentMode() == ApiAnalysisConfig.SharedComponentMode.SEPARATE);

                            if (!exclude) {
                                apiRevs += methodRevisions.getOrDefault(calledMethod, 0);
                                apiLoc += locMap.getOrDefault(calledMethod, 0);
                                apiDecayedRevs += methodDecayedRevisions.getOrDefault(calledMethod, 0.0);
                                apiComplexity += methodComplexities.getOrDefault(calledMethod, 0.0);
                                sumCoverage += methodCoverages.getOrDefault(calledMethod, 0.0);
                                count++;
                            }
                            filteredCallGraph.add(calledMethod);
                        }

                        double apiScore = scoreCalculator.calculateComposite(apiDecayedRevs, apiComplexity, sumCoverage / count);
                        apiHotspots.add(new ApiHotspot(
                                mapping.httpMethod(),
                                mapping.route(),
                                controllerMethod,
                                apiRevs,
                                apiLoc,
                                apiScore,
                                filteredCallGraph
                        ));
                    }
                }
            } else {
                for (Map.Entry<MethodSignature, List<MethodSignature>> entry : callGraphs.entrySet()) {
                    MethodSignature controllerMethod = entry.getKey();
                    List<ApiMappingInfo> mappings = apiMappingsMap.get(controllerMethod);
                    if (mappings == null) continue;

                    for (ApiMappingInfo mapping : mappings) {
                        int apiRevs = methodRevisions.getOrDefault(controllerMethod, 0);
                        int apiLoc = locMap.getOrDefault(controllerMethod, 0);

                        List<MethodSignature> filteredCallGraph = new ArrayList<>();

                        for (MethodSignature calledMethod : entry.getValue()) {
                            boolean isShared = sharedComponentSignatures.contains(calledMethod);
                            boolean exclude = isShared && (apiConfig.sharedComponentMode() == ApiAnalysisConfig.SharedComponentMode.SEPARATE);

                            if (!exclude) {
                                apiRevs += methodRevisions.getOrDefault(calledMethod, 0);
                                apiLoc += locMap.getOrDefault(calledMethod, 0);
                            }
                            filteredCallGraph.add(calledMethod);
                        }

                        double apiScore = scoreCalculator.calculate(apiRevs, apiLoc, formula);
                        apiHotspots.add(new ApiHotspot(
                                mapping.httpMethod(),
                                mapping.route(),
                                controllerMethod,
                                apiRevs,
                                apiLoc,
                                apiScore,
                                filteredCallGraph
                        ));
                    }
                }
            }
            apiHotspots.sort(Comparator.comparingDouble(ApiHotspot::score).reversed()
                    .thenComparing(ApiHotspot::route)
                    .thenComparing(ApiHotspot::httpMethod));
        }

        int topN = config.output().topN();
        if (topN > 0) {
            files = takeTop(files, topN);
            methods = takeTop(methods, topN);
            if (!apiHotspots.isEmpty()) {
                apiHotspots = takeTop(apiHotspots, topN);
            }
            if (!sharedComponents.isEmpty()) {
                sharedComponents = takeTop(sharedComponents, topN);
            }
        }

        AnalysisMeta meta = new AnalysisMeta(
                Instant.now(),
                "LOCAL_GIT:" + repoRoot,
                commits.size(),
                methodsByFile.size(),
                countMethods(methodsByFile),
                formula);

        return new AnalysisResult(files, methods, apiHotspots, sharedComponents, meta);
    }

    private Map<String, List<MethodInfo>> parseAll(Path repoRoot, List<Path> javaFiles) {
        Map<String, List<MethodInfo>> result = new HashMap<>();
        for (Path file : javaFiles) {
            String relative = repoRoot.relativize(file).toString().replace('\\', '/');
            result.put(relative, sourceParser.parse(file));
        }
        return result;
    }

    private List<FileHotspot> buildFileHotspots(java.util.Set<String> paths,
                                                Map<String, Integer> fileRevisions,
                                                Map<String, Integer> fileLoc,
                                                ScoringConfig.Formula formula) {
        List<FileHotspot> hotspots = new ArrayList<>();
        for (String path : paths) {
            int revisions = fileRevisions.getOrDefault(path, 0);
            int loc = fileLoc.getOrDefault(path, 0);
            double score = scoreCalculator.calculate(revisions, loc, formula);
            hotspots.add(new FileHotspot(path, revisions, loc, score));
        }
        hotspots.sort(Comparator
                .comparingDouble(FileHotspot::score).reversed()
                .thenComparing(FileHotspot::path));
        return hotspots;
    }

    private List<MethodHotspot> buildMethodHotspots(Map<String, List<MethodInfo>> methodsByFile,
                                                    Map<MethodSignature, Integer> methodRevisions,
                                                    ScoringConfig.Formula formula) {
        List<MethodHotspot> hotspots = new ArrayList<>();
        for (Map.Entry<String, List<MethodInfo>> entry : methodsByFile.entrySet()) {
            for (MethodInfo method : entry.getValue()) {
                int revisions = methodRevisions.getOrDefault(method.signature(), 0);
                int loc = method.lineCount();
                double score = scoreCalculator.calculate(revisions, loc, formula);
                hotspots.add(new MethodHotspot(
                        method.signature(), entry.getKey(),
                        method.startLine(), method.endLine(),
                        revisions, loc, score));
            }
        }
        hotspots.sort(Comparator
                .comparingDouble(MethodHotspot::score).reversed()
                .thenComparing(h -> h.signature().toCanonicalString()));
        return hotspots;
    }

    private List<FileHotspot> buildCompositeFileHotspots(Map<String, List<MethodInfo>> methodsByFile,
                                                         Map<String, Integer> fileRevisions,
                                                         Map<String, Integer> fileLoc,
                                                         Map<String, Double> fileDecayedRevisions,
                                                         io.github.baekchangjoon.hotspotanalysis.coverage.JacocoReportParser jacocoParser) {
        List<FileHotspot> hotspots = new ArrayList<>();
        for (String path : methodsByFile.keySet()) {
            int revisions = fileRevisions.getOrDefault(path, 0);
            int loc = fileLoc.getOrDefault(path, 0);

            double decayedRevs = fileDecayedRevisions.getOrDefault(path, 0.0);
            double cognitiveComplexity = 0.0;
            List<MethodInfo> mList = methodsByFile.get(path);
            if (mList != null) {
                for (MethodInfo m : mList) {
                    cognitiveComplexity += m.cognitiveComplexity();
                }
            }
            double coverage = jacocoParser.getFileCoverage(path);
            double score = scoreCalculator.calculateComposite(decayedRevs, cognitiveComplexity, coverage);

            hotspots.add(new FileHotspot(path, revisions, loc, score, decayedRevs, cognitiveComplexity, coverage));
        }
        hotspots.sort(Comparator
                .comparingDouble(FileHotspot::score).reversed()
                .thenComparing(FileHotspot::path));
        return hotspots;
    }

    private List<MethodHotspot> buildCompositeMethodHotspots(Map<String, List<MethodInfo>> methodsByFile,
                                                             Map<MethodSignature, Integer> methodRevisions,
                                                             Map<MethodSignature, Double> methodDecayedRevisions,
                                                             io.github.baekchangjoon.hotspotanalysis.coverage.JacocoReportParser jacocoParser) {
        List<MethodHotspot> hotspots = new ArrayList<>();
        for (Map.Entry<String, List<MethodInfo>> entry : methodsByFile.entrySet()) {
            String path = entry.getKey();
            for (MethodInfo method : entry.getValue()) {
                int revisions = methodRevisions.getOrDefault(method.signature(), 0);
                int loc = method.lineCount();

                double decayedRevs = methodDecayedRevisions.getOrDefault(method.signature(), 0.0);
                double cognitiveComplexity = (double) method.cognitiveComplexity();
                double coverage = jacocoParser.getMethodCoverage(path, method.startLine(), method.endLine());
                double score = scoreCalculator.calculateComposite(decayedRevs, cognitiveComplexity, coverage);

                hotspots.add(new MethodHotspot(
                        method.signature(), path,
                        method.startLine(), method.endLine(),
                        revisions, loc, score,
                        decayedRevs, cognitiveComplexity, coverage));
            }
        }
        hotspots.sort(Comparator
                .comparingDouble(MethodHotspot::score).reversed()
                .thenComparing(h -> h.signature().toCanonicalString()));
        return hotspots;
    }

    private static <T> List<T> takeTop(List<T> source, int n) {
        return source.size() <= n ? source : List.copyOf(source.subList(0, n));
    }

    private static int countMethods(Map<String, List<MethodInfo>> methodsByFile) {
        return methodsByFile.values().stream().mapToInt(List::size).sum();
    }
}
