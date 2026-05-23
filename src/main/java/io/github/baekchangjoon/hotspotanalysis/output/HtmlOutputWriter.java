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
import java.util.List;

/**
 * Writes the analysis result as a single, self-contained HTML report.
 */
@Component
public class HtmlOutputWriter implements OutputWriter {

    @Override
    public OutputConfig.OutputFormat format() {
        return OutputConfig.OutputFormat.HTML;
    }

    @Override
    public void write(AnalysisResult result, Path outputDir) {
        write(result, outputDir, new OutputConfig(List.of(OutputConfig.OutputFormat.HTML), outputDir.toString(), 0), false);
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
                Files.writeString(outputDir.resolve("hotspots.html"), body, StandardCharsets.UTF_8);
                return;
            }

            if (combined) {
                String body = renderCombined(result, true);
                Files.writeString(outputDir.resolve("hotspots.html"), body, StandardCharsets.UTF_8);
            }

            if (standalone) {
                String body = renderStandalone(result);
                Files.writeString(outputDir.resolve("api_report.html"), body, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new OutputException("Failed to write HTML output to " + outputDir, e);
        }
    }

    private static String renderCombined(AnalysisResult result, boolean includeApi) {
        StringBuilder html = new StringBuilder(32_768);
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n");
        appendHead(html);
        html.append("<body>\n");
        appendHeader(html, result.meta());
        appendMetadata(html, result.meta());

        // Tab Navigation
        html.append("  <div class=\"tab-container\">\n");
        html.append("    <button class=\"tab-button active\" onclick=\"switchTab(event, 'files-tab')\">File Hotspots</button>\n");
        html.append("    <button class=\"tab-button\" onclick=\"switchTab(event, 'methods-tab')\">Method Hotspots</button>\n");
        if (includeApi) {
            html.append("    <button class=\"tab-button\" onclick=\"switchTab(event, 'apis-tab')\">REST API Hotspots</button>\n");
            html.append("    <button class=\"tab-button\" onclick=\"switchTab(event, 'shared-tab')\">Shared Components</button>\n");
        }
        html.append("  </div>\n\n");

        // Tab Content
        html.append("  <div id=\"files-tab\" class=\"tab-content active\">\n");
        appendFileSection(html, result.fileHotspots(), result.methodHotspots());
        html.append("  </div>\n");

        html.append("  <div id=\"methods-tab\" class=\"tab-content\">\n");
        appendMethodSection(html, result.methodHotspots());
        html.append("  </div>\n");

        if (includeApi) {
            html.append("  <div id=\"apis-tab\" class=\"tab-content\">\n");
            appendApiSection(html, result.apiHotspots());
            html.append("  </div>\n");

            html.append("  <div id=\"shared-tab\" class=\"tab-content\">\n");
            appendSharedSection(html, result.sharedComponents());
            html.append("  </div>\n");
        }

        appendScript(html);
        html.append("</body>\n</html>\n");
        return html.toString();
    }

    private static String renderStandalone(AnalysisResult result) {
        StringBuilder html = new StringBuilder(32_768);
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n");
        appendHead(html);
        html.append("<body>\n");
        appendHeader(html, result.meta());
        appendMetadata(html, result.meta());

        // Tab Navigation
        html.append("  <div class=\"tab-container\">\n");
        html.append("    <button class=\"tab-button active\" onclick=\"switchTab(event, 'apis-tab')\">REST API Hotspots</button>\n");
        html.append("    <button class=\"tab-button\" onclick=\"switchTab(event, 'shared-tab')\">Shared Components</button>\n");
        html.append("  </div>\n\n");

        // Tab Content
        html.append("  <div id=\"apis-tab\" class=\"tab-content active\">\n");
        appendApiSection(html, result.apiHotspots());
        html.append("  </div>\n");

        html.append("  <div id=\"shared-tab\" class=\"tab-content\">\n");
        appendSharedSection(html, result.sharedComponents());
        html.append("  </div>\n");

        appendScript(html);
        html.append("</body>\n</html>\n");
        return html.toString();
    }

