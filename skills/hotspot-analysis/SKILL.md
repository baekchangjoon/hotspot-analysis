---
name: hotspot-analysis
description: Use to produce a deterministic, reproducible, ranked prioritization of a Java codebase's REST API endpoints (and methods) for test generation — especially as the input that decides which RestAssured API tests to write first. Drives this repo's Java CLI over a local git working tree, combining recency-weighted git churn, SonarQube-style cognitive complexity, and JaCoCo coverage gap into a Composite Hotspot Score, and emits a machine-readable ranking (CSV/YAML/Markdown/HTML) plus a CI gating exit code. Method-level Java, hunk-accurate, no LLM guesswork. Based on Adam Tornhill's "Your Code as a Crime Scene".
---

# Hotspot analysis → test-generation prioritization (Java)

This skill is a **thin wrapper around a Java CLI**. The jar does the analysis
deterministically; the skill's job is to build it, configure it, run it, and
hand the **ranked, machine-readable output** to whatever generates tests
(typically **RestAssured** API tests, or unit tests for the top methods).

What makes it worth a separate step (vs. asking a model to "guess the risky
endpoints"):

- **Deterministic & reproducible** — pure JVM computation; same inputs → same
  scores and same ordering. No randomness, no model variance.
- **Method-level, hunk-accurate Java** — churn is attributed to the exact
  methods whose line ranges a commit's diff hunks touched, not the whole file.
- **Recency-weighted** — recent churn counts more via exponential decay
  (configurable half-life).
- **JaCoCo-integrated** — real line-coverage gaps raise priority of untested
  code; coverage can be a scoring input or an observational column.
- **CI-gating** — `--strict` returns a non-zero exit code on empty results.

The primary deliverable is a **priority queue of REST API endpoints**: for each
endpoint you get its HTTP method + route, the aggregated risk over its whole
call graph, the call graph itself, and a coverage signal — i.e. exactly what an
agent needs to decide *which endpoint to test first and which under-tested path
to target*.

## When to use

- "Generate RestAssured tests for this Spring app, most important endpoints first."
- "Which API endpoints / methods are riskiest and least tested? In what order?"
- Producing a deterministic prioritization that a test-generation step consumes.

Not for: non-Java repos; deciding test *content* (it ranks *what* to test, the
test generator decides *how*).

Target must be a directory containing a real `.git/` folder. Phase 1 runs
`local-git` end-to-end; `github` target needs a local clone (see Troubleshooting).

## Prerequisites

| Need | Requirement |
|---|---|
| Build the jar | Any JDK 17+ on `PATH` (Gradle auto-provisions the Java 21 toolchain) |
| **Run the jar** | **JDK 21+ on `PATH`** — verify `java -version` reports 21 or later |
| Analysis target | A directory with a `.git/` folder |
| API analysis (recommended) | `apiAnalysis.enabled: true`; ideally `classpathDirectories` for symbol resolution |
| Coverage signal (recommended) | A JaCoCo XML report from the **same** build |

## Workflow

Run from the `hotspot-analysis` project root (this skill's repo).

### 1. Build the jar (once)

```bash
./gradlew clean build
# → build/libs/hotspot-0.1.0-SNAPSHOT.jar
```

### 2. Generate a config

```bash
java -jar build/libs/hotspot-0.1.0-SNAPSHOT.jar init -o hotspot.yml
```

### 3. Configure for endpoint prioritization

Point `analysis.target.path` at the target repo, enable API analysis, and (if
available) supply a JaCoCo report. See **Config reference**. The key block:

```yaml
analysis:
  apiAnalysis:
    enabled: true
    sharedComponentMode: BOTH            # CUMULATIVE | SEPARATE | BOTH
    classpathDirectories:                # optional but improves call-graph resolution
      - build/libs
  jacocoReportPath: build/reports/jacoco/test/jacocoTestReport.xml
output:
  formats: [yaml, md, html]
  apiLayout: BOTH                        # COMBINED | STANDALONE | BOTH
  topN: 30
```

### 4. Analyze (add `--strict` in CI)

```bash
java -jar build/libs/hotspot-0.1.0-SNAPSHOT.jar analyze --config hotspot.yml --strict
```

Outputs land in `output.path`. With API analysis on and `apiLayout: BOTH`:

```
hotspot-report/
├── api_report.yml        ← STANDALONE: apiHotspots + sharedComponents (agent input)
├── hotspots.yml          ← COMBINED: file + method + api + shared in one doc
├── hotspots.md / .html   ← human-readable
├── file_hotspots.csv
└── method_hotspots.csv
```

### 5. Consume the ranking for RestAssured

Read `api_report.yml` and iterate `apiHotspots` in `compositeRank` order. Each
row carries:

| Field | Use for test generation |
|---|---|
| `httpMethod`, `route` | The request: `given()...when().<method>(route)` |
| `fqcn`, `method`, `parameters` | Controller signature → request body/param shape |
| `callGraph` | Reachable methods → which downstream logic the endpoint exercises |
| `coverageMultiplier` / `lineCoverage` | How under-tested the endpoint's logic is |
| `compositeRank` | **The order to write tests in** |
| `sharedComponents[]` | Methods many endpoints depend on — high-leverage to cover once |

Generate tests highest-rank first, targeting the least-covered paths in each
endpoint's call graph. **Do not fabricate the ranking — run the CLI and read the
actual file.**

## How the score is computed

Four input factors → two scores. Full per-granularity derivations (with source
references and worked examples) live in **[`docs/scoring/`](../../docs/scoring/README.md)**:
[file](../../docs/scoring/file.md) ·
[method](../../docs/scoring/method.md) ·
[REST API endpoint](../../docs/scoring/rest-api-endpoint.md) ·
[shared component](../../docs/scoring/shared-component.md).

| Factor / score | Definition |
|---|---|
| Revisions | Commits in the window that touched the artifact (method: diff-hunk overlap) |
| Recency Decay | `Σ exp(-ln(2)·Δt / halfLife)` over those commits — recent weighs more |
| Cognitive Complexity | SonarQube-style AST walk (file = sum of its methods) |
| Coverage Multiplier | `1/(lineCoverage + 0.1)` from JaCoCo; `1.0` if no report |
| Simple Score | `Revisions × LOC` (Tornhill's original) |
| Composite Score | `Cognitive Complexity × Recency Decay × Coverage Multiplier` |

For an **API endpoint**, each factor is aggregated over the controller method
**plus its whole call graph**; coverage is the average over those methods.
Sorted by Composite DESC, ties broken deterministically (`route`,`httpMethod`).

## Config reference

```yaml
analysis:
  target:
    type: local-git          # local-git | github (Phase 1 CLI: local-git end-to-end)
    path: /path/to/target/repo   # must contain a .git/ folder
  window:
    days: 365                # Mode A: relative window from now
    # since: "2024-01-01"    # Mode B: absolute ISO range (use INSTEAD of days)
    # until: "2026-01-01"
  scope:
    granularity: [file, method]
    include:
      - "src/main/java/**/*.java"     # single-module repos
      - "**/src/main/java/**/*.java"  # multi-module repos (list both if unsure)
    exclude:
      - "**/generated/**"
      - "**/test/**"
      - "**/build/**"
  scoring:
    decayHalfLifeDays: 90    # half-life for recency decay (days)
    excludeCoverage: false   # true → Composite = CC × Decay; coverage shown raw, not scored
  apiAnalysis:
    enabled: true            # off by default; required for api/shared granularities
    sharedComponentMode: BOTH        # CUMULATIVE | SEPARATE | BOTH
    classpathDirectories: []         # dirs with dependency jars/classes for symbol resolution
  jacocoReportPath: build/reports/jacoco/test/jacocoTestReport.xml   # optional
output:
  formats: [csv, yaml, md, html]   # case-insensitive; ≥1 required
  apiLayout: BOTH          # COMBINED (into hotspots.*) | STANDALONE (api_report.*) | BOTH
  path: ./hotspot-report
  topN: 30                 # 0 = all rows
```

Env vars substitute as `${VAR_NAME}` in any string value; YAML comment lines
(`#`) are left untouched.

### `sharedComponentMode`

- `CUMULATIVE` — shared methods counted inside every endpoint's aggregate; no separate list.
- `SEPARATE` — shared methods excluded from endpoint aggregates and reported once on their own.
- `BOTH` (default) — endpoint aggregates include them **and** a separate shared list is emitted.

### `analyze` options

| Option | Effect |
|---|---|
| `--config, -c <file>` | Path to the YAML config (required) |
| `--quiet, -q` | Suppress the stdout summary |
| `--strict, -s` | Exit code **3** on empty result (zero commits or zero files) — for CI gating |

Exit codes: `0` ok · `1` config/pipeline failure · `2` usage error · `3` `--strict` empty result.

## Troubleshooting

**`apiHotspots` is empty** even though endpoints exist:
- `apiAnalysis.enabled` must be `true`.
- Controllers need `@RestController`/`@Controller` + a mapping annotation
  (`@GetMapping`/`@PostMapping`/…/`@RequestMapping`).
- Call graphs need symbol resolution — add dependency jars/class dirs to
  `apiAnalysis.classpathDirectories` so cross-type calls resolve.

**`Files: 0`** — no source matched `scope.include`. Java NIO `**` does not match
zero path segments; list both `src/main/java/**/*.java` and
`**/src/main/java/**/*.java` (the collector deduplicates). Check:

```bash
git -C <repo> ls-files | grep -E 'src/main/java/.*\.java$' | head
```

**`Commits: 0`** — the window had no Java-touching commit. Switch to an absolute
`window.since`/`window.until` overlapping real activity.

**Every coverage multiplier is 10** — JaCoCo was supplied but its paths don't
match the analyzed files (so coverage reads as 0.0 → max penalty). Use a report
from the **same** build/module as the target source.

**`UnsupportedClassVersionError`** — the jar is compiled for Java 21; *running*
needs JDK 21+. `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` on macOS.

**`github` target — `Authentication required`.** Phase 1 wires `local-git`
end-to-end. Clone the GitHub repo locally and point `target.type: local-git` at it.

## Rules

- Treat scores as **evidence to prioritize attention**, not a verdict. Surface
  the factors (churn, recency, complexity, coverage) so the choice is explainable.
- Don't fabricate output — run the CLI and read the actual `api_report.yml` rows.
- Generate tests in `compositeRank` order; use `callGraph` + coverage to pick
  which paths to assert first.
- In CI, use `--strict` so a misconfigured run fails loudly instead of emitting
  empty reports.

## References

- Scoring derivations: [`docs/scoring/`](../../docs/scoring/README.md).
- Project README and `docs/` (architecture, advanced techniques, theory).
- Adam Tornhill, *Your Code as a Crime Scene*.
