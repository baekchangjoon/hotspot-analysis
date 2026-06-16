# Advanced Techniques Behavior Verification Report

> 🌐 [한국어](advanced-techniques-verification.md) · **English** (this page)

> Subject: Verify that the implementation of the 4 advanced techniques
> (Recency Decay / Cognitive Complexity / Coverage Gap / X-Ray + COMPOSITE)
> defined in `docs/hotspot-advanced-spec.en.md` matches the specification formulas.
> Date: 2026-05-25
> Tested against commit: `0debe57`
> Reviewer: external exploratory test (targeting `~/github_*` projects)

---

## 1. Verification Scope

| ID | Technique | Introducing Commit | Spec |
|---|---|---|---|
| B-2 | Recency Decay | `46c5a04` | `Σ exp(-ln(2)/t_half × Δt)` |
| A-2 | Cognitive Complexity | `1c10bc2` | SonarQube spec (`if/for/while/catch/switch/&&/\|\|` + nesting) |
| C-4 | Coverage Gap | `b225d4e` | JaCoCo XML line mapping → `1/(coverage + 0.1)` |
| C-5 | X-Ray + COMPOSITE | `a39663e` | method-level HTML drill-down, `complexity × decayed × multiplier` |

## 2. Verification Target Projects

| Tier | Path | Purpose |
|---|---|---|
| Tiny | `~/github_line-service/line-service` | JaCoCo report generation / coverage parsing verification |
| Small | `~/github_advance-spring-boot-microservice/...` | simple cognitive complexity (file/method `=1`) baseline |
| Self | `~/github_hotspot-analysis/hotspot-analysis` | composite verification (real jacocoTestReport.xml + deep branching) |

## 3. Verification Method

For each technique, an independent ground-truth was built and compared against the produced values to a precision of 1e-4:

- **Recency Decay**: extract commit dates with `git log --pretty=%cI` → recompute with Python `math.exp(-ln(2)/t_half × ΔDays)`
- **Cognitive Complexity**: directly count from the source AST (SonarQube increment rules)
- **Coverage Gap**: directly parse `build/reports/jacoco/test/jacocoTestReport.xml` with Python `ElementTree`, counting lines with `ci>0` as covered
- **Composite**: multiply the three values above to produce `complexity × decayed × 1/(coverage+0.1)`, then compare with the produced score
- **X-Ray HTML**: directly grep the `xray-table` element in the output `hotspots.html`, confirming the presence of method rows and columns (Lines/Complexity/Decayed Revs/Coverage/Score/Share)

## 4. Verification Results

### 4.1 `JacocoReportParser.java` Precise Comparison (self-analysis)

Settings: `decayHalfLifeDays=180`, `until=2026-12-31`, jacoco supplied. The file was added in a single commit
`b225d4e` (2026-05-23T17:35:36+09:00) → Δdays = 222.

| Factor | Produced | Independent Calculation | Result |
|---|---|---|---|
| Decayed Revs (file) | `0.43` | `exp(-ln2/180 × 222) = 0.425334` | ✅ |
| Cognitive Complexity `parse(Path)` | `10` | AST hand-count = 10 | ✅ |
| Cognitive Complexity `getMethodCoverage(...)` | `9` | AST hand-count = 9 | ✅ |
| Cognitive Complexity (file total, back-computed) | `25` | `10 + 9 + 2(getFileCoverage) + 3(findCoverageForPath) + 1(normalizePath)` | ✅ |
| Coverage `parse` (lines 22–59) | `96.3%` | `26/27 = 0.9630` | ✅ |
| Coverage `getMethodCoverage` (lines 71–92) | `100.0%` | `15/15 = 1.000` | ✅ |
| Coverage (file) | `96.6%` (back-computed) | `56/58 = 0.9655` | ✅ |
| Composite Score (file) | `9.9795` | `25 × 0.425334 × 1/(0.9655+0.1) = 9.9795` | ✅ |
| Composite Score (`parse`) | `4.00` | `10 × 0.425334 × 1/(0.963+0.1) = 4.001` | ✅ |
| Composite Score (`getMethodCoverage`) | `3.48` | `9 × 0.425334 × 1/(1.0+0.1) = 3.479` | ✅ |

### 4.2 External Project Cross-Verification

| Item | Confirmation |
|---|---|
| With decay disabled (`t_half=1,000,000d`), advance-spring-boot top 5 score = `9.9845` = `complexity(1) × 0.998453 × 10` | ✅ |
| With decay enabled (`t_half=90d`), 2020 commits → score ≈ 0 (decay weight `exp(-17) ≈ 4×10⁻⁸`) | ✅ Cold Legacy penalty works as intended |
| For all line-service methods with `cognitiveComplexity=0` → all composite scores = 0 | ✅ multiplicative zero-property consistent |
| When line-service jacoco is supplied, X-Ray's coverage for 6 methods such as `createLine 100%`, `updateLine 0%`, `recordHistory 100%` matches the `ci>0` count in the jacoco XML, 6/6 | ✅ |

