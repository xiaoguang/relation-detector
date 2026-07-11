#!/usr/bin/env python3
"""Validate the complete direct/derived sample-data parser result matrix."""

import argparse
import json
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
    for path in paths:
        data = json.loads(path.read_text(encoding="utf-8"))
        validate_counts(path, data)
        validate_naming_refs(path, data)
        validate_sources(path, data)
        if data.get("warnings"):
            fail(f"{path}: expected zero warnings, found {len(data['warnings'])}")
    print(f"sample-data parser results validated: {len(direct)} categories, {len(paths)} JSON files")


if __name__ == "__main__":
    main()
