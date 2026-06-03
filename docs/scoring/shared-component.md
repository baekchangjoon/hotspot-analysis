# 공유 컴포넌트 단위 (shared-component)

공통 요인·점수 정의는 [스코어링 레퍼런스](README.md)를, 엔드포인트 집계 맥락은
[REST API 엔드포인트](rest-api-endpoint.md)를 먼저 보세요.

**공유 컴포넌트**는 **2개 이상의 서로 다른 API 엔드포인트**의 콜그래프에서 공통으로
호출되는 메서드입니다(전형적으로 서비스/리포지토리 메서드). 여러 엔드포인트가
의존하므로, 여기서 깨지면 **장애 반경이 넓습니다**.

근거: `HotspotAnalyzer.buildApiAndShared()` (공유 판정·집계), 모델
`analysis/model/SharedComponentHotspot.java`.

## 판정

콜그래프 집계 중, 각 피호출 메서드가 **몇 개의 API에서 호출되는지**를 셉니다
(`callingApisMap`). 호출 API가 **2개 이상**이면 공유 컴포넌트입니다.

```
sharedComponent(m) ⇔ | { api : m ∈ api.callGraph } | ≥ 2
```

공유 컴포넌트 목록은 `sharedComponentMode`가 `SEPARATE` 또는 `BOTH`일 때만
생성됩니다(`CUMULATIVE`에서는 별도 목록 없이 각 엔드포인트 점수에 흡수). 모드별
엔드포인트 합산 취급은 [엔드포인트 문서의 표](rest-api-endpoint.md#공유-컴포넌트-제외-규칙-sharedcomponentmode)를
보세요.

## 요인 산출

공유 컴포넌트는 **그 메서드 자체의** 요인을 씁니다(엔드포인트처럼 합산하지 않음).
값은 [메서드 단위](method.md)와 동일하게 구해집니다:

| 요인 | 산출 |
|---|---|
| **Revisions** | 그 메서드를 건드린 커밋 수(hunk 겹침) |
| **LOC** | `endLine - startLine + 1` |
| **Recency Decay** | 그 메서드의 `Σ exp(-λ·Δt)` |
| **Cognitive Complexity** | 그 메서드 본문 CC |
| **Coverage / Multiplier** | 메서드 커버리지로 `1/(cov+0.1)`, 미제공 시 1.0 |
| **callingApis** | 이 메서드를 호출하는 엔드포인트 목록(`"GET /a"`, `"POST /b"` …) |

## 점수 계산

```
Simple    = Revisions × LOC
Composite = CC × RecencyDecay × CoverageMultiplier      # 기본
Composite = CC × RecencyDecay                           # excludeCoverage=true
```

정렬: Composite 내림차순, 동점은 정식 시그니처.

## 리포트 열

CSV(`shared_components.csv` 또는 통합 섹션), 기본:

```
simple_rank, composite_rank, fqcn, method, parameters,
loc, revisions, simple_score, recency_decay, cognitive_complexity,
coverage_multiplier, composite_score, calling_apis
```

`excludeCoverage=true`면 `composite_score, calling_apis, line_coverage` 순.
`calling_apis`는 `;`로 join. YAML/MD/HTML도 같은 필드를 담습니다.

## 워크드 예시

`PricingService#quote(Sku, Customer)` 가 `GET /api/price`, `POST /api/orders`,
`POST /api/quotes` 세 엔드포인트에서 호출됨. CC=5, LOC=25, 최근 변경으로 D=1.4,
커버리지=0.80.

```
callingApis = ["GET /api/price", "POST /api/orders", "POST /api/quotes"]  # 3개 → 공유
Simple    = Revisions × LOC = 1 × 25 = 25
M         = 1/(0.80+0.10) = 1.111
Composite = 5 × 1.4 × 1.111 ≈ 7.8
```

해석: 점수 자체는 낮아도 **3개 API가 의존**한다는 신호(`callingApis`)가 핵심입니다.
공유 컴포넌트는 단위 테스트로 한 번 단단히 막으면 여러 엔드포인트를 동시에
보호하므로, **테스트 투자 대비 보호 범위가 가장 큰** 후보입니다. 반대로 공유
컴포넌트의 커버리지가 낮으면(M↑) 우선순위를 끌어올려야 합니다.
