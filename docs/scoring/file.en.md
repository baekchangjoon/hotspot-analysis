# File granularity (file)

> 🌐 [한국어](file.md) · **English** (this page)

For the common factor and score definitions, read the [Scoring
Reference](README.en.md) first. This document covers only how the **file
granularity** derives and aggregates those factors.

Source: `HotspotAnalyzer.buildFileHotspots()`, model `analysis/model/FileHotspot.java`.

## Factor derivation

| Factor | How it is derived at the file granularity |
|---|---|
| **Revisions** | The number of window commits that touched that file's path (once per commit). `calculateFileRevisions` |
| **LOC** | The line count of the file at HEAD (no comment/blank stripping). `LocCalculator.countLines` |
| **Recency Decay** | `Σ exp(-λ·Δt)` over the commits that touched that file. `calculateFileDecayedRevisions` |
| **Cognitive Complexity** | The **sum of the CC of all methods** in the file (`Σ m.cognitiveComplexity()`) |
| **Coverage** | `covered/instrumented` over the whole file's lines (when JaCoCo is provided). `getFileCoverage` |
| **Coverage Multiplier** | `1/(coverage+0.1)`, or 1.0 when not provided |

## Score computation

```
Simple    = Revisions × LOC
Composite = CC × RecencyDecay × CoverageMultiplier      # default
Composite = CC × RecencyDecay                           # excludeCoverage=true
```

## Report columns (CSV `file_hotspots.csv`)

Default (coverage included):

```
simple_rank, composite_rank, path, loc, revisions, simple_score,
recency_decay, cognitive_complexity, coverage_multiplier, composite_score
```

If `excludeCoverage=true`, the last two columns become `composite_score, line_coverage`.
YAML/MD/HTML carry the same values keyed by `path`.

## Worked example

`OrderService.java`: 8 commits within the window touched it (LOC=420), of which 4
were within the last 90 days. The sum of method CC in the file = 47. JaCoCo line
coverage = 0.30.

```
Revisions = 8
LOC       = 420
Simple    = 8 × 420 = 3360
D ≈ (sum of the 4 recent commits' weights) + (small sum of the 4 older commits' weights) ≈ 5.1   # example values
M         = 1/(0.30+0.10) = 2.5
Composite = 47 × 5.1 × 2.5 ≈ 599
```

Interpretation: Simple reflects "large and frequently changed," while Composite
reflects "complexity × recency × unprotected" together, pushing more dangerous
files to the top. The file granularity is for a quick overview; for pinpointing
exactly where to test, the [method](method.en.md) and [API
endpoint](rest-api-endpoint.en.md) granularities are more useful.
