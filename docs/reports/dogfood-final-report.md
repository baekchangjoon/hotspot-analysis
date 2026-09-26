# hotspot-analysis 독 푸딩 최종 보고 (2026-09-26)

대상: 릴리스 jar v0.1.6 (`releases/latest/download/hotspot.jar`, 27.1MB) + main(56ba66a) 소스.
환경: Linux 샌드박스, OpenJDK 21.0.10, POSIX 로케일, Maven Central 간헐 429, Adoptium API 차단, Docker 데몬 없음.
수정 브랜치: `claude/brave-carson-7r8s20` (커밋 6794de9, main 미접촉).

## 1. 실행 매트릭스

| # | 시나리오 | 결과 | 비고 |
|---|---|---|---|
| S1 | 자체 저장소 zero-config (shallow clone) | 5.6s, 50 커밋/67 파일/206 메서드 | shallow 경고 정상 |
| S2 | `git fetch --unshallow` 후 재실행 | 95 커밋, 상위 11개 중 7개 순위 변동 | 경고 근거 실증 |
| S3 | `--print-config` → `--config` 라운드트립 | exit 0, 동일 결과 | |
| S4 | `init` 템플릿 그대로 / 하위 디렉터리 실행 | 명확한 에러, exit 1 | |
| S5 | `days`+`since` 충돌, 오타 키, 출력 경로가 파일 | 각각 필드·값·허용값 포함 에러 | |
| S6 | 깨진 JaCoCo XML | 경고 2줄 후 배수 1.0 진행 | |
| S7 | 커밋 0건 + `--strict` | exit 3, 힌트 3줄 | 리포트 파일은 여전히 생성됨 (F-E) |
| S8 | 한글 디렉터리·공백·`Héllo.java` | POSIX 로케일: `InvalidPathException` 크래시 / `LC_ALL=C.UTF-8`: 정상 | JVM `sun.jnu.encoding` 제약 (F-L) |
| S9 | `ensure-java.sh` fast path, `get-jar.sh` 다운로드·캐시, `install.sh` PREFIX 격리, 래퍼 실행 | 전부 정상 | JRE 자동 다운로드 경로는 Adoptium 차단으로 미검증 |
| S10 | spring-petclinic zero-config (빌드 전) | 2.7s, 57 커밋/30 파일/87 메서드, **엔드포인트 9/17** | **F-M 무경고 누락** |
| S11 | petclinic Maven 빌드 후 (JaCoCo·target/classes 자동 감지) | 커버리지 배수 적용(0.91~10), 엔드포인트 여전히 9 | 의존성 jar 없이는 동일 |
| S12 | petclinic + Spring jar 일부(9개) classpath | **NoClassDefFoundError 크래시, exit 1** | **F-N** |
| S13 | petclinic + `~/.m2/repository` 전체 classpath | 3.1s, **엔드포인트 17/17** | 원인 확정 |
| S14 | ftgo-application zero-config (2022년 이후 비활성) | Commits 0 → `--strict` exit 3 + 힌트 | 설계대로 |
| S15 | ftgo 절대 윈도우(2017~2022) | 4.3s, 295 커밋/277 파일/902 메서드, 엔드포인트 16 | Composite 대부분 0.0x (F-G) |
| S16 | wiremock zero-config | **Files: 3** (1,327개 중) | **F-H 모듈 감지 오류** |
| S17 | wiremock `**/src/main/java` 수동 설정 | 10s, 835 커밋/870 파일/6,079 메서드, 최대 heap 398MB | 성능 양호 |
| S18 | `./gradlew test` (개발자 환경 재현) | **61/282 실패** — `gpg.format=ssh` | **F-K 테스트 비격리** |

## 2. 발견 사항 (심각도 순)

