# Oracle Sample-Data Migration Review

## Summary

This audit records the Oracle sample-data and correctness-golden state after the full-grammer correction. It is intentionally factual: Oracle is wired into relation-detector with an adaptor, an Oracle-owned token-event grammar, sample-data directories, root token-event golden, and generated-parser-backed full-grammer sample-data fixtures. It is not a claim that the Oracle official SQL/PLSQL grammar is fully implemented.

## Scope

Oracle support matrix:

| Profile | Sample-data directory | Correctness directory | Fixture count |
| --- | --- | --- | ---: |
| root token-event | `sample-data/oracle/26ai` as root fixture source pattern | `test-fixtures/correctness/oracle` | 33 |
| `oracle/12c` | `sample-data/oracle/12c` + profile smoke | `test-fixtures/correctness/oracle/v12c` | 30 |
| `oracle/19c` | `sample-data/oracle/19c` + profile smoke + 19c-only syntax | `test-fixtures/correctness/oracle/v19c` | 31 |
| `oracle/21c` | `sample-data/oracle/21c` + profile smoke + 21c-only syntax | `test-fixtures/correctness/oracle/v21c` | 31 |
| `oracle/26ai` | `sample-data/oracle/26ai` + profile smoke + 26ai-only syntax | `test-fixtures/correctness/oracle/v26ai` | 31 |

Total Oracle correctness fixtures: 156.

Each versioned `sample-data/oracle/<version>` directory still contains the translated ERP SQL assets for future runtime and parser work:

| Area | Files | Notes |
| --- | ---: | --- |
| `01-schema` | 7 | ERP schema, indexes, views and semantic-layer supporting tables. |
| `02-procedures` | 13 | Procedure/trigger/package-like business logic translated from the PostgreSQL sample. |
| `03-data` | 5 | Seed and business data. |
| `04-queries` | 12 | Business queries, DML examples and version-specific file. |

These files feed both the root Oracle token-event baseline and the four Oracle versioned full-grammer golden directories. The versioned full-grammer path is independent: it uses each version's generated lexer/parser and typed visitor, not the Oracle token-event parser.

The source `sample-data/oracle/<version>` directories remain complete ERP sample assets. The correctness directories are now a pruned execution set: they keep fixtures that produce relationship / lineage / diagnostics, plus fixtures needed for DDL parsing, version-only syntax and profile smoke. Pure seed-data, routine/function and metadata-only DDL slices that produce no relation, no lineage, and no diagnostic were removed from correctness to keep runtime manageable.

## Current Golden Statistics

| Golden group | Fixture | SQL / DDL | Relation fingerprints | Lineage fingerprints | Diagnostics | NAMING_MATCH |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Oracle root token-event | 41 | 33 / 8 | 643 | 247 | 0 | 255 |
| Oracle full-grammer v12c | 42 | 34 / 8 | 681 | 249 | 0 | 289 |
| Oracle full-grammer v19c | 43 | 35 / 8 | 681 | 249 | 0 | 289 |
| Oracle full-grammer v21c | 43 | 35 / 8 | 681 | 249 | 0 | 289 |
| Oracle full-grammer v26ai | 43 | 35 / 8 | 681 | 249 | 0 | 289 |

The versioned full-grammer counts intentionally stay close to root token-event counts because they cover the same sample-data surface, plus profile smoke and version-only syntax fixtures. They prove that each `oracle/<version>` profile uses its own generated lexer/parser and typed visitor for sample-data correctness and the first official grammar boundaries. They no longer reuse the Oracle token-event parser. They still do not prove complete Oracle official SQL/PLSQL coverage.

The sample-data-only cross-parser comparison after the procedure lineage parity fix is:

| Parser family | Fixture | SQL / DDL | Relation fingerprints | Lineage fingerprints |
| --- | ---: | ---: | ---: | ---: |
| common token-event sample-data | 15 | 11 / 4 | 721 | 225 |
| MySQL token-event root sample-data | 34 | 28 / 6 | 556 | 208 |
| MySQL full-grammer v8_0 sample-data | 37 | 31 / 6 | 784 | 273 |
| PostgreSQL token-event root sample-data | 30 | 24 / 6 | 438 | 119 |
| PostgreSQL full-grammer v16 sample-data | 30 | 24 / 6 | 671 | 120 |
| PostgreSQL full-grammer v17 sample-data | 30 | 24 / 6 | 671 | 120 |
| PostgreSQL full-grammer v18 sample-data | 30 | 24 / 6 | 670 | 119 |
| Oracle token-event root sample-data | 34 | 27 / 7 | 629 | 217 |
| Oracle full-grammer v12c sample-data | 34 | 27 / 7 | 666 | 217 |
| Oracle full-grammer v19c sample-data | 34 | 27 / 7 | 666 | 217 |
| Oracle full-grammer v21c sample-data | 34 | 27 / 7 | 666 | 217 |
| Oracle full-grammer v26ai sample-data | 34 | 27 / 7 | 666 | 217 |

The Oracle full-grammer sample-data lineage now covers the previously confirmed root token-event procedure lineage from `02-procedures/13-erp-deep-scenario-procedures.sql`, including sales fact rebuild, MRP planning, picking task generation and repair-part inventory issue mappings. The fix is in typed grammar / generated parse-tree visitor behavior plus one Oracle SQL asset correction from the invalid `(-rop.quantity)(10)` fragment to the Oracle unary expression `-rop.quantity`.

## Translation Method

The first Oracle sample-data import was generated from the PostgreSQL 18 ERP sample-data and mechanically adapted to Oracle-style SQL:

- PostgreSQL enum declarations were removed and enum usages were mapped to `VARCHAR2(40)`.
- Numeric/string/time types were translated to Oracle-style `NUMBER`, `VARCHAR2`, `CLOB`, `DATE`, and `TIMESTAMP` forms.
- PostgreSQL `RETURNING` / function / timestamp / JSON expressions were normalized into parser-friendly Oracle-style forms where possible.
- PostgreSQL boolean literals were rewritten to numeric booleans for the sample schema.
- Version-specific files were named as `11-oracle12c-compatible.sql`, `11-oracle19c-compatible.sql`, `11-oracle21c-specific.sql`, and `11-oracle26ai-specific.sql`.

This is enough to exercise relation-detector parser/golden behavior, but it is not yet external Oracle runtime validation.

## Runtime Dialect Cleanup Status

Oracle SQL assets now have an explicit hygiene guard. `OracleParserArchitectureTest` scans both
`sample-data/oracle/**` and `test-fixtures/correctness/oracle/**/input.sql` after stripping comments
and string literals, then fails if executable SQL still contains PostgreSQL/MySQL residues such as
`LANGUAGE plpgsql`, PostgreSQL casts, `WITH RECURSIVE`, `LIMIT`, `string_agg`, PostgreSQL temporal
FK syntax, MySQL `AUTO_INCREMENT`, `ENGINE=`, `ON DUPLICATE KEY UPDATE`, `jsonb_*`, or PostgreSQL
JSON arrow operators.

The deterministic cleanup has now been applied to the Oracle sample-data source directories and
synced into correctness fixtures:

| Item | Action | Scope |
| --- | --- | --- |
| PostgreSQL/MySQL SQL residue | Rewrote executable SQL to Oracle-style syntax and added a hygiene test so future residue fails visibly. | All Oracle sample-data and correctness inputs. |
| PostgreSQL-style trigger bodies | Replaced trigger wrappers and `UPDATE ... FROM` bodies with Oracle PL/SQL row triggers. | `01-schema/03-triggers.sql` for 12c/19c/21c/26ai. |
| PostgreSQL casts / intervals | Rewrote common casts to `CAST(...)` and interval arithmetic to `NUMTODSINTERVAL(...)` or Oracle interval literals. | Procedure and query files. |
| PostgreSQL aggregates / row limiting | Rewrote aggregate comments and executable SQL to Oracle `LISTAGG` / `FETCH FIRST ... ROWS ONLY` forms. | Query files. |
| SQL/JSON access | Rewrote parser-facing JSON loops and field access to Oracle SQL/JSON style (`JSON_TABLE`, `JSON_VALUE`, `JSON_QUERY`) where used by sample routines. | Procedure files. |
| Fixture source sync | Regenerated Oracle correctness `input.sql` files from `sample-data/oracle/<version>`. | 185 sample-data-backed fixtures. |
| Full-grammer non-Oracle syntax cleanup | Removed PostgreSQL/MySQL structural grammar from Oracle full-grammer: `LIMIT`, `UNLOGGED`, `CONCURRENTLY`, PostgreSQL casts/arrows, `TABLESAMPLE`, `WITH ORDINALITY`, `DO NOTHING`, and materialized CTE syntax are no longer legal in versioned Oracle full-grammer. | `oracle/12c`, `oracle/19c`, `oracle/21c`, `oracle/26ai`. |

The current Oracle sample-data is still **parser correctness coverage**, not a proven loadable Oracle
ERP schema. A future runtime-smoke task should load `sample-data/oracle/19c` and
`sample-data/oracle/26ai` into real Oracle instances before claiming database-load compatibility.
The important difference from the previous state is that the checked-in Oracle SQL assets no longer
carry known PostgreSQL/MySQL executable syntax residue.

## Review Classification

| Classification | Current status | Explanation |
| --- | --- | --- |
| `PARSER_GAP_BACKLOG` | Open | Oracle token-event and full-grammer now cover the sample-data surface, but still need broader PL/SQL, `CONNECT BY`, packages, Oracle `MERGE`, and version-specific SQL coverage beyond the sample set. |
| `OFFICIAL_GRAMMAR_BACKLOG` | Open | `oracle/12c|19c|21c|26ai` `.g4` files now include official version-boundary rules for selected 19c/21c/26ai syntax, but they are not complete Oracle grammar conversions. Official strict full-grammer still needs broader source-of-truth conversion from Oracle SQL/PLSQL documentation. |
| `RUNTIME_SQL_DIALECT_BACKLOG` | Narrowed | Known PostgreSQL/MySQL executable syntax residues are now blocked by the hygiene test. Real Oracle database loading and deeper PL/SQL/runtime behavior still need external smoke validation. |
| `RUNTIME_SMOKE_PENDING` | Open | `sample-data/oracle/19c` and `sample-data/oracle/26ai` have not yet been loaded into a real Oracle instance in this environment. |
| `REVIEW_NEEDED` | Closed | `docs/parser-audit/data-lineage-full-audit.md` now reports `PENDING_REVIEW | 0`. The reviewed Oracle procedure lineage candidates have been promoted into correctness golden after the SQL was rewritten into Oracle syntax. |

## Rules For Future Oracle Work

- Do not restore scanner, regex, token-span parsing, or table/column name special cases to improve Oracle output.
- Oracle token-event should gain capability through typed grammar and visitor only.
- Oracle full-grammer should become strict by expanding the version-specific generated parser/visitor implementations; it must not delegate to token-event.
- Low Oracle versions must not silently accept high-version-only syntax once version-specific syntax fixtures are introduced.
- Dynamic SQL, PL/SQL local variables, parameters, pseudo rowsets and temporary tables must not enter physical relation/lineage golden.

## Verification Commands

```bash
mvn -pl cli -am \
  -Dtest=CorrectnessFixtureRunnerTest#allCorrectnessFixturesPassGoldenExpectations \
  -DcorrectnessFixtureFilter=oracle \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -pl adaptor-oracle,cli -am \
  -Dtest='OracleAdaptorParserTest,SqlGrammarProfileTest,ParserBundleSelectorTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