    private static void appendHead(StringBuilder html) {
        html.append("""
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Hotspot Analysis Report</title>
                  <style>
                    :root {
                      --bg: #ffffff;
                      --fg: #1f2328;
                      --muted: #57606a;
                      --border: #d0d7de;
                      --accent: #0969da;
                      --accent-bg: #ddf4ff;
                      --row-hover: #f6f8fa;
                      --code-bg: #f6f8fa;
                    }
                    * { box-sizing: border-box; }
                    body {
                      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI",
                                   "Helvetica Neue", Arial, sans-serif;
                      color: var(--fg);
                      background: var(--bg);
                      margin: 0;
                      padding: 24px;
                      line-height: 1.55;
                    }
                    h1, h2 { margin-top: 0; }
                    h1 { font-size: 1.75rem; border-bottom: 1px solid var(--border);
                         padding-bottom: 8px; }
                    h2 { font-size: 1.25rem; margin-top: 32px; }
                    p.subtitle { color: var(--muted); margin-top: -4px; }
                    code { background: var(--code-bg); border-radius: 4px;
                           padding: 1px 6px; font-size: 0.9em;
                           font-family: ui-monospace, SFMono-Regular, Menlo,
                                        Consolas, monospace; }
                    .meta-table { border-collapse: collapse; margin-bottom: 8px; }
                    .meta-table th, .meta-table td { text-align: left; padding: 4px 12px;
                                                     border-bottom: 1px dashed var(--border); }
                    .meta-table th { color: var(--muted); font-weight: 600; }
                    .toolbar { display: flex; gap: 12px; align-items: center;
                               margin: 12px 0; }
                    .toolbar input[type="search"] {
                      flex: 1; max-width: 420px;
                      padding: 6px 10px;
                      border: 1px solid var(--border);
                      border-radius: 6px;
                      font-size: 0.95rem;
                    }
                    .toolbar .count {
                      color: var(--muted); font-size: 0.9rem;
                    }
                    table.sortable {
                      border-collapse: collapse;
                      width: 100%;
                      font-size: 0.92rem;
                    }
                    table.sortable thead th {
                      background: var(--code-bg);
                      border-bottom: 2px solid var(--border);
                      cursor: pointer;
                      user-select: none;
                      padding: 8px 12px;
                      text-align: left;
                      position: sticky; top: 0;
                    }
                    table.sortable thead th:hover { background: var(--accent-bg); }
                    table.sortable thead th[aria-sort="ascending"]::after  { content: " \\25B2"; }
                    table.sortable thead th[aria-sort="descending"]::after { content: " \\25BC"; }
                    table.sortable tbody td {
                      padding: 6px 12px;
                      border-bottom: 1px solid var(--border);
                      vertical-align: top;
                    }
                    table.sortable tbody tr:hover { background: var(--row-hover); }
                    .num { text-align: right; font-variant-numeric: tabular-nums; }
                    .rank { color: var(--muted); width: 48px; }
                    .params { color: var(--muted); font-size: 0.85em; }
                    footer { margin-top: 48px; color: var(--muted); font-size: 0.85rem;
                             border-top: 1px solid var(--border); padding-top: 12px; }
                    
                    /* Tab Navigation Styles */
                    .tab-container {
                      margin-top: 24px;
                      border-bottom: 1px solid var(--border);
                      display: flex;
                      gap: 8px;
                    }
                    .tab-button {
                      background: none;
                      border: none;
                      border-bottom: 2px solid transparent;
                      color: var(--muted);
                      cursor: pointer;
                      font-size: 1rem;
                      font-weight: 600;
                      padding: 8px 16px;
                      transition: all 0.2s ease;
                    }
                    .tab-button:hover {
                      color: var(--fg);
                      border-bottom-color: var(--border);
                    }
                    .tab-button.active {
                      color: var(--accent);
                      border-bottom-color: var(--accent);
                    }
                    .tab-content {
                      display: none;
                    }
                    .tab-content.active {
                      display: block;
                    }

                    /* HTTP Method Badge Styles */
                    .method-badge {
                      display: inline-block;
                      padding: 2px 8px;
                      font-size: 0.8rem;
                      font-weight: 700;
                      border-radius: 4px;
                      text-align: center;
                      color: #fff;
                    }
                    .method-badge.get { background-color: #2da44e; }
                    .method-badge.post { background-color: #0969da; }
                    .method-badge.put { background-color: #bf8700; }
                    .method-badge.delete { background-color: #cf222e; }
                    .method-badge.patch { background-color: #8250df; }

                    /* Details and Collapsible styles */
                    details {
                      cursor: pointer;
                    }
                    details summary {
                      font-weight: 600;
                      color: var(--accent);
                      outline: none;
                    }
                    details ul {
                      margin: 4px 0 0 0;
                      padding-left: 20px;
                      list-style-type: disc;
                    }
                    details li {
                      margin-bottom: 2px;
                    }
                    .no-calls {
                      color: var(--muted);
                      font-style: italic;
                    }

                    @media (prefers-color-scheme: dark) {
                      :root {
                        --bg: #0d1117; --fg: #c9d1d9; --muted: #8b949e;
                        --border: #30363d; --accent: #58a6ff; --accent-bg: #1f6feb33;
                        --row-hover: #161b22; --code-bg: #161b22;
                      }
                    }

                    /* X-Ray Drilldown Styles */
                    .xray-toggle-icon {
                      font-size: 0.8em;
                      color: var(--muted);
                      margin-left: 4px;
                      transition: transform 0.2s ease;
                      display: inline-block;
                    }
                    .file-row.expanded .xray-toggle-icon {
                      transform: rotate(90deg);
                    }
                    .xray-row {
                      background: var(--code-bg);
                    }
                    .xray-container {
                      padding: 16px 24px !important;
                      border-left: 4px solid var(--accent);
                    }
                    .xray-title {
                      font-size: 0.95rem;
                      font-weight: 600;
                      margin-bottom: 8px;
                      color: var(--fg);
                    }
                    .xray-table {
                      width: 100%;
                      border-collapse: collapse;
                      font-size: 0.88rem;
                    }
                    .xray-table th, .xray-table td {
                      padding: 6px 12px;
                      border-bottom: 1px solid var(--border);
                      text-align: left;
                    }
                    .xray-table th {
                      color: var(--muted);
                      font-weight: 600;
                      border-bottom: 2px solid var(--border);
                    }
                    .no-methods {
                      color: var(--muted);
                      font-style: italic;
                      margin: 0;
                    }
                  </style>
                </head>
                """);
    }

