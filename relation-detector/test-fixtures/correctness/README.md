# Correctness Fixture Suite

This directory is the shared baseline for parser and relationship correctness.
Large SQL/DDL inputs and their expected relationship output should live here
instead of being embedded as long Java strings.

See `docs/guides/relation-detector/test-assets-map.md` for the full test directory map, Java test
classification, fixture classification, and parser primary acceptance matrix.

## Case Layout

Each case uses the same four files:

```text
manifest.yml
input.sql or input.ddl.sql
expected-relations.json
expected-diagnostics.json
```

`manifest.yml` declares the database dialect, parser target, source type,
and golden file paths. The manifest parser in
`CorrectnessFixtureRunnerTest` intentionally supports only this small flat
format so fixture behavior stays easy to review.

`expected-relations.json` stores normalized relationship fingerprints:

```text
RELATION_TYPE:source->target:EVIDENCE_TYPE[,EVIDENCE_TYPE...]
```

It also stores `forbiddenTables` for negative assertions such as CTE names,
derived-table aliases, system schemas, and truncated log tokens.

`expected-diagnostics.json` stores the fixture hash and warning code counts.
ANTLR relation output is the only correctness baseline; Simple/ANTLR comparison
diagnostics are no longer part of fixture acceptance.

## What Belongs Here

Move parser/relationship correctness scenarios here when the input is SQL,
DDL, database object text, SQL log text, or database DDL text and the output is
a relationship, evidence, warning, or parser comparison diagnostic.

Keep focused Java unit tests for config parsing, parser failure warning paths,
mock JDBC collectors, relationship merger internals, metadata enrichment, and
adaptor wiring. Those tests are better expressed as code because they validate
local control flow rather than large input-to-relation behavior.

## Golden Updates

Golden files must not be silently regenerated during normal tests. If a parser
change updates a fingerprint, warning, or diagnostic list, review the diff and
decide whether it is a desired capability change or a false positive.
