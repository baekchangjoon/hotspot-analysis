# Shared component level (shared-component)

> 🌐 [한국어](shared-component.md) · **English** (this page)

For the shared factor and score definitions, read the [scoring reference](README.en.md);
for endpoint aggregation context, read the
[REST API endpoint](rest-api-endpoint.en.md) first.

A **shared component** is a method called in common from the call graphs of
**two or more distinct API endpoints** (typically a service/repository method).
Because multiple endpoints depend on it, a break here has a **wide blast radius**.

Sources: `HotspotAnalyzer.buildApiAndShared()` (shared determination and aggregation), model
`analysis/model/SharedComponentHotspot.java`.

## Determination

During call-graph aggregation, we count **how many APIs call each callee method**
(`callingApisMap`). If **two or more** APIs call it, it is a shared component.

```
sharedComponent(m) ⇔ | { api : m ∈ api.callGraph } | ≥ 2
```

The shared-component list is generated only when `sharedComponentMode` is `SEPARATE`
or `BOTH` (under `CUMULATIVE` there is no separate list; it is absorbed into each
endpoint's score). For how each mode treats endpoint summation, see the
[table in the endpoint document](rest-api-endpoint.en.md#shared-component-exclusion-rule-sharedcomponentmode).

## Factor computation

A shared component uses the factors **of the method itself** (it does not sum like an
endpoint does). The values are computed the same way as the [method level](method.en.md):

| Factor | Computation |
|---|---|
| **Revisions** | The number of commits that touched the method (hunk overlap) |
| **LOC** | `endLine - startLine + 1` |
| **Recency Decay** | The method's `Σ exp(-λ·Δt)` |
| **Cognitive Complexity** | The CC of that method body |
| **Coverage / Multiplier** | `1/(cov+0.1)` from the method coverage, or 1.0 when not provided |
| **callingApis** | The list of endpoints that call this method (`"GET /a"`, `"POST /b"` …) |

## Score computation

```
Simple    = Revisions × LOC
Composite = CC × RecencyDecay × CoverageMultiplier      # default
Composite = CC × RecencyDecay                           # excludeCoverage=true
```

Sorting: Composite descending, ties broken by canonical signature.

## Report columns

CSV (`shared_components.csv` or an integrated section), default:

```
simple_rank, composite_rank, fqcn, method, parameters,
loc, revisions, simple_score, recency_decay, cognitive_complexity,
coverage_multiplier, composite_score, calling_apis
```

When `excludeCoverage=true`, the order is `composite_score, calling_apis, line_coverage`.
`calling_apis` is joined with `;`. YAML/MD/HTML carry the same fields.

## Worked example

`PricingService#quote(Sku, Customer)` is called from three endpoints `GET /api/price`,
`POST /api/orders`, and `POST /api/quotes`. CC=5, LOC=25, recent changes give D=1.4,
coverage=0.80.

```
callingApis = ["GET /api/price", "POST /api/orders", "POST /api/quotes"]  # 3 → shared
Simple    = Revisions × LOC = 1 × 25 = 25
M         = 1/(0.80+0.10) = 1.111
Composite = 5 × 1.4 × 1.111 ≈ 7.8
```

Interpretation: even though the score itself is low, the key signal is that **3 APIs
depend on it** (`callingApis`). A shared component, once firmly guarded with unit tests,
protects multiple endpoints at once, making it the candidate with the **largest protection
coverage per test investment**. Conversely, if a shared component's coverage is low (M↑),
its priority should be raised.
