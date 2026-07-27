# 릴리스 가이드 (RELEASING)

버튼 한 번으로 jar → 검증된 4-플랫폼 아카이브 → Docker 이미지 → Homebrew tap 갱신까지
흘러가는 릴리스 파이프라인의 운영 문서입니다. (v0.1.5 릴리스에서 전 구간 실검증됨.)

## 1. 릴리스 절차 (요약)

1. **버전 범프 PR** — 아래 4곳을 같은 값으로 올리고(`release.yml`이 강제 검증),
   SKILL.md의 Changelog에 항목을 추가한 뒤 PR로 머지:
   - `build.gradle.kts` → `version = "X.Y.Z"`
   - `src/main/java/.../cli/HotspotCommand.java` → `@Command(version = "hotspot X.Y.Z")`
   - `.claude-plugin/plugin.json` / `.claude-plugin/marketplace.json` → `"version": "X.Y.Z"`
2. **릴리스 버튼** — Actions 탭 → `release` 워크플로 → Run workflow →
   `version: vX.Y.Z` 입력. 또는 CLI:
   ```bash
   gh workflow run release.yml -f version=vX.Y.Z
   ```
3. 끝. 이후는 전부 자동입니다(아래 파이프라인).

> 태그는 `gh skill publish`가 **생성**하므로 항상 새 버전이어야 하며,
> protect-release-tags ruleset이 기존 태그 재작성/삭제를 차단합니다(불변 릴리스).

## 2. 파이프라인 구조

```
release.yml (버튼, workflow_dispatch)
 └─ publish: 버전 4곳 = 태그 검증 → gh skill publish (태그+릴리스 생성)
    ├─ assets → release-assets.yml (workflow_call, secrets: inherit ★)
    │   ├─ jar            : fat jar 빌드·실행 확인 → hotspot.jar + hotspot-X.Y.Z.jar 첨부
    │   ├─ bundles        : 4개 플랫폼 아카이브 교차 조립(JRE sha256 검증)
    │   ├─ verify-bundles : {ubuntu-latest, ubuntu-24.04-arm, macos-15-intel, macos-14}
    │   │                   네이티브 러너에서 bin/hotspot --version 실행 검증
    │   ├─ attach-bundles : 4개 전부 검증 통과 후에만 릴리스에 첨부
    │   ├─ bump-tap       : homebrew-tap의 Formula/hotspot.rb 갱신(deploy key push)
    │   └─ verify-tap     : tap formula가 이 태그를 참조하는지 단언(불일치 = 실패)
    └─ image → docker.yml : ghcr 멀티아치 이미지
```

`release-assets.yml`은 세 가지 트리거로 돕니다:

| 트리거 | 언제 | bump-tap |
|---|---|---|
| `workflow_call` (release.yml 버튼) | 정식 릴리스 | 실행 |
| `release: published` | 사용자가 직접 릴리스 생성(CLI/UI) | 실행 |
| `workflow_dispatch` | 기존 태그 자산 재빌드 | 기본 skip (`bump_tap=true`로 opt-in) |

기존 태그 재빌드·tap 재갱신:

```bash
gh workflow run release-assets.yml -f tag=vX.Y.Z                  # 자산만 재빌드·재첨부
gh workflow run release-assets.yml -f tag=vX.Y.Z -f bump_tap=true # + tap formula 갱신
```

## 3. 자격증명·전제

| 항목 | 내용 |
|---|---|
| `TAP_DEPLOY_KEY` (secret) | homebrew-tap 쓰기 SSH deploy key의 **개인키**. 공개키는 tap 저장소 deploy key(read-write)로 등록. 없으면 bump-tap이 경고 후 skip → **verify-tap이 실패**해서 드러남 |
| deploy key 재발급 | `ssh-keygen -t ed25519` → `gh repo deploy-key add <pub> --repo baekchangjoon/homebrew-tap --allow-write` → `gh secret set TAP_DEPLOY_KEY --repo baekchangjoon/hotspot-analysis < <priv>` |
| `secrets: inherit` ★ | reusable workflow는 **호출자의 secret을 자동으로 받지 못한다**. release.yml의 `assets` 호출에 반드시 유지 — v0.1.5에서 이것이 빠져 bump-tap이 조용히 skip됐던 전례 있음 |

## 4. 릴리스 후 확인 포인트

- run의 job summary — bump-tap이 스텝 내부에서 skip됐다면 사유가 표기됨
  (dispatch 게이트로 잡 자체가 skip된 경우엔 summary 없음 — 이때는 verify-tap도 같이 skip)
- verify-tap green = tap formula가 새 태그를 참조함 (아래 수동 확인과 동등)
  ```bash
  brew update && brew info baekchangjoon/tap/hotspot   # stable X.Y.Z 확인
  ```
- 릴리스 자산 6종: `hotspot.jar`, `hotspot-X.Y.Z.jar`,
  `hotspot-vX.Y.Z-{macos,linux}-{x64,aarch64}.tar.gz`

## 5. 트러블슈팅

| 증상 | 원인/조치 |
|---|---|
| verify-tap 실패 | ① `TAP_DEPLOY_KEY` 미설정으로 bump-tap이 skip(green)됐거나 ② formula 내용이 태그와 불일치, 또는 ③ formula fetch 실패. bump-tap job summary에서 사유 확인 후 `-f bump_tap=true`로 재실행. **bump-tap 자체가 실패(red)한 경우 verify-tap은 스킵**되므로 bump-tap 잡 로그를 직접 확인 |
| bump-tap "success"인데 tap 미갱신 | job summary에 skip 사유가 있는지 확인. verify-tap 도입 이후에는 이 케이스가 하드 실패로 드러남 |
| publish의 버전 불일치 에러 | §1의 4곳 중 하나가 태그와 다름. 범프 PR부터 다시 |
| 아카이브 검증 실패 | 해당 플랫폼 러너 로그 확인. 첨부는 4개 전부 통과해야만 수행되므로 릴리스에 불량 아카이브가 올라가지는 않음 |
