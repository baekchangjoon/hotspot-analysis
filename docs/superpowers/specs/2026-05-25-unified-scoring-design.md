# Unified Scoring Model — Design

> Date: 2026-05-25
> Status: Approved (proceeds to implementation plan)
> Owner: Baek
> Targets: hotspot-analysis v0.2

## 1. Goal

Eliminate the dual `SIMPLE`/`COMPOSITE` paths. Every analysis run computes — and every report (CSV, YAML, Markdown, HTML) emits — both scores side by side, plus the four input factors (`loc`, `revisions`, recency decay, cognitive complexity, coverage multiplier) in a fixed column order.

## 2. Non-Goals

- New scoring formulas. Only `simpleScore = revisions × loc` and `compositeScore = cognitiveComplexity × recencyDecay × coverageMultiplier` are produced.
- Method-level coverage display. Existing per-method coverage in X-Ray HTML continues; no new UI surface.
- Multi-language support; bot filtering; other Phase 2 items.

## 3. Approved Decisions

| Decision | Choice |
|---|---|
| Row sort key | **Composite Score DESC**, tie-break by `path` (files) / canonical signature (methods) / `route+method` (API) |
| `scoring.formula` YAML key | **Removed.** Old configs surface a friendly error: *"scoring.formula has been removed in v0.2 — all reports now include both simple and composite scores. Delete this line."* |
| Coverage Multiplier when JaCoCo missing | **1.0** (neutral). Composite Score then = `cognitiveComplexity × recencyDecay`. |
| API / SharedComponent reports | Same 7-column metric block as file/method |

## 4. Column Order (every report)

After identifier columns:

```
LOC | Revisions | Simple Score | Recency Decay | Cognitive Complexity | Coverage Multiplier | Composite Score
```

Identifier columns by granularity:

| Report | Identifier columns |
|---|---|
| File | `rank, path` |
| Method | `rank, fqcn, method, parameters, file, start_line, end_line` |
| API | `rank, httpMethod, route, controllerMethod` |
| SharedComponent | `rank, fqcn, method, parameters` |

## 5. Config Schema (v0.2)

```yaml
analysis:
  target: { type: local-git, path: ... }
  window: { since: ..., until: ... }    # or { days: N }
  scope: { granularity: [file, method], include: [...], exclude: [...] }

  scoring:                                # OPTIONAL
    decayHalfLifeDays: 90                 # default 90

  jacocoReportPath: build/.../jacoco.xml  # OPTIONAL; absent → multiplier=1.0

  apiAnalysis: { enabled: false, ... }    # unchanged

output:
  formats: [csv, yaml, md, html]
  path: ./hotspot-report
  topN: 20
```

### Migration handling

`ConfigLoader#parse` already catches `UnrecognizedPropertyException`. Specialize the branch so that `propertyName == "formula"` produces the migration message above. All other unknown keys keep the existing generic message.

## 6. Domain Model

All metric fields become **non-nullable `double`** / `int`. The optional `Double` fields + secondary constructors are deleted.

```java
public record FileHotspot(
    String path,
    int loc,
    int revisions,
    double simpleScore,
    double recencyDecay,
    double cognitiveComplexity,
    double coverageMultiplier,
    double compositeScore
) {}

public record MethodHotspot(
    MethodSignature signature,
    String filePath,
    int startLine, int endLine,
    int loc,
    int revisions,
    double simpleScore,
    double recencyDecay,
    double cognitiveComplexity,
    double coverageMultiplier,
    double compositeScore
) {}

public record ApiHotspot(
    String httpMethod,
    String route,
    MethodSignature controllerMethod,
    int loc,
    int revisions,
    double simpleScore,
    double recencyDecay,
    double cognitiveComplexity,
    double coverageMultiplier,
    double compositeScore,
    List<MethodSignature> callGraph
) {}

public record SharedComponentHotspot(
    MethodSignature method,
    int loc,
    int revisions,
    double simpleScore,
    double recencyDecay,
    double cognitiveComplexity,
    double coverageMultiplier,
    double compositeScore,
    List<String> callingApis
) {}
```

## 7. Score Pipeline

`HotspotAnalyzer#analyze` becomes single-path:

