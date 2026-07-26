#!/usr/bin/env bash
# Acceptance-test harness for the JDK-friction-free requirements
# (docs/superpowers/requirements/2026-07-26-jdk-friction-free-requirements.md).
#
# The download-path scenarios need a java-free environment. On macOS,
# /usr/libexec/java_home finds system JDKs regardless of PATH, so those
# scenarios run in a java-free Linux container (Docker required); the
# fast-path scenarios run locally. Nothing touches the host system: temp
# dirs via mktemp, containers via --rm + a per-run label.
#
# Usage: scripts/e2e-jdk-friction.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_ID="e2e-jdk-$$"
IMG="debian:stable-slim"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/e2e-jdk.XXXXXX")"
PASS=0; FAIL=0

cleanup() {
  rm -rf "$TMP"
  docker ps -aq --filter "label=test-run=$RUN_ID" | xargs -r docker rm -f >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

ok()   { PASS=$((PASS+1)); echo "PASS: $1"; }
bad()  { FAIL=$((FAIL+1)); echo "FAIL: $1"; }
check(){ if "$@" >/dev/null 2>&1; then ok "${DESC:-$*}"; else bad "${DESC:-$*}"; fi; }

in_container() { # $1 = script
  docker run --rm --label "test-run=$RUN_ID" \
    -v "$ROOT/skills/hotspot-analysis/scripts:/s:ro" -v "$ROOT/install.sh:/install.sh:ro" \
    "$IMG" bash -c "export DEBIAN_FRONTEND=noninteractive
apt-get update -qq >/dev/null && apt-get install -y -qq curl ca-certificates git >/dev/null 2>&1
$1"
}

echo "== local (fast-path) scenarios =="

DESC="scripts pass bash -n"
check bash -n "$ROOT/install.sh" "$ROOT/skills/hotspot-analysis/scripts/ensure-java.sh" \
  "$ROOT/skills/hotspot-analysis/scripts/get-jar.sh" "$ROOT/skills/hotspot-analysis/scripts/run-analysis.sh"

DESC="REQ-011: install.sh heredoc is byte-identical to ensure-java.sh"
sed -n "/<<'ENSURE_JAVA_EOF'\$/,/^ENSURE_JAVA_EOF\$/p" "$ROOT/install.sh" | sed '1d;$d' > "$TMP/embedded.sh"
check diff "$TMP/embedded.sh" "$ROOT/skills/hotspot-analysis/scripts/ensure-java.sh"

DESC="regression: JAVA_TOOL_OPTIONS preamble must not break version parsing"
jdk_any="$(/usr/libexec/java_home 2>/dev/null || echo "${JAVA_HOME:-}")"
if [ -n "$jdk_any" ]; then
  out="$(env -i HOME="$TMP/home" XDG_CACHE_HOME="$TMP/cache" PATH=/usr/bin:/bin \
        JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8" JAVA_HOME="$jdk_any" \
        bash "$ROOT/skills/hotspot-analysis/scripts/ensure-java.sh" 2>/dev/null || true)"
  if [ -n "$out" ]; then ok "$DESC"; else bad "$DESC"; fi
else
  echo "SKIP: $DESC (no local JDK)"
fi

DESC="REQ-002: existing JDK is used directly, no download, no cache write"
jdk="$(/usr/libexec/java_home -v 21 2>/dev/null || echo "${JAVA_HOME:-}")"
if [ -n "$jdk" ]; then
  out="$(env -i HOME="$TMP/home" XDG_CACHE_HOME="$TMP/cache" PATH=/usr/bin:/bin \
        HOTSPOT_JAVA_HOME="$jdk" bash "$ROOT/skills/hotspot-analysis/scripts/ensure-java.sh")"
  if [ "$out" = "$jdk/bin/java" ] && [ ! -d "$TMP/cache/hotspot-analysis/jre" ]; then ok "$DESC"; else bad "$DESC"; fi
else
  echo "SKIP: $DESC (no local JDK 21+)"
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "SKIP: container scenarios (docker unavailable) — REQ-001/003/004/009/010 not verified"
  echo "== result: $PASS passed, $FAIL failed =="
  [ "$FAIL" -eq 0 ]
  exit $?
fi

echo "== container (download-path) scenarios =="

DESC="REQ-001: install.sh on a java-free system → hotspot --version works"
check in_container '
bash /install.sh >/dev/null 2>&1
export PATH="$HOME/.local/bin:$PATH"
hotspot --version'

