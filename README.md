# hotspot-analysis

> 🌐 **한국어** (현재 문서) · [English](README.en.md)

[![CI](https://github.com/baekchangjoon/hotspot-analysis/actions/workflows/ci.yml/badge.svg)](https://github.com/baekchangjoon/hotspot-analysis/actions/workflows/ci.yml)
[![Coverage](https://raw.githubusercontent.com/baekchangjoon/hotspot-analysis/badges/.github/badges/jacoco.svg)](https://github.com/baekchangjoon/hotspot-analysis/actions/workflows/ci.yml)
[![Branch Coverage](https://raw.githubusercontent.com/baekchangjoon/hotspot-analysis/badges/.github/badges/branches.svg)](https://github.com/baekchangjoon/hotspot-analysis/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](build.gradle.kts)
[![Skill](https://img.shields.io/badge/Skill-Claude%20Code%20%7C%20Cursor%20%7C%20Codex%20%7C%20Gemini%20CLI-blueviolet)](https://agentskills.io)
[![Docs](https://img.shields.io/badge/Docs-KO%20%7C%20EN-green)](README.en.md)

> Java 소스 파일과 메서드를 **복합 핫스팟 점수(Composite Hotspot Score)** 로
> 순위화합니다. 최근성 가중 변경 횟수, 인지 복잡도, 커버리지 공백을 결합해
> **역사적 증거상 버그가 가장 많이 살 만한 곳**에 테스트 노력을 집중하게 합니다.
> Adam Tornhill의 *Your Code as a Crime Scene* 방법론에 기반합니다.

이 저장소는 프로젝트의 **Phase 1**을 제공합니다. 로컬 git 저장소를 분석해
파일과 메서드에 점수를 매기고 CSV / YAML / Markdown 리포트를 내보내는, 완전히
연결된 CLI입니다.

---

## Claude Code 스킬로 설치

이 저장소는 **Claude Code 플러그인 마켓플레이스**이기도 합니다. 번들된
`hotspot-analysis` 스킬이 에이전트에게 CLI를 받아·설정·실행·해석하는 방법을
가르칩니다(릴리스 jar 자동 다운로드, 빌드 불필요). Claude Code 안에서:

```text
/plugin marketplace add baekchangjoon/hotspot-analysis
/plugin install hotspot-analysis@hotspot-analysis
/reload-plugins
```

이후 *"이 Java 저장소의 핫스팟 찾아줘"* 처럼 요청하면, 모델이 스킬을 호출해
아래 워크플로를 수행합니다.

### 스킬만 설치 (플러그인 없이)

[Agent Skills](https://agentskills.io) 호환 에이전트(Claude Code, Claude.ai,
Cursor, Gemini CLI 등)는 스킬 폴더만 직접 복사해 쓸 수 있습니다:

```bash
git clone https://github.com/baekchangjoon/hotspot-analysis
cp -r hotspot-analysis/skills/hotspot-analysis ~/.claude/skills/
```

> 스킬의 `scripts/get-jar.sh`가 릴리스 jar를 자동으로 내려받으므로 빌드는 필요
> 없습니다(JDK 21 런타임만 필요). JDK 없이 쓰려면 위 Docker 경로를 쓰세요.

---

## 기능

- **두 가지 단위** — 파일 단위와 메서드 단위 핫스팟, 점수 기준 정렬.
- **두 가지 VCS 소스** — 로컬 git 작업 트리(JGit) 또는 원격 GitHub(kohsuke `github-api`, WireMock으로 hermetic 테스트).
- **Java 21 파싱** — JavaParser 3.26이 record, sealed 타입, switch 식, 패턴 매칭을 이해.
- **YAML 설정** — 강타입, Jakarta Bean Validation 검증, 환경변수 치환.
- **네 가지 출력 형식** — CSV(엑셀 친화), YAML(기계 판독), Markdown(PR 친화), **HTML(브라우저에서 바로, 정렬·필터 가능, XSS 안전, 다크모드 대응)**.
- **CI 자체 분석 데모** — 매 CI 실행마다 다운로드해 브라우저로 열 수 있는 `hotspot-demo-report-<N>` 아티팩트 생성.
- **모든 계층의 포괄적 테스트** — 계약 테스트 + 단위 + Spring Boot E2E, 실패 0.

---

## 사전 요구사항

| 용도          | 요구사항                                                        |
|-------------------|--------------------------------------------------------------------|
| 빌드          | `PATH`에 JDK 17+ (Gradle이 Java 21 툴체인을 자동 프로비저닝) |
| **jar 실행** | **`PATH`에 JDK 21+** — `java -version`으로 확인 |
| 분석 대상   | `.git/` 폴더가 있는 디렉터리(실제 git 작업 트리) |

---

## 빠른 시작

### 1. jar 받기 (빌드 불필요)

[최신 Release](https://github.com/baekchangjoon/hotspot-analysis/releases/latest)에서
self-contained 실행 jar를 내려받습니다(JDK 21 런타임만 있으면 됨):

```bash
curl -fsSL https://github.com/baekchangjoon/hotspot-analysis/releases/latest/download/hotspot.jar -o hotspot.jar
```

> 소스에서 빌드하려면(선택): `./gradlew bootJar` → `build/libs/hotspot-*.jar`.
> 아래 예시의 `hotspot.jar`를 그 경로로 바꾸세요.

> **JDK 없이 — Docker.** 분석 대상 저장소를 `/work`에 마운트해 실행합니다:
> ```bash
> docker run --rm -v "$PWD":/work ghcr.io/baekchangjoon/hotspot-analysis:latest \
>   analyze --config /work/hotspot.yml
> ```

### 2. 샘플 설정 생성

```bash
java -jar hotspot.jar init -o hotspot.yml
```

### 3. `hotspot.yml` 편집

```yaml
analysis:
  target:
    type: local-git
    # .git/ 폴더가 있는 디렉터리여야 함
    path: /path/to/your/repo

  window:
    # 모드 A: 상대 윈도우("지금"부터 최근 N일) -- 안전한 기본값
    days: 365
    # 모드 B: 절대 ISO-8601 날짜 범위 (`days` 대신 사용)
    # since: "2024-01-01"
    # until: "2026-01-01"

  scope:
    granularity: [file, method]
    include:
      # 단일 모듈 Spring Boot 프로젝트
      - "src/main/java/**/*.java"
      # 멀티 모듈 Gradle/Maven 프로젝트 -- 서브모듈이 있으면 주석 해제
      # (ftgo, jhipster 등 대부분의 실제 스택).
      # - "**/src/main/java/**/*.java"
    exclude:
      - "**/generated/**"
      - "**/test/**"
      - "**/build/**"
      - "**/target/**"

  scoring:
    decayHalfLifeDays: 90   # 최근성 감쇠 반감기(일)
    # excludeCoverage: false  # true면 Composite = CC × Decay (커버리지는 관측용)

  # REST API 엔드포인트 핫스팟 (Spring 컨트롤러를 콜그래프 따라 집계).
  # RestAssured 테스트 생성 우선순위 입력. Spring 앱이면 켜세요.
  apiAnalysis:
    enabled: true
    sharedComponentMode: BOTH       # CUMULATIVE | SEPARATE | BOTH
    classpathDirectories:           # 콜그래프 심볼 해석 향상(선택)
      - build/libs

  # JaCoCo XML 리포트(선택). 실제 리포트가 있을 때만 주석을 해제하세요.
  # 커버리지가 점수에 반영됩니다(배수 1/(coverage+0.1)). 존재하지 않는 경로를
  # 가리키면 커버리지 없이 진행한다는 경고가 출력됩니다(점수는 왜곡되지 않음).
  # jacocoReportPath: build/reports/jacoco/test/jacocoTestReport.xml

output:
  # 대소문자 무시: csv | yaml | md | html (여러 개 허용)
  formats: [csv, yaml, md, html]
  apiLayout: BOTH       # COMBINED(hotspots.*에 통합) | STANDALONE(api_report.*) | BOTH
  path: ./hotspot-report
  topN: 20            # 0 = 무제한
```

### 4. 분석 실행

```bash
java -jar hotspot.jar analyze --config hotspot.yml
```

출력:

```
hotspot-report/
├── file_hotspots.csv      ← CSV는 단위별로 분리(파일 9열, 메서드 14열)
├── method_hotspots.csv
├── hotspots.yml           ← YAML/MD/HTML은 두 단위를 한 문서에 묶음
├── hotspots.md
└── hotspots.html          ← 아무 브라우저에서나 열기 — 정렬 가능 열 + 필터 박스
```

> **CSV는 분리하고 YAML/MD/HTML은 합치는 이유?**
> CSV는 단일 헤더의 표 형식이라 파일(9열)과 메서드(14열) 리포트가 헤더를
> 공유할 수 없어, 엑셀/시트 편의를 위해 두 파일로 내보냅니다. YAML, Markdown,
> HTML은 두 표를 자연스럽게 한 파일에 담는 문서 형식이라 — PR에 첨부(`.md`),
> 다운스트림 자동화에 투입(`.yml`), 또는 자체 완결형 증거 페이지로 브라우저에서
> 열기(`.html`)가 더 쉽습니다. 이를 뒤집는 `output.layout` 옵션은 Phase 2로
> 추적 중입니다.

> **HTML 리포트 기능.** `hotspots.html`은 단일 자체 완결형 파일입니다
> (원격 CSS/JS 없음, CDN 없음). 아무 열 헤더나 클릭해 오름/내림차순 정렬;
> 검색 박스에 입력해 경로·클래스·메서드·매개변수로 행 필터링. 라이트/다크 모드는
> 브라우저 설정을 따릅니다. 사용자 제어 값은 모두 HTML 이스케이프되어, 악의적인
> 파일 경로가 스크립트 태그를 주입할 수 없습니다.

---

## CLI 레퍼런스

```
Usage: hotspot [-hV] [COMMAND]

Description:
  Hotspot analysis CLI for Java codebases.

Options:
  -h, --help      Show this help message and exit.
  -V, --version   Print version information and exit.

Commands:
  analyze  Analyse a repository and emit hotspot reports.
  init     Generate a sample hotspot.yml configuration file.
```

### `hotspot analyze`

| 옵션 | 필수 | 기본값 | 설명 |
|---|:---:|---|---|
| `--config, -c <file>` | ✅ | — | YAML 설정 파일 경로 |
| `--quiet, -q`         |   | off  | stdout 요약 출력 억제 |
| `--strict, -s`        |   | off  | 결과가 비면 종료 코드 3 (윈도우 내 커밋 0건 또는 스코프 매칭 파일 0건). CI 게이팅용. |

| 종료 코드 | 의미 |
|:---:|---|
| 0 | 분석 완료; 출력 기록됨 |
| 1 | 설정 무효 / 파이프라인 실패 / 미지원 대상 |
| 2 | Picocli 사용법 오류 |
| 3 | `--strict` 설정 상태에서 결과가 비어 있음 |

### `hotspot init`

| 옵션 | 필수 | 기본값 | 설명 |
|---|:---:|---|---|
| `--output, -o <path>` |   | `./hotspot.yml` | 샘플 설정 저장 위치 |
| `--force, -f`         |   | off | 대상이 있으면 덮어쓰기 |

---

## 설정 스키마

| 경로 | 타입 | 비고 |
|---|---|---|
| `analysis.target.type` | `local-git` \| `github` | Phase 1 CLI는 `local-git`만 end-to-end 실행 |
| `analysis.target.path` | string | `local-git`에 필수 |
| `analysis.target.github.{owner,repo,branch,token}` | strings | `github`에 필수; `token`은 `${ENV_VAR}` 치환 지원 |
| `analysis.window.since` / `analysis.window.until` | ISO date | 또는 `analysis.window.days` 사용 |
| `analysis.window.days` | integer ≥ 1 | 지금 기준 상대 윈도우 |
| `analysis.scope.granularity[]` | `file` \| `method` | 둘 다 선택 가능 |
| `analysis.scope.include[]` | glob[] | 최소 한 개 필요 |
| `analysis.scope.exclude[]` | glob[] | 선택 |
| `analysis.scoring.decayHalfLifeDays` | integer ≥ 1 | 최근성 감쇠 반감기; 기본 90일 |
| `analysis.scoring.excludeCoverage` | boolean | `true`면 Composite = CC × Decay, 리포트는 배수 대신 원시 line-coverage 표시; 기본 `false` |
| `analysis.apiAnalysis.enabled` | boolean | REST API 엔드포인트 + 공유 컴포넌트 단위 활성화; 기본 `false` |
| `analysis.apiAnalysis.sharedComponentMode` | `CUMULATIVE` \| `SEPARATE` \| `BOTH` | 2개 이상 엔드포인트가 공유하는 메서드의 집계/리포트 방식; 기본 `BOTH` |
| `analysis.apiAnalysis.classpathDirectories[]` | string[] | 콜그래프 심볼 해석을 돕는 의존성 jar/클래스 디렉터리 |
| `analysis.jacocoReportPath` | string | JaCoCo XML 리포트 경로; 커버리지 배수 `1/(coverage+0.1)` 활성화. 없으면 배수 1.0 |
| `output.formats[]` | `CSV` \| `YAML` \| `MD` \| `HTML` | 최소 한 개 |
| `output.apiLayout` | `COMBINED` \| `STANDALONE` \| `BOTH` | API/공유 테이블 위치: `hotspots.*`에 통합, 독립 `api_report.*`, 또는 둘 다; 기본 `BOTH` |
| `output.path` | string | 출력 디렉터리 |
| `output.topN` | integer ≥ 0 | `0`은 "모든 행" |

문자열 값의 환경변수는 `${VAR_NAME}` 구문으로 치환됩니다. `#`로 시작하는 줄
(YAML 주석)은 그대로 두어 문서 예시가 오류를 일으키지 않습니다.

---

## 점수 계산 방식

모든 리포트는 이제 **두 개의 점수**와 **네 개의 입력 요인**을 다음 표준 순서로
나란히 담습니다:

| 열 | 의미 |
|---|---|
| LOC | 아티팩트의 현재 라인 수 |
| Revisions | 윈도우 내에서 해당 항목을 건드린 커밋 수 |
| Simple Score | `Revisions × LOC` — Adam Tornhill의 원래 신호 |
| Recency Decay | 같은 커밋들에 대한 `Σ exp(-ln(2) × Δt / halfLife)` |
| Cognitive Complexity | SonarQube에서 영감을 받은 AST 순회 점수 |
| Coverage Multiplier | JaCoCo XML 기준 `1 / (line_coverage + 0.1)`; 리포트 없으면 1.0 |
| Composite Score | `Cognitive Complexity × Recency Decay × Coverage Multiplier` |

행은 **Composite Score 내림차순**으로 정렬됩니다(동점은 경로 / 표준 시그니처로 처리).

CSV는 단위별로 분리(파일 9열, 메서드 14열); YAML/MD/HTML은 모든 단위를 한 문서에 묶습니다.

> 단위별 산출 방식 — **파일 / 메서드 / REST API 엔드포인트 / 공유 컴포넌트**마다
> Revisions / Recency Decay / Cognitive Complexity / Coverage를 어떻게 측정하고
> 결합하는지 — 는 [`docs/scoring/`](docs/scoring/README.md)에 소스 근거와 워크드
> 예시까지 정리돼 있습니다.

---

## 저장소 구조

```
docs/
  phase1-design.md            ← 아키텍처 & 결정
  reports/T1…T11-*.md         ← 태스크별 완료 리포트
src/main/java/io/github/baekchangjoon/hotspotanalysis/
  HotspotApplication.java     ← Spring Boot 진입점
  cli/                        ← Picocli 서브커맨드
  config/                     ← YAML 스키마 record + 로더
  vcs/                        ← VcsProvider + LocalGit / GitHub 구현
  parser/                     ← JavaParser 기반 메서드 추출
  analysis/                   ← Revisions / LOC / Score / Orchestrator
  output/                     ← CSV / YAML / MD 라이터
src/main/resources/
  application.yml             ← Spring Boot 로깅 설정
  templates/hotspot.example.yml ← `hotspot init`용 번들 샘플
```

---

## 트러블슈팅

첫 실행이 "비어" 보인다면 거의 확실히 아래 셋 중 하나입니다.

### `Files: 0` — `scope.include`에 매칭된 소스 파일 없음

Java NIO의 `**` glob은 빈 경로 세그먼트를 매칭하지 **않습니다**. 그래서 단일
모듈과 멀티 모듈 레이아웃 사이에 미묘한 비대칭이 생깁니다:

| 저장소 형태 | 첫 Java 경로 예시 | 동작하는 패턴 |
|---|---|---|
| 단일 모듈 | `src/main/java/com/foo/Hot.java` | `src/main/java/**/*.java` |
| 멀티 모듈 | `service-a/src/main/java/com/foo/Hot.java` | `**/src/main/java/**/*.java` |

대상의 형태를 미리 모르면 **두 패턴을 모두 나열**하세요. 수집기가 매칭을
중복 제거합니다:

```yaml
scope:
  include:
    - "src/main/java/**/*.java"        # 단일 모듈 저장소 포착
    - "**/src/main/java/**/*.java"     # 멀티 모듈 저장소 포착
```

빠른 점검(Bash):

```bash
git -C <repo> ls-files | grep -E 'src/main/java/.*\.java$' | head
```

### `Commits: 0` — 윈도우에 Java를 건드린 커밋 없음

윈도우는 *지금* 기준으로는 맞지만, 저장소가 수년간 비활성이었을 수 있습니다
(동결된 참조 구현, 아카이브된 데모 등). 실제 활동과 겹치는 절대 범위로
바꾸세요:

```yaml
window:
  since: "2017-01-01"
  until: "2026-12-31"
```

커맨드라인에서 교차 확인:

```bash
git -C <repo> log --since=<since> --until=<until> --name-only \
    --pretty=format: -- '*.java' | sort -u | head
```

> **CI 팁:** `hotspot analyze` 호출에 `--strict`를 추가하세요. 커밋이나 파일이
> 비어 돌아오면 종료 코드 **3**으로 끝나, 잘못 설정된 CI 파이프라인이 빈
> 리포트를 만드는 대신 큰 소리로 실패합니다.
>
> ```bash
> java -jar hotspot.jar analyze --config hotspot.yml --strict
> ```

### jar 실행 시 `UnsupportedClassVersionError`

번들 jar는 Java 21로 컴파일됩니다. Gradle 툴체인이 *빌드*에 21을 자동
프로비저닝했더라도, **실행**에는 여전히 `PATH`에 JDK 21+가 필요합니다:

```bash
java -version            # 반드시 21 이상 보고
# macOS:
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
# Linux (sdkman):
sdk use java 21.0.4-tem
```

### `Could not parse GitHub repository` / `Authentication required`

`target.type: github`에는 토큰이 필요합니다. 둘 중 하나:

```yaml
target:
  type: github
  github:
    owner: my-org
    repo:  my-repo
    branch: main
    token: ${GITHUB_TOKEN}     # 로드 시 환경에서 해석
```

…그리고 실행 전에 `export GITHUB_TOKEN=…`. Phase 1 CLI는 **`local-git`에만
end-to-end로 연결**되어 있고, GitHub 프로바이더는 WireMock 계약 테스트로
검증됩니다. 오늘 GitHub 저장소를 분석하려면 로컬에 클론하고
`target.type: local-git`을 작업 트리에 가리키세요.

---

## Phase 1 한계

1. **GitHub 대상 end-to-end**: API 클라이언트는 WireMock으로 검증되지만 CLI의
   `analyze`는 `local-git` 대상을 요구합니다. GitHub 클론 통합이 들어오기 전까지는
   저장소를 로컬에 클론하고 `target.type=local-git`으로 다시 실행하세요.
2. **작업 트리 LOC**: LOC는 각 역사적 커밋이 아니라 HEAD에서 읽습니다
   (Tornhill이 책에서 하는 동일한 단순화).
3. **봇 필터링 / 커버리지 통합 / 증분 캐시 없음**: Phase 2+로 연기.

이 결정들과 대안의 태스크별 분석은 `docs/reports/*`를 참고하세요.

---

## 로드맵

| Phase | 목표 | 상태 |
|---|---|:---:|
| 1 | 파일/메서드 점수화, 세 출력 형식의 CLI 프로토타입 | ✅ 완료 |
| 2 | 확장 CLI: 매개변수 조합, 커버리지 통합, 더 많은 언어 | 🚧 |
| 3 | REST API 백엔드 | ⏳ |
| 4 | 백엔드 위 프런트엔드 시각화 | ⏳ |

---

## 개발

```bash
./gradlew test            # 포괄적 테스트 스위트, ~10초
./gradlew build           # 전체 어셈블리
./gradlew check           # 테스트 + 정적 분석
```

CI는 매 푸시마다 그리고 **매일 스케줄**(09:00 KST)로 실행됩니다. 각 실행은
다음 아티팩트를 업로드합니다([Actions 탭](https://github.com/baekchangjoon/hotspot-analysis/actions) 참고):

| 아티팩트 | 내용 |
|---|---|
| `hotspot-jar-<N>` | 어셈블된 fat jar (`hotspot-*.jar`) |
| `test-results-<N>` | 모든 테스트 클래스의 JUnit XML |
| `test-report-<N>` | Gradle의 전체 HTML 테스트 리포트 |
| `test-summary-<N>` | GitHub Step Summary 패널에 표시되는 Markdown 요약 |
| `hotspot-demo-report-<N>` | **자체 분석 출력** — `file_hotspots.csv`, `method_hotspots.csv`, `hotspots.yml`, `hotspots.md`, `hotspots.html`, 그리고 이를 만든 `hotspot.yml`. 다운로드해 `hotspots.html`을 브라우저에서 바로 여세요. |

---

## Privacy

hotspot-analysis는 **전적으로 당신의 머신에서** 실행됩니다. 텔레메트리도,
애널리틱스도, "phone home"도 없습니다.

- **`local-git` 대상 (Phase 1 기본값):** git 히스토리와 Java 소스를 지정한 작업
  트리에서 읽고, 점수를 로컬에서 계산해, 리포트를 로컬 `output.path` 디렉터리에
  씁니다. **아무것도 컴퓨터를 떠나지 않습니다.**
- **`github` 대상:** 유일한 외부 네트워크 트래픽은 GitHub REST API로 향하며,
  **당신이** `${GITHUB_TOKEN}`으로 제공한 토큰으로 인증합니다. 토큰은 로드 시
  환경에서 읽히고 리포트에는 절대 기록되지 않습니다.
- **리포트**(`hotspots.html` 등)는 코드의 파일 경로·클래스/메서드 이름·라인 수를
  담습니다. 소스와 동일하게 취급하세요 — HTML 리포트는 완전히 자체 완결형이고
  (CDN/원격 호출 없음) 모든 값을 HTML 이스케이프하지만, 여전히 코드 구조를
  담으므로 의도적으로만 공유하세요.
- **스킬 / 플러그인**은 이 로컬 CLI를 구동할 뿐입니다. 설치해도 어떤 코드나 분석도
  제3자에게 전송하지 않습니다.

---

## License

[MIT](LICENSE) © 2026 baekchangjoon
