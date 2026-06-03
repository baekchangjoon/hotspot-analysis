---
name: hotspot-analysis
description: Use to find where a Java codebase most likely hides bugs — rank files and methods by a Composite Hotspot Score that combines recency-weighted git churn, cognitive complexity, and test-coverage gap — so the user can focus testing or refactoring on the highest-risk code. Drives this repo's Java CLI against a local git working tree and emits CSV / YAML / Markdown / HTML reports. Based on Adam Tornhill's "Your Code as a Crime Scene".
---

# Hotspot analysis for Java codebases

A *hotspot* is code that changes often AND is hard to understand AND is poorly
tested — the intersection where bugs cluster. This skill runs the
`hotspot-analysis` CLI over a **local git repository** to rank Java files and
methods by a **Composite Hotspot Score**, so effort goes where the history says
it pays off.

```
Composite Score = Cognitive Complexity × Recency Decay × Coverage Multiplier
```

## When to use

- "Where should we add tests / refactor first in this Java repo?"
- "Which files are the riskiest?" / "Find the hotspots."
- Prioritising technical-debt paydown with evidence from git history, not gut feel.

Target must be a directory containing a real `.git/` folder. Phase 1 runs
`local-git` end-to-end; `github` target needs a token (see Troubleshooting).

## Prerequisites

| Need | Requirement |
|---|---|
| Build the jar | Any JDK 17+ on `PATH` (Gradle auto-provisions the Java 21 toolchain) |
| **Run the jar** | **JDK 21+ on `PATH`** — verify `java -version` reports 21 or later |
| Analysis target | A directory with a `.git/` folder |

## Workflow

Run these from the `hotspot-analysis` project root (this skill's repo).

### 1. Build the jar (once)

```bash
./gradlew clean build
# → build/libs/hotspot-0.1.0-SNAPSHOT.jar
```

### 2. Generate a config

```bash
java -jar build/libs/hotspot-0.1.0-SNAPSHOT.jar init -o hotspot.yml
```

### 3. Edit `hotspot.yml` for the target repo

Minimum: point `analysis.target.path` at the target's git working tree, set the
window, and list at least one `include` glob. See **Config reference** below.

### 4. Analyze

```bash
java -jar build/libs/hotspot-0.1.0-SNAPSHOT.jar analyze --config hotspot.yml
```

Outputs land in `output.path`:

```
hotspot-report/
├── file_hotspots.csv     ← per-file table (9 cols)
├── method_hotspots.csv   ← per-method table (14 cols)
├── hotspots.yml          ← both granularities, machine-readable
├── hotspots.md           ← both granularities, PR-friendly
└── hotspots.html         ← self-contained, sortable + filterable, open in a browser
```

CSV is split per granularity (file vs method headers differ); YAML/MD/HTML
bundle both tables in one document.

### 5. Interpret

Rows are sorted by **Composite Score DESC**. The top rows are the hotspots —
report them to the user with their factors so the recommendation is explainable.

| Column | Meaning |
|---|---|
| LOC | Current line count |
| Revisions | Commits in the window that touched it |
| Simple Score | `Revisions × LOC` — Tornhill's original signal |
| Recency Decay | `Σ exp(-ln(2) × Δt / halfLife)` — recent churn weighs more |
| Cognitive Complexity | SonarQube-inspired AST-walk score |
| Coverage Multiplier | `1 / (line_coverage + 0.1)` from JaCoCo XML; `1.0` if no coverage supplied |
| Composite Score | `Cognitive Complexity × Recency Decay × Coverage Multiplier` |

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
output:
  formats: [csv, yaml, md, html]   # case-insensitive; ≥1 required
  path: ./hotspot-report
  topN: 20                  # 0 = all rows
```

Env vars substitute as `${VAR_NAME}` in any string value; YAML comment lines
(`#`) are left untouched.

### `analyze` options

| Option | Effect |
|---|---|
| `--config, -c <file>` | Path to the YAML config (required) |
| `--quiet, -q` | Suppress the stdout summary |
| `--strict, -s` | Exit code **3** on empty result (zero commits or zero files) — for CI gating |

Exit codes: `0` ok · `1` config/pipeline failure · `2` usage error · `3` `--strict` empty result.

## Troubleshooting

An "empty" first run is almost always one of these three.

**`Files: 0` — no source matched `scope.include`.** Java NIO `**` does not match
zero path segments, so single-module vs multi-module layouts need different
globs. List both `src/main/java/**/*.java` and `**/src/main/java/**/*.java`; the
collector deduplicates. Sanity check:

```bash
git -C <repo> ls-files | grep -E 'src/main/java/.*\.java$' | head
```

**`Commits: 0` — window had no Java-touching commit.** The repo may have been
inactive in the relative window. Switch to an absolute range that overlaps real
activity (`window.since` / `window.until`). Cross-check:

```bash
git -C <repo> log --since=<since> --until=<until> --name-only \
    --pretty=format: -- '*.java' | sort -u | head
```

**`UnsupportedClassVersionError`** — the jar is compiled for Java 21; *running*
needs JDK 21+ even if Gradle auto-provisioned 21 to build. `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` on macOS.

**`github` target — `Authentication required`.** Provide a token via env:

```yaml
target:
  type: github
  github:
    owner: my-org
    repo: my-repo
    branch: main
    token: ${GITHUB_TOKEN}
```

## Rules

- Treat scores as **evidence to focus attention**, not a verdict. Surface the
  factors (churn, complexity, coverage) so the user can judge.
- Don't fabricate output — run the CLI and report the actual top rows.
- For CI gating, prefer `--strict` so a misconfigured run fails loudly instead
  of emitting empty reports.

## References

- Project README and `docs/` (architecture, advanced techniques, theory).
- Adam Tornhill, *Your Code as a Crime Scene*.
