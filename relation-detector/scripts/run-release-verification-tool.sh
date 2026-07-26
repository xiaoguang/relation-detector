#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CLI_TARGET="$ROOT/relation-detector/cli/target"
MAIN_CLASS="com.relationdetector.cli.verification.ReleaseVerificationMain"
MAIN_FILE="$CLI_TARGET/classes/com/relationdetector/cli/verification/ReleaseVerificationMain.class"
CLASSPATH_FILE="$CLI_TARGET/release-verification.classpath"
HEAP="${RELATION_DETECTOR_VERIFICATION_HEAP:-512m}"
MVN_BIN="${RELATION_DETECTOR_VERIFICATION_MVN:-mvn}"

if ! [[ "$HEAP" =~ ^[1-9][0-9]*[mMgG]$ ]]; then
  echo "RELATION_DETECTOR_VERIFICATION_HEAP must be a positive heap such as 512m" >&2
  exit 2
fi

cd "$ROOT"
if [[ ! -f "$MAIN_FILE" ]]; then
  "$MVN_BIN" -q -pl relation-detector/cli -am \
    -Dmaven.test.skip=true compile
fi
if [[ ! -s "$CLASSPATH_FILE" ]]; then
  "$MVN_BIN" -q -pl relation-detector/cli \
    -Dmdep.outputFile="$CLASSPATH_FILE" \
    dependency:build-classpath
fi

cd "${RELATION_DETECTOR_VERIFICATION_WORKING_DIRECTORY:-$ROOT}"
exec java -Xms64m -Xmx"$HEAP" \
  -cp "$CLI_TARGET/classes:$(cat "$CLASSPATH_FILE")" \
  "$MAIN_CLASS" "$@"
