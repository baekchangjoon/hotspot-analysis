# T9 Completion Report — HotspotAnalyzer (Orchestration)

> Task: Wire every component built in T1–T8 into a single end-to-end pipeline that turns an `AnalysisConfig` into a ranked `AnalysisResult`.
> Status: ✅ Completed (2026-05-21)

## Outcome

| Item | Result |
|---|---|
| Build | `BUILD SUCCESSFUL` |
| Tests | 112 / 112 passed (cumulative: +6 vs T8) |
| New main classes | 8 (4 domain records + `VcsProviderFactory` + `JavaSourceCollector` + `SourceScanException` + `HotspotAnalyzer`) |
| New test class | 1 (`HotspotAnalyzerTest`, 6 end-to-end tests using JGit fixture) |

## End-to-end test suite (6)

| Test | What it asserts |
|---|---|
| `shouldRankMostEditedFileFirst` | The hot file (3 commits) ranks above the cold file (1 commit) |
| `shouldComputeSimpleScoreCorrectly` | `score == revisions × loc` to the digit |
| `shouldApplyTopNLimit` | `output.topN` truncates both `fileHotspots` and `methodHotspots` |
| `shouldEmitMethodHotspots` | Method-level rows carry correct signature, file path, line range, LOC |
| `shouldRespectExcludeGlob` | `scope.exclude` removes `**/test/**` files from results |
| `shouldRejectGithubTargetInPhase1` | `github` target raises `UnsupportedOperationException` with clear remediation |

## Pipeline summary

```
AnalysisConfig
    │
    ▼
┌───────────────────────┐
│ VcsProviderFactory    │ ── picks LocalGitProvider or GithubProvider
└──────────┬────────────┘
           ▼
   List<CommitRecord>            <-- loadCommits(window)
           │
           ▼
┌───────────────────────┐
│ JavaSourceCollector   │ ── glob include/exclude over repo working tree
└──────────┬────────────┘
           ▼
       List<Path>
           │
           ▼
┌───────────────────────┐
│ JavaSourceParser×N    │ ── per-file method extraction (T6)
└──────────┬────────────┘
           ▼
Map<file, List<MethodInfo>>
           │
           ▼ (commits + methodsByFile)
┌───────────────────────┐    ┌───────────────────────┐
│ RevisionsCalculator   │    │ LocCalculator         │
└──────────┬────────────┘    └──────────┬────────────┘
           │                            │
           ▼                            ▼
   Map<file|sig, revs>         Map<file, loc>
           │                            │
           └────────────┬───────────────┘
                        ▼
            ┌───────────────────────┐
            │ HotspotScoreCalculator │ ── SIMPLE formula = revisions × loc
            └───────────┬────────────┘
                        ▼
   Sort by score desc, apply topN, build AnalysisMeta
                        ▼
                AnalysisResult
```

## Key design decisions

| Decision | Choice | Rationale |
|---|---|---|
| Domain records | `FileHotspot`, `MethodHotspot`, `AnalysisMeta`, `AnalysisResult` | Immutable, easy to serialise in T10 (CSV/YAML/MD) |
| Provider factory | Dedicated `VcsProviderFactory` | Encapsulates the LOCAL_GIT/GITHUB branching; isolates Spring DI from runtime decisions |
| Source scanning | `Files.walk` + `PathMatcher` glob | Stdlib only; no external file-system traversal lib |
| Path normalisation | Always relative POSIX (`replace('\\', '/')`) | Windows-safe and what every output format expects |
| Sorting | Score desc, tiebreak by path/signature lexicographically | Stable, reproducible outputs across runs |
| Tiebreaker on equal score | Path / canonical signature | Snapshot tests in T10 will be deterministic |
| GitHub target | Throw `UnsupportedOperationException` with clear hint | Honest about Phase 1 scope; better than silently producing wrong output |
| Constructor DI | All six collaborators injected | Plays cleanly with Spring Boot tests + manual `new HotspotAnalyzer(...)` in unit tests |
| Time measurement | `Instant.now()` recorded in `AnalysisMeta.analyzedAt` | Useful for traceability in CI; ignored in snapshot tests |

## Sample test output

`shouldRankMostEditedFileFirst` produces (top of `fileHotspots`):

```
[0] src/main/java/com/example/Hot.java   revisions=3 loc=3 score=9.0
[1] src/main/java/com/example/Cold.java  revisions=1 loc=1 score=1.0
```

## Counter-arguments considered

| Alternative | Why rejected |
|---|---|
| Single god-class for everything | Untestable; T8 already proved each calculator can be unit-tested in isolation |
| Spring `@Service` + Spring Boot integration test | Heavier than needed; constructor `new HotspotAnalyzer(...)` keeps the test fixture readable |
| Producer–consumer streaming (lazy) | Adds complexity (back-pressure); for repos with <10k commits the eager pipeline is fast enough |

## Phase 1 limitations (documented; addressed later)

1. **GitHub target end-to-end**: not yet supported in the analyzer. Will be added by a `GithubRepositoryFetcher` that `git clone`s into a temp dir, then re-uses `LocalGitProvider`.
2. **Working-tree LOC only**: `LocCalculator` reads the file at HEAD. Historical LOC at the time of a past commit is not computed. Same simplification Tornhill makes.
3. **No incremental mode**: every run re-walks the repo and reparses everything. Phase 3 (backend service) will add caching.

## Next step

T10 — Output writers (CSV, YAML, MD) with snapshot tests. The `OutputWriter` interface will emit `AnalysisResult` to any subset of `OutputConfig.OutputFormat`, with deterministic ordering already established by T9.
