# T4 Completion Report — LocalGitProvider (JGit)

> Task: Implement `LocalGitProvider` that reads commit history and per-file diff stats from a local git working tree via JGit, satisfying the `VcsProviderContract`.
> Status: ✅ Completed (2026-05-21)

## Outcome

| Item | Result |
|---|---|
| Build | `BUILD SUCCESSFUL` |
| Tests | 58 / 58 passed (cumulative: +12 vs T3) |
| New main classes | 1 (`LocalGitProvider`) |
| New test classes | 1 (`LocalGitProviderTest`, 12 tests: 6 contract + 6 JGit-specific) |
| New dependency | `org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r` |

## Test breakdown for LocalGitProviderTest (12)

### Contract (inherited from `VcsProviderContract`)
- `loadCommits returns a non-null list for the full window`
- `loadCommits returns the expected commit count for the full window`
- `loadCommits returns an empty list for a window that matches no commits`
- `each returned commit satisfies the CommitRecord invariants`
- `repeated calls with the same window produce identical results`
- `commits are returned in chronological order (oldest first)`

### JGit-specific
- `rejects a path that is not a directory`
- `rejects a directory that is not a git repository`
- `returns an empty list when the repository has no commits`
- `classifies the initial commit as ADDED with no parent`
- `classifies a follow-up edit as MODIFIED with added/deleted lines counted`
- `filters out commits whose committedAt falls outside the window`

## Key design decisions

| Decision | Choice | Rationale |
|---|---|---|
| Initial commit handling | Walk tree directly, count lines from blob | JGit can't `scan(null, tree)`; produce `ChangeType.ADDED` per file |
| Parent commit | Use `commit.getParent(0)` and re-parse via `walk.parseCommit` | Ensures `parent.getTree()` is populated, not lazy |
| Rename detection | `formatter.setDetectRenames(true)` | Lets JGit emit `RENAME`/`COPY` change types |
| `RENAME` → `previousPath` mapping | New path → `path`, old path → `previousPath` | Matches our `FileChange` record contract |
| `COPY` mapping | Treated as `RENAMED` for now | Rare in practice; Phase 2 may model separately |
| Line counts | `EditList.toEditList()` from `FileHeader` | Same hunk arithmetic as `git diff --numstat` |
| Empty repository handling | `repo.resolve(HEAD) == null` → `List.of()` | Avoids `RevWalk.markStart(null)` NPE |
| Window resolution | UTC day boundaries: `since=00:00 UTC`, `until=23:59:59.999...999 UTC` | Identical formula to `InMemoryVcsProvider`, ensuring contract equivalence |

## Fixture strategy

Tests build a synthetic git repository inside a JUnit `@TempDir`:

```java
commitFile(git, "src/Foo.java", "class Foo {}\n",                "alice", "c1: init",       2026-01-15T10:00:00Z);
commitFile(git, "src/Foo.java", "class Foo {\n    int x;\n}\n",  "bob",   "c2: add field",  2026-01-15T11:00:00Z);
commitFile(git, "src/Bar.java", "class Bar {}\n",                "alice", "c3: add Bar",    2026-01-15T12:00:00Z);
```

All commit times are explicitly pinned via `PersonIdent(name, email, Date.from(timestamp), UTC)` so windowing tests are 100% deterministic.

## Notable JGit pitfalls navigated

1. **`GitAPIException` not thrown**: removed from `catch` clause (compile-time check by `javac`).
2. **Import collision**: `java.time.Comparator` does **not** exist — must use `java.util.Comparator`.
3. **`walk.parseCommit` for parents**: `commit.getParent(0)` returns a `RevCommit` whose tree may be unparsed; explicit re-parse is required before tree-walking.

## Algorithm summary

```
For each reachable commit from HEAD:
  Read commit time from commit.getCommitTime() (epoch seconds).
  If outside [lowerBound, upperBound], skip.
  If commit has no parent:
    Walk commit.tree, emit ADDED FileChange per blob with line count.
  Else:
    Diff parent.tree vs commit.tree with rename detection.
    For each DiffEntry:
      Sum edit hunks → linesAdded, linesDeleted.
      Map JGit ChangeType → our ChangeType.
      Emit FileChange.

Sort collected commits by committedAt (ascending). Return immutable list.
```

## Next step

T5 — `GithubProvider` using `org.kohsuke:github-api`. Same `VcsProviderContract` will be re-applied. WireMock will be used to stub the GitHub REST API so the test is hermetic (no network) yet exercises the real HTTP client.
