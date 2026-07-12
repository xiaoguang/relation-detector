MySQL 8.0 full-grammar profile
==============================

Source: antlr/grammars-v4 `sql/mysql/Oracle`
Commit: `2e36a9dda4b1ef5bdc1c6a7ebe551616ae2da328`

Vendored for the `mysql-8.0` full-grammar parser. The upstream grammar
header says it is derived from Oracle/MySQL Shell grammar work and includes
MySQL 8.0+ language features. This repository vendors a fixed snapshot instead
of downloading grammar files at build time.

Local changes:

- Renamed `MySQLLexer` to `MySqlFullGrammarLexer`.
- Renamed `MySQLParser` to `MySqlFullGrammarParser`.
- Updated `tokenVocab` to reference the renamed lexer.
- Java helper/base classes live under
  `com.relationdetector.mysql.fullgrammar.v8_0`.
- MySQL server/session `SQL_MODE` runtime flags are represented by
  `MySqlGrammarSqlMode` and `MySqlGrammarSqlModes`. These classes are grammar
  helpers for MySQL options such as `ANSI_QUOTES` and `PIPES_AS_CONCAT`; they
  are not related to the product-level `parser.mode` setting.

This grammar backs the versioned full-grammar profile. Runtime selection is
controlled by `parser.mode`; token-event remains the fallback when the profile
cannot be selected or parsing fails.
