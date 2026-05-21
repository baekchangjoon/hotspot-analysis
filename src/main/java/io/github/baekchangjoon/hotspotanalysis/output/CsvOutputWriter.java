package io.github.baekchangjoon.hotspotanalysis.output;

import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.FileHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.MethodHotspot;
import io.github.baekchangjoon.hotspotanalysis.config.OutputConfig;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the analysis result as two CSV files: {@code file_hotspots.csv} and
 * {@code method_hotspots.csv}. RFC 4180-style escaping is applied (only when
 * needed) so the files can be opened as-is in Excel / Numbers / Google Sheets.
 */
@Component
public class CsvOutputWriter implements OutputWriter {

    @Override
    public OutputConfig.OutputFormat format() {
        return OutputConfig.OutputFormat.CSV;
    }

    @Override
    public void write(AnalysisResult result, Path outputDir) {
        try {
            Files.createDirectories(outputDir);
            writeFileHotspots(result, outputDir.resolve("file_hotspots.csv"));
            writeMethodHotspots(result, outputDir.resolve("method_hotspots.csv"));
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
}
