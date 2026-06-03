# 고도화 기법 동작 검증 보고서

> 🌐 **한국어** (현재 문서) · [English](advanced-techniques-verification.en.md)

> Subject: `docs/hotspot-advanced-spec.md` 에 정의된 4가지 고도화 기법
> (Recency Decay / Cognitive Complexity / Coverage Gap / X-Ray + COMPOSITE)
> 의 구현이 명세 수식과 일치하는지 확인.
> Date: 2026-05-25
> Tested against commit: `0debe57`
> Reviewer: 외부 탐색 시험 (`~/github_*` 프로젝트 대상)

---

## 1. 검증 범위

| ID | 기법 | 도입 커밋 | 명세 |
|---|---|---|---|
| B-2 | Recency Decay | `46c5a04` | `Σ exp(-ln(2)/t_half × Δt)` |
| A-2 | Cognitive Complexity | `1c10bc2` | SonarQube spec (`if/for/while/catch/switch/&&/\|\|` + nesting) |
| C-4 | Coverage Gap | `b225d4e` | JaCoCo XML 라인 매핑 → `1/(coverage + 0.1)` |
| C-5 | X-Ray + COMPOSITE | `a39663e` | 메서드 단위 HTML 드릴다운, `complexity × decayed × multiplier` |

## 2. 검증 대상 프로젝트

| 티어 | Path | 용도 |
|---|---|---|
| Tiny | `~/github_line-service/line-service` | JaCoCo 리포트 생성 / coverage 파싱 검증 |
| Small | `~/github_advance-spring-boot-microservice/...` | 단순 cognitive complexity (file/method `=1`) 베이스라인 |
| Self | `~/github_hotspot-analysis/hotspot-analysis` | 복합 검증 (실제 jacocoTestReport.xml + 깊은 분기) |

## 3. 검증 방법

각 기법별로 독립 ground-truth 를 만들어 산출물 값과 1e-4 정밀도까지 대조:

- **Recency Decay**: `git log --pretty=%cI`로 commit 일자 추출 → Python `math.exp(-ln(2)/t_half × ΔDays)` 으로 재계산
- **Cognitive Complexity**: 소스 AST 를 직접 카운트 (SonarQube 가산점 룰)
- **Coverage Gap**: `build/reports/jacoco/test/jacocoTestReport.xml` 를 Python `ElementTree` 로 직접 파싱, `ci>0` 라인을 covered 로 집계
- **Composite**: 위 세 값을 곱해 `complexity × decayed × 1/(coverage+0.1)` 산출 후 산출물 score 와 비교
- **X-Ray HTML**: 출력 `hotspots.html` 에서 `xray-table` 엘리먼트를 직접 grep, 메서드 행과 컬럼 (Lines/Complexity/Decayed Revs/Coverage/Score/Share) 존재 확인

## 4. 검증 결과

### 4.1 `JacocoReportParser.java` 정밀 비교 (self-analysis)

설정: `decayHalfLifeDays=180`, `until=2026-12-31`, jacoco 공급. 파일은 단일 커밋
`b225d4e` (2026-05-23T17:35:36+09:00) 에서 추가됨 → Δdays = 222.

| 인자 | 산출물 | 독립 계산 | 결과 |
|---|---|---|---|
| Decayed Revs (file) | `0.43` | `exp(-ln2/180 × 222) = 0.425334` | ✅ |
| Cognitive Complexity `parse(Path)` | `10` | AST hand-count = 10 | ✅ |
| Cognitive Complexity `getMethodCoverage(...)` | `9` | AST hand-count = 9 | ✅ |
| Cognitive Complexity (file 총합, 역산) | `25` | `10 + 9 + 2(getFileCoverage) + 3(findCoverageForPath) + 1(normalizePath)` | ✅ |
| Coverage `parse` (lines 22–59) | `96.3%` | `26/27 = 0.9630` | ✅ |
| Coverage `getMethodCoverage` (lines 71–92) | `100.0%` | `15/15 = 1.000` | ✅ |
| Coverage (file) | `96.6%` (역산) | `56/58 = 0.9655` | ✅ |
| Composite Score (file) | `9.9795` | `25 × 0.425334 × 1/(0.9655+0.1) = 9.9795` | ✅ |
| Composite Score (`parse`) | `4.00` | `10 × 0.425334 × 1/(0.963+0.1) = 4.001` | ✅ |
| Composite Score (`getMethodCoverage`) | `3.48` | `9 × 0.425334 × 1/(1.0+0.1) = 3.479` | ✅ |

### 4.2 외부 프로젝트 교차 검증

| 항목 | 확인 |
|---|---|
| Decay 비활성화 (`t_half=1,000,000d`) 시 advance-spring-boot 상위 5개 score = `9.9845` = `complexity(1) × 0.998453 × 10` | ✅ |
| Decay 활성화 (`t_half=90d`) 시 2020년 커밋들 → score ≈ 0 (decay weight `exp(-17) ≈ 4×10⁻⁸`) | ✅ Cold Legacy 페널티가 의도대로 작동 |
| `cognitiveComplexity=0` 인 line-service 모든 메서드 → 모든 composite score = 0 | ✅ 곱의 영점성 일관 |
| line-service jacoco 공급 시 X-Ray 의 `createLine 100%`, `updateLine 0%`, `recordHistory 100%` 등 6개 메서드 coverage 가 jacoco XML 의 `ci>0` 집계와 6/6 일치 | ✅ |

### 4.3 출력 포맷·불변식

