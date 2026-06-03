# REST API Endpoint Unit (rest-api-endpoint)

> 🌐 [한국어](rest-api-endpoint.md) · **English** (this page)

For the common factor and score definitions, read the [scoring reference](README.en.md) first. This document covers how the
**API endpoint unit** **aggregates** factors along the call graph.

This unit is produced only when `analysis.apiAnalysis.enabled: true`, and it gathers into a single score the risk that one HTTP endpoint carries
**from the controller method through its entire call graph**. It is designed as a **prioritization input for API test generation** such as RestAssured.

Source: `HotspotAnalyzer.buildApiAndShared()`, `analysis/CallGraphBuilder.java`,
model `analysis/model/ApiHotspot.java`.

## 1) Endpoint Identification — `httpMethod` + `route`

- Controller: methods of `@RestController` / `@Controller` classes annotated with
  `@GetMapping`·`@PostMapping`·`@PutMapping`·`@DeleteMapping`·`@PatchMapping`·`@RequestMapping`.
- **route** = the class-level `@RequestMapping` prefix combined with the method-level mapping path,
  then normalized (cleaning up `//`, removing trailing `/`). `JavaSourceParser.resolveApiMappings`.
- **httpMethod** = the annotation type (`@GetMapping`→GET, etc.). If a `@RequestMapping`
  has no `method`, it is treated as GET.

## 2) Call Graph Construction

`CallGraphBuilder` resolves types with JavaSymbolSolver, starting from the controller method and performing
**DFS**, following only method calls "within the analyzed sources":

- Interface calls are chained to their implementations (interface→impl mapping).
- Cycles are blocked via `visited`, and reached methods are deduplicated with a `LinkedHashSet`.
- External library calls are out of scope and are not included in the graph.
- **Accuracy tip**: for symbol resolution, specifying dependency
  jar/class directories in `apiAnalysis.classpathDirectories` lets more calls be resolved. When unspecified, resolution relies only on the standard
  library (ReflectionTypeSolver) and the source roots.

`ApiHotspot.callGraph` carries **every** reached callee method signature (the
shared-component exclusion rule below affects only score aggregation, not the list itself).

## 3) Factor Aggregation — Controller + Call Graph Sum

Each factor of an endpoint = **the controller method value + the sum of the values of the reached callee methods**.

| Factor | Aggregation |
|---|---|
| **Revisions** | `R(controller) + Σ R(called)` |
| **LOC** | `LOC(controller) + Σ LOC(called)` |
| **Recency Decay** | `D(controller) + Σ D(called)` |
| **Cognitive Complexity** | `CC(controller) + Σ CC(called)` |
| **Coverage** | The **average method coverage** of the controller + callee methods (when provided by JaCoCo). Only methods with data are included in the average |
| **Coverage Multiplier** | `1/(avg+0.1)` from the average coverage above; 1.0 when not provided |

Each per-method factor (R/D/CC/coverage) reuses the values computed exactly as in the
[method unit](method.en.md).

### Shared-Component Exclusion Rule (`sharedComponentMode`)

A method called in common by two or more endpoints is a [shared component](shared-component.en.md).
Depending on the mode, its treatment in the endpoint sum differs:

| Mode | Shared method included in endpoint sum? | Shared component reported separately? |
|---|:---:|:---:|
| `CUMULATIVE` | Included (redundantly summed for every endpoint) | No |
| `SEPARATE` | **Excluded** (the shared portion is counted once, aggregated separately) | Yes |
| `BOTH` (default) | Included | Yes |

Use `SEPARATE` when you want to see "endpoint-specific risk", and `CUMULATIVE` when you want to see "the total risk reached
when you touch this endpoint". `BOTH` outputs both.

## 4) Score Calculation

```
Simple    = Revisions_agg × LOC_agg
Composite = CC_agg × RecencyDecay_agg × CoverageMultiplier      # default
Composite = CC_agg × RecencyDecay_agg                           # excludeCoverage=true
```

Sorting: Composite descending, ties broken by `route` → `httpMethod`.

## 5) Report Columns / Schema

CSV (`api_hotspots.csv`, or a separate section when combined), default:

```
simple_rank, composite_rank, http_method, route, fqcn, method, parameters,
loc, revisions, simple_score, recency_decay, cognitive_complexity,
coverage_multiplier, composite_score
```

YAML (`api_report.yml`, or `apiHotspots` in `hotspots.yml`) includes, in addition to the fields above,
`callGraph` (the list of reached method signatures). The output location is controlled by
`output.apiLayout` (`COMBINED`|`STANDALONE`|`BOTH`).

## 6) Worked Example

`POST /api/orders` → `OrderController#create(OrderRequest)` calls
`OrderService#place → InventoryService#reserve, PricingService#quote`.

```
# per method (example values)
create:  R=2 LOC=18 D=1.8 CC=3  cov=0.50
place:   R=5 LOC=60 D=4.2 CC=14 cov=0.20
reserve: R=3 LOC=40 D=2.1 CC=9  cov=0.00
quote:   R=1 LOC=25 D=0.6 CC=5  cov=0.80   # quote is a shared component if used by 2+ APIs

# BOTH/CUMULATIVE aggregate (includes quote)
R_agg   = 2+5+3+1 = 11
LOC_agg = 18+60+40+25 = 143
D_agg   = 1.8+4.2+2.1+0.6 = 8.7
CC_agg  = 3+14+9+5 = 31
avg cov = (0.50+0.20+0.00+0.80)/4 = 0.375
M       = 1/(0.375+0.10) = 2.105
Simple    = 11 × 143 = 1573
Composite = 31 × 8.7 × 2.105 ≈ 568

# SEPARATE aggregate (excludes shared component quote)
CC_agg' = 3+14+9 = 26 ;  D_agg' = 1.8+4.2+2.1 = 8.1
avg cov'= (0.50+0.20+0.00)/3 = 0.233 → M' = 1/(0.333) ≈ 3.0
Composite' = 26 × 8.1 × 3.0 ≈ 631   # excluding quote's high coverage makes the risk stand out more
```

## Using It as Input for RestAssured Test Generation

If you read `api_report.yml` and consume it in `compositeRank` order, then per endpoint:

- `httpMethod` + `route` → the information to build a request (RestAssured `given().when().<method>(route)`).
- `parameters` / the controller signature → clues to the shape of the request body and parameters.
- `callGraph` + method coverage → **which sub-logic is unprotected** → which scenarios
  (branch/exception paths) to verify first.
- `compositeRank` → **the order in which to attach tests**.

In other words, it becomes a deterministic priority queue that generates RestAssured tests "starting from the riskiest endpoint,
targeting the most unprotected paths".
