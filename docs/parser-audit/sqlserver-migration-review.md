# SQL Server Adaptor And MySQL 8.0 Migration Review

## 1. Scope

This document records the SQL Server adaptor, sample-data migration, and correctness state.

The current implementation has moved beyond the initial smoke slice: SQL Server now has a full ERP sample-data correctness surface aligned to the MySQL 8.0 sample-data file layout. Each SQL Server version directory contains 38 SQL files, and all 38 files are covered by both root token-event correctness and the corresponding versioned full-grammar correctness.

The supported profile matrix is:

| Item | Value |
| --- | --- |
| Root baseline | `test-fixtures/correctness/sqlserver` |
| Root sample source | `sample-data/sqlserver/2025` |
| Version profiles | `sqlserver/2016`, `sqlserver/2017`, `sqlserver/2019`, `sqlserver/2022`, `sqlserver/2025` |
| Version directories | `test-fixtures/correctness/sqlserver/v2016|v2017|v2019|v2022|v2025` |
| Sample data | `sample-data/sqlserver/2016|2017|2019|2022|2025` |

## 2. Parser And Lexer Source

SQL Server does not use an ad hoc grammar assembled from web snippets. The full-grammar source is pinned from `antlr/grammars-v4/sql/tsql`:

| Source | Decision |
| --- | --- |
| Grammar base | `antlr/grammars-v4/sql/tsql` |
| Pinned commit | `994628b6d261f5313b72e76039818549352684ce` |
| Version truth | Microsoft Learn T-SQL reference and compatibility-level documentation |
| Local strategy | Vendor a fixed grammar snapshot, then tighten version differences from official documentation |

The upstream grammar is community-maintained and is not treated as the final source of version truth. Microsoft documentation decides whether a syntax belongs to SQL Server 2016 / 2017 / 2019 / 2022 / 2025.

The SQL Server token-event grammar is separate from full-grammar. It is a compact fallback grammar owned by `adaptor-sqlserver/tokenevent`; it does not import, delegate to, or merge events from versioned full-grammar.

## 3. Current Implementation

Implemented:

| Area | Status |
| --- | --- |
| Maven module | `adaptor-sqlserver` added to the root build |
| CLI classpath | `relation-detector-adaptor-sqlserver` added as a CLI dependency |
| DatabaseAdaptor | `SqlServerDatabaseAdaptor` registered through Java SPI |
| token-event grammar | Compact `SqlServerRelationSql.g4` under `adaptor-sqlserver` |
| full-grammar grammar | Independent generated lexer/parser packages for `v2016|v2017|v2019|v2022|v2025` |
| SQL/DDL visitor | Shared `SqlServerParseTreeEventCollector` in `fullgrammar/common` |
| correctness | Root token-event and five versioned full-grammar fixture sets, each covering 38 sample-data files |
| asset hygiene | SQL Server sample-data and correctness inputs are scanned for MySQL/PostgreSQL/Oracle residue |

Current limitations:

| Area | Limitation |
| --- | --- |
| Version strictness | The five version grammars start from the same pinned T-SQL grammar snapshot, but the first Microsoft-documented version boundaries are now encoded in `.g4`: 2017 `STRING_AGG`, 2022 `DATETRUNC` / `GENERATE_SERIES`, and 2025 `VECTOR(...)`. Broader official T-SQL family coverage remains incremental backlog. |
| Runtime validation | Correctness proves parser output, not live SQL Server execution. Runtime smoke against SQL Server instances remains future work. |
| JDBC collectors | Metadata, object, database-DDL, and bounded profiling collectors are implemented and capability-gated. Contract tests prove callable/partial-success behavior, not live-server completeness. Database-DDL still needs connection-catalog propagation and ordinal-safe composite-FK reconstruction; runtime permission/version smoke remains pending. |

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

Version-only fixtures currently added outside the ERP sample-data baseline:

| Fixture | Profile | SQL feature | Expected semantic output |
| --- | --- | --- | --- |
| `sqlserver2017-version-string-agg-sql` | `sqlserver/2017` | `STRING_AGG(...) WITHIN GROUP (...)` | One SQL join relationship: `sales_orders.customer_id -> customers.id`; no lineage. |
| `sqlserver2022-version-datetrunc-generate-series-sql` | `sqlserver/2022` | `DATETRUNC(...)` and `GENERATE_SERIES(...)` rowset function | One physical SQL join relationship: `sales_orders.customer_id -> customers.id`; generated-series rowset is not emitted as a physical table relation; no lineage. |
| `sqlserver2025-version-vector-ddl` | `sqlserver/2025` | `VECTOR(...)` data type | One DDL FK relationship: `product_embeddings.product_id -> products.id`; no lineage. |

