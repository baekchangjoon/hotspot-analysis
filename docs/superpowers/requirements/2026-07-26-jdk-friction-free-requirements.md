# 요구사항명세: JDK 마찰 제거 (스킬·CLI)

- 작성일: 2026-07-26
- 배경: 에이전트 스킬·CLI 사용 시 JDK 21 런타임을 사용자가 직접 설치해야 하는 마찰을 제거한다.
  승인된 접근 3가지를 모두 구현한다: ① 래퍼의 JRE 자동 프로비저닝, ② Homebrew tap,
  ③ 릴리스에 JRE 동봉 self-contained 아카이브 추가.
- 범위 밖: Windows 지원(기존 install.sh도 미지원), GraalVM native image, 기존 Docker 경로 변경.

## 설계 결정 요약

### ① JRE 자동 프로비저닝 (`install.sh` + 스킬 스크립트)

Java 21+ 해석 순서(첫 히트 사용):

1. `HOTSPOT_JAVA_HOME` 환경변수 (명시 오버라이드)
2. `JAVA_HOME`이 21+이면 사용
3. `PATH`의 `java`가 21+이면 사용
4. macOS: `/usr/libexec/java_home -v 21` 결과가 있으면 사용
5. 캐시된 프로비저닝 JRE (`~/.cache/hotspot-analysis/jre/bin/java`)
6. **다운로드**: Adoptium API에서 Temurin 21 **JRE**(~46MB)를 받아 5의 캐시 위치에 설치
   - 메타데이터: `api.adoptium.net/v3/assets/latest/21/hotspot?os=<os>&architecture=<arch>&image_type=jre`
     (python3로 파싱 — 반드시 **`binary.package.link`/`binary.package.checksum`**을 읽는다.
     macOS 응답에는 `binary.installer`(.pkg)도 있으므로 절대 installer를 선택하지 않는다 — 실측 검증됨)
   - python3 없으면 폴백: `v3/binary/latest/.../jre/hotspot/normal/eclipse`를 **리다이렉트 해석해
     최종 GitHub 자산 URL을 얻은 뒤** 그 URL에 `.sha256.txt`를 붙인 사이드카 사용
     (사이드카는 최종 자산 URL 기준으로만 존재 — API URL에 붙이면 404. 최종 URL 기준 실측 HTTP 200 확인)
   - **fail-closed**: sha256 **불일치**뿐 아니라 체크섬 **확보 실패**(네트워크 오류·404·검증 도구 부재)도
     설치하지 않고 실패한다(공급망 방어). `shasum -a 256`/`sha256sum` 자동 선택.
   - 한계 명시: sha256은 같은 출처(Adoptium API/CDN)에서 오므로 **무결성(전송 손상) 방어**이지
     업스트림 자체가 침해된 경우의 **진본성 방어는 아니다**. GPG 서명 검증은 범위 밖(수용된 트레이드오프).
   - 다운로드·추출은 **캐시 루트 내부의 mktemp 스테이징 디렉터리**에서 수행 후 원자적 rename으로
     설치한다(교차 파일시스템 mv로 인한 부분 상태 방지; get-jar.sh의 tmp+mv 관례와 동일).
   - 캐시 갱신 정책: 자동 만료 없음. 수동 갱신은 `rm -rf ~/.cache/hotspot-analysis/jre` (문서화).
   - OS/arch 매핑: darwin→`mac`, linux→`linux`; arm64|aarch64→`aarch64`, x86_64→`x64`.
     그 외 조합은 명확한 에러 + Docker 안내.
7. 전부 실패 시: 기존과 동일한 에러 + Docker 안내 (동작 악화 없음).

배치:

- 공용 로직을 `skills/hotspot-analysis/scripts/ensure-java.sh`로 신설.
  stdout에 java 실행 파일 절대경로만 출력(`JAVA=$(ensure-java.sh)` 패턴, get-jar.sh와 동일 관례).
  `run-analysis.sh`가 `exec java` 대신 이것을 사용.
