# Session Handoff — Hotspot Analysis Project

> Status: Phase 1 complete + post-merge documentation/UX hardening.
> Audience: The next LLM/engineer picking up this codebase mid-flight.
> Owner: Baek (`baekchangjoon`)
> Last updated: 2026-05-21

## 0. How to use this document

Read top-to-bottom **once** before responding to the user. The repo follows
strict conventions (user rules, package naming, TDD, etc.) and pattern
matching from prior chats; jumping in without the context below will
produce inconsistent suggestions. After reading, skim Section 9 for the
exact terminal commands needed to reach a working state in <5 minutes.

---

## 1. Project mission

Identify and rank **hotspots** — source code that combines high change
frequency with significant complexity — so testing effort can be invested
where the historical evidence says bugs are most likely to live (Adam
Tornhill, *Your Code as a Crime Scene*). The project is delivered in four
phases:

| Phase | Goal | Status |
|---:|---|:---:|
| 1 | CLI prototype (file + method scoring, three output formats) | ✅ Complete |
| 2 | Extended CLI (parameter combinations, coverage integration, more languages) | 🚧 Not started |
| 3 | REST API backend exposing the analysis as a service | ⏳ |
| 4 | Front-end visualisation on top of the backend | ⏳ |

---

## 2. Current repository state

| Item | Value |
|---|---|
| GitHub repo | https://github.com/baekchangjoon/hotspot-analysis |
| Default branch | `main` |
| Recent PRs | **#1** Phase 1 CLI + CI (merged), **#2** SESSION-HANDOFF.md (merged), **#3** HTML output + CI demo artifact (verify status with `gh pr list --state all`) |
| Latest fat jar | `build/libs/hotspot-0.1.0-SNAPSHOT.jar` (Java 21) |
| Test suite | **145 tests**, 0 failures, ~10 s |
| Output formats | `CSV`, `YAML`, `MD`, **`HTML`** (self-contained, sortable, filterable, XSS-safe) |
| CI | `.github/workflows/ci.yml` — push / PR / daily cron (`0 0 * * *` UTC) / `workflow_dispatch`. Uploads `hotspot-demo-report-<run>` artifact with all four output formats so reviewers can open `hotspots.html` directly. |

### Latest functional verification (run against ChrisRichardson/ftgo-application)

| Window | Files matched | Methods | Top score |
|---|---:|---:|---:|
| `since: 2017-01-01, until: 2022-12-31` + `**/src/main/java/**/*.java` | 288 | 1010 | 4347 (`OrderService.java`, rev=23 × loc=189) |

Execution time on M-series Mac: **~2.3 s** (warm), **~6 s** (cold JVM).

---

## 3. Tech stack and key decisions

| Layer | Choice | Why |
|---|---|---|
| Language / runtime | Java 21 LTS | User's stack majority is Java 21 + Spring Boot 3 |
| Framework | Spring Boot 3.3.x | DI for Picocli sub-commands, future REST API in Phase 3 |
| Build | Gradle 8.10.2 (Kotlin DSL) + Foojay toolchain auto-provisioning | Predictable JDK 21 on any host |
| CLI | Picocli 4.7.x with Spring `IFactory` | Strong typing, env-var aware, fits DI |
| Config | Jackson YAML + Jakarta Bean Validation | Strong schema + env-var substitution + readable validation errors |
| Local VCS | JGit (`org.eclipse.jgit`) | Pure-Java, no native git binary required |
| Remote VCS | `org.kohsuke:github-api` + WireMock (test only) | Mature, hermetically testable |
| Java parsing | JavaParser 3.26 (symbol-solver) | Records / sealed / switch-expr / pattern-matching ready |
| Scoring formula | `revisions × loc` (`SIMPLE`) | Phase 1 only. Phase 2 should add `COMPOSITE` (entropy + recency weighting) |

### Architectural invariants (must not break in Phase 2+)

1. **`VcsProvider` interface is the only VCS abstraction.** Add new
   providers; do not let any pipeline class talk to JGit / GitHub APIs
   directly.
