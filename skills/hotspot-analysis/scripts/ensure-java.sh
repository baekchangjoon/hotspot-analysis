#!/usr/bin/env bash
# Resolve a Java 21+ runtime and print the absolute path of its `java` binary
# on stdout. Downloads a Temurin 21 JRE (~46MB, sha256-verified) into the
# user cache as a last resort, so callers never require a pre-installed JDK.
#
# Resolution order (first hit wins):
#   1. $HOTSPOT_JAVA_HOME/bin/java        (explicit override)
#   2. $JAVA_HOME/bin/java                 if version >= 21
#   3. `java` on PATH                      if version >= 21
#   4. macOS: /usr/libexec/java_home -v 21+
#   5. previously provisioned JRE in the cache
#   6. download Temurin 21 JRE from the Adoptium API (checksum-verified)
#
# All progress/log output goes to stderr so `JAVA=$(ensure-java.sh)` stays
# clean (same convention as get-jar.sh).
#
# NOTE: install.sh embeds a byte-identical copy of this file as a heredoc
# (single-file `curl | bash` installer). validate-skills.yml diffs the two —
# keep them in sync.
set -euo pipefail

MIN_MAJOR=21
CACHE_ROOT="${XDG_CACHE_HOME:-$HOME/.cache}/hotspot-analysis"
JRE_HOME="$CACHE_ROOT/jre"

log() { printf '%s\n' "$*" >&2; }

docker_hint() {
  log "Install JDK 21+ manually, or use the Docker image instead:"
  log "  docker run --rm -v \"\$PWD\":/work ghcr.io/baekchangjoon/hotspot-analysis:latest analyze"
}

# Print the major version of the given java binary, or nothing on failure.
# Matches the 'version "..."' line rather than line 1: JVMs prepend preamble
# lines (e.g. 'Picked up JAVA_TOOL_OPTIONS: ...') when those env vars are set.
# Always returns 0 — a parse failure must fall through to the next candidate,
# not abort the resolver under set -e.
java_major() {
  local raw
  raw="$("$1" -version 2>&1 | grep -m1 'version "' | sed -E 's/.*version "([0-9]+)[^"]*".*/\1/')" || raw=''
  if [ "$raw" = "1" ]; then # legacy 1.x scheme
    raw="$("$1" -version 2>&1 | grep -m1 'version "' | sed -E 's/.*version "1\.([0-9]+).*/\1/')" || raw=''
  fi
  if printf '%s' "$raw" | grep -qE '^[0-9]+$'; then printf '%s' "$raw"; fi
  return 0
}

# If $1 is an executable java >= MIN_MAJOR, print it and exit 0.
use_if_ok() {
  local m
  if [ -n "$1" ] && [ -x "$1" ]; then
    m="$(java_major "$1")"
    if [ -n "$m" ] && [ "$m" -ge "$MIN_MAJOR" ]; then
      printf '%s\n' "$1"
      exit 0
    fi
  fi
}

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1" | awk '{print $1}'
  else return 1; fi
}

# --- 1..4: existing runtimes ---------------------------------------------------
use_if_ok "${HOTSPOT_JAVA_HOME:+$HOTSPOT_JAVA_HOME/bin/java}"
if [ -n "${HOTSPOT_JAVA_HOME:-}" ]; then
  # An explicit override that is unusable must not fail silently.
  log "WARNING: HOTSPOT_JAVA_HOME is set ($HOTSPOT_JAVA_HOME) but is not a Java ${MIN_MAJOR}+ runtime — ignoring it."
fi
use_if_ok "${JAVA_HOME:+$JAVA_HOME/bin/java}"
use_if_ok "$(command -v java 2>/dev/null || true)"
if [ -x /usr/libexec/java_home ]; then
  jh="$(/usr/libexec/java_home -v "${MIN_MAJOR}+" 2>/dev/null || true)"
  use_if_ok "${jh:+$jh/bin/java}"
fi

# --- 5: cached provisioned JRE -------------------------------------------------
use_if_ok "$JRE_HOME/bin/java"

# --- 6: download a Temurin JRE (checksum-verified) -----------------------------
os='' arch=''
case "$(uname -s)" in
  Darwin) os=mac ;;
  Linux)  os=linux ;;
esac
case "$(uname -m)" in
  arm64|aarch64) arch=aarch64 ;;
  x86_64|amd64)  arch=x64 ;;
