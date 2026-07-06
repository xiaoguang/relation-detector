#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-$(pwd)}"

cd "$ROOT"

status=0

if command -v rg >/dev/null 2>&1; then
  bad_classes="$(rg -a -l 'Unresolved compilation problem' . \
    --glob '*/target/classes/**/*.class' \
    --glob '!**/target/test-classes/**' || true)"
else
  bad_classes="$(
    while IFS= read -r -d '' class_file; do
      if grep -a -q 'Unresolved compilation problem' "$class_file"; then
        printf '%s\n' "$class_file"
      fi
    done < <(find . -path '*/target/classes/*' -name '*.class' ! -path '*/target/test-classes/*' -print0)
  )"
fi
if [[ -n "$bad_classes" ]]; then
  cat >&2 <<'MSG'
Found Java Language Server / Eclipse placeholder class files in Maven target/classes.

These class files can throw "Unresolved compilation problems" at runtime or make
ServiceLoader report "not a subtype". Do not use this target/classes for CLI or
correctness runs. Clean and rebuild with Maven, then restart/clean the Java
Language Server workspace if the files come back.

Suggested cleanup:
  mvn clean -pl cli -am -DskipTests test-compile
  VS Code: Java: Clean Java Language Server Workspace

Files:
MSG
  printf '%s\n' "$bad_classes" >&2
  status=1
fi

classpath="relation-detector/contracts/target/classes:relation-detector/core/target/classes:relation-detector/adaptor-mysql/target/classes:relation-detector/adaptor-postgres/target/classes"
for adaptor in \
  com.relationdetector.mysql.MySqlDatabaseAdaptor \
  com.relationdetector.postgres.PostgresDatabaseAdaptor
do
  class_output="$(javap -classpath "$classpath" "$adaptor" 2>/dev/null || true)"
  if [[ -n "$class_output" ]] \
    && ! grep -q 'implements com.relationdetector.contracts.spi.DatabaseAdaptor' <<< "$class_output" \
    && ! grep -q 'extends com.relationdetector.contracts.spi.AbstractDatabaseAdaptor' <<< "$class_output"; then
    cat >&2 <<MSG

Adaptor class does not implement the public DatabaseAdaptor SPI in target/classes:
  $adaptor

This is usually another sign that target/classes contains IDE/JLS placeholder
bytecode or stale compiled output. Rebuild with Maven before running CLI scans.
MSG
    status=1
  fi
done

exit "$status"
