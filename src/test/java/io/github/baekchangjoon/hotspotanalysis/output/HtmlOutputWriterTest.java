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
    @DisplayName("renders one row per file hotspot with sort-friendly data attributes")
    void shouldRenderFileHotspotRows(@TempDir Path tempDir) throws Exception {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);
        String html = Files.readString(tempDir.resolve("hotspots.html"));

        assertThat(html).contains("src/main/java/com/example/Hot.java");
        assertThat(html).contains("src/main/java/com/example/Cold.java");
        // numeric columns use data-sort-value for client-side ordering
        assertThat(html).contains("data-sort-value=\"600\"");
        assertThat(html).contains("data-sort-value=\"30\"");
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
        assertThat(html).contains("com.example.MyController#apiA()");
        assertThat(html).contains("com.example.MyService#commonMethod()");
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
