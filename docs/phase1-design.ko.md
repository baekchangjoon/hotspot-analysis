# Phase 1 Design — Hotspot Analysis Prototype (CLI)

> 🌐 **한국어** (현재 문서) · [English](phase1-design.md)

> 상태: Draft (2026-05-21 확정)
> 범위: Phase 1 — 제한된 범위의 CLI 프로토타입
> 담당자: Baek
> 함께 보기: [`./hotspot-analysis.md`](./hotspot-analysis.md) (이론 및 배경)

## 0. Goals and Non-goals

### Goals
- Java 코드베이스에 대한 핫스팟 점수를 계산하는 Java 21 + Spring Boot 3 기반 CLI.
- 두 가지 granularity: 파일 수준과 메서드 수준.
- 출력: CSV, YAML, Markdown (한 번의 실행에서 여러 포맷을 동시에 출력 가능).
- VCS 어댑터: `LocalGitProvider` (JGit)와 `GithubProvider` (kohsuke `github-api`).
- YAML 설정 파일로 구동.

### Non-goals (Phase 2+ 로 연기)
- 봇 커밋 필터링 (Dependabot, Renovate 등)
- 테스트 커버리지 통합 (JaCoCo / SonarQube).
- 조합 테스트 설계 (pairwise / N-wise) — Phase 1 에서는 파라미터 메타데이터만 추출.
- 비 Java 언어 (Python, TypeScript).
- 시각화 (D3 circle packing) — Phase 4.

---

## 1. Decisions Snapshot

| Item | Decision | Rationale |
|---|---|---|
| Language / runtime | Java 21 LTS | Spring Boot 3 기준, virtual threads 사용 가능 |
| Framework | Spring Boot 3.3.x | 사용자 요구사항에 따름; DI 일관성 |
| CLI framework | Picocli 4.7.x + `picocli-spring-boot-starter` | 공식 Spring 통합 |
| Build tool | Gradle 8.x + Kotlin DSL | Maven 대비 더 빠른 증분 빌드 (~70%) |
| Java AST | JavaParser 3.26.x + Symbol Solver | Java 시맨틱에 대해 99%+ 정확도 |
| Git library | JGit 6.10.x | `git` 셸 호출 회피; 테스트 가능 |
| GitHub API client | `org.kohsuke:github-api` 1.x | 성숙함, Jenkins 생태계에서 사용됨 |
| Test stack | JUnit 5 + AssertJ + Mockito + WireMock | 사실상의 표준 |
| YAML | SnakeYAML + Jackson YAML | Spring Boot 번들 |
| CSV | Apache Commons CSV | RFC 4180 준수 |
| Markdown | Mustache (`spullara/mustache.java`) | 템플릿 분리 |

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

모든 외부 의존성(JGit, JavaParser, GitHub API)은 어댑터 뒤에 격리되어 생성자로 주입되며, 이를 통해 단위 테스트에서 mocking 이 가능합니다.

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
    formula: simple                # Phase 1: simple = revisions * loc

output:
  formats: [csv, md]               # csv | yaml | md (multiple allowed)
  path: ./hotspot-report
  topN: 50                         # 0 = all
