# Oracle Version Grammar Difference Audit

## Summary

This document records the first official-doc-derived version boundary for Oracle
full-grammer profiles. It is intentionally narrower than a full Oracle grammar
conversion: the repository grammar is an ANTLR projection that now encodes
confirmed version differences and protects those boundaries with tests.

`OracleRelationSql.g4` remains a single broad token-event fallback grammar.
Version strictness belongs only to `oracle/12c`, `oracle/19c`, `oracle/21c`,
and `oracle/26ai` full-grammer profiles.

## Official Sources

| Version | Source-of-truth documents | Local grammar target |
| --- | --- | --- |
| 12c Release 2 / 12.2 | Oracle Database 12.2 SQL Language Reference and PL/SQL Language Reference: `https://docs.oracle.com/en/database/oracle/oracle-database/12.2/sqlrf/` | Baseline Oracle SQL/PLSQL grammar projection. |
| 19c | Oracle Database 19c SQL Language Reference and PL/SQL Language Reference: `https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/` | 12c projection plus confirmed 19c grammar additions used by fixtures. |
| 21c | Oracle Database 21c SQL Language Reference and PL/SQL Language Reference: `https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/` | 19c projection plus confirmed 21c grammar additions used by fixtures. |
| 26ai | Oracle AI Database 26ai SQL Language Reference and PL/SQL Language Reference: `https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/` | 21c projection plus confirmed 26ai grammar additions used by fixtures. |

## Implemented Version Boundaries

| Feature | First accepted profile | Lower-version behavior | Positive fixture/test |
| --- | --- | --- | --- |
| `CREATE TABLE ... MEMOPTIMIZE FOR READ` | `oracle/19c` | `oracle/12c` rejects through grammar syntax errors. | `oracle19c-version-memoptimize-sql`; `OracleAdaptorParserTest`. |
| PL/SQL `SQL_MACRO(SCALAR)` function header | `oracle/21c` | `oracle/12c` and `oracle/19c` reject through grammar syntax errors. | `oracle21c-version-sql-macro-sql`; `OracleAdaptorParserTest`. |
| `VECTOR(...)` column data type | `oracle/26ai` | `oracle/12c`, `oracle/19c`, and `oracle/21c` reject through grammar syntax errors. | `oracle26ai-version-vector-sql`; `OracleAdaptorParserTest`. |

The lower-version rejection is grammar-based. Lower-version lexers recognize the
future-version keyword tokens, but their parser rules do not allow those tokens
inside broad `unknownStatement`, routine header, or column-definition paths.
This prevents high-version syntax from being accepted as a generic identifier.

## Current Grammar Status

| Profile | Grammar status | Notes |
| --- | --- | --- |
| `oracle/12c` | `INCOMPLETE_VERSIONED` projection | Rejects 19c/21c/26ai feature tokens listed above. |
| `oracle/19c` | `INCOMPLETE_VERSIONED` projection | Accepts `MEMOPTIMIZE FOR READ`; rejects 21c/26ai feature tokens listed above. |
| `oracle/21c` | `INCOMPLETE_VERSIONED` projection | Accepts `SQL_MACRO` and inherited 19c feature; rejects 26ai `VECTOR`. |
| `oracle/26ai` | `INCOMPLETE_VERSIONED` projection | Accepts `VECTOR` and inherited 19c/21c feature syntax. |

This is not yet a complete, runtime-validated Oracle grammar for every SQL and
PL/SQL production in the official manuals. Future work should continue replacing
`INCOMPLETE_VERSIONED` broad rules with official grammar productions, especially
packages, advanced PL/SQL, hierarchical queries, model clauses, JSON, XML,
analytic SQL, and full Oracle DDL options.

## Testing Rules

- Token-event may parse broadly and must not be used as proof of version
  support.
- Versioned full-grammer fixtures must not silently fallback to token-event.
- Low versions must reject high-version-only syntax by grammar parse failure or
  explicit unsupported syntax diagnostics.
- New version-only syntax must be backed by an official source entry in this
  document before entering correctness golden.

## Review Status

| Classification | Status | Notes |
| --- | --- | --- |
| `CONFIRMED_VERSION_BOUNDARY` | Closed for the three implemented boundaries | 19c `MEMOPTIMIZE`, 21c `SQL_MACRO`, 26ai `VECTOR`. |
| `OFFICIAL_GRAMMAR_BACKLOG` | Open | Full Oracle SQL/PLSQL conversion remains broad future work. |
| `REVIEW_NEEDED` | None | No ambiguous relationship or lineage semantics are introduced by these boundary fixtures. |
