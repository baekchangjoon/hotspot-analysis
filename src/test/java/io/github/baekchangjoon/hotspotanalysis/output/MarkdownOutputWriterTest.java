package io.github.baekchangjoon.hotspotanalysis.output;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownOutputWriterTest {

    private final MarkdownOutputWriter writer = new MarkdownOutputWriter();

    @Test
    @DisplayName("writes a top-level title and three sections")
    void shouldRenderThreeSections(@TempDir Path tempDir) throws IOException {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);

        String md = Files.readString(tempDir.resolve("hotspots.md"));
        assertThat(md).startsWith("# Hotspot Analysis Report");
        assertThat(md).contains("## File Hotspots");
        assertThat(md).contains("## Method Hotspots");
    }

    @Test
    @DisplayName("renders the file table with header, separator, and one row per hotspot")
    void shouldRenderFileTable(@TempDir Path tempDir) throws IOException {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);

        String md = Files.readString(tempDir.resolve("hotspots.md"));
        assertThat(md).contains("| Rank | Path | Revisions | LOC | Score |");
        assertThat(md).contains("|---:|:---|---:|---:|---:|");
        assertThat(md).contains(
                "| 1 | `src/main/java/com/example/Hot.java` | 5 | 120 | 600 |");
    }

    @Test
    @DisplayName("renders the method table with canonical signatures")
    void shouldRenderMethodTable(@TempDir Path tempDir) throws IOException {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);

        String md = Files.readString(tempDir.resolve("hotspots.md"));
        assertThat(md).contains(
                "| 1 | `com.example.Hot#doWork(int, String)` | "
                        + "`src/main/java/com/example/Hot.java` | 12-28 | 4 | 17 | 68 |");
    }

    @Test
    @DisplayName("embeds meta block with target description and counts")
    void shouldEmbedMetaTable(@TempDir Path tempDir) throws IOException {
        writer.write(OutputWriterTestFixtures.sampleResult(), tempDir);

        String md = Files.readString(tempDir.resolve("hotspots.md"));
        assertThat(md).contains("| Generated at | 2026-05-21T09:00:00Z |");
        assertThat(md).contains("| Target | `LOCAL_GIT:/tmp/example` |");
        assertThat(md).contains("| Scoring formula | SIMPLE |");
        assertThat(md).contains("| Total commits | 42 |");
    }

    @Test
    @DisplayName("writes hotspots.md (combined) and api_report.md (standalone) when API analysis is enabled")
    void shouldWriteApiMarkdownFiles(@TempDir Path tempDir) throws IOException {
        writer.write(OutputWriterTestFixtures.sampleApiResult(), tempDir,
                new io.github.baekchangjoon.hotspotanalysis.config.OutputConfig(
                        java.util.List.of(io.github.baekchangjoon.hotspotanalysis.config.OutputConfig.OutputFormat.MD),
                        tempDir.toString(),
                        0,
                        io.github.baekchangjoon.hotspotanalysis.config.OutputConfig.ApiLayout.BOTH),
                true);

        Path hotspotsMd = tempDir.resolve("hotspots.md");
        Path apiReportMd = tempDir.resolve("api_report.md");

        assertThat(hotspotsMd).exists();
        assertThat(apiReportMd).exists();

        String combinedContent = Files.readString(hotspotsMd);
        assertThat(combinedContent).contains("# Hotspot Analysis Report");
        assertThat(combinedContent).contains("## File Hotspots");
        assertThat(combinedContent).contains("## Method Hotspots");
        assertThat(combinedContent).contains("## REST API Hotspots");
        assertThat(combinedContent).contains("## Shared Components");

        String standaloneContent = Files.readString(apiReportMd);
        assertThat(standaloneContent).contains("# RESTful API Hotspot Analysis Report");
        assertThat(standaloneContent).doesNotContain("## File Hotspots");
        assertThat(standaloneContent).doesNotContain("## Method Hotspots");
        assertThat(standaloneContent).contains("## REST API Hotspots");
        assertThat(standaloneContent).contains("## Shared Components");
    }
}
