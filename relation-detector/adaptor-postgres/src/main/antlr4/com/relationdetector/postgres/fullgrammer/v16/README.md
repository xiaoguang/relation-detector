PostgreSQL 16 full-grammer profile
==================================

Official source-of-truth: PostgreSQL 16 source tree (`REL_16_*`), especially
`src/backend/parser/gram.y`, `src/backend/parser/scan.l`, and keyword tables.

ANTLR derivation base: antlr/grammars-v4 `sql/postgresql`
Commit: `2e36a9dda4b1ef5bdc1c6a7ebe551616ae2da328`

Vendored for the `postgresql-16` full-grammer parser. PostgreSQL does not
publish an official ANTLR grammar, so this file is a checked-in ANTLR
projection of the official Bison/Flex grammar, starting from a fixed
grammars-v4 snapshot and then constrained to the PostgreSQL 16 syntax boundary.

Local changes:

- Renamed `PostgreSQLLexer` to `Postgres16FullGrammerLexer`.
- Renamed `PostgreSQLParser` to `Postgres16FullGrammerParser`.
- Updated `tokenVocab` to reference the renamed lexer.
- Java helper/base classes live under
  `com.relationdetector.postgres.fullgrammer.v16`.
- Removed PostgreSQL 17-only `JSON_TABLE` and `MERGE_ACTION` keyword exposure
  from the PostgreSQL 16 lexer/parser lists.

This grammar backs the versioned full-grammer profile. Runtime selection is
controlled by `parser.mode`; token-event remains the fallback when the profile
cannot be selected or parsing fails.

Known boundary note:

- PostgreSQL 16 must not accept PostgreSQL 17/18-only syntax in strict
  correctness fixtures. If this grammar accepts such syntax, treat it as a
  grammar pollution bug unless PostgreSQL official sources prove otherwise.
