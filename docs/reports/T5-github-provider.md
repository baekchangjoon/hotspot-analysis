# T5 Completion Report — GithubProvider (kohsuke github-api)

> Task: Implement `GithubProvider` that fetches commit history through the GitHub REST API, satisfies the `VcsProviderContract`, and is decoupled from the third-party HTTP client to allow hermetic testing.
> Status: ✅ Completed (2026-05-21)

## Outcome

| Item | Result |
|---|---|
| Build | `BUILD SUCCESSFUL` |
| Tests | 70 / 70 passed (cumulative: +12 vs T4) |
| New main classes | 5 (`GhCommit`, `GhFileChange`, `GithubClient`, `GithubProvider`, `KohsukeGithubClient`) |
| New test classes | 3 (`FakeGithubClient`, `GithubProviderTest`, `KohsukeGithubClientWireMockTest`) |
| New dependencies | `org.kohsuke:github-api:1.326`, `org.wiremock:wiremock-standalone:3.10.0` (test) |

## Layered design

```
┌─────────────────────────────────────────────────────┐
│ GithubProvider  (implements VcsProvider)            │  ← T5 main artifact
│  - converts GhCommit  → CommitRecord                │
│  - converts GhFileChange → FileChange               │
└──────────────────────┬──────────────────────────────┘
                       │ depends on
┌──────────────────────▼──────────────────────────────┐
│ GithubClient  (interface)                           │
└──────────────────────┬──────────────────────────────┘
            ┌──────────┴────────────┐
            │                       │
┌───────────▼─────────────┐   ┌─────▼──────────────┐
│ KohsukeGithubClient     │   │ FakeGithubClient   │
│ (production, HTTP/JSON) │   │ (test fixture)     │
└─────────────────────────┘   └────────────────────┘
```

This split is the central decision of T5: it isolates the **business mapping** (`GithubProvider`) from the **transport** (`KohsukeGithubClient`), letting us run the full `VcsProviderContract` against `GithubProvider` without any HTTP traffic.

## Test distribution

| Class | Tests | Purpose |
|---|---|---|
| `GithubProviderTest` (extends `VcsProviderContract`) | 11 | 6 contract checks + 5 status-mapping rules |
| `KohsukeGithubClientWireMockTest` | 1 | End-to-end HTTP exercise via stubbed GitHub API |

### Status mapping verified
- `added`        → `ADDED`
- `modified`     → `MODIFIED`
- `removed`      → `DELETED`
- `renamed`      → `RENAMED` + `previousPath` populated
- `copied`       → `RENAMED` (collapsed)
- unknown values → `MODIFIED` (defensive default)

## Key design decisions

| Decision | Choice | Rationale |
|---|---|---|
| Transport abstraction | `GithubClient` interface | Mock/fake provider in unit tests, real client tested separately |
| DTO records | `GhCommit`, `GhFileChange` (record) | Decouple from `org.kohsuke.github.GHCommit` (which is impractical to construct in tests) |
| HTTP test strategy | **WireMock 3.x** with stubbed `/repos/...` and `/commits/...` endpoints | Hermetic; exercises real `github-api` JSON parsing |
| Endpoint override | `KohsukeGithubClient(GithubConfig, String endpoint)` overload | Test points to `wireMockServer.baseUrl()`; production uses `DEFAULT_ENDPOINT` |
| `GH_URL` constant | Local `DEFAULT_ENDPOINT = "https://api.github.com"` | kohsuke library's `GitHub.GITHUB_URL` was removed/renamed in 1.326; local constant avoids fragility |
| Author resolution | `author.login` → `commit.author.name` → "unknown" | Some commits have null author objects (deleted users, anonymous commits) |
| Error wrapping | `IOException` → `VcsException` | Same error surface as `LocalGitProvider`, transparent to callers |

## WireMock fixture highlights

The single HTTP test stubs three endpoints to drive a complete `listCommits` call:

| Endpoint | Why kohsuke calls it |
|---|---|
| `GET /repos/owner/repo` | `GitHub.getRepository("owner/repo")` initialisation |
| `GET /repos/owner/repo/commits` | `repository.queryCommits().list()` page iteration |
| `GET /repos/owner/repo/commits/{sha}` | Lazy hydration when `GHCommit.getFiles()` is read |

Each stub returns a JSON payload built with text blocks (Java 21) so the fixture is readable and easy to evolve.

## Counter-arguments considered

| Alternative | Why rejected |
|---|---|
| Mock `org.kohsuke.github.GHCommit` directly | `GHCommit` and `GHCommit.File` are difficult to instantiate; final/package-private constructors |
| Skip transport tests; integrate at T11 only | Would not catch JSON-shape regressions until much later in the cycle |
| Use Mockito on the HTTP client | Less realistic than full HTTP round-trip; doesn't catch URL/path mistakes |

## Next step

T6 — `JavaSourceParser` using JavaParser 3.26.x. Will extract methods (signature + line range + parameter metadata) from a Java source file, with explicit coverage for Java 21 features (records, sealed types, switch expressions). Dependency: `com.github.javaparser:javaparser-symbol-solver-core:3.26.2`.
