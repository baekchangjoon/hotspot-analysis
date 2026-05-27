# API Hotspot 분석, 쉽게 이해하기

> **누구를 위한 문서?** Java 백엔드 한두 해 다뤄본 분 / 핫스팟 분석 도구를 처음 접하는 주니어 엔지니어
> **읽는 데 걸리는 시간:** 10분
> **선수 지식:** Spring 컨트롤러(`@RestController`, `@GetMapping`)가 무엇인지 정도

---

## 0. TL;DR — 한 문장 요약

> **API Hotspot 분석은 "어느 REST 엔드포인트가 깨질 가능성이 가장 높은가?"를 알려준다.**
> 점수는 그 엔드포인트가 실제로 실행하는 **모든 코드 경로**(컨트롤러 → 서비스 → 리포지토리 …)를 합쳐서 계산된다.

---

## 1. 왜 File/Method 점수만으로는 부족한가?

먼저 익숙한 두 가지부터 짚고 가자.

### File Hotspot

- 단위: **파일 하나**
- 직관: "`OrderService.java`가 작년에 50번 바뀌었고 800줄짜리야 → 다음 버그가 여기서 터질 확률이 높지"
- 한 줄로: *어느 파일에 테스트를 더 써야 하나?*

### Method Hotspot

- 단위: **메서드 하나**
- 직관: 파일 안에서 한 단계 더 줌인. "`OrderService.process()` 메서드가 그 파일 변경의 80%를 차지하네"
- 한 줄로: *그 파일 안에서 정확히 어느 메서드가 문제인가?*

### 그런데 — 운영팀이 보는 단위는 다르다

장애 알림은 보통 이런 식으로 온다:

> 🚨 *Slack: "**`POST /api/orders` 에러율 4% 급등**"*

운영팀은 `OrderService.java`의 코드 변경 횟수를 모른다. 그들은 **REST 엔드포인트 단위로 세상을 본다**.
그래서 분석 단위 자체를 **"API 엔드포인트"** 로 한 번 올린 게 API Hotspot이다.

| 단위 | 식별자 | 사용자 |
|---|---|---|
| File | `src/main/java/.../OrderService.java` | 코드 리뷰어, 리팩토링 담당 |
| Method | `OrderService.process(OrderReq)` | TDD 작성자 |
| **API** | **`POST /api/orders`** | **운영팀, QA, PM** |

---

## 2. "API 하나의 코드"란 정확히 무엇인가?

이게 핵심이다. **API 하나의 "코드"는 메서드 하나가 아니라 트리(tree)다.**

이런 컨트롤러를 상상해보자.

```java
@RestController
class OrderController {
    private final OrderService orderService;
    private final NotificationService notifications;

    @PostMapping("/api/orders")              //  ← API 엔트리포인트
    public Order createOrder(OrderRequest req) {
        validate(req);                        //  ← 같은 클래스의 private
        Order saved = orderService.create(req); // ← 서비스 호출
        notifications.send(saved);            //  ← 다른 서비스 호출
        return saved;
    }

    private void validate(OrderRequest req) {
        if (req.amount() < 0) throw new BadRequest();
    }
}

class OrderService {
    private final OrderRepository repo;
    private final PriceCalculator pricer;

    Order create(OrderRequest req) {
        BigDecimal price = pricer.compute(req); //  ← 또 호출
        return repo.save(new Order(req, price));//  ← 또 호출
    }
}
```

`POST /api/orders`를 한 번 호출하면 다음 메서드들이 차례로 실행된다:

```
POST /api/orders
└─ OrderController.createOrder         ← 엔트리
   ├─ OrderController.validate
   ├─ OrderService.create
   │  ├─ PriceCalculator.compute
   │  └─ OrderRepository.save
   └─ NotificationService.send
```

**즉, `POST /api/orders`의 "코드 면적" = 위 6개 메서드 전부.**
이 중 어떤 메서드가 바뀌어도 그 API는 영향을 받는다. 그러니 위험도도 **이 6개 메서드의 위험도를 다 합쳐서** 계산하는 게 자연스럽다.

이 "엔트리에서 도달 가능한 모든 메서드의 모음"을 **호출 그래프(call graph)** 라고 부른다.

---

## 3. 그래서 도구는 무엇을 하나? (단계별)

### Step 1 — 엔드포인트 찾기

소스 코드를 AST로 파싱해서 Spring 어노테이션(`@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`, `@RequestMapping`)이 붙은 메서드를 모두 수집한다.

```java
@GetMapping("/api/orders/{id}")
public Order getOne(Long id) { … }    // ← 엔드포인트 1개 발견
```

### Step 2 — 호출 그래프 만들기

