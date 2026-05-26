package io.github.baekchangjoon.hotspotanalysis.output;

import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisMeta;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.FileHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.MethodHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.ApiHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.SharedComponentHotspot;
import io.github.baekchangjoon.hotspotanalysis.config.OutputConfig;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes the analysis result as a single human-readable {@code hotspots.md}
 * and/or {@code api_report.md} file, suitable for pasting into a PR description or wiki.
 *
 * <p>Column order follows the canonical 7-metric block:
 * {@code loc, revisions, simpleScore, recencyDecay, cognitiveComplexity,
 * coverageMultiplier, compositeScore}.</p>
 */
@Component
public class MarkdownOutputWriter implements OutputWriter {

    @Override
    public OutputConfig.OutputFormat format() {
        return OutputConfig.OutputFormat.MD;
    }

    @Override
    public void write(AnalysisResult result, Path outputDir) {
        write(result, outputDir, new OutputConfig(List.of(OutputConfig.OutputFormat.MD), outputDir.toString(), 0), false);
    }

    @Override
    public void write(AnalysisResult result, Path outputDir, OutputConfig outputConfig, boolean apiEnabled) {
        try {
            Files.createDirectories(outputDir);

            boolean combined = outputConfig.apiLayout() == OutputConfig.ApiLayout.COMBINED ||
                               outputConfig.apiLayout() == OutputConfig.ApiLayout.BOTH;
            boolean standalone = outputConfig.apiLayout() == OutputConfig.ApiLayout.STANDALONE ||
                                 outputConfig.apiLayout() == OutputConfig.ApiLayout.BOTH;

            if (!apiEnabled) {
                String body = renderCombined(result, false);
                Files.writeString(outputDir.resolve("hotspots.md"), body, StandardCharsets.UTF_8);
                return;
            }

            if (combined) {
                String body = renderCombined(result, true);
                Files.writeString(outputDir.resolve("hotspots.md"), body, StandardCharsets.UTF_8);
            }

            if (standalone) {
                String body = renderStandalone(result);
                Files.writeString(outputDir.resolve("api_report.md"), body, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new OutputException("Failed to write MD output to " + outputDir, e);
        }
    }

    private static String renderCombined(AnalysisResult result, boolean includeApi) {
        StringBuilder sb = new StringBuilder(4_096);
        AnalysisMeta meta = result.meta();

        sb.append("# Hotspot Analysis Report\n\n");
        sb.append("| Field | Value |\n|---|---|\n");
        sb.append("| Generated at | ").append(meta.analyzedAt()).append(" |\n");
        sb.append("| Target | `").append(meta.targetDescription()).append("` |\n");
        sb.append("| Total commits | ").append(meta.totalCommits()).append(" |\n");
        sb.append("| Total files | ").append(meta.totalFiles()).append(" |\n");
        sb.append("| Total methods | ").append(meta.totalMethods()).append(" |\n\n");

        appendFileHotspots(sb, result.fileHotspots());
        appendMethodHotspots(sb, result.methodHotspots());

        if (includeApi) {
            appendApiHotspots(sb, result.apiHotspots());
            appendSharedComponents(sb, result.sharedComponents());
        }

        return sb.toString();
    }

    private static String renderStandalone(AnalysisResult result) {
        StringBuilder sb = new StringBuilder(4_096);
        AnalysisMeta meta = result.meta();

        sb.append("# RESTful API Hotspot Analysis Report\n\n");
        sb.append("| Field | Value |\n|---|---|\n");
        sb.append("| Generated at | ").append(meta.analyzedAt()).append(" |\n");
        sb.append("| Target | `").append(meta.targetDescription()).append("` |\n");
        sb.append("| Total commits | ").append(meta.totalCommits()).append(" |\n");
        sb.append("| Total files | ").append(meta.totalFiles()).append(" |\n");
        sb.append("| Total methods | ").append(meta.totalMethods()).append(" |\n\n");

        appendApiHotspots(sb, result.apiHotspots());
        appendSharedComponents(sb, result.sharedComponents());

        return sb.toString();
    }

    private static void appendFileHotspots(StringBuilder sb, List<FileHotspot> files) {
        sb.append("## File Hotspots (").append(files.size()).append(" rows)\n\n");
        sb.append("| Rank | Path | LOC | Revisions | Simple Score | Recency Decay | Cognitive Complexity | Coverage Multiplier | Composite Score |\n");
        sb.append("|---:|:---|---:|---:|---:|---:|---:|---:|---:|\n");
        int rank = 1;
        for (FileHotspot file : files) {
            sb.append("| ").append(rank++).append(" | `").append(file.path()).append("` | ")
                    .append(file.loc()).append(" | ")
                    .append(file.revisions()).append(" | ")
                    .append(fmt(file.simpleScore())).append(" | ")
                    .append(fmt(file.recencyDecay())).append(" | ")
                    .append(fmt(file.cognitiveComplexity())).append(" | ")
                    .append(fmt(file.coverageMultiplier())).append(" | ")
                    .append(fmt(file.compositeScore())).append(" |\n");
        }
        sb.append("\n");
    }

    private static void appendMethodHotspots(StringBuilder sb, List<MethodHotspot> methods) {
        sb.append("## Method Hotspots (").append(methods.size()).append(" rows)\n\n");
        sb.append("| Rank | FQCN | Method | Parameters | File | Lines | LOC | Revisions | Simple Score | Recency Decay | Cognitive Complexity | Coverage Multiplier | Composite Score |\n");
        sb.append("|---:|:---|:---|:---|:---|:---:|---:|---:|---:|---:|---:|---:|---:|\n");
        int rank = 1;
        for (MethodHotspot method : methods) {
            String params = String.join(", ", method.signature().parameterTypes());
            sb.append("| ").append(rank++).append(" | `")
                    .append(method.signature().fullyQualifiedClassName()).append("` | `")
                    .append(method.signature().methodName()).append("` | `")
                    .append(params).append("` | `")
                    .append(method.filePath()).append("` | ")
                    .append(method.startLine()).append("-").append(method.endLine()).append(" | ")
                    .append(method.loc()).append(" | ")
                    .append(method.revisions()).append(" | ")
                    .append(fmt(method.simpleScore())).append(" | ")
                    .append(fmt(method.recencyDecay())).append(" | ")
                    .append(fmt(method.cognitiveComplexity())).append(" | ")
                    .append(fmt(method.coverageMultiplier())).append(" | ")
                    .append(fmt(method.compositeScore())).append(" |\n");
        }
        sb.append("\n");
    }

    private static void appendApiHotspots(StringBuilder sb, List<ApiHotspot> apis) {
        sb.append("## REST API Hotspots (").append(apis.size()).append(" rows)\n\n");
        sb.append("| Rank | HTTP Method | Route | FQCN | Method | Parameters | LOC | Revisions | Simple Score | Recency Decay | Cognitive Complexity | Coverage Multiplier | Composite Score | Call Graph |\n");
        sb.append("|---:|:---|:---|:---|:---|:---|---:|---:|---:|---:|---:|---:|---:|:---|\n");
        int rank = 1;
        for (ApiHotspot api : apis) {
            String params = String.join(", ", api.controllerMethod().parameterTypes());
            List<String> cgSigs = new ArrayList<>();
            for (var sig : api.callGraph()) {
                cgSigs.add("`" + sig.toCanonicalString() + "`");
            }
            sb.append("| ").append(rank++).append(" | ")
                    .append(api.httpMethod()).append(" | `")
                    .append(api.route()).append("` | `")
                    .append(api.controllerMethod().fullyQualifiedClassName()).append("` | `")
                    .append(api.controllerMethod().methodName()).append("` | `")
                    .append(params).append("` | ")
                    .append(api.loc()).append(" | ")
                    .append(api.revisions()).append(" | ")
                    .append(fmt(api.simpleScore())).append(" | ")
                    .append(fmt(api.recencyDecay())).append(" | ")
                    .append(fmt(api.cognitiveComplexity())).append(" | ")
                    .append(fmt(api.coverageMultiplier())).append(" | ")
                    .append(fmt(api.compositeScore())).append(" | ")
                    .append(String.join("<br>", cgSigs)).append(" |\n");
        }
        sb.append("\n");
    }

    private static void appendSharedComponents(StringBuilder sb, List<SharedComponentHotspot> components) {
        sb.append("## Shared Components (").append(components.size()).append(" rows)\n\n");
        sb.append("| Rank | FQCN | Method | Parameters | LOC | Revisions | Simple Score | Recency Decay | Cognitive Complexity | Coverage Multiplier | Composite Score | Calling APIs |\n");
        sb.append("|---:|:---|:---|:---|---:|---:|---:|---:|---:|---:|---:|:---|\n");
        int rank = 1;
        for (SharedComponentHotspot component : components) {
            String params = String.join(", ", component.method().parameterTypes());
            List<String> apis = new ArrayList<>();
            for (var api : component.callingApis()) {
                apis.add("`" + api + "`");
            }
            sb.append("| ").append(rank++).append(" | `")
                    .append(component.method().fullyQualifiedClassName()).append("` | `")
                    .append(component.method().methodName()).append("` | `")
                    .append(params).append("` | ")
                    .append(component.loc()).append(" | ")
                    .append(component.revisions()).append(" | ")
                    .append(fmt(component.simpleScore())).append(" | ")
                    .append(fmt(component.recencyDecay())).append(" | ")
                    .append(fmt(component.cognitiveComplexity())).append(" | ")
                    .append(fmt(component.coverageMultiplier())).append(" | ")
                    .append(fmt(component.compositeScore())).append(" | ")
                    .append(String.join("<br>", apis)).append(" |\n");
        }
        sb.append("\n");
    }

    /**
     * Formats a double: no decimals when the value is a whole number,
     * otherwise 4 decimal places with US locale (dot separator).
     */
    private static String fmt(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return Long.toString((long) v);
        }
        return String.format(Locale.ROOT, "%.4f", v);
    }
}
