package io.github.baekchangjoon.hotspotanalysis.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.ApiHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.FileHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.MethodHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.SharedComponentHotspot;
import io.github.baekchangjoon.hotspotanalysis.config.OutputConfig;
import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Writes the analysis result as a single {@code hotspots.yml} or {@code api_report.yml} file
 * using a canonical flat key layout for each hotspot type.
 *
 * <p>Each row is built as a {@link LinkedHashMap} to guarantee canonical key order:
 * <ul>
 *   <li>File: rank, path, loc, revisions, simpleScore, recencyDecay, cognitiveComplexity,
 *       coverageMultiplier, compositeScore</li>
 *   <li>Method: rank, fqcn, method, parameters, file, startLine, endLine + 7-metric block</li>
 *   <li>API: rank, httpMethod, route, fqcn, method, parameters + 7-metric block, callGraph</li>
 *   <li>Shared: rank, fqcn, method, parameters + 7-metric block, callingApis</li>
 * </ul>
 * </p>
 */
@Component
public class YamlOutputWriter implements OutputWriter {

    private final ObjectMapper yamlMapper;

    public YamlOutputWriter() {
        YAMLFactory factory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        this.yamlMapper = new ObjectMapper(factory)
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public OutputConfig.OutputFormat format() {
        return OutputConfig.OutputFormat.YAML;
    }

    @Override
    public void write(AnalysisResult result, Path outputDir) {
        write(result, outputDir, new OutputConfig(List.of(OutputConfig.OutputFormat.YAML), outputDir.toString(), 0), false);
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
                yamlMapper.writerWithDefaultPrettyPrinter()
                        .writeValue(outputDir.resolve("hotspots.yml").toFile(), buildCombined(result));
                return;
            }

            if (combined) {
                yamlMapper.writerWithDefaultPrettyPrinter()
                        .writeValue(outputDir.resolve("hotspots.yml").toFile(), buildCombined(result));
            }

            if (standalone) {
                yamlMapper.writerWithDefaultPrettyPrinter()
                        .writeValue(outputDir.resolve("api_report.yml").toFile(), buildApiReport(result));
            }
        } catch (IOException e) {
            throw new OutputException("Failed to write YAML output to " + outputDir, e);
        }
    }

    // -------------------------------------------------------------------------
    // Document builders
    // -------------------------------------------------------------------------

    private Map<String, Object> buildCombined(AnalysisResult result) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("meta", result.meta());
        doc.put("fileHotspots", buildFileRows(result.fileHotspots()));
        doc.put("methodHotspots", buildMethodRows(result.methodHotspots()));
        if (!result.apiHotspots().isEmpty()) {
            doc.put("apiHotspots", buildApiRows(result.apiHotspots()));
        }
        if (!result.sharedComponents().isEmpty()) {
            doc.put("sharedComponents", buildSharedRows(result.sharedComponents()));
        }
        return doc;
    }

    private Map<String, Object> buildApiReport(AnalysisResult result) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("meta", result.meta());
        doc.put("apiHotspots", buildApiRows(result.apiHotspots()));
        doc.put("sharedComponents", buildSharedRows(result.sharedComponents()));
        return doc;
    }

    // -------------------------------------------------------------------------
    // Row builders — one per hotspot type
    // -------------------------------------------------------------------------

    private List<Map<String, Object>> buildFileRows(List<FileHotspot> hotspots) {
        List<Map<String, Object>> rows = new ArrayList<>(hotspots.size());
        int rank = 1;
        for (FileHotspot f : hotspots) {
            rows.add(fileRow(rank++, f));
        }
        return rows;
    }

    private List<Map<String, Object>> buildMethodRows(List<MethodHotspot> hotspots) {
        List<Map<String, Object>> rows = new ArrayList<>(hotspots.size());
        int rank = 1;
        for (MethodHotspot m : hotspots) {
            rows.add(methodRow(rank++, m));
        }
        return rows;
    }

    private List<Map<String, Object>> buildApiRows(List<ApiHotspot> hotspots) {
        List<Map<String, Object>> rows = new ArrayList<>(hotspots.size());
        int rank = 1;
        for (ApiHotspot a : hotspots) {
            rows.add(apiRow(rank++, a));
        }
        return rows;
    }

    private List<Map<String, Object>> buildSharedRows(List<SharedComponentHotspot> hotspots) {
        List<Map<String, Object>> rows = new ArrayList<>(hotspots.size());
        int rank = 1;
        for (SharedComponentHotspot s : hotspots) {
            rows.add(sharedRow(rank++, s));
        }
        return rows;
    }

    // -------------------------------------------------------------------------
    // Individual row factories — canonical key order guaranteed by LinkedHashMap
    // -------------------------------------------------------------------------

    private static Map<String, Object> fileRow(int rank, FileHotspot f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rank", rank);
        m.put("path", f.path());
        m.put("loc", f.loc());
        m.put("revisions", f.revisions());
        m.put("simpleScore", asYamlNumber(f.simpleScore()));
        m.put("recencyDecay", asYamlNumber(f.recencyDecay()));
        m.put("cognitiveComplexity", asYamlNumber(f.cognitiveComplexity()));
        m.put("coverageMultiplier", asYamlNumber(f.coverageMultiplier()));
        m.put("compositeScore", asYamlNumber(f.compositeScore()));
        return m;
    }

    private static Map<String, Object> methodRow(int rank, MethodHotspot mh) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rank", rank);
        m.put("fqcn", mh.signature().fullyQualifiedClassName());
        m.put("method", mh.signature().methodName());
        m.put("parameters", mh.signature().parameterTypes());
        m.put("file", mh.filePath());
        m.put("startLine", mh.startLine());
        m.put("endLine", mh.endLine());
        putMetricBlock(m, mh.loc(), mh.revisions(),
                mh.simpleScore(), mh.recencyDecay(),
                mh.cognitiveComplexity(), mh.coverageMultiplier(), mh.compositeScore());
        return m;
    }

    private static Map<String, Object> apiRow(int rank, ApiHotspot a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rank", rank);
        m.put("httpMethod", a.httpMethod());
        m.put("route", a.route());
        m.put("fqcn", a.controllerMethod().fullyQualifiedClassName());
        m.put("method", a.controllerMethod().methodName());
        m.put("parameters", a.controllerMethod().parameterTypes());
        putMetricBlock(m, a.loc(), a.revisions(),
                a.simpleScore(), a.recencyDecay(),
                a.cognitiveComplexity(), a.coverageMultiplier(), a.compositeScore());
        m.put("callGraph", signaturesAsList(a.callGraph()));
        return m;
    }

    private static Map<String, Object> sharedRow(int rank, SharedComponentHotspot s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rank", rank);
        m.put("fqcn", s.method().fullyQualifiedClassName());
        m.put("method", s.method().methodName());
        m.put("parameters", s.method().parameterTypes());
        putMetricBlock(m, s.loc(), s.revisions(),
                s.simpleScore(), s.recencyDecay(),
                s.cognitiveComplexity(), s.coverageMultiplier(), s.compositeScore());
        m.put("callingApis", s.callingApis());
        return m;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Appends the canonical 7-metric block to an existing map in order. */
    private static void putMetricBlock(Map<String, Object> m,
                                       int loc, int revisions,
                                       double simpleScore, double recencyDecay,
                                       double cognitiveComplexity, double coverageMultiplier,
                                       double compositeScore) {
        m.put("loc", loc);
        m.put("revisions", revisions);
        m.put("simpleScore", asYamlNumber(simpleScore));
        m.put("recencyDecay", asYamlNumber(recencyDecay));
        m.put("cognitiveComplexity", asYamlNumber(cognitiveComplexity));
        m.put("coverageMultiplier", asYamlNumber(coverageMultiplier));
        m.put("compositeScore", asYamlNumber(compositeScore));
    }

    /** Converts a list of {@link MethodSignature} to canonical strings for YAML sequences. */
    private static List<String> signaturesAsList(List<MethodSignature> sigs) {
        return sigs.stream()
                .map(MethodSignature::toCanonicalString)
                .collect(Collectors.toList());
    }

    private static double round4(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }

    /**
     * Returns a {@code long} when the value is a whole number so that the YAML
     * serialiser emits {@code 600} instead of {@code 600.0}, matching the
     * integer rendering used by the CSV/Markdown/HTML writers.
     */
    private static Object asYamlNumber(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return (long) v;
        }
        return round4(v);
    }
}