DESC="REQ-003: run-analysis.sh end-to-end on a java-free system"
check in_container '
mkdir -p /repo/src/main/java/com/example && cd /repo
git init -q . && git config user.email t@t && git config user.name t
printf "package com.example;\npublic class A { int m(int x){ if (x>0) return x; return 0; } }\n" > src/main/java/com/example/A.java
git add . && git commit -qm c1
cat > /tmp/h.yml <<CFG
analysis:
  target: { type: local-git, path: /repo }
  window: { days: 365 }
  scope:
    granularity: [file]
    include: ["**/*.java"]
output: { formats: [csv], path: /tmp/out, topN: 0 }
CFG
cp -r /s /tmp/scripts
/tmp/scripts/run-analysis.sh /tmp/h.yml --quiet
test -s /tmp/out/file_hotspots.csv'

DESC="REQ-004: corrupted JRE download is rejected, nothing installed"
check in_container '
mkdir -p /fake
cat > /fake/curl <<"EOF"
#!/bin/bash
/usr/bin/curl "$@"; rc=$?
prev=""; out=""
for a in "$@"; do [ "$prev" = "-o" ] && out="$a"; prev="$a"; done
case "$out" in *jre.tar.gz) printf CORRUPT >> "$out";; esac
exit $rc
EOF
chmod +x /fake/curl
export PATH=/fake:$PATH
/s/ensure-java.sh 2>/tmp/err && exit 1
grep -q "checksum mismatch" /tmp/err && [ ! -d /root/.cache/hotspot-analysis/jre ]'

DESC="REQ-010: checksum unobtainable (no sha tools) → fail-closed"
check in_container '
mkdir -p /min && for t in bash curl tar gzip grep sed awk head find mktemp uname mkdir rm mv dirname cat; do
  ln -s "$(type -P $t)" /min/$t 2>/dev/null || true
done
PATH=/min /s/ensure-java.sh 2>/tmp/err && exit 1
grep -qi "refusing" /tmp/err && [ ! -d /root/.cache/hotspot-analysis/jre ]'

DESC="REQ-009: Adoptium API unreachable → clean error + Docker hint"
prep="req009-prep-$RUN_ID"
docker run --name "$prep" --label "test-run=$RUN_ID" "$IMG" bash -c \
  'apt-get update -qq >/dev/null && apt-get install -y -qq curl ca-certificates >/dev/null 2>&1' >/dev/null
docker commit "$prep" "req009-img-$RUN_ID" >/dev/null
docker rm "$prep" >/dev/null
if docker run --rm --network none --label "test-run=$RUN_ID" \
     -v "$ROOT/skills/hotspot-analysis/scripts:/s:ro" "req009-img-$RUN_ID" \
     bash -c '/s/ensure-java.sh 2>/tmp/err; rc=$?; [ $rc -eq 1 ] && grep -q "Docker" /tmp/err' >/dev/null 2>&1
then ok "$DESC"; else bad "$DESC"; fi
docker rmi "req009-img-$RUN_ID" >/dev/null 2>&1 || true

DESC="REQ-002a: system java appearing later takes priority over cached JRE"
check in_container '
JAVA1=$(/s/ensure-java.sh 2>/dev/null)   # provisions the cache
case "$JAVA1" in */.cache/*) ;; *) exit 1;; esac
# Deterministic "system JDK appears later": a PATH shim reporting version 21
# (resolution step 3 runs before the cache step, so it must win).
mkdir -p /fakejdk
printf "#!/bin/bash\n[ \"\$1\" = -version ] && { echo '"'"'openjdk version \"21.0.99\" 2099-01-01'"'"' >&2; exit 0; }\nexit 0\n" > /fakejdk/java
chmod +x /fakejdk/java
JAVA2=$(PATH=/fakejdk:$PATH /s/ensure-java.sh 2>/dev/null)
[ "$JAVA2" = /fakejdk/java ]'

DESC="REQ-002b: unsupported platform → clean error + Docker hint"
check in_container '
mkdir -p /fake && printf "#!/bin/bash\necho SunOS\n" > /fake/uname && chmod +x /fake/uname
PATH=/fake:$PATH /s/ensure-java.sh 2>/tmp/err && exit 1
grep -q "does not support this platform" /tmp/err && grep -q "Docker" /tmp/err'

echo "== result: $PASS passed, $FAIL failed =="
[ "$FAIL" -eq 0 ]