    private static void appendHeader(StringBuilder html, AnalysisMeta meta) {
        html.append("  <h1>Hotspot Analysis Report</h1>\n");
        html.append("  <p class=\"subtitle\">Generated ")
                .append(escape(meta.analyzedAt().toString()))
                .append(" &middot; ")
                .append(meta.totalFiles()).append(" files &middot; ")
                .append(meta.totalMethods()).append(" methods &middot; ")
                .append(meta.totalCommits()).append(" commits</p>\n");
    }

    private static void appendMetadata(StringBuilder html, AnalysisMeta meta) {
        html.append("  <table class=\"meta-table\">\n");
        appendMetaRow(html, "Generated at", meta.analyzedAt().toString());
        appendMetaRow(html, "Target",       meta.targetDescription());
        appendMetaRow(html, "Scoring formula", meta.scoringFormula().name());
        appendMetaRow(html, "Total commits", Integer.toString(meta.totalCommits()));
        appendMetaRow(html, "Total files",   Integer.toString(meta.totalFiles()));
        appendMetaRow(html, "Total methods", Integer.toString(meta.totalMethods()));
        html.append("  </table>\n");
    }

    private static void appendMetaRow(StringBuilder html, String key, String value) {
        html.append("    <tr><th>").append(escape(key)).append("</th><td><code>")
                .append(escape(value)).append("</code></td></tr>\n");
    }

