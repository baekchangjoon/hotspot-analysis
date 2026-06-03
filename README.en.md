# hotspot-analysis

> 🌐 [한국어](README.md) · **English** (this page)

[![CI](https://github.com/baekchangjoon/hotspot-analysis/actions/workflows/ci.yml/badge.svg)](https://github.com/baekchangjoon/hotspot-analysis/actions/workflows/ci.yml)
[![Coverage](https://raw.githubusercontent.com/baekchangjoon/hotspot-analysis/badges/.github/badges/jacoco.svg)](https://github.com/baekchangjoon/hotspot-analysis/actions/workflows/ci.yml)
[![Branch Coverage](https://raw.githubusercontent.com/baekchangjoon/hotspot-analysis/badges/.github/badges/branches.svg)](https://github.com/baekchangjoon/hotspot-analysis/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](build.gradle.kts)
[![Skill](https://img.shields.io/badge/Skill-Claude%20Code%20%7C%20Cursor%20%7C%20Codex%20%7C%20Gemini%20CLI-blueviolet)](https://agentskills.io)
[![Docs](https://img.shields.io/badge/Docs-KO%20%7C%20EN-green)](README.md)

> Rank Java source files and methods by a **Composite Hotspot Score** that
> combines recency-weighted revisions, cognitive complexity, and coverage gap —
> so testing effort can be invested where the historical evidence says bugs
> are most likely to live.
> Based on Adam Tornhill's *Your Code as a Crime Scene* methodology.

This repository ships **Phase 1** of the project: a fully wired CLI that
analyses a local git repository, scores files and methods, and emits reports
in CSV / YAML / Markdown.

---

## Install as a Claude Code skill

This repo is also a **Claude Code plugin marketplace**. The bundled
`hotspot-analysis` skill teaches an agent to fetch, configure, run, and
interpret the CLI for you (it auto-downloads the released jar — no build). Inside Claude Code:

```text
/plugin marketplace add baekchangjoon/hotspot-analysis
/plugin install hotspot-analysis@hotspot-analysis
/reload-plugins
```

Then just ask, e.g. *"find the hotspots in this Java repo"* — the model invokes
the skill, which drives the workflow below.

### Skill only (without the plugin)

Any [Agent Skills](https://agentskills.io)-compatible agent (Claude Code,
Claude.ai, Cursor, Gemini CLI, …) can use the skill folder directly:

```bash
git clone https://github.com/baekchangjoon/hotspot-analysis
cp -r hotspot-analysis/skills/hotspot-analysis ~/.claude/skills/
```

> No build needed — the skill's `scripts/get-jar.sh` downloads the released jar
> (only a JDK 21 runtime is required). For no JDK at all, use the Docker path above.

---

## Features

- **Two granularities** — file-level and method-level hotspots, sorted by score.
- **Two VCS sources** — local git working tree (JGit) or remote GitHub (kohsuke `github-api`, hermetic-tested with WireMock).
- **Java 21 parsing** — JavaParser 3.26 understands records, sealed types, switch expressions, pattern matching.
- **YAML configuration** — strongly typed, validated via Jakarta Bean Validation, env-var substitution.
- **Four output formats** — CSV (Excel-friendly), YAML (machine-readable), Markdown (PR-friendly), **HTML (browser-ready, sortable, filterable, XSS-safe, dark-mode aware)**.
- **Self-analysis demo in CI** — every CI run produces a fresh `hotspot-demo-report-<N>` artifact you can download and open in a browser.
- **Comprehensive tests across every layer** — contract tests + unit + Spring Boot E2E, 0 failures.

---

## Prerequisites

| Use case          | Requirement                                                        |
|-------------------|--------------------------------------------------------------------|
| Building          | Any JDK 17+ on `PATH` (Gradle auto-provisions the Java 21 toolchain) |
| **Running the jar** | **JDK 21+ on `PATH`** — verify with `java -version` |
| Analysis target   | A directory that contains a `.git/` folder (a real git working tree) |

---

## Quick start

### 1. Get the jar (no build needed)

Download the self-contained runnable jar from the
[latest Release](https://github.com/baekchangjoon/hotspot-analysis/releases/latest)
(only a JDK 21 runtime is required):

```bash
curl -fsSL https://github.com/baekchangjoon/hotspot-analysis/releases/latest/download/hotspot.jar -o hotspot.jar
```

> To build from source instead (optional): `./gradlew bootJar` →
> `build/libs/hotspot-*.jar`. Swap `hotspot.jar` below for that path.

> **No JDK — Docker.** Mount the target repo at `/work`:
> ```bash
> docker run --rm -v "$PWD":/work ghcr.io/baekchangjoon/hotspot-analysis:latest \
>   analyze --config /work/hotspot.yml
> ```

### 2. Generate a sample config

```bash
java -jar hotspot.jar init -o hotspot.yml
```

### 3. Edit `hotspot.yml`

```yaml
analysis:
  target:
    type: local-git
    # Must be a directory that contains a .git/ folder
    path: /path/to/your/repo

  window:
    # Mode A: relative window (last N days from "now") -- safe default
    days: 365
    # Mode B: absolute ISO-8601 date range (use INSTEAD of `days`)
    # since: "2024-01-01"
    # until: "2026-01-01"

  scope:
    granularity: [file, method]
    include:
      # Single-module Spring Boot project
      - "src/main/java/**/*.java"
      # Multi-module Gradle/Maven project -- uncomment if your repo
      # has sub-modules (ftgo, jhipster, most real-world stacks).
      # - "**/src/main/java/**/*.java"
    exclude:
      - "**/generated/**"
      - "**/test/**"
      - "**/build/**"
      - "**/target/**"

  scoring:
    decayHalfLifeDays: 90   # half-life for recency decay (days)
    # excludeCoverage: false  # true → Composite = CC × Decay (coverage observational)

  # REST API endpoint hotspots (aggregates Spring controllers along the call graph).
  # The priority input for RestAssured test generation. Turn on for Spring apps.
  apiAnalysis:
    enabled: true
    sharedComponentMode: BOTH       # CUMULATIVE | SEPARATE | BOTH
    classpathDirectories:           # improves call-graph symbol resolution (optional)
      - build/libs

  # JaCoCo XML report (optional). Uncomment ONLY when you actually have a
  # report. Coverage then feeds the score (multiplier 1/(coverage+0.1)).
  # Pointing at a non-existent path prints a warning and proceeds without
  # coverage (scores stay correct).
  # jacocoReportPath: build/reports/jacoco/test/jacocoTestReport.xml

output:
  # Case-insensitive: csv | yaml | md | html (multiple allowed)
  formats: [csv, yaml, md, html]
  apiLayout: BOTH       # COMBINED (into hotspots.*) | STANDALONE (api_report.*) | BOTH
  path: ./hotspot-report
  topN: 20            # 0 = unlimited
```

### 4. Run analysis

```bash
java -jar hotspot.jar analyze --config hotspot.yml
```

Outputs:

```
hotspot-report/
├── file_hotspots.csv      ← CSV is split per granularity (9 columns for files, 14 for methods)
├── method_hotspots.csv
├── hotspots.yml           ← YAML/MD/HTML bundle both granularities in one document
├── hotspots.md
└── hotspots.html          ← open in any browser — sortable columns + filter box
```

> **Why are CSVs split but YAML/MD/HTML combined?**
> CSV is a single-header tabular format and the file (9 cols) and method
> (14 cols) reports can't share a header, so they are emitted as two files
> for Excel/Sheets ease of use. YAML, Markdown, and HTML are document
> formats that naturally hold both tables in one file — easier to attach
> to a PR (`.md`), feed downstream automation (`.yml`), or open in a browser
> as a self-contained evidence page (`.html`). A future `output.layout`
> option to flip this is tracked under Phase 2.

> **HTML report features.** `hotspots.html` is a single self-contained file
> (no remote CSS/JS, no CDN). Click any column header to sort
> ascending/descending; type into the search box to filter rows by path,
> class, method, or parameters. Light/dark mode follows your browser's
> preference. Every user-controlled value is HTML-escaped, so a malicious
> file path can't inject script tags.

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
| `--strict, -s`        |   | off  | Exit with code 3 when the result is empty (zero commits in window or zero files matching scope). Designed for CI gating. |

| Exit code | Meaning |
|:---:|---|
| 0 | Analysis completed; outputs were written |
| 1 | Configuration invalid / pipeline failure / unsupported target |
| 2 | Picocli usage error |
| 3 | `--strict` set and the analysis produced an empty result |

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
| `analysis.scoring.decayHalfLifeDays` | integer ≥ 1 | Half-life for recency decay; default 90 days |
| `analysis.scoring.excludeCoverage` | boolean | When `true`, Composite = CC × Decay and reports show raw line-coverage instead of the multiplier; default `false` |
| `analysis.apiAnalysis.enabled` | boolean | Turn on the REST API endpoint + shared-component granularities; default `false` |
| `analysis.apiAnalysis.sharedComponentMode` | `CUMULATIVE` \| `SEPARATE` \| `BOTH` | How methods shared by ≥2 endpoints are aggregated/reported; default `BOTH` |
| `analysis.apiAnalysis.classpathDirectories[]` | string[] | Dirs with dependency jars/classes to improve call-graph symbol resolution |
| `analysis.jacocoReportPath` | string | JaCoCo XML report path; enables the coverage multiplier `1/(coverage+0.1)`. Absent → multiplier 1.0 |
| `output.formats[]` | `CSV` \| `YAML` \| `MD` \| `HTML` | At least one |
| `output.apiLayout` | `COMBINED` \| `STANDALONE` \| `BOTH` | Where API/shared tables go: into `hotspots.*`, a standalone `api_report.*`, or both; default `BOTH` |
| `output.coverageBreakdown` | boolean | When `true` (and a JaCoCo report is supplied), also writes `coverage_breakdown.yml` — the calculation trace behind every coverage number (per-file counts; per-endpoint per-method covered/executable lines); default `false` |
| `output.path` | string | Output directory |
| `output.topN` | integer ≥ 0 | `0` means "all rows" |

Environment variables in any string value are substituted with `${VAR_NAME}`
syntax; lines starting with `#` (YAML comments) are left untouched so
documentation examples don't trigger errors.

---

## How the score is computed

Every report now carries **two scores** and **four input factors** side by side, in this canonical order:

| Column | Meaning |
|---|---|
| LOC | Current line count of the artifact |
| Revisions | Number of commits within the window that touched it |
| Simple Score | `Revisions × LOC` — Adam Tornhill's original signal |
| Recency Decay | `Σ exp(-ln(2) × Δt / halfLife)` over the same commits |
| Cognitive Complexity | SonarQube-inspired AST-walk score |
| Coverage Multiplier | `1 / (line_coverage + 0.1)` from JaCoCo XML; 1.0 if no report supplied |
| Composite Score | `Cognitive Complexity × Recency Decay × Coverage Multiplier` |

Rows are sorted by **Composite Score DESC** (ties broken by path / canonical signature).

CSVs split per granularity (9 columns for files, 14 for methods); YAML/MD/HTML bundle every granularity into one document.

> Per-granularity derivations — how Revisions / Recency Decay / Cognitive
> Complexity / Coverage are measured and combined for **file / method / REST API
> endpoint / shared component** — are in [`docs/scoring/`](docs/scoring/README.en.md),
> with source references and worked examples.

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

## Troubleshooting

If your first real run looks "empty", almost certainly one of these three.

### `Files: 0` — no source files matched `scope.include`

Java NIO's `**` glob does **not** match zero path segments. That creates
a subtle asymmetry between single-module and multi-module layouts:

| Repo shape | First Java path looks like | Pattern that works |
|---|---|---|
| Single-module | `src/main/java/com/foo/Hot.java` | `src/main/java/**/*.java` |
| Multi-module | `service-a/src/main/java/com/foo/Hot.java` | `**/src/main/java/**/*.java` |

If you don't know upfront which shape the target has, **list both
patterns**; the collector deduplicates matches:

```yaml
scope:
  include:
    - "src/main/java/**/*.java"        # catches single-module repos
    - "**/src/main/java/**/*.java"     # catches multi-module repos
```

Quick sanity check (Bash):

```bash
git -C <repo> ls-files | grep -E 'src/main/java/.*\.java$' | head
```

### `Commits: 0` — window had no Java-touching commit

The window is correct relative to *now*, but the repo may have been
inactive for years (a frozen reference implementation, an archived demo,
…). Switch to an absolute range that overlaps the actual activity:

```yaml
window:
  since: "2017-01-01"
  until: "2026-12-31"
```

Cross-check from the command line:

```bash
git -C <repo> log --since=<since> --until=<until> --name-only \
    --pretty=format: -- '*.java' | sort -u | head
```

> **CI tip:** add `--strict` to your `hotspot analyze` invocation. The
> command will exit with code **3** when commits or files come back empty,
> so a misconfigured CI pipeline fails loudly instead of producing empty
> reports.
>
> ```bash
> java -jar hotspot.jar analyze --config hotspot.yml --strict
> ```

### `UnsupportedClassVersionError` when running the jar

The bundled jar is compiled for Java 21. Even when the Gradle toolchain
auto-provisioned 21 to *build* it, **running** still needs JDK 21+ on
`PATH`:

```bash
java -version            # must report 21 or later
# macOS:
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
# Linux (sdkman):
sdk use java 21.0.4-tem
```

### `Could not parse GitHub repository` / `Authentication required`

For `target.type: github` you need a token. Either:

```yaml
target:
  type: github
  github:
    owner: my-org
    repo:  my-repo
    branch: main
    token: ${GITHUB_TOKEN}     # resolved from env at load time
```

…and `export GITHUB_TOKEN=…` before running. The Phase 1 CLI is wired
**end-to-end only for `local-git`**; the GitHub provider is verified by
WireMock contract tests. To analyse a GitHub repo today, clone it locally
and point `target.type: local-git` at the working tree.

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
./gradlew test            # comprehensive test suite, ~10s
./gradlew build           # full assembly
./gradlew check           # tests + static analysis
```

Continuous Integration runs on every push and on a **daily schedule**
(09:00 KST). Each run uploads the following artifacts (see the
[Actions tab](https://github.com/baekchangjoon/hotspot-analysis/actions)):

| Artifact | What's inside |
|---|---|
| `hotspot-jar-<N>` | The assembled fat jar (`hotspot-*.jar`) |
| `test-results-<N>` | JUnit XML for every test class |
| `test-report-<N>` | Gradle's full HTML test report |
| `test-summary-<N>` | Markdown summary surfaced on the GitHub Step Summary panel |
| `hotspot-demo-report-<N>` | **Self-analysis output** — `file_hotspots.csv`, `method_hotspots.csv`, `hotspots.yml`, `hotspots.md`, `hotspots.html`, plus the `hotspot.yml` used to produce them. Download and open `hotspots.html` directly in your browser. |

---

## Privacy

hotspot-analysis runs **entirely on your machine**. There is no telemetry,
no analytics, and no "phone home".

- **`local-git` target (the Phase 1 default):** the tool reads git history and
  Java source from the working tree you point it at, computes scores locally,
  and writes reports to the local `output.path` directory. **Nothing leaves your
  computer.**
- **`github` target:** the only outbound network traffic is to GitHub's REST
  API, authenticated with the token **you** supply via `${GITHUB_TOKEN}`. The
  token is read from your environment at load time and is never written to the
  reports.
- **Reports** (`hotspots.html` and friends) embed file paths, class/method
  names, and line counts from your code. Treat them as you would the source —
  the HTML report is fully self-contained (no CDN/remote calls) and
  HTML-escapes every value, but it still contains your code's structure, so
  share it deliberately.
- **The skill / plugin** simply drives this local CLI; installing it sends no
  code or analysis to any third party.

---

## License

[MIT](LICENSE) © 2026 baekchangjoon
