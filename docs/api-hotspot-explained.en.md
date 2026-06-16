# API Hotspot Analysis, Made Easy

> 🌐 [한국어](api-hotspot-explained.md) · **English** (this page)

> **Who is this for?** Engineers with a year or two of Java backend experience / junior engineers encountering a hotspot analysis tool for the first time
> **Reading time:** 10 minutes
> **Prerequisites:** Roughly knowing what a Spring controller (`@RestController`, `@GetMapping`) is

---

## 0. TL;DR — One-Sentence Summary

> **API Hotspot analysis tells you "which REST endpoint is most likely to break?"**
> The score is computed by summing up **every code path** the endpoint actually executes (controller → service → repository …).

---

## 1. Why Aren't File/Method Scores Enough?

Let's start with two familiar concepts.

### File Hotspot

- Unit: **a single file**
- Intuition: "`OrderService.java` changed 50 times last year and it's 800 lines long → the next bug is likely to surface here"
- In one line: *which file should I write more tests for?*

### Method Hotspot

- Unit: **a single method**
- Intuition: zoom in one more level within the file. "The `OrderService.process()` method accounts for 80% of that file's changes"
- In one line: *exactly which method inside that file is the problem?*

### But — Ops Teams See a Different Unit

Incident alerts usually come in like this:

> 🚨 *Slack: "**`POST /api/orders` error rate spiked to 4%**"*

The ops team doesn't know the code change count of `OrderService.java`. They **see the world in terms of REST endpoints**.
So API Hotspot raises the unit of analysis itself up to the **"API endpoint"** level.

| Unit | Identifier | User |
|---|---|---|
| File | `src/main/java/.../OrderService.java` | Code reviewer, refactoring owner |
| Method | `OrderService.process(OrderReq)` | TDD author |
| **API** | **`POST /api/orders`** | **Ops team, QA, PM** |

---

## 2. What Exactly Is "the Code of a Single API"?

This is the crux. **The "code" of a single API is not one method but a tree.**

Imagine a controller like this.

```java
@RestController
class OrderController {
    private final OrderService orderService;
    private final NotificationService notifications;

    @PostMapping("/api/orders")              //  ← API entry point
    public Order createOrder(OrderRequest req) {
        validate(req);                        //  ← private in the same class
        Order saved = orderService.create(req); // ← service call
        notifications.send(saved);            //  ← call to another service
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
        BigDecimal price = pricer.compute(req); //  ← another call
        return repo.save(new Order(req, price));//  ← another call
    }
}
```

When you call `POST /api/orders` once, the following methods run in turn:

```
POST /api/orders
└─ OrderController.createOrder         ← entry
   ├─ OrderController.validate
   ├─ OrderService.create
   │  ├─ PriceCalculator.compute
   │  └─ OrderRepository.save
   └─ NotificationService.send
```

**In other words, the "code surface" of `POST /api/orders` = all 6 methods above.**
A change to any one of these methods affects that API. So it's natural to compute the risk too by **summing up the risk of all 6 methods**.

This "collection of all methods reachable from the entry" is called the **call graph**.

---

## 3. So What Does the Tool Do? (Step by Step)

### Step 1 — Find the Endpoints

It parses the source code into an AST and collects every method annotated with a Spring annotation (`@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`, `@RequestMapping`).

```java
@GetMapping("/api/orders/{id}")
public Order getOne(Long id) { … }    // ← 1 endpoint found
```

### Step 2 — Build the Call Graph

Starting from each endpoint method, it follows the other methods this method calls.
It enters the body of each callee, and follows the methods called there too. Depth-first search.

Like `OrderController.createOrder` → `OrderService.create` → `PriceCalculator.compute` → ….

> ⚠️ **Why are compiled class files needed?**
> Looking at source alone, you sometimes can't be 100% certain of the *real type* of `orderService` in `orderService.create(req)` (interface + implementation separation). Reading the compiled `.class` reveals the actual implementation class information.
> → This is why you need to put a directory like `build/classes/java/main` in `apiAnalysis.classpathDirectories` in `hotspot.yml`.

### Step 3 — Sum Up the Scores of the Methods in the Call Graph

It sums the (LOC, Revisions, Recency Decay, Cognitive Complexity, Line Coverage) of all methods in the call graph, and uses that sum to produce the score for one API row.

As a formula it looks like this (simplified):

```
ApiSimpleScore   = Σ MethodSimpleScore     for m in callGraph
ApiRevisions     = Σ MethodRevisions       for m in callGraph
ApiRecencyDecay  = Σ MethodRecencyDecay    for m in callGraph
ApiCognitive     = Σ MethodCognitive       for m in callGraph
ApiCoverageMult  = 1 / (avg(coverage) + 0.1)    ← average folded in once more as a multiplier
ApiCompositeScore = ApiCognitive × ApiRecencyDecay × ApiCoverageMult
```

(If `excludeCoverage=true`, only the last term drops out.)

---

## 4. Walking Through a Concrete Example All the Way

Let's plug actual numbers into the `OrderController` example above and compute.

### Assumed Per-Method Measurements

| Method | LOC | Revisions | Recency Decay | Cognitive | Line Coverage |
|---|---:|---:|---:|---:|---:|
| `OrderController.createOrder` | 8 | 12 | 4.5 | 3 | 95% |
| `OrderController.validate` | 4 | 2 | 0.5 | 2 | 100% |
| `OrderService.create` | 6 | 18 | 6.2 | 4 | 70% |
| `PriceCalculator.compute` | 25 | 35 | 12.8 | 18 | 40% |
| `OrderRepository.save` | 2 | 1 | 0.2 | 1 | 90% |
| `NotificationService.send` | 12 | 8 | 2.0 | 6 | 30% |
| **Total (= `POST /api/orders` score)** | **57** | **76** | **26.2** | **34** | avg≈70% |