- `install.sh`는 단일 파일 배포(curl | bash)이므로 같은 로직을 **인라인 복제(byte-identical heredoc)**한다.
  install.sh는 이 heredoc을 `$PREFIX/share/hotspot/ensure-java.sh`로 설치하고, `hotspot` 래퍼가
  매 실행 시 그것을 호출한다(JDK를 나중에 설치하면 그것을 우선 사용; 다운로드는 최초 1회만).
  캐시 위치는 래퍼도 `~/.cache/hotspot-analysis/jre` 공용(스킬과 중복 다운로드 방지).
- **동기화 CI 가드**: `validate-skills.yml`에 install.sh의 heredoc과
  `skills/hotspot-analysis/scripts/ensure-java.sh`가 byte-identical한지 diff하는 단계를 추가하고,
  불일치 시 실패시킨다(보안 로직 이중화의 drift 방지 — REQ-001 수용 기준에 포함).
- 기존 `check_java` 경고는 "JRE를 자동으로 받는다"는 안내로 대체하고, install.sh는
  설치 시점에 resolver를 1회 실행해 JRE를 선확보한다(실패해도 경고 후 설치는 계속 —
  래퍼가 첫 실행 시 재시도).
- 참고: 접근 ③의 CI 패키징 스텝은 한 러너에서 4개 플랫폼 JRE를 교차 다운로드해야 하므로
  단일 대상용 `ensure-java.sh`를 재사용할 수 없는 **의도된 세 번째 구현**이다(assets API + jq,
  체크섬 검증 필수). drift 리스크는 인지된 트레이드오프로 기록한다.

### ② Homebrew tap

- **선행 부트스트랩(1회성 외부 작업)**: ⑴ public 저장소 `baekchangjoon/homebrew-tap` 신설(이 작업에서 수행),
  ⑵ 초기 `Formula/hotspot.rb` 커밋, ⑶ `TAP_GITHUB_TOKEN`(tap repo 쓰기 PAT) 발급·secret 등록 —
  **⑶은 관리자(사용자)의 수동 작업**이며 완료 전까지 REQ-007 자동 갱신은 skip 경로로 동작한다.
- `url` = 릴리스의 **버전 자산** `hotspot-0.1.4.jar`(이미 존재) + `sha256` 고정,
  `depends_on "openjdk@21"`, `bin.write_jar_script`.
  (Homebrew에 유지되는 JRE-only formula가 없어 풀 JDK 의존을 수용 — brew의 네이티브 의존성
  관리를 얻는 대신 크기를 양보하는 의도된 트레이드오프.)
- 검증: `brew style`/`brew audit` 통과 + `brew fetch`로 url·sha 일치 확인
  (전체 `brew install`은 openjdk@21 대용량 설치라 로컬 검증에서 제외하고 명시).
- 릴리스 자동 갱신: **별도 워크플로가 아니라 `release-assets.yml` 안의 `bump-tap` job**으로 둔다 —
  release-assets.yml은 이미 두 릴리스 경로(실사용자의 `release: published` 이벤트 +
  release.yml의 `workflow_call`) 모두에서 실행되므로, 그 안의 job이면 두 경로가 자동으로
  일관되게 커버된다(GITHUB_TOKEN 재귀 가드 문제 재발 방지).
  job은 버전 자산을 다운로드해 sha256을 계산하고 formula를 렌더링한 뒤 **`ruby -c` 문법 검증을
  통과해야만** tap 저장소에 커밋-푸시한다(sha는 실제 다운로드물에서 계산하므로 정의상 일치;
  잔여 리스크는 템플릿 파손 → ruby -c가 게이트).
  **secret `TAP_GITHUB_TOKEN`(tap repo 쓰기 PAT) 없으면 경고 후 skip**(릴리스는 실패시키지 않음).

### ③ Self-contained 릴리스 아카이브 (JRE 동봉)

- `release-assets.yml`에 job 추가: **ubuntu 러너 1개에서 4개 플랫폼 아카이브를 교차 조립**
  (아카이브 = tar이므로 러너 arch 불문. jpackage/jlink 대신 Temurin JRE 동봉 방식 —
  모듈 트리밍 리스크 없음, 예측 가능한 크기 ~70MB).
  - 대상: `{macos,linux} × {x64,aarch64}` → `hotspot-<tag>-<os>-<arch>.tar.gz`
  - 내용: `hotspot/`(루트) 아래 `lib/hotspot.jar` + `jre/`(Temurin 21 JRE, sha256 검증 후 동봉)
    + 실행 스크립트 `bin/hotspot`(동봉 jre 사용, 심링크·공백 경로 안전한 상대경로 해석)
