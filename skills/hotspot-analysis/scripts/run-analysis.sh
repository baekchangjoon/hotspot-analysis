#!/usr/bin/env bash
# Thin convenience wrapper around the hotspot-analysis CLI.
# Resolves a Java 21+ runtime (auto-downloads a Temurin JRE if none is
# installed — see ensure-java.sh) and the jar (downloads the released fat jar
# if needed — see get-jar.sh), then runs `analyze` with the given config.
#
# Usage:
#   scripts/run-analysis.sh <config.yml> [extra analyze args...]
#   scripts/run-analysis.sh hotspot.yml --strict
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG="${1:?usage: run-analysis.sh <config.yml> [extra analyze args...]}"
shift

# C/POSIX makes the JVM's path charset US-ASCII, so a non-ASCII path throws
# InvalidPathException. Set this before the JVM starts, and only when the
# user has not already chosen a locale.
case "${LC_ALL:-${LANG:-}}" in
  ""|C|POSIX)
    export LANG=C.UTF-8
    export LC_ALL=C.UTF-8
    ;;
esac

JAVA="$("$HERE/ensure-java.sh")"
JAR="$("$HERE/get-jar.sh")"
exec "$JAVA" -jar "$JAR" analyze --config "$CONFIG" "$@"
