#!/usr/bin/env bash
# Thin convenience wrapper around the hotspot-analysis CLI.
# Builds the jar if it is missing, then runs `analyze` with the given config.
#
# Usage:
#   scripts/run-analysis.sh <config.yml> [extra analyze args...]
#   scripts/run-analysis.sh hotspot.yml --strict
#
# Requires JDK 21+ on PATH to RUN the jar (Gradle auto-provisions 21 to build it).
set -euo pipefail

# Repo root = three levels up from this script (skills/hotspot-analysis/scripts/).
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
JAR="$ROOT/build/libs/hotspot-0.1.0-SNAPSHOT.jar"

CONFIG="${1:?usage: run-analysis.sh <config.yml> [extra analyze args...]}"
shift

if [ ! -f "$JAR" ]; then
  echo "jar not found, building: $JAR" >&2
  (cd "$ROOT" && ./gradlew clean build -q)
fi

exec java -jar "$JAR" analyze --config "$CONFIG" "$@"
