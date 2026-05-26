# Unified Scoring Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the SIMPLE/COMPOSITE dual-mode reporting with a single unified output that always carries seven metric columns (`LOC, Revisions, Simple Score, Recency Decay, Cognitive Complexity, Coverage Multiplier, Composite Score`) across CSV / YAML / Markdown / HTML.

**Architecture:** Single-pass scoring in `HotspotAnalyzer`: every file & method receives all four input factors (revisions, recency decay, cognitive complexity, coverage multiplier) and both derived scores. Records become non-nullable. Writers emit a fixed column order. `scoring.formula` config key removed with a friendly migration message.

**Tech Stack:** Java 21, Spring Boot 3.3, JaCoCo XML, JUnit 5 + AssertJ, Picocli.

**Spec:** `docs/superpowers/specs/2026-05-25-unified-scoring-design.md`

---

## File Structure

| File | Action | Responsibility after this plan |
|---|---|---|
| `src/main/java/.../analysis/HotspotScoreCalculator.java` | Modify | Three pure functions: `simple(int,int)`, `composite(double,double,double)`, `multiplier(OptionalDouble)`; old `calculate` / `calculateComposite` removed |
| `src/main/java/.../analysis/model/FileHotspot.java` | Modify | 8 non-nullable components in canonical order |
| `src/main/java/.../analysis/model/MethodHotspot.java` | Modify | 11 non-nullable components |
| `src/main/java/.../analysis/model/ApiHotspot.java` | Modify | 11 non-nullable components + callGraph |
| `src/main/java/.../analysis/model/SharedComponentHotspot.java` | Modify | 9 non-nullable components + callingApis |
| `src/main/java/.../analysis/model/AnalysisMeta.java` | Modify | Drop `scoringFormula` component |
| `src/main/java/.../analysis/HotspotAnalyzer.java` | Modify | Single-path: always compute four factors + both scores; sort by compositeScore |
| `src/main/java/.../config/ScoringConfig.java` | Modify | Single component `Integer decayHalfLifeDays`, `Formula` enum deleted |
| `src/main/java/.../config/ConfigLoader.java` | Modify | Specialised migration message when unknown property is `formula` |
| `src/main/java/.../output/CsvOutputWriter.java` | Modify | New 10/14-column headers in canonical order |
| `src/main/java/.../output/YamlOutputWriter.java` | Modify | New flat-keys-per-row layout |
| `src/main/java/.../output/MarkdownOutputWriter.java` | Modify | New table headers |
| `src/main/java/.../output/HtmlOutputWriter.java` | Modify | New columns + X-Ray drill-down with 9 columns |
| `src/main/resources/templates/hotspot.example.yml` | Modify | Drop `formula:` line; document optional `decayHalfLifeDays` |
| `src/test/java/.../analysis/HotspotScoreCalculatorTest.java` | Modify | Tests for new helpers |
| `src/test/java/.../analysis/HotspotAnalyzerTest.java` | Modify | Single happy-path verifying all 7 metrics |
| `src/test/java/.../output/OutputWriterTestFixtures.java` | Modify | New canonical fixture with all 7 fields |
| `src/test/java/.../output/{Csv,Yaml,Markdown,Html}OutputWriterTest.java` | Modify | Updated snapshot expectations |
| `src/test/java/.../config/ConfigLoaderTest.java` | Modify | Add test for the formula migration message |
| `src/test/java/.../HotspotCliE2ETest.java` | Modify | Single unified-path E2E |
| `README.md` | Modify | Rewrite "How the score is computed" + CSV header note |
| `docs/phase1-design.md` | Modify | Append §13 "v0.2: unified scoring model" |
| `docs/hotspot-advanced-spec.md` | Modify | Leading note that all four factors are now always-on |

**Build status convention:** each task ends with a green `./gradlew clean check` (or noted otherwise) and a single commit. Task 2 touches many files but its FINAL step compiles and tests pass before commit; intermediate steps may leave the tree red.

---

### Task 1: Pure scoring helpers (additive)

**Files:**
- Modify: `src/main/java/io/github/baekchangjoon/hotspotanalysis/analysis/HotspotScoreCalculator.java`
- Test: `src/test/java/io/github/baekchangjoon/hotspotanalysis/analysis/HotspotScoreCalculatorTest.java`

- [ ] **Step 1: Add failing unit tests for the new helpers**

Append to `HotspotScoreCalculatorTest.java`:

```java
@Test
void simpleMultipliesRevisionsByLoc() {
    HotspotScoreCalculator c = new HotspotScoreCalculator();
    assertThat(c.simple(3, 120)).isEqualTo(360.0);
}

@Test
void simpleRejectsNegativeInputs() {
    HotspotScoreCalculator c = new HotspotScoreCalculator();
    assertThatThrownBy(() -> c.simple(-1, 10))
            .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> c.simple(1, -5))
            .isInstanceOf(IllegalArgumentException.class);
}

@Test
void compositeMultipliesAllThreeFactors() {
    HotspotScoreCalculator c = new HotspotScoreCalculator();
    assertThat(c.composite(25.0, 0.425, 0.9381))
            .isCloseTo(9.97, within(0.01));
}

@Test
void compositeRejectsNegativeOrZeroMultiplier() {
    HotspotScoreCalculator c = new HotspotScoreCalculator();
    assertThatThrownBy(() -> c.composite(-1, 0.5, 1.0))
            .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> c.composite(1.0, 0.5, 0.0))
            .isInstanceOf(IllegalArgumentException.class);
}

@Test
void multiplierReturnsOneWhenCoverageAbsent() {
    HotspotScoreCalculator c = new HotspotScoreCalculator();
    assertThat(c.multiplier(java.util.OptionalDouble.empty())).isEqualTo(1.0);
}

@Test
void multiplierReturnsInverseShiftedCoverage() {
    HotspotScoreCalculator c = new HotspotScoreCalculator();
    assertThat(c.multiplier(java.util.OptionalDouble.of(0.0)))
            .isCloseTo(10.0, within(1e-9));
    assertThat(c.multiplier(java.util.OptionalDouble.of(1.0)))
            .isCloseTo(1.0 / 1.1, within(1e-9));
}

@Test
void multiplierRejectsOutOfRangeCoverage() {
    HotspotScoreCalculator c = new HotspotScoreCalculator();
    assertThatThrownBy(() -> c.multiplier(java.util.OptionalDouble.of(-0.01)))
            .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> c.multiplier(java.util.OptionalDouble.of(1.01)))
            .isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests HotspotScoreCalculatorTest`
