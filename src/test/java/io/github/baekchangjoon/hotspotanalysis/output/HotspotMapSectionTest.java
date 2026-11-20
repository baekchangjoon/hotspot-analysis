package io.github.baekchangjoon.hotspotanalysis.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisMeta;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.CoverageBreakdown;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.FileHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.MethodHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.ReportContext;
import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HotspotMapSectionTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BLOB = "https://github.com/acme/app/blob/abc1234/";

    @Test
    @DisplayName("the data block carries the schema version, run facts and every factor behind a file score")
    void shouldDescribeFilesWithTheirFactors() throws Exception {
        JsonNode doc = parse(HotspotMapSection.toJson(HotspotMapSection.buildData(sample(), false)));

        assertThat(doc.get("schemaVersion").asInt()).isEqualTo(HotspotMapSection.SCHEMA_VERSION);
        JsonNode meta = doc.get("meta");
        assertThat(meta.get("label").asText()).isEqualTo("app");
        assertThat(meta.get("analyzedAt").asText()).isEqualTo("2026-05-21T09:00:00Z");
        assertThat(meta.get("commits").asInt()).isEqualTo(42);
        assertThat(meta.get("halfLifeDays").asInt()).isEqualTo(30);
        assertThat(meta.get("blobBase").asText()).isEqualTo(BLOB);
        assertThat(meta.get("revision").asText()).isEqualTo("abc1234");
        assertThat(meta.get("excludeCoverage").asBoolean()).isFalse();
        // Totals over the whole repository, not just the two rows shown.
        assertThat(meta.get("totalFiles").asInt()).isEqualTo(9);
        assertThat(meta.get("totalFileScore").asDouble()).isEqualTo(250.0);
        assertThat(meta.get("coveredLines").asInt()).isEqualTo(88);
        assertThat(meta.get("executableLines").asInt()).isEqualTo(120);

        JsonNode hot = doc.get("files").get(0);
        assertThat(hot.get("path").asText()).isEqualTo("src/main/java/com/example/Hot.java");
        assertThat(hot.get("rank").asInt()).isEqualTo(1);
        assertThat(hot.get("cc").isInt()).isTrue();
        assertThat(hot.get("cc").asInt()).isEqualTo(40);
        assertThat(hot.get("decay").asDouble()).isEqualTo(2.5);
        assertThat(hot.get("covMul").asDouble()).isEqualTo(1.25);
        assertThat(hot.get("cov").asDouble()).isEqualTo(0.7);
        assertThat(hot.get("covered").asInt()).isEqualTo(70);
        assertThat(hot.get("exec").asInt()).isEqualTo(100);
        assertThat(hot.get("score").asDouble()).isEqualTo(125.0);
    }

    @Test
    @DisplayName("unknown coverage stays null — it is not the same as 0% or as a 1.0 multiplier")
    void shouldKeepUnknownCoverageDistinctFromZero() throws Exception {
        JsonNode doc = parse(HotspotMapSection.toJson(HotspotMapSection.buildData(sample(), false)));

        JsonNode untested = method(doc, "untested");
        assertThat(untested.get("cov").isNumber()).isTrue();
        assertThat(untested.get("cov").asDouble()).isZero();
        assertThat(untested.get("covMul").asDouble()).isEqualTo(10.0);

        JsonNode declaration = method(doc, "declaration");
        assertThat(declaration.get("cov").isNull()).isTrue();
        assertThat(declaration.get("covMul").asDouble()).isEqualTo(1.0);

        JsonNode ninety = method(doc, "ninetyPercent");
        assertThat(ninety.get("cov").asDouble()).isEqualTo(0.9);
        assertThat(ninety.get("covMul").asDouble()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("rows are ranked by composite score regardless of the order they arrive in")
    void shouldRankByCompositeScore() throws Exception {
        JsonNode doc = parse(HotspotMapSection.toJson(HotspotMapSection.buildData(sample(), false)));

        assertThat(doc.get("files").findValuesAsText("path"))
                .containsExactly("src/main/java/com/example/Hot.java", "src/main/java/com/example/Cold.java");
        assertThat(doc.get("methods").get(0).get("name").asText()).isEqualTo("untested");
        assertThat(doc.get("methods").get(0).get("rank").asInt()).isEqualTo(1);
        assertThat(doc.get("methods").get(0).get("cls").asText()).isEqualTo("Hot");
        assertThat(doc.get("methods").get(0).get("params").get(0).asText()).isEqualTo("int");
    }

    @Test
    @DisplayName("the same result always serialises to the same bytes")
    void shouldBeDeterministic() {
        String first = HotspotMapSection.dataElement(sample(), false);
        String second = HotspotMapSection.dataElement(sample(), false);

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("hostile names cannot close the script element or start markup")
    void shouldEscapeForScriptEmbedding() throws Exception {
        FileHotspot hostile = new FileHotspot(
                "src/</script><script>alert(1)</script>/Evil.java", 9, 1, 9.0, 1.0, 1.0, 1.0, 1.0);
        MethodHotspot hostileMethod = new MethodHotspot(
                new MethodSignature("com.example.<!--Evil", "a&b c", List.of("List<String>")),
                hostile.path(), 1, 2, 2, 1, 2.0, 1.0, 1.0, 1.0, 1.0);
        AnalysisResult result = new AnalysisResult(List.of(hostile), List.of(hostileMethod),
                new AnalysisMeta(Instant.parse("2026-05-21T09:00:00Z"), "LOCAL_GIT:/tmp/</script>", 1, 1, 1));

        String element = HotspotMapSection.dataElement(result, false);
        String json = element.substring(element.indexOf('>') + 1, element.lastIndexOf("</script>"));

        assertThat(json).doesNotContain("<").doesNotContain(">").doesNotContain("&").doesNotContain(" ");
        // Escaping is lossless: a JSON parser gets the original strings back.
        JsonNode doc = parse(json);
        assertThat(doc.get("files").get(0).get("path").asText()).isEqualTo(hostile.path());
        assertThat(doc.get("methods").get(0).get("name").asText()).isEqualTo("a&b c");
        assertThat(doc.get("methods").get(0).get("fqcn").asText()).isEqualTo("com.example.<!--Evil");
    }

    @Test
    @DisplayName("without an analyzer context the block still renders, with no source links")
    void shouldTolerateMissingContext() throws Exception {
        AnalysisResult bare = new AnalysisResult(
                OutputWriterTestFixtures.sampleResult().fileHotspots(),
                OutputWriterTestFixtures.sampleResult().methodHotspots(),
                OutputWriterTestFixtures.sampleResult().meta());

        JsonNode meta = parse(HotspotMapSection.toJson(HotspotMapSection.buildData(bare, true))).get("meta");

        assertThat(meta.get("blobBase").isNull()).isTrue();
        assertThat(meta.get("revision").isNull()).isTrue();
        assertThat(meta.get("halfLifeDays").asInt()).isEqualTo(90);
        assertThat(meta.get("excludeCoverage").asBoolean()).isTrue();
        assertThat(meta.get("executableLines").asInt()).isZero();
        assertThat(meta.get("totalFileScore").asDouble()).isPositive();
    }

    @Test
    @DisplayName("the absolute analysis path is reduced to the repository directory name")
    void shouldNotLeakAbsolutePath() {
        String element = HotspotMapSection.dataElement(sample(), false);

        assertThat(element).doesNotContain("/home/dev");
    }

    // ------------------------------------------------------------------

    private static JsonNode parse(String json) throws Exception {
        return JSON.readTree(json);
    }

    private static JsonNode method(JsonNode doc, String name) {
        for (JsonNode m : doc.get("methods")) {
            if (m.get("name").asText().equals(name)) {
                return m;
            }
        }
        throw new AssertionError("no method named " + name);
    }

    private static MethodHotspot methodOf(String name, double cc, double multiplier, double score, Double coverage) {
        return new MethodHotspot(
                new MethodSignature("com.example.Hot", name, List.of("int")),
                "src/main/java/com/example/Hot.java", 10, 20, 11, 2, 22.0, 1.0, cc, multiplier, score, coverage);
    }

    /** Deliberately unsorted, and cut to 2 of 9 files as output.topN would. */
    static AnalysisResult sample() {
        List<FileHotspot> files = List.of(
                new FileHotspot("src/main/java/com/example/Cold.java", 30, 1, 30.0, 0.5, 2.0, 1.0, 1.0, 0.9),
                new FileHotspot("src/main/java/com/example/Hot.java", 200, 8, 1600.0, 2.5, 40.0, 1.25, 125.0, 0.7));
        List<MethodHotspot> methods = List.of(
                methodOf("ninetyPercent", 3, 1.0, 3.0, 0.9),
                methodOf("declaration", 0, 1.0, 0.0, null),
                methodOf("untested", 4, 10.0, 40.0, 0.0));
        CoverageBreakdown breakdown = new CoverageBreakdown("build/jacoco.xml", List.of(
                new CoverageBreakdown.FileCoverage("src/main/java/com/example/Hot.java", 70, 100, 0.7),
                new CoverageBreakdown.FileCoverage("src/main/java/com/example/Cold.java", 18, 20, 0.9)), List.of());
        return new AnalysisResult(files, methods, List.of(), List.of(),
                new AnalysisMeta(Instant.parse("2026-05-21T09:00:00Z"), "LOCAL_GIT:/home/dev/work/app", 42, 9, 55),
                breakdown,
                new ReportContext(BLOB, "abc1234", 30, 250.0));
    }
}
