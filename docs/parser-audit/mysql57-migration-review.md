# MySQL 5.7 Full-Grammer Migration Review

## 1. Scope

This review records the MySQL 5.7 full-grammer migration. The user-facing `mysql7.0` request is implemented as **MySQL Server 5.7**, not NDB Cluster 7.x.

The new strict profile is:

| Item | Value |
| --- | --- |
| User profile | `mysql/5.7` |
| Internal profile id | `mysql-5.7` |
| Version package | `com.relationdetector.mysql.fullgrammer.v5_7` |
| Correctness directory | `test-fixtures/correctness/mysql/v5_7` |
| Sample data directory | `sample-data/mysql/5.7` |

## 2. Parser And Lexer Source

MySQL 5.7 does not use a separate community `MySQL57Parser.g4` / `MySQL57Lexer.g4`. It vendors the same fixed upstream grammar family as MySQL 8.0:

| Source | Decision |
| --- | --- |
| Grammar base | `antlr/grammars-v4/sql/mysql/Oracle` |
| Pinned commit | `2e36a9dda4b1ef5bdc1c6a7ebe551616ae2da328` |
| Version truth | MySQL 5.7 official documentation |
| Local strategy | Start from the vendored MySQL grammar and tighten 8.0-only constructs in grammar / lexer predicates |

The 5.7 lexer/parser is generated independently from `adaptor-mysql/src/main/antlr4/com/relationdetector/mysql/fullgrammer/v5_7`. It does not delegate to token-event and does not share generated parser classes with `mysql/v8_0`.

## 3. Version Boundary Rules

The 5.7 grammar keeps MySQL 5.7 capabilities such as stored routines, triggers, multi-table DML, generated columns, basic JSON functions, `ON DUPLICATE KEY UPDATE`, DDL FK/index parsing, and views.

The 5.7 grammar rejects or warns on known MySQL 8.0-only syntax:

| 8.0-only family | 5.7 handling |
| --- | --- |
| CTE / recursive CTE | Grammar-gated as 8.0-only; 5.7 fixtures keep these as version-boundary negatives |
| Window functions / `OVER (...)` | Grammar-gated as 8.0-only |
| `JSON_TABLE` | Grammar-gated as 8.0-only |
| Invisible / visible indexes | Lexer/parser gated as 8.0-only |
| LATERAL derived tables | Kept as version-boundary negative when inherited from 8.0 fixtures |
| Functional index expression DDL | Kept as version-boundary negative where it cannot be represented as a 5.7 generated-column index fixture |

Some boundary fixtures contain both compatible SQL/DDL and one 8.0-only statement. In those fixtures, compatible statements may still produce relationship / lineage fingerprints, while the 8.0-only statement records a parse diagnostic. This is intentional: the fixture proves that mysql/5.7 does not silently accept the high-version construct without discarding unrelated valid evidence from the same file.

## 4. Sample-Data Rewrite

`sample-data/mysql/5.7` was copied from `sample-data/mysql/8.0` and then rewritten to remove 8.0-only constructs. Rewrites preserve ERP business semantics where possible:

| File group | Rewrite pattern | Reason |
| --- | --- | --- |
| `04-queries/*.sql` | CTE/window queries rewritten as derived tables, joins, aggregate subqueries, and `HAVING` | MySQL 5.7 has no CTE/window support |
| `02-procedures/*supplement*.sql` | CTE/window procedure logic rewritten using temporary staging tables or derived joins | Keep procedure lineage testable under 5.7 |
| Store/customer, batch/expiry, return/refund, supplier/common-system procedures | Window and CTE logic rewritten into compatible joins and aggregate subqueries | Preserve relation / lineage coverage without 8.0 syntax |
| Financial asset wash and cross-border reconciliation fixtures | `JSON_TABLE` input rewritten as physical staging tables | MySQL 5.7 cannot turn JSON arrays into rowsets with `JSON_TABLE` |
| Supply-chain update fixtures | CTE/window ranking rewritten into derived aggregate tables | Preserve update relationship / lineage endpoints |

The MySQL 5.7 sample-data hygiene test scans `sample-data/mysql/5.7` for CTE, window functions, `JSON_TABLE`, LATERAL, and invisible/visible index syntax. It does not scan version-boundary negative fixtures, because those intentionally contain 8.0-only SQL.

## 5. Correctness Statistics

Current golden counts:

| Golden group | Fixtures | SQL / DDL | Relations | Lineage | NAMING_MATCH | Diagnostics |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| MySQL root token-event | 83 | 65 / 18 | 659 | 349 | 321 | 0 |
| MySQL full-grammer v5_7 | 89 | 71 / 18 | 706 | 414 | 327 | 9 |
| MySQL full-grammer v8_0 | 89 | 71 / 18 | 923 | 398 | 491 | 6 |

Interpretation:

- `mysql/v5_7` has fewer relations than `mysql/v8_0` because 8.0-only relation-producing syntax is rejected or rewritten to 5.7-compatible forms.
- `mysql/v5_7` has slightly more lineage than `mysql/v8_0` because several complex 8.0 fixtures were rewritten into simpler 5.7-positive DML forms that expose field mappings more directly.
- Current MySQL 5.7 / 8.0 correctness keeps a small number of explicit diagnostics for version-boundary and parser-warning fixtures. These are expected warning counts, not silently swallowed parse failures.

## 6. Parser Fixes Made

| Area | Fix |
| --- | --- |
| Profile selection | Added `mysql-5.7` `FullGrammerDialectModule` and registry/profile tests |
| Grammar version boundary | Added 5.7 grammar/lexer predicates for CTE, window, `JSON_TABLE`, invisible/visible indexes |
| Correctness profile filter | Added `mysql57`, `mysql5.7`, and `mysql-v5_7` fixture profile aliases |
| Warning propagation | SQL and DDL parser runners now forward structured parser warnings into correctness diagnostics |
| Sample-data hygiene | Added a MySQL 5.7 SQL asset test to prevent 8.0-only syntax in `sample-data/mysql/5.7` |

## 7. Review Result

No `REVIEW_NEEDED` item remains for this migration. The decisions are structural:

- If a MySQL 8.0 fixture is valid in 5.7, it remains positive.
- If it has a 5.7-compatible equivalent, the 5.7 fixture is rewritten and its relation / lineage golden is regenerated.
- If the syntax is truly 8.0-only and no clean 5.7 equivalent exists, it stays as a version-boundary negative with diagnostics.

Remaining backlog is parser coverage, not a business semantic review issue:

- Reduce full-grammer parse warnings in stored routines and DDL by extending typed grammar / visitor coverage.
- Split mixed boundary fixtures if a future test style requires unsupported statements and compatible statements to live in separate fixture directories.
- Add future MySQL 8.4 as a separate profile rather than modifying `mysql/v5_7` or `mysql/v8_0`.
