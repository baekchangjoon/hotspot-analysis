package io.github.baekchangjoon.hotspotanalysis.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.CoverageBreakdown;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes {@code coverage_breakdown.yml}: the calculation trace behind every
 * coverage number in the reports. For each file the covered/executable line
 * counts, and for each API endpoint the line-weighted aggregate plus the
 * per-method contributions (which file/method added how many covered and
 * executable lines, including no-data and SEPARATE-excluded methods).
 */
public class CoverageBreakdownWriter {

    private final ObjectMapper yamlMapper;

    public CoverageBreakdownWriter() {
        YAMLFactory factory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        this.yamlMapper = new ObjectMapper(factory);
    }

    public void write(CoverageBreakdown breakdown, Path outputDir) {
        try {
            Files.createDirectories(outputDir);
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("jacocoReport", breakdown.jacocoReportPath());
            doc.put("files", fileRows(breakdown.files()));
            if (!breakdown.apis().isEmpty()) {
                doc.put("apiHotspots", apiRows(breakdown.apis()));
            }
            yamlMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(outputDir.resolve("coverage_breakdown.yml").toFile(), doc);
        } catch (IOException e) {
            throw new OutputException("Failed to write coverage_breakdown.yml", e);
        }
    }

    private static List<Map<String, Object>> fileRows(List<CoverageBreakdown.FileCoverage> files) {
        List<Map<String, Object>> rows = new ArrayList<>(files.size());
        for (CoverageBreakdown.FileCoverage f : files) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", f.path());
            row.put("coveredLines", f.coveredLines());
            row.put("executableLines", f.executableLines());
            row.put("lineCoverage", coverageValue(f.lineCoverage()));
            rows.add(row);
        }
        return rows;
    }

    private static List<Map<String, Object>> apiRows(List<CoverageBreakdown.ApiCoverage> apis) {
        List<Map<String, Object>> rows = new ArrayList<>(apis.size());
        for (CoverageBreakdown.ApiCoverage a : apis) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("httpMethod", a.httpMethod());
            row.put("route", a.route());
            row.put("coveredLines", a.coveredLines());
            row.put("executableLines", a.executableLines());
            row.put("lineCoverage", coverageValue(a.lineCoverage()));
            row.put("coverageMultiplier", coverageValue(a.coverageMultiplier()));
            row.put("methods", methodRows(a.methods()));
            rows.add(row);
        }
        return rows;
    }

    private static List<Map<String, Object>> methodRows(List<CoverageBreakdown.MethodContribution> methods) {
        List<Map<String, Object>> rows = new ArrayList<>(methods.size());
        for (CoverageBreakdown.MethodContribution m : methods) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("signature", m.signature());
            if (m.file() != null) {
                row.put("file", m.file());
            }
            if (m.startLine() != null && m.endLine() != null) {
                row.put("lines", m.startLine() + "-" + m.endLine());
            }
            row.put("coveredLines", m.coveredLines());
            row.put("executableLines", m.executableLines());
            row.put("coverage", coverageValue(m.coverage()));
            if (m.note() != null) {
                row.put("note", m.note());
            }
            rows.add(row);
        }
        return rows;
    }

    /** Coverage/multiplier rounded to 4 decimals; "N/A" when there is no data. */
    private static Object coverageValue(Double v) {
        if (v == null) {
            return "N/A";
        }
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
