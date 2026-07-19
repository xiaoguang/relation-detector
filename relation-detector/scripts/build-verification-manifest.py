#!/usr/bin/env python3
"""Build and validate a self-describing relation-detector verification manifest."""

import argparse
import csv
import hashlib
import json
from pathlib import Path


DERIVED_SUFFIX = "-derived-fresh"


def fail(message):
    raise SystemExit(message)


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def endpoint_key(endpoint):
    if not isinstance(endpoint, dict):
        return str(endpoint)
    parts = [endpoint.get(name) for name in ("catalog", "schema", "table", "column")]
    return ".".join(str(part) for part in parts if part not in (None, ""))


def validate_integrity(result_paths):
    naming_refs_ok = True
    sources_ok = True
    source_lines_ok = True
    cycles_ok = True
    diagnostics = 0
    line_counts = {}

    def visit(value):
        nonlocal sources_ok, source_lines_ok
        if isinstance(value, dict):
            source_file = value.get("sourceFile")
            source_line = value.get("sourceLine")
            for key in ("source", "sourceFile"):
                child = value.get(key)
                if isinstance(child, str) and child.startswith("/"):
                    sources_ok = False
            if source_file and source_line is not None:
                source_path = Path(source_file)
                if not source_path.exists():
                    source_lines_ok = False
                else:
                    if source_path not in line_counts:
                        with source_path.open(encoding="utf-8", errors="ignore") as handle:
                            line_counts[source_path] = sum(1 for _ in handle)
                    line = int(source_line)
                    if not 1 <= line <= line_counts[source_path]:
                        source_lines_ok = False
            for child in value.values():
                visit(child)
        elif isinstance(value, list):
            for child in value:
                visit(child)

    for path in result_paths:
        data = json.loads(path.read_text(encoding="utf-8"))
        diagnostics += len(data.get("warnings") or [])
        naming_ids = {item.get("id") for item in data.get("namingEvidence") or []}
        for section in ("relationships", "derivedRelationships"):
            for fact in data.get(section) or []:
                for evidence in fact.get("evidence") or []:
                    if evidence.get("type") != "NAMING_MATCH":
                        continue
                    reference = evidence.get("evidenceRef") or (evidence.get("attributes") or {}).get("evidenceRef")
                    if not reference or reference not in naming_ids:
                        naming_refs_ok = False
        for section in ("derivedRelationships", "derivedDataLineages"):
            for fact in data.get(section) or []:
                keys = [endpoint_key(item) for item in fact.get("path") or []]
                for index, key in enumerate(keys):
                    if key in keys[: max(0, index - 1)]:
                        cycles_ok = False
        visit(data)
    return {
        "evidenceRefs": "PASS" if naming_refs_ok else "FAIL",
        "sourcePaths": "PASS" if sources_ok else "FAIL",
        "sourceLines": "PASS" if source_lines_ok else "FAIL",
        "derivedCycles": "PASS" if cycles_ok else "FAIL",
    }, diagnostics


def read_parity(path):
    difference_count = 0
    pairs = 0
    with path.open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle, delimiter="\t"):
            pairs += 1
            difference_count += int(row.get("TokenOnly") or 0) + int(row.get("FullOnly") or 0)
    return pairs, difference_count


def read_warning_total(path):
    total = 0
    with path.open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle, delimiter="\t"):
            total += int(row.get("count") or 0)
    return total


