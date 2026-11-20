package io.github.baekchangjoon.hotspotanalysis.output;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisMeta;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.CoverageBreakdown;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.FileHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.MethodHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.ReportContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The interactive "Hotspot Map" tab of the HTML report.
 *
 * <p>The tab is a fixed template (markup, stylesheet and script under
 * {@code /report} on the classpath) plus one JSON document describing the
 * analysis. Nothing here depends on wall-clock time or randomness: the same
 * {@link AnalysisResult} always yields byte-identical output.</p>
 *
 * <p>The JSON block (element id {@value #DATA_ELEMENT_ID}, see
 * {@link #SCHEMA_VERSION}) is also the machine-readable form of the report —
 * a dashboard can read it back out of the published HTML instead of scraping
 * table cells.</p>
 */
final class HotspotMapSection {

    /** Bumped whenever a field is renamed, removed or changes meaning. */
    static final int SCHEMA_VERSION = 1;
    static final String DATA_ELEMENT_ID = "hotspot-map-data";
    /** Half-life assumed when a result carries no {@link ReportContext}; the scoring default. */
    private static final int DEFAULT_HALF_LIFE_DAYS = 90;

    /** U+2028 / U+2029: legal in JSON, but line terminators to older JavaScript parsers. */
    private static final String LINE_SEPARATOR = Character.toString(0x2028);
    private static final String PARAGRAPH_SEPARATOR = Character.toString(0x2029);

    private static final ObjectMapper JSON = new ObjectMapper();

    private HotspotMapSection() {
    }

    static String stylesheet() {
        return resource("hotspot-map.css");
    }

    static String markup() {
        return resource("hotspot-map.html");
    }

    /** The vendored layout library followed by the map script, each in its own script element. */
    static String scripts() {
        return "  <script>\n" + resource("d3-hierarchy.min.js") + "\n  </script>\n"
                + "  <script>\n" + resource("hotspot-map.js") + "  </script>\n";
    }

    static String dataElement(AnalysisResult result, boolean excludeCoverage) {
        return "    <script type=\"application/json\" id=\"" + DATA_ELEMENT_ID + "\">"
                + toJson(buildData(result, excludeCoverage)) + "</script>\n";
    }

    static Map<String, Object> buildData(AnalysisResult result, boolean excludeCoverage) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("schemaVersion", SCHEMA_VERSION);
        doc.put("meta", buildMeta(result, excludeCoverage));
        doc.put("files", buildFiles(result));
        doc.put("methods", buildMethods(result.methodHotspots()));
        return doc;
    }

    private static Map<String, Object> buildMeta(AnalysisResult result, boolean excludeCoverage) {
        AnalysisMeta meta = result.meta();
        ReportContext context = result.reportContext();
        double shownTotal = result.fileHotspots().stream().mapToDouble(FileHotspot::compositeScore).sum();
        long covered = 0;
        long executable = 0;
        if (result.coverageBreakdown() != null) {
            for (CoverageBreakdown.FileCoverage file : result.coverageBreakdown().files()) {
                covered += file.coveredLines();
                executable += file.executableLines();
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("label", label(meta.targetDescription()));
        out.put("analyzedAt", meta.analyzedAt().toString());
        out.put("commits", meta.totalCommits());
        // The lists may be cut by output.topN; never report fewer than what is shown.
        out.put("totalFiles", Math.max(meta.totalFiles(), result.fileHotspots().size()));
        out.put("totalMethods", Math.max(meta.totalMethods(), result.methodHotspots().size()));
        out.put("halfLifeDays", context != null ? context.decayHalfLifeDays() : DEFAULT_HALF_LIFE_DAYS);
        out.put("excludeCoverage", excludeCoverage);
        out.put("blobBase", context != null ? context.sourceBlobBase() : null);
        out.put("revision", context != null ? context.revision() : null);
        out.put("totalFileScore", round(context != null
                ? Math.max(context.totalFileCompositeScore(), shownTotal) : shownTotal));
        out.put("coveredLines", covered);
        out.put("executableLines", executable);
        return out;
    }

    /** The repository directory name — the absolute path stays out of the shareable data block. */
    private static String label(String targetDescription) {
        String value = targetDescription;
        int colon = value.indexOf(':');
        if (colon >= 0 && value.substring(0, colon).matches("[A-Z_]+")) {
            value = value.substring(colon + 1);
        }
        value = value.replace('\\', '/');
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        String last = value.substring(value.lastIndexOf('/') + 1);
        return last.isBlank() ? "repository" : last;
    }

    private static List<Map<String, Object>> buildFiles(AnalysisResult result) {
        Map<String, CoverageBreakdown.FileCoverage> counts = new HashMap<>();
        if (result.coverageBreakdown() != null) {
            for (CoverageBreakdown.FileCoverage file : result.coverageBreakdown().files()) {
                counts.put(file.path(), file);
            }
        }
        List<FileHotspot> ranked = new ArrayList<>(result.fileHotspots());
        ranked.sort(Comparator.comparingDouble(FileHotspot::compositeScore).reversed()
                .thenComparing(FileHotspot::path));
        List<Map<String, Object>> rows = new ArrayList<>(ranked.size());
        int rank = 1;
        for (FileHotspot f : ranked) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", f.path());
            row.put("rank", rank++);
            row.put("loc", f.loc());
            row.put("rev", f.revisions());
            row.put("decay", round(f.recencyDecay()));
            row.put("cc", wholeOrRounded(f.cognitiveComplexity()));
            row.put("covMul", round(f.coverageMultiplier()));
            row.put("cov", f.lineCoverage() == null ? null : round(f.lineCoverage()));
            CoverageBreakdown.FileCoverage count = counts.get(f.path());
            // Line counts only when they describe the same coverage value shown above.
            boolean usable = count != null && f.lineCoverage() != null && count.executableLines() > 0;
            row.put("covered", usable ? count.coveredLines() : null);
            row.put("exec", usable ? count.executableLines() : null);
            row.put("score", round(f.compositeScore()));
            rows.add(row);
        }
        return rows;
    }

    private static List<Map<String, Object>> buildMethods(List<MethodHotspot> methods) {
        List<MethodHotspot> ranked = new ArrayList<>(methods);
        ranked.sort(Comparator.comparingDouble(MethodHotspot::compositeScore).reversed()
                .thenComparing(m -> m.signature().toCanonicalString()));
        List<Map<String, Object>> rows = new ArrayList<>(ranked.size());
        int rank = 1;
        for (MethodHotspot m : ranked) {
            String fqcn = m.signature().fullyQualifiedClassName();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fqcn", fqcn);
            row.put("cls", fqcn.substring(fqcn.lastIndexOf('.') + 1));
            row.put("name", m.signature().methodName());
            row.put("params", m.signature().parameterTypes());
            row.put("file", m.filePath());
            row.put("start", m.startLine());
            row.put("end", m.endLine());
            row.put("rank", rank++);
            row.put("loc", m.loc());
            row.put("rev", m.revisions());
            row.put("decay", round(m.recencyDecay()));
            row.put("cc", wholeOrRounded(m.cognitiveComplexity()));
            row.put("covMul", round(m.coverageMultiplier()));
            row.put("cov", m.lineCoverage() == null ? null : round(m.lineCoverage()));
            row.put("score", round(m.compositeScore()));
            rows.add(row);
        }
        return rows;
    }

    /**
     * Serialises for embedding inside a {@code <script>} element: the
     * characters that could end the element or start markup are written as
     * JSON unicode escapes, so no value — however hostile a path or method
     * name — can break out of the data block.
     */
    static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value)
                    .replace("<", "\\u003c")
                    .replace(">", "\\u003e")
                    .replace("&", "\\u0026")
                    .replace(LINE_SEPARATOR, "\\u2028")
                    .replace(PARAGRAPH_SEPARATOR, "\\u2029");
        } catch (JsonProcessingException e) {
            throw new OutputException("Failed to serialise the hotspot map data", e);
        }
    }

    private static double round(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }

    /** Complexity is a count; keep it an integer in the JSON unless it genuinely is fractional. */
    private static Number wholeOrRounded(double value) {
        if (value == Math.rint(value) && Math.abs(value) < Integer.MAX_VALUE) {
            return (int) value;
        }
        return round(value);
    }

    private static String resource(String name) {
        try (InputStream in = HotspotMapSection.class.getResourceAsStream("/report/" + name)) {
            if (in == null) {
                throw new OutputException("Missing report resource /report/" + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new OutputException("Failed to read report resource /report/" + name, e);
        }
    }
}
