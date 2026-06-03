# Method level (method)

> 🌐 [한국어](method.md) · **English** (this page)

For the shared factor and score definitions, read the [scoring reference](README.en.md)
first. This document only covers how the **method level** computes and aggregates
those factors.

Sources: `HotspotAnalyzer.buildMethodHotspots()`, model
`analysis/model/MethodHotspot.java`, extraction `parser/JavaSourceParser` →
`parser/model/MethodInfo.java`.

A method is identified by its canonical signature `MethodSignature(fqcn, methodName, parameterTypes)`.
`startLine`/`endLine` is the 1-based inclusive range from the declaration to the closing brace.

## Factor computation

| Factor | How the method level computes it |
|---|---|
| **Revisions** | If a commit's diff hunk (the line range in the new file) **overlaps** the method's `[startLine,endLine]`, +1 (once per commit). If there is no hunk, falls back to +1 for every method in the file. `calculateMethodRevisions` |
| **LOC** | `endLine - startLine + 1`. `MethodInfo.lineCount()` |
| **Recency Decay** | `Σ exp(-λ·Δt)` over the "overlapping commits" above. `calculateMethodDecayedRevisions` |
| **Cognitive Complexity** | The CC of that method body (not a sum). `CognitiveComplexityCalculator.calculate` |
| **Coverage** | `covered/instrumented` within the method's line range. `getMethodCoverage(path,start,end)` |
| **Coverage Multiplier** | `1/(coverage+0.1)`, or 1.0 when not provided |

> **Hunk overlap is the key**: even when the same file changes, only the methods
> whose line range actually changed earn Revisions/Decay. This is why we can pinpoint
> exactly "the method you touch often" rather than the whole file. (This assumes the
> `local-git` provider, which supplies hunks. Without hunks it is approximated at the
> file level.)

## Score computation

```
Simple    = Revisions × LOC
Composite = CC × RecencyDecay × CoverageMultiplier      # default
Composite = CC × RecencyDecay                           # excludeCoverage=true
```

## Report columns (CSV `method_hotspots.csv` — 14 columns)

Default (coverage included):

```
simple_rank, composite_rank, fqcn, method, parameters, file,
start_line, end_line, loc, revisions, simple_score, recency_decay,
cognitive_complexity, coverage_multiplier, composite_score
```

When `excludeCoverage=true`, the last two columns are `composite_score, line_coverage`.
`parameters` is joined with `;`.

## Worked example

`OrderService#applyDiscount(Order, Coupon)`: `startLine=88, endLine=140`,
3 commits within the window overlap this range (all recent), CC=12, JaCoCo method coverage=0.0
(no tests for this method).

```
LOC       = 140 - 88 + 1 = 53
Revisions = 3
Simple    = 3 × 53 = 159
D ≈ 2.7                       # sum of the 3 recent commits' weights (example value)
M         = 1/(0.0+0.1) = 10  # untested → max penalty
Composite = 12 × 2.7 × 10 = 324
```

Interpretation: it is unremarkable by Simple, but it is the classic risky method —
"recently changed often (D), complex (CC), and with no tests at all (M=10)" — so its
Composite spikes. A method like this is exactly the candidate to attach unit tests to first.