| ID | 심각도 | 내용 | 상태 |
|---|---|---|---|
| F-M | **High** | 컨트롤러 메서드의 매개변수·반환 타입이 심볼 해석에 실패하면(`Model`, `BindingResult`, `RedirectAttributes` 등 의존성 jar 전용 타입) 엔드포인트가 `api_report`에서 **무경고 누락**. petclinic 17개 중 8개(POST 전부) 소실. SKILL.md의 "비어 있으면 classpath 확인" 규칙은 *부분* 누락을 잡지 못함 | **수정**: 누락 엔드포인트 수집 + WARNING(이름·해결책) |
| F-N | **High** | classpath jar가 참조하는 클래스가 없으면 `NoClassDefFoundError`(Error라 `catch (Exception)` 미포착) → 전체 분석 크래시 | **수정**: `LinkageError` 포착. 엔드포인트는 순위에 남기고 콜그래프가 불완전하다는 별도 경고 |
| F-H | **High** | zero-config 모듈 스캔이 루트 `src/main/java`에서 멈춰 하위 모듈 무시 (wiremock 1,327 → 3 파일). `--strict`도 통과해 사용자가 알 수 없음 | **수정**: 스캔 계속 + 두 glob 동시 포함. 둘 다면 라벨은 `root + modules` |
| F-K | Medium | 테스트 픽스처가 개발자 전역 `~/.gitconfig`(`gpg.format=ssh`)에 영향받아 61건 실패. CI는 green이라 로컬 개발 경험만 깨짐 | **수정**: 픽스처에 빈 `GpgConfig` 명시 |
| F-I | Medium | `apiAnalysis: { enabled: true }`만 쓰면 "sharedComponentMode is required" 검증 실패. README는 기본 BOTH라고 문서화 | **수정**: 레코드 기본값 BOTH |
| F-A | Low | `System.err` 경고의 `—`가 POSIX 로케일에서 `?`로 출력. 같은 실행의 Picocli 출력(`→`)은 정상 → 불일치 | **수정**: `ConsoleEncoding`이 Picocli 규칙으로 stderr 정렬 |
| F-B | Low | zero-config에서 JaCoCo 미검출 시 생성 방법 힌트 없음 | **수정**: 힌트 1줄 |
| F-D | Low | README "CI 자체 분석 데모"가 실제로는 합성 2커밋 mock 저장소 분석 | **수정**: 실제 자체 분석 잡 추가 + 문구 정정 |
| F-C | Medium | PR #49(커버리지 차용 버그 수정)가 두 달째 미릴리스. 스킬·installer·brew 사용자 전원이 버그 포함 v0.1.6 사용 | 미수정 (릴리스는 `docs/RELEASING.md`. 이 작업에서 버전을 올리지 않음) |
| F-G | Medium | 절대 윈도우로 오래된 히스토리를 볼 때 감쇠 기준이 `until`이라 설계는 맞으나, 반감기 90일 vs 6년 윈도우에서 Composite가 0.0x로 붕괴하고 요약은 소수 1자리(`composite=0.0`)만 표시해 순위 근거가 안 보임 | **수정**: `|score|≥10`은 소수 1자리, `≥1`은 2자리, 그 미만은 4자리. 윈도우가 반감기의 8배를 넘으면 stderr 경고. 반감기 자동 스케일은 점수를 바꾸므로 하지 않음 |
| F-E | Low | `--strict` 빈 결과에서 exit 3이지만 리포트 파일과 "complete" 요약이 먼저 출력됨. README의 "빈 리포트 대신 실패"와 어긋남 | **수정**: 빈 `--strict`는 리포트를 쓰지 않고 "complete"도 출력하지 않음 |
| F-L | Low | POSIX 로케일 + 비ASCII 경로에서 JVM 자체 `InvalidPathException`. 래퍼·Docker·스킬이 `LC_ALL` 폴백을 세팅하면 회피 가능 | **수정**: `LC_ALL`/`LANG`이 비어 있거나 `C`/`POSIX`일 때만 설치 래퍼와 `run-analysis.sh`가 `C.UTF-8`을 설정. Dockerfile `ENV`. `ensure-java.sh`는 부모에 export가 전달되지 않아 건드리지 않음 |
| F-O | Low | CI 액션들(checkout@v4 등 5종)이 Node 20 deprecation 경고, 매일 cron이 변경 없이도 아티팩트 5종 업로드 | **수정**: checkout/setup-java v5, setup-gradle v5, upload/download-artifact v7, junit-report v6, Docker 액션 v4/v7. 자체 분석 상위 5개를 Step Summary에 표시. jar는 `ls -t`. cron은 매일 유지(아티팩트 용량은 비용 문제이지 매일 실행을 막는 결함이 아님) |

## 3. 적용한 수정 (브랜치 `claude/brave-carson-7r8s20`, 커밋 6794de9)

| 파일 | 변경 |
|---|---|
| `analysis/CallGraphBuilder.java` | `CallGraphResult.unresolvedEndpoints` 추가, 메서드·클래스·엔트리 해석 실패를 수집, `catch (Exception \| LinkageError)` |
| `analysis/HotspotAnalyzer.java` | `warnUnresolvedEndpoints` — 개수·상위 5개 이름·classpath 해결책 경고 |
| `config/ConfigSynthesizer.java` | 루트 소스 + 하위 모듈 → 두 glob, 모듈 루트 아래로 계속 스캔(`src` 디렉터리 제외) |
| `config/ApiAnalysisConfig.java` | `sharedComponentMode` null → BOTH |
| `cli/AnalyzeCommand.java` | 멀티모듈 라벨을 glob 전체 기준으로, JaCoCo 미검출 힌트 |
| `cli/ConsoleEncoding.java` (신규) + `HotspotApplication.main` | stderr 문자셋을 Picocli 규칙(`sun.stderr.encoding` → 기본 문자셋)에 정렬 |
| 테스트 5개 픽스처 | `setGpgConfig(new GpgConfig(new Config()))` |
| 신규 테스트 10건 | 누락 엔드포인트 보고(단위·분석기), LinkageError 생존, 루트+모듈 glob, 중첩 모듈, 기본 sharedComponentMode, JaCoCo 힌트, ConsoleEncoding 4건 |
| `.github/workflows/ci.yml` | 실제 자체 분석 스텝(`analyze . --strict`, 전체 히스토리 + JaCoCo XML) + `hotspot-self-report-<N>` 아티팩트; 기존 합성 데모는 "API demo"로 개명 |
| `README.md`, `README.en.md`, `SKILL.md` | 자체 분석 문구 정정, 한계 5항(엔드포인트 심볼 해석·의존성 클로저), 스킬 결정 규칙·안티패턴 추가 |

