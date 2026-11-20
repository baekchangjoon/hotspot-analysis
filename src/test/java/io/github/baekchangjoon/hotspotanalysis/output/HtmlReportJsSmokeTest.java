package io.github.baekchangjoon.hotspotanalysis.output;

import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisMeta;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.FileHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.MethodHotspot;
import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlTable;
import org.htmlunit.html.HtmlTableRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes the report's embedded JavaScript for real. The other tests only
 * string-match the markup, which let a fully broken column sort ship — the
 * global-header-index bug only surfaces when a click handler actually runs
 * (evaluation finding: sorting was dead in the default report).
 */
class HtmlReportJsSmokeTest {

    @Test
    @DisplayName("clicking column headers reorders rows in BOTH tables, and x-ray rows follow their parent")
    void sortingWorksInBothTables(@TempDir Path tempDir) throws Exception {
        Path htmlFile = writeReport(tempDir);

        try (WebClient client = newClient()) {
            HtmlPage page = client.getPage(htmlFile.toUri().toURL());

            HtmlTable fileTable = page.querySelector("#file-hotspots");
            int fileLocIdx = headerIndex(fileTable, "LOC");
            header(fileTable, "LOC").click();
            assertThat(dataCellTexts(fileTable, fileLocIdx))
                    .as("file table sorted by LOC ascending")
                    .containsExactly("10", "20", "30");
            header(fileTable, "LOC").click();
            assertThat(dataCellTexts(fileTable, fileLocIdx))
                    .as("file table sorted by LOC descending")
                    .containsExactly("30", "20", "10");

            // Each x-ray drilldown must follow ITS OWN parent (the parent's
            // onclick names the x-ray row id) and stay collapsed after sorting.
            List<HtmlTableRow> rows = fileTable.getBodies().get(0).getRows();
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).getAttribute("class").contains("xray-row")) {
                    assertThat(i).isGreaterThan(0);
                    assertThat(rows.get(i - 1).getAttribute("onclick"))
                            .contains("'" + rows.get(i).getId() + "'");
                    assertThat(rows.get(i).getAttribute("style").replace(";", ""))
                            .contains("display: none");
                }
            }

            // The SECOND sortable table was completely dead (the old script
            // used the document-global header index as a per-table column).
            HtmlTable methodTable = page.querySelector("#method-hotspots");
            int methodLocIdx = headerIndex(methodTable, "LOC");
            header(methodTable, "LOC").click();
            assertThat(dataCellTexts(methodTable, methodLocIdx))
                    .as("method table sorted by LOC ascending")
                    .containsExactly("5", "15", "25");
        }
    }

    @Test
    @DisplayName("the filter box counts only data rows and clearing it does not expand x-ray rows")
    void filterCountsOnlyDataRowsAndKeepsXrayCollapsed(@TempDir Path tempDir) throws Exception {
        Path htmlFile = writeReport(tempDir);

        try (WebClient client = newClient()) {
            HtmlPage page = client.getPage(htmlFile.toUri().toURL());
            HtmlInput input = page.querySelector("input[data-filter-target=\"#file-hotspots\"]");

            input.type("Beta");
            HtmlElement count = page.querySelector("[data-count-target=\"#file-hotspots\"]");
            // 3 data rows total — x-ray rows are not part of the denominator.
            assertThat(count.getTextContent().trim()).isEqualTo("1 / 3");

            for (int i = 0; i < 4; i++) {
                input.type('\b');
            }
            assertThat(count.getTextContent().trim()).isEqualTo("3 / 3");
            // Clearing the filter must not fling every x-ray drilldown open
            // (CSS is disabled in this client, so assert the inline style).
            HtmlTable fileTable = page.querySelector("#file-hotspots");
            for (HtmlTableRow row : fileTable.getBodies().get(0).getRows()) {
                if (row.getAttribute("class").contains("xray-row")) {
                    assertThat(row.getAttribute("style").replace(";", ""))
                            .contains("display: none");
                }
            }
        }
    }

    @Test
    @DisplayName("the hotspot map draws one circle per package, file, class and method, and drills down on click")
    void mapDrawsAndDrillsDown(@TempDir Path tempDir) throws Exception {
        Path htmlFile = writeReport(tempDir);

        try (WebClient client = newClient()) {
            HtmlPage page = client.getPage(htmlFile.toUri().toURL());
            HtmlElement map = page.querySelector("#hm");

            // The script wraps its boot in try/catch, so a silent failure would
            // leave this marker unset: root + 3 files + 1 class + 1 method each (the
            // shared "src/" prefix is dropped, so there is no package circle).
            assertThat(map.getAttribute("data-hm-ready")).isEqualTo("10");
            assertThat(page.querySelectorAll("#hm-map circle")).hasSize(10);
            assertThat(page.querySelectorAll(".hm-error")).isEmpty();

            HtmlElement side = page.querySelector("#hm-side");
            // Composite scores 90 / 40 / 10 of a 140 total: all three files are the top 3.
            assertThat(side.getTextContent()).contains("100%");
            List<HtmlElement> rows = side.getByXPath(".//ol[contains(@class,'hm-list')]//button");
            assertThat(rows).hasSize(3);
            assertThat(rows.get(0).getTextContent()).contains("Alpha.java").contains("90.0");

            // File > (single class, so straight into it) > method.
            rows.get(0).click();
            assertThat(side.getTextContent()).contains("Alpha.java").contains("src/Alpha.java");
            assertThat(page.querySelector("#hm-crumbs").getTextContent()).contains("Alpha.java").contains("C");
            List<HtmlElement> methodRows = side.getByXPath(".//ol[contains(@class,'hm-list')]//button");
            assertThat(methodRows).hasSize(1);
            methodRows.get(0).click();
            assertThat(side.getTextContent()).contains("a()").contains("L1");
            // No remote was known for this result, so no source link is offered.
            assertThat(side.getByXPath(".//a")).isEmpty();
        }
    }

    @Test
    @DisplayName("with a known remote, files and methods link to the exact revision and line range")
    void mapLinksToSource(@TempDir Path tempDir) throws Exception {
        new HtmlOutputWriter().write(HotspotMapSectionTest.sample(), tempDir);

        try (WebClient client = newClient()) {
            HtmlPage page = client.getPage(tempDir.resolve("hotspots.html").toUri().toURL());
            HtmlElement side = page.querySelector("#hm-side");
            // 2 of 9 files are shown; the headline uses the repository-wide total (125 + 1 of 250).
            assertThat(side.getTextContent()).contains("50%");

            List<HtmlElement> rows = side.getByXPath(".//ol[contains(@class,'hm-list')]//button");
            rows.get(0).click();
            HtmlElement fileLink = side.getFirstByXPath(".//a");
            assertThat(fileLink.getAttribute("href"))
                    .isEqualTo("https://github.com/acme/app/blob/abc1234/src/main/java/com/example/Hot.java");
            assertThat(fileLink.getAttribute("rel")).contains("noopener");
            // The coverage box explains itself from the real line counts.
            assertThat(side.getTextContent()).contains("70").contains("100").contains("70.0%");

            List<HtmlElement> methodRows = side.getByXPath(".//ol[contains(@class,'hm-list')]//button");
            methodRows.get(0).click();
            HtmlElement methodLink = side.getFirstByXPath(".//a");
            assertThat(methodLink.getAttribute("href"))
                    .isEqualTo("https://github.com/acme/app/blob/abc1234/src/main/java/com/example/Hot.java#L10-L20");
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static WebClient newClient() {
        WebClient client = new WebClient();
        client.getOptions().setJavaScriptEnabled(true);
        client.getOptions().setCssEnabled(false);
        client.getOptions().setThrowExceptionOnScriptError(true); // any JS error fails the test
        return client;
    }

    private static HtmlElement header(HtmlTable table, String label) {
        for (HtmlElement th : table.getElementsByTagName("th")) {
            if (th.getTextContent().trim().equals(label)) {
                return th;
            }
        }
        throw new AssertionError("no header labeled " + label);
    }

    private static int headerIndex(HtmlTable table, String label) {
        int idx = 0;
        for (HtmlElement th : table.getElementsByTagName("th")) {
            if (th.getTextContent().trim().equals(label)) {
                return idx;
            }
            idx++;
        }
        throw new AssertionError("no header labeled " + label);
    }

    /** Text of the given cell index for each non-xray body row, in DOM order. */
    private static List<String> dataCellTexts(HtmlTable table, int cellIndex) {
        List<String> out = new ArrayList<>();
        for (HtmlTableRow row : table.getBodies().get(0).getRows()) {
            if (row.getAttribute("class").contains("xray-row")) {
                continue;
            }
            out.add(row.getCells().get(cellIndex).getTextContent().trim());
        }
        return out;
    }

    private static Path writeReport(Path tempDir) {
        List<FileHotspot> files = List.of(
                new FileHotspot("src/Alpha.java", 30, 3, 90, 3.0, 30, 1.0, 90.0, null),
                new FileHotspot("src/Beta.java", 10, 1, 10, 1.0, 10, 1.0, 10.0, null),
                new FileHotspot("src/Gamma.java", 20, 2, 40, 2.0, 20, 1.0, 40.0, null));
        List<MethodHotspot> methods = List.of(
                method("src/Alpha.java", "a", 25, 3.0),
                method("src/Beta.java", "b", 5, 1.0),
                method("src/Gamma.java", "c", 15, 2.0));
        AnalysisMeta meta = new AnalysisMeta(Instant.parse("2026-01-01T00:00:00Z"),
                "LOCAL_GIT:/tmp/x", 3, 3, 3);
        AnalysisResult result = new AnalysisResult(files, methods, List.of(), List.of(), meta, null);

        new HtmlOutputWriter().write(result, tempDir);
        return tempDir.resolve("hotspots.html");
    }

    private static MethodHotspot method(String path, String name, int loc, double decay) {
        return new MethodHotspot(
                new MethodSignature("com.example.C", name, List.of()),
                path, 1, 1 + loc, loc, 1, loc, decay, loc, 1.0, loc * decay, null);
    }
}
