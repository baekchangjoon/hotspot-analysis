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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes the analysis result as CSV files. Every row now carries two ranks
 * side-by-side: {@code simple_rank} (1-based position when sorted by Simple
 * Score DESC) and {@code composite_rank} (1-based position when sorted by
 * Composite Score DESC, which matches the order of rows in the output —
 * Composite Rank is the initial display sort).
 */
@Component
public class CsvOutputWriter implements OutputWriter {

    @Override
    public OutputConfig.OutputFormat format() {
        return OutputConfig.OutputFormat.CSV;
    }

    @Override
    public void write(AnalysisResult result, Path outputDir) {
        write(result, outputDir, new OutputConfig(List.of(OutputConfig.OutputFormat.CSV), outputDir.toString(), 0), false, false);
    }

    @Override
    public void write(AnalysisResult result, Path outputDir, OutputConfig outputConfig, boolean apiEnabled) {
        write(result, outputDir, outputConfig, apiEnabled, false);
    }

    @Override
    public void write(AnalysisResult result, Path outputDir, OutputConfig outputConfig,
                      boolean apiEnabled, boolean excludeCoverage) {
        try {
            Files.createDirectories(outputDir);

            boolean combined = outputConfig.apiLayout() == OutputConfig.ApiLayout.COMBINED ||
                               outputConfig.apiLayout() == OutputConfig.ApiLayout.BOTH;
            boolean standalone = outputConfig.apiLayout() == OutputConfig.ApiLayout.STANDALONE ||
                                 outputConfig.apiLayout() == OutputConfig.ApiLayout.BOTH;

            if (!apiEnabled) {
                writeFileHotspots(result, outputDir.resolve("file_hotspots.csv"), excludeCoverage);
                writeMethodHotspots(result, outputDir.resolve("method_hotspots.csv"), excludeCoverage);
                return;
            }

            if (combined) {
                writeFileHotspots(result, outputDir.resolve("file_hotspots.csv"), excludeCoverage);
                writeMethodHotspots(result, outputDir.resolve("method_hotspots.csv"), excludeCoverage);
            }

            if (standalone || combined) {
                writeApiHotspots(result, outputDir.resolve("api_hotspots.csv"), excludeCoverage);
                writeSharedComponents(result, outputDir.resolve("shared_components.csv"), excludeCoverage);
            }
        } catch (IOException e) {
            throw new OutputException("Failed to write CSV outputs to " + outputDir, e);
        }
    }

