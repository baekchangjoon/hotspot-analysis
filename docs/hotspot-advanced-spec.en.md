# Hotspot Analysis Advanced Detailed Specification and Calculation Formulas

> 🌐 [한국어](hotspot-advanced-spec.md) · **English** (this page)

> **v0.2 note.** Starting v0.2, all four factors described below
> (Recency Decay, Cognitive Complexity, Coverage Multiplier, X-Ray) are
> always computed — there is no longer a `formula: simple|composite`
> toggle. See `docs/advanced-techniques-verification.en.md` for the
> validation results.

This document explains the operating principles, detailed mathematical calculation formulas, and component architecture design of the advanced core techniques (A-2, B-2, C-4, C-5) to be additionally introduced into the `hotspot-analysis` project.

---

## 1. B-2. Recency Decay (Change-Time Decay Weighting)

### 1.1. Concept and Operating Principle
If you simply count cumulative commits, code that changed frequently years ago but is now very stable (Cold Legacy) takes a high score and distorts the analysis. By giving higher weight to code that is being actively modified recently, we precisely pinpoint the currently ongoing unstable regions.

### 1.2. Mathematical Calculation Formula
For each commit $c$, we compute the time gap $\Delta t_c$ (unit: days) between when it occurred and the analysis reference point (Until), and use an exponential decay function to derive the **Effective Revisions**.

$$\text{Effective Revisions}(F) = \sum_{c \in \text{Commits}(F)} e^{-\lambda \Delta t_c}$$

*   $\Delta t_c = \text{Date}_{\text{until}} - \text{Date}_{\text{commit}}(c)$ (unit: days)
*   $\lambda = \frac{\ln(2)}{t_{half}}$ (decay constant; $t_{half}$ is the half-life at which the score drops by half, default: 90 days or 180 days)

When the half-life $t_{half} = 90$:
- A commit made today scores $e^0 = 1.0$
- A commit made 90 days ago scores $e^{-\frac{\ln(2)}{90} \times 90} = 0.5$
- A commit made 180 days ago scores $e^{-\frac{\ln(2)}{90} \times 180} = 0.25$

---

## 2. A-2. Cognitive Complexity

### 2.1. Concept and Operating Principle
Simply counting total lines of code (LOC) can be inflated by comments, bracket formatting, and the like, and does not fully represent actual complexity. Cognitive Complexity quantifies the real level of mental computation burden imposed on a reader by accumulating weights for the code's control flow (branches, loops, exception handling) and their nesting depth (Nesting Level).

### 2.2. Calculation Formula and Rules (Based on the SonarQube Spec)
1.  **+1 point**: When a control structure appears (`if`, `for`, `while`, `catch`, `switch`, `&&`, `||`, etc.)
2.  **Nesting increment (Nesting Level)**: When control structures are nested, an additional weight is added per depth level
    *   Example: if a `for` is nested inside an `if`, the `for` statement earns a base increment of 1 point + a nesting level of 1 point = 2 points total.
3.  **Exception rules**: Simple method declarations, single-branch flows, simple annotations, and the like are not counted.

$$\text{Cognitive Complexity}(M) = \sum_{n \in \text{Nodes}(M)} (\text{Base}(n) + \text{Nesting}(n))$$

---

## 3. C-4. Coverage Gap (Test Coverage Integration)

### 3.1. Concept and Operating Principle
No matter how complex and frequently changed a hotspot is, if it is protected by solid integration/unit test code, its relative risk of leading to a production failure is low. By substantially increasing the alarm weighting for risk regions that have no test safety net (Coverage Gap), we re-prioritize refactoring.

### 3.2. Mathematical Calculation Formula
From an external JaCoCo XML report, we read the line coverage ratio of each source file ($Cov_F \in [0.0, 1.0]$). When computing the overall hotspot risk, we multiply by the inverse coverage as a weighting factor.

$$\text{Coverage Multiplier}(F) = \frac{1}{\text{Line Coverage}(F) + \epsilon}$$

*   $\epsilon$: a minimum correction constant to prevent the denominator from becoming 0 (default: $0.1$)
*   When coverage is $100\%$ ($1.0$): the multiplier is $\frac{1}{1.0 + 0.1} \approx 0.909$
*   When coverage is $0\%$ ($0.0$): the multiplier is $\frac{1}{0.0 + 0.1} = 10.0$ (the risk of an untested complex region is amplified up to 10x)

---

## 4. C-5. X-Ray (Method-Level Precision Investigation and Visualization)

### 4.1. Concept and Operating Principle
When a huge file (God Class) is detected as a hotspot, refactoring the entire file at once is unrealistic due to the large effort required. X-Ray analysis isolates and explores the core methods within the file that are actually modified frequently and are most complex, and visualizes their score share as a sub-report.

### 4.2. Score Mapping Structure
*   Under each file's hotspot, the method list is expanded and output in a tree structure.
*   For the sub-methods contained in that file, we compute each individual `Score(Method) = Revisions(Method) × Cognitive Complexity(Method)` and derive the percentage share of score within the file.
    $$\text{Method Score Share}(M) = \frac{\text{Score}(M)}{\sum_{m \in \text{Methods}(F)} \text{Score}(m)} \times 100\%$$

---

## 5. Composite Hotspot Score

The formula for the final Composite Score, derived by combining the four factors above, is as follows.

$$\text{Composite Score}(F) = \text{Cognitive Complexity}(F) \times \text{Effective Revisions}(F) \times \text{Coverage Multiplier}(F)$$

$$\text{Composite Score}(F) = \text{Cognitive Complexity}(F) \times \left( \sum_{c \in \text{Commits}(F)} e^{-\frac{\ln(2)}{t_{half}} \Delta t_c} \right) \times \left( \frac{1}{\text{Line Coverage}(F) + 0.1} \right)$$
