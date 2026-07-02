# SQL Server Adaptor And MySQL 8.0 Migration Review

## 1. Scope

This document records the initial SQL Server adaptor and correctness migration. The current implementation is an **initial smoke slice**, not a completed migration of every MySQL 8.0 correctness fixture.

The supported profile matrix is:

| Item | Value |
| --- | --- |
| Root baseline | `test-fixtures/correctness/sqlserver` |
| Version profiles | `sqlserver/2016`, `sqlserver/2017`, `sqlserver/2019`, `sqlserver/2022`, `sqlserver/2025` |
| Version directories | `test-fixtures/correctness/sqlserver/v2016|v2017|v2019|v2022|v2025` |
| Sample data | `sample-data/sqlserver/2016|2017|2019|2022|2025` |

## 2. Parser And Lexer Source

SQL Server does not use an ad hoc grammar assembled from web snippets. The initial grammar source is pinned from `antlr/grammars-v4/sql/tsql`:

| Source | Decision |
| --- | --- |
| Grammar base | `antlr/grammars-v4/sql/tsql` |
| Pinned commit | `994628b6d261f5313b72e76039818549352684ce` |
| Version truth | Microsoft Learn T-SQL reference and compatibility-level documentation |
| Local strategy | Vendor a fixed grammar snapshot, then tighten version differences from official documentation |

The upstream grammar is community-maintained and not treated as the final source of version truth. Microsoft documentation decides whether a syntax belongs to SQL Server 2016 / 2017 / 2019 / 2022 / 2025.

## 3. Current Implementation

Implemented:

| Area | Status |
| --- | --- |
| Maven module | `adaptor-sqlserver` added to the root build |
| CLI classpath | `relation-detector-adaptor-sqlserver` added as a CLI dependency |
| DatabaseAdaptor | `SqlServerDatabaseAdaptor` registered through Java SPI |
| token-event grammar | `SqlServerRelationSqlLexer.g4` / `SqlServerRelationSqlParser.g4` under `adaptor-sqlserver` |
| full-grammer grammar | Independent generated lexer/parser packages for `v2016|v2017|v2019|v2022|v2025` |
| SQL/DDL visitor | Shared `SqlServerParseTreeEventCollector` in `fullgrammer/common` |
| correctness | Root token-event and five versioned full-grammer smoke fixtures |

Intentional limitations:

| Area | Limitation |
| --- | --- |
| Version strictness | The five version grammars currently start from the same pinned T-SQL grammar snapshot; official version pruning is still backlog |
| MySQL 8.0 migration | Only the sales-fact smoke slice is migrated |
| Routine correctness | Procedure source exists in sample-data, but correctness currently uses the equivalent DML batch |
| JDBC collectors | metadata / object / profiling collectors are empty placeholders |

## 4. Migrated Smoke Slice

The first semantic-equivalent migration covers a compact ERP sales fact flow:

| Source concept | SQL Server asset |
| --- | --- |
| Customer master | `dbo.customers` |
| Sales order header | `dbo.orders` |
| Payment transaction | `dbo.payments` |
| Sales fact table | `dbo.sales_fact` |
| Fact rebuild | `INSERT INTO dbo.sales_fact ... SELECT ...` |
| Paid amount refresh | `UPDATE sf SET ... FROM dbo.sales_fact AS sf JOIN ...` |
| Payment merge | `MERGE INTO dbo.sales_fact ... USING (...) ... WHEN MATCHED THEN UPDATE` |

The DDL fixture produces four FK-like relationships from explicit FK/index evidence:

| Source | Target |
| --- | --- |
| `dbo.orders.customer_id` | `dbo.customers.customer_id` |
| `dbo.payments.order_id` | `dbo.orders.order_id` |
| `dbo.sales_fact.customer_id` | `dbo.customers.customer_id` |
| `dbo.sales_fact.order_id` | `dbo.orders.order_id` |

The DML fixture produces three SQL predicate relationships and six field lineage fingerprints:

| Output kind | Count | Notes |
| --- | ---: | --- |
| Relationship | 3 | SQL join / subquery predicates from typed parse-tree contexts |
| Lineage | 6 | `orders` and `payments` fields flowing into `sales_fact` |
| Diagnostics | 0 | No parse warning in the initial smoke fixtures |

## 5. Parser Fixes Made

| Area | Fix |
| --- | --- |
| Dialect registration | Added `SqlDialect.SQLSERVER`, `DatabaseType.SQLSERVER` adaptor wiring, and ServiceLoader entries |
| Correctness runner | Added SQL Server adaptor construction in fixture execution |
| Update mode | Allowed missing expected JSON files during `-DupdateCorrectnessGold=true` bootstrap |
| T-SQL expression support | Added `Full_column_nameContext` support to full-grammer expression source extraction |
| Predicate relation support | Added parse-tree endpoint emission for T-SQL comparison predicates |
| DDL support | Added typed DDL event extraction for FK and index evidence |

No SQL structure recognition was implemented with scanner, token-span parsing, SQL regex, or table/column name special filters. The only string-level processing in the SQL Server adaptor is identifier normalization after typed parse-tree contexts have already identified the syntactic role.

## 6. Review Result

No `REVIEW_NEEDED` item remains for the current smoke slice.

The remaining work is implementation backlog:

- Migrate all MySQL 8.0 correctness fixture families to SQL Server by semantic rewrite.
- Add version-boundary fixture families once Microsoft documentation-backed syntax differences are encoded in `.g4`.
- Add T-SQL procedure / trigger correctness with `OBJECT_BLOCKS`.
- Add SQL Server SQL asset hygiene tests.
- Add SQL Server semantic-equivalent benchmark rows alongside common / MySQL / PostgreSQL / Oracle.

