# 스코어링 레퍼런스

> 🌐 **한국어** (현재 문서) · [English](README.en.md)

hotspot-analysis가 **네 가지 입력 요인**(Revisions, Recency Decay, Cognitive
Complexity, Coverage)을 어떻게 측정하고, 이를 어떻게 **두 가지 점수**(Simple,
Composite)로 계산하는지를 단위(granularity)별로 설명합니다.

이 문서(인덱스)는 모든 단위에 **공통인 정의와 공식**을 한곳에 모읍니다. 각 단위가
이 요인들을 어떻게 **집계**하고 어떤 **리포트 열**로 내보내는지는 단위별 문서를
보세요:

- [파일 (file)](file.md)
- [메서드 (method)](method.md)
- [REST API 엔드포인트 (rest-api-endpoint)](rest-api-endpoint.md)
- [공유 컴포넌트 (shared-component)](shared-component.md)

모든 공식은 결정론적입니다. 같은 입력(같은 커밋 범위·소스·JaCoCo 리포트)이면 항상
같은 점수와 같은 정렬이 나옵니다. 난수도, 시간 의존(아래 `until` 기준점 제외)도
없습니다.

---

## 분석 파이프라인 (한눈에)

`HotspotAnalyzer.analyze()` (`src/main/java/.../analysis/HotspotAnalyzer.java`):

1. `window` 안의 커밋을 VCS 프로바이더로 로드.
2. `scope.include/exclude`로 Java 파일 수집 → JavaParser로 메서드 추출.
3. 파일/메서드별 **Revisions**, **LOC**, **Recency Decay**, **Cognitive
   Complexity**, (선택)**Coverage** 계산.
4. `HotspotScoreCalculator`로 **Simple**·**Composite** 점수 합성.
5. `apiAnalysis.enabled`면 콜그래프를 만들어 **API 엔드포인트**·**공유 컴포넌트**
   단위를 추가 집계.
6. Composite 내림차순 정렬 → `output.topN` 적용 → 리포트 출력.

---

## 입력 요인 1 — Revisions (R)

윈도우 내에서 해당 아티팩트를 **건드린 커밋 수**. 한 커밋이 같은 대상을 여러 번
바꿔도 **1로** 셉니다(= `git log --oneline -- <path> | wc -l` 의미).

근거: `RevisionsCalculator` (`analysis/RevisionsCalculator.java`).

- **파일**: 그 커밋의 `FileChange` 경로 집합에 파일이 있으면 +1.
- **메서드**: diff hunk의 신규 파일 라인 범위가 메서드의 `[startLine, endLine]`과
  **겹치면** +1 (커밋당 최대 1회). hunk 정보가 없으면(예: GitHub 프로바이더) 파일의
  모든 메서드에 +1로 폴백.

```
R(artifact) = | { c ∈ window : c가 artifact를 건드림 } |
```

## 입력 요인 2 — Recency Decay (D)

Revisions와 같은 "건드린 커밋"을 세되, **최근 커밋에 더 큰 가중치**를 줍니다. 각
커밋의 가중치는 기준 시점 `until`로부터의 경과 일수에 대한 지수 감쇠입니다.

근거: `RevisionsCalculator.calculate*DecayedRevisions`.

```
λ      = ln(2) / halfLifeDays          # scoring.decayHalfLifeDays, 기본 90
Δt(c)  = max(0, days_between(c.committedAt, until))
weight(c) = exp(-λ · Δt(c))
D(artifact) = Σ_{c가 artifact를 건드림} weight(c)
```

- `until` = `window.until`이 있으면 그 날의 끝, 없으면 분석 실행 시각(now).
- 반감기만큼 오래된 커밋의 가중치는 0.5, 두 반감기 전이면 0.25 … 입니다.
- 메서드/파일 귀속 규칙(커밋당 1회, hunk 겹침)은 Revisions와 동일.

> 직관: `halfLifeDays=90`이면 "최근 90일 안의 변경 1회"가 "180일 전 변경 1회"의
> 두 배 무게입니다. 오래 안 바뀐 레거시는 D가 작아지고, 지금 활발히 바뀌는 곳은
> D가 커집니다.

## 입력 요인 3 — Cognitive Complexity (CC)

SonarQube의 Cognitive Complexity를 메서드 본문 AST를 순회하며 계산합니다. "읽고
이해하기 얼마나 어려운가"의 근사치입니다(단순 분기 수인 Cyclomatic과 다름).

