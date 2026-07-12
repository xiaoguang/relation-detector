# Sample-Data Parser CLI Examples

This directory contains runnable CLI examples for scanning `sample-data` with every user-facing parser family:

- MySQL root `token-event`
- MySQL `full-grammar` profiles `mysql/5.7` and `mysql/8.0`
- PostgreSQL root `token-event`
- PostgreSQL `full-grammar` profiles `postgresql/16`, `postgresql/17`, `postgresql/18`
- Oracle root `token-event`
- Oracle `full-grammar` profiles `oracle/12c`, `oracle/19c`, `oracle/21c`, `oracle/26ai`
- SQL Server root `token-event`
- SQL Server `full-grammar` profiles `sqlserver/2016`, `sqlserver/2017`, `sqlserver/2019`, `sqlserver/2022`, `sqlserver/2025`
- Common portable SQL `token-event`

`common token-event` is a first-class portable parser category. The script runs the real CLI with `database.type: COMMON` against `sample-data/common-natural`, so the output goes through the same `ScanEngine`, naming evidence, lineage, derived path, and JSON writer path as dialect scans. It is still a portable benchmark, not a database-specific adaptor and not a substitute for natural MySQL/PostgreSQL/Oracle/SQL Server sample-data. Parser/correctness-only portable bodies live under `sample-data/common-parser-coverage` and remain covered by correctness fixtures; they are intentionally excluded from natural sample-data CLI statistics.

Run all sample-data parser scans:

```bash
bash relation-detector/test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh
```

Run one parser scan:

```bash
bash relation-detector/test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh mysql-v8_0-full
```

Show only the common portable benchmark row:

```bash
bash relation-detector/test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh common-token-event-sample-data
```

Outputs are written to:

```text
relation-detector/target/sample-data-parser-cli/configs/
relation-detector/target/sample-data-parser-cli/results/
relation-detector/target/sample-data-parser-cli/summary.tsv
relation-detector/target/sample-data-parser-cli/summary-with-derived.tsv
relation-detector/target/sample-data-parser-cli/warning-codes.tsv
relation-detector/target/sample-data-parser-cli/observation-parity.tsv
relation-detector/target/sample-data-parser-cli/observation-diffs/
```

By default the script also runs `*-derived-fresh` variants with `derivedPaths.enabled=true`.
Set `SAMPLE_DATA_PARSER_CLI_INCLUDE_DERIVED=false` to run only the direct parser outputs.
`summary.tsv` contains direct outputs only; `summary-with-derived.tsv` adds `DerRel`,
`DerLin`, and `DerName` for the derived-enabled outputs.

When derived output is enabled, each parser case is scanned once. The CLI writes the
direct-only view with `--direct-output` and the derived view with `--output`, so the
second JSON does not repeat parsing or source collection. The default runner uses up to
three independent parser cases at once and up to two independent statement/file tasks
inside each scan. Override those bounded settings when the host has less capacity:

```bash
SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM=2 \
SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM=1 \
  bash relation-detector/test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh
```

Case logs are retained under `relation-detector/target/sample-data-parser-cli/logs/`.
The compact summary columns mean `Rel` = direct relationships, `Lin` = direct lineage,
`Name` = direct naming evidence, and `Der*` = the corresponding derived counts.

When both sides of a same-asset parser pair are requested, the runner also compares
token-event and full-grammar semantic observations. The comparison includes fact
endpoints, relationship/flow/transform semantics, source object or file, statement,
block, line, and join/write mapping kind; parser implementation flags are ignored.
Any difference fails the run and is written to `observation-diffs/`.

Run the explicit serial-versus-parallel consistency check for all 38 JSON artifacts:

```bash
bash relation-detector/scripts/verify-sample-data-parser-concurrency.sh
```

It canonicalizes each JSON document after removing `generatedAt`; any relationship,
lineage, naming evidence, observation, or diagnostic difference fails the check.

The common benchmark output is:

```text
relation-detector/target/sample-data-parser-cli/results/common-token-event-sample-data.json
```
