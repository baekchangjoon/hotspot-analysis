package io.github.baekchangjoon.hotspotanalysis.output;

import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisMeta;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.FileHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.MethodHotspot;
import io.github.baekchangjoon.hotspotanalysis.config.OutputConfig;
import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlOutputWriterTest {

    private final HtmlOutputWriter writer = new HtmlOutputWriter();

    @Test
    @DisplayName("declares HTML as its format")
    void shouldDeclareHtmlFormat() {
        assertThat(writer.format()).isEqualTo(OutputConfig.OutputFormat.HTML);
    }

    @Test
    @DisplayName("writes a self-contained hotspots.html with DOCTYPE and inline assets")
    void shouldWriteSelfContainedHtml(@TempDir Path tempDir) throws Exception {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);

        Path output = tempDir.resolve("hotspots.html");
        assertThat(output).exists();
        String html = Files.readString(output);

        assertThat(html).startsWith("<!DOCTYPE html>");
        assertThat(html).contains("<title>Hotspot Analysis Report</title>");
        assertThat(html).contains("<style>");
        assertThat(html).contains("<script>");
        // self-contained: no remote stylesheet or script
        assertThat(html).doesNotContainIgnoringCase("https://cdn");
        assertThat(html).doesNotContainIgnoringCase("http://");
        assertThat(html).doesNotContainIgnoringCase("integrity=");
    }

    @Test
    @DisplayName("renders metadata block (target, commits, files, methods); no scoring formula in unified mode")
    void shouldRenderMetadataBlock(@TempDir Path tempDir) throws Exception {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);
        String html = Files.readString(tempDir.resolve("hotspots.html"));

        assertThat(html).contains("LOCAL_GIT:/tmp/example");
        assertThat(html).contains(">42<");          // totalCommits
        assertThat(html).contains("2026-05-21");    // generation timestamp
        assertThat(html).doesNotContain("Scoring formula");
    }

    @Test
    @DisplayName("file table has Simple Rank + Composite Rank + 7-metric headers in spec order")
    void shouldRenderFileTableHeaders(@TempDir Path tempDir) throws Exception {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);
        String html = Files.readString(tempDir.resolve("hotspots.html"));

        assertThat(html).contains(">Simple Rank<");
        assertThat(html).contains(">Composite Rank<");
        assertThat(html).contains(">Path<");
        assertThat(html).contains(">LOC<");
        assertThat(html).contains(">Revisions<");
        assertThat(html).contains(">Simple Score<");
        assertThat(html).contains(">Recency Decay<");
        assertThat(html).contains(">Cognitive Complexity<");
        assertThat(html).contains(">Coverage Multiplier<");
        assertThat(html).contains(">Composite Score<");
        // Composite Rank carries the initial-sort marker.
        assertThat(html).contains("aria-sort=\"ascending\">Composite Rank<");

        int sRankIdx   = html.indexOf(">Simple Rank<");
        int cRankIdx   = html.indexOf(">Composite Rank<");
        int pathIdx    = html.indexOf(">Path<");
        int locIdx     = html.indexOf(">LOC<");
        int revIdx     = html.indexOf(">Revisions<");
        int simpleIdx  = html.indexOf(">Simple Score<");
        int recencyIdx = html.indexOf(">Recency Decay<");
        int cogIdx     = html.indexOf(">Cognitive Complexity<");
        int covIdx     = html.indexOf(">Coverage Multiplier<");
        int compIdx    = html.indexOf(">Composite Score<");
        assertThat(sRankIdx).isLessThan(cRankIdx);
        assertThat(cRankIdx).isLessThan(pathIdx);
        assertThat(pathIdx).isLessThan(locIdx);
        assertThat(locIdx).isLessThan(revIdx);
        assertThat(revIdx).isLessThan(simpleIdx);
        assertThat(simpleIdx).isLessThan(recencyIdx);
        assertThat(recencyIdx).isLessThan(cogIdx);
        assertThat(cogIdx).isLessThan(covIdx);
        assertThat(covIdx).isLessThan(compIdx);
    }

    @Test
    @DisplayName("method table has canonical identifier + 7-metric column headers in spec order")
    void shouldRenderMethodTableHeaders(@TempDir Path tempDir) throws Exception {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);
        String html = Files.readString(tempDir.resolve("hotspots.html"));

        // All method-table headers must appear: Rank, FQCN, Method, Parameters, File, Lines, LOC, Revisions, Simple Score, Recency Decay, Cognitive Complexity, Coverage Multiplier, Composite Score
        assertThat(html).contains(">FQCN<");
        assertThat(html).contains(">Method<");
        assertThat(html).contains(">Parameters<");
        assertThat(html).contains(">File<");
        assertThat(html).contains(">Lines<");

        // Order check within the method section (find the method-hotspots table and verify order there)
        int methodTableStart = html.indexOf("id=\"method-hotspots\"");
        assertThat(methodTableStart).isGreaterThan(0);
        String methodSection = html.substring(methodTableStart);

        int rankIdx    = methodSection.indexOf(">Simple Rank<");
        int fqcnIdx    = methodSection.indexOf(">FQCN<");
        int methodIdx  = methodSection.indexOf(">Method<");
        int paramsIdx  = methodSection.indexOf(">Parameters<");
        int fileIdx    = methodSection.indexOf(">File<");
        int linesIdx   = methodSection.indexOf(">Lines<");
        int locIdx     = methodSection.indexOf(">LOC<");
        int revIdx     = methodSection.indexOf(">Revisions<");
        int simpleIdx  = methodSection.indexOf(">Simple Score<");
        int recencyIdx = methodSection.indexOf(">Recency Decay<");
        int cogIdx     = methodSection.indexOf(">Cognitive Complexity<");
        int covIdx     = methodSection.indexOf(">Coverage Multiplier<");
        int compIdx    = methodSection.indexOf(">Composite Score<");

        assertThat(rankIdx).isLessThan(fqcnIdx);
        assertThat(fqcnIdx).isLessThan(methodIdx);
        assertThat(methodIdx).isLessThan(paramsIdx);
        assertThat(paramsIdx).isLessThan(fileIdx);
        assertThat(fileIdx).isLessThan(linesIdx);
        assertThat(linesIdx).isLessThan(locIdx);
        assertThat(locIdx).isLessThan(revIdx);
        assertThat(revIdx).isLessThan(simpleIdx);
        assertThat(simpleIdx).isLessThan(recencyIdx);
        assertThat(recencyIdx).isLessThan(cogIdx);
        assertThat(cogIdx).isLessThan(covIdx);
        assertThat(covIdx).isLessThan(compIdx);
    }

    @Test
    @DisplayName("X-Ray drilldown has canonical 7-metric column headers including Share")
    void shouldRenderXRayDrilldownHeaders(@TempDir Path tempDir) throws Exception {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);
        String html = Files.readString(tempDir.resolve("hotspots.html"));

        // X-Ray table headers: Method Signature, LOC, Revisions, Simple Score, Recency Decay, Cognitive Complexity, Coverage Multiplier, Composite Score, Share
        assertThat(html).contains(">Method Signature<");
        assertThat(html).contains(">Share<");

        // Verify order within the xray-table
        int xrayStart = html.indexOf("class=\"xray-table\"");
        assertThat(xrayStart).isGreaterThan(0);
        String xraySection = html.substring(xrayStart);

        int sigIdx     = xraySection.indexOf(">Method Signature<");
        int locIdx     = xraySection.indexOf(">LOC<");
        int revIdx     = xraySection.indexOf(">Revisions<");
        int simpleIdx  = xraySection.indexOf(">Simple Score<");
        int recencyIdx = xraySection.indexOf(">Recency Decay<");
        int cogIdx     = xraySection.indexOf(">Cognitive Complexity<");
        int covIdx     = xraySection.indexOf(">Coverage Multiplier<");
        int compIdx    = xraySection.indexOf(">Composite Score<");
        int shareIdx   = xraySection.indexOf(">Share<");

        assertThat(sigIdx).isLessThan(locIdx);
        assertThat(locIdx).isLessThan(revIdx);
        assertThat(revIdx).isLessThan(simpleIdx);
        assertThat(simpleIdx).isLessThan(recencyIdx);
        assertThat(recencyIdx).isLessThan(cogIdx);
        assertThat(cogIdx).isLessThan(covIdx);
        assertThat(covIdx).isLessThan(compIdx);
        assertThat(compIdx).isLessThan(shareIdx);
    }

    @Test
    @DisplayName("API table has canonical column headers in spec order")
    void shouldRenderApiTableHeaders(@TempDir Path tempDir) throws Exception {
        OutputConfig config = new OutputConfig(
                List.of(OutputConfig.OutputFormat.HTML),
                tempDir.toString(),
                10,
                OutputConfig.ApiLayout.COMBINED
        );
        writer.write(OutputWriterTestFixtures.sampleApiResult(), tempDir, config, true);
        String html = Files.readString(tempDir.resolve("hotspots.html"));

        int apiTableStart = html.indexOf("id=\"api-hotspots\"");
        assertThat(apiTableStart).isGreaterThan(0);
        String apiSection = html.substring(apiTableStart);

        // Rank, HTTP Method, Route, FQCN, Method, Parameters, LOC, Revisions, Simple Score, Recency Decay, Cognitive Complexity, Coverage Multiplier, Composite Score, Call Graph
        int rankIdx     = apiSection.indexOf(">Simple Rank<");
        int httpIdx     = apiSection.indexOf(">HTTP Method<");
        int routeIdx    = apiSection.indexOf(">Route<");
        int fqcnIdx     = apiSection.indexOf(">FQCN<");
        int methodIdx   = apiSection.indexOf(">Method<");
        int paramsIdx   = apiSection.indexOf(">Parameters<");
        int locIdx      = apiSection.indexOf(">LOC<");
        int revIdx      = apiSection.indexOf(">Revisions<");
        int simpleIdx   = apiSection.indexOf(">Simple Score<");
        int recencyIdx  = apiSection.indexOf(">Recency Decay<");
        int cogIdx      = apiSection.indexOf(">Cognitive Complexity<");
        int covIdx      = apiSection.indexOf(">Coverage Multiplier<");
        int compIdx     = apiSection.indexOf(">Composite Score<");
        int callIdx     = apiSection.indexOf(">Call Graph<");

        assertThat(rankIdx).isLessThan(httpIdx);
        assertThat(httpIdx).isLessThan(routeIdx);
        assertThat(routeIdx).isLessThan(fqcnIdx);
        assertThat(fqcnIdx).isLessThan(methodIdx);
        assertThat(methodIdx).isLessThan(paramsIdx);
        assertThat(paramsIdx).isLessThan(locIdx);
        assertThat(locIdx).isLessThan(revIdx);
        assertThat(revIdx).isLessThan(simpleIdx);
        assertThat(simpleIdx).isLessThan(recencyIdx);
        assertThat(recencyIdx).isLessThan(cogIdx);
        assertThat(cogIdx).isLessThan(covIdx);
        assertThat(covIdx).isLessThan(compIdx);
        assertThat(compIdx).isLessThan(callIdx);
    }

    @Test
    @DisplayName("Shared Components table has canonical column headers in spec order")
    void shouldRenderSharedTableHeaders(@TempDir Path tempDir) throws Exception {
        OutputConfig config = new OutputConfig(
                List.of(OutputConfig.OutputFormat.HTML),
                tempDir.toString(),
                10,
                OutputConfig.ApiLayout.COMBINED
        );
        writer.write(OutputWriterTestFixtures.sampleApiResult(), tempDir, config, true);
        String html = Files.readString(tempDir.resolve("hotspots.html"));

        int sharedTableStart = html.indexOf("id=\"shared-hotspots\"");
        assertThat(sharedTableStart).isGreaterThan(0);
        String sharedSection = html.substring(sharedTableStart);

        // Rank, FQCN, Method, Parameters, LOC, Revisions, Simple Score, Recency Decay, Cognitive Complexity, Coverage Multiplier, Composite Score, Calling APIs
        int rankIdx     = sharedSection.indexOf(">Simple Rank<");
        int fqcnIdx     = sharedSection.indexOf(">FQCN<");
        int methodIdx   = sharedSection.indexOf(">Method<");
        int paramsIdx   = sharedSection.indexOf(">Parameters<");
        int locIdx      = sharedSection.indexOf(">LOC<");
        int revIdx      = sharedSection.indexOf(">Revisions<");
        int simpleIdx   = sharedSection.indexOf(">Simple Score<");
        int recencyIdx  = sharedSection.indexOf(">Recency Decay<");
        int cogIdx      = sharedSection.indexOf(">Cognitive Complexity<");
        int covIdx      = sharedSection.indexOf(">Coverage Multiplier<");
        int compIdx     = sharedSection.indexOf(">Composite Score<");
        int callingIdx  = sharedSection.indexOf(">Calling APIs<");

        assertThat(rankIdx).isLessThan(fqcnIdx);
        assertThat(fqcnIdx).isLessThan(methodIdx);
        assertThat(methodIdx).isLessThan(paramsIdx);
        assertThat(paramsIdx).isLessThan(locIdx);
        assertThat(locIdx).isLessThan(revIdx);
        assertThat(revIdx).isLessThan(simpleIdx);
        assertThat(simpleIdx).isLessThan(recencyIdx);
        assertThat(recencyIdx).isLessThan(cogIdx);
        assertThat(cogIdx).isLessThan(covIdx);
        assertThat(covIdx).isLessThan(compIdx);
        assertThat(compIdx).isLessThan(callingIdx);
    }

    @Test
    @DisplayName("X-Ray Share column uses compositeScore (not simpleScore) for the calculation")
    void shouldUseCompositeScoreForXRayShare(@TempDir Path tempDir) throws Exception {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);
        String html = Files.readString(tempDir.resolve("hotspots.html"));

        // sampleResult has two methods for Hot.java with compositeScores 20.4 and 1.7
        // totalCompositeScore = 22.1; composite-based share of first = 20.4/22.1*100 ≈ 92.3%
        // (Simple-score based share would be 68/(68+3)*100 ≈ 95.8%, which we do NOT expect.)
        assertThat(html).contains("92.3%");
        assertThat(html).doesNotContain("95.8%");
    }

    @Test
    @DisplayName("renders one row per file hotspot with sort-friendly data attributes")
    void shouldRenderFileHotspotRows(@TempDir Path tempDir) throws Exception {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);
        String html = Files.readString(tempDir.resolve("hotspots.html"));

        assertThat(html).contains("src/main/java/com/example/Hot.java");
        assertThat(html).contains("src/main/java/com/example/Cold.java");
        // numeric columns use data-sort-value for client-side ordering
        // Hot.java: loc=120 (int), revisions=5 (int), simpleScore=600.0 (double)
        assertThat(html).contains("data-sort-value=\"120\"");  // loc of Hot.java
        assertThat(html).contains("data-sort-value=\"5\"");   // revisions of Hot.java
        // Cold.java: loc=30 (int), simpleScore=30.0 (double)
        assertThat(html).contains("data-sort-value=\"30\"");  // loc of Cold.java
    }

    @Test
    @DisplayName("renders method hotspot rows with line ranges and parameters")
    void shouldRenderMethodHotspotRows(@TempDir Path tempDir) throws Exception {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);
        String html = Files.readString(tempDir.resolve("hotspots.html"));

        assertThat(html).contains("com.example.Hot");
        assertThat(html).contains("doWork");
        assertThat(html).contains("12&ndash;28");      // line range rendered as en-dash
        assertThat(html).contains("int, String");      // parameter types
    }

    @Test
    @DisplayName("escapes HTML-active characters in user-controlled values (XSS guard)")
    void shouldEscapeUserControlledValues(@TempDir Path tempDir) throws Exception {
        FileHotspot hostile = new FileHotspot(
                "src/<script>alert(1)</script>/Evil.java",
                /* loc */ 99, /* revisions */ 9,
                /* simpleScore */ 891.0, /* recencyDecay */ 1.0,
                /* cognitiveComplexity */ 1.0, /* coverageMultiplier */ 1.0,
                /* compositeScore */ 1.0);
        MethodHotspot hostileMethod = new MethodHotspot(
                new MethodSignature(
                        "com.example.<img src=x onerror=alert(2)>",
                        "boom\"&<>",
                        List.of("List<String&Co>")),
                "src/<script>alert(1)</script>/Evil.java",
                1, 2,
                /* loc */ 4, /* revisions */ 3,
                /* simpleScore */ 12.0, /* recencyDecay */ 1.0,
                /* cognitiveComplexity */ 1.0, /* coverageMultiplier */ 1.0,
                /* compositeScore */ 1.0);
        AnalysisResult result = new AnalysisResult(
                List.of(hostile),
                List.of(hostileMethod),
                new AnalysisMeta(
                        Instant.parse("2026-05-21T09:00:00Z"),
                        "LOCAL_GIT:/tmp/<script>",
                        1, 1, 1));

        writer.write(result, tempDir);
        String html = Files.readString(tempDir.resolve("hotspots.html"));

        // raw script tags from input must never appear unescaped in output
        assertThat(html).doesNotContain("<script>alert(1)");
        assertThat(html).doesNotContain("<img src=x onerror=");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html).contains("&lt;img src=x onerror=alert(2)&gt;");
        // injected ampersand and quote must be escaped
        assertThat(html).contains("boom&quot;&amp;&lt;&gt;");
    }

    @Test
    @DisplayName("emits a search input bound to the hotspot tables")
    void shouldEmitSearchAndSortableMarkers(@TempDir Path tempDir) throws Exception {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);
        String html = Files.readString(tempDir.resolve("hotspots.html"));

        assertThat(html).contains("data-filter-target");
        assertThat(html).contains("class=\"sortable\"");
        assertThat(html).contains("data-sort-type");
    }

    @Test
    @DisplayName("writes combined HTML with API hotspots when apiEnabled is true and layout is COMBINED")
    void shouldWriteCombinedHtmlWithApiInfo(@TempDir Path tempDir) throws Exception {
        OutputConfig config = new OutputConfig(
                List.of(OutputConfig.OutputFormat.HTML),
                tempDir.toString(),
                10,
                OutputConfig.ApiLayout.COMBINED
        );

        writer.write(OutputWriterTestFixtures.sampleApiResult(), tempDir, config, true);

        Path combinedPath = tempDir.resolve("hotspots.html");
        Path standalonePath = tempDir.resolve("api_report.html");

        assertThat(combinedPath).exists();
        assertThat(standalonePath).doesNotExist();

        String html = Files.readString(combinedPath);
        assertThat(html).contains("REST API Hotspots");
        assertThat(html).contains("Shared Components");
        assertThat(html).contains("GET");
        assertThat(html).contains("/api/a");
        // API and Shared sections now emit FQCN, Method, Parameters in separate cells
        assertThat(html).contains("com.example.MyController");
        assertThat(html).contains("apiA");
        assertThat(html).contains("com.example.MyService");
        assertThat(html).contains("commonMethod");
    }

    @Test
    @DisplayName("writes standalone HTML with API hotspots when apiEnabled is true and layout is STANDALONE")
    void shouldWriteStandaloneHtmlWithOnlyApiInfo(@TempDir Path tempDir) throws Exception {
        OutputConfig config = new OutputConfig(
                List.of(OutputConfig.OutputFormat.HTML),
                tempDir.toString(),
                10,
                OutputConfig.ApiLayout.STANDALONE
        );

        writer.write(OutputWriterTestFixtures.sampleApiResult(), tempDir, config, true);

        Path combinedPath = tempDir.resolve("hotspots.html");
        Path standalonePath = tempDir.resolve("api_report.html");

        assertThat(combinedPath).doesNotExist();
        assertThat(standalonePath).exists();

        String html = Files.readString(standalonePath);
        assertThat(html).contains("REST API Hotspots");
        assertThat(html).contains("Shared Components");
        assertThat(html).doesNotContain("File Hotspots");
        assertThat(html).doesNotContain("Method Hotspots");
    }

    @Test
    @DisplayName("excludeCoverage=true replaces Coverage Multiplier header with Line Coverage at rightmost position")
    void shouldRenderLineCoverageHeaderAtRightmostWhenExcludeCoverage(@TempDir Path tempDir) throws Exception {
        AnalysisResult result = new AnalysisResult(
                List.of(new FileHotspot(
                        "X.java",
                        /* loc */ 20, /* revisions */ 1,
                        /* simpleScore */ 20.0, /* recencyDecay */ 1.0,
                        /* cognitiveComplexity */ 2.0, /* coverageMultiplier */ 1.0,
                        /* compositeScore */ 2.0,
                        /* lineCoverage */ 0.4)),
                List.of(),
                new AnalysisMeta(Instant.parse("2026-05-27T10:00:00Z"),
                        "LOCAL_GIT:/tmp", 1, 1, 0));

        OutputConfig config = new OutputConfig(
                List.of(OutputConfig.OutputFormat.HTML), tempDir.toString(), 0);
        writer.write(result, tempDir, config, false, true);

        String html = Files.readString(tempDir.resolve("hotspots.html"));

        // Header: Line Coverage replaces Coverage Multiplier and sits to the right of Composite Score.
        assertThat(html).contains(">Line Coverage<");
        assertThat(html).doesNotContain(">Coverage Multiplier<");
        int compositeIdx = html.indexOf(">Composite Score<");
        int coverageIdx = html.indexOf(">Line Coverage<");
        assertThat(compositeIdx).isPositive();
        assertThat(coverageIdx).isPositive();
        assertThat(coverageIdx).isGreaterThan(compositeIdx);

        // Cell value: rendered as a percentage.
        assertThat(html).contains("40.0%");
    }

    @Test
    @DisplayName("excludeCoverage=true emits N/A in the Line Coverage cell when JaCoCo data missing")
    void shouldRenderNAForMissingLineCoverage(@TempDir Path tempDir) throws Exception {
        AnalysisResult result = new AnalysisResult(
                List.of(new FileHotspot(
                        "X.java",
                        /* loc */ 20, /* revisions */ 1,
                        /* simpleScore */ 20.0, /* recencyDecay */ 1.0,
                        /* cognitiveComplexity */ 2.0, /* coverageMultiplier */ 1.0,
                        /* compositeScore */ 2.0,
                        /* lineCoverage */ null)),
                List.of(),
                new AnalysisMeta(Instant.parse("2026-05-27T10:00:00Z"),
                        "LOCAL_GIT:/tmp", 1, 1, 0));

        OutputConfig config = new OutputConfig(
                List.of(OutputConfig.OutputFormat.HTML), tempDir.toString(), 0);
        writer.write(result, tempDir, config, false, true);

        String html = Files.readString(tempDir.resolve("hotspots.html"));
        assertThat(html).contains(">N/A<");
    }

    @Test
    @DisplayName("excludeCoverage=true emits Line Coverage column on REST API and Shared Components tables")
    void shouldEmitLineCoverageOnApiAndSharedSections(@TempDir Path tempDir) throws Exception {
        OutputConfig config = new OutputConfig(
                List.of(OutputConfig.OutputFormat.HTML),
                tempDir.toString(),
                0,
                OutputConfig.ApiLayout.BOTH);

        writer.write(OutputWriterTestFixtures.sampleApiResultWithCoverage(), tempDir, config, true, true);

        String html = Files.readString(tempDir.resolve("hotspots.html"));

        // Coverage Multiplier header gone, Line Coverage present.
        assertThat(html).doesNotContain(">Coverage Multiplier<");
        assertThat(html).contains(">Line Coverage<");

        // REST API row: 42.0% rendered for lineCoverage=0.42.
        assertThat(html).contains("42.0%");
        // Shared row: lineCoverage=null in fixture → N/A in the rightmost cell.
        assertThat(html).contains(">N/A<");

        // Standalone api_report.html also reflects the swap.
        String standalone = Files.readString(tempDir.resolve("api_report.html"));
        assertThat(standalone).doesNotContain(">Coverage Multiplier<");
        assertThat(standalone).contains(">Line Coverage<");
    }

    @Test
    @DisplayName("writes both combined and standalone HTML files when layout is BOTH")
    void shouldWriteBothHtmlFiles(@TempDir Path tempDir) throws Exception {
        OutputConfig config = new OutputConfig(
                List.of(OutputConfig.OutputFormat.HTML),
                tempDir.toString(),
                10,
                OutputConfig.ApiLayout.BOTH
        );

        writer.write(OutputWriterTestFixtures.sampleApiResult(), tempDir, config, true);

        Path combinedPath = tempDir.resolve("hotspots.html");
        Path standalonePath = tempDir.resolve("api_report.html");

        assertThat(combinedPath).exists();
        assertThat(standalonePath).exists();
    }
}