2. **`OutputWriter` interface is the only output abstraction.** New
   formats register themselves as Spring beans; `OutputDispatcher`
   fan-outs automatically by format key. Current formats: `CSV`, `YAML`,
   `MD`, `HTML`. The HTML writer ships a self-contained page (inline
   CSS + vanilla JS, no remote assets); every user-controlled string is
   HTML-escaped before injection.
3. **`AnalysisResult` is immutable.** Pipeline mutations belong inside
   `HotspotAnalyzer`; downstream code only reads.
4. **`DiffHunk`-driven method revisions with a fallback.** `LocalGitProvider`
   populates per-file diff hunks; `RevisionsCalculator` uses them when
   present and falls back to file-level revisions when the provider can't
   (Phase 1 `GithubProvider`).
5. **CLI exit code contract** (do not renumber without README + tests):
   `0` ok • `1` fatal • `2` Picocli usage • `3` `--strict` empty result.

---

## 4. Package & directory layout

Root package: `io.github.baekchangjoon.hotspotanalysis`
(filesystem: `src/main/java/io/github/baekchangjoon/hotspotanalysis/`)

```
HotspotApplication.java     ← Spring Boot entry. Adds Picocli subcommands at runtime.
cli/                        ← HotspotCommand (root), AnalyzeCommand, InitCommand
config/                     ← Record-based POJOs + ConfigLoader + EnvironmentVariableResolver
vcs/                        ← VcsProvider interface, VcsProviderFactory, LocalGitProvider
vcs/model/                  ← CommitRecord, FileChange, DiffHunk, ChangeType
vcs/github/                 ← GithubProvider, GithubClient, KohsukeGithubClient + DTOs
parser/                     ← JavaSourceParser + method models
parser/model/               ← MethodInfo, MethodSignature, ParameterInfo
analysis/                   ← HotspotAnalyzer (orchestrator), RevisionsCalculator, LocCalculator,
                              HotspotScoreCalculator, JavaSourceCollector
analysis/model/             ← FileHotspot, MethodHotspot, AnalysisMeta, AnalysisResult
output/                     ← OutputWriter interface + Csv/Yaml/Markdown writers + OutputDispatcher
```

Tests mirror this tree under `src/test/java/...`. Every public type has at
least a unit test; the CLI has a full Spring Boot E2E test
(`HotspotCliE2ETest`).

Resources:
- `src/main/resources/application.yml` — Spring Boot CLI config (no web, suppressed logging).
- `src/main/resources/templates/hotspot.example.yml` — bundled sample emitted by `hotspot init`.

Repo-level docs:
- `docs/phase1-design.md` — full Phase 1 design (architecture, schema, E2E plan, TDD breakdown).
- `docs/reports/T1-…T11-*.md` — one completion report per task (chronological).
- `docs/SESSION-HANDOFF.md` — **this document**.
- `hotspot-analysis.md` — original theory / methodology notes (read-only reference).

---

## 5. User working style — non-negotiable conventions

These come straight from the user's persistent rules. Violating them
will visibly break trust.

| Topic | Convention |
|---|---|
| **Chat language** | Korean. Always respond in Korean. |
| **Code language** | English — javadoc, inline comments, log messages, exception messages, commit messages, PR commit titles. |
| **Persona** | Senior Software Quality Engineer and consultant. Confident, expert tone. **No apologies, no remorse, no hedging.** |
| **Stack focus** | Enterprise telecom backend: Java, Spring Boot, JPA, JUnit, Python where relevant. IntelliJ / Antigravity / Cursor / Kiro IDEs. |
| **TDD** | Write failing tests **first**, then implementation. Always cover edge cases, return values, exceptions. Mock/Spy as needed. |
| **Function size** | ~20 lines / function. DI everywhere. Descriptive variable names (>1 word). One statement per line. Spaces around operators. |
| **Style guides** | PEP-8 for Python, Google Java Style for Java. Classes with a `main` entry point where applicable. |
| **Research first** | Use web search to verify library versions, best practices, performance numbers **before** writing code. Quote sources. |
| **Alternative proposal** | Don't just implement what the user asks. Propose 1–2 alternatives with quantitative evidence and a balanced view (optimistic + pessimistic). |
| **Ambiguity** | If the request is under-specified, PAUSE and ask. Do not guess. |
| **Step-by-step logic** | Break complex problems into small steps; cite trusted sources/links. |
| **PR titles / bodies** | The user requested Korean PR title and body for this project (`gh pr create` HEREDOC). |
| **Commit titles / bodies** | English, conventional-style (`feat:`, `docs:`, `docs+feat:`, `fix:`). Use `git commit -m "$(cat <<'EOF' … EOF)"` HEREDOC pattern. |