## 5. Current Golden And CLI Statistics

Current SQL Server per-fixture correctness output is still tracked by the correctness golden files. This is a per-fixture sum, not the same as the merged scan-level CLI count:

| Golden group | Fixtures | SQL / DDL | Relations | Lineage | Diagnostics | Rel NAMING_MATCH | Top-level namingEvidence |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| SQL Server root token-event | 38 | 32 / 6 | 499 | 389 | 0 | 144 | 210 |
| SQL Server full-grammar v2016 | 39 | 33 / 6 | 795 | 389 | 0 | 385 | 451 |
| SQL Server full-grammar v2017 | 40 | 34 / 6 | 796 | 389 | 0 | 386 | 452 |
| SQL Server full-grammar v2019 | 39 | 33 / 6 | 795 | 389 | 0 | 385 | 451 |
| SQL Server full-grammar v2022 | 40 | 34 / 6 | 796 | 389 | 0 | 386 | 452 |
| SQL Server full-grammar v2025 | 40 | 33 / 7 | 796 | 389 | 0 | 385 | 451 |

SQL Server sample-data keeps natural ERP business SQL at a density comparable to the other dialects; high-density JOIN/EXISTS/IN relation probes live under semantic-equivalent benchmark. Per-fixture correctness totals differ because versioned directories include profile and version-boundary fixtures. On the same 38-file natural sample-data scan, root token-event and all five full profiles have equal facts and semantic observations.

Current merged SQL Server sample-data CLI comparison; the complete cross-dialect table is maintained only in [`parser-comparison-summary.md`](parser-comparison-summary.md):

| Parser category | Fixtures | SQL / DDL | Relations | Lineage | NAMING_MATCH | Diagnostics |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| SQL Server token-event root sample-data | 38 | 32 / 6 | 342 | 756 | 246 | 0 |
| SQL Server full-grammar v2016 sample-data | 38 | 32 / 6 | 342 | 756 | 246 | 0 |
| SQL Server full-grammar v2017 sample-data | 38 | 32 / 6 | 342 | 756 | 246 | 0 |
| SQL Server full-grammar v2019 sample-data | 38 | 32 / 6 | 342 | 756 | 246 | 0 |
| SQL Server full-grammar v2022 sample-data | 38 | 32 / 6 | 342 | 756 | 246 | 0 |
| SQL Server full-grammar v2025 sample-data | 38 | 32 / 6 | 342 | 756 | 246 | 0 |

The earlier low-count SQL Server surface was a sample-data asset gap: the directories had 38 files but only a thin subset of the ERP semantics. The current SQL Server sample-data now carries full schema, FK, procedure, trigger, natural query, data-generation and analytic coverage. Remaining cross-dialect count differences are parser coverage and dialect expression differences, not missing SQL Server ERP sample-data coverage.

Current output audit note: the former full-only `period_code = CONVERT(...order_date...)` relationship was a false positive and has been removed. Direct equality now requires typed physical columns, or a projection alias proven to be a single direct-column trace. Trigger predicates use `TRIGGER_REFERENCE`; SQL Server FK targets no longer manufacture `REFERENCED_KEY` uniqueness evidence.

## 6. Parser Fixes Made

| Area | Fix |
| --- | --- |
| Dialect registration | Added `SqlDialect.SQLSERVER`, `DatabaseType.SQLSERVER` adaptor wiring, and ServiceLoader entries |
| Correctness runner | Added SQL Server adaptor construction in fixture execution |
| Update mode | Allowed missing expected JSON files during `-DupdateCorrectnessGold=true` bootstrap |
| T-SQL expression support | Added `Full_column_nameContext` support to full-grammar expression source extraction |
| Predicate relation support | Added parse-tree endpoint emission for T-SQL comparison predicates |
| DDL support | Added typed DDL event extraction for FK and index evidence |
| token-event routines | Added compact typed coverage for table-valued function and trigger object blocks |
| token-event update aliasing | Preserved resolved qualified target table in `UPDATE ... FROM` lineage |

No SQL structure recognition was implemented with scanner, token-span parsing, SQL regex, or table/column name special filters. Identifier normalization only happens after typed parse-tree contexts identify the syntactic role.

## 7. Review Result

No `REVIEW_NEEDED` item remains for the current SQL Server sample-data migration.

Remaining implementation backlog:

- Keep SQL Server sample-data as natural ERP SQL; add high-density relation probes only to semantic-equivalent benchmark scenarios.
- Continue adding Microsoft-documented version-only positive/negative fixtures and encode each version-specific difference in `.g4`; do not use Java blacklist checks for version boundaries.
- Add runtime smoke against real SQL Server instances.
- Add metadata / object / profiler collectors.