    private static void appendFileSection(StringBuilder html, List<FileHotspot> files, List<MethodHotspot> methodHotspots) {
        java.util.Map<String, List<MethodHotspot>> methodsByFile = new java.util.HashMap<>();
        for (MethodHotspot mh : methodHotspots) {
            methodsByFile.computeIfAbsent(mh.filePath(), k -> new java.util.ArrayList<>()).add(mh);
        }

        html.append("  <section>\n");
        html.append("    <h2>File Hotspots (").append(files.size()).append(" rows)</h2>\n");
        html.append("    <div class=\"toolbar\">\n");
        html.append("      <input type=\"search\" data-filter-target=\"#file-hotspots\" "
                + "placeholder=\"Filter by path…\" aria-label=\"Filter file hotspots\">\n");
        html.append("      <span class=\"count\" data-count-target=\"#file-hotspots\">")
                .append(files.size()).append(" / ").append(files.size()).append("</span>\n");
        html.append("    </div>\n");
        html.append("    <table id=\"file-hotspots\" class=\"sortable\">\n");
        html.append("      <thead><tr>");
        html.append("<th data-sort-type=\"number\">Rank</th>");
        html.append("<th data-sort-type=\"string\">Path</th>");
        html.append("<th data-sort-type=\"number\" class=\"num\">Revisions</th>");
        html.append("<th data-sort-type=\"number\" class=\"num\">LOC</th>");
        html.append("<th data-sort-type=\"number\" class=\"num\">Score</th>");
        html.append("</tr></thead>\n");
        html.append("      <tbody>\n");
        int rank = 1;
        for (FileHotspot file : files) {
            List<MethodHotspot> fileMethods = methodsByFile.getOrDefault(file.path(), java.util.List.of());
            double totalMethodScore = fileMethods.stream().mapToDouble(MethodHotspot::score).sum();

            html.append("        <tr class=\"file-row\" onclick=\"toggleXray(&#39;xray-")
                .append(rank).append("&#39;, this)\" style=\"cursor: pointer;\">");
            html.append("<td class=\"rank\" data-sort-value=\"").append(rank).append("\">")
                    .append(rank).append("</td>");
            html.append("<td><code>").append(escape(file.path())).append("</code> <span class=\"xray-toggle-icon\">▶</span></td>");
            appendNumericCell(html, file.revisions(), file.revisions());
            appendNumericCell(html, file.loc(), file.loc());
            appendScoreCell(html, file.score());
            html.append("</tr>\n");

            html.append("        <tr id=\"xray-").append(rank).append("\" class=\"xray-row\" style=\"display: none;\">\n");
            html.append("          <td colspan=\"5\" class=\"xray-container\">\n");
            if (fileMethods.isEmpty()) {
                html.append("            <p class=\"no-methods\">No methods analyzed in this file.</p>\n");
            } else {
                html.append("            <div class=\"xray-title\">X-Ray Method Drill-Down for <code>").append(escape(file.path())).append("</code></div>\n");
                html.append("            <table class=\"xray-table\">\n");
                html.append("              <thead><tr>");
                html.append("<th>Method Signature</th>");
                html.append("<th class=\"num\">Lines</th>");
                html.append("<th class=\"num\">Complexity</th>");
                html.append("<th class=\"num\">Decayed Revs</th>");
                html.append("<th class=\"num\">Coverage</th>");
                html.append("<th class=\"num\">Score</th>");
                html.append("<th class=\"num\">Share</th>");
                html.append("</tr></thead>\n");
                html.append("              <tbody>\n");
                for (MethodHotspot mh : fileMethods) {
                    double share = totalMethodScore > 0 ? (mh.score() / totalMethodScore) * 100.0 : 0.0;
                    String methodSig = mh.signature().methodName() + "(" + String.join(", ", mh.signature().parameterTypes()) + ")";

                    html.append("                <tr>");
                    html.append("<td><code>").append(escape(methodSig)).append("</code></td>");
                    html.append("<td class=\"num\">").append(mh.startLine()).append("&ndash;").append(mh.endLine()).append("</td>");

                    double cc = mh.cognitiveComplexity() != null ? mh.cognitiveComplexity() : 0.0;
                    html.append("<td class=\"num\">").append((int) cc).append("</td>");

                    double dr = mh.decayedRevisions() != null ? mh.decayedRevisions() : (double) mh.revisions();
                    html.append("<td class=\"num\">").append(String.format("%.2f", dr)).append("</td>");

                    double cov = mh.coverage() != null ? mh.coverage() : 0.0;
                    html.append("<td class=\"num\">").append(String.format("%.1f%%", cov * 100.0)).append("</td>");

                    appendScoreCell(html, mh.score());

                    html.append("<td class=\"num\">").append(String.format("%.1f%%", share)).append("</td>");
                    html.append("</tr>\n");
                }
                html.append("              </tbody>\n");
                html.append("            </table>\n");
            }
            html.append("          </td>\n");
            html.append("        </tr>\n");

            rank++;
        }
        html.append("      </tbody>\n");
        html.append("    </table>\n");
        html.append("  </section>\n");
    }

