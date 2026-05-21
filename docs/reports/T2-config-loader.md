# T2 Completion Report — YAML Configuration Loader

> Task: Parse `hotspot.yml` into a strongly-typed, validated configuration object with environment variable substitution.
> Status: ✅ Completed (2026-05-21)

## Outcome

| Item | Result |
|---|---|
| Build | `BUILD SUCCESSFUL` |
| Tests | 23 / 23 passed (cumulative: T1 5 + T2 18) |
| New main classes | 12 (7 records + 1 enum-bearing types, 1 loader, 1 resolver, 2 exceptions) |
| New test classes | 3 |

## Test distribution

| Class | Tests | Scope |
|---|---|---|
| `EnvironmentVariableResolverTest` | 6 | Placeholder substitution, comment-line protection, invalid names, missing var |
| `ConfigLoaderTest` | 11 | Happy path × 2 (local + github), 9 failure scenarios |
| `HotspotExampleConfigTest` | 1 | Regression guard for shipped sample YAML |

## Key design decisions

| Decision | Choice | Rationale |
|---|---|---|
| Validation framework | Jakarta Bean Validation (Hibernate Validator 8.x) | Standard, supports records out of the box |
| Cross-field validation | `@AssertTrue` methods on records | Cleanest record-native pattern (TargetConfig, WindowConfig) |
| Env substitution timing | Text-level **before** YAML parse | Allows placeholders at any position; comment lines are preserved |
| Comment protection | Line-aware skip (first non-WS = `#`) | `templates/hotspot.example.yml` keeps `# token: ${GITHUB_TOKEN}` safely |
| Enum mapping | `@JsonCreator from(String)` with case + dash normalisation | `local-git` ↔ `LOCAL_GIT` natural mapping |
| Date parsing | JavaTimeModule + ISO-8601 `LocalDate` | Standard, locale-stable |
| Exception hierarchy | `ConfigLoadException ← ConfigValidationException` | Caller can distinguish IO/parse errors from validation errors |

## Validation rules enforced (15 rules)

```
[V1]  AnalysisConfig.analysis              not null
[V2]  AnalysisConfig.output                not null
[V3]  AnalysisSection.{target, window, scope, scoring}  not null
[V4]  TargetConfig.type                    not null + valid enum
[V5]  TargetConfig.path                    required when type=LOCAL_GIT
[V6]  TargetConfig.github                  required when type=GITHUB
[V7]  GithubConfig.{owner, repo, branch, token}  not blank
[V8]  WindowConfig                         (since,until) ∨ days required
[V9]  WindowConfig                         since ≤ until
[V10] WindowConfig.days                    >= 1
[V11] ScopeConfig.granularity              not empty
[V12] ScopeConfig.include                  not empty
[V13] OutputConfig.formats                 not empty
[V14] OutputConfig.path                    not blank
[V15] OutputConfig.topN                    >= 0
```

## TDD lesson learned

The `HotspotExampleConfigTest` initially failed because the commented-out `${GITHUB_TOKEN}` line in the sample was still being resolved. The fix added a 6th `EnvironmentVariableResolverTest` ("leaves placeholders inside YAML comment lines untouched") to lock the behaviour in place. This is exactly the kind of regression an integration-style fixture is meant to catch.

## Dependencies added

```kotlin
implementation("org.springframework.boot:spring-boot-starter-validation")
implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
```

## Next step

T3 — VCS Provider interface + domain model: define the source-agnostic facade and the immutable records (`CommitRecord`, `FileChange`, `ChangeType`) that every provider must emit.
