#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

printf 'type\tfile\n'
while IFS= read -r file; do
  name="$(basename "$file" .java)"
  [[ "$name" == "package-info" || "$name" == "module-info" ]] && continue
  rg -q "public[[:space:]]+((final|sealed|non-sealed)[[:space:]]+)?(class|interface|record|enum)[[:space:]]+$name\\b" "$file" \
    || continue
  # SPI implementations, ServiceLoader declarations, and immutable model containers
  # are runtime entry points even when no ordinary Java source names them directly.
  if rg -q "(^|[[:space:]])(record|enum)[[:space:]]+$name\\b|implements[[:space:]].*DatabaseAdaptor|implements[[:space:]].*Collector" "$file"; then
    continue
  fi
  if rg -q --fixed-strings "$name" "$repo_root" --glob '**/src/main/resources/META-INF/services/*'; then
    continue
  fi
  references="$({ rg -l --fixed-strings "$name" "$repo_root" \
    --glob '!**/target/**' --glob '!**/.mvn/build-cache/**' --glob '!**/.git/**' || true; } \
    | while IFS= read -r reference; do
        [[ "$reference" == "$file" ]] || printf '%s\n' "$reference"
      done)"
  [[ -n "$references" ]] && continue
  printf '%s\t%s\n' "$name" "${file#"$repo_root/"}"
done < <(find "$repo_root/relation-detector" "$repo_root/semantic-layer" \
  -path '*/src/main/java/*.java' -type f \
  ! -path '*/target/*' ! -path '*/generated-*/*' | sort)
