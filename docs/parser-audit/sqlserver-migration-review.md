# SQL Server Adaptor And MySQL 8.0 Migration Review

## 1. Scope

This document records the SQL Server adaptor, sample-data migration, and correctness state.

The current implementation has moved beyond the initial smoke slice: SQL Server now has a full ERP sample-data correctness surface aligned to the MySQL 8.0 sample-data file layout. Each SQL Server version directory contains 38 SQL files, and all 38 files are covered by both root token-event correctness and the corresponding versioned full-grammer correctness.

The supported profile matrix is:

| Item | Value |
| --- | --- |
| Root baseline | `test-fixtures/correctness/sqlserver` |
| Root sample source | `sample-data/sqlserver/2025` |
| Version profiles | `sqlserver/2016`, `sqlserver/2017`, `sqlserver/2019`, `sqlserver/2022`, `sqlserver/2025` |
| Version directories | `test-fixtures/correctness/sqlserver/v2016|v2017|v2019|v2022|v2025` |
| Sample data | `sample-data/sqlserver/2016|2017|2019|2022|2025` |

## 2. Parser And Lexer Source

SQL Server does not use an ad hoc grammar assembled from web snippets. The full-grammer source is pinned from `antlr/grammars-v4/sql/tsql`:

| Source | Decision |
| --- | --- |
| Grammar base | `antlr/grammars-v4/sql/tsql` |
| Pinned commit | `994628b6d261f5313b72e76039818549352684ce` |
| Version truth | Microsoft Learn T-SQL reference and compatibility-level documentation |
| Local strategy | Vendor a fixed grammar snapshot, then tighten version differences from official documentation |

The upstream grammar is community-maintained and is not treated as the final source of version truth. Microsoft documentation decides whether a syntax belongs to SQL Server 2016 / 2017 / 2019 / 2022 / 2025.

The SQL Server token-event grammar is separate from full-grammer. It is a compact fallback grammar owned by `adaptor-sqlserver/tokenevent`; it does not import, delegate to, or merge events from versioned full-grammer.

## 3. Current Implementation

Implemented:

| Area | Status |
| --- | --- |
| Maven module | `adaptor-sqlserver` added to the root build |
| CLI classpath | `relation-detector-adaptor-sqlserver` added as a CLI dependency |
| DatabaseAdaptor | `SqlServerDatabaseAdaptor` registered through Java SPI |
| token-event grammar | Compact `SqlServerRelationSql.g4` under `adaptor-sqlserver` |
| full-grammer grammar | Independent generated lexer/parser packages for `v2016|v2017|v2019|v2022|v2025` |
| SQL/DDL visitor | Shared `SqlServerParseTreeEventCollector` in `fullgrammer/common` |
| correctness | Root token-event and five versioned full-grammer fixture sets, each covering 38 sample-data files |
| asset hygiene | SQL Server sample-data and correctness inputs are scanned for MySQL/PostgreSQL/Oracle residue |

Current limitations:

| Area | Limitation |
| --- | --- |
| Version strictness | The five version grammars currently start from the same pinned T-SQL grammar snapshot. The sample-data SQL is written as a conservative SQL Server 2016-compatible subset so it can run across all five versions. Official version-only grammar pruning is still backlog. |
| Runtime validation | Correctness proves parser output, not live SQL Server execution. Runtime smoke against SQL Server instances remains future work. |
| JDBC collectors | metadata / object / profiling collectors are conservative placeholders. |

## 4. Full Sample-Data Migration

SQL Server sample-data now mirrors the MySQL 8.0 ERP sample-data file layout:

| Version | Files | SQL / DDL | Correctness coverage |
| --- | ---: | ---: | --- |
| 2016 | 38 | 32 / 6 | `test-fixtures/correctness/sqlserver/v2016` |
| 2017 | 38 | 32 / 6 | `test-fixtures/correctness/sqlserver/v2017` |
| 2019 | 38 | 32 / 6 | `test-fixtures/correctness/sqlserver/v2019` |
| 2022 | 38 | 32 / 6 | `test-fixtures/correctness/sqlserver/v2022` |
| 2025 | 38 | 32 / 6 | `test-fixtures/correctness/sqlserver/v2025` and root token-event |

The translated files cover the same ERP areas as the MySQL sample-data source: schema, indexes/views, triggers, procedures, functions, seed data, data-generation procedures, complex queries, enterprise extension cases, and deep ERP scenarios.

Translation rules used:

| MySQL / other dialect source | SQL Server rewrite |
| --- | --- |
| `AUTO_INCREMENT` | `IDENTITY` |
| backtick or PostgreSQL quoted identifiers | SQL Server-compatible identifiers |
| `LIMIT` | `TOP` or `OFFSET ... FETCH` |
| `ON DUPLICATE KEY UPDATE` | `MERGE` |
| MySQL routine body | `CREATE OR ALTER PROCEDURE` / T-SQL body |
| PostgreSQL / Oracle residue | removed; forbidden by hygiene tests |

The migration intentionally uses a SQL Server 2016-compatible T-SQL subset for shared ERP semantics. Later version-only SQL should be added as separate boundary fixtures, not mixed into the cross-version sample-data baseline.

## 5. Current Golden Statistics

Current SQL Server sample-data correctness output:

| Golden group | Fixtures | SQL / DDL | Relations | Lineage | NAMING_MATCH | Diagnostics |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| SQL Server root token-event | 38 | 32 / 6 | 498 | 250 | 380 | 0 |
| SQL Server full-grammer v2016 | 38 | 32 / 6 | 500 | 250 | 384 | 0 |
| SQL Server full-grammer v2017 | 38 | 32 / 6 | 500 | 250 | 384 | 0 |
| SQL Server full-grammer v2019 | 38 | 32 / 6 | 500 | 250 | 384 | 0 |
| SQL Server full-grammer v2022 | 38 | 32 / 6 | 500 | 250 | 384 | 0 |
| SQL Server full-grammer v2025 | 38 | 32 / 6 | 500 | 250 | 384 | 0 |

SQL Server sample-data now keeps natural ERP business SQL at a density comparable to the other dialects; the high-density JOIN/EXISTS/IN relation-probe corpus lives under semantic-equivalent benchmark. Root token-event and versioned full-grammer produce the same lineage count; full-grammer produces more relationship and `NAMING_MATCH` evidence because the generated T-SQL parser exposes richer DDL and predicate context than the compact token-event fallback grammar. The five versioned full-grammer profiles match because the sample-data baseline intentionally uses a SQL Server 2016-compatible T-SQL subset.

Cross-dialect sample-data comparison:

| Parser category | Fixtures | SQL / DDL | Relations | Lineage | NAMING_MATCH | Diagnostics |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| common token-event sample-data | 15 | 11 / 4 | 729 | 292 | 419 | 0 |
| MySQL token-event root sample-data | 38 | 32 / 6 | 349 | 240 | 238 | 0 |
| MySQL full-grammer v5_7 sample-data | 38 | 32 / 6 | 346 | 265 | 243 | 0 |
| MySQL full-grammer v8_0 sample-data | 38 | 32 / 6 | 397 | 254 | 245 | 0 |
| PostgreSQL token-event root sample-data | 38 | 32 / 6 | 368 | 205 | 240 | 0 |
| PostgreSQL full-grammer v16 sample-data | 38 | 32 / 6 | 371 | 206 | 241 | 0 |
| PostgreSQL full-grammer v17 sample-data | 38 | 32 / 6 | 371 | 206 | 241 | 0 |
| PostgreSQL full-grammer v18 sample-data | 38 | 32 / 6 | 370 | 205 | 241 | 0 |
| Oracle token-event root sample-data | 38 | 32 / 6 | 373 | 212 | 238 | 0 |
| Oracle full-grammer v12c sample-data | 38 | 32 / 6 | 375 | 217 | 239 | 0 |
| Oracle full-grammer v19c sample-data | 38 | 32 / 6 | 375 | 217 | 239 | 0 |
| Oracle full-grammer v21c sample-data | 38 | 32 / 6 | 375 | 217 | 239 | 0 |
| Oracle full-grammer v26ai sample-data | 38 | 32 / 6 | 375 | 217 | 239 | 0 |
| SQL Server token-event root sample-data | 38 | 32 / 6 | 498 | 250 | 380 | 0 |
| SQL Server full-grammer v2016 sample-data | 38 | 32 / 6 | 500 | 250 | 384 | 0 |
| SQL Server full-grammer v2017 sample-data | 38 | 32 / 6 | 500 | 250 | 384 | 0 |
| SQL Server full-grammer v2019 sample-data | 38 | 32 / 6 | 500 | 250 | 384 | 0 |
| SQL Server full-grammer v2022 sample-data | 38 | 32 / 6 | 500 | 250 | 384 | 0 |
| SQL Server full-grammer v2025 sample-data | 38 | 32 / 6 | 500 | 250 | 384 | 0 |

The earlier low-count SQL Server surface was a sample-data asset gap: the directories had 38 files but only a thin subset of the ERP semantics. The current SQL Server sample-data now carries full schema, FK, procedure, trigger, natural query, data-generation and analytic coverage. Remaining cross-dialect count differences are parser coverage and dialect expression differences, not missing SQL Server ERP sample-data coverage.

## 6. Parser Fixes Made

| Area | Fix |
| --- | --- |
| Dialect registration | Added `SqlDialect.SQLSERVER`, `DatabaseType.SQLSERVER` adaptor wiring, and ServiceLoader entries |
| Correctness runner | Added SQL Server adaptor construction in fixture execution |
| Update mode | Allowed missing expected JSON files during `-DupdateCorrectnessGold=true` bootstrap |
| T-SQL expression support | Added `Full_column_nameContext` support to full-grammer expression source extraction |
| Predicate relation support | Added parse-tree endpoint emission for T-SQL comparison predicates |
| DDL support | Added typed DDL event extraction for FK and index evidence |
| token-event routines | Added compact typed coverage for table-valued function and trigger object blocks |
| token-event update aliasing | Preserved resolved qualified target table in `UPDATE ... FROM` lineage |

No SQL structure recognition was implemented with scanner, token-span parsing, SQL regex, or table/column name special filters. Identifier normalization only happens after typed parse-tree contexts identify the syntactic role.

## 7. Review Result

No `REVIEW_NEEDED` item remains for the current SQL Server sample-data migration.

Remaining implementation backlog:

- Keep SQL Server sample-data as natural ERP SQL; add high-density relation probes only to semantic-equivalent benchmark scenarios.
- Add Microsoft-documented version-only positive/negative fixtures and encode version-specific grammar differences in `.g4`.
- Add runtime smoke against real SQL Server instances.
- Add metadata / object / profiler collectors.
