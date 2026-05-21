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
import java.util.List;

/**
 * Writes the analysis result as CSV files: {@code file_hotspots.csv},
 * {@code method_hotspots.csv}, and optionally API analysis files.
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
            writer.write("rank,path,revisions,loc,score\n");
            int rank = 1;
            for (FileHotspot file : result.fileHotspots()) {
                writer.write(rank++ + ","
                        + escape(file.path()) + ","
                        + file.revisions() + ","
                        + file.loc() + ","
                        + formatScore(file.score()) + "\n");
            }
        }
    }

    private void writeMethodHotspots(AnalysisResult result, Path target) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write("rank,fqcn,method,parameters,file,start_line,end_line,"
                    + "revisions,loc,score\n");
            int rank = 1;
            for (MethodHotspot method : result.methodHotspots()) {
                writer.write(rank++ + ","
                        + escape(method.signature().fullyQualifiedClassName()) + ","
                        + escape(method.signature().methodName()) + ","
                        + escape(String.join(";", method.signature().parameterTypes())) + ","
                        + escape(method.filePath()) + ","
                        + method.startLine() + ","
                        + method.endLine() + ","
                        + method.revisions() + ","
                        + method.loc() + ","
                        + formatScore(method.score()) + "\n");
            }
        }
    }

    private static String escape(String raw) {
        if (raw.indexOf(',') < 0 && raw.indexOf('"') < 0 && raw.indexOf('\n') < 0) {
            return raw;
        }
        return "\"" + raw.replace("\"", "\"\"") + "\"";
    }

    private static String formatScore(double score) {
        if (score == Math.floor(score) && !Double.isInfinite(score)) {
            return String.valueOf((long) score);
        }
        return String.format("%.4f", score);
    }

    private void writeApiHotspots(AnalysisResult result, Path target) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write("rank,http_method,route,controller_method,revisions,loc,score,call_graph\n");
            int rank = 1;
            for (ApiHotspot api : result.apiHotspots()) {
                List<String> cgSigs = new java.util.ArrayList<>();
                for (var sig : api.callGraph()) {
                    cgSigs.add(sig.toCanonicalString());
                }
                writer.write(rank++ + ","
                        + escape(api.httpMethod()) + ","
                        + escape(api.route()) + ","
                        + escape(api.controllerMethod().toCanonicalString()) + ","
                        + api.revisions() + ","
                        + api.loc() + ","
                        + formatScore(api.score()) + ","
                        + escape(String.join(";", cgSigs)) + "\n");
            }
        }
    }

    private void writeSharedComponents(AnalysisResult result, Path target) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write("rank,method,revisions,loc,score,calling_apis\n");
            int rank = 1;
            for (SharedComponentHotspot component : result.sharedComponents()) {
                writer.write(rank++ + ","
                        + escape(component.method().toCanonicalString()) + ","
                        + component.revisions() + ","
                        + component.loc() + ","
                        + formatScore(component.score()) + ","
                        + escape(String.join(";", component.callingApis())) + "\n");
            }
        }
    }
}
