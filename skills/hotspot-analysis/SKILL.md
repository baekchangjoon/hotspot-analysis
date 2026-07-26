---
name: hotspot-analysis
description: Use to produce a deterministic, reproducible, ranked prioritization of a Java codebase's REST API endpoints (and methods) for test generation — especially as the input that decides which RestAssured API tests to write first. Drives this repo's Java CLI over a local git working tree, combining recency-weighted git churn, SonarQube-style cognitive complexity, and JaCoCo coverage gap into a Composite Hotspot Score, and emits a machine-readable ranking (CSV/YAML/Markdown/HTML) plus a CI gating exit code. Method-level Java, hunk-accurate, no LLM guesswork. Based on Adam Tornhill's "Your Code as a Crime Scene".
license: MIT
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
| Get the jar | Nothing to build — `scripts/get-jar.sh` downloads the released fat jar (cached). Building from source instead needs any JDK 17+. |
| **Run the jar** | **No JDK required** — `scripts/ensure-java.sh` finds an installed Java 21+ or auto-downloads a Temurin 21 JRE (~46MB, sha256-verified, cached in `~/.cache/hotspot-analysis/jre`; refresh by deleting that dir) |
| Analysis target | A directory with a `.git/` folder |
| API analysis (recommended) | `apiAnalysis.enabled: true`; ideally `classpathDirectories` for symbol resolution |
| Coverage signal (recommended) | A JaCoCo XML report from the **same** build |

## Workflow

A convenience wrapper, [`scripts/run-analysis.sh`](scripts/run-analysis.sh),
resolves the jar (downloading the released fat jar if needed) and runs `analyze`.
The steps below show the explicit form.

1. **Get a runtime and the jar.** No build and no pre-installed JDK required —
   `ensure-java.sh` resolves an installed Java 21+ (or auto-downloads a
   Temurin JRE), and `get-jar.sh` downloads the released fat jar to a cache
   (or reuses a local build, or builds from source as a fallback):

   ```bash
   JAVA="$(skills/hotspot-analysis/scripts/ensure-java.sh)"  # prints a java 21+ path
   JAR="$(skills/hotspot-analysis/scripts/get-jar.sh)"       # prints the jar path
   # manual alternative (no clone needed):
   #   curl -fsSL https://github.com/baekchangjoon/hotspot-analysis/releases/latest/download/hotspot.jar -o hotspot.jar
   #   JAR=hotspot.jar
   # from-source alternative (needs the repo + a JDK):  ./gradlew bootJar
   ```

   Prefer no downloads at all? Use the Docker image, mounting the target repo at `/work`:
   `docker run --rm -v "$PWD":/work ghcr.io/baekchangjoon/hotspot-analysis:latest analyze --config /work/hotspot.yml`

2. **Generate a config.**

   ```bash
   "$JAVA" -jar "$JAR" init -o hotspot.yml
   ```

