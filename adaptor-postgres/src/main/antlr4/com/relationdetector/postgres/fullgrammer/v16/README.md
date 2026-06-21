PostgreSQL 16 full-grammer profile
==================================

Source: antlr/grammars-v4 `sql/postgresql`
Commit: `2e36a9dda4b1ef5bdc1c6a7ebe551616ae2da328`

Vendored for the `postgresql-16` full-grammer parser. The upstream
grammar header describes the grammar as based on PostgreSQL's official Bison
grammar. This repository vendors a fixed snapshot instead of downloading grammar
files at build time.

Local changes:

- Renamed `PostgreSQLLexer` to `PostgresFullGrammerLexer`.
- Renamed `PostgreSQLParser` to `PostgresFullGrammerParser`.
- Updated `tokenVocab` to reference the renamed lexer.
- Java helper/base classes live under
  `com.relationdetector.postgres.fullgrammer.v16`.

This grammar backs the versioned full-grammer profile. Runtime selection is
controlled by `parser.mode`; token-event remains the fallback when the profile
cannot be selected or parsing fails.
