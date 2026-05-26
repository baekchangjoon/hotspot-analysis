# Hotspot Analysis 고도화 상세 명세 및 계산 공식

> **v0.2 note.** Starting v0.2, all four factors described below
> (Recency Decay, Cognitive Complexity, Coverage Multiplier, X-Ray) are
> always computed — there is no longer a `formula: simple|composite`
> toggle. See `docs/advanced-techniques-verification.md` for the
> validation results.

이 문서는 `hotspot-analysis` 프로젝트에 추가로 도입할 고도화 핵심 기법들(A-2, B-2, C-4, C-5)의 동작 원리, 상세 수학적 계산 공식 및 컴포넌트 아키텍처 설계를 설명합니다.

---

## 1. B-2. Recency Decay (변경 시간 감쇄 가중치)

### 1.1. 개념 및 동작 원리
단순히 누적 커밋 횟수를 계산할 경우, 수년 전에 빈번히 바뀌었으나 현재는 매우 안정된 코드(Cold Legacy)가 높은 점수를 차지해 분석을 왜곡합니다. 최근에 활발히 수정되고 있는 코드에 더 높은 가중치를 주어 현재 진행형인 불안정 구간을 정밀 지목합니다.

### 1.2. 수학적 계산 공식
각 커밋 $c$가 발생한 시점과 분석 기준 시점(Until) 간의 시간 격차 $\Delta t_c$ (단위: 일)를 계산하고, 지수 감쇄 함수(Exponential Decay)를 활용하여 **유효 리비전 수(Effective Revisions)**를 산출합니다.

$$\text{Effective Revisions}(F) = \sum_{c \in \text{Commits}(F)} e^{-\lambda \Delta t_c}$$

*   $\Delta t_c = \text{Date}_{\text{until}} - \text{Date}_{\text{commit}}(c)$ (단위: 일)
*   $\lambda = \frac{\ln(2)}{t_{half}}$ (감쇄 상수, $t_{half}$는 스코어가 절반으로 떨어지는 반감기 기본값: 90일 또는 180일)

만약 반감기 $t_{half} = 90$일 때:
- 오늘 발생한 커밋은 $e^0 = 1.0$ 점
- 90일 전 발생한 커밋은 $e^{-\frac{\ln(2)}{90} \times 90} = 0.5$ 점
- 180일 전 발생한 커밋은 $e^{-\frac{\ln(2)}{90} \times 180} = 0.25$ 점

---

## 2. A-2. Cognitive Complexity (인지 복잡도)

### 2.1. 개념 및 동작 원리
단순히 코드의 총 라인 수(LOC)는 주석이나 괄호 포맷 등으로 부풀려져 실제 복잡도를 온전히 대변하지 못합니다. 인지 복잡도(Cognitive Complexity)는 코드의 제어 흐름(분기, 반복, 예외 처리) 및 이들의 중첩 깊이(Nesting Level)에 대해 가중치를 누적하여 사람이 읽었을 때 두뇌 연산 부담을 주는 실제 수준을 정량화합니다.

### 2.2. 계산 공식 및 규칙 (SonarQube 스펙 기준)
1.  **가산점 1점**: 제어 구조의 출현 시 (`if`, `for`, `while`, `catch`, `switch`, `&&`, `||` 등)
2.  **중첩 가산점 (Nesting Level)**: 제어 구조가 중첩될 때 깊이당 추가 가중치를 부여
    *   예: `if` 내부에 `for`가 중첩되면 `for` 문은 기본 가산점 1점 + 중첩 레벨 1점 = 총 2점을 획득합니다.
3.  **예외 규칙**: 단순 메소드 선언, 단선 분기, 단순 어노테이션 등은 가산되지 않습니다.

$$\text{Cognitive Complexity}(M) = \sum_{n \in \text{Nodes}(M)} (\text{Base}(n) + \text{Nesting}(n))$$

---

## 3. C-4. Coverage Gap (테스트 커버리지 결합)

### 3.1. 개념 및 동작 원리
아무리 복잡하고 자주 바뀌는 핫스팟이더라도, 견고한 통합/단위 테스트 코드에 의해 보호되고 있다면 상대적으로 프로덕션 장애로 연결될 위험도는 낮습니다. 테스트 보호 장치가 없는(Coverage Gap) 위험 구역에 경보 가중치를 대폭 늘려 리팩토링의 우선순위를 재조정합니다.

### 3.2. 수학적 계산 공식
외부 JaCoCo XML 리포트로부터 각 소스 파일의 라인 커버리지 비율($Cov_F \in [0.0, 1.0]$)을 읽어들입니까. 전체 핫스팟 위험도 계산 시 커버리지의 역수(Inverse Coverage)를 가중 비율로 곱해줍니다.

$$\text{Coverage Multiplier}(F) = \frac{1}{\text{Line Coverage}(F) + \epsilon}$$

*   $\epsilon$: 분모가 0이 되는 것을 방지하기 위한 최소 보정 상수 (기본값: $0.1$)
*   커버리지가 $100\%$ ($1.0$)인 경우: 승수는 $\frac{1}{1.0 + 0.1} \approx 0.909$
*   커버리지가 $0\%$ ($0.0$)인 경우: 승수는 $\frac{1}{0.0 + 0.1} = 10.0$ (테스트가 없는 복잡 구간의 위험도가 최대 10배 증폭됨)

---

## 4. C-5. X-Ray (메소드 단위 정밀 조사 및 시각화)

### 4.1. 개념 및 동작 원리
거대 파일(God Class)이 핫스팟으로 탐지되었을 때, 전체 파일을 통째로 리팩토링하는 것은 큰 공수가 들어 비현실적입니다. X-Ray 분석은 파일 내부에서 실제 빈번히 수정되고 가장 복잡한 핵심 메소드를 격리 탐색하여 점수 지분율을 서브 리포트로 시각화합니다.

### 4.2. 점수 매핑 구조
*   각 파일의 핫스팟 하위에 메소드 리스트를 트리 구조로 확장 출력합니다.
*   해당 파일이 포함하고 있는 하위 메소드들의 개별 `Score(Method) = Revisions(Method) × Cognitive Complexity(Method)`를 계산하여 파일 내 점수 비중 백분율을 도출합니다.
    $$\text{Method Score Share}(M) = \frac{\text{Score}(M)}{\sum_{m \in \text{Methods}(F)} \text{Score}(m)} \times 100\%$$

---

## 5. 종합 핫스팟 공식 (Composite Hotspot Score)

상기 네 가지 인자를 결합하여 도출하는 최종 복합 핫스팟 스코어(Composite Score) 산출 공식은 다음과 같습니다.

$$\text{Composite Score}(F) = \text{Cognitive Complexity}(F) \times \text{Effective Revisions}(F) \times \text{Coverage Multiplier}(F)$$

$$\text{Composite Score}(F) = \text{Cognitive Complexity}(F) \times \left( \sum_{c \in \text{Commits}(F)} e^{-\frac{\ln(2)}{t_{half}} \Delta t_c} \right) \times \left( \frac{1}{\text{Line Coverage}(F) + 0.1} \right)$$
