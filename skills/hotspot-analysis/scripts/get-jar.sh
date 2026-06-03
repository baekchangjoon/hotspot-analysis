#!/usr/bin/env bash
# Resolve a runnable hotspot fat jar and print its absolute path on stdout.
# Resolution order (first hit wins):
#   1. a local dev build under build/libs/        (developers)
#   2. a cached download                          (repeat runs)
#   3. download the pinned GitHub Release asset    (the common case — no build)
#   4. build from source with ./gradlew           (offline fallback, needs JDK)
#
# All progress/log output goes to stderr so `JAR=$(get-jar.sh)` stays clean.
# Requires JDK 21+ on PATH to RUN the jar (whatever path produced it).
set -euo pipefail

VERSION="0.1.0"
REPO="baekchangjoon/hotspot-analysis"
ASSET="hotspot-${VERSION}.jar"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/hotspot-analysis"

log() { printf '%s\n' "$*" >&2; }

# 1) local dev build
local_jar="$(ls "$ROOT"/build/libs/hotspot-*.jar 2>/dev/null | head -n1 || true)"
if [ -n "$local_jar" ]; then echo "$local_jar"; exit 0; fi

# 2) cached download
if [ -f "$CACHE/$ASSET" ]; then echo "$CACHE/$ASSET"; exit 0; fi

# 3) download the released asset (no build needed)
mkdir -p "$CACHE"
url="https://github.com/${REPO}/releases/download/v${VERSION}/${ASSET}"
if command -v gh >/dev/null 2>&1 \
   && gh release download "v${VERSION}" -R "$REPO" --pattern "$ASSET" --dir "$CACHE" >&2 2>/dev/null; then
  echo "$CACHE/$ASSET"; exit 0
fi
if command -v curl >/dev/null 2>&1 && curl -fsSL "$url" -o "$CACHE/$ASSET"; then
  log "downloaded $ASSET → $CACHE"
  echo "$CACHE/$ASSET"; exit 0
fi
rm -f "$CACHE/$ASSET" 2>/dev/null || true

# 4) build from source (needs the repo + a JDK)
if [ -x "$ROOT/gradlew" ]; then
  log "no release jar reachable; building from source…"
  ( cd "$ROOT" && ./gradlew bootJar -q ) >&2
  built="$(ls "$ROOT"/build/libs/hotspot-*.jar 2>/dev/null | head -n1 || true)"
  if [ -n "$built" ]; then echo "$built"; exit 0; fi
fi

log "ERROR: could not obtain the hotspot jar (no local build, no network/release, no source to build)."
exit 1
