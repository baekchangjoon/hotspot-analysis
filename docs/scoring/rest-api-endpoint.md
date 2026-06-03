# REST API 엔드포인트 단위 (rest-api-endpoint)

> 🌐 **한국어** (현재 문서) · [English](rest-api-endpoint.en.md)

공통 요인·점수 정의는 [스코어링 레퍼런스](README.md)를 먼저 보세요. 이 문서는
**API 엔드포인트 단위**가 콜그래프를 따라 요인을 어떻게 **집계**하는지를 다룹니다.

이 단위는 `analysis.apiAnalysis.enabled: true`일 때만 생성되며, 한 HTTP 엔드포인트가
**컨트롤러 메서드부터 그 호출 그래프 전체**에서 짊어지는 위험을 하나의 점수로
모읍니다. RestAssured 같은 **API 테스트 생성의 우선순위 입력**으로 설계됐습니다.

근거: `HotspotAnalyzer.buildApiAndShared()`, `analysis/CallGraphBuilder.java`,
모델 `analysis/model/ApiHotspot.java`.

## 1) 엔드포인트 식별 — `httpMethod` + `route`

- 컨트롤러: `@RestController` / `@Controller` 클래스의 메서드 중
  `@GetMapping`·`@PostMapping`·`@PutMapping`·`@DeleteMapping`·`@PatchMapping`·`@RequestMapping`
  이 붙은 것.
- **route** = 클래스 레벨 `@RequestMapping` prefix + 메서드 레벨 매핑 경로를 합쳐
  정규화(`//` 정리, 끝 `/` 제거). `JavaSourceParser.resolveApiMappings`.
- **httpMethod** = 애너테이션 종류(`@GetMapping`→GET 등). `@RequestMapping`에
  `method`가 없으면 GET으로 간주.

## 2) 콜그래프 구성

`CallGraphBuilder`가 JavaSymbolSolver로 타입을 해석해, 컨트롤러 메서드를 시작점으로
**DFS**하며 "분석 대상 소스 안에 있는" 메서드 호출만 따라갑니다:

- 인터페이스 호출은 구현체로 이어 붙입니다(인터페이스→impl 매핑).
- 순환은 `visited`로 차단, 도달 메서드는 `LinkedHashSet`으로 중복 제거.
- 외부 라이브러리 호출은 스코프 밖이라 그래프에 포함되지 않습니다.
- **정확도 팁**: 심볼 해석을 위해 `apiAnalysis.classpathDirectories`에 의존성
  jar/클래스 디렉터리를 지정하면 더 많은 호출이 해석됩니다. 미지정 시 표준
  라이브러리(ReflectionTypeSolver)와 소스 루트만으로 해석합니다.

`ApiHotspot.callGraph`에는 도달한 **모든** 피호출 메서드 시그니처가 실립니다(아래
공유 컴포넌트 제외 규칙은 점수 집계에만 영향, 목록 자체에는 영향 없음).

## 3) 요인 집계 — 컨트롤러 + 콜그래프 합산

엔드포인트의 각 요인 = **컨트롤러 메서드 값 + 도달한 피호출 메서드들의 값의 합**.

| 요인 | 집계 방식 |
|---|---|
| **Revisions** | `R(controller) + Σ R(called)` |
| **LOC** | `LOC(controller) + Σ LOC(called)` |
| **Recency Decay** | `D(controller) + Σ D(called)` |
| **Cognitive Complexity** | `CC(controller) + Σ CC(called)` |
| **Coverage** | 컨트롤러+피호출 메서드들의 **라인 가중 커버리지**(JaCoCo 제공 시): `분자 = Σ 터치된 라인`, `분모 = Σ 실행가능 라인`. 비율의 단순 평균이 아님 — 작은 풀커버 메서드가 큰 미테스트 메서드를 상쇄하지 못하게(Simpson 역설 회피). 리포트에 데이터 없는 메서드는 0/0으로 기여 안 함 |
| **Coverage Multiplier** | 위 라인 가중 커버리지로 `1/(cov+0.1)`, 미제공 시 1.0 |

각 메서드 단위 요인(R/D/CC/coverage)은 [메서드 단위](method.md)와 동일하게
구해진 값을 재사용합니다.

> **검산 파일:** `output.coverageBreakdown: true`면 이 집계의 계산 근거 —
> 메서드별 covered/실행가능 라인, 무데이터·SEPARATE 제외 표시 — 가
> `coverage_breakdown.yml`로 함께 출력됩니다(파일 단위 counts 포함).

### 공유 컴포넌트 제외 규칙 (`sharedComponentMode`)

