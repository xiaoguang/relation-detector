#!/usr/bin/env bash
set -euo pipefail

# CN: 基于已编译production bytecode构建jdeps类图，从CLI/verification main、ServiceLoader和contracts/SPI
# 根执行可达性遍历；只报告手写production类，能识别互相引用但没有入口的死循环，不修改源码。
# EN: Builds a jdeps production-class graph and traverses from CLI/verification mains, ServiceLoader providers,
# and contracts/SPI roots. It reports handwritten production classes, including unreachable cycles, without editing.

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
check=false
if [[ "${1:-}" == "--check" ]]; then
  check=true
  shift
fi
if [[ "$#" -ne 0 ]]; then
  echo "usage: $0 [--check]" >&2
  exit 2
fi
command -v jdeps >/dev/null 2>&1 || {
  echo "jdeps is required for production reachability audit" >&2
  exit 2
}

work="$(mktemp -d "${TMPDIR:-/tmp}/relation-detector-reachability.XXXXXX")"
cleanup() { rm -rf "$work"; }
trap cleanup EXIT INT TERM

find "$repo_root/relation-detector" "$repo_root/semantic-layer" \
  -path '*/target/classes' -type d -print | sort >"$work/class-dirs"
if [[ ! -s "$work/class-dirs" ]]; then
  echo "compiled production classes are required; run Maven test-compile first" >&2
  exit 2
fi

: >"$work/sources"
while IFS= read -r source; do
  name="$(basename "$source" .java)"
  [[ "$name" == "package-info" || "$name" == "module-info" ]] && continue
  if rg -q 'Generated from |@Generated\(|@javax\.annotation\.Generated|@jakarta\.annotation\.Generated' "$source"; then
    continue
  fi
  package_name="$(sed -n 's/^[[:space:]]*package[[:space:]]\([^;]*\);.*/\1/p' "$source" | head -1)"
  [[ -n "$package_name" ]] || continue
  printf '%s\t%s\n' "$package_name.$name" "${source#"$repo_root/"}" >>"$work/sources"
done < <(find "$repo_root/relation-detector" "$repo_root/semantic-layer" \
  -path '*/src/main/java/*.java' -type f ! -path '*/target/*' | sort)
sort -u "$work/sources" -o "$work/sources"

# Generated parser classes are traversal nodes, not deletion candidates. Their
# bytecode carries the only dependency edge to handwritten lexer/parser bases.
# Keeping them out of the graph would falsely mark required grammar support as
# unreachable; keeping them out of `sources` still prevents generated code from
# appearing in the audit result.
cp "$work/sources" "$work/graph-nodes"
while IFS= read -r source; do
  name="$(basename "$source" .java)"
  [[ "$name" == "package-info" || "$name" == "module-info" ]] && continue
  package_name="$(sed -n 's/^[[:space:]]*package[[:space:]]\([^;]*\);.*/\1/p' "$source" | head -1)"
  [[ -n "$package_name" ]] || continue
  printf '%s\t%s\n' "$package_name.$name" "${source#"$repo_root/"}" >>"$work/graph-nodes"
done < <(find "$repo_root/relation-detector" "$repo_root/semantic-layer" \
  -path '*/target/generated-sources/*' -name '*.java' -type f | sort)
sort -u "$work/graph-nodes" -o "$work/graph-nodes"

: >"$work/analysis-class-dirs"
cut -f2 "$work/sources" | while IFS= read -r source; do
  module="${source%%/src/main/java/*}"
  directory="$repo_root/$module/target/classes"
  [[ -d "$directory" ]] && printf '%s\n' "$directory"
done | sort -u >"$work/analysis-class-dirs"

: >"$work/roots"
cut -f1 "$work/sources" | awk '/^com\.relationdetector\.contracts\./' >>"$work/roots"
while IFS=$'\t' read -r type source; do
  if rg -q 'static[[:space:]]+void[[:space:]]+main[[:space:]]*\(' "$repo_root/$source"; then
    printf '%s\n' "$type" >>"$work/roots"
  fi
done <"$work/sources"
while IFS= read -r service_file; do
  sed -e 's/#.*//' -e '/^[[:space:]]*$/d' -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' \
    "$service_file" >>"$work/roots"
done < <(find "$repo_root/relation-detector" "$repo_root/semantic-layer" \
  -path '*/src/main/resources/META-INF/services/*' -type f | sort)
sort -u "$work/roots" -o "$work/roots"

classpath="$(paste -sd: "$work/class-dirs")"
: >"$work/jdeps"
# target/classes has the same archive label in every Maven module. Passing all
# directories to one jdeps invocation collapses those labels and loses edges.
# Analyze one module at a time against the complete reactor classpath, then merge.
while IFS= read -r directory; do
  jdeps -q -recursive -verbose:class -filter:none --class-path "$classpath" "$directory" \
    2>/dev/null >>"$work/jdeps" || true
done <"$work/analysis-class-dirs"

awk '
  NR==FNR { known[$1]=1; next }
  /^[[:space:]]+com\.relationdetector\./ {
    source=$1; target=$3
    sub(/\$.*/, "", source); sub(/\$.*/, "", target)
    if (known[source] && known[target]) print source "\t" target
  }
' "$work/graph-nodes" "$work/jdeps" | sort -u >"$work/edges"

awk -F '\t' '
  FILENAME==ARGV[1] { root[$1]=1; next }
  FILENAME==ARGV[2] {
    degree[$1]++
    edge[$1 SUBSEP degree[$1]]=$2
    next
  }
  END {
    head=1; tail=0
    for (node in root) { if (!seen[node]++) queue[++tail]=node }
    while (head <= tail) {
      node=queue[head++]
      for (i=1; i<=degree[node]; i++) {
        target=edge[node SUBSEP i]
        if (!seen[target]++) queue[++tail]=target
      }
    }
    for (node in seen) print node
  }
' "$work/roots" "$work/edges" | sort -u >"$work/reachable"

printf 'type\tfile\n'
awk -F '\t' '
  NR==FNR { reachable[$1]=1; next }
  !reachable[$1] { print $1 "\t" $2 }
' "$work/reachable" "$work/sources" | sort >"$work/unreachable"

printf 'reachability audit: sources=%s roots=%s edges=%s reachable=%s unreachable=%s\n' \
  "$(wc -l <"$work/sources" | tr -d ' ')" \
  "$(wc -l <"$work/roots" | tr -d ' ')" \
  "$(wc -l <"$work/edges" | tr -d ' ')" \
  "$(wc -l <"$work/reachable" | tr -d ' ')" \
  "$(wc -l <"$work/unreachable" | tr -d ' ')" >&2
cat "$work/unreachable"

if $check && [[ -s "$work/unreachable" ]]; then
  echo "unreachable handwritten production classes found" >&2
  exit 1
fi
