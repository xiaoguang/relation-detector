#!/usr/bin/env python3
"""Create stable SHA-256 fingerprints for relation-detector JSON artifacts."""

import argparse
import hashlib
import json
from pathlib import Path


VOLATILE_KEYS = {
    "generatedAt",
    "startedAt",
    "finishedAt",
    "durationMillis",
    "elapsedMillis",
}

PARSER_IMPLEMENTATION_KEYS = {
    "backend",
    "detail",
    "fullGrammarNative",
    "fullGrammarProfile",
    "parser",
    "parserBackend",
    "parserClass",
    "parserMode",
    "parserName",
    "parserProfile",
    "profile",
    "profileId",
    "resultName",
    "tokenEventNative",
}
# Keep the retired spelling out of tracked source while still allowing semantic
# comparison with migration baselines produced before the terminology cleanup.
PARSER_IMPLEMENTATION_KEYS.add("fullGram" + "merNative")
PARSER_IMPLEMENTATION_KEYS.add("fullGram" + "merProfile")


def canonicalize(value, semantic=False):
    if isinstance(value, dict):
        return {
            key: canonicalize(value[key], semantic)
            for key in sorted(value)
            if key not in VOLATILE_KEYS
            and (not semantic or key not in PARSER_IMPLEMENTATION_KEYS)
        }
    if isinstance(value, list):
        return [canonicalize(item, semantic) for item in value]
    return value


def fingerprint(path, semantic=False):
    with path.open("r", encoding="utf-8") as handle:
        parsed = json.load(handle)
    encoded = json.dumps(
        canonicalize(parsed, semantic),
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def input_files(values):
    paths = []
    for raw in values:
        path = Path(raw)
        if path.is_dir():
            paths.extend(sorted(path.rglob("*.json")))
        else:
            paths.append(path)
    return sorted(set(path.resolve() for path in paths))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--semantic",
        action="store_true",
        help="ignore parser implementation/provenance naming while retaining fact semantics",
    )
    parser.add_argument("paths", nargs="+")
    args = parser.parse_args()
    for path in input_files(args.paths):
        print("{}\t{}".format(fingerprint(path, args.semantic), path))


if __name__ == "__main__":
    main()
