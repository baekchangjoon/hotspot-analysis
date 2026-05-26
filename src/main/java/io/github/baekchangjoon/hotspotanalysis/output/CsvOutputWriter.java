package io.github.baekchangjoon.hotspotanalysis.output;

import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.FileHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.MethodHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.ApiHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.SharedComponentHotspot;
import io.github.baekchangjoon.hotspotanalysis.config.OutputConfig;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes the analysis result as CSV files: {@code file_hotspots.csv},
 * {@code method_hotspots.csv}, and optionally API analysis files.
 *
 * <p>Column order follows the canonical 7-metric block:
 * {@code loc, revisions, simple_score, recency_decay, cognitive_complexity,
 * coverage_multiplier, composite_score}.</p>
 */
@Component
public class CsvOutputWriter implements OutputWriter {

    @Override
    public OutputConfig.OutputFormat format() {
        return OutputConfig.OutputFormat.CSV;
    }

    @Override
    public void write(AnalysisResult result, Path outputDir) {
        write(result, outputDir, new OutputConfig(List.of(OutputConfig.OutputFormat.CSV), outputDir.toString(), 0), false);
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
                writeFileHotspots(result, outputDir.resolve("file_hotspots.csv"));
                writeMethodHotspots(result, outputDir.resolve("method_hotspots.csv"));
                return;
            }

            if (combined) {
                writeFileHotspots(result, outputDir.resolve("file_hotspots.csv"));
                writeMethodHotspots(result, outputDir.resolve("method_hotspots.csv"));
            }

            if (standalone || combined) {
                writeApiHotspots(result, outputDir.resolve("api_hotspots.csv"));
                writeSharedComponents(result, outputDir.resolve("shared_components.csv"));
            }
        } catch (IOException e) {
            throw new OutputException("Failed to write CSV outputs to " + outputDir, e);
        }
    }

    private void writeFileHotspots(AnalysisResult result, Path target) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write("rank,path,loc,revisions,simple_score,recency_decay,"
                    + "cognitive_complexity,coverage_multiplier,composite_score\n");
            int rank = 1;
            for (FileHotspot f : result.fileHotspots()) {
                writer.write(rank++ + ","
                        + escape(f.path()) + ","
                        + f.loc() + ","
                        + f.revisions() + ","
                        + fmt(f.simpleScore()) + ","
                        + fmt(f.recencyDecay()) + ","
                        + fmt(f.cognitiveComplexity()) + ","
                        + fmt(f.coverageMultiplier()) + ","
                        + fmt(f.compositeScore()) + "\n");
            }
        }
    }

    private void writeMethodHotspots(AnalysisResult result, Path target) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write("rank,fqcn,method,parameters,file,start_line,end_line,"
                    + "loc,revisions,simple_score,recency_decay,"
                    + "cognitive_complexity,coverage_multiplier,composite_score\n");
            int rank = 1;
            for (MethodHotspot m : result.methodHotspots()) {
                writer.write(rank++ + ","
                        + escape(m.signature().fullyQualifiedClassName()) + ","
                        + escape(m.signature().methodName()) + ","
                        + escape(String.join(";", m.signature().parameterTypes())) + ","
                        + escape(m.filePath()) + ","
                        + m.startLine() + ","
                        + m.endLine() + ","
                        + m.loc() + ","
                        + m.revisions() + ","
                        + fmt(m.simpleScore()) + ","
                        + fmt(m.recencyDecay()) + ","
                        + fmt(m.cognitiveComplexity()) + ","
                        + fmt(m.coverageMultiplier()) + ","
                        + fmt(m.compositeScore()) + "\n");
            }
        }
    }

    private void writeApiHotspots(AnalysisResult result, Path target) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write("rank,http_method,route,fqcn,method,parameters,"
                    + "loc,revisions,simple_score,recency_decay,"
                    + "cognitive_complexity,coverage_multiplier,composite_score\n");
            int rank = 1;
            for (ApiHotspot api : result.apiHotspots()) {
                writer.write(rank++ + ","
                        + escape(api.httpMethod()) + ","
                        + escape(api.route()) + ","
                        + escape(api.controllerMethod().fullyQualifiedClassName()) + ","
                        + escape(api.controllerMethod().methodName()) + ","
                        + escape(String.join(";", api.controllerMethod().parameterTypes())) + ","
                        + api.loc() + ","
                        + api.revisions() + ","
                        + fmt(api.simpleScore()) + ","
                        + fmt(api.recencyDecay()) + ","
                        + fmt(api.cognitiveComplexity()) + ","
                        + fmt(api.coverageMultiplier()) + ","
                        + fmt(api.compositeScore()) + "\n");
            }
        }
    }

    private void writeSharedComponents(AnalysisResult result, Path target) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write("rank,fqcn,method,parameters,"
                    + "loc,revisions,simple_score,recency_decay,"
                    + "cognitive_complexity,coverage_multiplier,composite_score,"
                    + "calling_apis\n");
            int rank = 1;
            for (SharedComponentHotspot component : result.sharedComponents()) {
                writer.write(rank++ + ","
                        + escape(component.method().fullyQualifiedClassName()) + ","
                        + escape(component.method().methodName()) + ","
                        + escape(String.join(";", component.method().parameterTypes())) + ","
                        + component.loc() + ","
                        + component.revisions() + ","
                        + fmt(component.simpleScore()) + ","
                        + fmt(component.recencyDecay()) + ","
                        + fmt(component.cognitiveComplexity()) + ","
                        + fmt(component.coverageMultiplier()) + ","
                        + fmt(component.compositeScore()) + ","
                        + escape(String.join(";", component.callingApis())) + "\n");
            }
        }
    }

    private static String escape(String raw) {
        if (raw.indexOf(',') < 0 && raw.indexOf('"') < 0 && raw.indexOf('\n') < 0) {
            return raw;
        }
        return "\"" + raw.replace("\"", "\"\"") + "\"";
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
