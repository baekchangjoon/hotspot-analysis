# hotspot-analysis

[![CI](https://github.com/baekchangjoon/hotspot-analysis/actions/workflows/ci.yml/badge.svg)](https://github.com/baekchangjoon/hotspot-analysis/actions/workflows/ci.yml)

> Rank Java source files and methods by **Hotspot score** = `revisions × LOC`,
> so testing effort can be invested where the historical evidence says bugs
> are most likely to live.
> Based on Adam Tornhill's *Your Code as a Crime Scene* methodology.

This repository ships **Phase 1** of the project: a fully wired CLI that
analyses a local git repository, scores files and methods, and emits reports
in CSV / YAML / Markdown.

---

## Features

- **Two granularities** — file-level and method-level hotspots, sorted by score.
- **Two VCS sources** — local git working tree (JGit) or remote GitHub (kohsuke `github-api`, hermetic-tested with WireMock).
- **Java 21 parsing** — JavaParser 3.26 understands records, sealed types, switch expressions, pattern matching.
- **YAML configuration** — strongly typed, validated via Jakarta Bean Validation, env-var substitution.
- **Three output formats** — CSV (Excel-friendly), YAML (machine-readable), Markdown (PR-friendly).
- **134 tests, 0 failures** — every layer is covered (contract tests + unit + Spring Boot E2E).

---

## Quick start

### 1. Build

```bash
./gradlew clean build
# → build/libs/hotspot-0.1.0-SNAPSHOT.jar
```

Requires Java 21 (auto-provisioned by Gradle's `foojay-resolver-convention`).

### 2. Generate a sample config

```bash
java -jar build/libs/hotspot-0.1.0-SNAPSHOT.jar init -o hotspot.yml
```

### 3. Edit `hotspot.yml`

```yaml
analysis:
  target:
    type: local-git
    path: /path/to/your/repo
  window:
    days: 365
  scope:
    granularity:
      - file
      - method
    include:
      - "src/main/java/**/*.java"
    exclude:
      - "**/test/**"
  scoring:
    formula: simple
output:
  formats:
    - CSV
    - YAML
    - MD
  path: ./hotspot-report
  topN: 20
```

### 4. Run analysis

```bash
java -jar build/libs/hotspot-0.1.0-SNAPSHOT.jar analyze --config hotspot.yml
```

Outputs:

```
hotspot-report/
├── file_hotspots.csv
├── method_hotspots.csv
├── hotspots.yml
└── hotspots.md
```

---

## CLI reference

```
Usage: hotspot [-hV] [COMMAND]

Description:
  Hotspot analysis CLI for Java codebases.

Options:
  -h, --help      Show this help message and exit.
  -V, --version   Print version information and exit.

Commands:
  analyze  Analyse a repository and emit hotspot reports.
  init     Generate a sample hotspot.yml configuration file.
```

### `hotspot analyze`

| Option | Required | Default | Description |
|---|:---:|---|---|
| `--config, -c <file>` | ✅ | — | Path to the YAML configuration file |
| `--quiet, -q`         |   | off  | Suppress the summary on stdout |

| Exit code | Meaning |
|:---:|---|
| 0 | Analysis completed; outputs were written |
| 1 | Configuration invalid / pipeline failure / unsupported target |
| 2 | Picocli usage error |

### `hotspot init`

| Option | Required | Default | Description |
|---|:---:|---|---|
| `--output, -o <path>` |   | `./hotspot.yml` | Destination for the sample config |
| `--force, -f`         |   | off | Overwrite the destination if it exists |

---

## Configuration schema

| Path | Type | Notes |
|---|---|---|
| `analysis.target.type` | `local-git` \| `github` | Phase 1 CLI runs only `local-git` end-to-end |
| `analysis.target.path` | string | Required for `local-git` |
| `analysis.target.github.{owner,repo,branch,token}` | strings | Required for `github`; `token` supports `${ENV_VAR}` substitution |
| `analysis.window.since` / `analysis.window.until` | ISO date | Or use `analysis.window.days` |
| `analysis.window.days` | integer ≥ 1 | Relative window from now |
| `analysis.scope.granularity[]` | `file` \| `method` | Both can be selected |
| `analysis.scope.include[]` | glob[] | At least one entry required |
| `analysis.scope.exclude[]` | glob[] | Optional |
| `analysis.scoring.formula` | `simple` | `revisions × loc` (Phase 1) |
| `output.formats[]` | `CSV` \| `YAML` \| `MD` | At least one |
| `output.path` | string | Output directory |
| `output.topN` | integer ≥ 0 | `0` means "all rows" |

Environment variables in any string value are substituted with `${VAR_NAME}`
syntax; lines starting with `#` (YAML comments) are left untouched so
documentation examples don't trigger errors.

---

## How the score is computed

```
For each file F in scope:
  revisions(F) = number of commits in window touching F
  loc(F)       = current line count of F
  score(F)     = revisions(F) × loc(F)            ← SIMPLE formula

For each method M in F:
  revisions(M) = commits whose diff hunks overlap the M's line range
                 (falls back to file-level when hunk info is unavailable)
  loc(M)       = M.endLine − M.startLine + 1
  score(M)     = revisions(M) × loc(M)
```

Multi-edit dedup: a commit that touches the same file (or method's range)
multiple times still counts as **one** revision, matching
`git log --oneline -- <path> | wc -l`.

---

## Repository layout

```
docs/
  phase1-design.md            ← architecture & decisions
  reports/T1…T11-*.md         ← per-task completion reports
src/main/java/io/github/baekchangjoon/hotspotanalysis/
  HotspotApplication.java     ← Spring Boot entry point
  cli/                        ← Picocli subcommands
  config/                     ← YAML schema records + loader
  vcs/                        ← VcsProvider + LocalGit / GitHub impls
  parser/                     ← JavaParser-based method extraction
  analysis/                   ← Revisions / LOC / Score / Orchestrator
  output/                     ← CSV / YAML / MD writers
src/main/resources/
  application.yml             ← Spring Boot logging config
  templates/hotspot.example.yml ← bundled sample for `hotspot init`
```

---

## Phase 1 limitations

1. **GitHub target end-to-end**: the API client is verified by WireMock, but
   the CLI's `analyze` requires a `local-git` target. Until a GitHub-clone
   integration lands, clone the repository locally and re-run with
   `target.type=local-git`.
2. **Working-tree LOC**: LOC is read at HEAD, not at each historical commit
   (same simplification Tornhill makes in his book).
3. **No bot filtering / coverage integration / incremental cache**: deferred
   to Phase 2+.

See `docs/reports/*` for a per-task breakdown of these decisions and their
alternatives.

---

## Roadmap

| Phase | Goal | Status |
|---|---|:---:|
| 1 | CLI prototype with file/method scoring, three output formats | ✅ Done |
| 2 | Extended CLI: parameter combinations, coverage integration, more languages | 🚧 |
| 3 | REST API backend | ⏳ |
| 4 | Front-end visualisation on top of the backend | ⏳ |

---

## Development

```bash
./gradlew test            # 134 tests, ~10s
./gradlew build           # full assembly
./gradlew check           # tests + static analysis
```

Continuous Integration runs on every push and on a **daily schedule**
(09:00 KST). Build logs and the assembled JAR are uploaded as workflow
artifacts on every run — see the [Actions tab](https://github.com/baekchangjoon/hotspot-analysis/actions).

---

## License

TBD.
