PostgreSQL 17 full-grammer profile
==================================

Official source-of-truth: PostgreSQL 17 source tree (`REL_17_*`), especially
`src/backend/parser/gram.y`, `src/backend/parser/scan.l`, and keyword tables.

ANTLR derivation base: antlr/grammars-v4 `sql/postgresql`
Commit: `2e36a9dda4b1ef5bdc1c6a7ebe551616ae2da328`

Vendored for the `postgresql-17` full-grammer parser. PostgreSQL does not
publish an official ANTLR grammar, so this file is a checked-in ANTLR
projection of the official Bison/Flex grammar, starting from a fixed
grammars-v4 snapshot and then constrained to the PostgreSQL 17 syntax boundary.

Local changes:

- Renamed `PostgreSQLLexer` to `PostgresFullGrammerLexer`.
- Renamed `PostgreSQLParser` to `PostgresFullGrammerParser`.
- Updated `tokenVocab` to reference the renamed lexer.
- Java helper/base classes live under
  `com.relationdetector.postgres.fullgrammer.v17`.
- Keeps PostgreSQL 17 SQL/JSON and MERGE extensions such as `JSON_TABLE`,
  `WHEN NOT MATCHED BY SOURCE`, and `merge_action()`.
- Does not include PostgreSQL 18 temporal column syntax
  `PERIOD? colid (WITHOUT OVERLAPS)?`.

This grammar backs the PostgreSQL 17 full-grammer profile. Runtime selection is
controlled by `parser.mode`; token-event remains the fallback when no strict
version profile can be selected in normal scans. Versioned correctness fixtures
must pass with this profile and must not silently fall back.

Known boundary note:

- PostgreSQL 17 must not accept PostgreSQL 18-only syntax in strict correctness
  fixtures. If it does, first inspect whether this grammar was accidentally
  widened beyond the official PostgreSQL 17 source grammar.
