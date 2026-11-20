# HTML 리포트 데이터 블록

> 🌐 **한국어** (현재 문서) · [English](html-report-data.en.md)

`hotspots.html`의 **Hotspot Map** 탭은 고정 템플릿(`src/main/resources/report/`)과
JSON 한 덩어리로 만들어집니다. 그 JSON은 리포트 안에 그대로 들어 있습니다.

```html
<script type="application/json" id="hotspot-map-data">{"schemaVersion":1,"meta":{…},"files":[…],"methods":[…]}</script>
```

여러 리포트를 모아 대시보드를 만들 때는 표를 긁지 말고 이 블록을 읽으세요.
브라우저에서는 `JSON.parse(document.getElementById("hotspot-map-data").textContent)`
입니다. `<`, `>`, `&`는 JSON 유니코드 이스케이프(`<` 등)로 쓰여 있으며, JSON
파서를 거치면 원래 문자열로 돌아옵니다.

필드의 이름을 바꾸거나 없애거나 의미를 바꾸면 `schemaVersion`을 올립니다. 필드
추가는 버전을 올리지 않으니, 모르는 필드는 무시하세요.

## `meta`

| 필드 | 타입 | 설명 |
|---|---|---|
| `label` | string | 분석 대상 디렉터리 이름 (절대 경로는 넣지 않음) |
| `analyzedAt` | string | 분석 시각 (ISO-8601, UTC) |
| `commits` | int | 윈도우 안의 커밋 수 |
| `totalFiles` / `totalMethods` | int | `output.topN` 적용 **전** 전체 개수 |
| `halfLifeDays` | int | `scoring.decayHalfLifeDays` |
| `excludeCoverage` | bool | `scoring.excludeCoverage` |
| `blobBase` | string \| null | 소스 링크 접두사. `blobBase + path + "#L10-L20"`. origin이 GitHub/GitLab이 아니면 `null` |
| `revision` | string \| null | 분석한 커밋의 전체 SHA |
| `totalFileScore` | number | **모든** 파일의 Composite 합계 (`topN` 적용 전) |
| `coveredLines` / `executableLines` | int | 모든 파일의 JaCoCo 라인 수 합계. 리포트가 없으면 0 |

## `files[]` — Composite 내림차순

| 필드 | 타입 | 설명 |
|---|---|---|
| `path` | string | 저장소 루트 기준 경로 |
| `rank` | int | Composite 순위 (1부터) |
| `loc`, `rev` | int | 줄 수, 윈도우 안 커밋 수 |
| `decay` | number | Recency Decay |
| `cc` | number | Cognitive Complexity (메서드 합) |
| `covMul` | number | Coverage Multiplier |
| `cov` | number \| null | 라인 커버리지 `[0,1]`. **`null` = 알 수 없음**, `0` = 한 줄도 실행 안 됨 |
| `covered`, `exec` | int \| null | 실행된 줄 / 실행 가능한 줄. `cov`가 `null`이면 `null` |
| `score` | number | Composite Score |

## `methods[]` — Composite 내림차순

`fqcn`, `cls`(단순 클래스 이름), `name`, `params[]`, `file`, `start`, `end`와,
파일과 같은 의미의 `rank`, `loc`, `rev`, `decay`, `cc`, `covMul`, `cov`, `score`.

## 결정성

같은 분석 결과는 항상 같은 바이트의 HTML이 됩니다(난수 없음, 지도의 배치와
흔들림 위상까지 데이터에서 계산). 실행마다 달라지는 것은 분석 쪽 입력 두 가지뿐
입니다: `analyzedAt`, 그리고 `window.until`을 지정하지 않았을 때 "지금"을 기준으로
계산되는 Recency Decay. 재현 가능한 리포트가 필요하면 `window.until`을 고정하세요.
