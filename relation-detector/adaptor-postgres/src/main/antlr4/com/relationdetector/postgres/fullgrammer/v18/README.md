PostgreSQL 18 full-grammer profile
==================================

Official source-of-truth: PostgreSQL 18 source tree (`REL_18_*`), especially
`src/backend/parser/gram.y`, `src/backend/parser/scan.l`, and keyword tables.

ANTLR derivation base: antlr/grammars-v4 `sql/postgresql`
Commit: `2e36a9dda4b1ef5bdc1c6a7ebe551616ae2da328`

Vendored for the `postgresql-18` full-grammer parser. PostgreSQL does not
publish an official ANTLR grammar, so this file is a checked-in ANTLR
projection of the official Bison/Flex grammar, starting from a fixed
grammars-v4 snapshot and then constrained to the PostgreSQL 18 syntax boundary.

Local changes:

- Renamed `PostgreSQLLexer` to `Postgres18FullGrammerLexer`.
- Renamed `PostgreSQLParser` to `Postgres18FullGrammerParser`.
- Updated `tokenVocab` to reference the renamed lexer.
- Java helper/base classes live under
  `com.relationdetector.postgres.fullgrammer.v18`.
- Adds PostgreSQL 18 temporal column syntax in `columnElem`:
  `PERIOD? colid (WITHOUT OVERLAPS)?`.

This grammar backs the PostgreSQL 18 full-grammer profile. Runtime selection is
controlled by `parser.mode`; token-event remains the fallback when no strict
version profile can be selected in normal scans. Versioned correctness fixtures
must pass with this profile and must not silently fall back.

Known boundary note:

- PostgreSQL 18 is the latest strict profile in this repository. Newer-version
  syntax must not be added here unless PostgreSQL official sources classify it
  as PostgreSQL 18-compatible.
