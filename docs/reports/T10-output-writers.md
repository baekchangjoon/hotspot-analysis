# T10 Completion Report — Output Writers (CSV / YAML / MD)

> Task: Serialise `AnalysisResult` into the three formats the CLI must ship — comma-separated values, YAML, and Markdown — with deterministic output for snapshot testing.
> Status: ✅ Completed (2026-05-21)

## Outcome

| Item | Result |
|---|---|
| Build | `BUILD SUCCESSFUL` |
| Tests | 125 / 125 passed (cumulative: +13 vs T9) |
| New main classes | 6 (`OutputWriter` interface, `CsvOutputWriter`, `YamlOutputWriter`, `MarkdownOutputWriter`, `OutputDispatcher`, `OutputException`) |
| New test classes | 5 (per-writer + dispatcher + shared fixture) |

## Test breakdown (13)

| Class | Tests | Highlights |
|---|---|---|
| `CsvOutputWriterTest` | 4 | header rows, signature serialisation, comma escaping, fractional score formatting |
| `YamlOutputWriterTest` | 2 | top-level keys present + full round-trip via Jackson |
| `MarkdownOutputWriterTest` | 4 | three sections, file table row, method table row with canonical signature, meta block |
| `OutputDispatcherTest` | 3 | multi-format fan-out, format selection, duplicate-registration safety |

## File-output contract

| Format | File names | Notes |
|---|---|---|
| CSV | `file_hotspots.csv`, `method_hotspots.csv` | Two files: one per granularity; RFC-4180 escaping only when needed |
| YAML | `hotspots.yml` | One file with `meta` + `fileHotspots` + `methodHotspots`; Jackson default pretty-printer; ISO-8601 dates |
| MD | `hotspots.md` | Single Markdown file; meta key/value table + two sortable tables for file/method |

## Key design decisions

| Decision | Choice | Rationale |
|---|---|---|
| Writer abstraction | `interface OutputWriter` with `format()` + `write()` | Adding a future format (e.g. JSON) is one new class, no edits elsewhere |
| Dispatcher | `EnumMap<OutputFormat, OutputWriter>` + Spring DI list injection | Single source of truth; fan-out is explicit |
| Duplicate guard | Reject in `OutputDispatcher` constructor | Catches misconfiguration at startup, not at runtime |
| Score formatting | Integer when score is whole; else 4 decimal places | Stable string output ⇒ snapshot tests don't flake on FP noise |
| CSV escaping | Quote only when needed | Smaller files; still RFC-compliant |
| YAML library | Jackson YAML | Same library already in T2; consistent dep tree |
| YAML doc start `---` | Disabled via `YAMLGenerator.Feature.WRITE_DOC_START_MARKER` | Cleaner files; not needed for single-document YAML |
| MD tables | Plain GitHub-Flavored Markdown | Renders natively in PR descriptions and GitHub wikis |
| Path-aware fixture | Fixed `Instant.parse("2026-05-21T09:00:00Z")` in `OutputWriterTestFixtures` | Identical bytes across CI/local |

## Sample output for the test fixture

### `file_hotspots.csv`
```
rank,path,revisions,loc,score
1,src/main/java/com/example/Hot.java,5,120,600
2,src/main/java/com/example/Cold.java,1,30,30
```

### `method_hotspots.csv`
```
rank,fqcn,method,parameters,file,start_line,end_line,revisions,loc,score
1,com.example.Hot,doWork,int;String,src/main/java/com/example/Hot.java,12,28,4,17,68
2,com.example.Hot,doWork,,src/main/java/com/example/Hot.java,30,32,1,3,3
```

### `hotspots.yml` (excerpt)
```yaml
meta:
  analyzedAt: "2026-05-21T09:00:00Z"
  targetDescription: "LOCAL_GIT:/tmp/example"
  totalCommits: 42
  totalFiles: 2
  totalMethods: 2
  scoringFormula: "SIMPLE"
fileHotspots:
- path: "src/main/java/com/example/Hot.java"
  revisions: 5
  loc: 120
  score: 600.0
```

### `hotspots.md` (excerpt)
```markdown
# Hotspot Analysis Report

| Field | Value |
|---|---|
| Generated at | 2026-05-21T09:00:00Z |
| Target | `LOCAL_GIT:/tmp/example` |
| Scoring formula | SIMPLE |
| Total commits | 42 |

## File Hotspots (2 rows)

| Rank | Path | Revisions | LOC | Score |
|---:|:---|---:|---:|---:|
| 1 | `src/main/java/com/example/Hot.java` | 5 | 120 | 600 |
```

## Counter-arguments considered

| Alternative | Why rejected |
|---|---|
| Apache Commons CSV | Adds a transitive jar; only saved ~10 lines of code |
| Snakeyaml directly | Less consistent with Jackson-based YAML reading already in T2 |
| Pebble/Freemarker templates for MD | Indirection for 30 lines of string building; templates also need their own tests |
| Single mega-writer that emits all 3 | Couples formats; harder to add JSON later |

## Next step

T11 — CLI integration. Picocli subcommands `analyze` (loads YAML, runs pipeline, writes outputs) and `init` (scaffolds a default `hotspot.yml`). E2E suite executes the assembled JAR against a real local git fixture and verifies exit codes + output file presence.