    private static void appendMethodSection(StringBuilder html, List<MethodHotspot> methods) {
        html.append("  <section>\n");
        html.append("    <h2>Method Hotspots (").append(methods.size()).append(" rows)</h2>\n");
        html.append("    <div class=\"toolbar\">\n");
        html.append("      <input type=\"search\" data-filter-target=\"#method-hotspots\" "
                + "placeholder=\"Filter by class, method, or file…\" "
                + "aria-label=\"Filter method hotspots\">\n");
        html.append("      <span class=\"count\" data-count-target=\"#method-hotspots\">")
                .append(methods.size()).append(" / ").append(methods.size()).append("</span>\n");
        html.append("    </div>\n");
        html.append("    <table id=\"method-hotspots\" class=\"sortable\">\n");
        html.append("      <thead><tr>");
        html.append("<th data-sort-type=\"number\">Rank</th>");
        html.append("<th data-sort-type=\"string\">Class</th>");
        html.append("<th data-sort-type=\"string\">Method</th>");
        html.append("<th data-sort-type=\"string\">Parameters</th>");
        html.append("<th data-sort-type=\"string\">File</th>");
        html.append("<th data-sort-type=\"number\" class=\"num\">Lines</th>");
        html.append("<th data-sort-type=\"number\" class=\"num\">Revisions</th>");
        html.append("<th data-sort-type=\"number\" class=\"num\">LOC</th>");
        html.append("<th data-sort-type=\"number\" class=\"num\">Score</th>");
        html.append("</tr></thead>\n");
        html.append("      <tbody>\n");
        int rank = 1;
        for (MethodHotspot method : methods) {
            String fqcn = method.signature().fullyQualifiedClassName();
            String name = method.signature().methodName();
            String params = String.join(", ", method.signature().parameterTypes());
            html.append("        <tr data-path=\"").append(escape(method.filePath()))
                    .append("\" data-start-line=\"").append(method.startLine())
                    .append("\" data-end-line=\"").append(method.endLine()).append("\">");
            html.append("<td class=\"rank\" data-sort-value=\"").append(rank).append("\">")
                    .append(rank).append("</td>");
            html.append("<td><code>").append(escape(fqcn)).append("</code></td>");
            html.append("<td><code>").append(escape(name)).append("</code></td>");
            html.append("<td class=\"params\">").append(escape(params)).append("</td>");
            html.append("<td><code>").append(escape(method.filePath())).append("</code></td>");
            String lineRange = method.startLine() + "&ndash;" + method.endLine();
            html.append("<td class=\"num\" data-sort-value=\"").append(method.startLine())
                    .append("\">").append(lineRange).append("</td>");
            appendNumericCell(html, method.revisions(), method.revisions());
            appendNumericCell(html, method.loc(), method.loc());
            appendScoreCell(html, method.score());
            html.append("</tr>\n");
            rank++;
        }
        html.append("      </tbody>\n");
        html.append("    </table>\n");
        html.append("  </section>\n");
    }

    private static void appendNumericCell(StringBuilder html, int displayValue, int sortValue) {
        html.append("<td class=\"num\" data-sort-value=\"").append(sortValue).append("\">")
                .append(displayValue).append("</td>");
    }

    private static void appendScoreCell(StringBuilder html, double score) {
        long rounded = Math.round(score);
        html.append("<td class=\"num\" data-sort-value=\"").append(rounded).append("\">")
                .append(formatScore(score)).append("</td>");
    }

    private static String formatScore(double score) {
        if (score == Math.floor(score) && !Double.isInfinite(score)) {
            return Long.toString((long) score);
        }
        return String.format("%.2f", score);
    }

