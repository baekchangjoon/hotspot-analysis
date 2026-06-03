#!/usr/bin/env bash
# Thin convenience wrapper around the hotspot-analysis CLI.
# Resolves the jar (downloads the released fat jar if needed — see get-jar.sh),
# then runs `analyze` with the given config.
#
# Usage:
#   scripts/run-analysis.sh <config.yml> [extra analyze args...]
#   scripts/run-analysis.sh hotspot.yml --strict
#
# Requires JDK 21+ on PATH to RUN the jar.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG="${1:?usage: run-analysis.sh <config.yml> [extra analyze args...]}"
shift

JAR="$("$HERE/get-jar.sh")"
exec java -jar "$JAR" analyze --config "$CONFIG" "$@"
