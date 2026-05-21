# T11 Completion Report — CLI Integration (analyze / init)

> Task: Expose the Phase 1 pipeline as a fully wired CLI with `hotspot analyze` and `hotspot init` subcommands, and verify the assembled JAR end-to-end via Spring Boot.
> Status: ✅ Completed (2026-05-21)

## Outcome

| Item | Result |
|---|---|
| Build | `BUILD SUCCESSFUL` |
| Tests | **134 / 134 passed** (cumulative: +9 vs T10) |
| New main classes | 2 (`AnalyzeCommand`, `InitCommand`) |
| Modified main classes | 2 (`HotspotCommand`, `HotspotApplication`) |
| New test classes | 3 (`AnalyzeCommandTest`, `InitCommandTest`, `HotspotCliE2ETest`) |
| Manual smoke test (assembled JAR) | ✅ All four CLI surfaces verified |

## Test breakdown (9 added in T11)

| Class | Tests | Highlights |
|---|---|---|
| `InitCommandTest` | 3 | Write sample, refuse overwrite, overwrite-with-force |
| `AnalyzeCommandTest` | 4 | End-to-end (CSV+YAML+MD), missing config, invalid config, `--quiet` |
| `HotspotCliE2ETest` (`@SpringBootTest`) | 2 | Full Spring context drives both `analyze` and `init` |

## CLI surface

```
$ hotspot --help
Usage: hotspot [-hV] [COMMAND]
Description: Hotspot analysis CLI for Java codebases.
Options:
  -h, --help      Show this help message and exit.
  -V, --version   Print version information and exit.
Commands:
  analyze  Analyse a repository and emit hotspot reports.
  init     Generate a sample hotspot.yml configuration file.
```

### `hotspot init`
- `--output, -o <path>` (default: `./hotspot.yml`)
- `--force, -f`         (overwrite existing)
- Exit codes: `0` on success, `1` when destination exists without `--force` or template is missing.

### `hotspot analyze`
- `--config, -c <file>` (required) — path to a YAML config
- `--quiet, -q`         (suppress stdout summary)
- Exit codes:
  - `0` analysis completed
  - `1` config missing / invalid / pipeline failure / unsupported target
  - `2` Picocli usage error (unknown option, missing required option)

## Manual smoke test on the assembled JAR

```bash
$ java -jar build/libs/hotspot-0.1.0-SNAPSHOT.jar --version
hotspot 0.1.0-SNAPSHOT                                          # exit=0

$ java -jar build/libs/hotspot-0.1.0-SNAPSHOT.jar init -o /tmp/hotspot.yml -f
Wrote sample configuration to /tmp/hotspot.yml                  # exit=0

$ java -jar build/libs/hotspot-0.1.0-SNAPSHOT.jar analyze --config /tmp/self.yml
Hotspot analysis complete.
  Target:      LOCAL_GIT:/Users/.../hotspot-analysis
  Commits:     1
  Files:       51
  Methods:     85
  Top file:    src/main/java/.../HotspotApplication.java (rev=0, loc=57, score=0)
                                                                # exit=0
```

The smoke test ran against this very repository (51 Java files, 85 methods discovered, 3 output files written under `/tmp/hotspot-e2e-demo/out`).

## Key design decisions

| Decision | Choice | Rationale |
|---|---|---|
| Picocli-Spring wiring | Subcommands registered at runtime via `addSubcommand("name", bean)` instead of `@Command(subcommands=...)` | Default-constructor reflection used by Picocli's class-based subcommand registration breaks our DI-required commands; runtime registration keeps the root command Spring-testable in isolation |
| `IFactory` injection | `picocli-spring-boot-starter`'s `IFactory` is taken from Spring | Subcommand options + mixin classes still benefit from DI when needed |
| Exit codes | 0 / 1 / 2 with documented semantics | Friendly to CI gating and grep-based scripts |
| `--quiet` | Optional, default off | Useful for piped output / CI logs |
| `init` template | Bundled resource at `/templates/hotspot.example.yml` | Same file used by `HotspotExampleConfigTest` so the sample is *always* valid |
| Spring Boot test depth | `@SpringBootTest` (no `WebApplicationType` overrides needed; CLI mode already disabled web in `application.yml`) | Smallest possible bootstrap |

## Phase 1 limitations (carried forward)

1. **GitHub target end-to-end**: still requires cloning to a local path. `AnalyzeCommand` returns exit code 1 with a clear remediation hint when `target.type=github`.
2. **No incremental mode**: every invocation re-walks the entire history. Phase 3 backend service will cache.
3. **No coverage integration**: deferred per Phase 1 scope.

## Phase 1 final test summary

| Layer | Tests |
|---|---:|
| Scaffolding (T1) | 5 |
| Config loader (T2) | 18 |
| VCS interface + contract (T3) | 23 |
| LocalGitProvider (T4) | 12 |
| GithubProvider + WireMock (T5) | 12 |
| JavaSourceParser (T6) | 10 |
| RevisionsCalculator + DiffHunk (T7) | 14 |
| LocCalculator + ScoreCalculator (T8) | 12 |
| HotspotAnalyzer (T9) | 6 |
| Output writers (T10) | 13 |
| CLI + E2E (T11) | 9 |
| **Total** | **134** |

## Counter-arguments considered for T11

| Alternative | Why rejected |
|---|---|
| Use class-based `@Command(subcommands={...})` only | Picocli instantiates via reflection with default constructor → fails for DI-only commands |
| ProcessBuilder-launched JAR in tests | Slow (~3 s per test) and brittle on CI; `@SpringBootTest` already exercises the same wiring |
| Hardcode the sample YAML in `InitCommand` | Then the sample drifts from the test fixture; bundling the resource file keeps a single source of truth |

## What's next (post-Phase-1)

1. **GitHub clone integration** so `target.type=github` can run end-to-end without manual clones.
2. **Cyclomatic complexity** (JavaParser symbol solver) as an alternative LOC dimension.
3. **Bot/account filter** to exclude bot author commits from revision counts.
4. **Coverage integration** to up-weight uncovered hotspots.
5. **Incremental analysis cache** for the upcoming Phase 3 backend service.