    private static void appendScript(StringBuilder html) {
        html.append("""
                  <footer>Sort by clicking a column header. Filter rows by typing in the search box above each table.</footer>
                  <script>
                  (function () {
                    function toggleXray(rowId, el) {
                      var target = document.getElementById(rowId);
                      if (!target) return;
                      if (target.style.display === 'none') {
                        target.style.display = 'table-row';
                        el.classList.add('expanded');
                      } else {
                        target.style.display = 'none';
                        el.classList.remove('expanded');
                      }
                    }
                    window.toggleXray = toggleXray;

                    function switchTab(evt, tabId) {
                      var contents = document.querySelectorAll('.tab-content');
                      contents.forEach(function (content) {
                        content.classList.remove('active');
                      });
                      var buttons = document.querySelectorAll('.tab-button');
                      buttons.forEach(function (button) {
                        button.classList.remove('active');
                      });
                      document.getElementById(tabId).classList.add('active');
                      evt.currentTarget.classList.add('active');
                    }
                    window.switchTab = switchTab;

                    function applyFilter(input) {
                      var sel = input.getAttribute('data-filter-target');
                      var table = document.querySelector(sel);
                      if (!table) return;
                      var q = input.value.trim().toLowerCase();
                      var rows = table.tBodies[0].rows;
                      var shown = 0;
                      for (var i = 0; i < rows.length; i++) {
                        var match = !q || rows[i].textContent.toLowerCase().indexOf(q) !== -1;
                        rows[i].style.display = match ? '' : 'none';
                        if (match) shown++;
                      }
                      var countEl = document.querySelector('[data-count-target="' + sel + '"]');
                      if (countEl) countEl.textContent = shown + ' / ' + rows.length;
                    }
                    document.querySelectorAll('input[type="search"][data-filter-target]')
                      .forEach(function (input) {
                        input.addEventListener('input', function () { applyFilter(input); });
                      });

                    function compare(a, b, type, asc) {
                      var av, bv;
                      if (type === 'number') {
                        av = parseFloat(a); bv = parseFloat(b);
                        if (isNaN(av)) av = -Infinity;
                        if (isNaN(bv)) bv = -Infinity;
                      } else {
                        av = a.toLowerCase(); bv = b.toLowerCase();
                      }
                      if (av < bv) return asc ? -1 : 1;
                      if (av > bv) return asc ? 1 : -1;
                      return 0;
                    }
                    document.querySelectorAll('table.sortable thead th').forEach(function (th, idx) {
                      th.addEventListener('click', function () {
                        var table = th.closest('table');
                        var type = th.getAttribute('data-sort-type') || 'string';
                        var asc = th.getAttribute('aria-sort') !== 'ascending';
                        table.querySelectorAll('thead th').forEach(function (h) {
                          h.removeAttribute('aria-sort');
                        });
                        th.setAttribute('aria-sort', asc ? 'ascending' : 'descending');
                        var rows = Array.prototype.slice.call(table.tBodies[0].rows);
                        rows.sort(function (r1, r2) {
                          var c1 = r1.cells[idx];
                          var c2 = r2.cells[idx];
                          var v1 = c1.getAttribute('data-sort-value');
                          var v2 = c2.getAttribute('data-sort-value');
                          if (v1 === null) v1 = c1.textContent;
                          if (v2 === null) v2 = c2.textContent;
                          return compare(v1, v2, type, asc);
                        });
                        var tbody = table.tBodies[0];
                        rows.forEach(function (r) { tbody.appendChild(r); });
                      });
                    });
                  })();
                  </script>
                """);
    }

