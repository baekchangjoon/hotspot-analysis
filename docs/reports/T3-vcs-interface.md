# T3 Completion Report — VCS Provider Interface and Domain Model

> Task: Define the source-agnostic `VcsProvider` facade and the immutable domain records consumed by the rest of the pipeline.
> Status: ✅ Completed (2026-05-21)

## Outcome

| Item | Result |
|---|---|
| Build | `BUILD SUCCESSFUL` |
| Tests | 46 / 46 passed (cumulative: T1 5 + T2 18 + T3 23) |
| New main classes | 5 (`VcsProvider`, `VcsException`, `ChangeType`, `FileChange`, `CommitRecord`) |
| New test classes | 5 (record tests × 2, contract abstract class × 1, in-memory provider × 1, contract self-check × 1) |

## Test distribution (T3 only)

| Class | Tests |
|---|---|
| `FileChangeTest` | 9 (valid construction × 2, eight failure cases) |
| `CommitRecordTest` | 8 (valid construction × 2, six failure cases) |
| `InMemoryVcsProviderContractTest` | 6 (extends `VcsProviderContract`) |

## Contract checks (re-used by T4 and T5)

```
[C1] loadCommits returns a non-null list
[C2] loadCommits returns the expected commit count for the full window
[C3] loadCommits returns an empty list for an unmatched window
[C4] every returned CommitRecord satisfies its invariants
[C5] repeated calls are idempotent
[C6] commits are returned in chronological order (oldest first)
```

Real provider tests (`LocalGitProviderTest`, `GithubProviderTest`) simply `extends VcsProviderContract` and supply three fixture hooks: `providerWithKnownHistory`, `fullWindow`, `emptyWindow`, plus `expectedCommitCount()`.

## Key design decisions

| Decision | Choice | Rationale |
|---|---|---|
| Model shape | Java 21 `record` | Immutability + auto equals/hashCode + concise |
| Validation | Compact constructor (`Objects.requireNonNull` + business rules) | Fail-fast at construction time |
| `RENAMED` handling | `previousPath` allowed only when `type=RENAMED` (cross-field rule) | Eliminates ambiguous states for downstream code |
| Null message normalisation | Null → empty string | Some merge commits ship empty messages |
| Defensive copy | `List.copyOf(changes)` | External `clear()` raises `UnsupportedOperationException` |
| Timestamp | `Instant` (commit time, not author time) | Timezone-agnostic; precise windowing |
| Ordering | Oldest-first | Friendly to incremental accumulation downstream |
| Contract pattern | Abstract test class + reference implementation | Same pattern as Spring's `AbstractApplicationContextTests`, Hibernate's `BaseEntityManagerFunctionalTestCase` |

## Why the abstract-test-class pattern was chosen over Mockito mocks

| Option | Pro | Con | Picked? |
|---|---|---|---|
| Mockito mock-based contract | Lightweight | Cannot capture "what the implementation must guarantee" — only stubs return values | ❌ |
| Unit tests on each impl only | Simple | No cross-impl consistency guarantee | ❌ |
| **Abstract contract class + reference impl** | One source of truth; every impl re-verifies all rules | Slight inheritance boilerplate | ✅ |

## Files created

```
src/main/java/.../vcs/VcsException.java
src/main/java/.../vcs/VcsProvider.java
src/main/java/.../vcs/model/ChangeType.java
src/main/java/.../vcs/model/FileChange.java
src/main/java/.../vcs/model/CommitRecord.java
src/test/java/.../vcs/VcsProviderContract.java        (abstract)
src/test/java/.../vcs/InMemoryVcsProvider.java        (test-only fixture)
src/test/java/.../vcs/InMemoryVcsProviderContractTest.java
src/test/java/.../vcs/model/FileChangeTest.java
src/test/java/.../vcs/model/CommitRecordTest.java
```

## Next step

T4 — `LocalGitProvider` implementation using JGit 6.10.x. Will deliver `loadCommits(WindowConfig)` against a real local git repository, with `LocalGitProviderTest extends VcsProviderContract` so the six contract checks apply automatically. Fixture: `@TempDir` based in-memory git repo built via JGit `Git.init()` + commits.
