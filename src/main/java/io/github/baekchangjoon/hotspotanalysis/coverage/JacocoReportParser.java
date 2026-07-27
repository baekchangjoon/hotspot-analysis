package io.github.baekchangjoon.hotspotanalysis.coverage;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses a JaCoCo XML coverage report and provides file/method-level coverage scores.
 */
public class JacocoReportParser {

    private final Map<String, Map<Integer, Boolean>> lineCoverageMap = new HashMap<>();

    public void parse(Path xmlPath) {
        if (xmlPath == null || !Files.exists(xmlPath)) {
            return;
        }
        try (InputStream is = Files.newInputStream(xmlPath)) {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            // The JDK's default handler sprays locale-dependent "[Fatal Error]"
            // lines onto stderr before our own warning; parse errors are
            // already surfaced via the catch below.
            dBuilder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            Document doc = dBuilder.parse(is);
            doc.getDocumentElement().normalize();

            NodeList packageNodes = doc.getElementsByTagName("package");
            for (int i = 0; i < packageNodes.getLength(); i++) {
                Element pkgElement = (Element) packageNodes.item(i);
                String pkgName = pkgElement.getAttribute("name");

                NodeList sourcefileNodes = pkgElement.getElementsByTagName("sourcefile");
                for (int j = 0; j < sourcefileNodes.getLength(); j++) {
                    Element srcElement = (Element) sourcefileNodes.item(j);
                    String srcName = srcElement.getAttribute("name");
                    String filePath = pkgName.isEmpty() ? srcName : pkgName + "/" + srcName;

                    Map<Integer, Boolean> fileLines = lineCoverageMap.computeIfAbsent(filePath, k -> new HashMap<>());

                    NodeList lineNodes = srcElement.getElementsByTagName("line");
                    for (int k = 0; k < lineNodes.getLength(); k++) {
                        Element lineElement = (Element) lineNodes.item(k);
                        int lineNr = Integer.parseInt(lineElement.getAttribute("nr"));
                        int coveredInstructions = Integer.parseInt(lineElement.getAttribute("ci"));

                        // Merged reports repeat <sourcefile> for the same file:
                        // a line executed in ANY entry is covered (OR), never
                        // last-wins (which would erase earlier coverage).
                        fileLines.merge(lineNr, coveredInstructions > 0, Boolean::logicalOr);
                    }
                }
            }
        } catch (Exception e) {
            // A malformed/corrupt report must not abort the whole run, but it also
            // must not pass silently: an empty coverage map inflates every coverage
            // multiplier to its 1/0.1 = 10 maximum. Warn and let callers fall back
            // via hasData() so the scores stay trustworthy.
            lineCoverageMap.clear();
            System.err.println("WARNING: failed to parse JaCoCo XML report at " + xmlPath
                    + " (" + e.getMessage() + ") — proceeding WITHOUT coverage.");
        }
    }

    /** True when parsing produced at least one file's line-coverage data. */
    public boolean hasData() {
        return !lineCoverageMap.isEmpty();
    }

    /**
     * True when at least one parsed line was actually executed. A structurally
     * valid report whose lines are all {@code ci="0"} (generated without test
     * execution data — stale .exec, tests skipped) passes {@link #hasData()}
     * but carries no coverage signal.
     */
    public boolean hasCoveredLines() {
        return lineCoverageMap.values().stream()
                .anyMatch(lines -> lines.values().stream().anyMatch(Boolean::booleanValue));
    }

    /**
     * True when the report carries line data for this file. Callers must not
     * treat an absent file as 0% covered — a partial report (e.g. one module
     * of a multi-module build) says nothing about files it does not mention.
     */
    public boolean hasDataForFile(String filePath) {
        return findCoverageForPath(normalizePath(filePath)) != null;
    }

    public LineCounts getFileLineCounts(String filePath) {
        String normalizedPath = normalizePath(filePath);
        Map<Integer, Boolean> lines = findCoverageForPath(normalizedPath);
        if (lines == null || lines.isEmpty()) {
            return new LineCounts(0, 0);
        }
        int covered = (int) lines.values().stream().filter(b -> b).count();
        return new LineCounts(covered, lines.size());
    }

    public double getFileCoverage(String filePath) {
        LineCounts c = getFileLineCounts(filePath);
        return c.executable() == 0 ? 0.0 : (double) c.covered() / c.executable();
    }

    /**
     * Covered and instrumented (executable) line counts within a method's line
     * range. {@code executable} is 0 when the file/method has no coverage data,
     * which lets callers line-weight aggregates without that method dragging the
     * result toward 0.
     */
    public record LineCounts(int covered, int executable) {}

    public LineCounts getMethodLineCounts(String filePath, int startLine, int endLine) {
        String normalizedPath = normalizePath(filePath);
        Map<Integer, Boolean> lines = findCoverageForPath(normalizedPath);
        if (lines == null || lines.isEmpty()) {
            return new LineCounts(0, 0);
        }
        int executable = 0;
        int covered = 0;
        for (int i = startLine; i <= endLine; i++) {
            Boolean isCovered = lines.get(i);
            if (isCovered != null) {
                executable++;
                if (isCovered) {
                    covered++;
                }
            }
        }
        return new LineCounts(covered, executable);
    }

    public double getMethodCoverage(String filePath, int startLine, int endLine) {
        LineCounts c = getMethodLineCounts(filePath, startLine, endLine);
        return c.executable() == 0 ? 0.0 : (double) c.covered() / c.executable();
    }

    private Map<Integer, Boolean> findCoverageForPath(String normalizedPath) {
        for (Map.Entry<String, Map<Integer, Boolean>> entry : lineCoverageMap.entrySet()) {
            if (normalizedPath.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return path.replace('\\', '/');
    }
}