- 검증 job: **4개 아카이브 전부** 해당 아키텍처 러너에서 추출 후 `bin/hotspot --version` 실행 확인 —
  linux-x64: `ubuntu-latest`, linux-aarch64: `ubuntu-24.04-arm`, macos-aarch64: `macos-14`,
  macos-x64: `macos-15-intel`(macos-13 이미지는 2025-12 폐기됨).
- 조립 시(빌드타임) JRE sha256 불일치면 **job이 실패하고 아카이브를 첨부하지 않는다**.
- `workflow_dispatch` 입력(tag)을 추가해 기존 태그로 드라이런 가능하게 한다.

### 문서

- README.md / README.en.md: 사전 요구사항 표(JDK "자동 확보" 반영), 빠른 시작에
  brew 설치·self-contained 아카이브 경로 추가, install.sh 설명 갱신, JRE 캐시 수동 갱신법.
- SKILL.md: "Requires JDK 21+" 문구를 자동 프로비저닝 안내로 갱신 + **명시적 명령들을
  `JAVA=$(scripts/ensure-java.sh)` 패턴으로 갱신**(REQ-003).
- 스크립트 헤더의 스테일 주석 정리: `get-jar.sh`("Requires JDK 21+ on PATH") 등.
- homebrew-tap 저장소 README(간단 사용법).

## 요구사항 매트릭스

| REQ | 우선순위 | 요구사항 (Given-When-Then) | 수용 테스트 | 상태 |
|---|---|---|---|---|
| REQ-001 | Must | **G**: java가 PATH·JAVA_HOME에 없거나 21 미만 **W**: `install.sh` 실행 후 `hotspot --version` **T**: JRE가 자동 다운로드(sha256 검증)되고 버전이 출력된다 | E2E-1 (로컬 하네스: PATH에서 java 제거 + `HOTSPOT_PREFIX` 임시 디렉터리) | ✅ |
| REQ-002 | Must | **G**: 시스템에 java 21+가 이미 있음 **W**: `hotspot` 래퍼 실행 **T**: 다운로드 없이 시스템 java를 사용한다. 서브케이스: ⒜ 캐시 JRE가 있어도 나중에 설치된 시스템 JDK가 우선한다 ⒝ 미지원 OS/arch에서는 명확한 에러 + Docker 안내 | E2E-2 (JAVA_HOME 지정·캐시 부재 확인; ⒜⒝는 컨테이너 서브케이스) | ✅ |
| REQ-003 | Must | **G**: java 없는 환경 **W**: 스킬 `run-analysis.sh`(→`ensure-java.sh`) 실행 **T**: JRE 자동 확보 후 분석이 완료된다. **SKILL.md의 명시적 명령("explicit form")도 `JAVA=$(scripts/ensure-java.sh)` 패턴으로 갱신**되고 Prerequisites의 "JDK 21+ 필수"가 자동 확보 안내로 바뀐다 | E2E-3 (java 없는 컨테이너에서 run-analysis.sh 실행) + DOC-1 | ✅ |
| REQ-004 | Must | **G**: sha256 불일치(변조/손상) **W**: JRE 다운로드 **T**: 설치하지 않고 명확한 에러로 실패한다 | UT-1 (하네스: 체크섬 위조 후 실패 확인) | ✅ |
| REQ-005 | Must | **G**: tap formula **W**: `brew style`+`brew audit`+`brew fetch` **T**: 모두 통과하고 sha가 릴리스 자산과 일치한다 | E2E-4 (로컬 brew 검증) | ✅ |
| REQ-006 | Must | **G**: 릴리스 발행 **W**: `release-assets.yml` **T**: 4개 self-contained 아카이브가 자산으로 첨부되고, 추출 후 `bin/hotspot --version`이 동작한다(**4개 플랫폼 전부** 네이티브 러너 CI 검증 후에만 첨부) | E2E-5 (workflow_dispatch 드라이런 v0.1.4 + CI 검증 job green) | 🟡 |
| REQ-007 | Should | **G**: 릴리스 발행 + `TAP_GITHUB_TOKEN` 존재 **W**: `release-assets.yml`의 `bump-tap` job **T**: tap formula의 url/sha가 새 버전으로 갱신되고 `ruby -c` 통과 후 푸시된다; secret 없으면 경고 후 skip | E2E-6 (워크플로 lint + secret-부재 경로는 드라이런으로 확인; 실제 갱신은 다음 릴리스에서 검증) | 🟡 |
| REQ-008 | Must | **G**: 문서 갱신 **W**: README/SKILL.md 대조 **T**: 새 설치 경로 3종이 실제 동작과 일치하게 기술된다 | DOC-1 (PR 문서동기화 게이트) | ✅ |
| REQ-009 | Must | **G**: Adoptium API 도달 불가(오프라인/프록시 차단) **W**: JRE 다운로드 시도 **T**: 스택트레이스 없이 명확한 에러 + Docker 안내로 실패한다(기존 동작 대비 악화 없음) | E2E-7 (`docker run --network none` 컨테이너) | ✅ |
| REQ-010 | Must | **G**: 체크섬 확보 실패(사이드카 404·검증 도구 부재) **W**: JRE 다운로드 **T**: 검증 없이 설치하지 않고 실패한다(fail-closed) | UT-2 (하네스: 체크섬 경로 차단) | ✅ |
| REQ-011 | Must | **G**: install.sh heredoc과 skill ensure-java.sh **W**: `validate-skills.yml` **T**: byte-identical 하지 않으면 CI가 실패한다 | CI-1 (diff 단계) | ✅ |

