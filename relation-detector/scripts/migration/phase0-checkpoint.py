#!/usr/bin/env python3
"""Audit-safe conversion, inventory, manifest, and reuse checks for Phase 0 checkpoints."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys
import tempfile


CASE_ID = re.compile(r"[a-z0-9][a-z0-9._-]*")
FIXED_VIEWS = ("direct.json", "result.json")
EXPECTED_CATEGORIES = 19
HASH_CHUNK_BYTES = 1024 * 1024


class CheckpointError(RuntimeError):
    """A checkpoint input violated the audited filesystem contract."""


def lstat(path: Path):
    try:
        return path.lstat()
    except OSError as error:
        raise CheckpointError(f"checkpoint path is unavailable: {path}") from error


def require_directory(path: Path, label: str) -> None:
    if not stat.S_ISDIR(lstat(path).st_mode):
        raise CheckpointError(f"{label} must be a non-symlink directory: {path}")


def require_regular_file(path: Path, label: str) -> None:
    if not stat.S_ISREG(lstat(path).st_mode):
        raise CheckpointError(f"{label} must be a non-symlink regular file: {path}")


def open_nofollow(path: Path):
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(path, flags)
    except OSError as error:
        raise CheckpointError(f"checkpoint file could not be opened safely: {path}") from error
    resolved = os.fstat(descriptor)
    if not stat.S_ISREG(resolved.st_mode):
        os.close(descriptor)
        raise CheckpointError(f"checkpoint file is not regular: {path}")
    return descriptor, resolved


def stream_fingerprint(path: Path) -> tuple[str, int]:
    descriptor, before = open_nofollow(path)
    digest = hashlib.sha256()
    try:
        with os.fdopen(descriptor, "rb", closefd=True) as handle:
            while True:
                chunk = handle.read(HASH_CHUNK_BYTES)
                if not chunk:
                    break
                digest.update(chunk)
            after = os.fstat(handle.fileno())
    except OSError as error:
        raise CheckpointError(f"checkpoint file hashing failed: {path}") from error
    if before.st_size != after.st_size or before.st_mtime_ns != after.st_mtime_ns:
        raise CheckpointError(f"checkpoint file changed while hashing: {path}")
    return digest.hexdigest(), after.st_size


def load_json(path: Path):
    descriptor, _ = open_nofollow(path)
    try:
        with os.fdopen(descriptor, "r", encoding="utf-8", closefd=True) as handle:
            return json.load(handle)
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise CheckpointError(f"checkpoint JSON is invalid: {path}") from error


def sorted_entries(path: Path):
    try:
        with os.scandir(path) as entries:
            return sorted(entries, key=lambda entry: entry.name)
    except OSError as error:
        raise CheckpointError(f"checkpoint directory could not be listed: {path}") from error


def exact_result_inventory(results_root: Path) -> list[dict]:
    require_directory(results_root, "checkpoint results root")
    category_entries = sorted_entries(results_root)
    if len(category_entries) != EXPECTED_CATEGORIES:
        raise CheckpointError("checkpoint results must contain exactly 19 categories")
    inventory = []
    for category in category_entries:
        if not CASE_ID.fullmatch(category.name) or not category.is_dir(follow_symlinks=False):
            raise CheckpointError(f"invalid checkpoint category entry: {category.path}")
        category_path = Path(category.path)
        views = sorted_entries(category_path)
        if [entry.name for entry in views] != list(FIXED_VIEWS):
            raise CheckpointError(f"checkpoint category must contain only fixed views: {category.path}")
        for view in views:
            if not view.is_file(follow_symlinks=False):
                raise CheckpointError(f"checkpoint view must be a regular file: {view.path}")
            view_path = Path(view.path)
            sha256, size = stream_fingerprint(view_path)
            inventory.append({
                "path": f"{category.name}/{view.name}",
                "sha256": sha256,
                "bytes": size,
            })
    return inventory


def historical_mapping(source_root: Path, batch_report: Path):
    require_directory(source_root, "historical result root")
    require_regular_file(batch_report, "historical batch report")
    report = load_json(batch_report)
    raw_cases = report.get("cases") if isinstance(report, dict) else None
    if not isinstance(raw_cases, list):
        raise CheckpointError("historical batch report cases are required")
    case_ids = []
    for item in raw_cases:
        case_id = item.get("id") if isinstance(item, dict) else None
        if not isinstance(case_id, str) or not CASE_ID.fullmatch(case_id):
            raise CheckpointError("historical batch report contains an invalid case id")
        case_ids.append(case_id)
    if len(case_ids) != EXPECTED_CATEGORIES or len(set(case_ids)) != EXPECTED_CATEGORIES:
        raise CheckpointError("historical batch report must contain 19 unique cases")

    entries = sorted_entries(source_root)
    if any(not entry.is_file(follow_symlinks=False) for entry in entries):
        raise CheckpointError("historical result inventory must contain only regular flat files")
    files = {entry.name: Path(entry.path) for entry in entries}
    direct_names = {f"{case_id}.json" for case_id in case_ids}
    mapping = []
    used = set()
    for case_id in sorted(case_ids):
        direct_name = f"{case_id}.json"
        direct = files.get(direct_name)
        full_names = sorted(
            name for name in files
            if name not in direct_names and name.startswith(f"{case_id}-") and name.endswith(".json")
        )
        if direct is None or len(full_names) != 1:
            raise CheckpointError(f"historical result pair is incomplete or ambiguous: {case_id}")
        full_name = full_names[0]
        if direct_name in used or full_name in used:
            raise CheckpointError(f"historical result file was assigned more than once: {case_id}")
        used.update((direct_name, full_name))
        mapping.append((case_id, direct, files[full_name]))
    if used != set(files):
        raise CheckpointError("historical result inventory contains unassigned files")
    return mapping


def copy_stream(source: Path, destination: Path) -> None:
    descriptor, _ = open_nofollow(source)
    try:
        with os.fdopen(descriptor, "rb", closefd=True) as input_handle:
            with destination.open("xb") as output_handle:
                while True:
                    chunk = input_handle.read(HASH_CHUNK_BYTES)
                    if not chunk:
                        break
                    output_handle.write(chunk)
    except OSError as error:
        raise CheckpointError(f"historical result copy failed: {source}") from error


def clean_owned_stage(stage: Path, files: list[Path], directories: list[Path]) -> None:
    for path in reversed(files):
        try:
            path.unlink()
        except FileNotFoundError:
            pass
    for path in reversed(directories):
        try:
            path.rmdir()
        except FileNotFoundError:
            pass
    try:
        stage.rmdir()
    except FileNotFoundError:
        pass


def publish_result_pairs(mapping, destination: Path) -> None:
    if os.path.lexists(destination):
        raise CheckpointError(f"checkpoint conversion destination already exists: {destination}")
    require_directory(destination.parent, "checkpoint conversion parent")
    stage = Path(tempfile.mkdtemp(prefix=f".{destination.name}.staging-", dir=destination.parent))
    created_files = []
    created_directories = []
    try:
        for case_id, direct, full in mapping:
            category = stage / case_id
            category.mkdir()
            created_directories.append(category)
            direct_target = category / "direct.json"
            result_target = category / "result.json"
            created_files.append(direct_target)
            created_files.append(result_target)
            copy_stream(direct, direct_target)
            copy_stream(full, result_target)
        exact_result_inventory(stage)
        if os.path.lexists(destination):
            raise CheckpointError(f"checkpoint conversion destination appeared during conversion: {destination}")
        stage.rename(destination)
    except Exception:
        clean_owned_stage(stage, created_files, created_directories)
        raise


def convert_results(source_root: Path, batch_report: Path, destination: Path) -> None:
    publish_result_pairs(historical_mapping(source_root, batch_report), destination)


def import_results(source_root: Path, batch_report: Path, destination: Path) -> None:
    try:
        exact_result_inventory(source_root)
    except CheckpointError:
        mapping = historical_mapping(source_root, batch_report)
    else:
        mapping = [
            (category.name, Path(category.path) / "direct.json", Path(category.path) / "result.json")
            for category in sorted_entries(source_root)
        ]
    publish_result_pairs(mapping, destination)


def optional_json(path: Path):
    if not os.path.lexists(path):
        return {}
    require_regular_file(path, "checkpoint JSON artifact")
    return load_json(path)


def warning_total(path: Path) -> int:
    if not os.path.lexists(path):
        return 0
    require_regular_file(path, "checkpoint warning report")
    descriptor, _ = open_nofollow(path)
    with os.fdopen(descriptor, "r", encoding="utf-8", newline="", closefd=True) as handle:
        return sum(int(row.get("count") or 0) for row in csv.DictReader(handle, delimiter="\t"))


def parity_differences(path: Path) -> int:
    if not os.path.lexists(path):
        return 0
    require_regular_file(path, "checkpoint parity report")
    descriptor, _ = open_nofollow(path)
    with os.fdopen(descriptor, "r", encoding="utf-8", newline="", closefd=True) as handle:
        return sum(
            int(row.get("TokenOnly") or 0) + int(row.get("FullOnly") or 0)
            for row in csv.DictReader(handle, delimiter="\t")
        )


def artifact_inventory(destination: Path) -> list[dict]:
    artifacts = []
    for entry in sorted_entries(destination):
        if entry.name == "checkpoint-manifest.json" or not entry.is_file(follow_symlinks=False):
            continue
        sha256, size = stream_fingerprint(Path(entry.path))
        artifacts.append({"path": entry.name, "sha256": sha256, "bytes": size})
    return artifacts


def write_manifest(args) -> None:
    destination = args.destination
    require_directory(destination, "checkpoint destination")
    target = destination / "checkpoint-manifest.json"
    if os.path.lexists(target):
        raise CheckpointError(f"checkpoint manifest already exists: {target}")
    result_files = exact_result_inventory(destination / "results")
    parser_status = int(args.parser_status)
    acceptance_status = int(args.acceptance_status)
    manifest = {
        "label": args.label,
        "commit": args.commit,
        "status": (
            "PASS" if parser_status == 0 and acceptance_status == 0
            else "PARTIAL_HISTORICAL" if parser_status == 0
            else "FAIL"
        ),
        "acceptanceStatus": acceptance_status,
        "parserBaselineStatus": parser_status,
        "generatedReportFreshness": args.generated_report_gate,
        "correctness": optional_json(destination / "correctness-run-summary.json"),
        "parserCategories": EXPECTED_CATEGORIES,
        "jsonFiles": EXPECTED_CATEGORIES * len(FIXED_VIEWS),
        "resultFiles": result_files,
        "diagnostics": warning_total(destination / "warning-codes.tsv"),
        "observationParityDifferences": parity_differences(destination / "observation-parity.tsv"),
        "artifacts": artifact_inventory(destination),
    }
    temporary = destination / f".checkpoint-manifest.json.tmp-{os.getpid()}"
    try:
        with temporary.open("x", encoding="utf-8") as handle:
            json.dump(manifest, handle, indent=2, sort_keys=True)
            handle.write("\n")
        if os.path.lexists(target):
            raise CheckpointError(f"checkpoint manifest appeared while writing: {target}")
        temporary.rename(target)
    except Exception:
        if temporary.exists() and temporary.is_file() and not temporary.is_symlink():
            temporary.unlink()
        raise


def verify_reuse(destination: Path, commit: str) -> None:
    require_directory(destination, "checkpoint destination")
    manifest_path = destination / "checkpoint-manifest.json"
    require_regular_file(manifest_path, "checkpoint manifest")
    manifest = load_json(manifest_path)
    actual = exact_result_inventory(destination / "results")
    expected = manifest.get("resultFiles") if isinstance(manifest, dict) else None
    valid = (
        manifest.get("commit") == commit
        and manifest.get("parserBaselineStatus") == 0
        and manifest.get("parserCategories") == EXPECTED_CATEGORIES
        and manifest.get("jsonFiles") == EXPECTED_CATEGORIES * len(FIXED_VIEWS)
        and expected == actual
    )
    if not valid:
        raise CheckpointError("completed checkpoint failed audited reuse validation")


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser()
    commands = result.add_subparsers(dest="command", required=True)
    conversion = commands.add_parser("convert-results")
    conversion.add_argument("source_root", type=Path)
    conversion.add_argument("batch_report", type=Path)
    conversion.add_argument("destination", type=Path)
    importing = commands.add_parser("import-results")
    importing.add_argument("source_root", type=Path)
    importing.add_argument("batch_report", type=Path)
    importing.add_argument("destination", type=Path)
    manifest = commands.add_parser("write-manifest")
    manifest.add_argument("destination", type=Path)
    manifest.add_argument("label")
    manifest.add_argument("commit")
    manifest.add_argument("parser_status")
    manifest.add_argument("acceptance_status")
    manifest.add_argument("generated_report_gate")
    reuse = commands.add_parser("verify-reuse")
    reuse.add_argument("destination", type=Path)
    reuse.add_argument("commit")
    return result


def main() -> int:
    args = parser().parse_args()
    try:
        if args.command == "convert-results":
            convert_results(args.source_root, args.batch_report, args.destination)
        elif args.command == "import-results":
            import_results(args.source_root, args.batch_report, args.destination)
        elif args.command == "write-manifest":
            write_manifest(args)
        else:
            verify_reuse(args.destination, args.commit)
        return 0
    except (CheckpointError, OSError, ValueError) as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
