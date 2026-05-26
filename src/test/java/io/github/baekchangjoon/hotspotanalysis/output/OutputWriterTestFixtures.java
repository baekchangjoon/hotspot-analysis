package io.github.baekchangjoon.hotspotanalysis.output;

import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisMeta;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.FileHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.MethodHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.ApiHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.SharedComponentHotspot;
import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;

import java.time.Instant;
import java.util.List;

/**
 * Shared, deterministic fixtures for output-writer tests.
 *
 * <p>The fixed {@code Instant} keeps snapshot-style assertions stable across
 * CI runs and developer machines.</p>
 *
 * <p>Every fixture carries the unified seven metric fields in canonical
 * order: {@code loc, revisions, simpleScore, recencyDecay,
 * cognitiveComplexity, coverageMultiplier, compositeScore}.</p>
 */
final class OutputWriterTestFixtures {

    static final Instant FIXED_INSTANT = Instant.parse("2026-05-21T09:00:00Z");

    private OutputWriterTestFixtures() {
    }

    static FileHotspot fileSample() {
        return new FileHotspot(
                "src/main/java/com/example/Foo.java",
                /* loc */ 120, /* revisions */ 3,
                /* simpleScore */ 360.0, /* recencyDecay */ 0.85,
                /* cognitiveComplexity */ 7.0, /* coverageMultiplier */ 1.0,
                /* compositeScore */ 5.95);
    }

    static MethodHotspot methodSample() {
        return new MethodHotspot(
                new MethodSignature("com.example.Foo", "bar", List.of("int", "String")),
                "src/main/java/com/example/Foo.java",
                /* startLine */ 22, /* endLine */ 45,
                /* loc */ 24, /* revisions */ 2,
                /* simpleScore */ 48.0, /* recencyDecay */ 0.50,
                /* cognitiveComplexity */ 5.0, /* coverageMultiplier */ 2.0,
                /* compositeScore */ 5.0);
    }

    static ApiHotspot apiSample() {
        return new ApiHotspot(
                "GET", "/api/foo/{id}",
                new MethodSignature("com.example.FooController", "getFoo", List.of("Long")),
                /* loc */ 30, /* revisions */ 3,
                /* simpleScore */ 90.0, /* recencyDecay */ 0.60,
                /* cognitiveComplexity */ 4.0, /* coverageMultiplier */ 1.25,
                /* compositeScore */ 3.0,
                /* callGraph */ List.of());
    }

    static SharedComponentHotspot sharedSample() {
        return new SharedComponentHotspot(
                new MethodSignature("com.example.SharedSvc", "save", List.of("Entity")),
                /* loc */ 18, /* revisions */ 4,
                /* simpleScore */ 72.0, /* recencyDecay */ 0.40,
                /* cognitiveComplexity */ 3.0, /* coverageMultiplier */ 1.0,
                /* compositeScore */ 1.2,
                /* callingApis */ List.of("GET /api/a", "POST /api/b"));
    }

    static AnalysisResult sampleResult() {
        List<FileHotspot> files = List.of(
                new FileHotspot(
                        "src/main/java/com/example/Hot.java",
                        /* loc */ 120, /* revisions */ 5,
                        /* simpleScore */ 600.0, /* recencyDecay */ 4.25,
                        /* cognitiveComplexity */ 8.0, /* coverageMultiplier */ 1.0,
                        /* compositeScore */ 34.0),
                new FileHotspot(
                        "src/main/java/com/example/Cold.java",
                        /* loc */ 30, /* revisions */ 1,
                        /* simpleScore */ 30.0, /* recencyDecay */ 0.75,
                        /* cognitiveComplexity */ 2.0, /* coverageMultiplier */ 1.0,
                        /* compositeScore */ 1.5));
        List<MethodHotspot> methods = List.of(
                new MethodHotspot(
                        new MethodSignature("com.example.Hot", "doWork", List.of("int", "String")),
                        "src/main/java/com/example/Hot.java",
                        /* startLine */ 12, /* endLine */ 28,
                        /* loc */ 17, /* revisions */ 4,
                        /* simpleScore */ 68.0, /* recencyDecay */ 3.40,
                        /* cognitiveComplexity */ 6.0, /* coverageMultiplier */ 1.0,
                        /* compositeScore */ 20.4),
                new MethodHotspot(
                        new MethodSignature("com.example.Hot", "doWork", List.of()),
                        "src/main/java/com/example/Hot.java",
                        /* startLine */ 30, /* endLine */ 32,
                        /* loc */ 3, /* revisions */ 1,
                        /* simpleScore */ 3.0, /* recencyDecay */ 0.85,
                        /* cognitiveComplexity */ 2.0, /* coverageMultiplier */ 1.0,
                        /* compositeScore */ 1.7));
        AnalysisMeta meta = new AnalysisMeta(
                FIXED_INSTANT,
                "LOCAL_GIT:/tmp/example",
                42, 2, 2);
        return new AnalysisResult(files, methods, meta);
    }

