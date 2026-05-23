# Hotspot Analysis 기법 고도화 및 구현성 검토 보고서

## 1. 개요
Hotspot Analysis는 소스 코드의 복잡도(Complexity)와 변경 빈도(Change Frequency)를 결합하여 코드베이스의 위험 영역을 식별하는 Behavioral Code Analysis 기법입니다. 본 문서에서는 기본적인 `Revisions × LOC` 공식을 넘어 업계(예: CodeScene 등)에서 활용되는 고도화 기법들을 요약하고, 이를 현재 `hotspot-analysis` 프로젝트에 추가로 도입할 때의 기술적 타당성을 검토합니다.

---

## 2. Hotspot Analysis 고도화 기법 요약

### A. 복잡도(Complexity) 메트릭의 고도화
*   **A-1. Indentation-based Complexity (들여쓰기 기반 복잡도)**: 코드의 들여쓰기 수준을 통계적으로 분석하여 제어 흐름의 복잡도를 유추합니다. 언어 독립적인 평가가 가능합니다.
*   **A-2. Cognitive Complexity (인지 복잡도)**: 단순 논리 연산 구조뿐만 아니라, 중첩 조건문이나 사람이 직관적으로 코드를 읽고 이해하기 어려운 흐름에 가중치를 주는 SonarQube 표준 방식입니다.

### B. 변경 빈도(Effort) 메트릭의 정밀화
*   **B-1. Code Churn (코드 변경량)**: 커밋 횟수가 아닌, 실제 추가/삭제된 코드 라인 수의 누적합을 활용하여 실질적 개발 공수를 포착합니다.
*   **B-2. Recency Decay & Ageing (최신성 가중)**: 시간적 감쇄 공식을 적용하여 오래전 수정된 레거시보다 최근 활발히 수정 중인 코드의 스코어를 상승시킵니다.

### C. 확장된 차원과의 결합
*   **C-1. Complexity Trends (복잡도 추세)**: 시간의 흐름에 따라 비선형적으로 복잡도가 치솟는 모듈을 탐지합니다.
*   **C-2. Temporal Coupling (시간적 결합도)**: 한 커밋에서 항상 묶여 바뀌는 다른 모듈들 간의 아키텍처적 결합 관계를 마이닝합니다.
*   **C-3. Developer Congestion (인적 요인)**: 다수의 개발자가 소통 없이 수정할 때 발생하는 혼잡도를 반영합니다.
*   **C-4. Coverage Gap (테스트 커버리지 결합)**: 복잡도가 높고 자주 수정되지만 테스트 보호가 부실한 영역의 위험 점수를 가중합니다.
*   **C-5. X-Ray (메소드 단위 정밀 조사)**: 거대 클래스 파일 내부에서 실질적인 스코어를 지배하는 특정 메소드를 AST 파싱으로 정밀 추적합니다.

---

## 3. A-2, B-2, C-4, C-5 구현 타당성 및 설계 방안

현재 `hotspot-analysis` 구현에 이 네 가지 요소를 결합하여 전체 점수 계산에 반영하는 방안의 타당성 및 구체적인 아키텍처 설계 제안입니다.

### A-2. 인지 복잡도 (Cognitive Complexity) 도입
*   **타당성**: **매우 높음**. 단순 LOC는 주석이나 빈 줄, 포맷팅 방식에 영향받기 쉽지만, 인지 복잡도는 실제 버그 발생율과 직관적으로 정비례합니다.
*   **구현 방안**: 
    - `JavaParser`를 활용한 AST 탐색 과정에서 인지 복잡도 계산기(`CognitiveComplexityCalculator`) 모듈을 구축합니다.
    - `if`, `for`, `catch`, `&&/||` 분기 및 중첩 깊이(Nesting Level)에 대해 SonarQube Cognitive Complexity 스펙 규칙에 맞춰 점수를 누적합니다.
    - 전체 점수 계산 시 LOC 대용(또는 LOC와 곱해지는 인자)으로 사용합니다.

