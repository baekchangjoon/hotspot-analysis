## 1. Adam Tornhill 소개

"Adam Tornhill has a Bachelor of Science degree in Electrical Engineering from The Faculty of Engineering at Lund University. Adam obtained this degree from 1994 to 1997. Later, they pursued a Bachelor of Social Science in Psychology at Kristianstad University, completing it from 2007 to 2010. Continuing their education, Adam Tornhill earned a Master of Arts (M.A.) degree in Psychology from Kristianstad University, where they studied from 2010 to 2013."

**핵심 프로필**:
- **출신**: 스웨덴 (룬드 / 크리스티안스타드 대학)
- **이중 전공**: 전기공학(1994-1997, Lund) + 심리학 학사·석사(2007-2013, Kristianstad) — 이 조합이 "**Behavioral Code Analysis**"의 사상적 원천
- **창업**: CodeScene AB 창업자 겸 CTO ([codescene.com](https://codescene.com))
- **언어**: Clojure(Lisp 계열)로 CodeScene 제품군을 개발 — Lisp 해커로 자칭
- **수상**: "CodeScene awarded Best Paper at the 7th International Conference on Technical Debt 2024"

**주요 저서 (4권, Pragmatic Bookshelf)**:

| 책 | 연도 | 핵심 주제 |
|---|---|---|
| *Patterns in C* | 2010 | C 디자인 패턴 |
| *Lisp for the Web* | 2013 | Common Lisp 웹 개발 |
| ***Your Code as a Crime Scene*** | 2015 (2nd ed. 2024) | Hotspot, Temporal Coupling 등 **15가지 분석 기법** 대중화 |
| ***Software Design X-Rays*** | 2018 | Behavioral Code Analysis 방법론 체계화 |

**철학적 정체성**: "a lot of these techniques have actually been evaluated in the academic field. So, you know, for example, with the hotspots looking at the hotspot metric, we know that that has more predictive value than any properties of the code itself." — 학계 연구를 실무자에게 전달하는 "**브리지 역할**"을 자처합니다.

---

## 2. Hotspot Analysis 소개

**한 줄 정의**: **자주 변경되면서 복잡한 코드 파일**을 식별하여, 결함 위험과 유지보수 비용이 집중되는 영역을 시각화하는 분석 기법.

**핵심 인사이트** (Tornhill의 실증 주장):
- "1-2% of our codebase accounts for up to 70% of our development work, and how focusing on these hotspots can make our team 2x faster and 10x more predictable"
- 학술 후속 검증 (Springer 2025): "critical hotspots—covering less than 5% of the code—account for over 50% of recorded defects"

→ **파레토 원리(80/20)의 코드 영역 적용**이 핵심 명제입니다.

---

## 3. 등장 배경

### 전통적 코드 분석의 3가지 한계

| 한계 | 설명 |
|---|---|
| **시간 차원 부재** | SonarQube, PMD 등 정적 분석은 *현재 스냅샷*만 봅니다. "이 코드가 1년간 몇 번 변경됐는가"라는 진화적 맥락이 빠져 있음 |
| **사람 차원 부재** | 코드는 사람이 작성합니다. 몇 명의 개발자가 어떻게 협업했는가는 코드 자체보다 강한 결함 예측 신호 |
| **우선순위 부재** | 정적 분석은 수천 건의 위반을 던질 뿐, "**어디부터 고쳐야 하는가**"에 답하지 못함. 실무자에게 무의미한 노이즈가 됨 |

### 학술적 토대 (Tornhill 이전)

1. **Pareto in Software** (1970s~): Endres, Boehm — 결함 80%가 모듈 20%에 집중
2. **Nagappan & Ball, Microsoft Research (ICSE 2005)**: *"Use of Relative Code Churn Measures to Predict System Defect Density"* — Code Churn이 LOC보다 결함 예측력이 높음을 실증
3. **Hassan, A.E. (ICSE 2009)**: *"Predicting Faults Using the Complexity of Code Changes"* — 변경 복잡도 기반 결함 예측

### Tornhill의 기여

Tornhill은 위 학술 발견을 "take some fascinating research on software and make that academic research accessible to the practicing programmer"라는 명시적 목표로 통합·대중화했습니다. **VCS(Git) 데이터 마이닝**이라는 실용적 접근으로 즉시 적용 가능하게 만든 것이 핵심 혁신입니다.

---

## 4. 개념

**Hotspot의 공식 정의** (CodeScene 문서):

"A hotspots is complicated code that you have to work with often. These Hotspots are calculated from two different data sources: We use the lines of code in each file as a proxy for complexity. We use the change frequency of each file as a proxy for the effort you've spent on that code."

**2차원 매트릭스로 시각화**:

```
복잡도(Complexity) ↑
       │
       │  [위험]      [HOTSPOT]
       │  Refactor?   ⚠️ 최우선
       │
       ├─────────────────────
       │
       │  [안전]      [경계]
       │  방치 OK     모니터링
       │
       └─────────────────────→ 변경 빈도(Change Frequency)
```

- **우상단(Hotspot)**: 변경 잦음 + 복잡함 → **즉시 조치 필요**
- **좌상단**: 복잡하지만 안 건드림 → 안정적인 레거시. 굳이 건드리지 말 것
- **우하단**: 자주 바뀌지만 단순 → 건강한 코드 진화
- **좌하단**: 무시 가능

→ **핵심 통찰**: "복잡도만 보면 안 됨". 복잡해도 *건드리지 않는* 코드는 위험이 낮음.

---

## 5. 원리

### 5.1 데이터 소스: Git Log

```bash
git log --pretty=format:'[%h] %an %s' --date=short --numstat --after=2024-11-01 > evolution.log
```

→ VCS 이력이 **유일한 진실의 원천**. 별도 측정 도구나 운영 데이터 불필요.

### 5.2 두 가지 메트릭

| 차원 | 기본 측정 | 대체 측정 (정밀) |
|---|---|---|
| **복잡도(Complexity)** | LOC (Lines of Code) | Cyclomatic Complexity, Cognitive Complexity (SonarQube) |
| **변경 빈도(Change Frequency)** | Commit 수 (Revisions) | Code Churn (lines added+deleted) |

"The resulting output ('*-hotspots.csv') is sorted on change frequencies first - the best predictor of problems - and size (number of lines) second."

→ **Change Frequency가 우선순위 정렬의 1순위 키**입니다. LOC는 보조 지표.

### 5.3 점수 산출

기본형:
```
Hotspot Score = Revisions × LOC
```

정밀형 (실무 권장):
```
Hotspot Score = log(Revisions + 1) × Cognitive Complexity × (1 / (Coverage + 0.1))
```

### 5.4 시각화 패턴 — Circle Packing (D3.js)

Tornhill의 트레이드마크는 **원형 패킹(circle packing)** 시각화입니다:
- 원 크기 = 복잡도 (LOC)
- 원 색상 = 변경 빈도 (붉을수록 핫)
- 중첩 구조 = 디렉토리 계층

---

## 6. 활용 방법

[stefano-zanotti-edo/code-hotspots 가이드](https://github.com/stefano-zanotti-edo/code-hotspots)에서 정리된 실무 활용 5가지:

### 6.1 리팩토링 우선순위 결정 (가장 일반적)
"I started this investigation because I needed a way to plan and prioritize improvements to a legacy system. Some of the suggested improvements were redesigns that cost several weeks of intense work. I had to ensure we spent time on improvements that actually helped future development efforts."

### 6.2 코드 리뷰 집중 영역 식별
"Because hotspots make good predictors of buggy code, they identify the parts where reviews would be a good investment of time."

→ 모든 PR을 동일 강도로 리뷰하지 말고, **Hotspot 파일을 건드린 PR에 시니어 리뷰어 배정**.

### 6.3 테스트 우선순위 (Baek님 원래 질문)
- Hotspot ∩ Coverage 낮음 = 최우선 테스트 작성 대상
- Hotspot 파일에 대한 통합/E2E 테스트 강화

### 6.4 기술부채 정량화 및 ROI 산정
- Hotspot에 투입되는 개발 시간을 측정해 **기술부채의 "달러 가치"** 산출
- CodeScene의 핵심 비즈니스 가치 제안

### 6.5 팀 커뮤니케이션 도구
- 시각화를 통해 비기술 이해관계자(PM, 임원)에게 **기술부채를 한 장으로 설명**
- 테스트 리더와 QA 우선순위 협의 시 공통 언어

---

## 7. Getting Started

### 7.1 오픈소스 경로 (무료, 권장 시작점)

**도구**: [Code Maat](https://github.com/adamtornhill/code-maat) — Tornhill 본인이 만든 오픈소스 CLI (Clojure JAR, Java로 실행)

**최소 단계 (15분 안에 첫 결과)**:

```bash
# 1. Code Maat 다운로드
mkdir -p ~/tools/code-forensics && cd ~/tools/code-forensics
curl -sL https://github.com/adamtornhill/code-maat/releases/download/v1.0.4/code-maat-1.0.4-standalone.jar -o code-maat.jar

# 2. 분석 대상 프로젝트에서 Git 로그 추출 (최근 6개월)
cd /path/to/your/spring-boot-project
git log --all --numstat --date=short \
  --pretty=format:'--%h--%ad--%aN' \
  --no-renames --after=2024-11-19 > gitlog.txt

# 3. 변경 빈도 분석 (Revisions)
java -jar ~/tools/code-forensics/code-maat.jar \
  -l gitlog.txt -c git2 -a revisions > revisions.csv

# 4. 라인 수 추출 (복잡도 프록시)
cloc . --by-file --csv --quiet > lines.csv

# 5. 두 CSV를 조인 → Hotspot 표 완성
```

### 7.2 Spring Boot/Java 정밀 경로 — Code Maat + SonarQube

[Apache Wicket 분석 사례 (DEV Community 가이드)](https://dev.to/janux_de/hotspot-analysis-for-a-java-project-4ej6) 참고:

"For the hotspots of a program, the locations with a high complexity and change rate, the refactoring efforts will most likely have the highest return on investment. This blog post explores a way how they can be identified in a Java project. Apache Wicket was chosen as an example project. For its analysis, the open-source tools SonarCube, Code Charta, and Code Maat, and Python scripting are being used. The complexity is measured with SonarCube's Cognitive complexity metric and the change rate by the number of commits for a particular file."

**구성**:
- **SonarQube** → Cognitive Complexity (정밀 복잡도)
- **Code Maat** → Revisions (변경 빈도)
- **Code Charta** → 3D 시각화 ([code-charta.com](https://maibornwolff.github.io/codecharta/))

### 7.3 상용 경로 — CodeScene

- **무료 티어**: 오픈소스 프로젝트는 무제한 무료
- **유료**: 사내 코드는 라이선스 필요
- **장점**: 자동화, X-Ray(함수 단위 분석), Jira/GitHub 통합, Code Health 메트릭 등 추가 분석
- **단점**: 비용, 외부 도구 의존성

### 7.4 추천 학습 자료

1. **단행본 (필독)**: *Your Code as a Crime Scene* (2nd ed., 2024) — 15가지 분석 기법 망라
2. **단행본 (심화)**: *Software Design X-Rays* (2018) — 함수 단위 정밀 분석
3. **블로그**: [codescene.com/blog/author/adam-tornhill](https://codescene.com/blog/author/adam-tornhill)
4. **팟캐스트**: [SE Radio Ep.554](https://se-radio.net/2023/03/episode-554-adam-tornhill-on-behavioral-code-analysis/), Tech Lead Journal #241
5. **튜토리얼 (한국어 부재, 영어 단계별)**: [stefano-zanotti-edo/code-hotspots](https://github.com/stefano-zanotti-edo/code-hotspots), [thiagoghisi/your-code-as-a-crime-scene](https://github.com/thiagoghisi/your-code-as-a-crime-scene)

---

## ⚠️ Counter-arguments (반대 관점 / 도입 전 검토)

1. **상업적 이해관계 인지 필요**: Tornhill은 CodeScene의 창업자이자 최대 수혜자입니다. 그의 "1-2%가 70% 작업" 주장은 **자사 제품 가치 제안과 일치**하며, 일부 데이터는 CodeScene 고객 사례 기반입니다. 학술 독립 연구(Hassan 2009, Nagappan 2005)와 교차 검증해야 객관성이 확보됩니다.

2. **Single-author 한계**: 단행본 한 권 분량의 방법론을 단일 저자가 정의했습니다. ISTQB나 IEEE 표준처럼 위원회 검증을 거치지 않았습니다. 학술 인용은 늘고 있지만 *de facto* 표준이지 *de jure* 표준은 아닙니다.

3. **Code Maat 유지보수 한계**: 마지막 릴리스 v1.0.4 이후 활발한 업데이트가 부족합니다. 신규 도구로의 마이그레이션 전략이 필요할 수 있습니다.

4. **MSA 환경에서의 적용성**: Tornhill의 원래 방법론은 **모놀리식**을 가정합니다. 여러 마이크로서비스 레포가 분리된 환경에서는 서비스 간 Temporal Coupling 분석이 어렵습니다. 사내 적용 시 **모노레포 또는 서비스별 분리 분석** 중 선택해야 합니다.

5. **봇 커밋 노이즈**: Dependabot, Renovate, GitHub Actions 자동 커밋이 Revisions를 부풀립니다. **필터링 정규식 필수**.