```

### Precedence
`CLI options > YAML config > code defaults`.

### Environment Variable Substitution
`${VAR_NAME}` 구문은 로드 시점에 프로세스 환경 변수를 기준으로 치환됩니다. 누락된 변수는 명확한 에러와 함께 즉시 실패(fail fast)합니다.

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
- `0` — 성공
- `1` — 런타임 / 분석 에러
- `2` — 설정 검증 에러
- `64` — 사용법 에러 (Picocli 기본값)

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
    double score      // Phase 1: revisions * loc
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
- 메서드 식별자 = `FQCN + name + parameter type signature` (오버로드 처리).
- JGit 은 `git log -L` 을 기본 제공하지 않습니다; diff hunk 와 현재 메서드 라인 범위를 교차시켜 근사합니다. Rename 추적은 Phase 1 에서 best-effort 입니다.

---

## 8. End-to-End Test Plan (E2E)

### 8.1 Verification Strategies (7 axes)

| ID | Strategy | Idea | Cost |
|---|---|---|---|
| S1 | Snapshot (Golden file) | 안정적인 fixture 의 사전 기록된 출력과 비교 | Low |
| S2 | Invariant | 항상 성립해야 하는 속성 검증 (예: `score = rev × loc`) | Low |
| S3 | Cross-validation | 출력을 `git log` / `wc -l` ground truth 와 비교 | Medium |
| S4 | Performance | tier 별 SLO 강제 | Medium |
| S5 | Format compliance | 표준 파서로 출력을 다시 파싱 | Low |
| S6 | CLI contract | exit code, help 텍스트, 옵션 우선순위 | Low |
| S7 | Provider equivalence | 동일 repo 에 대해 LocalGitProvider ≈ GithubProvider | High (network) |

### 8.2 Invariants

```
[I1]  FileHotspot.revisions >= 1
[I2]  FileHotspot.loc >= 0
[I3]  score == revisions * loc (Phase 1 simple formula)
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
| S1 | repo 내 4개 fixture: snapshot 대비 byte 수준 diff = 0 |
| S2 | 모든 fixture 및 외부 repo 전반에서 10개 invariant 모두 성립 |
| S3 | 각 외부 repo 별로 무작위 10개 파일: `revisions` 와 `loc` 가 `git log` / `wc -l` 과 정확히 일치 |
| S4 | Tiny < 3s, Small < 10s, Medium < 30s, Large < 90s, XL < 180s (Apple Silicon Mac 기준) |
| S5 | CSV 는 Commons CSV 로 재파싱 가능; YAML 은 SnakeYAML 로 재파싱 가능하며 스키마와 일치; MD 는 CommonMark 로 파싱 가능하며 테이블 추출 가능 |
| S6 | exit code: success=0, config error=2, runtime error=1; help 에 모든 옵션 표시; 우선순위 CLI > YAML > default |
| S7 | Local 과 Github 으로 동일 repo: score 상대 오차 ≤ 0.1% (참고: GitHub squash merge 로 인해 완전 일치는 보장되지 않음) |

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
| R1: 외부 repo 가 시간이 지나며 변경됨 (특히 `wiremock`) | 테스트 코드에 `HEAD` SHA 를 고정; 불일치 시 `assumeTrue(actualSha == pinnedSha)` 로 skip |
| R2: 경로가 사용자 머신마다 다름 | `System.getProperty("hotspot.e2e.fixturesDir")` 읽기; CI 에서는 `@EnabledIfSystemProperty` 로 자동 skip |
| R3: 시간 의존적 결과 | 모든 E2E 테스트가 `analysis.window.until` 을 고정 날짜로 고정 |
| R4: 대형 repo 가 개발 루프를 느리게 함 | 태그 기반 분리: `e2e-fast` (기본, < 60s) vs `e2e-slow` (nightly) |
| R5: Maven vs Gradle 레이아웃 차이 | 기본 scope: `src/main/java/**/*.java` 가 양쪽 모두에서 동작 |

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
| T1 | Project scaffolding | Gradle build, Spring Boot main, Picocli root | `hotspot --version` 동작 |
| T2 | YAML config loader | `ConfigLoader` + 7 POJOs | 유효 / 무효 / 누락 YAML 케이스 |
| T3 | VCS provider interface + model | `VcsProvider`, `CommitRecord`, `FileChange` | mock 을 통한 contract test |
| T4 | LocalGitProvider (JGit) | Local git commit + diff collection | repo 내 fixture 로 integration test |
| T5 | GithubProvider (kohsuke) | 동일 인터페이스, GitHub 기반 | WireMock 기반 API mocking |
| T6 | JavaSourceParser | 라인 범위 + 시그니처가 포함된 메서드 추출 | Java 21 (records, sealed types) 커버리지 |
| T7 | RevisionsCalculator | 라인 범위 ↔ diff hunk 겹침 카운터 | hunk 겹침의 경계 케이스 |
| T8 | LocCalculator + ScoreCalculator | LOC + `revisions * loc` | 순수 단위 테스트 |
| T9 | HotspotAnalyzer | End-to-end orchestration | fixture repo 를 통한 integration |
| T10 | Output writers (CSV/YAML/MD) | 세 가지 writer 구현 | Snapshot test + schema validation |
| T11 | CLI wiring + `init` subcommand | Picocli subcommand 통합 | CLI 호출 integration test + E2E (§8) |

예상 공수: ~2주 풀타임 / ~3–4주 파트타임.

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

## 11. Out of Scope for Phase 1 (Forward References)

| Topic | Target Phase |
|---|---|
| Bot commit filtering | Phase 2 |
| Coverage integration (JaCoCo / SonarQube) | Phase 2 |
| Cognitive complexity scoring | Phase 2 |
| Combinatorial test case generation | Phase 2 |
| Multi-language support (Tree-sitter) | Phase 2 / 3 |
| REST API backend | Phase 3 |
| React + D3 frontend | Phase 4 |

---

## 12. Open Items (Pending User Decision when Reached)

1. 백엔드 인증 정책 (Phase 3)
2. Monorepo vs multi-repo 스캐닝 전략 (Phase 2)
3. 배포 모델 (OSS / 내부용) (Phase 1+)
4. 결과 영속화 (DB / S3 / file) (Phase 3)
5. CodeScene 또는 기타 상용 도구 통합 (any phase)

각 phase 가 다가오면 재검토합니다.

## 13. v0.2: Unified scoring model

v0.2 부터는 `SIMPLE`/`COMPOSITE` 구분이 사라집니다. 모든 실행에서
두 점수와 네 가지 입력 factor 를 함께 계산하여 모든 출력 포맷에 나란히
출력합니다. 승인된 설계는
`docs/superpowers/specs/2026-05-25-unified-scoring-design.md` 를
참고하세요.