    static AnalysisResult sampleApiResult() {
        List<FileHotspot> files = List.of(
                new FileHotspot(
                        "src/main/java/com/example/MyController.java",
                        /* loc */ 120, /* revisions */ 5,
                        /* simpleScore */ 600.0, /* recencyDecay */ 4.25,
                        /* cognitiveComplexity */ 8.0, /* coverageMultiplier */ 1.0,
                        /* compositeScore */ 34.0),
                new FileHotspot(
                        "src/main/java/com/example/MyService.java",
                        /* loc */ 30, /* revisions */ 1,
                        /* simpleScore */ 30.0, /* recencyDecay */ 0.75,
                        /* cognitiveComplexity */ 2.0, /* coverageMultiplier */ 1.0,
                        /* compositeScore */ 1.5));
        List<MethodHotspot> methods = List.of(
                new MethodHotspot(
                        new MethodSignature("com.example.MyController", "apiA", List.of()),
                        "src/main/java/com/example/MyController.java",
                        /* startLine */ 12, /* endLine */ 28,
                        /* loc */ 17, /* revisions */ 4,
                        /* simpleScore */ 68.0, /* recencyDecay */ 3.40,
                        /* cognitiveComplexity */ 6.0, /* coverageMultiplier */ 1.0,
                        /* compositeScore */ 20.4),
                new MethodHotspot(
                        new MethodSignature("com.example.MyService", "commonMethod", List.of()),
                        "src/main/java/com/example/MyService.java",
                        /* startLine */ 30, /* endLine */ 32,
                        /* loc */ 3, /* revisions */ 1,
                        /* simpleScore */ 3.0, /* recencyDecay */ 0.85,
                        /* cognitiveComplexity */ 2.0, /* coverageMultiplier */ 1.0,
                        /* compositeScore */ 1.7));

        MethodSignature ctrlMethod = new MethodSignature("com.example.MyController", "apiA", List.of());
        MethodSignature svcMethod = new MethodSignature("com.example.MyService", "commonMethod", List.of());

        List<ApiHotspot> apiHotspots = List.of(
                new ApiHotspot(
                        "GET", "/api/a", ctrlMethod,
                        /* loc */ 120, /* revisions */ 5,
                        /* simpleScore */ 600.0, /* recencyDecay */ 4.25,
                        /* cognitiveComplexity */ 8.0, /* coverageMultiplier */ 1.0,
                        /* compositeScore */ 34.0,
                        List.of(svcMethod))
        );

        List<SharedComponentHotspot> sharedComponents = List.of(
                new SharedComponentHotspot(
                        svcMethod,
                        /* loc */ 30, /* revisions */ 1,
                        /* simpleScore */ 30.0, /* recencyDecay */ 0.85,
                        /* cognitiveComplexity */ 2.0, /* coverageMultiplier */ 1.0,
                        /* compositeScore */ 1.7,
                        List.of("GET /api/a"))
        );

        AnalysisMeta meta = new AnalysisMeta(
                FIXED_INSTANT,
                "LOCAL_GIT:/tmp/example",
                42, 2, 2);
        return new AnalysisResult(files, methods, apiHotspots, sharedComponents, meta);
    }
}
