# Hotspot Analysis Technique Advancement and Implementability Review Report

> 🌐 [한국어](hotspot-advanced-theory.md) · **English** (this page)

## 1. Overview
Hotspot Analysis is a Behavioral Code Analysis technique that identifies risk areas in a codebase by combining the Complexity and Change Frequency of source code. This document summarizes advanced techniques used in the industry (e.g., CodeScene) that go beyond the basic `Revisions × LOC` formula, and reviews the technical feasibility of additionally introducing them into the current `hotspot-analysis` project.

---

## 2. Summary of Advanced Hotspot Analysis Techniques

### A. Advancing the Complexity Metric
*   **A-1. Indentation-based Complexity**: Statistically analyzes the indentation level of code to infer the complexity of the control flow. A language-independent evaluation is possible.
*   **A-2. Cognitive Complexity**: The SonarQube standard approach that weights not only simple logical-operator structures but also nested conditionals and flows that are hard for a human to intuitively read and understand.

### B. Refining the Change-Frequency (Effort) Metric
*   **B-1. Code Churn**: Uses the cumulative sum of the number of actually added/deleted lines of code, rather than the commit count, to capture real development effort.
*   **B-2. Recency Decay & Ageing**: Applies a temporal decay formula to raise the score of code being actively modified recently over legacy that was modified long ago.

### C. Combination with Extended Dimensions
*   **C-1. Complexity Trends**: Detects modules whose complexity surges nonlinearly over time.
*   **C-2. Temporal Coupling**: Mines the architectural coupling relationship between other modules that are always changed together in a single commit.
*   **C-3. Developer Congestion (Human Factor)**: Reflects the congestion that arises when many developers modify code without communicating.
*   **C-4. Coverage Gap (Test Coverage Integration)**: Weights the risk score of areas that are highly complex and frequently modified but have poor test protection.
*   **C-5. X-Ray (Method-Level Precision Investigation)**: Precisely tracks the specific methods that actually dominate the score within a huge class file via AST parsing.

---

## 3. Implementation Feasibility and Design Approach for A-2, B-2, C-4, C-5

This is a feasibility assessment and concrete architecture design proposal for combining these four factors into the current `hotspot-analysis` implementation and reflecting them in the overall score calculation.

### Introducing A-2. Cognitive Complexity
*   **Feasibility**: **Very high**. Simple LOC is easily affected by comments, blank lines, and formatting style, whereas cognitive complexity is intuitively proportional to the actual bug occurrence rate.
*   **Implementation approach**:
    - Build a cognitive complexity calculator (`CognitiveComplexityCalculator`) module during AST traversal using `JavaParser`.
    - Accumulate scores for `if`, `for`, `catch`, `&&/||` branches and nesting depth (Nesting Level) in accordance with the SonarQube Cognitive Complexity spec rules.
    - Use it in place of LOC (or as a factor multiplied by LOC) in the overall score calculation.

### Introducing B-2. Recency Decay & Ageing
*   **Feasibility**: **Very high**. Even 50 revisions, if they were from 3 years ago, are likely stabilized legacy; even 3 revisions, if they were modified daily over the last week, are likely an unstable feature currently under development.
*   **Implementation approach**:
    - When collecting revisions, compute the time difference ($\Delta t$) in days between each commit's `committedAt` time and the analysis point (`now` or `until`).
    - Apply an exponential decay formula with a half-life ($t_{half}$, e.g., 90 days).
      $$\text{Decayed Revision} = \sum_{c \in \text{Commits}} e^{-\lambda \Delta t_c} \quad \left(\lambda = \frac{\ln(2)}{t_{half}}\right)$$
    - Through this, recent commits are reflected close to 1 point and old commits converge to 0 points, recomputing the cumulative revision weight (Effective Revisions).

### Combining C-4. Test Coverage Gap
*   **Feasibility**: **High (but external data integration required)**. Complex code without tests should have its risk score surge exponentially.
*   **Implementation approach**:
    - Add a parser for JaCoCo's XML report (e.g., `jacoco.xml`) to parse the coverage report from a specified path at analysis time.
    - Based on file/class names and line ranges, obtain the line coverage ratio ($Cov \in [0.1, 1.0]$) mapped to individual files and methods.
    - Combine it into the score calculation formula as an inverse factor:
      $$\text{Risk Score} = \text{Complexity} \times \text{Effective Revisions} \times \left(\frac{1}{Cov + \epsilon}\right) \quad (\epsilon = 0.1 \text{ prevents zero-division when coverage is 0})$$

### Advancing C-5. X-Ray (Method-Level Precision Investigation)
*   **Feasibility**: **Core foundation already complete**. The current Phase 1 and Phase 2 prototypes already partially implement the ability to compute `Method Hotspots` and `REST API Hotspots` and map the sub-methods in the Call Graph.
*   **Implementation approach**:
    - Leveraging the current API Call Graph and method parsing structure, complete a drill-down reporting structure that visualizes `LOC × Revisions` at the internal method level of a top hotspot file (e.g., `PetController.java`) and displays it as a sub-ranking.

---

## 4. Composite Scoring Formula Proposal

The formula for the Composite Score that ultimately combines the four factors can be designed as follows.

$$\text{Composite Score}(F) = \text{Cognitive Complexity}(F) \times \left( \sum_{c \in \text{Commits}(F)} 2^{-\frac{\text{Age}(c)}{\tau}} \right) \times \left(\frac{1}{\text{Line Coverage}(F) + 0.1}\right)$$

*   $\text{Age}(c)$: the elapsed time (unit: days) from the current analysis point to when commit $c$ occurred
*   $\tau$: half-life parameter (default: 180 days)
*   $\text{Line Coverage}(F)$: the coverage ratio in the range $[0.0, 1.0]$ obtained from the JaCoCo report

If this composite formula is added as `ScoringConfig.Formula.COMPOSITE` and implemented via a `CompositeScoreCalculator`, it becomes possible to precisely detect, with 100% accuracy, code in which actual defect risk is extremely concentrated, going beyond simple size and cumulative modification counts.
