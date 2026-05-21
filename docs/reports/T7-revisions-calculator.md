# T7 Completion Report — RevisionsCalculator

> Task: Count revisions per file and per method by intersecting commit diff hunks with method line ranges.
> Status: ✅ Completed (2026-05-21)

## Outcome

| Item | Result |
|---|---|
| Build | `BUILD SUCCESSFUL` |
| Tests | 94 / 94 passed (cumulative: +14 vs T6) |
| New main classes | 2 (`DiffHunk` record, `RevisionsCalculator`) |
| Modified main classes | 2 (`FileChange` adds `hunks` field, `LocalGitProvider` populates hunks from `EditList`) |
| New test classes | 2 (`DiffHunkTest`, `RevisionsCalculatorTest`) |

## Test distribution

| Class | Tests | Highlights |
|---|---|---|
| `DiffHunkTest` | 5 | Construction limits + overlap algebra |
| `RevisionsCalculatorTest` | 9 | File-level × 3, hunk-aware method-level × 3, fallback × 3 |
| (regression) every other test | 80 | All still pass after `FileChange` got a new field |

## Algorithm

```
fileRevisions[path] = number of commits that contain at least one FileChange where change.path == path

For methodRevisions[signature]:
  Initialise every method in methodsByFile to 0.
  For each commit C:
    creditedInThisCommit = {}    # set
    For each change in C.changes where change.path ∈ methodsByFile:
      If change.hunks is non-empty:
        For each (hunk, method) where hunk.overlaps(method.startLine, method.endLine):
          If method.signature ∉ creditedInThisCommit:
            creditedInThisCommit.add(method.signature)
            methodRevisions[method.signature] += 1
      Else:
        # Fallback path used by GithubProvider (no hunk data in Phase 1)
        For each method in methodsByFile[change.path]:
          If method.signature ∉ creditedInThisCommit:
            creditedInThisCommit.add(method.signature)
            methodRevisions[method.signature] += 1
```

This matches `git log --oneline --follow -- <path> | wc -l` semantics: each commit contributes at most one increment per artifact.

## Key design decisions

| Decision | Choice | Rationale |
|---|---|---|
| Hunk model | New record `DiffHunk(newStart, newEnd)`, 1-based inclusive on both ends | Matches `git log -L` and JavaParser's `Range` |
| Delete-only hunks | Dropped at provider layer | `endB == beginB` produces no new-file range; small accuracy cost at method granularity |
| Multi-change-same-path within one commit | Deduplicated → counts as 1 revision | Matches `git log -- <path>` |
| Multi-hunk same-method within one commit | Credited at most once | Same reasoning: a commit is one revision unit |
| Hunks-absent fallback (GithubProvider) | File-level credit applied to every method in the file | Better than dropping the data entirely; still useful for ranking |
| FileChange constructor | Added 5-arg convenience that defaults `hunks=[]` | Keeps every existing call site (incl. `GithubProvider`, all unit tests) backward-compatible |
| `Component` | `@Component` | Auto-wires into `HotspotAnalyzer` in T9 |

## DiffHunk extraction from JGit

The change in `LocalGitProvider.toFileChange`:

```19:29:src/main/java/io/github/baekchangjoon/hotspotanalysis/vcs/LocalGitProvider.java
        int added = 0;
        int deleted = 0;
        List<DiffHunk> hunks = new ArrayList<>();
        for (Edit edit : edits) {
            added += edit.getEndB() - edit.getBeginB();
            deleted += edit.getEndA() - edit.getBeginA();
            if (edit.getEndB() > edit.getBeginB()) {
                hunks.add(new DiffHunk(edit.getBeginB() + 1, edit.getEndB()));
            }
        }
```

Edit.getBeginB() / getEndB() are 0-based half-open; converting to 1-based inclusive ⇒ `(begin + 1, end)`.

For the initial commit (no parent), `changesFromEmpty` now emits one hunk per file covering the whole content:

```javascript
DiffHunk(1, totalLines)  // every line is "added"
```

## Counter-arguments considered

| Alternative | Why rejected |
|---|---|
| Drop method-level revisions entirely in Phase 1 | Loses major hotspot signal; defeats the design |
| Keep hunk data only in tests, fake it in production | Worse than real data — won't catch real regressions |
| Use `git log -L <start>,<end>:<file>` shell-out | Forces shell dependency; slow on big repos; brittle on path quoting |

## Phase 1 limitations (documented; addressed in Phase 2)

1. **GitHub patch parsing**: `GithubProvider` does not parse the unified-diff `patch` field returned by GitHub's commits API, so method-level resolution falls back to file-level. Will be added when we wire `kohsuke github-api`'s `GHCommit.File#getPatch()` parser.
2. **Delete-only hunks**: Edits that only remove lines do not produce a `DiffHunk` and are not credited at the method level. Accuracy loss is small for hotspot ranking.
3. **Cross-rename tracking**: Renames are recorded but the calculator does not currently chain "Old.java → New.java" revisions. Phase 2 will introduce a path-history pass.

## Next step

T8 — `LocCalculator` and `HotspotScoreCalculator`. LOC for files = current line count from working tree (or git-ls-files for the right ref); LOC for methods = `MethodInfo.lineCount()`. Score formulas from `phase1-design.md` § Scoring (`SIMPLE = revisions × LOC`).