각 엔드포인트 메서드에서 시작해서, 이 메서드가 호출하는 다른 메서드를 따라간다.
호출 대상의 메서드 본문에 들어가서, 거기서도 또 호출하는 메서드를 따라간다. 깊이 우선 탐색.

`OrderController.createOrder` → `OrderService.create` → `PriceCalculator.compute` → … 처럼.

> ⚠️ **왜 컴파일된 클래스 파일이 필요할까?**
> 소스만 보면 `orderService.create(req)`에서 `orderService`의 *진짜 타입*을 100% 확신할 수 없을 때가 있다 (인터페이스 + 구현체 분리). 컴파일된 `.class`를 읽으면 실제 구현 클래스 정보를 알 수 있다.
> → `hotspot.yml`의 `apiAnalysis.classpathDirectories`에 `build/classes/java/main` 같은 디렉토리를 넣어야 하는 이유.

### Step 3 — 호출 그래프 안 메서드들의 점수를 합산

호출 그래프에 속한 모든 메서드의 (LOC, Revisions, Recency Decay, Cognitive Complexity, Line Coverage)를 합쳐서, 그 합으로 API 한 줄의 점수를 만든다.

수식으로 보면 이렇다 (단순화):

```
ApiSimpleScore   = Σ MethodSimpleScore     for m in callGraph
ApiRevisions     = Σ MethodRevisions       for m in callGraph
ApiRecencyDecay  = Σ MethodRecencyDecay    for m in callGraph
ApiCognitive     = Σ MethodCognitive       for m in callGraph
ApiCoverageMult  = 1 / (avg(coverage) + 0.1)    ← 평균을 한 번 더 multiplier로
ApiCompositeScore = ApiCognitive × ApiRecencyDecay × ApiCoverageMult
```

(`excludeCoverage=true`면 마지막 항만 빠진다.)

---

## 4. 구체적인 예시로 끝까지 가보기

위의 `OrderController` 예시로 실제 숫자를 끼워서 계산해보자.

### 가정한 메서드 단위 측정값

| 메서드 | LOC | Revisions | Recency Decay | Cognitive | Line Coverage |
|---|---:|---:|---:|---:|---:|
| `OrderController.createOrder` | 8 | 12 | 4.5 | 3 | 95% |
| `OrderController.validate` | 4 | 2 | 0.5 | 2 | 100% |
| `OrderService.create` | 6 | 18 | 6.2 | 4 | 70% |
| `PriceCalculator.compute` | 25 | 35 | 12.8 | 18 | 40% |
| `OrderRepository.save` | 2 | 1 | 0.2 | 1 | 90% |
| `NotificationService.send` | 12 | 8 | 2.0 | 6 | 30% |
| **합계 (= `POST /api/orders` 점수)** | **57** | **76** | **26.2** | **34** | avg≈70% |

### 같은 컨트롤러의 다른 API와 비교

```java
@GetMapping("/api/health")
public String health() { return "OK"; }    // 호출 그래프 = 자기 자신 한 개
```

| 메서드 | LOC | Revisions | Recency Decay | Cognitive | Line Coverage |
|---|---:|---:|---:|---:|---:|
| `OrderController.health` | 1 | 1 | 0.1 | 0 | 100% |

같은 표에 나란히 둬보면:

| API | Composite Score | 직관 |
|---|---:|---|
| `POST /api/orders` | **34 × 26.2 × 1.25 ≈ 1,113** | 무겁고 자주 바뀌고 복잡한 경로 |
| `GET /api/health` | **0 × 0.1 × 0.9 ≈ 0** | 거의 안 바뀌고 단순 |

→ "어디부터 회귀 테스트를 짤까?" 답은 자명해진다.

---

## 5. Shared Components — 한 명이 너무 많이 호출당하면?

위 예시에서 `PriceCalculator.compute`는 `POST /api/orders` 한 곳에서만 쓰였다. 그런데 실제 코드는 보통 이렇다.

```
POST /api/orders          → OrderService.create → PriceCalculator.compute → …
POST /api/orders/preview  → OrderService.preview → PriceCalculator.compute → …
GET  /api/prices/{sku}    → PricingController.get → PriceCalculator.compute → …
```

세 API가 같은 `PriceCalculator.compute`를 공유한다. 이 메서드를 한 줄만 잘못 고치면 **세 API가 동시에 깨진다.**
이런 "두 개 이상의 API가 호출하는 메서드"를 도구는 **Shared Component** 로 따로 분류한다.

### `sharedComponentMode` 옵션