3. **Configure for endpoint prioritization.** Point `analysis.target.path` at
   the target repo, enable API analysis, and (if available) supply a JaCoCo
   report. If the user can't hand-write the YAML, **run the interview below**
   (["Configure interactively"](#configure-interactively-interview)) — ask, fill
   defaults, write the file. See **Config reference** for every key. The key block:

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

4. **Analyze** (add `--strict` in CI).

   ```bash
   "$JAVA" -jar "$JAR" analyze --config hotspot.yml --strict
   # or, in one shot (resolves the runtime AND the jar for you):
   #   skills/hotspot-analysis/scripts/run-analysis.sh hotspot.yml --strict
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

5. **Consume the ranking for RestAssured.** Read `api_report.yml` and iterate
   `apiHotspots` in `compositeRank` order. Field-by-field schema:
   [`references/api-report-schema.md`](references/api-report-schema.md). Each row
   carries:

   | Field | Use for test generation |
   |---|---|
   | `httpMethod`, `route` | The request: `given()...when().<method>(route)` |
   | `fqcn`, `method`, `parameters` | Controller signature → request body/param shape |
   | `callGraph` | Reachable methods → which downstream logic the endpoint exercises |
   | `coverageMultiplier` / `lineCoverage` | How under-tested the endpoint's logic is |
   | `compositeRank` | **The order to write tests in** |
   | `sharedComponents[]` | Methods many endpoints depend on — high-leverage to cover once |

   Generate tests highest-rank first, targeting the least-covered paths in each
   endpoint's call graph. **Do not fabricate the ranking — run the CLI and read
   the actual file.**

## Configure interactively (interview)

A freshly-installed user usually can't write `hotspot.yml` cold. **Don't make
them.** Generate a starting file (`init`), then fill it by Q&A: auto-detect what
you can, ask only what's ambiguous, confirm, and write the file.

Procedure:

1. **Detect first, then ask.** Inspect the target repo to pre-fill defaults so
   most questions become a yes/no confirmation:
   - Spring app? — `grep -rl "@RestController\|@RequestMapping" <repo>/src` → if
     hits, default `apiAnalysis.enabled: true`.
   - Multi-module? — more than one `src/main/java` root → include both globs.
   - JaCoCo report present? — look for `**/jacoco/**/*.xml` (e.g.
     `build/reports/jacoco/test/jacocoTestReport.xml`) → default `jacocoReportPath`.
   - Built classes/jars? — `build/libs`, `build/classes` → default
     `apiAnalysis.classpathDirectories`.
   - Recent activity? — `git -C <repo> log -1 --format=%cd` → if older than a
     year, propose absolute `window.since`/`until` instead of `days`.

2. **Ask, one decision at a time** (skip any you confidently detected — just
   state the default and let the user correct it):

   | # | Question | Maps to | Default |
   |---|---|---|---|
   | 1 | Which repo to analyze? (path to the `.git/` working tree) | `analysis.target.path` | — (required) |
   | 2 | Prioritize REST API endpoints for test generation? | `apiAnalysis.enabled` | `true` if Spring detected |
   | 3 | Count churn over the last N days, or an absolute date range? | `window.days` or `window.since`/`until` | `days: 365` |
   | 4 | File-level, method-level, or both? | `scope.granularity` | `[file, method]` |
   | 5 | Have a JaCoCo coverage report? Where? | `analysis.jacocoReportPath` | detected path, else omit |
   | 6 | Dirs with built classes/dep jars (improves call graph)? | `apiAnalysis.classpathDirectories` | detected, else `[]` |
   | 7 | Shared-method handling? | `apiAnalysis.sharedComponentMode` | `BOTH` |
   | 8 | Output formats / where / how many rows? | `output.formats`/`path`/`topN` | `[csv,yaml,md,html]`, `./hotspot-report`, `30` |
   | 9 | Fail the run if the result is empty (CI)? | pass `--strict` at run time | no |

3. **Write `hotspot.yml`** from the answers, **show it back** to the user for a
   final OK, then run step 4. If a tool like `AskUserQuestion` is available,
   prefer it for crisp multiple-choice prompts; otherwise ask in plain text.

Keep it short: a typical session is "confirm repo path → confirm Spring/API on →
accept window default → confirm the detected JaCoCo path → go".

## How the score is computed

Four input factors → two scores. Full per-granularity derivations (with source
references and worked examples) live in **[`docs/scoring/`](../../docs/scoring/README.en.md)**:
[file](../../docs/scoring/file.en.md) ·
[method](../../docs/scoring/method.en.md) ·
[REST API endpoint](../../docs/scoring/rest-api-endpoint.en.md) ·
[shared component](../../docs/scoring/shared-component.en.md).

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
  coverageBreakdown: false # true → also write coverage_breakdown.yml: the audit
                           # trail behind every coverage number (per-file counts;
                           # per-endpoint per-method covered/executable lines)
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
| `--output-dir, -o <dir>` | Directory to write the reports into (overrides `output.path`) |
| `--quiet, -q` | Suppress the stdout summary |
| `--strict, -s` | Exit code **3** on empty result (zero commits or zero files) — for CI gating |

Exit codes: `0` ok · `1` config/pipeline failure · `2` usage error · `3` `--strict` empty result.

## Decision rules (IF → THEN)

- **IF** the user wants the order to write API tests in **THEN** read
  `api_report.yml` and iterate `apiHotspots` by ascending `compositeRank`.
- **IF** `apiHotspots` is empty but the app clearly has endpoints **THEN** check,
  in order: `apiAnalysis.enabled: true`, controllers carry
  `@RestController`/`@Controller` + a mapping annotation, and
  `apiAnalysis.classpathDirectories` includes the dependency jars/classes so
  cross-type calls resolve. Do **not** report "no endpoints".
- **IF** every `coverageMultiplier` is `10` (or every `lineCoverage` is `0`)
  **THEN** the JaCoCo report doesn't match the analyzed sources — supply a report
  from the **same** build/module; do not conclude "nothing is tested".
  (A report with zero covered lines overall — generated without test execution
  data — is auto-detected: the CLI warns and proceeds without coverage,
  multiplier `1.0`. Regenerate it, e.g. `./gradlew test jacocoTestReport`.)
- **IF** the summary shows `Files: 0` **THEN** fix `scope.include` (single-module
  needs `src/main/java/**/*.java`, multi-module needs `**/src/main/java/**/*.java`;
  list both).
- **IF** the summary shows `Commits: 0` **THEN** widen `window.days` or switch to
  absolute `window.since`/`window.until` overlapping real activity.
- **IF** `target.type` is `github` **THEN** clone the repo locally and re-run with
  `target.type: local-git` (Phase 1 wires only `local-git` end-to-end).
- **IF** running in CI **THEN** pass `--strict` so an empty result fails loudly.
- **IF** you only need observational coverage, not coverage-driven scoring
  **THEN** set `scoring.excludeCoverage: true` (Composite becomes `CC × Decay`).

## Anti-patterns & pitfalls

- **Don't fabricate or estimate the ranking.** Run the jar and read the actual
  `api_report.yml`; the whole point is determinism, not a model guess.
- **Don't treat an empty `apiHotspots` as "no endpoints."** It almost always
  means `apiAnalysis` is off or the call graph couldn't resolve (missing
  `classpathDirectories`).
- **Don't feed a JaCoCo report from a different module/build.** Path mismatch
  reads as 0% coverage → every multiplier maxes at 10 and the ranking is bogus.
- **Don't run the jar on JDK < 21** — it's compiled for 21 (`UnsupportedClassVersionError`);
  `ensure-java.sh` guards this by only accepting 21+.
- **Don't analyze generated or build output** — keep `**/generated/**`,
  `**/build/**`, `**/target/**`, `**/test/**` in `scope.exclude`.
- **Don't present the Composite Score as a verdict.** It's prioritization
  evidence; surface the factors (churn, recency, complexity, coverage) so the
  choice is explainable.
- **Don't reorder by Simple Score when the goal is risk.** `compositeRank`, not
  `simpleRank`, is the test-priority signal.

## Testing

The project's own suite exercises every layer (parser, scoring, output, E2E):

```bash
./gradlew test            # comprehensive; run before trusting a build
```

Skill-level smoke check — analyze this very repo and assert a non-empty result:

```bash
bash -n skills/hotspot-analysis/scripts/ensure-java.sh skills/hotspot-analysis/scripts/get-jar.sh skills/hotspot-analysis/scripts/run-analysis.sh
JAVA="$(skills/hotspot-analysis/scripts/ensure-java.sh)"  # resolves/downloads a java 21+
JAR="$(skills/hotspot-analysis/scripts/get-jar.sh)"   # resolves/downloads the jar
"$JAVA" -jar "$JAR" init -o /tmp/h.yml -f
# set analysis.target.path in /tmp/h.yml to this repo's absolute path, then:
"$JAVA" -jar "$JAR" analyze --config /tmp/h.yml --strict
echo "exit=$?"   # 0 = produced output; 3 = empty (misconfigured)
```

A green `./gradlew test` plus a `0` exit on the smoke run means the skill's
toolchain is sound end-to-end.

## Changelog

- **0.1.5** — **zero-config**: `analyze` now runs without a config file
  (auto-detects git root, single/multi-module layout, JaCoCo report, Spring
  API), takes an optional `[path]`, `--print-config` dumps the synthesized
  config, the first run prints the top-3 hotspots + the report path, and
  linked git worktrees are supported. A one-line installer
  (`curl ... install.sh | bash`) provides the `hotspot` command. And the
  JDK-21 friction is gone: `scripts/ensure-java.sh` (and the
  `hotspot` wrapper installed by install.sh) finds an installed Java 21+ or
  auto-downloads a sha256-verified Temurin 21 JRE; `brew install
  baekchangjoon/tap/hotspot` installs with the JDK as a brew dependency; each
  release ships self-contained `hotspot-<tag>-<os>-<arch>.tar.gz` archives
  (bundled JRE, verified on 4 native CI runners before attach); an all-zero
  JaCoCo report (no execution data) now warns and disables coverage instead
  of silently inflating every multiplier to 10x; `analyze -o/--output-dir`
  overrides the report directory.
- **0.1.4** — endpoint coverage is now line-weighted (Σcovered/Σexecutable over
  the call graph) instead of a mean of per-method ratios, so a large untested
  method can no longer hide behind a small covered one; new opt-in
  `output.coverageBreakdown` writes `coverage_breakdown.yml`, the calculation
  trace behind every coverage number; releases enforce 4-way version
  consistency (tag = gradle = CLI = plugin/marketplace manifests).
- **0.1.3** — one-click `release` button + skills-validation CI gate + tag
  protection; the button reliably fans out to jar/image via `workflow_call`
  (a GITHUB_TOKEN-created release doesn't re-trigger event workflows).
- **0.1.2** — releases are now event-driven: every published release (incl. one
  created by `gh skill publish`) auto-attaches `hotspot.jar` and builds the
  Docker image, so a new release never breaks the download. `license` added to
  frontmatter.
- **0.1.1** — distribute the jar via GitHub Releases (version-stable
  `hotspot.jar` asset) + ghcr Docker image; a missing `jacocoReportPath` now
  warns and disables coverage instead of silently penalizing every artifact.
- **0.1.0** — initial skill: file / method / REST API endpoint / shared-component
  prioritization driving the Phase 1 CLI; RestAssured consumption guide;
  `apiAnalysis` + JaCoCo + `--strict` exposed; per-granularity scoring docs.

## References

- API report field schema: [`references/api-report-schema.md`](references/api-report-schema.md).
- Scoring derivations: [`docs/scoring/`](../../docs/scoring/README.en.md).
- Jar resolver / wrapper: [`scripts/get-jar.sh`](scripts/get-jar.sh) · [`scripts/run-analysis.sh`](scripts/run-analysis.sh).
- Prebuilt jar: [GitHub Releases](https://github.com/baekchangjoon/hotspot-analysis/releases/latest).
- Project README and `docs/` (architecture, advanced techniques, theory).
- Adam Tornhill, *Your Code as a Crime Scene*.
