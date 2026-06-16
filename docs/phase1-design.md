# Phase 1 Design — Hotspot Analysis Prototype (CLI)

> 🌐 [한국어](phase1-design.ko.md) · **English** (this page)

> Status: Draft (confirmed 2026-05-21)
> Scope: Phase 1 — CLI prototype with constrained scope
> Owner: Baek
> See also: [`./hotspot-analysis.md`](./hotspot-analysis.md) (theory & background)

> **Note:** Since v0.2 the scorer always computes both the simple score and the composite score (recency decay × cognitive complexity × coverage multiplier); the `formula: simple|composite` toggle described below was removed.

## 0. Goals and Non-goals

### Goals
- Java 21 + Spring Boot 3 based CLI that calculates hotspot scores for a Java codebase.
- Two granularities: file level and method level.
- Output: CSV, YAML, Markdown (single run can emit multiple formats).
- VCS adapters: `LocalGitProvider` (JGit) and `GithubProvider` (kohsuke `github-api`).
- Driven by a YAML configuration file.

### Non-goals
- Bot commit filtering (Dependabot, Renovate, etc.)
- Combinatorial test design (pairwise / N-wise) — only parameter metadata is extracted.
- Non-Java languages (Python, TypeScript).
- Visualization (D3 circle packing).

---

## 1. Decisions Snapshot

| Item | Decision | Rationale |
|---|---|---|
| Language / runtime | Java 21 LTS | Spring Boot 3 baseline, virtual threads available |
| Framework | Spring Boot 3.3.x | Per user requirement; DI consistency |
| CLI framework | Picocli 4.7.x + `picocli-spring-boot-starter` | Official Spring integration |
| Build tool | Gradle 8.x + Kotlin DSL | Faster incremental builds (~70%) than Maven |
| Java AST | JavaParser 3.26.x + Symbol Solver | 99%+ accuracy for Java semantics |
| Git library | JGit 6.10.x | Avoid shelling out to `git`; testable |
| GitHub API client | `org.kohsuke:github-api` 1.x | Mature, used by Jenkins ecosystem |
| Test stack | JUnit 5 + AssertJ + Mockito + WireMock | De-facto standard |
| YAML | SnakeYAML + Jackson YAML | Spring Boot bundled |
| CSV | Apache Commons CSV | RFC 4180 compliance |
| Markdown | Mustache (`spullara/mustache.java`) | Template separation |

---

## 2. Architecture

```
┌─────────────────────────────────────────────────────────┐
│  CLI Layer (Picocli)                                    │
│  - HotspotCommand (root)                                │
│  - AnalyzeSubCommand, InitSubCommand                    │
└─────────────────────────┬───────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────┐
│  Application Layer                                      │
│  - HotspotAnalyzer (orchestration)                      │
│  - ConfigLoader (YAML → POJO)                           │
└─────┬──────────────────┬─────────────────┬──────────────┘
      │                  │                 │
┌─────▼─────┐    ┌───────▼────────┐  ┌─────▼─────────┐
│ VCS       │    │ Parser         │  │ Metrics       │
│ Provider  │    │ (JavaParser)   │  │ Calculator    │
│ (iface)   │    │                │  │               │
│ ├Local    │    │                │  │               │
│ └Github   │    │                │  │               │
└───────────┘    └────────────────┘  └───────────────┘
                          │
                ┌─────────▼──────────────┐
                │ Output Writer (strategy)│
                │ ├ CsvWriter             │
                │ ├ YamlWriter            │
                │ └ MarkdownWriter        │
                └─────────────────────────┘
```

All external dependencies (JGit, JavaParser, GitHub API) are isolated behind adapters and injected by constructor, enabling mocking in unit tests.

---

## 3. Directory Layout