### User's typical response patterns

- `"진행해줘"` ("proceed") — execute the plan you just outlined. Do not
  ask again unless brand-new ambiguity appears.
- `"T1 부터 시작해줘"` / `"T11 까지 모두 진행하고 각 단계가 진행될때마다 '완료 보고' 를 MD 파일로 생성해줘"`
  — work through a numbered plan and emit a completion report per step.
- When the user confirms partial items (`"1, 2, 3 은 정상 동작을 확인했어"`),
  treat them as accepted and focus on the remaining item.
- When the user disagrees with output (e.g. `"실제로 실행하여 결과를 보여줘"`),
  actually run the command and surface real output — don't paraphrase.

---

## 6. Local environment

| Item | Value |
|---|---|
| OS | macOS 25.4.0 (darwin) |
| Shell | zsh |
| Workspace root | `/Users/changjoonbaek/github_hotspot-analysis/hotspot-analysis` |
| JDK runtime | Eclipse Temurin 22 installed at `/Library/Java/JavaVirtualMachines/temurin-22.jdk/`. **JDK 22 runs the jar fine** (jar is built for class file v65 = Java 21). |
| `JAVA_HOME` recipe | `export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null \|\| /usr/libexec/java_home)` |
| Gradle | `./gradlew` wrapper (`8.10.2`). Auto-provisions Java 21 via Foojay. |
| `gh` CLI | Authenticated. `gh pr list`, `gh run list` work without flags. |

### Useful sibling projects on this machine

Under `/Users/changjoonbaek/` there are many `github_*` directories. Most
of them are container folders with the real git repo *nested* one level
down. Useful Java targets:

| Repo path | Java files | Commits | Notes |
|---|---:|---:|---|
| `github_ftgo/ftgo-application` | 277 | 295 | Multi-module microservices (best e2e demo) |
| `github_spring-microservice-sample/spring-microservice-sample` | 42 | 181 | Small Spring Boot demo |
| `github_assurenet/assurenet` | 209 | 300 | Mid-size monolith |
| `github_wiremock/wiremock` | 1255 | 4479 | Largest; slow but valuable stress test |

**Trap to remember**: the multi-module repos (ftgo, jpashop, spring-ms,
etc.) require `**/src/main/java/**/*.java` glob — the `**/` prefix is
mandatory. The README's Troubleshooting section covers this; tests for
the same trap (`--strict` exit 3) live in `AnalyzeCommandTest`.

---

## 7. Most-recent change set (post-Phase-1 hardening)

Completed in PR #1 (after T1–T11) — see `git log`:

1. **CLI walkthrough hardening** — multi-module include glob, Prerequisites
   box, "why CSV split / YAML&MD combined" rationale, Troubleshooting
   section (Files: 0 / Commits: 0 / ClassVersionError / GitHub auth).
2. **`hotspot init` template overhaul** — switched to `days: 365` default,
   surfaced the multi-module glob as an inline comment.
3. **`--strict, -s` flag** on `hotspot analyze` — returns exit code 3
   when the analysis result is empty. Backwards compatible (no flag
   ⇒ legacy exit 0). Stderr lists three actionable hints.
4. **TDD cases** added (`AnalyzeCommandTest`): no-commits / no-files /
   healthy / backward-compat without flag.