def artifact_entry(path, base):
    try:
        name = str(path.resolve().relative_to(base.resolve()))
    except ValueError:
        name = path.name
    return {"path": name, "sha256": sha256(path), "bytes": path.stat().st_size}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--verification-dir", type=Path, required=True)
    parser.add_argument("--results-dir", type=Path, required=True)
    parser.add_argument("--correctness-summary", type=Path, required=True)
    parser.add_argument("--observation-parity", type=Path, required=True)
    parser.add_argument("--warning-codes", type=Path, required=True)
    parser.add_argument("--fingerprints", type=Path, required=True)
    parser.add_argument("--semantic-fingerprints", type=Path, required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--branch", required=True)
    parser.add_argument("--origin-main", required=True)
    parser.add_argument("--worktree-clean", choices=("true", "false"), required=True)
    parser.add_argument("--maven-status", type=int, required=True)
    parser.add_argument("--no-cache-status", type=int)
    parser.add_argument("--expected-fixtures", type=int, default=1197)
    parser.add_argument("--expected-categories", type=int, default=19)
    parser.add_argument("--expected-json", type=int, default=38)
    parser.add_argument("--artifact", action="append", type=Path, default=[])
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    required = [
        args.correctness_summary,
        args.observation_parity,
        args.warning_codes,
        args.fingerprints,
        args.semantic_fingerprints,
    ]
    missing = [str(path) for path in required if not path.is_file()]
    if missing:
        fail("Missing verification artifacts: " + ", ".join(missing))

    result_paths = sorted(args.results_dir.glob("*.json"))
    direct = [path for path in result_paths if not path.stem.endswith(DERIVED_SUFFIX)]
    correctness = json.loads(args.correctness_summary.read_text(encoding="utf-8"))
    executed = int(correctness.get("executed") or 0)
    passed = int(correctness.get("passed") or 0)
    failed = int(correctness.get("failed") or 0)
    parity_pairs, parity_differences = read_parity(args.observation_parity)
    warning_total = read_warning_total(args.warning_codes)
    integrity, json_diagnostics = validate_integrity(result_paths)
    diagnostic_total = max(warning_total, json_diagnostics)

    errors = []
    if args.maven_status != 0:
        errors.append(f"acceptance Maven status is {args.maven_status}")
    if args.no_cache_status not in (None, 0):
        errors.append(f"no-cache Maven status is {args.no_cache_status}")
    if executed != args.expected_fixtures or passed != args.expected_fixtures or failed != 0:
        errors.append(f"correctness expected {args.expected_fixtures} passing fixtures, got executed={executed}, passed={passed}, failed={failed}")
    if len(direct) != args.expected_categories:
        errors.append(f"expected {args.expected_categories} parser categories, found {len(direct)}")
    if len(result_paths) != args.expected_json:
        errors.append(f"expected {args.expected_json} JSON files, found {len(result_paths)}")
    if parity_pairs != 4 or parity_differences != 0:
        errors.append(f"observation parity expected 4 clean pairs, got pairs={parity_pairs}, differences={parity_differences}")
    if diagnostic_total != 0:
        errors.append(f"expected zero diagnostics, found {diagnostic_total}")
    for name, status in integrity.items():
        if status != "PASS":
            errors.append(f"integrity check {name} failed")

    artifact_paths = []
    for path in required + args.artifact:
        if path not in artifact_paths:
            artifact_paths.append(path)
    missing_artifacts = [str(path) for path in artifact_paths if not path.is_file()]
    if missing_artifacts:
        fail("Missing hashed artifacts: " + ", ".join(missing_artifacts))
    artifacts = [artifact_entry(path, args.verification_dir) for path in artifact_paths]
    manifest = {
        "status": "PASS" if not errors else "FAIL",
        "commit": args.commit,
        "branch": args.branch,
        "originMain": args.origin_main,
        "worktreeClean": args.worktree_clean == "true",
        "maven": {"acceptanceStatus": args.maven_status, "noCacheStatus": args.no_cache_status},
        "correctness": correctness,
        "parserMatrix": {"categories": len(direct), "jsonFiles": len(result_paths)},
        "diagnostics": {"total": diagnostic_total},
        "observationParity": {"pairs": parity_pairs, "differenceCount": parity_differences},
        "integrity": integrity,
        "artifacts": sorted(artifacts, key=lambda item: item["path"]),
        "errors": errors,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if errors:
        fail("Verification manifest failed: " + "; ".join(errors))


if __name__ == "__main__":
    main()