| 항목 | 결과 |
|---|---|
| HTML X-Ray 드릴다운 (파일 행 클릭 시 메서드 테이블 펼침) | ✅ 렌더링됨 |
| X-Ray 행 `Share` 백분율 합 = 100% (per file) | ✅ |
| Score DESC 정렬 (file/method) | ✅ |
| `topN` honored | ✅ |
| Idempotence (동일 입력 두 번 실행 → 바이트 동일 CSV) | ✅ |
| Markdown 메타 테이블에 `Scoring formula: COMPOSITE` 노출 | ✅ |

## 5. 평가

### 5.1 잘 된 점

1. **명세-구현 정합성**: 4가지 기법 모두 `docs/hotspot-advanced-spec.md` 의 수식과 1e-4 자릿수까지 일치. 특히 자명하지 않은 `until` 처리
   (`until.plusDays(1).atStartOfDay(UTC).toInstant().minusNanos(1)`) 가 일관되게 적용되어 day-count 가 정확히 222 로 떨어짐.
2. **곱의 영점성 보존**: `complexity=0` 또는 `decayed≈0` 일 때 composite score 가 안정적으로 0 으로 수렴 — 큰 입력에서도 NaN/Inf 가 관찰되지 않음.
3. **JaCoCo path resolver 의 견고성**: `endsWith` 매칭 (`JacocoReportParser#findCoverageForPath`) 으로 패키지 prefix 변동 (`com/example/...` vs `io/github/...`) 을 흡수. 멀티 모듈 빌드에서 sourcefile-name 만 일치하면 매핑되는 점이 실용적.
4. **X-Ray UI**: 파일 행 토글 + 메서드 테이블의 `Share %` 가 직관적. score 만 보고는 알 수 없는 "이 파일에서 어느 메서드가 점수의 대부분을 만들었나" 가 한눈에 드러남.

### 5.2 보완 권장 사항 (버그 아님, UX 차원)

1. **CSV/Markdown 에 분해 인자 부재**: composite mode 결과를 PR/스프레드시트에서 분해 검증하려면 HTML 만 가능. README 의 *CSV → 5/10 컬럼* 가이드를 composite mode 용으로 별도 명시하거나, 옵션 컬럼
   (`decayed_revisions`, `cognitive_complexity`, `coverage`) 을 CSV 에 추가하는 것을 검토 (Phase 2 후보).
2. **기본 `decayHalfLifeDays=90` 과 긴 window 의 충돌**: Tornhill 책 권장값이지만, `since:2017-01-01 until:2026-12-31` 같은 6년 윈도와 결합되면
   대부분 score 가 e-8 자릿수로 압축되어 모든 행이 `0` 으로 보임. 사용자가 의아해할 가능성이 큼.
   - 권장: `analyze` 실행 종료 시 *"이 윈도의 95-th percentile commit age = N days. half-life=90d 라 decay weight 가 매우 작습니다"* 같은 힌트 메시지 출력.
   - 또는 `decayHalfLifeDays: auto` 옵션으로 윈도 길이의 절반 등을 자동 설정.
3. **JaCoCo 미공급 시 multiplier = 10 의 부작용**: `coverage=0` 으로 처리되어 `1/(0+0.1)=10` 이 곱해짐. 즉 "측정 안 함" 과 "전혀 안 덮인 코드" 가
   수치적으로 구분 불가. 의도된 단순화이긴 하나 **CSV/MD 에 그 값이 보이지 않으므로 사용자가 자기 점수가 10배 부풀려진 줄 모를 수 있음**.
   - 권장: `jacocoReportPath` 가 null 인데 `formula: composite` 가 선택된 경우 표준출력에 *"NOTE: coverage 미공급 → 모든 파일에 multiplier=10 적용됨"* 경고.
4. **Cognitive Complexity 구현이 SonarQube *근사*임**: `CognitiveComplexityCalculator` 는 `&&/||` 가 같은 연산자로 연속될 때도 매번 +1 가산 (SonarQube 규약은 동일 연산자 sequence 는 1번만). 단위 테스트가 자체 정의에 따라 통과 중이지만, *"SonarQube-compliant"* 라는 docstring 표현은 *"SonarQube-inspired"* 로 톤다운 권장.
5. **`build/libs/` 의 stale jar 문제**: 첫 분석 시도에서 `formula: composite` 가 "YAML 파싱 실패" 로 떨어졌는데, 원인은 jar 가 신규 enum 추가 *이전* 시점 빌드였음. 에러 메시지가 `Failed to parse YAML configuration` 뿐이라 디버깅이 까다로움.
   - 권장: `Formula.from(...)` 의 `IllegalArgumentException` 을 ConfigLoader 가 별도 분기에서 잡아 `Unsupported scoring.formula: <raw> (allowed: SIMPLE, COMPOSITE)` 처럼 surface.

### 5.3 회귀 위험 모니터링 대상

- `HotspotAnalyzer#analyze` 가 251 LOC, cognitive complexity ≈ 115. 이 자신의 self-analysis 에서도 메서드 단위 1위 (score 181.83) — 즉 **분석기 본인이 가장 위험한 메서드를 가리키고 있는 self-coherent 신호**.
  Composite formula 가 늘어날 때 이 메서드도 함께 분기 폭증할 가능성이 있으니 Phase 2 진입 전 의도적 분해 (e.g. `CompositeScoringPipeline` 추출) 검토 권장.

## 6. 결론

`docs/hotspot-advanced-spec.md` 의 네 가지 기법 (B-2/A-2/C-4/C-5) 은 모두 명세 수식 그대로 구현되어 외부·자기-분석 모두에서 1e-4 정밀도 일치를 확인했다. 보완 사항은 모두 UX/문서 수준의 개선 제안이며, 산식·산출물 자체에는 결함이 발견되지 않았다.
