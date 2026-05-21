package io.github.baekchangjoon.hotspotanalysis.output;

import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisMeta;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.FileHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.MethodHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.ApiHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.SharedComponentHotspot;
import io.github.baekchangjoon.hotspotanalysis.config.ScoringConfig;
import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;

import java.time.Instant;
import java.util.List;

/**
 * Shared, deterministic fixtures for output-writer tests.
 *
 * <p>The fixed {@code Instant} keeps snapshot-style assertions stable across
 * CI runs and developer machines.</p>
 */
final class OutputWriterTestFixtures {

    static final Instant FIXED_INSTANT = Instant.parse("2026-05-21T09:00:00Z");

    private OutputWriterTestFixtures() {
    }

    static AnalysisResult sampleResult() {
        List<FileHotspot> files = List.of(
                new FileHotspot("src/main/java/com/example/Hot.java", 5, 120, 600.0),
                new FileHotspot("src/main/java/com/example/Cold.java", 1, 30, 30.0));
        List<MethodHotspot> methods = List.of(
                new MethodHotspot(
                        new MethodSignature("com.example.Hot", "doWork", List.of("int", "String")),
                        "src/main/java/com/example/Hot.java",
                        12, 28, 4, 17, 68.0),
                new MethodHotspot(
                        new MethodSignature("com.example.Hot", "doWork", List.of()),
                        "src/main/java/com/example/Hot.java",
                        30, 32, 1, 3, 3.0));
        AnalysisMeta meta = new AnalysisMeta(
                FIXED_INSTANT,
                "LOCAL_GIT:/tmp/example",
                42, 2, 2,
                ScoringConfig.Formula.SIMPLE);
        return new AnalysisResult(files, methods, meta);
    }

    static AnalysisResult sampleApiResult() {
        List<FileHotspot> files = List.of(
                new FileHotspot("src/main/java/com/example/MyController.java", 5, 120, 600.0),
                new FileHotspot("src/main/java/com/example/MyService.java", 1, 30, 30.0));
        List<MethodHotspot> methods = List.of(
                new MethodHotspot(
                        new MethodSignature("com.example.MyController", "apiA", List.of()),
                        "src/main/java/com/example/MyController.java",
                        12, 28, 4, 17, 68.0),
                new MethodHotspot(
                        new MethodSignature("com.example.MyService", "commonMethod", List.of()),
                        "src/main/java/com/example/MyService.java",
                        30, 32, 1, 3, 3.0));

        MethodSignature ctrlMethod = new MethodSignature("com.example.MyController", "apiA", List.of());
        MethodSignature svcMethod = new MethodSignature("com.example.MyService", "commonMethod", List.of());

        List<ApiHotspot> apiHotspots = List.of(
                new ApiHotspot("GET", "/api/a", ctrlMethod, 5, 120, 600.0, List.of(svcMethod))
        );

        List<SharedComponentHotspot> sharedComponents = List.of(
                new SharedComponentHotspot(svcMethod, 1, 30, 30.0, List.of("GET /api/a"))
        );

        AnalysisMeta meta = new AnalysisMeta(
                FIXED_INSTANT,
                "LOCAL_GIT:/tmp/example",
                42, 2, 2,
                ScoringConfig.Formula.SIMPLE);
        return new AnalysisResult(files, methods, apiHotspots, sharedComponents, meta);
    }
}
