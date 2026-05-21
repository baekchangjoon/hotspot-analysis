package io.github.baekchangjoon.hotspotanalysis.analysis;

import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisMeta;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.FileHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.MethodHotspot;
import io.github.baekchangjoon.hotspotanalysis.config.AnalysisConfig;
import io.github.baekchangjoon.hotspotanalysis.config.ScoringConfig;
import io.github.baekchangjoon.hotspotanalysis.config.TargetConfig;
import io.github.baekchangjoon.hotspotanalysis.parser.JavaSourceParser;
import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodInfo;
import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;
import io.github.baekchangjoon.hotspotanalysis.vcs.VcsProvider;
import io.github.baekchangjoon.hotspotanalysis.vcs.VcsProviderFactory;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.CommitRecord;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    public HotspotAnalyzer(VcsProviderFactory providerFactory,
                           JavaSourceCollector sourceCollector,
                           JavaSourceParser sourceParser,
                           RevisionsCalculator revisionsCalculator,
                           LocCalculator locCalculator,
                           HotspotScoreCalculator scoreCalculator) {
        this.providerFactory = Objects.requireNonNull(providerFactory);
        this.sourceCollector = Objects.requireNonNull(sourceCollector);
        this.sourceParser = Objects.requireNonNull(sourceParser);
        this.revisionsCalculator = Objects.requireNonNull(revisionsCalculator);
        this.locCalculator = Objects.requireNonNull(locCalculator);
        this.scoreCalculator = Objects.requireNonNull(scoreCalculator);
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
        List<FileHotspot> files = buildFileHotspots(
                methodsByFile.keySet(), fileRevisions, fileLoc, formula);
        List<MethodHotspot> methods = buildMethodHotspots(
                methodsByFile, methodRevisions, formula);

        int topN = config.output().topN();
        if (topN > 0) {
            files = takeTop(files, topN);
            methods = takeTop(methods, topN);
        }

        AnalysisMeta meta = new AnalysisMeta(
                Instant.now(),
                "LOCAL_GIT:" + repoRoot,
                commits.size(),
                methodsByFile.size(),
                countMethods(methodsByFile),
                formula);

        return new AnalysisResult(files, methods, meta);
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

    private static <T> List<T> takeTop(List<T> source, int n) {
        return source.size() <= n ? source : List.copyOf(source.subList(0, n));
    }

    private static int countMethods(Map<String, List<MethodInfo>> methodsByFile) {
        return methodsByFile.values().stream().mapToInt(List::size).sum();
    }
}
