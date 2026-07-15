#!/usr/bin/env python3
"""Aggregate isolated correctness summaries and prove complete parser coverage."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--expected-category", action="append", default=[])
    parser.add_argument("summaries", nargs="+", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    expected = set(args.expected_category)
    if not expected:
        raise SystemExit("at least one --expected-category is required")

    discovered: int | None = None
    categories: dict[str, dict] = {}
    selected = executed = passed = failed = elapsed = 0

    for path in args.summaries:
        with path.open(encoding="utf-8") as handle:
            summary = json.load(handle)
        current_discovered = int(summary["discovered"])
        if discovered is None:
            discovered = current_discovered
        elif discovered != current_discovered:
            raise SystemExit(
                f"inconsistent discovered counts: {discovered} != {current_discovered} in {path}"
            )

        group_selected = int(summary["selected"])
        group_executed = int(summary["executed"])
        group_passed = int(summary["passed"])
        group_failed = int(summary["failed"])
        if group_selected != group_executed:
            raise SystemExit(f"selected/executed mismatch in {path}")
        if group_executed != group_passed or group_failed != 0:
            raise SystemExit(f"correctness failures in {path}")

        selected += group_selected
        executed += group_executed
        passed += group_passed
        failed += group_failed
        elapsed += int(summary.get("elapsedMillis", 0))

        for category in summary.get("dialectVersions", []):
            category_id = category["id"]
            if category_id in categories:
                raise SystemExit(f"duplicate parser category: {category_id}")
            categories[category_id] = category

    actual = set(categories)
    if actual != expected:
        missing = sorted(expected - actual)
        unexpected = sorted(actual - expected)
        raise SystemExit(f"parser category mismatch: missing={missing}, unexpected={unexpected}")
    if discovered is None or selected != discovered:
        raise SystemExit(f"fixture coverage mismatch: discovered={discovered}, selected={selected}")

    aggregate = {
        "profile": "full-isolated",
        "discovered": discovered,
        "selected": selected,
        "executed": executed,
        "passed": passed,
        "failed": failed,
        "elapsedMillis": elapsed,
        "dialectVersions": [categories[key] for key in sorted(categories)],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8") as handle:
        json.dump(aggregate, handle, indent=2, sort_keys=False)
        handle.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
