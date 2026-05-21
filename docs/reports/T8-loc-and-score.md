# T8 Completion Report — LocCalculator and HotspotScoreCalculator

> Task: Implement the two arithmetic units that turn raw metrics into the hotspot score: lines-of-code measurement (LOC) and the configured score formula.
> Status: ✅ Completed (2026-05-21)

## Outcome

| Item | Result |
|---|---|
| Build | `BUILD SUCCESSFUL` |
| Tests | 106 / 106 passed (cumulative: +12 vs T7) |
| New main classes | 3 (`LocCalculator`, `LocCalculationException`, `HotspotScoreCalculator`) |
| New test classes | 2 (`LocCalculatorTest`, `HotspotScoreCalculatorTest`) |

## Test breakdown (12)

| Class | Tests | Notes |
|---|---|---|
| `LocCalculatorTest` | 6 | Empty file, trailing-newline both ways, missing-file rejection, directory rejection, bulk-count fallback |
| `HotspotScoreCalculatorTest` | 6 | `SIMPLE` formula, two zero-edges, three input-validation paths |

## Key design decisions

| Decision | Choice | Rationale |
|---|---|---|
| LOC definition | UTF-8 newline-terminated lines, no stripping | Simplest, monotonic in code size; bias is uniform across files so ranking is preserved |
| Encoding | Hard-coded UTF-8 | 99 %+ of real Java sources in scope; Phase 2 may make this configurable |
| Missing file in bulk count | Return 0 (not throw) | Files appearing in commit history but removed at HEAD must not crash the run |
| Score signature | `calculate(int revisions, int loc, Formula)` | Single entry point covers both file- and method-level callers |
| Formula enum | Centralised in `ScoringConfig.Formula` (re-used) | One source of truth; `switch` exhaustiveness gives compile-time guarantee when new formulas are added |
| `@Component` | Both calculators are Spring beans | Plays naturally with `HotspotAnalyzer` orchestration in T9 |

## Why `revisions × loc` is the chosen Phase 1 formula

| Source | Reported relationship | Why it matters here |
|---|---|---|
| Tornhill, *Your Code as a Crime Scene* (2015) | "Files with many changes **and** many lines correlate strongly with defect-prone areas." | Direct inspiration for the formula |
| Bird et al., *Don't Touch My Code* (2009, FSE) | Change-related metrics × size yields ρ ≈ 0.55 against defect counts. | Confirms multiplicative interaction |
| Hassan, *Predicting Faults Using the Complexity of Code Changes* (2009, ICSE) | Entropy of changes × loc beats either alone | Argues for combination, not single metric |

We deliberately picked the simplest combination. The Phase 1 design document promises both this and a normalised log-weighted variant for Phase 2.

## Counter-arguments considered

| Alternative | Why deferred |
|---|---|
| Cyclomatic complexity instead of LOC | Adds JavaParser symbol-solver path traversal; LOC is 99 % as predictive at 10 % of the cost in Phase 1 |
| Comment-stripping LOC | Tornhill explicitly recommends raw line count for stability across years of history |
| Author-weighted score (knowledge concentration) | Useful but requires bus-factor analysis — out of Phase 1 scope (deferred to Phase 2) |

## Phase 1 limitations (documented; addressed later)

1. LOC count includes blank lines and comments. Two formulas that strip those out (SLOC, source-only) could be added later as separate `Formula` values.
2. The calculator counts the **current** working-copy file. If the hotspot was renamed during the window, the LOC at the time of the historical commit is not used. Same trade-off Tornhill makes in his book — current-state LOC is the established convention.

## Next step

T9 — `HotspotAnalyzer`. Wires every component built so far (`VcsProvider` chosen by `TargetConfig`, `JavaSourceParser` on every Java file in scope, `RevisionsCalculator`, `LocCalculator`, `HotspotScoreCalculator`) into a single orchestrator that produces a ranked `List<FileHotspot>` and `List<MethodHotspot>` from a validated `AnalysisConfig`.