검증: 292 테스트 0 실패(로컬, gpg 격리 후). 재빌드 jar로 S10(경고 8건 명시), S12(크래시 → 정상 완료, 17 엔드포인트), S16(3 → 873 파일), F-A(`?` → `—`) 재실행 확인.

후속 브랜치 `fix/dogfood-followups`에서 남은 항목을 닫았다. 중첩 `@RestController` 메서드는 바깥 클래스에 한 번 더 세지 않는다. `traverse` 중 `LinkageError`는 엔드포인트를 순위에 남기고 `incompleteCallGraphs`로 경고한다. F-G·F-E·F-L·F-O는 위 상태 표 참고. `./gradlew test`는 300건 0 실패. F-C(릴리스)만 남는다.

**주의**: `ci.yml`의 push 트리거는 `main`, `feat/**`, `fix/**`, `chore/**`, `docs/**`만 대상이라 `claude/**` 브랜치 푸시로는 CI가 돌지 않았습니다. 새 자체 분석 스텝은 PR을 열어야 CI에서 검증됩니다.

## 4. 개선 방향 (우선순위 제안)

1. **v0.1.7 릴리스** — PR #49 + 이번 수정. 스킬 사용자가 받는 jar가 두 달째 버그 포함(F-C). 릴리스 버튼 절차는 `docs/RELEASING.md` 그대로.
2. **엔드포인트 해석을 심볼 해석과 분리** — 지금은 경고로 가시화했지만, 해석 실패 엔드포인트도 구문 시그니처로 `api_report`에 포함(콜그래프만 비움)하면 "우선순위 큐" 자체가 완전해진다. 중간 규모 변경.
3. **classpath 자동 감지 확장** — zero-config가 `~/.m2/repository`·Gradle 캐시를 후보로 제안하거나, Gradle `build/install/*/lib`·Maven `target/dependency`를 감지. 현재는 사용자가 직접 알아야 한다.
4. **절대 윈도우 UX(F-G)** — 완료. 요약은 크기별 소수 자릿수(1/2/4자리), 윈도우가 반감기의 8배를 넘으면 경고. 반감기 자동 스케일은 점수를 바꾸므로 채택하지 않음.
5. **래퍼·Docker의 로케일 폴백(F-L)** — 완료. `hotspot` 래퍼와 `run-analysis.sh`는 `LC_ALL`이 C/POSIX를 고정할 때만 `LC_ALL`을, 그 외에는 `LC_CTYPE`만 `C.UTF-8`로 채움(JVM 경로 문자셋은 `LC_CTYPE`만 본다). Dockerfile `ENV`. `ensure-java.sh`는 export가 부모에 전달되지 않아 제외.
6. **`--strict` 의미 정리(F-E)** — 완료. 빈 결과면 리포트를 쓰지 않고 exit 3.
7. **CI 위생(F-O)** — 액션 메이저 버전은 Node 24 대응으로 올렸고, `hotspot-self-report` 상위 5개는 Step Summary에 표시한다. 일일 cron은 유지한다. 변경 없는 날의 아티팩트 업로드는 비용이지, 매일 실행을 막는 결함은 아니다.
8. **회귀 방지용 독 푸딩 자동화** — petclinic 클론은 CI 시간과 Maven Central 의존 때문에 넣지 않는다. 같은 불변조건(매핑된 메서드는 순위에 있거나 누락 경고에 이름이 있다, `@ModelAttribute`만 있는 메서드는 엔드포인트가 아니다, 누락이 5건을 넘으면 `(N more)`)은 `HotspotCliE2ETest`의 합성 픽스처가 잠근다.

## 5. 반론·리스크

- `ConsoleEncoding`은 `System.err`를 전역 교체한다. Windows 콘솔에서 `sun.stderr.encoding`이 설정된 경우는 그대로 존중하지만, 그 외 환경에서 터미널이 UTF-8이 아니면 깨진 글자가 `?` 대신 mojibake로 보일 수 있다. Picocli 출력과 동일한 동작이므로 일관성은 확보되나, 회귀 리포트를 받으면 ASCII 메시지로 되돌리는 것이 안전한 대안이다.
- `ConfigSynthesizer`에 `src`를 SKIP_DIRS에 추가했다. `src/` 아래에 모듈을 두는 비관습적 레이아웃(`src/moduleA/src/main/java`)은 감지되지 않는다(이전에도 depth 제한으로 불안정).
- 루트+모듈 이중 glob은 수집기가 중복 제거하므로 중복 행은 없지만, 라벨은 "multi-module"로만 표시된다.
- petclinic S13은 `~/.m2` 358개 jar 전체를 classpath로 넣어 3.1초였다. 대형 캐시(수천 jar)에서는 `Files.walk` + URLClassLoader 비용이 커질 수 있어 측정이 필요하다.
