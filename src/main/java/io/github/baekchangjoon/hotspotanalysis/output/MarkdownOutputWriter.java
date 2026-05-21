package io.github.baekchangjoon.hotspotanalysis.output;

import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisMeta;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.FileHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.MethodHotspot;
import io.github.baekchangjoon.hotspotanalysis.config.OutputConfig;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the analysis result as a single human-readable {@code hotspots.md}
 * file, suitable for pasting into a PR description or wiki.
 */
@Component
public class MarkdownOutputWriter implements OutputWriter {

    @Override
    public OutputConfig.OutputFormat format() {
        return OutputConfig.OutputFormat.MD;
    }

    @Override
    public void write(AnalysisResult result, Path outputDir) {
        try {
            Files.createDirectories(outputDir);
            String body = render(result);
            Files.writeString(outputDir.resolve("hotspots.md"), body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new OutputException("Failed to write MD output to " + outputDir, e);
        }
    }

    private static String render(AnalysisResult result) {
        StringBuilder sb = new StringBuilder(2_048);
        AnalysisMeta meta = result.meta();

        sb.append("# Hotspot Analysis Report\n\n");
        sb.append("| Field | Value |\n|---|---|\n");
        sb.append("| Generated at | ").append(meta.analyzedAt()).append(" |\n");
        sb.append("| Target | `").append(meta.targetDescription()).append("` |\n");
        sb.append("| Scoring formula | ").append(meta.scoringFormula()).append(" |\n");
        sb.append("| Total commits | ").append(meta.totalCommits()).append(" |\n");
        sb.append("| Total files | ").append(meta.totalFiles()).append(" |\n");
        sb.append("| Total methods | ").append(meta.totalMethods()).append(" |\n\n");

        sb.append("## File Hotspots (").append(result.fileHotspots().size()).append(" rows)\n\n");
        sb.append("| Rank | Path | Revisions | LOC | Score |\n");
        sb.append("|---:|:---|---:|---:|---:|\n");
        int rank = 1;
        for (FileHotspot file : result.fileHotspots()) {
            sb.append("| ").append(rank++).append(" | `").append(file.path()).append("` | ")
                    .append(file.revisions()).append(" | ")
                    .append(file.loc()).append(" | ")
                    .append(formatScore(file.score())).append(" |\n");
        }
        sb.append("\n");

        sb.append("## Method Hotspots (").append(result.methodHotspots().size()).append(" rows)\n\n");
        sb.append("| Rank | Method | File | Lines | Revisions | LOC | Score |\n");
        sb.append("|---:|:---|:---|:---:|---:|---:|---:|\n");
        rank = 1;
        for (MethodHotspot method : result.methodHotspots()) {
            sb.append("| ").append(rank++).append(" | `")
                    .append(method.signature().toCanonicalString()).append("` | `")
                    .append(method.filePath()).append("` | ")
                    .append(method.startLine()).append("-").append(method.endLine()).append(" | ")
                    .append(method.revisions()).append(" | ")
                    .append(method.loc()).append(" | ")
                    .append(formatScore(method.score())).append(" |\n");
        }
        return sb.toString();
    }

    private static String formatScore(double score) {
        if (score == Math.floor(score) && !Double.isInfinite(score)) {
            return String.valueOf((long) score);
        }
        return String.format("%.4f", score);
    }
}