    private void writeFileHotspots(AnalysisResult result, Path target, boolean excludeCoverage) throws IOException {
        List<FileHotspot> files = result.fileHotspots();
        Map<FileHotspot, Integer> simpleRank = Rankings.rank(files,
                Comparator.comparingDouble(FileHotspot::simpleScore).reversed()
                        .thenComparing(FileHotspot::path));
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write("simple_rank,composite_rank,path,loc,revisions,simple_score,recency_decay,"
                    + "cognitive_complexity,"
                    + (excludeCoverage ? "composite_score,line_coverage" : "coverage_multiplier,composite_score")
                    + "\n");
            int compositeRank = 1;
            for (FileHotspot f : files) {
                writer.write(simpleRank.get(f) + ","
                        + compositeRank++ + ","
                        + escape(f.path()) + ","
                        + f.loc() + ","
                        + f.revisions() + ","
                        + fmt(f.simpleScore()) + ","
                        + fmt(f.recencyDecay()) + ","
                        + fmt(f.cognitiveComplexity()) + ","
                        + (excludeCoverage
                                ? fmt(f.compositeScore()) + "," + fmtCoverage(f.lineCoverage())
                                : fmt(f.coverageMultiplier()) + "," + fmt(f.compositeScore()))
                        + "\n");
            }
        }
    }

    private void writeMethodHotspots(AnalysisResult result, Path target, boolean excludeCoverage) throws IOException {
        List<MethodHotspot> methods = result.methodHotspots();
        Map<MethodHotspot, Integer> simpleRank = Rankings.rank(methods,
                Comparator.comparingDouble(MethodHotspot::simpleScore).reversed()
                        .thenComparing(h -> h.signature().toCanonicalString()));
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write("simple_rank,composite_rank,fqcn,method,parameters,file,start_line,end_line,"
                    + "loc,revisions,simple_score,recency_decay,"
                    + "cognitive_complexity,"
                    + (excludeCoverage ? "composite_score,line_coverage" : "coverage_multiplier,composite_score")
                    + "\n");
            int compositeRank = 1;
            for (MethodHotspot m : methods) {
                writer.write(simpleRank.get(m) + ","
                        + compositeRank++ + ","
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
                        + (excludeCoverage
                                ? fmt(m.compositeScore()) + "," + fmtCoverage(m.lineCoverage())
                                : fmt(m.coverageMultiplier()) + "," + fmt(m.compositeScore()))
                        + "\n");
            }
        }
    }

    private void writeApiHotspots(AnalysisResult result, Path target, boolean excludeCoverage) throws IOException {
        List<ApiHotspot> apis = result.apiHotspots();
        Map<ApiHotspot, Integer> simpleRank = Rankings.rank(apis,
                Comparator.comparingDouble(ApiHotspot::simpleScore).reversed()
                        .thenComparing(ApiHotspot::route)
                        .thenComparing(ApiHotspot::httpMethod));
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write("simple_rank,composite_rank,http_method,route,fqcn,method,parameters,"
                    + "loc,revisions,simple_score,recency_decay,"
                    + "cognitive_complexity,"
                    + (excludeCoverage ? "composite_score,line_coverage" : "coverage_multiplier,composite_score")
                    + "\n");
            int compositeRank = 1;
            for (ApiHotspot api : apis) {
                writer.write(simpleRank.get(api) + ","
                        + compositeRank++ + ","
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
                        + (excludeCoverage
                                ? fmt(api.compositeScore()) + "," + fmtCoverage(api.lineCoverage())
                                : fmt(api.coverageMultiplier()) + "," + fmt(api.compositeScore()))
                        + "\n");
            }
        }
    }

    private void writeSharedComponents(AnalysisResult result, Path target, boolean excludeCoverage) throws IOException {
        List<SharedComponentHotspot> components = result.sharedComponents();
        Map<SharedComponentHotspot, Integer> simpleRank = Rankings.rank(components,
                Comparator.comparingDouble(SharedComponentHotspot::simpleScore).reversed()
                        .thenComparing(c -> c.method().toCanonicalString()));
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write("simple_rank,composite_rank,fqcn,method,parameters,"
                    + "loc,revisions,simple_score,recency_decay,"
                    + "cognitive_complexity,"
                    + (excludeCoverage
                            ? "composite_score,calling_apis,line_coverage"
                            : "coverage_multiplier,composite_score,calling_apis")
                    + "\n");
            int compositeRank = 1;
            for (SharedComponentHotspot component : components) {
                writer.write(simpleRank.get(component) + ","
                        + compositeRank++ + ","
                        + escape(component.method().fullyQualifiedClassName()) + ","
                        + escape(component.method().methodName()) + ","
                        + escape(String.join(";", component.method().parameterTypes())) + ","
                        + component.loc() + ","
                        + component.revisions() + ","
                        + fmt(component.simpleScore()) + ","
                        + fmt(component.recencyDecay()) + ","
                        + fmt(component.cognitiveComplexity()) + ","
                        + (excludeCoverage
                                ? fmt(component.compositeScore()) + ","
                                        + escape(String.join(";", component.callingApis())) + ","
                                        + fmtCoverage(component.lineCoverage())
                                : fmt(component.coverageMultiplier()) + ","
                                        + fmt(component.compositeScore()) + ","
                                        + escape(String.join(";", component.callingApis())))
                        + "\n");
            }
        }
    }

    private static String escape(String raw) {
        if (raw.indexOf(',') < 0 && raw.indexOf('"') < 0 && raw.indexOf('\n') < 0) {
            return raw;
        }
        return "\"" + raw.replace("\"", "\"\"") + "\"";
    }

    private static String fmt(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return Long.toString((long) v);
        }
        return String.format(Locale.ROOT, "%.4f", v);
    }

    private static String fmtCoverage(Double lineCoverage) {
        if (lineCoverage == null) {
            return "N/A";
        }
        return String.format(Locale.ROOT, "%.1f%%", lineCoverage * 100.0);
    }
}