    private static void appendApiSection(StringBuilder html, List<ApiHotspot> apis) {
        html.append("  <section>\n");
        html.append("    <h2>REST API Hotspots (").append(apis.size()).append(" rows)</h2>\n");
        html.append("    <div class=\"toolbar\">\n");
        html.append("      <input type=\"search\" data-filter-target=\"#api-hotspots\" "
                + "placeholder=\"Filter by method, route, or controller…\" aria-label=\"Filter API hotspots\">\n");
        html.append("      <span class=\"count\" data-count-target=\"#api-hotspots\">")
                .append(apis.size()).append(" / ").append(apis.size()).append("</span>\n");
        html.append("    </div>\n");
        html.append("    <table id=\"api-hotspots\" class=\"sortable\">\n");
        html.append("      <thead><tr>");
        html.append("<th data-sort-type=\"number\">Rank</th>");
        html.append("<th data-sort-type=\"string\">HTTP Method</th>");
        html.append("<th data-sort-type=\"string\">Route</th>");
        html.append("<th data-sort-type=\"string\">Controller Method</th>");
        html.append("<th data-sort-type=\"number\" class=\"num\">Revisions</th>");
        html.append("<th data-sort-type=\"number\" class=\"num\">LOC</th>");
        html.append("<th data-sort-type=\"number\" class=\"num\">Score</th>");
        html.append("<th>Call Graph</th>");
        html.append("</tr></thead>\n");
        html.append("      <tbody>\n");
        int rank = 1;
        for (ApiHotspot api : apis) {
            html.append("        <tr>");
            html.append("<td class=\"rank\" data-sort-value=\"").append(rank).append("\">")
                    .append(rank).append("</td>");
            html.append("<td><span class=\"method-badge ")
                    .append(api.httpMethod().toLowerCase()).append("\">")
                    .append(escape(api.httpMethod())).append("</span></td>");
            html.append("<td><code>").append(escape(api.route())).append("</code></td>");
            html.append("<td><code>").append(escape(api.controllerMethod().toCanonicalString())).append("</code></td>");
            appendNumericCell(html, api.revisions(), api.revisions());
            appendNumericCell(html, api.loc(), api.loc());
            appendScoreCell(html, api.score());
            
            // Call Graph cell
            html.append("<td>");
            if (api.callGraph().isEmpty()) {
                html.append("<span class=\"no-calls\">None</span>");
            } else {
                html.append("<details><summary>").append(api.callGraph().size()).append(" calls</summary><ul>");
                for (var sig : api.callGraph()) {
                    html.append("<li><code>").append(escape(sig.toCanonicalString())).append("</code></li>");
                }
                html.append("</ul></details>");
            }
            html.append("</td>");
            
            html.append("</tr>\n");
            rank++;
        }
        html.append("      </tbody>\n");
        html.append("    </table>\n");
        html.append("  </section>\n");
    }

    private static void appendSharedSection(StringBuilder html, List<SharedComponentHotspot> components) {
        html.append("  <section>\n");
        html.append("    <h2>Shared Components (").append(components.size()).append(" rows)</h2>\n");
        html.append("    <div class=\"toolbar\">\n");
        html.append("      <input type=\"search\" data-filter-target=\"#shared-hotspots\" "
                + "placeholder=\"Filter by method or calling api…\" aria-label=\"Filter shared components\">\n");
        html.append("      <span class=\"count\" data-count-target=\"#shared-hotspots\">")
                .append(components.size()).append(" / ").append(components.size()).append("</span>\n");
        html.append("    </div>\n");
        html.append("    <table id=\"shared-hotspots\" class=\"sortable\">\n");
        html.append("      <thead><tr>");
        html.append("<th data-sort-type=\"number\">Rank</th>");
        html.append("<th data-sort-type=\"string\">Method</th>");
        html.append("<th data-sort-type=\"number\" class=\"num\">Revisions</th>");
        html.append("<th data-sort-type=\"number\" class=\"num\">LOC</th>");
        html.append("<th data-sort-type=\"number\" class=\"num\">Score</th>");
        html.append("<th>Calling APIs</th>");
        html.append("</tr></thead>\n");
        html.append("      <tbody>\n");
        int rank = 1;
        for (SharedComponentHotspot component : components) {
            html.append("        <tr>");
            html.append("<td class=\"rank\" data-sort-value=\"").append(rank).append("\">")
                    .append(rank).append("</td>");
            html.append("<td><code>").append(escape(component.method().toCanonicalString())).append("</code></td>");
            appendNumericCell(html, component.revisions(), component.revisions());
            appendNumericCell(html, component.loc(), component.loc());
            appendScoreCell(html, component.score());
            
            // Calling APIs cell
            html.append("<td>");
            if (component.callingApis().isEmpty()) {
                html.append("<span class=\"no-calls\">None</span>");
            } else {
                html.append("<details><summary>").append(component.callingApis().size()).append(" APIs</summary><ul>");
                for (var api : component.callingApis()) {
                    html.append("<li><code>").append(escape(api)).append("</code></li>");
                }
                html.append("</ul></details>");
            }
            html.append("</td>");
            
            html.append("</tr>\n");
            rank++;
        }
        html.append("      </tbody>\n");
        html.append("    </table>\n");
        html.append("  </section>\n");
    }

    private static String escape(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
