#!/usr/bin/env python3
"""Synchronize parser comparison tables with the latest sample-data CLI summary."""

import argparse
from pathlib import Path
import sys


DISPLAY_NAMES = {
    "common-token-event-sample-data": "common token-event sample-data",
    "mysql-token-event-root": "MySQL token-event root sample-data",
    "mysql-v5_7-full": "MySQL full-grammar v5_7 sample-data",
    "mysql-v8_0-full": "MySQL full-grammar v8_0 sample-data",
    "postgres-token-event-root": "PostgreSQL token-event root sample-data",
    "postgres-v16-full": "PostgreSQL full-grammar v16 sample-data",
    "postgres-v17-full": "PostgreSQL full-grammar v17 sample-data",
    "postgres-v18-full": "PostgreSQL full-grammar v18 sample-data",
    "oracle-token-event-root": "Oracle token-event root sample-data",
    "oracle-v12c-full": "Oracle full-grammar v12c sample-data",
    "oracle-v19c-full": "Oracle full-grammar v19c sample-data",
    "oracle-v21c-full": "Oracle full-grammar v21c sample-data",
    "oracle-v26ai-full": "Oracle full-grammar v26ai sample-data",
    "sqlserver-token-event-root": "SQL Server token-event root sample-data",
    "sqlserver-v2016-full": "SQL Server full-grammar v2016 sample-data",
    "sqlserver-v2017-full": "SQL Server full-grammar v2017 sample-data",
    "sqlserver-v2019-full": "SQL Server full-grammar v2019 sample-data",
    "sqlserver-v2022-full": "SQL Server full-grammar v2022 sample-data",
    "sqlserver-v2025-full": "SQL Server full-grammar v2025 sample-data",
}


def parse_summary(path):
    rows = {}
    lines = path.read_text(encoding="utf-8").splitlines()
    if not lines or lines[0].split("\t")[:2] != ["Parser", "Fix"]:
        raise ValueError("sample-data summary header is missing or invalid")
    for line in lines[1:]:
        if not line.strip():
            continue
        values = line.split("\t")
        if len(values) != 10:
            raise ValueError("sample-data summary row must contain 10 columns: " + line)
        parser_id = values[0]
        display_name = DISPLAY_NAMES.get(parser_id)
        if display_name is None:
            raise ValueError("unknown parser category: " + parser_id)
        direct = "| {} | {} | {} | {} | {} | {} | {} |".format(
            display_name, *values[1:7])
        derived = "| {} | {} | {} | {} | {} | {} | {} | {} | {} | {} |".format(
            display_name, *values[1:10])
        rows[display_name] = (direct, derived)
    if set(rows) != set(DISPLAY_NAMES.values()):
        missing = sorted(set(DISPLAY_NAMES.values()) - set(rows))
        raise ValueError("sample-data summary is incomplete: " + ", ".join(missing))
    return rows


def synchronize(document, rows):
    lines = document.splitlines()
    direct_occurrences = {name: 0 for name in rows}
    derived_occurrences = {name: 0 for name in rows}
    for index, line in enumerate(lines):
        for display_name, generated in rows.items():
            if not line.startswith("| " + display_name + " |"):
                continue
            separators = line.count("|")
            if separators == 8:
                lines[index] = generated[0]
                direct_occurrences[display_name] += 1
            elif separators == 11:
                lines[index] = generated[1]
                derived_occurrences[display_name] += 1
    for display_name in rows:
        if direct_occurrences[display_name] != 1:
            raise ValueError("expected one direct table row for " + display_name)
        if derived_occurrences[display_name] != 1:
            raise ValueError("expected one derived table row for " + display_name)
    suffix = "\n" if document.endswith("\n") else ""
    return "\n".join(lines) + suffix


def main():
    repository_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--summary",
        type=Path,
        default=repository_root / "relation-detector/target/sample-data-parser-cli/summary-with-derived.tsv")
    parser.add_argument(
        "--document",
        type=Path,
        default=repository_root / "docs/parser-audit/parser-comparison-summary.md")
    parser.add_argument("--update", action="store_true")
    args = parser.parse_args()

    current = args.document.read_text(encoding="utf-8")
    generated = synchronize(current, parse_summary(args.summary))
    if args.update:
        args.document.write_text(generated, encoding="utf-8")
        print("parser comparison summary updated: {}".format(args.document))
        return 0
    if current != generated:
        print(
            "parser comparison summary is stale; refresh with "
            "relation-detector/scripts/sync-parser-comparison-summary.py --update",
            file=sys.stderr)
        return 1
    print("parser comparison summary is up to date")
    return 0


if __name__ == "__main__":
    sys.exit(main())