| 값 | 동작 | 언제 쓰나 |
|---|---|---|
| `BOTH` (기본) | API hotspot 합산에도 포함 + Shared Components 표에도 따로 표시 | 그냥 보고 싶을 때 |
| `SEPARATE` | API hotspot 합산에서 빼고, Shared Components 표에만 표시 | 영향 범위(가로축) vs 엔드포인트별 위험(세로축)을 분리해서 보고 싶을 때 |
| `COMBINED` | Shared 표 안 만들고 API 점수에만 합산 | Shared 표가 거추장스러울 때 |

```yaml
analysis:
  apiAnalysis:
    enabled: true
    sharedComponentMode: separate   # 또는 both / combined
```

### 왜 분리가 의미 있나?

- **API 표만 보면** "어느 엔드포인트가 위험한가" → 운영팀 관점
- **Shared 표를 분리해서 보면** "어느 *내부 메서드*가 여러 API에 영향을 주는가" → 플랫폼/리팩토링 관점

같은 데이터를 두 시선으로 보는 거다.

---

## 6. 리포트 컬럼 읽기

`api_hotspots.csv` 한 줄을 까보면 이렇다.

```
simple_rank,composite_rank,http_method,route,fqcn,method,parameters,
loc,revisions,simple_score,recency_decay,cognitive_complexity,
coverage_multiplier,composite_score
```

| 컬럼 | 의미 |
|---|---|
| `simple_rank` / `composite_rank` | 같은 표 안에서 두 가지 점수 기준 각각의 순위 |
| `http_method` + `route` | API 식별자 (`GET /api/orders/{id}`) |
| `fqcn` + `method` + `parameters` | **엔트리포인트** 컨트롤러 메서드의 시그니처 |
| `loc / revisions / …` | 호출 그래프 **전체의 합** (한 메서드의 값이 아님) |
| HTML의 "Call Graph" 컬럼 | 합산에 포함된 메서드들의 목록 (펼쳐 보기) |

`shared_components.csv`도 거의 동일하지만, 식별자가 (메서드 시그니처)이고 `calling_apis` 컬럼이 "이 메서드를 부르는 API들의 리스트"를 보여준다.

---

## 7. 설정 한 줄 한 줄

```yaml
analysis:
  apiAnalysis:
    enabled: true                  # 켜면 api_hotspots / shared_components 표 추가 생성
    sharedComponentMode: both      # both | separate | combined
    classpathDirectories:          # 컴파일된 .class 위치 — call graph 정확도 위해 필요
      - build/classes/java/main
      # 멀티모듈이면 각 모듈의 classes 디렉토리도 추가
      - module-a/build/classes/java/main
      - module-b/build/classes/java/main
```

> 💡 **Gradle 멀티모듈 / Maven 모듈** 둘 다 지원. 각 서브모듈의 `target/classes`(Maven) 또는 `build/classes/java/main`(Gradle)을 모두 나열해주면 cross-module 호출까지 잡힌다.

---

## 8. 한계 — 이건 안 잡힌다

| 케이스 | 왜? | 어떻게 대처? |
|---|---|---|
| `@FeignClient` / RestTemplate으로 외부 API 호출 | 도구는 *내부* 호출 그래프만 추적 | 외부 의존성 표는 별도 도구 (예: jdeps) |
| 람다·익명클래스 안의 동적 디스패치 | 정적 분석으로 타입 결정 불가 | 의도된 한계. Composite Score는 약간 보수적으로 나옴 |
| Spring MVC가 아닌 다른 프레임워크 (gRPC, GraphQL, Quarkus 등) | 현재는 Spring 어노테이션만 인식 | Phase 2+에서 어노테이션 셋 확장 예정 |
| 호출 그래프 안의 클래스가 classpath에 없음 | 클래스 로딩 실패 → `ClassNotFoundException` → 전체 분석 실패 | 의존성 JAR 포함된 fat classpath 또는 `~/.gradle/caches/...` 디렉토리 추가 |

---

## 9. 5초 요약 다이어그램

```
┌────────────────────────────────────────────────────────────────┐
│  File Hotspot      = 파일 1개의 위험도                          │
│  Method Hotspot    = 그 파일 안 메서드 1개의 위험도             │
│  API Hotspot       = 엔드포인트가 부르는 메서드들 전부의 합     │
│  Shared Component  = 여러 API가 공유하는 메서드 → 변경 영향 ↑↑ │
└────────────────────────────────────────────────────────────────┘
```

---

## 10. 더 읽기

- 점수 공식 자체의 수학적 정의 → [`hotspot-advanced-spec.md`](./hotspot-advanced-spec.md)
- excludeCoverage 옵션 (coverage를 점수에서 빼는 모드) → [`../README.md`](../README.md)의 *Configuration schema* 절
- 실제 호출 그래프 추출 구현 → `src/main/java/io/github/baekchangjoon/hotspotanalysis/analysis/CallGraphBuilder.java`
