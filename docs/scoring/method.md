# 메서드 단위 (method)

> 🌐 **한국어** (현재 문서) · [English](method.en.md)

공통 요인·점수 정의는 [스코어링 레퍼런스](README.md)를 먼저 보세요. 이 문서는
**메서드 단위**가 그 요인들을 어떻게 구하고 집계하는지만 다룹니다.

근거: `HotspotAnalyzer.buildMethodHotspots()`, 모델
`analysis/model/MethodHotspot.java`, 추출 `parser/JavaSourceParser` →
`parser/model/MethodInfo.java`.

메서드는 정식 시그니처 `MethodSignature(fqcn, methodName, parameterTypes)`로
식별됩니다. `startLine`/`endLine`은 선언부~닫는 중괄호까지의 1-based 포함 범위입니다.

## 요인 산출

| 요인 | 메서드 단위 산출 방법 |
|---|---|
| **Revisions** | 커밋의 diff hunk(신규 파일 라인 범위)가 메서드 `[startLine,endLine]`과 **겹치면** +1 (커밋당 1회). hunk가 없으면 파일 내 전 메서드에 +1 폴백. `calculateMethodRevisions` |
| **LOC** | `endLine - startLine + 1`. `MethodInfo.lineCount()` |
| **Recency Decay** | 위 "겹친 커밋"들의 `Σ exp(-λ·Δt)`. `calculateMethodDecayedRevisions` |
| **Cognitive Complexity** | 그 메서드 본문의 CC(합산 아님). `CognitiveComplexityCalculator.calculate` |
| **Coverage** | 메서드 라인 범위 내 `covered/instrumented`. `getMethodCoverage(path,start,end)` |
| **Coverage Multiplier** | `1/(coverage+0.1)`, 미제공 시 1.0 |

> **hunk 겹침이 핵심**: 같은 파일이 바뀌어도, 실제로 그 줄 범위가 바뀐 메서드만
> Revisions/Decay를 얻습니다. 그래서 파일 전체가 아니라 "자주 손대는 그 메서드"를
> 정확히 짚을 수 있습니다. (단 hunk를 제공하는 `local-git` 프로바이더 기준. hunk가
> 없으면 파일 단위로 근사됩니다.)

## 점수 계산

```
Simple    = Revisions × LOC
Composite = CC × RecencyDecay × CoverageMultiplier      # 기본
Composite = CC × RecencyDecay                           # excludeCoverage=true
```

## 리포트 열 (CSV `method_hotspots.csv` — 14열)

기본(커버리지 포함):

```
simple_rank, composite_rank, fqcn, method, parameters, file,
start_line, end_line, loc, revisions, simple_score, recency_decay,
cognitive_complexity, coverage_multiplier, composite_score
```

`excludeCoverage=true`면 마지막 두 열이 `composite_score, line_coverage`.
`parameters`는 `;`로 join됩니다.

## 워크드 예시

`OrderService#applyDiscount(Order, Coupon)` : `startLine=88, endLine=140`,
윈도우 내 3개 커밋이 이 범위와 겹침(전부 최근), CC=12, JaCoCo 메서드 커버리지=0.0
(이 메서드에 테스트 없음).

```
LOC       = 140 - 88 + 1 = 53
Revisions = 3
Simple    = 3 × 53 = 159
D ≈ 2.7                       # 최근 3커밋 weight 합 (예시값)
M         = 1/(0.0+0.1) = 10  # 미보호 → 최대 패널티
Composite = 12 × 2.7 × 10 = 324
```

해석: Simple로는 평범하지만, "최근에 자주 바뀌고(D) 복잡하며(CC) 테스트가 전혀
없는(M=10)" 전형적 위험 메서드라 Composite가 크게 뜁니다. 이런 메서드가 바로 단위
테스트를 먼저 붙일 후보입니다.