Expected: FAIL with "cannot find symbol method simple/composite/multiplier"

- [ ] **Step 3: Add the three helper methods to `HotspotScoreCalculator`**

Insert into `HotspotScoreCalculator.java` (keep existing `calculate` and `calculateComposite` for now — they will be removed in Task 2):

```java
public double simple(int revisions, int loc) {
    if (revisions < 0 || loc < 0) {
        throw new IllegalArgumentException(
                "revisions and loc must both be >= 0 (was " + revisions + " / " + loc + ")");
    }
    return (double) revisions * loc;
}

public double composite(double cognitiveComplexity, double recencyDecay, double coverageMultiplier) {
    if (cognitiveComplexity < 0) {
        throw new IllegalArgumentException("cognitiveComplexity must be >= 0");
    }
    if (recencyDecay < 0) {
        throw new IllegalArgumentException("recencyDecay must be >= 0");
    }
    if (coverageMultiplier <= 0) {
        throw new IllegalArgumentException("coverageMultiplier must be > 0");
    }
    return cognitiveComplexity * recencyDecay * coverageMultiplier;
}

public double multiplier(java.util.OptionalDouble coverage) {
    if (coverage.isEmpty()) {
        return 1.0;
    }
    double cov = coverage.getAsDouble();
    if (cov < 0.0 || cov > 1.0) {
        throw new IllegalArgumentException("coverage must be in [0.0, 1.0] (was " + cov + ")");
    }
    return 1.0 / (cov + 0.1);
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests HotspotScoreCalculatorTest`
Expected: PASS (all old tests still green; 7 new tests green)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/baekchangjoon/hotspotanalysis/analysis/HotspotScoreCalculator.java \
        src/test/java/io/github/baekchangjoon/hotspotanalysis/analysis/HotspotScoreCalculatorTest.java
git commit -m "feat(scoring): add unified simple/composite/multiplier helpers"
```

---

### Task 2: Records, AnalysisMeta, ScoringConfig, HotspotAnalyzer, and writers — atomic refactor

**Files:**
- Modify (records): `src/main/java/io/github/baekchangjoon/hotspotanalysis/analysis/model/{FileHotspot,MethodHotspot,ApiHotspot,SharedComponentHotspot,AnalysisMeta}.java`
- Modify (config): `src/main/java/io/github/baekchangjoon/hotspotanalysis/config/ScoringConfig.java`
- Modify (analyzer): `src/main/java/io/github/baekchangjoon/hotspotanalysis/analysis/HotspotAnalyzer.java`
- Modify (analyzer cleanup): `src/main/java/io/github/baekchangjoon/hotspotanalysis/analysis/HotspotScoreCalculator.java` (remove old methods)
- Modify (writers): all four writers in `src/main/java/io/github/baekchangjoon/hotspotanalysis/output/`
- Modify (tests): `OutputWriterTestFixtures.java`, all four writer tests, `HotspotAnalyzerTest.java`

**Build status during task:** **WILL be red between steps**; the task only commits at Step 30 once the entire chain compiles and `./gradlew check` is green.

- [ ] **Step 1: Replace `FileHotspot.java` with the unified record**

```java
package io.github.baekchangjoon.hotspotanalysis.analysis.model;

import java.util.Objects;