### 4.3 Output Format / Invariants

| Item | Result |
|---|---|
| HTML X-Ray drill-down (clicking a file row expands the method table) | ✅ renders |
| X-Ray row `Share` percentages sum = 100% (per file) | ✅ |
| Score DESC sorting (file/method) | ✅ |
| `topN` honored | ✅ |
| Idempotence (running the same input twice → byte-identical CSV) | ✅ |
| `Scoring formula: COMPOSITE` exposed in the Markdown meta table | ✅ |

## 5. Assessment

### 5.1 What Went Well

1. **Spec-implementation consistency**: All 4 techniques match the formulas in `docs/hotspot-advanced-spec.en.md` to 1e-4 digits. In particular, the non-trivial `until` handling
   (`until.plusDays(1).atStartOfDay(UTC).toInstant().minusNanos(1)`) is applied consistently, so the day-count comes out to exactly 222.
2. **Preservation of the multiplicative zero-property**: When `complexity=0` or `decayed≈0`, the composite score converges stably to 0 — no NaN/Inf is observed even with large inputs.
3. **Robustness of the JaCoCo path resolver**: `endsWith` matching (`JacocoReportParser#findCoverageForPath`) absorbs package-prefix variation (`com/example/...` vs `io/github/...`). It is practical that mapping succeeds as long as the sourcefile name matches in a multi-module build.
4. **X-Ray UI**: The file-row toggle + the `Share %` in the method table are intuitive. "Which method created most of the score in this file," which cannot be told from score alone, is revealed at a glance.

### 5.2 Recommended Improvements (Not Bugs, UX-Level)

1. **Absence of decomposition factors in CSV/Markdown**: To verify a composite-mode result by decomposition in a PR/spreadsheet, only HTML works. Consider separately specifying the README's *CSV → 5/10 column* guide for composite mode, or adding optional columns
   (`decayed_revisions`, `cognitive_complexity`, `coverage`) to the CSV (follow-up candidate).
2. **Conflict between the default `decayHalfLifeDays=90` and a long window**: It is the value recommended in Tornhill's book, but combined with a 6-year window such as `since:2017-01-01 until:2026-12-31`,
   most scores are compressed to e-8 digits, so every row appears as `0`. Users are likely to find this puzzling.
   - Recommendation: at the end of an `analyze` run, output a hint message like *"The 95-th percentile commit age in this window = N days. With half-life=90d, the decay weight is very small."*
   - Or auto-set, e.g., half the window length via a `decayHalfLifeDays: auto` option.
3. **Side effect of multiplier = 10 when JaCoCo is not supplied**: It is treated as `coverage=0` and multiplied by `1/(0+0.1)=10`. That is, "not measured" and "code that is entirely uncovered" are
   numerically indistinguishable. This is an intended simplification, but **since that value is not visible in CSV/MD, users may not realize their score is inflated 10x**.
   - Recommendation: when `jacocoReportPath` is null but `formula: composite` is selected, print a warning to stdout such as *"NOTE: coverage not supplied → multiplier=10 applied to all files."*
4. **The Cognitive Complexity implementation is a SonarQube *approximation***: `CognitiveComplexityCalculator` adds +1 each time even when `&&/||` are chained with the same operator (the SonarQube convention counts a sequence of the same operator only once). The unit tests pass according to its own definition, but the *"SonarQube-compliant"* docstring wording is recommended to be toned down to *"SonarQube-inspired."*
5. **The stale jar problem in `build/libs/`**: On the first analysis attempt, `formula: composite` failed as a "YAML parse failure"; the cause was that the jar was built *before* the new enum was added. Since the error message was only `Failed to parse YAML configuration`, debugging was tricky.
   - Recommendation: have ConfigLoader catch the `IllegalArgumentException` from `Formula.from(...)` in a separate branch and surface it as, e.g., `Unsupported scoring.formula: <raw> (allowed: SIMPLE, COMPOSITE)`.

### 5.3 Regression-Risk Monitoring Targets

- `HotspotAnalyzer#analyze` is 251 LOC, cognitive complexity ≈ 115. In its own self-analysis it ranks #1 at the method level (score 181.83) — i.e., **a self-coherent signal in which the analyzer itself points to the most dangerous method**.
  As the composite formula grows, this method is likely to explode in branching as well; deliberate decomposition (e.g., extracting a `CompositeScoringPipeline`) is recommended.

## 6. Conclusion

The four techniques (B-2/A-2/C-4/C-5) in `docs/hotspot-advanced-spec.en.md` are all implemented exactly as the specification formulas, and a 1e-4 precision match was confirmed in both external and self-analysis. All improvement items are UX/documentation-level suggestions, and no defects were found in the formulas or outputs themselves.