- 커버리지 분모: Must 10건 + Should 1건(REQ-007, 미연기) = 11건.
- REQ-006/007의 "실제 릴리스에서의 최종 확인"은 다음 릴리스 시점으로 자연 이연되나,
  드라이런(workflow_dispatch)과 CI green으로 이번 PR의 수용 기준을 충족한다.
- (리뷰 반영) 본 문서 작성과 병행해 `ensure-java.sh` 프로토타입이 먼저 작성·실측 검증되었다.
- 상태 기준(2026-07-26 PR 시점): ✅ = 수용 테스트 실측 통과(하네스 10/10 + brew audit/fetch).
  🟡 REQ-006 = 조립 스크립트를 컨테이너에서 그대로 실행해 4개 아카이브 생성 + 2개 플랫폼
  (linux-aarch64, macos-aarch64)은 로컬 실행 검증까지 완료; 4-러너 CI 검증은 머지 후
  `workflow_dispatch` 드라이런(v0.1.4)으로 확정한다.
  🟡 REQ-007 = job 구현·lint 완료, secret-부재 skip 경로는 드라이런에서 확인 예정;
  실제 formula 갱신은 `TAP_GITHUB_TOKEN` 등록 후 다음 릴리스에서 확정한다.

## E2E 하네스 (수용 테스트 실행 방법)

**macOS 로컬에서는 다운로드 경로(REQ-001/003/009/010)를 검증할 수 없다** — 해석 4단계
`/usr/libexec/java_home`은 PATH와 무관하게 시스템 JDK를 찾으므로(이 저장소 개발 머신에서 실측),
PATH 스트립만으로는 다운로드 코드가 실행되지 않는다. 따라서:

- **다운로드 경로 E2E는 java 없는 Linux 컨테이너에서 실행한다**(`debian:stable-slim` + curl;
  `--label test-run=<id>`, `--rm`로 자원 정리). REQ-009는 `--network none`으로 재현.
- macOS 로컬에서는 빠른 경로(REQ-002: 기존 JDK 발견·다운로드 생략)만 검증한다
  (`JAVA_HOME`/`HOTSPOT_JAVA_HOME` 지정 + 캐시 부재 확인).
- 하네스는 `JAVA_HOME`·`HOTSPOT_JAVA_HOME`을 unset하고, `HOTSPOT_PREFIX`·`XDG_CACHE_HOME`을
  mktemp 디렉터리로 지정한다 → 시스템 오염 없음.
- 종료 경로: `trap cleanup EXIT INT TERM`으로 임시 디렉터리·컨테이너 제거(자원 정리 게이트).
- 각 REQ 시나리오를 함수로 분리, 실패 시 비-0 종료.
