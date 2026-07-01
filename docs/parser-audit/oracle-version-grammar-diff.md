# Oracle Version Grammar Difference Audit

## Summary

This document records the Oracle full-grammer version boundary. The repository
now vendors `antlr/grammars-v4/sql/plsql` at a fixed commit and then applies
project-maintained version cuts for `oracle/12c`, `oracle/19c`, `oracle/21c`,
and `oracle/26ai`. It is still not a complete Oracle manual conversion; it is a
version-scoped ANTLR projection with explicit provenance, local deltas, and
tests.

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

## Community Grammar Reference

The closest open-source grammar with MySQL/PostgreSQL-like breadth is
`antlr/grammars-v4/sql/plsql`. Its `PlSqlParser.g4` is roughly ten thousand
lines, its `PlSqlLexer.g4` is roughly twenty-six hundred lines, and it contains
rules for `sql_script`, DDL, DML, `MERGE`, `CREATE TABLE`, `ALTER TABLE`,
`CREATE PROCEDURE`, `CREATE FUNCTION`, `CREATE TRIGGER`, JSON functions, and
many Oracle-specific clauses. It is Apache-2.0 licensed and references Oracle
SQL / PL/SQL documentation, but it is not directly split into Oracle
`12c/19c/21c/26ai` strict profiles.

Pinned upstream commit:

```text
antlr/grammars-v4 994628b6d261f5313b72e76039818549352684ce
```

Vendored files:

```text
sql/plsql/PlSqlLexer.g4
sql/plsql/PlSqlParser.g4
sql/plsql/Java/PlSqlLexerBase.java
sql/plsql/Java/PlSqlParserBase.java
```

Local rename:

```text
PlSqlLexer.g4       -> OracleFullGrammerLexer.g4
PlSqlParser.g4      -> OracleFullGrammerParser.g4
PlSqlLexerBase.java -> OracleFullGrammerLexerBase.java
PlSqlParserBase.java -> OracleFullGrammerParserBase.java
```

The upstream Apache-2.0 license headers are retained in the vendored grammar
and base class files. Bytebase remains an engineering reference only; it is not
used as the source of truth for version boundaries.

The applied full-grammer upgrade path is:

1. Pin an exact `antlr/grammars-v4` commit before vendoring anything into this
   repository. Do not depend on floating `master`.
2. Vendor the PL/SQL lexer/parser grammar and the required Java base classes
   into `adaptor-oracle/fullgrammer/<version>` packages with local names such as
   `OracleFullGrammerLexer` and `OracleFullGrammerParser`.
3. Make `oracle/12c` the first generated-parser baseline, then derive
   `oracle/19c`, `oracle/21c`, and `oracle/26ai` by removing or adding rules
   according to the official Oracle version documents listed above.
4. Rebuild typed visitors and DDL collectors against the upstream-style parse
   tree contexts. Do not bridge through `OracleRelationSql.g4` token-event.
5. Add version-boundary fixtures before widening golden coverage: a lower
   version must fail to parse a higher-version-only grammar production by its
   own grammar, not by Java profile blacklist logic.

The checked-in Oracle full-grammer is therefore stronger than the earlier
scoped grammar and no longer resembles token-event, but it remains
`INCOMPLETE_VERSIONED`: it proves grammars-v4-backed generated parser wiring,
sample-data coverage, DDL/SQL/PLSQL extraction for current fixtures, and the
first version-boundary examples, not complete Oracle Reference coverage.

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

Current versioned full-grammer correctness totals after the grammars-v4
migration:

| Profile | Fixtures | Relationship fingerprints | Lineage fingerprints |
| --- | ---: | ---: | ---: |
| `oracle/12c` | 30 | 525 | 75 |
| `oracle/19c` | 31 | 525 | 75 |
| `oracle/21c` | 31 | 525 | 75 |
| `oracle/26ai` | 31 | 525 | 75 |

This is not yet a complete, runtime-validated Oracle grammar for every SQL and
PL/SQL production in the official manuals. Future work should continue replacing
`INCOMPLETE_VERSIONED` broad rules with official grammar productions, especially
packages, advanced PL/SQL, hierarchical queries, model clauses, JSON, XML,
analytic SQL, and full Oracle DDL options.

The current versioned full-grammer no longer accepts known non-Oracle structural
syntax by grammar fallback. The Oracle full-grammer lexer/parser files do not
declare PostgreSQL/MySQL constructs such as `LIMIT`, `UNLOGGED`, `CONCURRENTLY`,
PostgreSQL `::` casts, JSON arrow operators, `TABLESAMPLE`, `WITH ORDINALITY`,
`DO NOTHING`, or PostgreSQL materialized CTE modifiers. Scoped flexible tokens
remain only inside bounded Oracle expression/function argument contexts where
the parser still needs to tolerate Oracle built-in function arguments; they are
not statement-level unknown fallbacks and cannot make an arbitrary non-Oracle
statement parse successfully.

Metadata-only DDL such as quoted identifiers, Oracle numeric/string/LOB/XML data
types, `PRIMARY KEY`, `COMMENT ON TABLE`, and `COMMENT ON COLUMN` is covered by
Oracle parser behavior tests. It is no longer retained as a large correctness
fixture when it produces no relationship, lineage, or diagnostic output.

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