public record FileHotspot(
        String path,
        int loc,
        int revisions,
        double simpleScore,
        double recencyDecay,
        double cognitiveComplexity,
        double coverageMultiplier,
        double compositeScore
) {
    public FileHotspot {
        Objects.requireNonNull(path, "path");
        if (path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (loc < 0 || revisions < 0) {
            throw new IllegalArgumentException(
                    "loc and revisions must both be >= 0 (was " + loc + " / " + revisions + ")");
        }
        if (simpleScore < 0 || recencyDecay < 0 || cognitiveComplexity < 0
                || coverageMultiplier <= 0 || compositeScore < 0) {
            throw new IllegalArgumentException(
                    "metric values must be non-negative; coverageMultiplier must be > 0");
        }
    }
}
```

- [ ] **Step 2: Replace `MethodHotspot.java` with the unified record**

```java
package io.github.baekchangjoon.hotspotanalysis.analysis.model;

import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;

import java.util.Objects;

public record MethodHotspot(
        MethodSignature signature,
        String filePath,
        int startLine,
        int endLine,
        int loc,
        int revisions,
        double simpleScore,
        double recencyDecay,
        double cognitiveComplexity,
        double coverageMultiplier,
        double compositeScore
) {
    public MethodHotspot {
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(filePath, "filePath");
        if (filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be blank");
        }
        if (startLine < 1 || endLine < startLine) {
            throw new IllegalArgumentException(
                    "invalid line range: [" + startLine + ", " + endLine + "]");
        }
        if (loc < 0 || revisions < 0) {
            throw new IllegalArgumentException(
                    "loc and revisions must both be >= 0 (was " + loc + " / " + revisions + ")");
        }
        if (simpleScore < 0 || recencyDecay < 0 || cognitiveComplexity < 0
                || coverageMultiplier <= 0 || compositeScore < 0) {
            throw new IllegalArgumentException(
                    "metric values must be non-negative; coverageMultiplier must be > 0");
        }
    }
}
```

- [ ] **Step 3: Replace `ApiHotspot.java` with the unified record**

Existing component list is `(httpMethod, route, controllerMethod, revisions, loc, score, callGraph)`. New shape:

```java
package io.github.baekchangjoon.hotspotanalysis.analysis.model;

import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;

import java.util.List;
import java.util.Objects;

public record ApiHotspot(
        String httpMethod,
        String route,
        MethodSignature controllerMethod,
        int loc,
        int revisions,
        double simpleScore,
        double recencyDecay,
        double cognitiveComplexity,
        double coverageMultiplier,
        double compositeScore,
        List<MethodSignature> callGraph
) {
    public ApiHotspot {
        Objects.requireNonNull(httpMethod, "httpMethod");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(controllerMethod, "controllerMethod");
        callGraph = callGraph == null ? List.of() : List.copyOf(callGraph);
        if (loc < 0 || revisions < 0) {
            throw new IllegalArgumentException("loc and revisions must be >= 0");
        }
        if (coverageMultiplier <= 0) {
            throw new IllegalArgumentException("coverageMultiplier must be > 0");
        }
    }
}
```

- [ ] **Step 4: Replace `SharedComponentHotspot.java` with the unified record**

```java
package io.github.baekchangjoon.hotspotanalysis.analysis.model;

import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;

import java.util.List;
import java.util.Objects;

public record SharedComponentHotspot(
        MethodSignature method,
        int loc,
        int revisions,
        double simpleScore,
        double recencyDecay,
        double cognitiveComplexity,
        double coverageMultiplier,
        double compositeScore,
        List<String> callingApis
) {
    public SharedComponentHotspot {
        Objects.requireNonNull(method, "method");
        callingApis = callingApis == null ? List.of() : List.copyOf(callingApis);
        if (loc < 0 || revisions < 0) {
            throw new IllegalArgumentException("loc and revisions must be >= 0");
        }
        if (coverageMultiplier <= 0) {
            throw new IllegalArgumentException("coverageMultiplier must be > 0");
        }
    }
}
```

- [ ] **Step 5: Replace `AnalysisMeta.java` to drop `scoringFormula`**

```java
package io.github.baekchangjoon.hotspotanalysis.analysis.model;

import java.time.Instant;
import java.util.Objects;

public record AnalysisMeta(
        Instant analyzedAt,
        String targetDescription,
        int totalCommits,
        int totalFiles,
        int totalMethods
) {
    public AnalysisMeta {
        Objects.requireNonNull(analyzedAt, "analyzedAt");
        Objects.requireNonNull(targetDescription, "targetDescription");
        if (totalCommits < 0 || totalFiles < 0 || totalMethods < 0) {
            throw new IllegalArgumentException(
                    "counts must be >= 0 (commits=" + totalCommits
                            + " files=" + totalFiles + " methods=" + totalMethods + ")");
        }
    }
}
```

- [ ] **Step 6: Replace `ScoringConfig.java` (drop Formula enum)**

```java
package io.github.baekchangjoon.hotspotanalysis.config;

public record ScoringConfig(
        Integer decayHalfLifeDays
) {
    public ScoringConfig {
        if (decayHalfLifeDays == null) {
            decayHalfLifeDays = 90;
        }
        if (decayHalfLifeDays <= 0) {
            throw new IllegalArgumentException(
                    "decayHalfLifeDays must be > 0 (was " + decayHalfLifeDays + ")");
        }
    }

    public ScoringConfig() {
        this(90);
    }
}
```

Verify no remaining references to `ScoringConfig.Formula` anywhere:

Run: `grep -rn "ScoringConfig.Formula\|Formula.SIMPLE\|Formula.COMPOSITE" src/`
Expected output: (none, except inside `HotspotAnalyzer.java` and `HotspotScoreCalculator.java` which will be fixed in steps 8-10)

- [ ] **Step 7: Delete old methods from `HotspotScoreCalculator.java`**

Remove `calculate(int, int, Formula)` and `calculateComposite(double, double, double)`. Keep only the three pure helpers added in Task 1 plus the class declaration:

```java
package io.github.baekchangjoon.hotspotanalysis.analysis;

import org.springframework.stereotype.Component;

import java.util.OptionalDouble;

/**
 * Pure helpers for the unified scoring model.
 *
 * <ul>
 *   <li>{@link #simple(int, int)} — Adam Tornhill's original {@code revisions × loc}.</li>
 *   <li>{@link #composite(double, double, double)} — cognitive complexity ×
 *       recency decay × coverage multiplier.</li>
 *   <li>{@link #multiplier(OptionalDouble)} — {@code 1/(coverage + 0.1)} or 1.0
 *       when no coverage data was supplied.</li>
 * </ul>
 */
@Component
public class HotspotScoreCalculator {

    public double simple(int revisions, int loc) { ... as in Task 1 step 3 ... }
    public double composite(double cognitiveComplexity, double recencyDecay, double coverageMultiplier) { ... }
    public double multiplier(OptionalDouble coverage) { ... }
}
```

Copy the three method bodies from Task 1, Step 3 verbatim.

- [ ] **Step 8: Refactor `HotspotAnalyzer.analyze()` to single-path**

Replace the entire method body — current lines 93–343 in the file. Use this canonical layout (full replacement; do NOT keep the old `if (formula == COMPOSITE)` branches):

```java
public AnalysisResult analyze(AnalysisConfig config) {
    Objects.requireNonNull(config, "config");
    TargetConfig target = config.analysis().target();
    if (target.type() != TargetConfig.TargetType.LOCAL_GIT) {
        throw new UnsupportedOperationException(
                "Phase 1 CLI supports only target.type=local-git for end-to-end analysis."
                        + " Clone the GitHub repository to a local path and re-run with"
                        + " target.type=local-git.");
    }

    Path repoRoot = Path.of(target.path()).toAbsolutePath().normalize();
    VcsProvider provider = providerFactory.create(target);
    List<CommitRecord> commits = provider.loadCommits(config.analysis().window());

    List<Path> javaFiles = sourceCollector.collect(repoRoot, config.analysis().scope());
    Map<String, List<MethodInfo>> methodsByFile = parseAll(repoRoot, javaFiles);

    Map<String, Integer> fileRevisions =
            revisionsCalculator.calculateFileRevisions(commits);
    Map<MethodSignature, Integer> methodRevisions =
            revisionsCalculator.calculateMethodRevisions(commits, methodsByFile);
    Map<String, Integer> fileLoc =
            locCalculator.countLines(repoRoot, methodsByFile.keySet());

    Instant untilInstant = (config.analysis().window().until() != null)
            ? config.analysis().window().until().plusDays(1)
                    .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().minusNanos(1)
            : Instant.now();
    int halfLifeDays = config.analysis().scoring() != null
            && config.analysis().scoring().decayHalfLifeDays() != null
            ? config.analysis().scoring().decayHalfLifeDays() : 90;

    Map<String, Double> fileDecayed =
            revisionsCalculator.calculateFileDecayedRevisions(commits, halfLifeDays, untilInstant);
    Map<MethodSignature, Double> methodDecayed =
            revisionsCalculator.calculateMethodDecayedRevisions(commits, methodsByFile, halfLifeDays, untilInstant);

    boolean jacocoSupplied =
            config.analysis().jacocoReportPath() != null
                    && !config.analysis().jacocoReportPath().isEmpty();
    io.github.baekchangjoon.hotspotanalysis.coverage.JacocoReportParser jacocoParser =
            new io.github.baekchangjoon.hotspotanalysis.coverage.JacocoReportParser();
    if (jacocoSupplied) {
        Path p = Path.of(config.analysis().jacocoReportPath());
        if (!p.isAbsolute()) p = repoRoot.resolve(p);
        jacocoParser.parse(p);
    }

    List<FileHotspot> files = buildFileHotspots(
            methodsByFile, fileRevisions, fileLoc, fileDecayed, jacocoParser, jacocoSupplied);
    List<MethodHotspot> methods = buildMethodHotspots(
            methodsByFile, methodRevisions, methodDecayed, jacocoParser, jacocoSupplied);

    List<ApiHotspot> apiHotspots = new ArrayList<>();
    List<SharedComponentHotspot> sharedComponents = new ArrayList<>();
    if (config.analysis().apiAnalysis() != null && config.analysis().apiAnalysis().enabled()) {
        buildApiAndShared(repoRoot, config, javaFiles, methodsByFile,
                methodRevisions, methodDecayed, jacocoParser, jacocoSupplied,
                apiHotspots, sharedComponents);
    }

    int topN = config.output().topN();
    if (topN > 0) {
        files = takeTop(files, topN);
        methods = takeTop(methods, topN);
        if (!apiHotspots.isEmpty()) apiHotspots = takeTop(apiHotspots, topN);
        if (!sharedComponents.isEmpty()) sharedComponents = takeTop(sharedComponents, topN);
    }

    AnalysisMeta meta = new AnalysisMeta(
            Instant.now(),
            "LOCAL_GIT:" + repoRoot,
            commits.size(),
            methodsByFile.size(),
            countMethods(methodsByFile));
    return new AnalysisResult(files, methods, apiHotspots, sharedComponents, meta);
}
```

- [ ] **Step 9: Replace `buildFileHotspots` / `buildMethodHotspots` in `HotspotAnalyzer`**

Remove the old `buildFileHotspots(Set<String>, ...)` and `buildMethodHotspots(...)` overloads as well as `buildCompositeFileHotspots` / `buildCompositeMethodHotspots`. Replace with two unified private methods:

```java
private List<FileHotspot> buildFileHotspots(
        Map<String, List<MethodInfo>> methodsByFile,
        Map<String, Integer> fileRevisions,
        Map<String, Integer> fileLoc,
        Map<String, Double> fileDecayed,
        io.github.baekchangjoon.hotspotanalysis.coverage.JacocoReportParser jacoco,
        boolean jacocoSupplied) {
    List<FileHotspot> out = new ArrayList<>();
    for (Map.Entry<String, List<MethodInfo>> e : methodsByFile.entrySet()) {
        String path = e.getKey();
        int revisions = fileRevisions.getOrDefault(path, 0);
        int loc = fileLoc.getOrDefault(path, 0);
        double simple = scoreCalculator.simple(revisions, loc);
        double decayed = fileDecayed.getOrDefault(path, 0.0);
        double cc = 0.0;
        for (MethodInfo m : e.getValue()) cc += m.cognitiveComplexity();
        double mult = scoreCalculator.multiplier(
                jacocoSupplied ? java.util.OptionalDouble.of(jacoco.getFileCoverage(path))
                               : java.util.OptionalDouble.empty());
        double composite = scoreCalculator.composite(cc, decayed, mult);
        out.add(new FileHotspot(path, loc, revisions, simple, decayed, cc, mult, composite));
    }
    out.sort(Comparator.comparingDouble(FileHotspot::compositeScore).reversed()
            .thenComparing(FileHotspot::path));
    return out;
}

private List<MethodHotspot> buildMethodHotspots(
        Map<String, List<MethodInfo>> methodsByFile,
        Map<MethodSignature, Integer> methodRevisions,
        Map<MethodSignature, Double> methodDecayed,
        io.github.baekchangjoon.hotspotanalysis.coverage.JacocoReportParser jacoco,
        boolean jacocoSupplied) {
    List<MethodHotspot> out = new ArrayList<>();
    for (Map.Entry<String, List<MethodInfo>> e : methodsByFile.entrySet()) {
        String path = e.getKey();
        for (MethodInfo m : e.getValue()) {
            int revisions = methodRevisions.getOrDefault(m.signature(), 0);
            int loc = m.lineCount();
            double simple = scoreCalculator.simple(revisions, loc);
            double decayed = methodDecayed.getOrDefault(m.signature(), 0.0);
            double cc = m.cognitiveComplexity();
            double mult = scoreCalculator.multiplier(
                    jacocoSupplied
                            ? java.util.OptionalDouble.of(
                                    jacoco.getMethodCoverage(path, m.startLine(), m.endLine()))
                            : java.util.OptionalDouble.empty());
            double composite = scoreCalculator.composite(cc, decayed, mult);
            out.add(new MethodHotspot(
                    m.signature(), path, m.startLine(), m.endLine(),
                    loc, revisions, simple, decayed, cc, mult, composite));
        }
    }
    out.sort(Comparator.comparingDouble(MethodHotspot::compositeScore).reversed()
            .thenComparing(h -> h.signature().toCanonicalString()));
    return out;
}
```

- [ ] **Step 10: Extract `buildApiAndShared` helper in `HotspotAnalyzer`**

Move the existing 200-LOC apiAnalysis block (currently lines ~160–320) into a private helper with this signature:

```java
private void buildApiAndShared(
        Path repoRoot,
        AnalysisConfig config,
        List<Path> javaFiles,
        Map<String, List<MethodInfo>> methodsByFile,
        Map<MethodSignature, Integer> methodRevisions,
        Map<MethodSignature, Double> methodDecayed,
        io.github.baekchangjoon.hotspotanalysis.coverage.JacocoReportParser jacoco,
        boolean jacocoSupplied,
        List<ApiHotspot> apiOut,
        List<SharedComponentHotspot> sharedOut) { ... }
```

Inside the helper, compute aggregated `simple`, `recencyDecay` (sum of decayed revs along the call graph), `cognitiveComplexity` (sum), `coverageMultiplier` (= `scoreCalculator.multiplier(avg coverage)` if jacoco supplied, else 1.0), `compositeScore = composite(...)`. Use the new `ApiHotspot` and `SharedComponentHotspot` constructors with all 11 / 9 components. Sort by `compositeScore` DESC, tie-break by `route + httpMethod` for API and by canonical signature for SharedComponent.

Pseudocode for the per-controller-method loop:

```java
for (entry in callGraphs.entrySet()) {
    controllerMethod = entry.getKey();
    mappings = apiMappingsMap.get(controllerMethod);
    if (mappings == null) continue;
    for (mapping in mappings) {
        int apiRevs = methodRevisions.getOrDefault(controllerMethod, 0);
        int apiLoc = locMap.getOrDefault(controllerMethod, 0);
        double apiDecayed = methodDecayed.getOrDefault(controllerMethod, 0.0);
        double apiCc = methodCcs.getOrDefault(controllerMethod, 0.0);
        double covSum = jacocoSupplied
                ? jacoco.getMethodCoverage(path-of-controllerMethod, ...)
                : 0.0;
        int covCount = jacocoSupplied ? 1 : 0;
        List<MethodSignature> filteredCallGraph = new ArrayList<>();
        for (called in entry.getValue()) {
            boolean exclude = isShared(called) && mode == SEPARATE;
            if (!exclude) {
                apiRevs += methodRevisions.getOrDefault(called, 0);
                apiLoc += locMap.getOrDefault(called, 0);
                apiDecayed += methodDecayed.getOrDefault(called, 0.0);
                apiCc += methodCcs.getOrDefault(called, 0.0);
                if (jacocoSupplied) { covSum += coverageFor(called); covCount++; }
            }
            filteredCallGraph.add(called);
        }
        double apiSimple = scoreCalculator.simple(apiRevs, apiLoc);
        double mult = jacocoSupplied
                ? scoreCalculator.multiplier(OptionalDouble.of(covSum / covCount))
                : 1.0;
        double apiComposite = scoreCalculator.composite(apiCc, apiDecayed, mult);
        apiOut.add(new ApiHotspot(
                mapping.httpMethod(), mapping.route(), controllerMethod,
                apiLoc, apiRevs, apiSimple, apiDecayed, apiCc, mult, apiComposite,
                filteredCallGraph));
    }
}
apiOut.sort(Comparator.comparingDouble(ApiHotspot::compositeScore).reversed()
        .thenComparing(ApiHotspot::route).thenComparing(ApiHotspot::httpMethod));
```

Apply the analogous transformation for `sharedOut`.

- [ ] **Step 11: Delete unused imports and dead helpers in `HotspotAnalyzer`**

Remove imports of `ScoringConfig.Formula` and any unused symbols. Compile:

Run: `./gradlew compileJava`
Expected: compile FAILS in writers (we'll fix next).

- [ ] **Step 12: Update `CsvOutputWriter` to call new accessors (interim, keep current columns)**

Find every `file.score()` / `method.score()` and replace with `file.simpleScore()` / `method.simpleScore()`. Find every `file.decayedRevisions()` and replace with `Double.valueOf(file.recencyDecay())`. Find every `file.cognitiveComplexity()` returning Double and replace with autoboxed `file.cognitiveComplexity()` (primitive). Find every `file.coverage()` and replace with the explicit text `file.coverageMultiplier()` plus a note that semantics changed.

**Goal of this step is compile-green only.** Final column layout comes in Task 3.

- [ ] **Step 13: Same compile-only fix for `YamlOutputWriter`**

Same rename pass as Step 12. Output structure can stay legacy for now.

- [ ] **Step 14: Same compile-only fix for `MarkdownOutputWriter`**

Same rename pass.

- [ ] **Step 15: Same compile-only fix for `HtmlOutputWriter`**

Same rename pass. The X-Ray drilldown uses `cognitiveComplexity / decayedRevisions / coverage` — rename to `cognitiveComplexity / recencyDecay / coverageMultiplier` accessors. **Display text** stays legacy; will be rewritten in Task 6.

Run: `./gradlew compileJava compileTestJava`
Expected: COMPILE PASS.

- [ ] **Step 16: Update `OutputWriterTestFixtures.java` to construct records with new field order**

Open the file and locate every `new FileHotspot(...)`, `new MethodHotspot(...)`, `new ApiHotspot(...)`, `new SharedComponentHotspot(...)` call. Rewrite each with the canonical components below. Each sample is self-consistent (simpleScore = revisions × loc, compositeScore = cognitiveComplexity × recencyDecay × coverageMultiplier):

```java
public static FileHotspot fileSample() {
    return new FileHotspot(
            "src/main/java/com/example/Foo.java",
            /* loc */ 120, /* revisions */ 3,
            /* simpleScore */ 360.0, /* recencyDecay */ 0.85,
            /* cognitiveComplexity */ 7.0, /* coverageMultiplier */ 1.0,
            /* compositeScore */ 5.95);
}

public static MethodHotspot methodSample() {
    return new MethodHotspot(
            new MethodSignature("com.example.Foo", "bar", List.of("int", "String")),
            "src/main/java/com/example/Foo.java",
            /* startLine */ 22, /* endLine */ 45,
            /* loc */ 24, /* revisions */ 2,
            /* simpleScore */ 48.0, /* recencyDecay */ 0.50,
            /* cognitiveComplexity */ 5.0, /* coverageMultiplier */ 2.0,
            /* compositeScore */ 5.0);
}

public static ApiHotspot apiSample() {
    return new ApiHotspot(
            "GET", "/api/foo/{id}",
            new MethodSignature("com.example.FooController", "getFoo", List.of("Long")),
            /* loc */ 30, /* revisions */ 3,
            /* simpleScore */ 90.0, /* recencyDecay */ 0.60,
            /* cognitiveComplexity */ 4.0, /* coverageMultiplier */ 1.25,
            /* compositeScore */ 3.0,
            /* callGraph */ List.of());
}

public static SharedComponentHotspot sharedSample() {
    return new SharedComponentHotspot(
            new MethodSignature("com.example.SharedSvc", "save", List.of("Entity")),
            /* loc */ 18, /* revisions */ 4,
            /* simpleScore */ 72.0, /* recencyDecay */ 0.40,
            /* cognitiveComplexity */ 3.0, /* coverageMultiplier */ 1.0,
            /* compositeScore */ 1.2,
            /* callingApis */ List.of("GET /api/a", "POST /api/b"));
}
```

- [ ] **Step 17: Update `HotspotAnalyzerTest.java` happy-path**

Replace the existing formula-branch tests with a single happy-path that uses the existing `tiny-repo-1.zip` fixture and verifies — for the top file — every one of the seven metric fields against expected values. Sort key asserted via `result.fileHotspots().get(0).compositeScore() >= result.fileHotspots().get(1).compositeScore()`.

- [ ] **Step 18: Run all unit tests to identify remaining breakage**

Run: `./gradlew test`
Expected: many failures in writer tests (their golden files are old).

- [ ] **Step 19: Interim regeneration of writer test expectations**

For each of `CsvOutputWriterTest`, `YamlOutputWriterTest`, `MarkdownOutputWriterTest`, `HtmlOutputWriterTest`:

- update assertions to match whatever the writer currently emits after the mechanical rename in Steps 12–15. The accessor names changed (`score()` → `simpleScore()`, `decayedRevisions()` → `recencyDecay()`, `coverage()` → `coverageMultiplier()`) so any string-equality assertion that referenced the old labels needs the new label.
- The canonical column reordering happens in Tasks 3–6; here, just make compilation and tests green with the legacy column layout intact.

- [ ] **Step 20: Run full test suite**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 21: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor: unify hotspot scoring model into single-path pipeline

Records (FileHotspot, MethodHotspot, ApiHotspot, SharedComponentHotspot)
now carry seven non-nullable metric fields in canonical order. AnalysisMeta
drops scoringFormula. ScoringConfig.Formula enum and HotspotScoreCalculator
old methods are deleted. HotspotAnalyzer.analyze() runs a single pass that
always computes both Simple and Composite scores plus the four input
factors. Writers updated mechanically to call the renamed accessors;
column reformatting follows in subsequent commits.
EOF
)"
```

---

### Task 3: CsvOutputWriter — canonical column order

**Files:**
- Modify: `src/main/java/io/github/baekchangjoon/hotspotanalysis/output/CsvOutputWriter.java`
- Test: `src/test/java/io/github/baekchangjoon/hotspotanalysis/output/CsvOutputWriterTest.java`

- [ ] **Step 1: Update test to assert the new file header**

In `CsvOutputWriterTest`, the file-hotspots assertion. Replace the expected header line with:

```
rank,path,loc,revisions,simple_score,recency_decay,cognitive_complexity,coverage_multiplier,composite_score
```

And the method-hotspots header with:

```
rank,fqcn,method,parameters,file,start_line,end_line,loc,revisions,simple_score,recency_decay,cognitive_complexity,coverage_multiplier,composite_score
```

Update the expected row to match the canonical fixture (e.g. `1,src/main/java/com/example/Foo.java,120,3,360.0000,0.8500,7.0000,1.0000,5.9500`).

For api / shared (when emitted), assert these headers respectively:

```
rank,http_method,route,fqcn,method,parameters,loc,revisions,simple_score,recency_decay,cognitive_complexity,coverage_multiplier,composite_score
rank,fqcn,method,parameters,loc,revisions,simple_score,recency_decay,cognitive_complexity,coverage_multiplier,composite_score,calling_apis
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests CsvOutputWriterTest`
Expected: FAIL (column mismatch).

- [ ] **Step 3: Rewrite the writer**

Replace `writeFileHotspots(...)`'s header and row formatting:

```java
private void writeFileHotspots(AnalysisResult result, Path target) throws IOException {
    try (Writer w = Files.newBufferedWriter(target, StandardCharsets.UTF_8);
         CSVPrinter csv = new CSVPrinter(w, CSVFormat.DEFAULT.builder()
                 .setHeader("rank","path","loc","revisions","simple_score",
                            "recency_decay","cognitive_complexity","coverage_multiplier",
                            "composite_score")
                 .build())) {
        int rank = 1;
        for (FileHotspot f : result.fileHotspots()) {
            csv.printRecord(
                    rank++, f.path(), f.loc(), f.revisions(),
                    fmt(f.simpleScore()), fmt(f.recencyDecay()), fmt(f.cognitiveComplexity()),
                    fmt(f.coverageMultiplier()), fmt(f.compositeScore()));
        }
    }
}

private static String fmt(double v) {
    // 4 decimals when fractional, integer notation otherwise
    if (v == Math.floor(v) && !Double.isInfinite(v)) return Long.toString((long) v);
    return String.format(java.util.Locale.ROOT, "%.4f", v);
}
```

Apply analogous rewrites to `writeMethodHotspots`, `writeApiHotspots`, `writeSharedComponents`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests CsvOutputWriterTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/baekchangjoon/hotspotanalysis/output/CsvOutputWriter.java \
        src/test/java/io/github/baekchangjoon/hotspotanalysis/output/CsvOutputWriterTest.java
git commit -m "feat(output): CSV emits canonical 7-metric column block"
```

---

### Task 4: YamlOutputWriter — canonical keys

**Files:**
- Modify: `src/main/java/io/github/baekchangjoon/hotspotanalysis/output/YamlOutputWriter.java`
- Test: `src/test/java/io/github/baekchangjoon/hotspotanalysis/output/YamlOutputWriterTest.java`

- [ ] **Step 1: Update test to assert the new flat key layout**

Each row in `fileHotspots:` should serialise to:

```yaml
fileHotspots:
  - rank: 1
    path: src/main/java/com/example/Foo.java
    loc: 120
    revisions: 3
    simpleScore: 360.0
    recencyDecay: 0.85
    cognitiveComplexity: 7.0
    coverageMultiplier: 1.0
    compositeScore: 5.95
```

Update the test assertion accordingly (likely a YAML reparse + asserts on the resulting `Map`).

Method, api, shared rows analogous.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests YamlOutputWriterTest`
Expected: FAIL.

- [ ] **Step 3: Rewrite the writer**

Build a `LinkedHashMap` per row in canonical key order, then let Jackson serialise. Example skeleton:

```java
private static Map<String, Object> fileRow(int rank, FileHotspot f) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("rank", rank);
    m.put("path", f.path());
    m.put("loc", f.loc());
    m.put("revisions", f.revisions());
    m.put("simpleScore", round4(f.simpleScore()));
    m.put("recencyDecay", round4(f.recencyDecay()));
    m.put("cognitiveComplexity", round4(f.cognitiveComplexity()));
    m.put("coverageMultiplier", round4(f.coverageMultiplier()));
    m.put("compositeScore", round4(f.compositeScore()));
    return m;
}

private static double round4(double v) {
    return Math.round(v * 10_000.0) / 10_000.0;
}
```

Repeat for method, api, shared. Replace any old mappings that used `decayedRevisions` / `coverage` / `score` keys.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests YamlOutputWriterTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/baekchangjoon/hotspotanalysis/output/YamlOutputWriter.java \
        src/test/java/io/github/baekchangjoon/hotspotanalysis/output/YamlOutputWriterTest.java
git commit -m "feat(output): YAML emits canonical metric keys"
```

---

### Task 5: MarkdownOutputWriter — canonical columns

**Files:**
- Modify: `src/main/java/io/github/baekchangjoon/hotspotanalysis/output/MarkdownOutputWriter.java`
- Test: `src/test/java/io/github/baekchangjoon/hotspotanalysis/output/MarkdownOutputWriterTest.java`

- [ ] **Step 1: Update test to assert the new table header**

For files:

```markdown
| Rank | Path | LOC | Revisions | Simple Score | Recency Decay | Cognitive Complexity | Coverage Multiplier | Composite Score |
|---:|:---|---:|---:|---:|---:|---:|---:|---:|
```

For methods:

```markdown
| Rank | FQCN | Method | Parameters | File | Lines | LOC | Revisions | Simple Score | Recency Decay | Cognitive Complexity | Coverage Multiplier | Composite Score |
```

Api / shared analogous.

The meta table at the top no longer shows `Scoring formula:`; remove that row from expectations.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests MarkdownOutputWriterTest`
Expected: FAIL.

- [ ] **Step 3: Rewrite the writer**

Replace `appendFileTable`, `appendMethodTable`, `appendApiTable`, `appendSharedTable` with the new column order. Reuse `fmt(double)` helper from CSV step (move to a shared `Formatting` utility class in `output/` if duplication grows).

Remove the `| Scoring formula | %s |` line from the meta-table builder.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests MarkdownOutputWriterTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/baekchangjoon/hotspotanalysis/output/MarkdownOutputWriter.java \
        src/test/java/io/github/baekchangjoon/hotspotanalysis/output/MarkdownOutputWriterTest.java
git commit -m "feat(output): Markdown emits canonical metric columns"
```

---

### Task 6: HtmlOutputWriter — canonical columns + X-Ray drill-down

**Files:**
- Modify: `src/main/java/io/github/baekchangjoon/hotspotanalysis/output/HtmlOutputWriter.java`
- Test: `src/test/java/io/github/baekchangjoon/hotspotanalysis/output/HtmlOutputWriterTest.java`

- [ ] **Step 1: Update test to assert the new column headers**

Top file table `<th>`s in order:

```
Rank, Path, LOC, Revisions, Simple Score, Recency Decay, Cognitive Complexity, Coverage Multiplier, Composite Score
```

Top method table `<th>`s in order include the identifier columns then the 7 metrics — same order as CSV/MD.

X-Ray drill-down `<th>`s in order:

```
Method Signature, LOC, Revisions, Simple Score, Recency Decay, Cognitive Complexity, Coverage Multiplier, Composite Score, Share
```

Assert that the meta table contains no `Scoring formula` row.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests HtmlOutputWriterTest`
Expected: FAIL.

- [ ] **Step 3: Rewrite the writer**

Replace the `<thead>` HTML for each section. Update the `<tbody>` row template:

```java
sb.append("<tr class=\"file-row\" ...>")
  .append("<td class=\"rank\" data-sort-value=\"").append(rank).append("\">").append(rank).append("</td>")
  .append("<td><code>").append(escape(f.path())).append("</code>").append(xrayToggle).append("</td>")
  .append("<td class=\"num\" data-sort-value=\"").append(f.loc()).append("\">").append(f.loc()).append("</td>")
  .append("<td class=\"num\" data-sort-value=\"").append(f.revisions()).append("\">").append(f.revisions()).append("</td>")
  .append("<td class=\"num\" data-sort-value=\"").append(f.simpleScore()).append("\">").append(fmt(f.simpleScore())).append("</td>")
  .append("<td class=\"num\" data-sort-value=\"").append(f.recencyDecay()).append("\">").append(fmt(f.recencyDecay())).append("</td>")
  .append("<td class=\"num\" data-sort-value=\"").append(f.cognitiveComplexity()).append("\">").append(fmt(f.cognitiveComplexity())).append("</td>")
  .append("<td class=\"num\" data-sort-value=\"").append(f.coverageMultiplier()).append("\">").append(fmt(f.coverageMultiplier())).append("</td>")
  .append("<td class=\"num\" data-sort-value=\"").append(f.compositeScore()).append("\">").append(fmt(f.compositeScore())).append("</td>")
  .append("</tr>");
```

For the X-Ray drilldown: the `Share` column = `methodCompositeScore / fileCompositeScore × 100`. When `fileCompositeScore == 0`, fall back to share `0.0`.

Verify all user-controlled strings still go through the existing `escape(...)` helper — no XSS regressions.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests HtmlOutputWriterTest`
Expected: PASS.

- [ ] **Step 5: Smoke-test in a browser (manual)**

```bash
./gradlew build
java -jar build/libs/hotspot-0.1.0-SNAPSHOT.jar analyze --config /tmp/hotspot-self-composite.yml
open hotspot-report/hotspots.html
```

Click a file row, verify X-Ray drilldown shows the 9-column table. Click each header — verify sort works on every column.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/baekchangjoon/hotspotanalysis/output/HtmlOutputWriter.java \
        src/test/java/io/github/baekchangjoon/hotspotanalysis/output/HtmlOutputWriterTest.java
git commit -m "feat(output): HTML report + X-Ray drilldown emit canonical 7-metric columns"
```

---

### Task 7: ConfigLoader migration message for `scoring.formula`

**Files:**
- Modify: `src/main/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigLoader.java`
- Test: `src/test/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigLoaderTest.java`

- [ ] **Step 1: Add a failing test for the migration message**

```java
@Test
void rejectsLegacyScoringFormulaKeyWithFriendlyMessage() throws Exception {
    Path yml = tempDir.resolve("legacy.yml");
    Files.writeString(yml, """
        analysis:
          target: { type: local-git, path: /tmp/some-repo }
          window: { days: 365 }
          scope:
            granularity: [file]
            include: ["src/main/java/**/*.java"]
          scoring:
            formula: simple
        output:
          formats: [csv]
          path: ./hotspot-report
          topN: 0
        """);
    assertThatThrownBy(() -> loader.load(yml))
            .isInstanceOf(ConfigLoadException.class)
            .hasMessageContaining("scoring.formula has been removed")
            .hasMessageContaining("Delete this line");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests ConfigLoaderTest.rejectsLegacyScoringFormulaKeyWithFriendlyMessage`
Expected: FAIL (current message is the generic "Unknown configuration key: formula").

- [ ] **Step 3: Specialise the `UnrecognizedPropertyException` branch**

In `ConfigLoader#parse(...)` replace:

```java
} catch (UnrecognizedPropertyException ex) {
    throw new ConfigLoadException(
            "Unknown configuration key: " + ex.getPropertyName(), ex);
}
```

with:

```java
} catch (UnrecognizedPropertyException ex) {
    if ("formula".equals(ex.getPropertyName())) {
        throw new ConfigLoadException(
                "scoring.formula has been removed in v0.2 — all reports now"
                        + " include both Simple and Composite scores. Delete this line.",
                ex);
    }
    throw new ConfigLoadException(
            "Unknown configuration key: " + ex.getPropertyName(), ex);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests ConfigLoaderTest`
Expected: PASS (new test + all existing tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigLoader.java \
        src/test/java/io/github/baekchangjoon/hotspotanalysis/config/ConfigLoaderTest.java
git commit -m "feat(config): friendly migration error for legacy scoring.formula key"
```

---

### Task 8: HotspotCliE2ETest — unified end-to-end happy path

**Files:**
- Modify: `src/test/java/io/github/baekchangjoon/hotspotanalysis/HotspotCliE2ETest.java`

- [ ] **Step 1: Replace the existing composite-formula test with a unified test**

Replace any test named `analyzesUnderCompositeFormula` (or similar) with:

```java
@Test
void analyzeEmitsAllSevenMetricsInEveryReport(@TempDir Path tempDir) throws Exception {
    Path repo = unpackFixture(tempDir, "tiny-repo-1.zip");
    Path configFile = tempDir.resolve("hotspot.yml");
    Files.writeString(configFile, """
        analysis:
          target: { type: local-git, path: %s }
          window: { since: "2017-01-01", until: "2026-12-31" }
          scope:
            granularity: [file, method]
            include: ["src/main/java/**/*.java"]
        output:
          formats: [csv, yaml, md, html]
          path: %s/out
          topN: 20
        """.formatted(repo, tempDir));

    int exit = new CommandLine(rootCommand)
            .execute("analyze", "--config", configFile.toString(), "--quiet");
    assertThat(exit).isZero();

    Path csvFile = tempDir.resolve("out/file_hotspots.csv");
    String csv = Files.readString(csvFile);
    String header = csv.lines().findFirst().orElseThrow();
    assertThat(header).isEqualTo(
            "rank,path,loc,revisions,simple_score,recency_decay,cognitive_complexity,coverage_multiplier,composite_score");

    // and analogous header asserts for method/api(when enabled)/shared csv, plus
    // a quick sniff on yaml/md/html that they contain "compositeScore" / "Composite Score"
    assertThat(Files.readString(tempDir.resolve("out/hotspots.yml")))
            .contains("compositeScore").contains("simpleScore");
    assertThat(Files.readString(tempDir.resolve("out/hotspots.md")))
            .contains("Composite Score").contains("Simple Score");
    assertThat(Files.readString(tempDir.resolve("out/hotspots.html")))
            .contains("Composite Score").contains("Simple Score")
            .doesNotContain("Scoring formula");
}
```

Remove any other tests that branched on `formula: simple|composite`.

- [ ] **Step 2: Run E2E test**

Run: `./gradlew test --tests HotspotCliE2ETest`
Expected: PASS.

- [ ] **Step 3: Run full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/io/github/baekchangjoon/hotspotanalysis/HotspotCliE2ETest.java
git commit -m "test(e2e): assert unified 7-metric output across all formats"
```

---

### Task 9: Sample config + documentation sync

**Files:**
- Modify: `src/main/resources/templates/hotspot.example.yml`
- Modify: `README.md`
- Modify: `docs/phase1-design.md`
- Modify: `docs/hotspot-advanced-spec.md`

- [ ] **Step 1: Update `hotspot.example.yml`**

Remove the `formula: simple` line. Replace the scoring block with:

```yaml
  scoring:
    # Optional. Half-life (days) for the exponential recency decay weight
    # applied to each commit. 90 days is the default; raise for slow-moving
    # legacy code, lower for fast-moving features.
    decayHalfLifeDays: 90

  # Optional. When supplied, every file/method's Coverage Multiplier is
  # computed as 1 / (line_coverage + 0.1). Absent → multiplier defaults
  # to 1.0 (no coverage penalty).
  jacocoReportPath: build/reports/jacoco/test/jacocoTestReport.xml
```

Verify the file parses through the existing `HotspotExampleConfigTest` (`./gradlew test --tests HotspotExampleConfigTest`).

- [ ] **Step 2: Rewrite README "How the score is computed" section**

Open `README.md`, locate the "How the score is computed" section, and replace it with:

```markdown
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
```

Also update any older references to `formula: simple|composite` or `Tests: 145`.

- [ ] **Step 3: Append §13 "v0.2: Unified scoring model" to `docs/phase1-design.md`**

```markdown
## 13. v0.2: Unified scoring model

Starting v0.2, the `SIMPLE`/`COMPOSITE` distinction is gone. Every run
computes both scores plus the four input factors and emits them side by
side in every output format. See
`docs/superpowers/specs/2026-05-25-unified-scoring-design.md` for the
approved design.
```

(Intentionally do not embed a SHA — leave the section version-agnostic so it stays accurate after rebase.)

- [ ] **Step 4: Add leading note to `docs/hotspot-advanced-spec.md`**

At the very top, under the existing title, insert:

```markdown
> **v0.2 note.** Starting v0.2, all four factors described below
> (Recency Decay, Cognitive Complexity, Coverage Multiplier, X-Ray) are
> always computed — there is no longer a `formula: simple|composite`
> toggle. See `docs/advanced-techniques-verification.md` for the
> validation results.
```

- [ ] **Step 5: Verify docs render**

Run: `git diff --stat` and visually inspect the updated docs in your editor.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/hotspot.example.yml README.md \
        docs/phase1-design.md docs/hotspot-advanced-spec.md
git commit -m "docs: sync sample config and design docs to unified scoring model"
```

---

## Final checklist (after Task 9)

Run once before opening the PR:

```bash
./gradlew clean check                                                              # all unit + E2E green
java -jar build/libs/hotspot-0.1.0-SNAPSHOT.jar analyze --config /tmp/hotspot-self-composite.yml   # smoke run
head -1 hotspot-report/file_hotspots.csv                                           # canonical header
grep "Simple Score" hotspot-report/hotspots.md hotspot-report/hotspots.html        # both formats include it
grep "compositeScore" hotspot-report/hotspots.yml                                  # YAML includes it
```

Expected:
- `clean check` BUILD SUCCESSFUL
- CSV header = `rank,path,loc,revisions,simple_score,recency_decay,cognitive_complexity,coverage_multiplier,composite_score`
- "Simple Score" present in both MD and HTML
- `compositeScore` key present in YAML

If all four pass, open a PR against `main`. CI's `Build & Test (Java 21)` check must pass before merge (rebase only, per repo rules).