5. **`.gitignore`** — added `demo/` (environment-specific scratch).

Verified by replaying the README walkthrough end-to-end against
`ftgo-application`:

- naive defaults silently return `Commits: 0 / Files: 0` (exit 0)
- `--strict` surfaces the failure with hints (exit 3)
- Troubleshooting-corrected config yields 295 commits / 288 files / 1010
  methods, top score 4347.

---

## 8. Known constraints and open follow-ups

| ID | Topic | Effort | Notes |
|---|---|:---:|---|
| **C1** | `target.type=github` is **not** end-to-end | M | `GithubProvider` + `KohsukeGithubClient` are WireMock-verified, but `HotspotAnalyzer.analyze` rejects non-`local-git` with a clear `UnsupportedOperationException`. To unblock: either clone-on-demand under a temp dir, or implement raw-content fetch via the GitHub API. |
| **C2** | `LocCalculator` reads **working-tree LOC**, not LOC at each historic commit | L | Same simplification Tornhill takes in the book. Acceptable for Phase 1; revisit if Phase 2 introduces composite scoring. |
| **C3** | No bot-commit filtering (Dependabot, Renovate) | L | Phase 2 candidate. Need a config flag `analysis.commitFilter.botPatterns` or similar. |
| **C4** | No coverage integration (JaCoCo / Sonar) | M | Phase 2. Combine with hotspot scores to surface "hot but uncovered". |
| **C5** | Gradle 8.10 deprecation warnings | S | Visible during every `./gradlew build`. Validate with `./gradlew --warning-mode all build`. Confirm spring-boot plugin compatibility before bumping to Gradle 9. |
| **C6** | Daily cron trigger | S | Active only after PR #1 is merged into `main` (GitHub Actions policy). After merge, expect a run shortly after `00:00 UTC` (= 09:00 KST). |
| **C7** | Output layout asymmetry (CSV split, YAML/MD/HTML combined) | S | Intentional. README documents it. If users vote for per-granularity YAML/MD/HTML, add `output.layout: combined \| per-granularity` option. |
| **C7b** | HTML report deep-links to source | M | Currently rows carry `data-path`/`data-start-line`/`data-end-line` attributes but no clickable hyperlinks. Adding `output.html.repoUrl` + `branch` config would let the writer emit `https://github.com/.../blob/<branch>/<path>#L<start>-L<end>` URLs as proper anchors. |
| **C8** | No `--scope` / `--window` CLI overrides for the YAML config | M | Currently config-only. Adding CLI overrides would help one-off triage runs (`hotspot analyze --window-days 90`). |

---

## 9. Quick-start commands for the next session

```bash
# 1. Land in the workspace and use Java 21
cd /Users/changjoonbaek/github_hotspot-analysis/hotspot-analysis
export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home)

# 2. Verify state
git status
git log --oneline -5
./gradlew --no-daemon test --console=plain | tail -5
gh pr list --state all
gh run list --limit 5

# 3. Smoke-test the CLI
java -jar build/libs/hotspot-0.1.0-SNAPSHOT.jar --version
java -jar build/libs/hotspot-0.1.0-SNAPSHOT.jar init -o /tmp/hotspot.yml

# 4. Real run against ftgo (good "is it alive" check)
cat > /tmp/hotspot.yml <<'YAML'
analysis:
  target: { type: local-git, path: /Users/changjoonbaek/github_ftgo/ftgo-application }
  window: { since: "2017-01-01", until: "2022-12-31" }
  scope:
    granularity: [file, method]
    include: ["**/src/main/java/**/*.java"]
    exclude: ["**/test/**", "**/build/**", "**/buildSrc/**"]
  scoring: { formula: simple }
output: { formats: [csv, yaml, md], path: /tmp/hot-out, topN: 10 }
YAML
java -jar build/libs/hotspot-0.1.0-SNAPSHOT.jar analyze --config /tmp/hotspot.yml --strict
```

Expected output:

```
Hotspot analysis complete.
  Target:      LOCAL_GIT:/Users/changjoonbaek/github_ftgo/ftgo-application
  Commits:     295
  Files:       288 (± depending on exclude glob)
  Methods:     ~1000
  Top file:    .../OrderService.java (rev=23, loc=189, score=4347)
```

If you see `Commits: 0 / Files: 0`, see README → Troubleshooting.

---

## 10. Recommended next steps (priority-ordered)

1. **Merge PR #1 into `main`** to activate the daily cron CI. Squash merge
   with `gh pr merge 1 --squash --delete-branch`.
2. **C6 verification** — within 24 h of merge, confirm at least one
   `schedule` event under `gh run list --workflow=CI`.
3. **Pick the first Phase 2 task.** Concrete candidates, in increasing
   effort:
   - (a) `--window-days` / `--top-n` CLI overrides → simple Picocli option
     additions + tests. ~2 h.
   - (b) Composite scoring formula (`revisions × LOC × log2(uniqueAuthors)`
     or entropy) → new `Formula.COMPOSITE`, new `CompositeScoreCalculator`,
     parametric tests. ~½ day.
   - (c) Bot-commit filter (`analysis.commitFilter`) → config record +
     `CommitFilter` interface + `LocalGitProvider` integration. ~½ day.
   - (d) End-to-end GitHub target (C1) → cloned-temp-dir adapter wired into
     `VcsProviderFactory`. ~1 day.
4. **Gradle 9 compatibility check** (C5) before any of the above. ~½ h.

When starting Phase 2, **always**:
- Begin by reading `docs/phase1-design.md` once more to recall the
  invariants in Section 3 of this document.
- Open a new task report file `docs/reports/T12-*.md` following the same
  style as T1–T11.
- Pin every new dependency version; do not let Gradle resolve "latest".

---

## 11. Quick reference — files most likely to need edits

| Goal | Touch points |
|---|---|
| Add a new CLI option | `cli/AnalyzeCommand.java` (or sibling), `cli/AnalyzeCommandTest.java`, README "CLI reference" table |
| Add a new YAML config field | `config/*Config.java` (record), `ConfigLoaderTest.java`, README "Configuration schema" table, `templates/hotspot.example.yml` |
| Add a new output format | new `output/*OutputWriter.java` impl, register as `@Component`, add format key to `OutputConfig.OutputFormat`, README "Outputs" |
| Add a new VCS source | new package under `vcs/`, implement `VcsProvider`, register in `VcsProviderFactory`, add contract test mirroring `VcsProviderContract` |
| Add a new scoring formula | `analysis/HotspotScoreCalculator.java` (extend switch), `ScoringConfig.Formula` enum, calculator test |

---

## 12. References

- Original methodology notes: [`../hotspot-analysis.md`](../hotspot-analysis.md)
- Phase 1 design: [`./phase1-design.md`](./phase1-design.md)
- Per-task completion reports: `./reports/T1-…T11-*.md`
- Pull request #1 (Phase 1 + hardening):
  https://github.com/baekchangjoon/hotspot-analysis/pull/1
- CI workflow definition: `.github/workflows/ci.yml`

---

## 13. First-message playbook for the next session

When the user opens a new chat, do the following **before** answering
anything substantive:

1. Read this file in full.
2. Confirm repo state with the Section 9 commands.
3. If the user's first prompt is implementation-shaped ("Phase 2 시작해줘",
   "X 기능 추가해줘"), respond in Korean with: a short status summary,
   the proposed plan (numbered), the alternatives considered, and a
   `진행해줘` invitation.
4. If the prompt is exploratory ("이거 왜 이래?", "어떻게 동작해?"),
   reproduce the behaviour first (run the CLI / inspect the file),
   then explain with quantitative evidence.
5. Use `TodoWrite` for any task with ≥3 steps. Mark items in-progress
   one at a time. Generate a completion report MD under
   `docs/reports/` for any task numbered T12+.

You are now caught up. Hand control back to the user.