### B-2. 최신성 가중 및 감쇄 (Recency Decay & Ageing) 도입
*   **타당성**: **매우 높음**. 리비전 수가 50회여도 3년 전 이야기라면 안정화된 레거시일 가능성이 높고, 3회여도 최근 1주 동안 매일 수정되었다면 현재 개발 중인 불안정한 피처일 가능성이 큽니다.
*   **구현 방안**:
    - 리비전 수집 시 각 커밋의 `committedAt` 시간과 분석 시점(`now` 또는 `until`) 사이의 시간 차이($\Delta t$)를 일(Days) 단위로 계산합니다.
    - 하프라이프(Half-life, 반감기 $t_{half}$, 예: 90일)를 둔 지수 감쇄 공식(Exponential Decay)을 적용합니다.
      $$\text{Decayed Revision} = \sum_{c \in \text{Commits}} e^{-\lambda \Delta t_c} \quad \left(\lambda = \frac{\ln(2)}{t_{half}}\right)$$
    - 이를 통해 최근 커밋은 1점에 가깝게, 오래된 커밋은 0점에 수렴하도록 반영하여 누적 리비전 가중치(Effective Revisions)를 재계산합니다.

### C-4. 테스트 커버리지 Gap 결합
*   **타당성**: **높음 (단, 외부 데이터 연동 필요)**. 테스트가 없는 복잡한 코드는 위험 점수가 기하급수적으로 치솟아야 합니다.
*   **구현 방안**:
    - JaCoCo의 XML 리포트(예: `jacoco.xml`) 파서를 추가하여 분석 시점에 지정된 경로에서 커버리지 리포트를 파싱합니다.
    - 파일/클래스 명칭 및 라인 범위를 기준으로 개별 파일 및 메소드에 매핑되는 라인 커버리지 비율($Cov \in [0.1, 1.0]$)을 확보합니다.
    - 스코어 계산 공식에 반비례 인자로 결합합니다:
      $$\text{Risk Score} = \text{Complexity} \times \text{Effective Revisions} \times \left(\frac{1}{Cov + \epsilon}\right) \quad (\epsilon = 0.1 \text{은 커버리지 0일 때의 Zero-division 방지용})$$

### C-5. X-Ray (메소드 단위 정밀 조사) 고도화
*   **타당성**: **이미 핵심 기반 완료**. 현재 Phase 1 및 Phase 2 프로토타입에 이미 `Method Hotspots`와 `REST API Hotspots`를 구하여 Call Graph 상 하위 메소드를 매핑해 내는 기능이 부분적으로 구현되어 있습니다.
*   **구현 방안**:
    - 현재의 API Call Graph 및 Method parsing 구조를 활용하여, 최상위 핫스팟 파일(예: `PetController.java`)의 내부 메소드 수준에서 `LOC × Revisions`를 시각적으로 시각화 및 서브 랭킹으로 표시하는 드릴다운(Drill-down) 리포팅 구조를 완성합니다.

---

## 4. 종합 핫스팟 공식 (Composite Scoring Formula) 제안

네 가지 요소를 최종 결합한 종합 핫스팟 점수(Composite Score) 산출 공식은 다음과 같이 설계될 수 있습니다.

$$\text{Composite Score}(F) = \text{Cognitive Complexity}(F) \times \left( \sum_{c \in \text{Commits}(F)} 2^{-\frac{\text{Age}(c)}{\tau}} \right) \times \left(\frac{1}{\text{Line Coverage}(F) + 0.1}\right)$$

*   $\text{Age}(c)$: 현재 분석 시점으로부터 커밋 $c$가 발생한 경과 시간(단위: 일)
*   $\tau$: 반감기 파라미터 (기본값: 180일)
*   $\text{Line Coverage}(F)$: JaCoCo 리포트로부터 취득한 $[0.0, 1.0]$ 범위의 커버리지 비율

이러한 종합 공식을 `ScoringConfig.Formula.COMPOSITE`로 추가하고 `CompositeScoreCalculator`를 통해 구현한다면, 단순 크기 및 누적 수정 횟수를 넘어 실질적 결함 위험성이 극도로 농축된 코드를 100% 정밀 탐색할 수 있습니다.
