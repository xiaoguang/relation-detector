#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
CHECK="$ROOT/relation-detector/scripts/audit/check-no-jls-bad-classes.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/check-jls-classes.XXXXXX")"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

mkdir -p "$TMP_DIR/root" "$TMP_DIR/bin"
cat >"$TMP_DIR/bin/javap" <<'SH'
#!/usr/bin/env bash
exit 0
SH
chmod +x "$TMP_DIR/bin/javap"

if PATH="$TMP_DIR/bin:$PATH" "$CHECK" "$TMP_DIR/root" >/dev/null 2>&1; then
  echo "empty javap output unexpectedly passed" >&2
  exit 1
fi

cat >"$TMP_DIR/bin/javap" <<'SH'
#!/usr/bin/env bash
class_name="${@: -1}"
printf 'public final class %s {}\n' "$class_name"
SH
chmod +x "$TMP_DIR/bin/javap"

if PATH="$TMP_DIR/bin:$PATH" "$CHECK" "$TMP_DIR/root" >/dev/null 2>&1; then
  echo "non-adaptor javap subtype unexpectedly passed" >&2
  exit 1
fi

cat >"$TMP_DIR/bin/javap" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"$JLS_TEST_JAVAP_CALLS"
class_name="${@: -1}"
printf 'public final class %s implements com.relationdetector.contracts.spi.DatabaseAdaptor {}\n' "$class_name"
SH
chmod +x "$TMP_DIR/bin/javap"

JLS_TEST_JAVAP_CALLS="$TMP_DIR/javap-calls.txt" \
PATH="$TMP_DIR/bin:$PATH" \
  "$CHECK" "$TMP_DIR/root"

[[ "$(wc -l <"$TMP_DIR/javap-calls.txt" | tr -d '[:space:]')" -eq 4 ]]
grep -q 'com.relationdetector.mysql.MySqlDatabaseAdaptor' "$TMP_DIR/javap-calls.txt"
grep -q 'com.relationdetector.postgres.PostgresDatabaseAdaptor' "$TMP_DIR/javap-calls.txt"
grep -q 'com.relationdetector.oracle.OracleDatabaseAdaptor' "$TMP_DIR/javap-calls.txt"
grep -q 'com.relationdetector.sqlserver.SqlServerDatabaseAdaptor' "$TMP_DIR/javap-calls.txt"

echo "JLS placeholder class checks passed"
