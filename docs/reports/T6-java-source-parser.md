# T6 Completion Report — JavaSourceParser (JavaParser 3.26.x)

> Task: Extract method declarations (signature, line range, parameter metadata) from Java source files, with explicit coverage for Java 21 features.
> Status: ✅ Completed (2026-05-21)

## Outcome

| Item | Result |
|---|---|
| Build | `BUILD SUCCESSFUL` |
| Tests | 80 / 80 passed (cumulative: +10 vs T5) |
| New main classes | 5 (3 records + parser + exception) |
| New test class | 1 (`JavaSourceParserTest`, 10 tests) |
| New dependency | `com.github.javaparser:javaparser-symbol-solver-core:3.26.2` |

## Files

```
src/main/java/.../parser/JavaSourceParser.java
src/main/java/.../parser/SourceParseException.java
src/main/java/.../parser/model/MethodInfo.java
src/main/java/.../parser/model/MethodSignature.java
src/main/java/.../parser/model/ParameterInfo.java
src/test/java/.../parser/JavaSourceParserTest.java
```

## Test coverage (10)

| Test | Verifies |
|---|---|
| `shouldExtractSingleMethod` | Basic happy path: FQCN, name, line range, parameter types |
| `shouldExtractMultipleMethods` | Two methods with distinct line ranges |
| `shouldDistinguishOverloads` | Canonical form differs for `save(User)` vs `save(User, Context)` |
| `shouldResolveInnerClassFqcn` | `p.Outer.Inner` for inner-class methods |
| `shouldExtractMethodsInsideRecord` | Java 21 `record` body method discovered |
| `shouldReturnEmptyForEmptyInterface` | Interfaces with no default methods return `[]` |
| `shouldParseSwitchExpression` | Java 21 switch expression / pattern matching parses |
| `shouldParseFromFile` | `parse(Path)` variant from disk |
| `shouldFailOnInvalidSyntax` | `SourceParseException` for non-Java input |
| `shouldFailOnMissingFile` | `SourceParseException` for missing file path |

## Key design decisions

| Decision | Choice | Rationale |
|---|---|---|
| Granularity | `MethodDeclaration` only (no constructors, no record compact constructors) | Phase 1 scope; constructors handled later if hotspot signal is meaningful for them |
| Language level | `ParserConfiguration.LanguageLevel.JAVA_21` | Matches our toolchain target |
| Type chain resolution | Walk parent nodes, collecting `class / record / enum / annotation` names dot-separated | Produces stable FQCNs for nested types like `p.Outer.Inner` |
| Anonymous classes | Synthetic `$N` marker | Avoids dropping methods from anonymous inner classes |
| Parameter type rendering | `p.getType().asString()` (no symbol resolution) | Symbol solver runs in T2-phase later; AST string is fine for hotspot scoring |
| Line range | `MethodDeclaration.getRange()` (begin.line, end.line, both 1-based inclusive) | Direct input to T7's diff-hunk overlap calculation |
| Two API surfaces | `parse(Path)` and `parse(String)` | Path is the production usage; String makes the tests trivially readable via text blocks |
| Spring integration | `@Component` | Will be auto-wired into `HotspotAnalyzer` in T9 |

## Lines-of-code metric and `MethodInfo.lineCount()`

`MethodInfo.lineCount() == endLine - startLine + 1` is the LOC used by both:
- the method-level hotspot score (`revisions * loc`), and
- the line-range overlap heuristic in T7 (diff hunks intersecting `[startLine, endLine]`).

This 1-based inclusive convention is consistent with git's own line-range syntax (`git log -L <start>,<end>:<file>`), so cross-validation in E2E will not be off-by-one.

## Counter-arguments considered

| Alternative | Why rejected |
|---|---|
| Tree-sitter (faster, multi-language) | Decision in `phase1-design.md`: Java-only Phase 1 → JavaParser's accuracy wins (99%+ vs ~95%) |
| Include constructors | Phase 1 hotspot signal is dominated by behaviour-bearing methods; constructors can be added later without breaking outputs |
| Full symbol resolution (resolve generics, imports) | Costs ~3× more setup (need classpath); Phase 1 does not need exact type identity |

## Next step

T7 — `RevisionsCalculator`. Given a list of `CommitRecord`s (from T4/T5) and a set of `MethodInfo`s per file (from T6), counts how many commits touched each file and each method's line range. This is the core "revisions" metric (Tornhill's #1 predictor).
