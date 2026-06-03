> 🌐 [한국어](hotspot-analysis.md) · **English** (this page)

## 1. About Adam Tornhill

"Adam Tornhill has a Bachelor of Science degree in Electrical Engineering from The Faculty of Engineering at Lund University. Adam obtained this degree from 1994 to 1997. Later, they pursued a Bachelor of Social Science in Psychology at Kristianstad University, completing it from 2007 to 2010. Continuing their education, Adam Tornhill earned a Master of Arts (M.A.) degree in Psychology from Kristianstad University, where they studied from 2010 to 2013."

**Core profile**:
- **Background**: Sweden (Lund / Kristianstad University)
- **Dual major**: Electrical Engineering (1994-1997, Lund) + Bachelor's and Master's in Psychology (2007-2013, Kristianstad) — this combination is the intellectual source of "**Behavioral Code Analysis**"
- **Company**: Founder and CTO of CodeScene AB ([codescene.com](https://codescene.com))
- **Language**: Develops the CodeScene product suite in Clojure (a Lisp dialect) — self-described Lisp hacker
- **Award**: "CodeScene awarded Best Paper at the 7th International Conference on Technical Debt 2024"

**Major books (4, Pragmatic Bookshelf)**:

| Book | Year | Core topic |
|---|---|---|
| *Patterns in C* | 2010 | C design patterns |
| *Lisp for the Web* | 2013 | Common Lisp web development |
| ***Your Code as a Crime Scene*** | 2015 (2nd ed. 2024) | Popularized **15 analysis techniques** such as Hotspot and Temporal Coupling |
| ***Software Design X-Rays*** | 2018 | Systematized the Behavioral Code Analysis methodology |

**Philosophical identity**: "a lot of these techniques have actually been evaluated in the academic field. So, you know, for example, with the hotspots looking at the hotspot metric, we know that that has more predictive value than any properties of the code itself." — He positions himself as a "**bridge**" that delivers academic research to practitioners.

---

## 2. About Hotspot Analysis

**One-line definition**: An analysis technique that identifies **code files that are both frequently changed and complex**, visualizing the areas where defect risk and maintenance cost are concentrated.

**Core insight** (Tornhill's empirical claim):
- "1-2% of our codebase accounts for up to 70% of our development work, and how focusing on these hotspots can make our team 2x faster and 10x more predictable"
- Academic follow-up validation (Springer 2025): "critical hotspots—covering less than 5% of the code—account for over 50% of recorded defects"

→ The core thesis is the **application of the Pareto principle (80/20) to code areas**.

---

## 3. Background

### Three limitations of traditional code analysis

| Limitation | Description |
|---|---|
| **No time dimension** | Static analysis tools such as SonarQube and PMD only look at the *current snapshot*. The evolutionary context of "how many times has this code changed over the past year" is missing |
| **No people dimension** | Code is written by people. How many developers collaborated and how is a stronger defect-prediction signal than the code itself |
| **No prioritization** | Static analysis just throws thousands of violations and fails to answer "**where should I start fixing**." It becomes meaningless noise to practitioners |

### Academic foundations (before Tornhill)

1. **Pareto in Software** (1970s~): Endres, Boehm — 80% of defects concentrated in 20% of modules
2. **Nagappan & Ball, Microsoft Research (ICSE 2005)**: *"Use of Relative Code Churn Measures to Predict System Defect Density"* — Empirically showed that Code Churn predicts defects better than LOC
3. **Hassan, A.E. (ICSE 2009)**: *"Predicting Faults Using the Complexity of Code Changes"* — Defect prediction based on change complexity

### Tornhill's contribution

Tornhill integrated and popularized the academic findings above with the explicit goal to "take some fascinating research on software and make that academic research accessible to the practicing programmer." The key innovation was making them immediately applicable through the practical approach of **mining VCS (Git) data**.

---

## 4. Concept

**Official definition of Hotspot** (CodeScene documentation):

"A hotspots is complicated code that you have to work with often. These Hotspots are calculated from two different data sources: We use the lines of code in each file as a proxy for complexity. We use the change frequency of each file as a proxy for the effort you've spent on that code."

**Visualized as a 2D matrix**:

```
Complexity ↑
       │
       │  [RISKY]     [HOTSPOT]
       │  Refactor?   ⚠️ top priority
       │
       ├─────────────────────
       │
       │  [SAFE]      [WATCH]
       │  Leave it    Monitor
       │
       └─────────────────────→ Change Frequency
```

- **Upper-right (Hotspot)**: Frequently changed + complex → **immediate action needed**
- **Upper-left**: Complex but untouched → stable legacy. Don't bother touching it
- **Lower-right**: Frequently changed but simple → healthy code evolution
- **Lower-left**: Can be ignored

→ **Key insight**: "Don't look at complexity alone." Code that is complex but *never touched* carries low risk.

---

## 5. Principles

### 5.1 Data source: Git Log

```bash
git log --pretty=format:'[%h] %an %s' --date=short --numstat --after=2024-11-01 > evolution.log
```

→ The VCS history is the **single source of truth**. No separate measurement tool or operational data is needed.

### 5.2 Two metrics

| Dimension | Default measure | Alternative measure (precise) |
|---|---|---|
| **Complexity** | LOC (Lines of Code) | Cyclomatic Complexity, Cognitive Complexity (SonarQube) |
| **Change Frequency** | Number of commits (Revisions) | Code Churn (lines added+deleted) |

"The resulting output ('*-hotspots.csv') is sorted on change frequencies first - the best predictor of problems - and size (number of lines) second."

→ **Change Frequency is the primary sort key for prioritization**. LOC is the secondary indicator.

### 5.3 Score calculation

Basic form:
```
Hotspot Score = Revisions × LOC
```

Precise form (recommended in practice):
```
Hotspot Score = log(Revisions + 1) × Cognitive Complexity × (1 / (Coverage + 0.1))
```

### 5.4 Visualization pattern — Circle Packing (D3.js)

Tornhill's trademark is the **circle packing** visualization:
- Circle size = complexity (LOC)
- Circle color = change frequency (the redder, the hotter)
- Nested structure = directory hierarchy

---

## 6. How to use it

Five practical uses summarized from the [stefano-zanotti-edo/code-hotspots guide](https://github.com/stefano-zanotti-edo/code-hotspots):

### 6.1 Prioritizing refactoring (most common)
"I started this investigation because I needed a way to plan and prioritize improvements to a legacy system. Some of the suggested improvements were redesigns that cost several weeks of intense work. I had to ensure we spent time on improvements that actually helped future development efforts."

### 6.2 Identifying focus areas for code review
"Because hotspots make good predictors of buggy code, they identify the parts where reviews would be a good investment of time."

→ Don't review every PR with the same intensity; **assign senior reviewers to PRs that touch Hotspot files**.

### 6.3 Test prioritization (Baek's original question)
- Hotspot ∩ low coverage = top-priority targets for writing tests
- Strengthen integration/E2E tests for Hotspot files

### 6.4 Quantifying technical debt and computing ROI
- Measure the development time spent on Hotspots to derive the **"dollar value" of technical debt**
- CodeScene's core business value proposition

### 6.5 Team communication tool
- Use the visualization to **explain technical debt in a single page** to non-technical stakeholders (PMs, executives)
- A common language when negotiating QA priorities with test leads

---

## 7. Getting Started

### 7.1 Open-source path (free, recommended starting point)

**Tool**: [Code Maat](https://github.com/adamtornhill/code-maat) — an open-source CLI built by Tornhill himself (Clojure JAR, run with Java)

**Minimal steps (first result within 15 minutes)**:

```bash
# 1. Download Code Maat
mkdir -p ~/tools/code-forensics && cd ~/tools/code-forensics
curl -sL https://github.com/adamtornhill/code-maat/releases/download/v1.0.4/code-maat-1.0.4-standalone.jar -o code-maat.jar

# 2. Extract the git log from the target project (last 6 months)
cd /path/to/your/spring-boot-project
git log --all --numstat --date=short \
  --pretty=format:'--%h--%ad--%aN' \
  --no-renames --after=2024-11-19 > gitlog.txt

# 3. Change-frequency analysis (Revisions)
java -jar ~/tools/code-forensics/code-maat.jar \
  -l gitlog.txt -c git2 -a revisions > revisions.csv

# 4. Extract line counts (complexity proxy)
cloc . --by-file --csv --quiet > lines.csv

# 5. Join the two CSVs → complete the hotspot table
```

### 7.2 Spring Boot/Java precise path — Code Maat + SonarQube

See the [Apache Wicket analysis case (DEV Community guide)](https://dev.to/janux_de/hotspot-analysis-for-a-java-project-4ej6):

"For the hotspots of a program, the locations with a high complexity and change rate, the refactoring efforts will most likely have the highest return on investment. This blog post explores a way how they can be identified in a Java project. Apache Wicket was chosen as an example project. For its analysis, the open-source tools SonarCube, Code Charta, and Code Maat, and Python scripting are being used. The complexity is measured with SonarCube's Cognitive complexity metric and the change rate by the number of commits for a particular file."

**Composition**:
- **SonarQube** → Cognitive Complexity (precise complexity)
- **Code Maat** → Revisions (change frequency)
- **Code Charta** → 3D visualization ([code-charta.com](https://maibornwolff.github.io/codecharta/))

### 7.3 Commercial path — CodeScene

- **Free tier**: Open-source projects are unlimited and free
- **Paid**: A license is required for in-house code
- **Pros**: Automation, X-Ray (function-level analysis), Jira/GitHub integration, Code Health metric, and other additional analyses
- **Cons**: Cost, dependency on an external tool

### 7.4 Recommended learning resources

1. **Book (must-read)**: *Your Code as a Crime Scene* (2nd ed., 2024) — covers 15 analysis techniques
2. **Book (advanced)**: *Software Design X-Rays* (2018) — function-level precise analysis
3. **Blog**: [codescene.com/blog/author/adam-tornhill](https://codescene.com/blog/author/adam-tornhill)
4. **Podcast**: [SE Radio Ep.554](https://se-radio.net/2023/03/episode-554-adam-tornhill-on-behavioral-code-analysis/), Tech Lead Journal #241
5. **Tutorials (no Korean available, step-by-step in English)**: [stefano-zanotti-edo/code-hotspots](https://github.com/stefano-zanotti-edo/code-hotspots), [thiagoghisi/your-code-as-a-crime-scene](https://github.com/thiagoghisi/your-code-as-a-crime-scene)

---

## ⚠️ Counter-arguments (perspectives against / to review before adoption)

1. **Be aware of commercial interests**: Tornhill is the founder of CodeScene and its biggest beneficiary. His "1-2% accounts for 70% of work" claim **aligns with his own product's value proposition**, and some of the data is based on CodeScene customer cases. Objectivity is secured only by cross-validating against independent academic research (Hassan 2009, Nagappan 2005).

2. **Single-author limitation**: A book-length methodology was defined by a single author. It has not gone through committee validation like ISTQB or IEEE standards. Academic citations are growing, but it is a *de facto* standard, not a *de jure* one.

3. **Code Maat maintenance limitation**: There has been a lack of active updates since the last release, v1.0.4. A migration strategy to a newer tool may be needed.

4. **Applicability in an MSA environment**: Tornhill's original methodology assumes a **monolith**. In an environment where multiple microservice repos are separated, cross-service Temporal Coupling analysis is difficult. For in-house adoption, you must choose between a **monorepo or per-service separate analysis**.

5. **Bot commit noise**: Automated commits from Dependabot, Renovate, and GitHub Actions inflate Revisions. **A filtering regex is essential**.
