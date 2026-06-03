# `api_report.yml` / hotspot report — field schema

Field reference for the machine-readable output an agent consumes. Derivations
(how each number is computed) are in [`docs/scoring/`](../../../docs/scoring/README.en.md).

Produced when `analysis.apiAnalysis.enabled: true`. With `output.apiLayout`:
`STANDALONE` → `api_report.yml`; `COMBINED` → merged into `hotspots.yml`;
`BOTH` → both. YAML, Markdown, and HTML carry the same fields; CSV splits per
granularity.

## Top-level keys

| Key | Present when | Contents |
|---|---|---|
| `apiHotspots` | `apiAnalysis.enabled` | Ranked REST endpoints (primary test-priority queue) |
| `sharedComponents` | mode `SEPARATE` or `BOTH` | Methods called by ≥2 endpoints |
| `fileHotspots` | combined doc | Per-file rows |
| `methodHotspots` | combined doc | Per-method rows |

Every row has `simpleRank` (rank by Simple Score) and `compositeRank` (rank by
Composite Score = the row order). **Use `compositeRank` for test priority.**

## `apiHotspots[]`

| Field | Type | Meaning |
|---|---|---|
| `simpleRank` | int | Rank by `simpleScore` (Revisions × LOC) |
| `compositeRank` | int | Rank by `compositeScore` — **the order to write tests in** |
| `httpMethod` | string | `GET` / `POST` / `PUT` / `DELETE` / `PATCH` |
| `route` | string | Full path = class `@RequestMapping` prefix + method mapping |
| `fqcn` | string | Controller class fully-qualified name |
| `method` | string | Controller method name |
| `parameters` | string[] | Controller method parameter types |
| `loc` | int | Aggregated LOC (controller + call graph) |
| `revisions` | int | Aggregated revisions |
| `simpleScore` | number | `revisions × loc` |
| `recencyDecay` | number | Aggregated recency-decayed revisions |
| `cognitiveComplexity` | number | Aggregated cognitive complexity |
| `coverageMultiplier` | number | `1/(avgCoverage + 0.1)`; `1.0` if no JaCoCo. *(absent when `excludeCoverage`)* |
| `lineCoverage` | number\|null | Avg line coverage `[0,1]` of the methods; only when `excludeCoverage` |
| `compositeScore` | number | `cognitiveComplexity × recencyDecay × coverageMultiplier` |
| `callGraph` | string[] | Reachable method signatures from this endpoint |

## `sharedComponents[]`

Same metric block as above, for a single shared method (not aggregated), plus:

| Field | Type | Meaning |
|---|---|---|
| `fqcn`, `method`, `parameters` | — | The shared method's signature |
| `callingApis` | string[] | Endpoints that call it, e.g. `"POST /api/orders"` |

`callingApis.length ≥ 2` by definition. High-leverage: covering one shared
component protects every endpoint in `callingApis`.

## Consuming for RestAssured (sketch)

```
for endpoint in sort(apiHotspots, by=compositeRank):     # most important first
    req   = build_request(endpoint.httpMethod, endpoint.route, endpoint.parameters)
    focus = methods in endpoint.callGraph with low coverage   # what to assert hardest
    emit  RestAssured test(req, focus)
# then cover top sharedComponents once to protect many endpoints at once
```
