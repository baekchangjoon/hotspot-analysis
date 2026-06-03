# Scoring Reference

> 🌐 [한국어](README.md) · **English** (this page)

This document explains how hotspot-analysis measures its **four input factors**
(Revisions, Recency Decay, Cognitive Complexity, Coverage) and how it computes
them into **two scores** (Simple, Composite), broken down by granularity.

This document (the index) gathers the **definitions and formulas common to all
granularities** in one place. For how each granularity **aggregates** these
factors and which **report columns** it emits, see the per-granularity
documents:

- [file](file.en.md)
- [method](method.en.md)
- [REST API endpoint (rest-api-endpoint)](rest-api-endpoint.en.md)
- [shared component (shared-component)](shared-component.en.md)

All formulas are deterministic. Given the same input (same commit range, source,
and JaCoCo report), they always produce the same scores and the same ordering.
There is no randomness and no time dependence (except for the `until` reference
point below).

---

## Analysis pipeline (at a glance)

`HotspotAnalyzer.analyze()` (`src/main/java/.../analysis/HotspotAnalyzer.java`):

1. Load the commits within `window` via the VCS provider.
2. Collect Java files via `scope.include/exclude` → extract methods with JavaParser.
3. Compute **Revisions**, **LOC**, **Recency Decay**, **Cognitive
   Complexity**, and (optionally) **Coverage** per file/method.
4. Synthesize **Simple** and **Composite** scores with `HotspotScoreCalculator`.
5. If `apiAnalysis.enabled`, build the call graph and additionally aggregate the
   **API endpoint** and **shared component** granularities.
6. Sort by Composite descending → apply `output.topN` → emit the report.

---

## Input factor 1 — Revisions (R)

The **number of commits that touched** the given artifact within the window. Even
if a single commit changes the same target multiple times, it counts as **1**
(i.e. the meaning of `git log --oneline -- <path> | wc -l`).

Source: `RevisionsCalculator` (`analysis/RevisionsCalculator.java`).

- **File**: +1 if the file is in that commit's set of `FileChange` paths.
- **Method**: +1 if the new-file line range of a diff hunk **overlaps** the
  method's `[startLine, endLine]` (at most once per commit). When hunk
  information is unavailable (e.g. the GitHub provider), it falls back to +1 for
  all methods in the file.

```
R(artifact) = | { c ∈ window : c touched artifact } |
```

## Input factor 2 — Recency Decay (D)

Counts the same "touching commits" as Revisions, but **gives greater weight to
more recent commits**. Each commit's weight is an exponential decay over the
number of days elapsed from the reference point `until`.

Source: `RevisionsCalculator.calculate*DecayedRevisions`.

```
λ      = ln(2) / halfLifeDays          # scoring.decayHalfLifeDays, default 90
Δt(c)  = max(0, days_between(c.committedAt, until))
weight(c) = exp(-λ · Δt(c))
D(artifact) = Σ_{c touched artifact} weight(c)
```

- `until` = the end of that day if `window.until` is set, otherwise the analysis
  run time (now).
- A commit as old as one half-life has weight 0.5, two half-lives ago 0.25, and
  so on.
- The method/file attribution rules (once per commit, hunk overlap) are the same
  as for Revisions.

> Intuition: with `halfLifeDays=90`, "one change within the last 90 days" carries
> twice the weight of "one change 180 days ago." Legacy code that hasn't changed
> in a long time gets a small D, while places that are actively changing right
> now get a large D.

## Input factor 3 — Cognitive Complexity (CC)

Computes SonarQube's Cognitive Complexity by traversing the method body's AST.
It is an approximation of "how hard it is to read and understand" (different from
Cyclomatic, which is simply the number of branches).

Source: `CognitiveComplexityCalculator` (`parser/CognitiveComplexityCalculator.java`).

Increment rules:

| Construct | Increment | Nesting increase |
|---|---|---|
| `if` / `for` / `for-each` / `while` / `do` / `catch` | `1 + current nesting depth` | +1 |
| `switch` / ternary (`? :`) | `1` | +1 |
| labeled `break` / `continue` | `1` | — |
| binary `&&` / `\|\|` | `1` | — |

- The deeper the nesting, the larger the penalty for the same construct (a nested
  `if` costs `1+depth`).
- Methods with no body (abstract/interface) are 0.
- **File CC** = the **sum** of the CC of all methods belonging to that file.

## Input factor 4 — Coverage and Coverage Multiplier (M)

Enabled only when a JaCoCo XML (`analysis.jacocoReportPath`) is provided. A line
is considered "covered" if its `ci` (covered instructions) > 0.

Source: `JacocoReportParser`, `HotspotScoreCalculator.multiplier`.

```
coverage(file)   = (covered lines) / (instrumented lines)        # whole file
coverage(method) = (covered lines in method range) / (instrumented lines in range)
M = 1 / (coverage + 0.1)                                          # coverage multiplier
```

- Range: coverage ∈ [0,1] → **M ∈ (0.0→10, 1.0→0.909)**. The lower the coverage,
  the larger M becomes, pushing up the Composite (weighting an alarm for risky
  zones not protected by tests).
- **JaCoCo not provided**: M = 1.0 (no coverage penalty), and `lineCoverage` is
  `null`.
- **JaCoCo provided but no data for that file/method**: coverage is treated as
  0.0 → M = `1/0.1 = 10` (maximum penalty). This means that if the measured
  target has no test report, it is treated as "unprotected," so make sure the
  JaCoCo path points to a report from the same build as the analysis target.

### The `excludeCoverage` switch

If `scoring.excludeCoverage: true`, coverage is **excluded from the score** and
shown for observation only:

- Composite = `CC × D` (M is not multiplied in).
- The rightmost report column becomes the **raw `line_coverage` (%)** instead of
  `coverage_multiplier`.

---

## Derived scores

Source: `HotspotScoreCalculator` (`analysis/HotspotScoreCalculator.java`).

### Simple Score — the Adam Tornhill original

```
Simple = Revisions × LOC
```

- **LOC**: for a file = the line count of the file at HEAD (no comment/blank
  stripping, `LocCalculator`). For a method = `endLine - startLine + 1`
  (including the declaration through the closing brace).
- A first-pass signal that quickly points to large, frequently changed files.

### Composite Score — this project's core signal

```
Composite = Cognitive Complexity × Recency Decay × Coverage Multiplier
          = CC × D × M
# if excludeCoverage=true:
Composite = CC × D
```

- A place that is simultaneously high in "**complex** (CC) × **actively changing
  right now** (D) × **not blocked by tests** (M)" rises to the top — the
  intersection where bugs cluster.

---

## Ordering and determinism

Every granularity is sorted by **Composite descending**, with ties broken by a
stable key:

| Granularity | Tie-breaker |
|---|---|
| File | `path` |
| Method | canonical signature (`fqcn#method(params)`) |
| API endpoint | `route` → `httpMethod` |
| Shared component | canonical signature |

If `output.topN > 0`, only the top N are kept after sorting (0 means all).

The report carries **both** `simpleRank` (rank by Simple) and `compositeRank`
(rank by Composite, = the output row order), so you can see at a glance where the
two signals diverge.