```
hotspot-analysis/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/wrapper/...
├── config/
│   └── hotspot.example.yml
├── docs/
│   └── phase1-design.md
├── src/
│   ├── main/
│   │   ├── java/com/baek/hotspot/
│   │   │   ├── HotspotApplication.java
│   │   │   ├── cli/
│   │   │   │   ├── HotspotCommand.java
│   │   │   │   ├── AnalyzeSubCommand.java
│   │   │   │   └── InitSubCommand.java
│   │   │   ├── config/
│   │   │   │   ├── AnalysisConfig.java
│   │   │   │   ├── TargetConfig.java
│   │   │   │   ├── WindowConfig.java
│   │   │   │   ├── ScopeConfig.java
│   │   │   │   ├── ScoringConfig.java
│   │   │   │   ├── OutputConfig.java
│   │   │   │   └── ConfigLoader.java
│   │   │   ├── vcs/
│   │   │   │   ├── VcsProvider.java
│   │   │   │   ├── LocalGitProvider.java
│   │   │   │   ├── GithubProvider.java
│   │   │   │   └── model/
│   │   │   │       ├── CommitRecord.java
│   │   │   │       └── FileChange.java
│   │   │   ├── parser/
│   │   │   │   ├── JavaSourceParser.java
│   │   │   │   └── model/
│   │   │   │       ├── MethodSignature.java
│   │   │   │       ├── MethodInfo.java
│   │   │   │       └── ParameterInfo.java
│   │   │   ├── metrics/
│   │   │   │   ├── RevisionsCalculator.java
│   │   │   │   ├── LocCalculator.java
│   │   │   │   └── HotspotScoreCalculator.java
│   │   │   ├── analysis/
│   │   │   │   ├── HotspotAnalyzer.java
│   │   │   │   └── model/
│   │   │   │       ├── FileHotspot.java
│   │   │   │       ├── MethodHotspot.java
│   │   │   │       └── HotspotReport.java
│   │   │   └── output/
│   │   │       ├── HotspotWriter.java
│   │   │       ├── CsvHotspotWriter.java
│   │   │       ├── YamlHotspotWriter.java
│   │   │       ├── MarkdownHotspotWriter.java
│   │   │       └── HotspotWriterFactory.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── templates/
│   │           └── hotspot-report.md.mustache
│   └── test/
│       ├── java/com/baek/hotspot/
│       │   ├── cli/        # CLI integration tests
│       │   ├── config/     # YAML loader tests
│       │   ├── vcs/        # Provider adapter tests
│       │   ├── parser/     # JavaParser adapter tests
│       │   ├── metrics/    # Metric calculation tests
│       │   ├── analysis/   # Orchestration integration tests
│       │   ├── output/     # Output format tests
│       │   └── e2e/        # End-to-end tests (see §8)
│       └── resources/
│           ├── fixtures/
│           │   ├── tiny-repo-1.zip
│           │   ├── tiny-repo-2.zip
│           │   ├── medium-repo.zip
│           │   └── multi-module-repo.zip
│           ├── snapshots/
│           │   ├── tiny-repo-1.expected.csv
│           │   ├── tiny-repo-1.expected.yaml
│           │   └── tiny-repo-1.expected.md
│           └── e2e-config.yml
└── README.md
```

---

## 4. YAML Configuration Schema

`config/hotspot.example.yml`:

```yaml
analysis:
  target:
    type: local-git                # local-git | github
    # When type=local-git
    path: ./my-spring-project
    # When type=github
    github:
      owner: myorg
      repo: myrepo
      branch: main
      token: ${GITHUB_TOKEN}       # env var substitution

  window:
    since: "2025-11-21"            # ISO-8601, takes precedence
    until: "2026-05-21"
    # days: 180                    # alternative: last N days

  scope:
    granularity: [file, method]    # [file] only or both
    include:
      - "src/main/java/**/*.java"
    exclude:
      - "**/generated/**"
      - "**/test/**"
      - "**/build/**"
      - "**/target/**"

  scoring:
    formula: simple                # simple = revisions * loc (note: v0.2+ always computes composite too)

output:
  formats: [csv, md]               # csv | yaml | md (multiple allowed)
  path: ./hotspot-report
  topN: 50                         # 0 = all
```

### Precedence
`CLI options > YAML config > code defaults`.

### Environment Variable Substitution
`${VAR_NAME}` syntax is resolved against the process environment at load time. Missing variables fail fast with a clear error.

---

## 5. CLI Surface

```bash
hotspot analyze --config ./hotspot.yml
hotspot analyze --config ./hotspot.yml --output-format yaml --top 100
hotspot init    --output ./hotspot.yml
hotspot --version
hotspot --help
hotspot analyze --help
```

### Exit Codes
- `0` — success
- `1` — runtime / analysis error
- `2` — configuration validation error
- `64` — usage error (Picocli default)

---

## 6. Domain Model (key records)

```java
public record CommitRecord(
    String hash,
    String author,
    Instant committedAt,
    List<FileChange> changes
) {}

public record FileChange(
    String path,
    int linesAdded,
    int linesDeleted,
    ChangeType type   // ADDED | MODIFIED | DELETED | RENAMED
) {}

public record MethodInfo(
    MethodSignature signature,
    int startLine,
    int endLine,
    List<ParameterInfo> parameters
) {}

public record FileHotspot(
    String path,
    int revisions,
    int loc,
    double score      // simple score: revisions * loc
) {}

public record MethodHotspot(
    MethodSignature signature,
    String filePath,
    int revisions,
    int loc,
    double score
) {}

public record HotspotReport(
    Instant generatedAt,
    AnalysisConfig config,
    List<FileHotspot> fileHotspots,
    List<MethodHotspot> methodHotspots
) {}
```

