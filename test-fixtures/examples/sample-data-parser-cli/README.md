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

`common token-event` is a portable correctness/benchmark parser, not a CLI database adaptor. The summary includes a `common-token-event-sample-data` row from `test-fixtures/correctness/common/*sample-data*` so the portable benchmark is visible next to real CLI scans. It is a high-density portable benchmark, not a one-to-one 38-file natural dialect sample-data scan.

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
target/sample-data-parser-cli/warning-codes.tsv
```