2개 이상 엔드포인트가 공통으로 호출하는 메서드는 [공유 컴포넌트](shared-component.md)
입니다. 모드에 따라 엔드포인트 합산에서의 취급이 달라집니다:

| 모드 | 엔드포인트 합산에 공유 메서드 포함? | 공유 컴포넌트 별도 리포트? |
|---|:---:|:---:|
| `CUMULATIVE` | 포함(매 엔드포인트마다 중복 합산) | 아니오 |
| `SEPARATE` | **제외**(공유분은 한 번만, 별도 집계) | 예 |
| `BOTH` (기본) | 포함 | 예 |

`SEPARATE`는 "엔드포인트 고유 위험"을 보고 싶을 때, `CUMULATIVE`는 "이 엔드포인트를
건드리면 닿는 총 위험"을 보고 싶을 때 씁니다. `BOTH`는 둘 다 출력합니다.

## 4) 점수 계산

```
Simple    = Revisions_agg × LOC_agg
Composite = CC_agg × RecencyDecay_agg × CoverageMultiplier      # 기본
Composite = CC_agg × RecencyDecay_agg                           # excludeCoverage=true
```

정렬: Composite 내림차순, 동점은 `route` → `httpMethod`.

## 5) 리포트 열 / 스키마

CSV(`api_hotspots.csv` 또는 통합 시 별도 섹션), 기본:

```
simple_rank, composite_rank, http_method, route, fqcn, method, parameters,
loc, revisions, simple_score, recency_decay, cognitive_complexity,
coverage_multiplier, composite_score
```

YAML(`api_report.yml` 또는 `hotspots.yml`의 `apiHotspots`)은 위 필드에 더해
`callGraph`(도달 메서드 시그니처 목록)를 포함합니다. 출력 위치는
`output.apiLayout`(`COMBINED`|`STANDALONE`|`BOTH`)로 제어합니다.

## 6) 워크드 예시

`POST /api/orders` → `OrderController#create(OrderRequest)` 가
`OrderService#place → InventoryService#reserve, PricingService#quote` 를 호출.

```
# 메서드별 (예시값): cov = 터치된 라인 / 실행가능 라인
create:  R=2 LOC=18 D=1.8 CC=3   cov=5/10
place:   R=5 LOC=60 D=4.2 CC=14  cov=8/40
reserve: R=3 LOC=40 D=2.1 CC=9   cov=0/25
quote:   R=1 LOC=25 D=0.6 CC=5   cov=12/15   # quote가 2개 API에서 쓰이면 공유 컴포넌트

# BOTH/CUMULATIVE 집계 (quote 포함)
R_agg   = 2+5+3+1 = 11
LOC_agg = 18+60+40+25 = 143
D_agg   = 1.8+4.2+2.1+0.6 = 8.7
CC_agg  = 3+14+9+5 = 31
cov(라인 가중) = (5+8+0+12)/(10+40+25+15) = 25/90 ≈ 0.278
   # 비율 평균이면 (0.50+0.20+0.00+0.80)/4 = 0.375 → 큰 미테스트 메서드가 가려짐
M       = 1/(0.278+0.10) ≈ 2.646
Simple    = 11 × 143 = 1573
Composite = 31 × 8.7 × 2.646 ≈ 714

# SEPARATE 집계 (공유 컴포넌트 quote 제외)
CC_agg' = 3+14+9 = 26 ;  D_agg' = 1.8+4.2+2.1 = 8.1
cov'(라인 가중) = (5+8+0)/(10+40+25) = 13/75 ≈ 0.173 → M' = 1/(0.273) ≈ 3.66
Composite' = 26 × 8.1 × 3.66 ≈ 771   # quote의 높은 커버리지가 빠져 위험이 더 도드라짐
```

## RestAssured 테스트 생성 입력으로 쓰기

`api_report.yml`을 읽어 `compositeRank` 순으로 소비하면, 엔드포인트 하나당:

- `httpMethod` + `route` → 요청을 만들 정보(RestAssured `given().when().<method>(route)`).
- `parameters` / 컨트롤러 시그니처 → 요청 바디·파라미터 형태 단서.
- `callGraph` + 메서드 커버리지 → **어느 하위 로직이 미보호인지** → 어떤 시나리오
  (분기·예외 경로)를 우선 검증할지.
- `compositeRank` → **테스트를 붙일 순서**.

즉 "가장 위험한 엔드포인트부터, 가장 미보호인 경로를 겨냥해" RestAssured 테스트를
생성하는 결정론적 우선순위 큐가 됩니다.
