#!/usr/bin/env bash
#
# hotspot-analysis installer.
#
#   curl -fsSL https://raw.githubusercontent.com/baekchangjoon/hotspot-analysis/main/install.sh | bash
#
# Downloads the latest release jar and installs a `hotspot` wrapper on PATH so
# you can run `hotspot analyze` instead of `java -jar hotspot.jar`. Re-running is
# safe (it overwrites the jar and wrapper).
#
# Environment overrides:
#   HOTSPOT_VERSION   release tag to install (default: latest)
#   HOTSPOT_PREFIX    install prefix (default: ~/.local) → bin/hotspot + share/hotspot/hotspot.jar
#   HOTSPOT_REPO      owner/repo (default: baekchangjoon/hotspot-analysis)
set -euo pipefail

REPO="${HOTSPOT_REPO:-baekchangjoon/hotspot-analysis}"
VERSION="${HOTSPOT_VERSION:-latest}"
PREFIX="${HOTSPOT_PREFIX:-$HOME/.local}"
BIN_DIR="$PREFIX/bin"
DATA_DIR="$PREFIX/share/hotspot"
JAR_PATH="$DATA_DIR/hotspot.jar"
WRAPPER="$BIN_DIR/hotspot"

info() { printf '  %s\n' "$*"; }
warn() { printf 'WARNING: %s\n' "$*" >&2; }
die()  { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

# --- pick a downloader ---------------------------------------------------------
if command -v curl >/dev/null 2>&1; then
  download() { curl -fsSL "$1" -o "$2"; }
elif command -v wget >/dev/null 2>&1; then
  download() { wget -qO "$2" "$1"; }
else
  die "need curl or wget to download the release jar."
fi

# --- warn early if no JDK 21 (the wrapper re-checks at run time) ----------------
check_java() {
  if ! command -v java >/dev/null 2>&1; then
    warn "no 'java' on PATH. hotspot needs a JDK 21+ runtime."
    warn "install JDK 21, or use the Docker image instead:"
    warn "  docker run --rm -v \"\$PWD\":/work ghcr.io/${REPO}:latest analyze /work"
    return
  fi
  # parse the major version from `java -version` (e.g. "21.0.2" or "1.8.0_392")
  local raw major
  raw="$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
  major="${raw%%.*}"
  if [ "$major" = "1" ]; then
    major="$(java -version 2>&1 | head -1 | sed -E 's/.*version "1\.([0-9]+).*/\1/')"
  fi
  if [ -z "$major" ] || ! printf '%s' "$major" | grep -qE '^[0-9]+$'; then
    warn "could not determine the Java version (got: '$major'). Ensure JDK 21+ is installed, or use Docker."
  elif [ "$major" -lt 21 ]; then
    warn "found Java $major, but hotspot needs JDK 21+. Install JDK 21 or use Docker."
  else
    info "Java $major detected (OK)."
  fi
}

# --- resolve the jar download URL ----------------------------------------------
if [ "$VERSION" = "latest" ]; then
  JAR_URL="https://github.com/${REPO}/releases/latest/download/hotspot.jar"
else
  JAR_URL="https://github.com/${REPO}/releases/download/${VERSION}/hotspot.jar"
fi

echo "Installing hotspot-analysis (${VERSION}) from ${REPO}"
check_java

mkdir -p "$DATA_DIR" "$BIN_DIR"

info "downloading jar → $JAR_PATH"
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
download "$JAR_URL" "$tmp" || die "failed to download $JAR_URL (is there a published release?)"
# sanity: a jar starts with the ZIP magic "PK"
if [ "$(head -c 2 "$tmp")" != "PK" ]; then
  die "downloaded file is not a jar (got: $(head -c 64 "$tmp" | tr -d '\0'))."
fi
mv "$tmp" "$JAR_PATH"
trap - EXIT

info "writing wrapper → $WRAPPER"
cat > "$WRAPPER" <<EOF
#!/usr/bin/env bash
# hotspot-analysis wrapper (installed by install.sh)
if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: hotspot needs a JDK 21+ runtime, but 'java' is not on PATH." >&2
  exit 1
fi
_raw="\$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
_major="\${_raw%%.*}"
if [ "\$_major" = "1" ]; then
  _major="\$(java -version 2>&1 | head -1 | sed -E 's/.*version "1\.([0-9]+).*/\1/')"
fi
if printf '%s' "\$_major" | grep -qE '^[0-9]+\$' && [ "\$_major" -lt 21 ]; then
  echo "ERROR: hotspot needs JDK 21+, but found Java \$_major. Install JDK 21 or use Docker." >&2
  exit 1
fi
exec java -jar "$JAR_PATH" "\$@"
EOF
chmod +x "$WRAPPER"

echo "Installed."
info "jar:     $JAR_PATH"
info "wrapper: $WRAPPER"
case ":$PATH:" in
  *":$BIN_DIR:"*) info "run: hotspot analyze" ;;
  *) warn "$BIN_DIR is not on your PATH. Add it, e.g.:"
     warn "  echo 'export PATH=\"$BIN_DIR:\$PATH\"' >> ~/.bashrc   # or ~/.zshrc"
     info "then run: hotspot analyze" ;;
esac
