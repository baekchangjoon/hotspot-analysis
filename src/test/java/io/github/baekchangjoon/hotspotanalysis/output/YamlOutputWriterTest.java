package io.github.baekchangjoon.hotspotanalysis.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class YamlOutputWriterTest {

    private final YamlOutputWriter writer = new YamlOutputWriter();

    @Test
    @DisplayName("emits a single hotspots.yml file with meta, fileHotspots, methodHotspots")
    void shouldEmitYamlFile(@TempDir Path tempDir) throws IOException {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);

        Path yaml = tempDir.resolve("hotspots.yml");
        assertThat(yaml).exists();
        String content = Files.readString(yaml);
        assertThat(content).contains("meta:");
        assertThat(content).contains("fileHotspots:");
        assertThat(content).contains("methodHotspots:");
        assertThat(content).contains("analyzedAt: \"2026-05-21T09:00:00Z\"");
        assertThat(content).doesNotContain("scoringFormula");
    }

    @Test
    @DisplayName("yaml round-trips back to the structural shape we wrote")
    void shouldRoundTripYaml(@TempDir Path tempDir) throws IOException {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);

        ObjectMapper parser = new ObjectMapper(new YAMLFactory())
                .registerModule(new JavaTimeModule());
        Map<String, Object> parsed = parser.readValue(
                tempDir.resolve("hotspots.yml").toFile(), Map.class);

        assertThat(parsed).containsKeys("meta", "fileHotspots", "methodHotspots");
        Map<String, Object> meta = (Map<String, Object>) parsed.get("meta");
        assertThat(meta).containsEntry("totalCommits", 42);
        assertThat(meta).containsEntry("targetDescription", "LOCAL_GIT:/tmp/example");
    }

    @Test
    @DisplayName("fileHotspot rows use canonical flat key layout in correct order")
    void shouldEmitCanonicalFileHotspotKeys(@TempDir Path tempDir) throws IOException {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);

        ObjectMapper parser = new ObjectMapper(new YAMLFactory())
                .registerModule(new JavaTimeModule());
        Map<String, Object> parsed = parser.readValue(
                tempDir.resolve("hotspots.yml").toFile(), Map.class);

        List<Map<String, Object>> fileRows = (List<Map<String, Object>>) parsed.get("fileHotspots");
        assertThat(fileRows).isNotEmpty();

        Map<String, Object> row = fileRows.get(0);
        // All canonical keys present
        assertThat(row).containsKeys(
                "rank", "path", "loc", "revisions",
                "simpleScore", "recencyDecay", "cognitiveComplexity",
                "coverageMultiplier", "compositeScore");

        // No legacy keys
        assertThat(row).doesNotContainKey("score");
        assertThat(row).doesNotContainKey("decayedRevisions");
        assertThat(row).doesNotContainKey("coverage");

        // Canonical key order
        List<String> keys = List.copyOf(row.keySet());
        assertThat(keys).containsExactly(
                "rank", "path", "loc", "revisions",
                "simpleScore", "recencyDecay", "cognitiveComplexity",
                "coverageMultiplier", "compositeScore");

        // Spot-check values from sampleResult (first entry: Hot.java)
        assertThat(row).containsEntry("rank", 1);
        assertThat(row).containsEntry("path", "src/main/java/com/example/Hot.java");
        assertThat(row).containsEntry("loc", 120);
        assertThat(row).containsEntry("revisions", 5);
    }

    @Test
    @DisplayName("methodHotspot rows use canonical flat key layout in correct order")
    void shouldEmitCanonicalMethodHotspotKeys(@TempDir Path tempDir) throws IOException {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);

        ObjectMapper parser = new ObjectMapper(new YAMLFactory())
                .registerModule(new JavaTimeModule());
        Map<String, Object> parsed = parser.readValue(
                tempDir.resolve("hotspots.yml").toFile(), Map.class);

        List<Map<String, Object>> methodRows = (List<Map<String, Object>>) parsed.get("methodHotspots");
        assertThat(methodRows).isNotEmpty();

        Map<String, Object> row = methodRows.get(0);
        // All canonical keys present
        assertThat(row).containsKeys(
                "rank", "fqcn", "method", "parameters", "file", "startLine", "endLine",
                "loc", "revisions", "simpleScore", "recencyDecay", "cognitiveComplexity",
                "coverageMultiplier", "compositeScore");

        // No legacy keys
        assertThat(row).doesNotContainKey("score");
        assertThat(row).doesNotContainKey("signature");

        // Canonical key order
        List<String> keys = List.copyOf(row.keySet());
        assertThat(keys).containsExactly(
                "rank", "fqcn", "method", "parameters", "file", "startLine", "endLine",
                "loc", "revisions", "simpleScore", "recencyDecay", "cognitiveComplexity",
                "coverageMultiplier", "compositeScore");

        // Spot-check values
        assertThat(row).containsEntry("rank", 1);
        assertThat(row).containsEntry("fqcn", "com.example.Hot");
        assertThat(row).containsEntry("method", "doWork");
    }

    @Test
    @DisplayName("emits api_report.yml when API analysis is enabled and layout is STANDALONE or BOTH")
    void shouldEmitApiYamlFiles(@TempDir Path tempDir) throws IOException {
        writer.write(OutputWriterTestFixtures.sampleApiResult(), tempDir,
                new io.github.baekchangjoon.hotspotanalysis.config.OutputConfig(
                        java.util.List.of(io.github.baekchangjoon.hotspotanalysis.config.OutputConfig.OutputFormat.YAML),
                        tempDir.toString(),
                        0,
                        io.github.baekchangjoon.hotspotanalysis.config.OutputConfig.ApiLayout.BOTH),
                true);

        Path hotspotsYaml = tempDir.resolve("hotspots.yml");
        Path apiReportYaml = tempDir.resolve("api_report.yml");

        assertThat(hotspotsYaml).exists();
        assertThat(apiReportYaml).exists();

        ObjectMapper parser = new ObjectMapper(new YAMLFactory())
                .registerModule(new JavaTimeModule());

        Map<String, Object> parsedCombined = parser.readValue(hotspotsYaml.toFile(), Map.class);
        assertThat(parsedCombined).containsKeys("meta", "fileHotspots", "methodHotspots", "apiHotspots", "sharedComponents");

        Map<String, Object> parsedStandalone = parser.readValue(apiReportYaml.toFile(), Map.class);
        assertThat(parsedStandalone).containsKeys("meta", "apiHotspots", "sharedComponents");
        assertThat(parsedStandalone).doesNotContainKey("fileHotspots");
        assertThat(parsedStandalone).doesNotContainKey("methodHotspots");
    }

    @Test
    @DisplayName("apiHotspot rows use canonical flat key layout in correct order")
    void shouldEmitCanonicalApiHotspotKeys(@TempDir Path tempDir) throws IOException {
        writer.write(OutputWriterTestFixtures.sampleApiResult(), tempDir,
                new io.github.baekchangjoon.hotspotanalysis.config.OutputConfig(
                        java.util.List.of(io.github.baekchangjoon.hotspotanalysis.config.OutputConfig.OutputFormat.YAML),
                        tempDir.toString(),
                        0,
                        io.github.baekchangjoon.hotspotanalysis.config.OutputConfig.ApiLayout.BOTH),
                true);

        ObjectMapper parser = new ObjectMapper(new YAMLFactory())
                .registerModule(new JavaTimeModule());

        // Check combined file
        Map<String, Object> parsed = parser.readValue(
                tempDir.resolve("hotspots.yml").toFile(), Map.class);

        List<Map<String, Object>> apiRows = (List<Map<String, Object>>) parsed.get("apiHotspots");
        assertThat(apiRows).isNotEmpty();

        Map<String, Object> row = apiRows.get(0);
        // All canonical keys present
        assertThat(row).containsKeys(
                "rank", "httpMethod", "route", "fqcn", "method", "parameters",
                "loc", "revisions", "simpleScore", "recencyDecay", "cognitiveComplexity",
                "coverageMultiplier", "compositeScore", "callGraph");

        // No legacy keys
        assertThat(row).doesNotContainKey("controllerMethod");
        assertThat(row).doesNotContainKey("score");

        // Canonical key order
        List<String> keys = List.copyOf(row.keySet());
        assertThat(keys).containsExactly(
                "rank", "httpMethod", "route", "fqcn", "method", "parameters",
                "loc", "revisions", "simpleScore", "recencyDecay", "cognitiveComplexity",
                "coverageMultiplier", "compositeScore", "callGraph");

        // Spot-check values
        assertThat(row).containsEntry("rank", 1);
        assertThat(row).containsEntry("httpMethod", "GET");
        assertThat(row).containsEntry("route", "/api/a");
        assertThat(row).containsEntry("fqcn", "com.example.MyController");
        assertThat(row).containsEntry("method", "apiA");
    }

    @Test
    @DisplayName("whole-number scores are emitted as integers, not floats (e.g. 600 not 600.0)")
    void emitsWholeNumberSimpleScoreAsInteger(@TempDir Path tempDir) throws IOException {
        // sampleResult() → Hot.java has loc=120, revisions=5 → simpleScore=600.0 (whole number)
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);

        String yaml = Files.readString(tempDir.resolve("hotspots.yml"));
        // 600 must be emitted as integer, not as 600.0
        assertThat(yaml).contains("simpleScore: 600\n");
        assertThat(yaml).doesNotContain("simpleScore: 600.0");
    }

    @Test
    @DisplayName("sharedComponent rows use canonical flat key layout in correct order")
    void shouldEmitCanonicalSharedComponentKeys(@TempDir Path tempDir) throws IOException {
        writer.write(OutputWriterTestFixtures.sampleApiResult(), tempDir,
                new io.github.baekchangjoon.hotspotanalysis.config.OutputConfig(
                        java.util.List.of(io.github.baekchangjoon.hotspotanalysis.config.OutputConfig.OutputFormat.YAML),
                        tempDir.toString(),
                        0,
                        io.github.baekchangjoon.hotspotanalysis.config.OutputConfig.ApiLayout.BOTH),
                true);

        ObjectMapper parser = new ObjectMapper(new YAMLFactory())
                .registerModule(new JavaTimeModule());

        Map<String, Object> parsed = parser.readValue(
                tempDir.resolve("hotspots.yml").toFile(), Map.class);

        List<Map<String, Object>> sharedRows = (List<Map<String, Object>>) parsed.get("sharedComponents");
        assertThat(sharedRows).isNotEmpty();

        Map<String, Object> row = sharedRows.get(0);
        // All canonical keys present
        assertThat(row).containsKeys(
                "rank", "fqcn", "method", "parameters",
                "loc", "revisions", "simpleScore", "recencyDecay", "cognitiveComplexity",
                "coverageMultiplier", "compositeScore", "callingApis");

        // No legacy keys
        assertThat(row).doesNotContainKey("score");

        // Canonical key order
        List<String> keys = List.copyOf(row.keySet());
        assertThat(keys).containsExactly(
                "rank", "fqcn", "method", "parameters",
                "loc", "revisions", "simpleScore", "recencyDecay", "cognitiveComplexity",
                "coverageMultiplier", "compositeScore", "callingApis");

        // Spot-check values
        assertThat(row).containsEntry("rank", 1);
        assertThat(row).containsEntry("fqcn", "com.example.MyService");
        assertThat(row).containsEntry("method", "commonMethod");
    }

    @Test
    @DisplayName("excludeCoverage=true swaps coverageMultiplier for lineCoverage at the rightmost key")
    @SuppressWarnings("unchecked")
    void shouldEmitLineCoverageAtRightmostKeyWhenExcludeCoverage(@TempDir Path tempDir) throws IOException {
        io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult result =
                new io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult(
                        List.of(new io.github.baekchangjoon.hotspotanalysis.analysis.model.FileHotspot(
                                "Y.java",
                                /* loc */ 10, /* revisions */ 1,
                                /* simpleScore */ 10.0, /* recencyDecay */ 1.0,
                                /* cognitiveComplexity */ 2.0, /* coverageMultiplier */ 1.0,
                                /* compositeScore */ 2.0,
                                /* lineCoverage */ 0.75)),
                        List.of(),
                        new io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisMeta(
                                OutputWriterTestFixtures.FIXED_INSTANT, "LOCAL_GIT:/tmp", 1, 1, 0));

        writer.write(result, tempDir,
                new io.github.baekchangjoon.hotspotanalysis.config.OutputConfig(
                        List.of(io.github.baekchangjoon.hotspotanalysis.config.OutputConfig.OutputFormat.YAML),
                        tempDir.toString(), 0),
                false, true);

        Map<String, Object> parsed = new ObjectMapper(new YAMLFactory())
                .registerModule(new JavaTimeModule())
                .readValue(tempDir.resolve("hotspots.yml").toFile(), Map.class);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) parsed.get("fileHotspots");
        Map<String, Object> row = rows.get(0);

        assertThat(row).containsKey("lineCoverage");
        assertThat(row).doesNotContainKey("coverageMultiplier");

        List<String> keys = List.copyOf(row.keySet());
        assertThat(keys).containsExactly(
                "rank", "path", "loc", "revisions",
                "simpleScore", "recencyDecay", "cognitiveComplexity",
                "compositeScore", "lineCoverage");
        assertThat(row).containsEntry("lineCoverage", 0.75);
    }

    @Test
    @DisplayName("excludeCoverage=true emits the string \"N/A\" for lineCoverage when JaCoCo absent")
    @SuppressWarnings("unchecked")
    void shouldEmitNAForMissingLineCoverage(@TempDir Path tempDir) throws IOException {
        io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult result =
                new io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult(
                        List.of(new io.github.baekchangjoon.hotspotanalysis.analysis.model.FileHotspot(
                                "Z.java",
                                /* loc */ 1, /* revisions */ 1,
                                /* simpleScore */ 1.0, /* recencyDecay */ 1.0,
                                /* cognitiveComplexity */ 1.0, /* coverageMultiplier */ 1.0,
                                /* compositeScore */ 1.0,
                                /* lineCoverage */ null)),
                        List.of(),
                        new io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisMeta(
                                OutputWriterTestFixtures.FIXED_INSTANT, "LOCAL_GIT:/tmp", 1, 1, 0));

        writer.write(result, tempDir,
                new io.github.baekchangjoon.hotspotanalysis.config.OutputConfig(
                        List.of(io.github.baekchangjoon.hotspotanalysis.config.OutputConfig.OutputFormat.YAML),
                        tempDir.toString(), 0),
                false, true);

        Map<String, Object> parsed = new ObjectMapper(new YAMLFactory())
                .registerModule(new JavaTimeModule())
                .readValue(tempDir.resolve("hotspots.yml").toFile(), Map.class);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) parsed.get("fileHotspots");
        assertThat(rows.get(0)).containsEntry("lineCoverage", "N/A");
    }

    @Test
    @DisplayName("excludeCoverage=true emits lineCoverage at rightmost on apiHotspots and sharedComponents rows")
    @SuppressWarnings("unchecked")
    void shouldEmitLineCoverageOnApiAndSharedRows(@TempDir Path tempDir) throws IOException {
        writer.write(OutputWriterTestFixtures.sampleApiResultWithCoverage(), tempDir,
                new io.github.baekchangjoon.hotspotanalysis.config.OutputConfig(
                        List.of(io.github.baekchangjoon.hotspotanalysis.config.OutputConfig.OutputFormat.YAML),
                        tempDir.toString(),
                        0,
                        io.github.baekchangjoon.hotspotanalysis.config.OutputConfig.ApiLayout.BOTH),
                true, true);

        ObjectMapper parser = new ObjectMapper(new YAMLFactory())
                .registerModule(new JavaTimeModule());

        Map<String, Object> parsed = parser.readValue(
                tempDir.resolve("hotspots.yml").toFile(), Map.class);

        // API row: canonical key order ends in compositeScore, callGraph, lineCoverage.
        Map<String, Object> apiRow = ((List<Map<String, Object>>) parsed.get("apiHotspots")).get(0);
        assertThat(apiRow).containsKey("lineCoverage").doesNotContainKey("coverageMultiplier");
        List<String> apiKeys = List.copyOf(apiRow.keySet());
        assertThat(apiKeys).endsWith("compositeScore", "callGraph", "lineCoverage");
        assertThat(apiRow).containsEntry("lineCoverage", 0.42);

        // Shared row: rightmost key is lineCoverage; null coverage → "N/A".
        Map<String, Object> sharedRow = ((List<Map<String, Object>>) parsed.get("sharedComponents")).get(0);
        assertThat(sharedRow).containsKey("lineCoverage").doesNotContainKey("coverageMultiplier");
        List<String> sharedKeys = List.copyOf(sharedRow.keySet());
        assertThat(sharedKeys).endsWith("compositeScore", "callingApis", "lineCoverage");
        assertThat(sharedRow).containsEntry("lineCoverage", "N/A");

        // Standalone api_report.yml also gets the swapped layout.
        Map<String, Object> standalone = parser.readValue(
                tempDir.resolve("api_report.yml").toFile(), Map.class);
        Map<String, Object> standaloneApi = ((List<Map<String, Object>>) standalone.get("apiHotspots")).get(0);
        assertThat(standaloneApi).containsKey("lineCoverage").doesNotContainKey("coverageMultiplier");
    }
}
