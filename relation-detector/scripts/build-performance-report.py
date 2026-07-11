#!/usr/bin/env python3
"""Summarize only build/test artifacts produced after a benchmark started."""

import argparse
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path


CLI_CASE = re.compile(r"case=(\S+)\s+elapsedSeconds=(\d+)\s+status=(\d+)")
REACTOR_LINE = re.compile(r"^\[INFO\]\s+(.+?)\s+\.{3,}\s+(SUCCESS|FAILURE)\s+\[\s*(.+?)\s*\]$")
FIXTURE_TIMING = re.compile(r"slow correctness fixture\s+(.+?)\s+(\d+)\s+ms$")


def recent_files(root, pattern, session_start):
    if root is None or not root.exists():
        return []
    return [
        path
        for path in root.rglob(pattern)
        if path.is_file() and path.stat().st_mtime >= session_start
    ]


def test_summary(root, session_start):
    suites = []
    totals = {"total": 0, "failures": 0, "errors": 0, "skipped": 0}
    for path in recent_files(root, "TEST-*.xml", session_start):
        try:
            suite = ET.parse(str(path)).getroot()
        except ET.ParseError:
            continue
        item = {
            "name": suite.attrib.get("name", path.stem),
            "seconds": float(suite.attrib.get("time", "0") or 0),
            "file": str(path),
        }
        suites.append(item)
        totals["total"] += int(suite.attrib.get("tests", "0") or 0)
        totals["failures"] += int(suite.attrib.get("failures", "0") or 0)
        totals["errors"] += int(suite.attrib.get("errors", "0") or 0)
        totals["skipped"] += int(suite.attrib.get("skipped", "0") or 0)
    totals["suiteCount"] = len(suites)
    totals["slowest"] = sorted(suites, key=lambda item: (-item["seconds"], item["name"]))[:20]
    return totals


def cli_cases(root, session_start):
    cases = []
    for path in recent_files(root, "*.log", session_start):
        text = path.read_text(encoding="utf-8", errors="replace")
        for name, elapsed, status in CLI_CASE.findall(text):
            cases.append({
                "name": name,
                "elapsedSeconds": int(elapsed),
                "status": int(status),
                "file": str(path),
            })
    return sorted(cases, key=lambda item: (-item["elapsedSeconds"], item["name"]))


def cli_batch(path):
    if path is None or not path.is_file():
        return {"summary": {}, "cases": []}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"summary": {}, "cases": []}
    cases = []
    for item in data.get("cases") or []:
        cases.append({
            "name": item.get("id", ""),
            "status": item.get("status", "UNKNOWN"),
            "elapsedMillis": int(item.get("elapsedMillis", 0) or 0),
        })
    cases.sort(key=lambda item: (-item["elapsedMillis"], item["name"]))
    return {
        "summary": data.get("summary") or {},
        "cases": cases,
        "file": str(path),
    }


def json_document(path):
    if path is None or not path.is_file():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}


def canonical_fingerprints(path):
    items = []
    if path is not None and path.is_file():
        for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
            if not raw.strip() or "\t" not in raw:
                continue
            digest, source = raw.split("\t", 1)
            items.append({"name": Path(source).name, "sha256": digest})
    items.sort(key=lambda item: item["name"])
    return {"count": len(items), "items": items}


def maven_summary(logs):
    modules = []
    grammar_count = 0
    for path in logs:
        if not path.exists():
            continue
        for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
            line = re.sub(r"\x1b\[[0-9;]*m", "", raw).rstrip()
            if "Processing grammar:" in line:
                grammar_count += 1
            match = REACTOR_LINE.match(line)
            if match:
                modules.append({"name": match.group(1).strip(), "status": match.group(2), "elapsed": match.group(3)})
    return {"antlrGrammarProcessCount": grammar_count, "modules": modules}


def fixture_timings(logs):
    fixtures = []
    for path in logs:
        if not path.exists():
            continue
        for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
            match = FIXTURE_TIMING.search(raw.rstrip())
            if match:
                fixtures.append({
                    "manifest": match.group(1),
                    "elapsedMillis": int(match.group(2)),
                    "file": str(path),
                })
    return sorted(fixtures, key=lambda item: (-item["elapsedMillis"], item["manifest"]))[:20]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--session-start", type=float, required=True)
    parser.add_argument("--surefire-root", type=Path)
    parser.add_argument("--cli-log-root", type=Path)
    parser.add_argument("--cli-report", type=Path)
    parser.add_argument("--correctness-summary", type=Path)
    parser.add_argument("--fingerprints", type=Path)
    parser.add_argument("--maven-log", action="append", default=[], type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    maven_logs = list(dict.fromkeys(args.maven_log))
    report = {
        "sessionStartEpoch": args.session_start,
        "tests": test_summary(args.surefire_root, args.session_start),
        "fixtures": {"slowest": fixture_timings(maven_logs)},
        "cliCases": cli_cases(args.cli_log_root, args.session_start),
        "cliBatch": cli_batch(args.cli_report),
        "correctness": json_document(args.correctness_summary),
        "canonicalFingerprints": canonical_fingerprints(args.fingerprints),
        "maven": maven_summary(maven_logs),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
