# Sample-Data Parser CLI Examples

This directory contains runnable CLI examples for scanning `sample-data` with every user-facing parser family:

- MySQL root `token-event`
- MySQL `full-grammer` profiles `mysql/5.7` and `mysql/8.0`
- PostgreSQL root `token-event`
- PostgreSQL `full-grammer` profiles `postgresql/16`, `postgresql/17`, `postgresql/18`
- Oracle root `token-event`
- Oracle `full-grammer` profiles `oracle/12c`, `oracle/19c`, `oracle/21c`, `oracle/26ai`
- SQL Server root `token-event`
- SQL Server `full-grammer` profiles `sqlserver/2016`, `sqlserver/2017`, `sqlserver/2019`, `sqlserver/2022`, `sqlserver/2025`
- Common portable SQL `token-event`

`common token-event` is a first-class portable parser category. The script runs the real CLI with `database.type: COMMON` against `sample-data/portable`, so the output goes through the same `ScanEngine`, naming evidence, lineage, derived path, and JSON writer path as dialect scans. It is still a portable benchmark, not a database-specific adaptor and not a substitute for natural MySQL/PostgreSQL/Oracle/SQL Server sample-data.

Run all sample-data parser scans:

```bash
bash test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh
```

Run one parser scan:

```bash
bash test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh mysql-v8_0-full
```

Show only the common portable benchmark row:

```bash
bash test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh common-token-event-sample-data
```

Outputs are written to:

```text
target/sample-data-parser-cli/configs/
target/sample-data-parser-cli/results/
target/sample-data-parser-cli/summary.tsv
target/sample-data-parser-cli/summary-with-derived.tsv
target/sample-data-parser-cli/warning-codes.tsv
```

By default the script also runs `*-derived-fresh` variants with `derivedPaths.enabled=true`.
Set `SAMPLE_DATA_PARSER_CLI_INCLUDE_DERIVED=false` to run only the direct parser outputs.
`summary.tsv` contains direct outputs only; `summary-with-derived.tsv` adds `DerRel`,
`DerLin`, and `DerName` for the derived-enabled outputs.

The common benchmark output is:

```text
target/sample-data-parser-cli/results/common-token-event-sample-data.json
```