### Comparing with Another API in the Same Controller

```java
@GetMapping("/api/health")
public String health() { return "OK"; }    // call graph = just itself
```

| Method | LOC | Revisions | Recency Decay | Cognitive | Line Coverage |
|---|---:|---:|---:|---:|---:|
| `OrderController.health` | 1 | 1 | 0.1 | 0 | 100% |

Putting them side by side in the same table:

| API | Composite Score | Intuition |
|---|---:|---|
| `POST /api/orders` | **34 × 26.2 × 1.25 ≈ 1,113** | heavy, frequently changed, complex path |
| `GET /api/health` | **0 × 0.1 × 0.9 ≈ 0** | barely changes, simple |

→ "Where should I write regression tests first?" The answer becomes obvious.

---

## 5. Shared Components — What If One Method Gets Called Too Much?

In the example above, `PriceCalculator.compute` was used in only one place, `POST /api/orders`. But real code usually looks like this.

```
POST /api/orders          → OrderService.create → PriceCalculator.compute → …
POST /api/orders/preview  → OrderService.preview → PriceCalculator.compute → …
GET  /api/prices/{sku}    → PricingController.get → PriceCalculator.compute → …
```

Three APIs share the same `PriceCalculator.compute`. Get even one line of this method wrong and **all three APIs break at once.**
The tool classifies such "methods called by two or more APIs" separately as **Shared Components**.

### The `sharedComponentMode` Option

| Value | Behavior | When to use |
|---|---|---|
| `BOTH` (default) | Included in the API hotspot sum + also listed separately in the Shared Components table | When you just want to see everything |
| `SEPARATE` | Excluded from the API hotspot sum, shown only in the Shared Components table | When you want to separate blast radius (horizontal axis) from per-endpoint risk (vertical axis) |
| `COMBINED` | No Shared table is built; only summed into API scores | When the Shared table is in the way |

```yaml
analysis:
  apiAnalysis:
    enabled: true
    sharedComponentMode: separate   # or both / combined
```

### Why Does Separating Them Matter?

- **Looking only at the API table** → "which endpoint is at risk" → ops team's perspective
- **Looking at the Shared table separately** → "which *internal method* affects multiple APIs" → platform/refactoring perspective

It's the same data viewed through two lenses.

---

## 6. Reading the Report Columns

Cracking open one row of `api_hotspots.csv` looks like this.

```
simple_rank,composite_rank,http_method,route,fqcn,method,parameters,
loc,revisions,simple_score,recency_decay,cognitive_complexity,
coverage_multiplier,composite_score
```

| Column | Meaning |
|---|---|
| `simple_rank` / `composite_rank` | Each row's rank under two scoring criteria, within the same table |
| `http_method` + `route` | API identifier (`GET /api/orders/{id}`) |
| `fqcn` + `method` + `parameters` | Signature of the **entry-point** controller method |
| `loc / revisions / …` | **Sum over the entire call graph** (not a single method's value) |
| HTML "Call Graph" column | List of the methods included in the sum (expandable) |

`shared_components.csv` is almost identical, except the identifier is (the method signature) and the `calling_apis` column shows "the list of APIs that call this method."

---

## 7. Configuration, Line by Line

```yaml
analysis:
  apiAnalysis:
    enabled: true                  # turning it on adds the api_hotspots / shared_components tables
    sharedComponentMode: both      # both | separate | combined
    classpathDirectories:          # location of compiled .class — needed for call graph accuracy
      - build/classes/java/main
      # for multi-module, also add each module's classes directory
      - module-a/build/classes/java/main
      - module-b/build/classes/java/main
```

> 💡 **Both Gradle multi-module and Maven modules** are supported. If you list every submodule's `target/classes` (Maven) or `build/classes/java/main` (Gradle), even cross-module calls get captured.

---

## 8. Limitations — These Won't Be Captured

| Case | Why? | How to handle? |
|---|---|---|
| External API calls via `@FeignClient` / RestTemplate | The tool only traces the *internal* call graph | Use a separate tool for the external dependency table (e.g. jdeps) |
| Dynamic dispatch inside lambdas / anonymous classes | Static analysis can't determine the type | An intended limitation. The Composite Score comes out slightly conservative |
| Frameworks other than Spring MVC (gRPC, GraphQL, Quarkus, etc.) | Currently only Spring annotations are recognized (other frameworks unsupported) | Not currently supported |
| A class in the call graph is not on the classpath | Class loading failure → `ClassNotFoundException` → the whole analysis fails | Add a fat classpath including dependency JARs, or a directory like `~/.gradle/caches/...` |

---

## 9. Five-Second Summary Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│  File Hotspot      = risk of a single file                           │
│  Method Hotspot    = risk of a single method in that file            │
│  API Hotspot       = sum over every method the endpoint calls        │
│  Shared Component  = method shared by many APIs (high blast radius)  │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 10. Further Reading

- The mathematical definition of the scoring formula itself → [`hotspot-advanced-spec.md`](./hotspot-advanced-spec.md)
- The excludeCoverage option (mode that removes coverage from the score) → the *Configuration schema* section of [`../README.md`](../README.md)
- The actual call graph extraction implementation → `src/main/java/io/github/baekchangjoon/hotspotanalysis/analysis/CallGraphBuilder.java`
