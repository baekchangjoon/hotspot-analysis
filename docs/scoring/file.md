# 파일 단위 (file)

공통 요인·점수 정의는 [스코어링 레퍼런스](README.md)를 먼저 보세요. 이 문서는
**파일 단위**가 그 요인들을 어떻게 구하고 집계하는지만 다룹니다.

근거: `HotspotAnalyzer.buildFileHotspots()`, 모델 `analysis/model/FileHotspot.java`.

## 요인 산출

| 요인 | 파일 단위 산출 방법 |
|---|---|
| **Revisions** | 윈도우 커밋 중 그 파일 경로를 건드린 커밋 수 (커밋당 1회). `calculateFileRevisions` |
| **LOC** | HEAD 시점 파일의 줄 수(주석/공백 제거 없음). `LocCalculator.countLines` |
| **Recency Decay** | 그 파일을 건드린 커밋들의 `Σ exp(-λ·Δt)`. `calculateFileDecayedRevisions` |
| **Cognitive Complexity** | 파일 안 **모든 메서드 CC의 합** (`Σ m.cognitiveComplexity()`) |
| **Coverage** | 파일 전체 라인 기준 `covered/instrumented` (JaCoCo 제공 시). `getFileCoverage` |
| **Coverage Multiplier** | `1/(coverage+0.1)`, 미제공 시 1.0 |

## 점수 계산

```
Simple    = Revisions × LOC
Composite = CC × RecencyDecay × CoverageMultiplier      # 기본
Composite = CC × RecencyDecay                           # excludeCoverage=true
```

## 리포트 열 (CSV `file_hotspots.csv`)

기본(커버리지 포함):

```
simple_rank, composite_rank, path, loc, revisions, simple_score,
recency_decay, cognitive_complexity, coverage_multiplier, composite_score
```

`excludeCoverage=true`면 마지막 두 열이 `composite_score, line_coverage`로 바뀝니다.
YAML/MD/HTML은 같은 값을 `path` 키로 담습니다.

## 워크드 예시

`OrderService.java`: 윈도우 내 8개 커밋이 건드림(LOC=420), 그중 4개가 최근 90일
이내. 파일 내 메서드 CC 합 = 47. JaCoCo line coverage = 0.30.

```
Revisions = 8
LOC       = 420
Simple    = 8 × 420 = 3360
D ≈ (최근 4커밋의 weight 합) + (오래된 4커밋의 작은 weight 합) ≈ 5.1   # 예시값
M         = 1/(0.30+0.10) = 2.5
Composite = 47 × 5.1 × 2.5 ≈ 599
```

해석: Simple은 "크고 자주 바뀜"을, Composite는 "복잡 × 최근성 × 미보호"를 함께
반영해 더 위험한 파일을 위로 올립니다. 파일 단위는 빠른 개요용이며, 정확히
어디를 테스트할지는 [메서드](method.md)·[API 엔드포인트](rest-api-endpoint.md)
단위가 더 유용합니다.
