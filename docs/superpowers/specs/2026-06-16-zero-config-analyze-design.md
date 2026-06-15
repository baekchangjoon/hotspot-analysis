# Zero-Config `analyze` — Design

- Date: 2026-06-16
- Status: Draft (pending review)
- Branch: `worktree-zero-config-analyze`

## Problem

Today the path to a first hotspot report is four steps with a manual editing
stage in the middle:

```
download jar → init (writes hotspot.yml) → edit hotspot.yml → analyze --config hotspot.yml
```

The middle edit is the dominant friction: before running anything the user must
already know

- the git root path (`analysis.target.path`),
- single- vs multi-module include globs (`src/main/java/**` vs `**/src/main/java/**`),
- the JaCoCo report path (if any),
- whether to enable Spring API analysis.

All of these are **derivable from the project on disk**. The tool currently
pushes that derivation onto the user.

## Goal

Let a user run, **from the repository root** (or by passing the root as
`[path]`), with **no configuration file**:

```
hotspot analyze
```

and get a report. Four steps collapse to one. The "learn the YAML glob syntax"
cost disappears for the common case, while `--config` remains fully supported
for custom setups.

Non-goals (YAGNI for this change):

- No per-field CLI override flags (e.g. `--window-days`). `--config` covers
  customisation; zero-config covers the common path.
- No upward `.git` discovery (walking parent directories). The base path must
  itself be the git work tree root; running from a subdirectory is an error
  with a hint to pass `[path]`.
- No multi-module JaCoCo aggregation. The config model carries a single
  `jacocoReportPath`; zero-config probes only the base-level report (see
  Detection Rules). Aggregating per-submodule coverage is out of scope.
- No interactive prompts / wizard.
- No new output formats or scoring changes.

## CLI Surface

`AnalyzeCommand` gains an optional positional argument and loses the
`required = true` on `--config`:

```
hotspot analyze [path] [--config <file>] [--print-config] [-q] [-s]
```

- `[path]` — optional positional, default = current working directory. The
  repository to analyse in zero-config mode.
- `--config <file>` — now **optional**. When present, the existing file-based
  path runs unchanged.
- `--print-config` — synthesize the config, write it to **stdout as YAML**, and
  exit 0 **without running the analysis**. The graduation path: redirect to a
  file and start customising.
- `-q/--quiet`, `-s/--strict` — unchanged.

### Mutual exclusivity

`--config` and `[path]` are mutually exclusive. Supplying both is a usage error
(exit 1) because it is ambiguous which is the source of truth.

### Dispatch

Validation runs **before** any config loading, so illegal flag combinations
fail fast and never trigger file reads or YAML parsing:

```
# 1. Preflight validation (usage errors, exit 1, before any I/O)
if --config present AND [path] present:        error "mutually exclusive"
if --config present AND --print-config:        error "print-config is zero-config only"

# 2. Build the config
if --config present:    config = ConfigLoader.load(file)        (existing behaviour)
else:                   config = ConfigSynthesizer.synthesize([path] || cwd)

# 3. Act
if --print-config:      print config as YAML to stdout, exit 0   (no analysis)
else:                   run analysis as today
```

`--print-config` only applies in zero-config mode; the preflight check above
guarantees it is never combined with `--config`.

## Architecture

A new `ConfigSynthesizer` `@Component`, symmetric to `ConfigLoader` and
injected into `AnalyzeCommand` the same way `ConfigLoader` is. It has **no
injected Spring collaborators** (its only input is the base `Path`); `@Component`
is used purely so it participates in the existing constructor-injection wiring:

```
ConfigSynthesizer.synthesize(Path basePath) -> AnalysisConfig
```

It applies the detection rules below and returns a **fully-populated, valid**
`AnalysisConfig` (the same record tree `ConfigLoader` produces). Keeping it a
separate component:

- isolates detection rules for unit testing,
- keeps `AnalyzeCommand` a thin dispatcher,
- mirrors the existing `ConfigLoader` pattern.

#### `--print-config` serialization contract

