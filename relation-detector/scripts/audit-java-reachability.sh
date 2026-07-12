#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
search_roots=(
  "$root/contracts/src/main"
  "$root/core/src/main"
  "$root/cli/src/main"
  "$root/adaptor-mysql/src/main"
  "$root/adaptor-postgres/src/main"
  "$root/adaptor-oracle/src/main"
  "$root/adaptor-sqlserver/src/main"
)

printf 'type\tfile\n'
while IFS= read -r file; do
  name="$(basename "$file" .java)"
  [[ "$name" == "package-info" || "$name" == "module-info" ]] && continue
  # Model containers whose filename differs from their package-private top-level
  # types are intentionally excluded from this conservative report.
  rg -q "public[[:space:]]+((final|sealed|non-sealed)[[:space:]]+)?(class|interface|record|enum)[[:space:]]+$name\\b" "$file" \
    || continue
  references="$(rg -l --fixed-strings "$name" "$root" \
    --glob '!**/target/**' --glob '!**/.mvn/build-cache/**' | grep -v -F "$file" || true)"
  [[ -n "$references" ]] && continue
  printf '%s\t%s\n' "$name" "${file#"$root/"}"
done < <(find "${search_roots[@]}" -type f -name '*.java' | sort)
