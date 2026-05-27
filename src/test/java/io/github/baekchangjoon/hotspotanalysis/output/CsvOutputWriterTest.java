package io.github.baekchangjoon.hotspotanalysis.output;

import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisMeta;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.FileHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.MethodHotspot;
import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvOutputWriterTest {

    private final CsvOutputWriter writer = new CsvOutputWriter();

    @Test
    @DisplayName("writes file_hotspots.csv with header and ranked rows")
    void shouldWriteFileHotspotsCsv(@TempDir Path tempDir) throws IOException {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);

        Path csv = tempDir.resolve("file_hotspots.csv");
        assertThat(csv).exists();
        String content = Files.readString(csv);
        assertThat(content).startsWith(
                "rank,path,loc,revisions,simple_score,recency_decay,cognitive_complexity,coverage_multiplier,composite_score\n");
        assertThat(content).contains(
                "1,src/main/java/com/example/Hot.java,120,5,600,4.2500,8,1,34\n");
        assertThat(content).contains(
                "2,src/main/java/com/example/Cold.java,30,1,30,0.7500,2,1,1.5000\n");
    }

    @Test
    @DisplayName("writes method_hotspots.csv with full method signature columns")
    void shouldWriteMethodHotspotsCsv(@TempDir Path tempDir) throws IOException {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);

        Path csv = tempDir.resolve("method_hotspots.csv");
        assertThat(csv).exists();
        String content = Files.readString(csv);
        assertThat(content).startsWith(
                "rank,fqcn,method,parameters,file,start_line,end_line,loc,revisions,simple_score,recency_decay,cognitive_complexity,coverage_multiplier,composite_score\n");
        assertThat(content).contains(
                "1,com.example.Hot,doWork,int;String,"
                        + "src/main/java/com/example/Hot.java,12,28,17,4,68,3.4000,6,1,20.4000\n");
        assertThat(content).contains(
                "2,com.example.Hot,doWork,,"
                        + "src/main/java/com/example/Hot.java,30,32,3,1,3,0.8500,2,1,1.7000\n");
    }

    @Test
    @DisplayName("escapes commas in field values with double quotes")
    void shouldEscapeCommasInFieldValues(@TempDir Path tempDir) throws IOException {
        AnalysisResult result = new AnalysisResult(
                List.of(new FileHotspot(
                        "path/with,comma.java",
                        /* loc */ 10, /* revisions */ 1,
                        /* simpleScore */ 10.0, /* recencyDecay */ 0.5,
                        /* cognitiveComplexity */ 1.0, /* coverageMultiplier */ 1.0,
                        /* compositeScore */ 0.5)),
                List.of(new MethodHotspot(
                        new MethodSignature("a.B", "m", List.of("int", "String")),
                        "path/with,comma.java", 1, 5,
                        /* loc */ 5, /* revisions */ 1,
                        /* simpleScore */ 5.0, /* recencyDecay */ 0.5,
                        /* cognitiveComplexity */ 1.0, /* coverageMultiplier */ 1.0,
                        /* compositeScore */ 0.5)),
                new AnalysisMeta(OutputWriterTestFixtures.FIXED_INSTANT,
                        "LOCAL_GIT:/tmp", 1, 1, 1));

        writer.write(result, tempDir);

        String fileCsv = Files.readString(tempDir.resolve("file_hotspots.csv"));
        assertThat(fileCsv).contains("\"path/with,comma.java\"");
    }

    @Test
    @DisplayName("formats fractional scores with 4 decimal places")
    void shouldFormatFractionalScores(@TempDir Path tempDir) throws IOException {
        AnalysisResult result = new AnalysisResult(
                List.of(new FileHotspot(
                        "A.java",
                        /* loc */ 7, /* revisions */ 3,
                        /* simpleScore */ 21.5, /* recencyDecay */ 1.0,
                        /* cognitiveComplexity */ 1.0, /* coverageMultiplier */ 1.0,
                        /* compositeScore */ 1.0)),
                List.of(),
                new AnalysisMeta(OutputWriterTestFixtures.FIXED_INSTANT,
                        "LOCAL_GIT:/tmp", 1, 1, 0));

        writer.write(result, tempDir);

        assertThat(Files.readString(tempDir.resolve("file_hotspots.csv")))
                .contains("1,A.java,7,3,21.5000,1,1,1,1\n");
    }

    @Test
    @DisplayName("excludeCoverage=true replaces coverage_multiplier with line_coverage at rightmost column")
    void shouldEmitLineCoverageAtRightmostWhenExcludeCoverage(@TempDir Path tempDir) throws IOException {
        AnalysisResult result = new AnalysisResult(
                List.of(new FileHotspot(
                        "Foo.java",
                        /* loc */ 100, /* revisions */ 2,
                        /* simpleScore */ 200.0, /* recencyDecay */ 1.5,
                        /* cognitiveComplexity */ 4.0, /* coverageMultiplier */ 1.0,
                        /* compositeScore */ 6.0,
                        /* lineCoverage */ 0.833)),
                List.of(new MethodHotspot(
                        new MethodSignature("a.B", "m", List.of()),
                        "Foo.java", 1, 5,
                        /* loc */ 5, /* revisions */ 1,
                        /* simpleScore */ 5.0, /* recencyDecay */ 0.5,
                        /* cognitiveComplexity */ 1.0, /* coverageMultiplier */ 1.0,
                        /* compositeScore */ 0.5,
                        /* lineCoverage */ null)),
                new AnalysisMeta(OutputWriterTestFixtures.FIXED_INSTANT,
                        "LOCAL_GIT:/tmp", 1, 1, 1));

        writer.write(result, tempDir,
                new io.github.baekchangjoon.hotspotanalysis.config.OutputConfig(
                        List.of(io.github.baekchangjoon.hotspotanalysis.config.OutputConfig.OutputFormat.CSV),
                        tempDir.toString(), 0),
                false, true);

        String fileCsv = Files.readString(tempDir.resolve("file_hotspots.csv"));
        assertThat(fileCsv).startsWith(
                "rank,path,loc,revisions,simple_score,recency_decay,"
                + "cognitive_complexity,composite_score,line_coverage\n");
        assertThat(fileCsv).doesNotContain("coverage_multiplier");
        // Composite Score precedes Line Coverage; coverage rendered as percentage with one decimal.
        assertThat(fileCsv).contains("1,Foo.java,100,2,200,1.5000,4,6,83.3%\n");

        String methodCsv = Files.readString(tempDir.resolve("method_hotspots.csv"));
        assertThat(methodCsv).startsWith(
                "rank,fqcn,method,parameters,file,start_line,end_line,"
                + "loc,revisions,simple_score,recency_decay,"
                + "cognitive_complexity,composite_score,line_coverage\n");
        // No JaCoCo data → N/A in the rightmost cell.
        assertThat(methodCsv).contains(",1,5,0.5000,1,0.5000,N/A\n");
    }

    @Test
    @DisplayName("writes api_hotspots.csv and shared_components.csv when API analysis is enabled")
    void shouldWriteApiAndSharedComponentsCsv(@TempDir Path tempDir) throws IOException {
        writer.write(OutputWriterTestFixtures.sampleApiResult(), tempDir,
                new io.github.baekchangjoon.hotspotanalysis.config.OutputConfig(
                        List.of(io.github.baekchangjoon.hotspotanalysis.config.OutputConfig.OutputFormat.CSV),
                        tempDir.toString(),
                        0,
                        io.github.baekchangjoon.hotspotanalysis.config.OutputConfig.ApiLayout.BOTH),
                true);

        Path apiCsv = tempDir.resolve("api_hotspots.csv");
        Path sharedCsv = tempDir.resolve("shared_components.csv");

        assertThat(apiCsv).exists();
        assertThat(sharedCsv).exists();

        String apiContent = Files.readString(apiCsv);
        assertThat(apiContent).startsWith(
                "rank,http_method,route,fqcn,method,parameters,loc,revisions,simple_score,recency_decay,cognitive_complexity,coverage_multiplier,composite_score\n");
        assertThat(apiContent).contains(
                "1,GET,/api/a,com.example.MyController,apiA,,120,5,600,4.2500,8,1,34\n");

        String sharedContent = Files.readString(sharedCsv);
        assertThat(sharedContent).startsWith(
                "rank,fqcn,method,parameters,loc,revisions,simple_score,recency_decay,cognitive_complexity,coverage_multiplier,composite_score,calling_apis\n");
        assertThat(sharedContent).contains(
                "1,com.example.MyService,commonMethod,,30,1,30,0.8500,2,1,1.7000,GET /api/a\n");
    }
}