Serialisation uses a YAML `ObjectMapper` configured for **output** (not the
loader's parse-tuned mapper). It MUST:

- **omit null fields** (`JsonInclude.Include.NON_NULL`) so absent values such as
  `jacocoReportPath`, `target.github`, and `window.since/until` do not appear as
  explicit `null` keys;
- **write dates as ISO-8601 strings** (`WRITE_DATES_AS_TIMESTAMPS` disabled via
  the JavaTime module) — defensive; synthesized configs use `window.days`, so no
  `LocalDate` is emitted in practice;
- use `LOWER_CAMEL_CASE` naming, matching the loader.

Enums serialise via `name()` (e.g. `LOCAL_GIT`, `FILE`, `CSV`). These re-load
correctly because each enum's `@JsonCreator` upper-cases and `-`→`_` normalises
input, so the printed YAML round-trips through `ConfigLoader` to an equivalent
config (verified by acceptance test E2E #5).

To avoid duplicating mapper setup, the output mapper lives in a small dedicated
helper (e.g. a `ConfigSerializer` component or a static factory) rather than
being bolted onto `ConfigLoader`.

Failures (not a git work tree, no Java sources) throw a dedicated
`ConfigSynthesisException` carrying a human-readable message + hint;
`AnalyzeCommand` maps it to exit 1, mirroring how `ConfigLoadException` is
handled today.

## Detection Rules

Base path = `[path]` if given, else current working directory, normalised to
absolute.

| Field | Rule |
|---|---|
| `target` | `type = local-git`, `path = base`. **If `base/.git` does not exist (as either a file or a directory) → error.** A `.git` *file* is valid: linked worktrees and submodules store a `gitdir:` pointer file, and JGit `Git.open(base)` (used by `LocalGitProvider`) opens these correctly. No upward walk. |
| `window.days` | `365` (fixed default; matches the template's safe default). |
| `scope.granularity` | `[file, method]`. |
| `scope.include` | If `base/src/main/java` is a directory → `["src/main/java/**/*.java"]` (single module). Else if any `src/main/java` directory exists below base (bounded scan, see below) → `["**/src/main/java/**/*.java"]` (multi-module). Else → **error**. |
| `scope.exclude` | `["**/generated/**", "**/test/**", "**/build/**", "**/target/**"]`. |
| `scoring` | Defaults: `decayHalfLifeDays = 90`, `excludeCoverage = false`. |
| `jacocoReportPath` | First existing of: `build/reports/jacoco/test/jacocoTestReport.xml` (Gradle), then `target/site/jacoco/jacoco.xml` (Maven), probed **at base only**. Else omit (null). Multi-module per-submodule reports are not aggregated — see Non-goals. |
| `apiAnalysis` | `enabled = true` iff a build file (`build.gradle`, `build.gradle.kts`, or `pom.xml`) **at base or at any detected module root** contains the substring `spring-boot-starter-web`, `spring-webmvc`, or `spring-web`; else `false`. `sharedComponentMode = BOTH`. `classpathDirectories` = the subset of `["build/classes/java/main", "target/classes", "build/libs"]` (in that priority order) that exist under base; `[]` if none. |
| `output` | `formats = [csv, yaml, md, html]`, `path = "./hotspot-report"`, `topN = 50` (matches the `init` template, so `init` and zero-config produce the same row count), `apiLayout = BOTH`, `coverageBreakdown = false`. |

### Module / `src/main/java` scan

Both `scope.include` selection and submodule Spring detection share one bounded
directory scan rooted at base. To stay cheap and avoid descending into build
output, the scan:

- skips directory names `build`, `target`, `.git`, `node_modules`, `.gradle`,
- is bounded so a module root may sit up to **3 directories below base** (i.e.
  the `src/main/java` directory is found at a `Files.walk` depth of up to 5
  segments: `group/module/src/main/java`).

This covers single-level (`module/src/main/java`) and nested Gradle group
layouts (`group/module/src/main/java`) without a full-tree walk. A "module root"
is the directory containing a discovered `src/main/java`; its build file
(`build.gradle`, `build.gradle.kts`, `pom.xml`) feeds Spring detection.

### Spring detection signal

Build-file substring match (cheap, stable) rather than scanning Java sources for
`@RestController`. Enabling API analysis when no controllers exist produces an
empty API section (harmless); the bigger risk is missing controllers, so the
signal biases toward ON when Spring web is on any build file. Build files at
base **and at each detected module root** are inspected, so multi-module
projects that declare Spring only in a submodule are detected correctly.
`--config` overrides the decision either way.

## Zero-config summary (stderr)

When running in zero-config mode and not `--quiet`, before analysis a detection
summary is written to **stderr** (so it never pollutes `-q`/piped stdout):

```
Detected (zero-config):
  Repo:           /path/to/repo (.git found)
  Module layout:  multi-module (**/src/main/java)
  JaCoCo:         build/reports/jacoco/test/jacocoTestReport.xml
  API analysis:   ON (spring-web detected)
  → run with --print-config to save this as hotspot.yml
```

JaCoCo line shows `none` when not found; API line shows `OFF (no spring-web on build)`.

## Error Handling

All exit 1, message + hint on stderr:

- No `.git` (neither file nor directory): `ERROR: not a git work tree: <path>. Pass a path to a git repo, or use --config.`
- No Java sources: `ERROR: no Java sources found under <path> (looked for src/main/java). Pass a [path], or use --config for a custom scope.`
- `--config` + `[path]`: `ERROR: --config and [path] are mutually exclusive.`
- `--print-config` + `--config`: `ERROR: --print-config applies only to zero-config mode (remove --config).`

## Testing

### E2E / acceptance (outer loop)

Reuses the existing out-of-process black-box CLI harness (the same level the
current `analyze`/`init` E2E tests use). Authored first, expected red until
implemented.

1. **Single-module zero-config**: `analyze` (no config) in a single-module git
   fixture → exit 0, `hotspot-report/` written, stderr summary shows
   `single-module`.
2. **Multi-module via positional**: `analyze <path>` at a multi-module git
   fixture → detects `**/src/main/java`, non-empty results, exit 0.
3. **Not a git work tree**: `analyze` in a dir without `.git` → exit 1, stderr
   `not a git work tree`.
4. **No Java sources**: `analyze` in a git repo with no `src/main/java` → exit 1,
   stderr hint.
5. **`--print-config`**: prints YAML to stdout, writes **no** report dir, exit 0;
   the printed YAML, saved and re-run via `--config`, re-loads through
   `ConfigLoader` and yields an equivalent `AnalysisConfig` (round-trip).
6. **JaCoCo auto-detect**: fixture containing a JaCoCo XML at the Gradle path →
   detected, coverage column populated in output.
7. **Mutually exclusive**: `analyze --config x.yml somepath` → exit 1.
8. **Linked worktree**: `analyze` in a git **worktree** (where `.git` is a
   pointer *file*, not a directory) → exit 0, analysis runs (guards the
   regression the reviewers caught).

### Unit (`ConfigSynthesizer`)

- Single- vs multi-module `include` selection; exclude defaults.
- `.git` accepted as both a directory and a pointer file; rejected when absent.
- JaCoCo Gradle path preferred over Maven; null when neither exists.
- Spring detection ON via base build file; ON via a submodule build file only;
  OFF when no build file matches.
- `classpathDirectories`: includes `build/classes/java/main`, `target/classes`,
  `build/libs` in priority order, only those that exist; `[]` when none.
- Error cases: not a git work tree; no Java sources.
- Module scan ignores `build/`/`target/` and respects the depth bound
  (finds `group/module/src/main/java`).

### Regression

The full existing suite stays green (run `./gradlew test` for the current
count). `analyze --config` behaviour is untouched.

## Definition of Done

- All 8 E2E/acceptance tests pass (including the linked-worktree case).
- `ConfigSynthesizer` unit tests pass.
- Full regression suite green.
- README.md and README.en.md document the zero-config quick start and the
  limitations (root-only execution, multi-module JaCoCo not aggregated).

## Affected Files (anticipated, repo-relative)

- `src/main/java/io/github/baekchangjoon/hotspotanalysis/cli/AnalyzeCommand.java`
  — optional `--config`, positional `[path]`, `--print-config`, preflight
  mutual-exclusion checks, dispatch, stderr summary.
- `src/main/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigSynthesizer.java`
  *(new)* — detection rules.
- `src/main/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigSynthesisException.java`
  *(new)*.
- `src/main/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigSerializer.java`
  *(new)* — output-tuned YAML mapper for `--print-config` (NON_NULL, ISO dates).
- `README.md`, `README.en.md` — zero-config section + documented limitations
  (root-only, multi-module JaCoCo).
- Tests *(new/extended)*:
  - `src/test/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigSynthesizerTest.java` *(new)*
  - `src/test/java/io/github/baekchangjoon/hotspotanalysis/HotspotCliE2ETest.java` — new zero-config E2E cases
  - `src/test/java/io/github/baekchangjoon/hotspotanalysis/cli/AnalyzeCommandTest.java` — dispatch/flag-validation cases