근거: `CognitiveComplexityCalculator` (`parser/CognitiveComplexityCalculator.java`).

가산 규칙:

| 구문 | 가산 | 중첩 증가 |
|---|---|---|
| `if` / `for` / `for-each` / `while` / `do` / `catch` | `1 + 현재 중첩깊이` | +1 |
| `switch` / 삼항(`? :`) | `1` | +1 |
| 레이블 있는 `break` / `continue` | `1` | — |
| 이항 `&&` / `\|\|` | `1` | — |

- 중첩이 깊을수록 같은 구문이라도 더 큰 패널티(중첩 if는 `1+깊이`).
- 본문이 없는(추상/인터페이스) 메서드는 0.
- **파일 CC** = 그 파일에 속한 모든 메서드 CC의 **합**.

## 입력 요인 4 — Coverage 와 Coverage Multiplier (M)

JaCoCo XML(`analysis.jacocoReportPath`)이 주어졌을 때만 활성화됩니다. 라인의
`ci`(covered instructions) > 0 이면 그 라인을 "커버됨"으로 봅니다.

근거: `JacocoReportParser`, `HotspotScoreCalculator.multiplier`.

```
coverage(file)   = (커버된 라인 수) / (계측된 라인 수)            # 파일 전체
coverage(method) = (메서드 범위 내 커버된 라인) / (범위 내 계측된 라인)
M = 1 / (coverage + 0.1)                                          # 커버리지 배수
```

- 범위: coverage ∈ [0,1] → **M ∈ (0.0→10, 1.0→0.909)**. 커버리지가 낮을수록 M이
  커져 Composite를 끌어올립니다(테스트로 보호되지 않은 위험 구역에 경보 가중).
- **JaCoCo 미제공**: M = 1.0 (커버리지 패널티 없음), `lineCoverage`는 `null`.
- **JaCoCo는 제공됐지만 해당 파일/메서드 데이터가 없음**: coverage = 0.0 으로 간주
  → M = `1/0.1 = 10`(최대 패널티). 측정 대상에 테스트 리포트가 없으면 "미보호"로
  취급된다는 뜻이니, JaCoCo 경로는 분석 대상과 같은 빌드의 리포트를 주세요.

### `excludeCoverage` 스위치

`scoring.excludeCoverage: true`면 커버리지를 **점수에서 제외**하고 관측용으로만
보여줍니다:

- Composite = `CC × D` (M을 곱하지 않음).
- 리포트 맨 오른쪽 열이 `coverage_multiplier` 대신 **원시 `line_coverage`(%)**.

---

## 파생 점수

근거: `HotspotScoreCalculator` (`analysis/HotspotScoreCalculator.java`).

### Simple Score — Adam Tornhill 원형

```
Simple = Revisions × LOC
```

- **LOC**: 파일 = HEAD 시점 파일의 줄 수(주석/공백 제거 없음, `LocCalculator`).
  메서드 = `endLine - startLine + 1`(선언부~닫는 중괄호 포함).
- 변경이 잦고 큰 파일을 빠르게 짚는 1차 신호.

### Composite Score — 이 프로젝트의 핵심 신호

```
Composite = Cognitive Complexity × Recency Decay × Coverage Multiplier
          = CC × D × M
# excludeCoverage=true 이면:
Composite = CC × D
```

- "**복잡하고**(CC) × **지금 활발히 바뀌며**(D) × **테스트로 안 막힌**(M)" 곳이
  동시에 높을 때 최상위로 올라옵니다 — 버그가 모이는 교집합.

---

## 정렬과 결정론

모든 단위는 **Composite 내림차순**으로 정렬하고, 동점은 안정적인 키로 깹니다:

| 단위 | 동점 타이브레이크 |
|---|---|
| 파일 | `path` |
| 메서드 | 정식 시그니처(`fqcn#method(params)`) |
| API 엔드포인트 | `route` → `httpMethod` |
| 공유 컴포넌트 | 정식 시그니처 |

`output.topN > 0`이면 정렬 후 상위 N개만 남깁니다(0이면 전부).

리포트는 `simpleRank`(Simple 기준 순위)와 `compositeRank`(Composite 기준 순위, =
출력 행 순서)를 **둘 다** 실어, 두 신호가 어디서 갈리는지 한눈에 보이게 합니다.
