#!/usr/bin/env python3
"""Validate the complete direct/derived sample-data parser result matrix."""

import argparse
import json
import re
from pathlib import Path


DERIVED_SUFFIX = "-derived-fresh"


def fail(message):
    raise SystemExit(message)


def count(summary, key, fallback):
    value = summary.get(key)
    return fallback if value is None else int(value)


def validate_counts(path, data):
    summary = data.get("summary") or {}
    relationships = data.get("relationships") or []
    derived_relationships = data.get("derivedRelationships") or []
    lineages = data.get("dataLineages") or []
    derived_lineages = data.get("derivedDataLineages") or []
    naming = data.get("namingEvidence") or []
    derived_naming = [item for item in naming if item.get("rule") == "TRANSITIVE_NAMING_PATH"]
    direct_naming = len(naming) - len(derived_naming)
    expected = {
        "directRelationshipCount": len(relationships),
        "derivedRelationshipCount": len(derived_relationships),
        "totalRelationshipCount": len(relationships) + len(derived_relationships),
        "directDataLineageCount": len(lineages),
        "derivedDataLineageCount": len(derived_lineages),
        "totalDataLineageCount": len(lineages) + len(derived_lineages),
        "directNamingEvidenceCount": direct_naming,
        "derivedNamingEvidenceCount": len(derived_naming),
        "totalNamingEvidenceCount": len(naming),
        "warningCount": len(data.get("warnings") or []),
    }
    for key, expected_value in expected.items():
        actual = count(summary, key, expected_value)
        if actual != expected_value:
            fail(f"{path}: summary.{key}={actual}, array-derived value={expected_value}")
    view = data.get("derivedNamingEvidence") or []
    if len(view) != len(derived_naming):
        fail(f"{path}: derivedNamingEvidence view has {len(view)}, expected {len(derived_naming)}")


def validate_naming_refs(path, data):
    naming_ids = {item.get("id") for item in data.get("namingEvidence") or []}
    for section in ("relationships", "derivedRelationships"):
        for fact in data.get(section) or []:
            for evidence in fact.get("evidence") or []:
                if evidence.get("type") != "NAMING_MATCH":
                    continue
                reference = evidence.get("evidenceRef") or (evidence.get("attributes") or {}).get("evidenceRef")
                if not reference or reference not in naming_ids:
                    fail(f"{path}: unresolved NAMING_MATCH evidenceRef {reference!r} in {section}")
                if evidence.get("rawEvidence"):
                    fail(f"{path}: relationship NAMING_MATCH must not duplicate rawEvidence")
    for item in data.get("derivedNamingEvidence") or []:
        if item.get("id") not in naming_ids:
            fail(f"{path}: derived naming view id {item.get('id')!r} is not in namingEvidence")


def validate_sources(path, value):
    if isinstance(value, dict):
        for key, child in value.items():
            if key in {"source", "sourceFile"} and isinstance(child, str) and child.startswith("/"):
                fail(f"{path}: absolute {key} is not portable: {child}")
            validate_sources(path, child)
    elif isinstance(value, list):
        for child in value:
            validate_sources(path, child)


def validate_source_locations(path, value, line_counts):
    if isinstance(value, dict):
        source_file = value.get("sourceFile")
        source_line = value.get("sourceLine")
        statement_id = value.get("sourceStatementId")
        if source_file and source_line is not None:
            source_path = Path(source_file)
            if not source_path.exists():
                fail(f"{path}: sourceFile does not exist: {source_file}")
            if source_path not in line_counts:
                with source_path.open(encoding="utf-8", errors="ignore") as handle:
                    line_counts[source_path] = sum(1 for _ in handle)
            line = int(source_line)
            if line < 1 or line > line_counts[source_path]:
                fail(f"{path}: sourceLine {line} is outside {source_file} "
                     f"(1-{line_counts[source_path]})")
            if statement_id:
                span = re.search(r":(\d+)-(\d+)$", str(statement_id))
                if span and not int(span.group(1)) <= line <= int(span.group(2)):
                    fail(f"{path}: sourceLine {line} is outside sourceStatementId {statement_id}")
        for child in value.values():
            validate_source_locations(path, child, line_counts)
    elif isinstance(value, list):
        for child in value:
            validate_source_locations(path, child, line_counts)


def validate_raw_observations(path, data):
    for section in ("relationships", "dataLineages", "namingEvidence",
                    "derivedRelationships", "derivedDataLineages"):
        for fact_index, fact in enumerate(data.get(section) or []):
            seen = set()
            for observation_index, observation in enumerate(fact.get("rawEvidence") or []):
                identity = json.dumps(observation, sort_keys=True, separators=(",", ":"))
                if identity in seen:
                    fail(f"{path}: duplicate raw observation in {section}[{fact_index}]"
                         f".rawEvidence[{observation_index}]")
                seen.add(identity)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("result_dir", type=Path)
    parser.add_argument("--expected-categories", type=int, default=19)
    args = parser.parse_args()
    paths = sorted(args.result_dir.glob("*.json"))
    expected_files = args.expected_categories * 2
    if len(paths) != expected_files:
        fail(f"Expected {expected_files} result JSON files, found {len(paths)} in {args.result_dir}")
    stems = {path.stem for path in paths}
    direct = sorted(stem for stem in stems if not stem.endswith(DERIVED_SUFFIX))
    if len(direct) != args.expected_categories:
        fail(f"Expected {args.expected_categories} direct parser categories, found {len(direct)}")
    for stem in direct:
        if stem + DERIVED_SUFFIX not in stems:
            fail(f"Missing derived result for {stem}")
    line_counts = {}
    for path in paths:
        data = json.loads(path.read_text(encoding="utf-8"))
        validate_counts(path, data)
        validate_naming_refs(path, data)
        validate_sources(path, data)
        validate_source_locations(path, data, line_counts)
        validate_raw_observations(path, data)
        if data.get("warnings"):
            fail(f"{path}: expected zero warnings, found {len(data['warnings'])}")
    print(f"sample-data parser results validated: {len(direct)} categories, {len(paths)} JSON files")


if __name__ == "__main__":
    main()