esac
if [ -z "$os" ] || [ -z "$arch" ]; then
  log "ERROR: no Java ${MIN_MAJOR}+ found and automatic JRE download does not support this platform ($(uname -s)/$(uname -m))."
  docker_hint
  exit 1
fi
if ! command -v curl >/dev/null 2>&1; then
  log "ERROR: no Java ${MIN_MAJOR}+ found and curl is unavailable for the automatic JRE download."
  docker_hint
  exit 1
fi

url='' expected_sha=''
api="https://api.adoptium.net/v3/assets/latest/${MIN_MAJOR}/hotspot?os=${os}&architecture=${arch}&image_type=jre"
if command -v python3 >/dev/null 2>&1; then
  # Primary: the assets API carries the download link AND its sha256.
  meta="$(curl -fsSL "$api" 2>/dev/null | python3 -c '
import json, sys
try:
    pkg = json.load(sys.stdin)[0]["binary"]["package"]
    print(pkg["link"]); print(pkg["checksum"])
except Exception:
    pass
' || true)"
  url="$(printf '%s\n' "$meta" | sed -n 1p)"
  expected_sha="$(printf '%s\n' "$meta" | sed -n 2p)"
fi
if [ -z "$url" ] || [ -z "$expected_sha" ]; then
  # Fallback without python3: the binary endpoint redirects to the GitHub
  # asset, which publishes a `<file>.sha256.txt` sidecar.
  bin_api="https://api.adoptium.net/v3/binary/latest/${MIN_MAJOR}/ga/${os}/${arch}/jre/hotspot/normal/eclipse"
  url="$(curl -fs -o /dev/null -w '%{redirect_url}' "$bin_api" || true)"
  if [ -n "$url" ]; then
    expected_sha="$(curl -fsSL "${url}.sha256.txt" 2>/dev/null | awk '{print $1}' || true)"
  fi
fi
if [ -z "$url" ] || [ -z "$expected_sha" ]; then
  log "ERROR: no Java ${MIN_MAJOR}+ found and the Temurin JRE metadata could not be resolved (offline?)."
  docker_hint
  exit 1
fi

log "no Java ${MIN_MAJOR}+ found — downloading Temurin ${MIN_MAJOR} JRE (~46MB, one-time) → $JRE_HOME"
# Stage inside the cache root so the final mv is an atomic same-filesystem
# rename (a /tmp staging dir may sit on another filesystem, where an
# interrupted mv could leave a half-written cache).
mkdir -p "$CACHE_ROOT"
tmp="$(mktemp -d "$CACHE_ROOT/jre-download.XXXXXX")"
trap 'rm -rf "$tmp"' EXIT INT TERM
curl -fsSL "$url" -o "$tmp/jre.tar.gz" || { log "ERROR: JRE download failed: $url"; docker_hint; exit 1; }

actual_sha="$(sha256_of "$tmp/jre.tar.gz" || true)"
if [ -z "$actual_sha" ]; then
  log "ERROR: neither sha256sum nor shasum is available to verify the JRE download — refusing to install it unverified."
  docker_hint
  exit 1
fi
if [ "$actual_sha" != "$expected_sha" ]; then
  log "ERROR: JRE checksum mismatch (expected $expected_sha, got $actual_sha) — refusing to install."
  log "This can indicate a corrupted download or a tampered mirror. Retry, or install JDK 21 manually."
  exit 1
fi

tar -xzf "$tmp/jre.tar.gz" -C "$tmp"
# Locate the runtime home inside the archive (linux: <top>/, macOS: <top>/Contents/Home).
java_bin="$(find "$tmp" -type f -name java -path '*/bin/java' | head -n1 || true)"
[ -n "$java_bin" ] || { log "ERROR: downloaded JRE archive has no bin/java."; exit 1; }
home_dir="$(cd "$(dirname "$java_bin")/.." && pwd)"

# First writer wins: never clobber a JRE that a concurrent run may already
# be executing from.
if [ -x "$JRE_HOME/bin/java" ]; then
  rm -rf "$home_dir"
else
  mv "$home_dir" "$JRE_HOME" 2>/dev/null || rm -rf "$home_dir"
fi
use_if_ok "$JRE_HOME/bin/java"

log "ERROR: provisioned JRE failed its version check."
exit 1
