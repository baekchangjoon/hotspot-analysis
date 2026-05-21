# T1 Completion Report — Project Scaffolding

> Task: Bootstrap a Gradle Kotlin DSL project with Spring Boot 3 and Picocli, exposing `hotspot --version` / `--help`.
> Status: ✅ Completed (2026-05-21)

## Outcome

| Item | Result |
|---|---|
| Build | `BUILD SUCCESSFUL` (Gradle 8.10.2, Java 21 toolchain auto-provisioned) |
| Tests | 5 / 5 passed (0 failures, 0 errors, 0.43s) |
| Artifact | `build/libs/hotspot-0.1.0-SNAPSHOT.jar` (10.9 MB, executable bootJar) |
| CLI surface | `--version`, `--help`, no-args, bad-option all behave as designed |

## Decisions (with quantitative rationale)

| Decision | Choice | Rationale |
|---|---|---|
| Gradle Wrapper version | 8.10.2 | Spring Boot 3.3.5 officially supports up to Gradle 8.x (9.x requires SB 3.4+) |
| Build-time JDK | Temurin 22 (already installed) | Avoids forcing the user to install OpenJDK 21 manually |
| Target JDK | OpenJDK 21 via Foojay auto-provisioning | `foojay-resolver-convention 0.8.0` plugin downloads it on demand; no permanent system change |
| CLI framework integration | `picocli-spring-boot-starter` 4.7.6 | Picocli's official Spring Boot adapter; constructor DI works transparently |
| Logging at runtime | All Spring/banner output suppressed via `application.yml` | CLI tools should not emit framework startup chatter |

## Files created (10)

```
settings.gradle.kts
build.gradle.kts
gradlew, gradlew.bat
gradle/wrapper/{gradle-wrapper.jar, gradle-wrapper.properties}
.gitignore
src/main/resources/application.yml
src/main/java/io/github/baekchangjoon/hotspotanalysis/HotspotApplication.java
src/main/java/io/github/baekchangjoon/hotspotanalysis/cli/HotspotCommand.java
src/test/java/io/github/baekchangjoon/hotspotanalysis/HotspotApplicationTest.java
src/test/java/io/github/baekchangjoon/hotspotanalysis/cli/HotspotCommandTest.java
```

## Test breakdown

| Test class | Tests |
|---|---|
| `HotspotApplicationTest` | 1 (Spring context smoke) |
| `HotspotCommandTest` | 4 (version, help, no-args, bad-option) |

## CLI behaviour verified

```bash
$ java -jar build/libs/hotspot-0.1.0-SNAPSHOT.jar --version
hotspot 0.1.0-SNAPSHOT          # exit=0

$ java -jar build/libs/hotspot-0.1.0-SNAPSHOT.jar --help
Usage: hotspot [-hV]
...                              # exit=0

$ java -jar build/libs/hotspot-0.1.0-SNAPSHOT.jar --bad-option
Unknown option: '--bad-option'   # exit=2
```

## Known minor issues (non-blocking, deferred)

1. JVM CDS warning (`Sharing is only supported for boot loader classes`) during daemon startup — JVM option polish in T11.
2. Gradle 8.10.2 emits a deprecation warning against Gradle 9.0 — resolved when Spring Boot is bumped to 3.4+.

## Next step

T2 — YAML configuration loader: define the seven record POJOs, parse with Jackson YAML, validate with Jakarta Bean Validation, resolve `${ENV_VAR}` placeholders.