---

## 7. Method-level Hotspot Algorithm

```
For each Java file F in scope:
  1. JavaParser → list of methods M[] with line range [start, end]
  2. For each commit C in window touching F:
       For each diff hunk in C affecting F:
         If hunk line range overlaps method.range:
           revisions(method) += 1
  3. loc(method)   = end - start + 1
  4. score(method) = revisions(method) * loc(method)
```

### Notes
- Method identifier = `FQCN + name + parameter type signature` (handles overloads).
- JGit does not provide `git log -L` natively; we approximate by intersecting diff hunks with current method line ranges. Rename tracking is best-effort.

---

## 8. End-to-End Test Plan (E2E)

### 8.1 Verification Strategies (7 axes)

| ID | Strategy | Idea | Cost |
|---|---|---|---|
| S1 | Snapshot (Golden file) | Compare against pre-recorded output of stable fixtures | Low |
| S2 | Invariant | Check properties that must always hold (e.g. `score = rev × loc`) | Low |
| S3 | Cross-validation | Compare our output against `git log` / `wc -l` ground truth | Medium |
| S4 | Performance | Enforce per-tier SLOs | Medium |
| S5 | Format compliance | Re-parse output with standard parsers | Low |
| S6 | CLI contract | Exit codes, help text, option precedence | Low |
| S7 | Provider equivalence | LocalGitProvider ≈ GithubProvider for the same repo | High (network) |

### 8.2 Invariants

```
[I1]  FileHotspot.revisions >= 1
[I2]  FileHotspot.loc >= 0
[I3]  simple score == revisions * loc
[I4]  Output sorted by score DESC, ties broken by path ASC
[I5]  Result path matches scope.include AND not scope.exclude
[I6]  Sum of MethodHotspot per file <= total methods in file
[I7]  Idempotency: same input → identical output
[I8]  Monotonicity: narrower window → row count non-increasing
[I9]  topN applied → row count <= topN
[I10] Row count and score values are consistent across CSV / YAML / MD outputs
```

### 8.3 Pass Criteria

| Axis | Pass Criterion |
|---|---|
| S1 | 4 in-repo fixtures: byte-level diff = 0 against snapshot |
| S2 | All 10 invariants hold across every fixture and external repo |
| S3 | For each external repo, 10 random files: `revisions` and `loc` match `git log` / `wc -l` exactly |
| S4 | Tiny < 3s, Small < 10s, Medium < 30s, Large < 90s, XL < 180s (Apple Silicon Mac baseline) |
| S5 | CSV reparsable by Commons CSV; YAML reparsable by SnakeYAML and matches schema; MD parsable by CommonMark with table extraction |
| S6 | Exit codes: success=0, config error=2, runtime error=1; help lists every option; precedence CLI > YAML > default |
| S7 | Same repo via Local and Github: score relative error ≤ 0.1% (note: full equality not guaranteed due to GitHub squash merges) |

### 8.4 External Fixtures Inventory (Java + Git, locally available)

| Tier | Path (under `/Users/changjoonbaek/`) | Build | Commits | Java |
|---|---|---|---|---|
| Tiny | `github_line-service/line-service` | Maven | 3 | 12 |
| Tiny | `github_member-service/member-service` | Maven | 4 | 20 |
| Tiny | `github_jpashop/jpashop` | Maven | 7 | 31 |
| Tiny | `github_jpashop/jpashop_qpakzk` | Gradle | 25 | 3 |
| Small | `github_advance-spring-boot-microservice/advance-spring-boot-microservice` | Maven | 73 | 55 |
| Medium | `github_ftgo/ftgo-application-kor` | Gradle | 174 | ~ |
| Medium | `github_spring-microservice-sample/spring-microservice-sample` | Maven | 181 | 61 |
| Medium | `github_assurenet/temp` | Maven | 202 | - |
| Large | `github_ftgo/ftgo-application` | Gradle | 296 | 337 |
| Large | `github_assurenet/assurenet` | Maven | 300 | 209 |
| XL | `github_rbm-server/rbm-server` | Gradle | 1,937 | 1,066 |
| XL | `github_wiremock/wiremock` | Gradle Kotlin DSL | 4,479 | 1,255 |

### 8.5 Fixture-to-Scenario Mapping

