#!/usr/bin/env python3
import argparse
import json
from pathlib import Path


def parse_args():
    parser = argparse.ArgumentParser(
        description="Aggregate isolated sample-data CLI batch reports."
    )
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--expected-case", action="append", default=[])
    parser.add_argument("reports", nargs="+", type=Path)
    return parser.parse_args()


def main():
    args = parse_args()
    expected = args.expected_case
    expected_set = set(expected)
    if len(expected) != len(expected_set):
        raise SystemExit("duplicate expected sample-data case")

    by_id = {}
    for report_path in args.reports:
        payload = json.loads(report_path.read_text(encoding="utf-8"))
        summary = payload.get("summary") or {}
        cases = payload.get("cases") or []
        if summary.get("caseCount") != len(cases):
            raise SystemExit(f"batch case count mismatch: {report_path}")
        if summary.get("failedCount", 0) or summary.get("skippedCount", 0):
            raise SystemExit(f"isolated sample-data group did not complete: {report_path}")
        for case in cases:
            case_id = case.get("id")
            if not case_id or case_id in by_id:
                raise SystemExit(f"duplicate or missing sample-data case id: {case_id}")
            if case.get("status") != "SUCCESS":
                raise SystemExit(f"sample-data case did not succeed: {case_id}")
            for field in ("output", "directOutput"):
                value = case.get(field)
                if value and not Path(value).is_file():
                    raise SystemExit(f"sample-data case {case_id} is missing {field}: {value}")
            by_id[case_id] = case

    actual_set = set(by_id)
    if actual_set != expected_set:
        missing = sorted(expected_set - actual_set)
        extra = sorted(actual_set - expected_set)
        raise SystemExit(f"sample-data case coverage mismatch: missing={missing}, extra={extra}")

    ordered = [by_id[case_id] for case_id in expected]
    output = {
        "summary": {
            "caseCount": len(ordered),
            "successCount": len(ordered),
            "failedCount": 0,
            "skippedCount": 0,
        },
        "cases": ordered,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
