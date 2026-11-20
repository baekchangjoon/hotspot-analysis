# HTML report data block

> 🌐 [한국어](html-report-data.md) · **English** (this page)

The **Hotspot Map** tab of `hotspots.html` is a fixed template
(`src/main/resources/report/`) plus one JSON document, and that JSON ships
inside the report:

```html
<script type="application/json" id="hotspot-map-data">{"schemaVersion":1,"meta":{…},"files":[…],"methods":[…]}</script>
```

When aggregating many reports into a dashboard, read this block instead of
scraping table cells. In a browser:
`JSON.parse(document.getElementById("hotspot-map-data").textContent)`. The
characters `<`, `>` and `&` are written as JSON unicode escapes (`<`, …);
any JSON parser returns the original strings.

`schemaVersion` is bumped when a field is renamed, removed or changes meaning.
Adding a field does not bump it, so ignore fields you do not know.

## `meta`

| Field | Type | Meaning |
|---|---|---|
| `label` | string | Name of the analysed directory (never the absolute path) |
| `analyzedAt` | string | Analysis time (ISO-8601, UTC) |
| `commits` | int | Commits inside the window |
| `totalFiles` / `totalMethods` | int | Counts **before** `output.topN` is applied |
| `halfLifeDays` | int | `scoring.decayHalfLifeDays` |
| `excludeCoverage` | bool | `scoring.excludeCoverage` |
| `blobBase` | string \| null | Source link prefix: `blobBase + path + "#L10-L20"`. `null` unless origin is on GitHub/GitLab |
| `revision` | string \| null | Full SHA of the analysed commit |
| `totalFileScore` | number | Composite total over **every** file (before `topN`) |
| `coveredLines` / `executableLines` | int | JaCoCo line totals over every file; 0 without a report |

## `files[]` — by descending composite score

| Field | Type | Meaning |
|---|---|---|
| `path` | string | Path relative to the repository root |
| `rank` | int | Composite rank, from 1 |
| `loc`, `rev` | int | Lines of code, commits in the window |
| `decay` | number | Recency Decay |
| `cc` | number | Cognitive Complexity (sum over methods) |
| `covMul` | number | Coverage Multiplier |
| `cov` | number \| null | Line coverage in `[0,1]`. **`null` = unknown**, `0` = no line was executed |
| `covered`, `exec` | int \| null | Executed / executable lines; `null` when `cov` is `null` |
| `score` | number | Composite Score |

## `methods[]` — by descending composite score

`fqcn`, `cls` (simple class name), `name`, `params[]`, `file`, `start`, `end`,
plus `rank`, `loc`, `rev`, `decay`, `cc`, `covMul`, `cov`, `score` with the same
meaning as for files.

## Determinism

The same analysis result always produces byte-identical HTML (no randomness —
even the map layout and drift phases are computed from the data). Only two
analysis inputs vary between runs: `analyzedAt`, and Recency Decay when
`window.until` is not set, because it is then measured from "now". Pin
`window.until` for a reproducible report.