| Scenario | Fixture(s) | Strategies |
|---|---|---|
| Fast regression (per PR, < 60s) | `line-service`, `member-service`, `jpashop_qpakzk` | S1, S2, S3, S6 |
| Maven compatibility | `jpashop/jpashop`, `advance-spring-boot-microservice` | S2, S5 |
| Gradle Groovy compatibility | `ftgo-application-kor` | S2, S5 |
| Gradle Kotlin DSL compatibility | `wiremock/wiremock` | S2, S5 |
| Multi-module MSA | `ftgo-application`, `spring-microservice-sample` | S2, S3 |
| Mid-size general behavior | `assurenet`, `spring-microservice-sample` | S1, S2, S3, S4 |
| Large performance (nightly) | `rbm-server`, `wiremock` | S4 |
| Provider equivalence | small synthetic repo pushed to GitHub | S7 |

### 8.6 Risks and Mitigations

| Risk | Mitigation |
|---|---|
| R1: External repo drifts over time (esp. `wiremock`) | Pin `HEAD` SHA in test code; `assumeTrue(actualSha == pinnedSha)` skip on mismatch |
| R2: Path is user-machine specific | Read `System.getProperty("hotspot.e2e.fixturesDir")`; `@EnabledIfSystemProperty` auto-skip on CI |
| R3: Time-dependent results | All E2E tests pin `analysis.window.until` to a fixed date |
| R4: Large repos slow down developer loop | Tag-based separation: `e2e-fast` (default, < 60s) vs `e2e-slow` (nightly) |
| R5: Maven vs Gradle layout differences | Default scope: `src/main/java/**/*.java` works for both |

### 8.7 Execution

```bash
# Fast tier (default for PR builds)
./gradlew test -Pe2e=fast \
  -Dhotspot.e2e.fixturesDir=/Users/changjoonbaek

# Full tier (nightly)
./gradlew test -Pe2e=full \
  -Dhotspot.e2e.fixturesDir=/Users/changjoonbaek

# CI without local fixtures (auto-skips path-dependent tests)
./gradlew test
```

---

## 9. TDD Work Breakdown (Phase 1)

| # | Task | Deliverables | Key Tests |
|---|---|---|---|
| T1 | Project scaffolding | Gradle build, Spring Boot main, Picocli root | `hotspot --version` works |
| T2 | YAML config loader | `ConfigLoader` + 7 POJOs | Valid / invalid / missing YAML cases |
| T3 | VCS provider interface + model | `VcsProvider`, `CommitRecord`, `FileChange` | Contract tests via mocks |
| T4 | LocalGitProvider (JGit) | Local git commit + diff collection | Integration test with in-repo fixture |
| T5 | GithubProvider (kohsuke) | Same interface, GitHub-backed | WireMock-based API mocking |
| T6 | JavaSourceParser | Method extraction with line ranges + signatures | Java 21 (records, sealed types) coverage |
| T7 | RevisionsCalculator | Line-range ↔ diff-hunk overlap counter | Boundary cases on hunk overlap |
| T8 | LocCalculator + ScoreCalculator | LOC + `revisions * loc` | Pure unit tests |
| T9 | HotspotAnalyzer | End-to-end orchestration | Integration via fixture repo |
| T10 | Output writers (CSV/YAML/MD) | Three writer impls | Snapshot tests + schema validation |
| T11 | CLI wiring + `init` subcommand | Picocli subcommands integrated | CLI invocation integration tests + E2E (§8) |

Estimated effort: ~2 weeks full-time / ~3–4 weeks part-time.

---

## 10. Key Dependencies (build.gradle.kts excerpt)

```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")

    // CLI
    implementation("info.picocli:picocli-spring-boot-starter:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")

    // Java AST
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.26.2")

    // Git
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")

    // GitHub
    implementation("org.kohsuke:github-api:1.326")

    // Output
    implementation("org.apache.commons:commons-csv:1.12.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.github.spullara.mustache.java:compiler:0.9.14")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.assertj:assertj-core")
    testImplementation("com.github.tomakehurst:wiremock-standalone:3.10.0")
}
```

---

## 11. Out of scope

The following are not currently supported:

- Bot commit filtering (Dependabot, Renovate, etc.)
- Multi-language support (Tree-sitter / non-Java)
- Combinatorial / pairwise test-case generation
- Monorepo-vs-multi-repo scan strategy
- REST API backend, result persistence, backend auth
- React + D3 frontend visualization

---

## 12. Open Items

1. Monorepo vs multi-repo scanning strategy
2. Result persistence (DB / S3 / file)
3. CodeScene or other commercial tool integration

---

## 13. v0.2: Unified scoring model

Starting v0.2, the `SIMPLE`/`COMPOSITE` distinction is gone. Every run
computes both scores plus the four input factors and emits them side by
side in every output format. See
`docs/superpowers/specs/2026-05-25-unified-scoring-design.md` for the
approved design.