1. Load commits → file/method revisions (existing).
2. Compute LOC (existing).
3. Compute **decayed revisions** for every file & method with `decayHalfLifeDays` (default 90).
4. Parse JaCoCo XML if `jacocoReportPath` is set; otherwise leave coverage map empty.
5. For every file & method:
   - `simpleScore = revisions × loc`
   - `recencyDecay = decayedRevisions` (file-level: file decayed; method-level: method decayed)
   - `cognitiveComplexity` from `CognitiveComplexityCalculator` (file = Σ of its methods)
   - `coverage = jacoco.getMethodCoverage(...)` or `getFileCoverage(...)`; if no jacoco supplied, `coverageMultiplier = 1.0`; otherwise `coverageMultiplier = 1/(coverage + 0.1)`. Aggregated API & SharedComponent rows use `avg(coverage)` across the call graph, same as today.
   - `compositeScore = cognitiveComplexity × recencyDecay × coverageMultiplier`

   The on-disk model stores only `coverageMultiplier` (always a real `double`); raw coverage doesn't surface in the report — users who want raw coverage should view the JaCoCo HTML directly.
6. Sort by `compositeScore` DESC + tie-break.
7. Apply `topN`.

Score helpers move out of `HotspotScoreCalculator`'s switch and into two pure methods:

```java
double simple(int revisions, int loc);
double composite(double complexity, double decayed, double multiplier);
double multiplier(OptionalDouble coverage);   // 1.0 if empty, else 1/(cov+0.1)
```

## 8. Output Writers

### CSV (single-table-per-granularity)

`file_hotspots.csv` header:

```
rank,path,loc,revisions,simple_score,recency_decay,cognitive_complexity,coverage_multiplier,composite_score
```

`method_hotspots.csv` header:

```
rank,fqcn,method,parameters,file,start_line,end_line,loc,revisions,simple_score,recency_decay,cognitive_complexity,coverage_multiplier,composite_score
```

`api_hotspots.csv` / `shared_components.csv` analogous.

### YAML

Each row a map with flat keys in the same order. Doubles formatted to 4 decimal places when fractional, ints as ints.

### Markdown

Single table per granularity, 7 metric columns appended to identifier columns. Pipe alignment `---:` for numerics.

### HTML

Top table: identifier columns + 7 metric columns, all sortable.
X-Ray drilldown header becomes: `Method Signature | LOC | Revisions | Simple Score | Recency Decay | Cognitive Complexity | Coverage Multiplier | Composite Score | Share`. The `Share` column is the row's `compositeScore` as a percentage of the enclosing file's `Σ method compositeScore` — same semantic as today.

Light/dark mode + XSS-escape preserved.

## 9. Tests

Mandatory updates:

- `FileHotspotTest`, `MethodHotspotTest`, `ApiHotspotTest`, `SharedComponentHotspotTest`: remove nullable-field tests; assert all metric fields are populated.
- `HotspotAnalyzerTest`: delete formula-branch tests; add a single happy-path that verifies all 7 metrics for one file & one method.
- `CsvOutputWriterTest`, `YamlOutputWriterTest`, `MarkdownOutputWriterTest`, `HtmlOutputWriterTest`: snapshot regeneration with new headers/columns.
- `HotspotCliE2ETest`: replace the COMPOSITE-mode test with a single E2E that asserts the unified output contains both score columns.
- `ConfigLoaderTest`: add a test for the `formula` legacy-key migration message.

Test fixtures (`tiny-repo-*.zip`) reusable as-is; only the expected output files change.

## 10. Documentation Sync

- `README.md`: rewrite "How the score is computed" section; update CSV column counts (10 / 14); show sample row.
- `docs/phase1-design.md`: append §13 "v0.2: Unified scoring model" with the table from §3.
- `docs/hotspot-advanced-spec.md`: add a leading note that all four advanced factors are now always-on.
- `src/main/resources/templates/hotspot.example.yml`: drop the `formula:` line, add a comment about `decayHalfLifeDays` and `jacocoReportPath` being optional.

## 11. Backward Compat & Migration

- Old YAML configs with `scoring.formula: simple|composite` → friendly error from `ConfigLoader` instructing deletion. Single line change.
- Stale jars built before v0.2 will still parse old YAMLs but won't carry the new fields; nothing we can do — user must rebuild.

## 12. Risks

| Risk | Mitigation |
|---|---|
| R1: Snapshot tests in the writers churn heavily, hiding regressions | Reviewer must diff against pre-change CSV/MD/HTML for one fixture to confirm only column additions, no value drift on simple_score |
| R2: Composite Score depends on JaCoCo presence; users who never had jacoco may now see compositeScore = simpleScore × constant when half-life is huge | README "How the score is computed" must make this explicit |
| R3: Sort key change (was simple-score-desc in v0.1) reorders existing PR diffs | Acceptable — release-noted under v0.2 |

## 13. Out of Scope (Phase 2+)

- `output.sortBy` config knob (deferred — current sort key fixed at composite)
- Per-method coverage in non-HTML outputs beyond what's already exposed by the unified columns
- Internationalised column headers
