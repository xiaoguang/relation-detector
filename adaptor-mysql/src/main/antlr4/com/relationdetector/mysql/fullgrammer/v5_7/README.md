MySQL 5.7 full-grammer profile
==============================

Source: antlr/grammars-v4 `sql/mysql/Oracle`
Commit: `2e36a9dda4b1ef5bdc1c6a7ebe551616ae2da328`

Vendored for the `mysql-5.7` full-grammer parser. The upstream grammar
header says it is derived from Oracle/MySQL Shell grammar work and includes
MySQL 8.0+ language features. This repository vendors the same fixed snapshot
used by `mysql-8.0`, then constrains this profile with `serverVersion=50700`
and MySQL 5.7 official documentation as the version boundary source of truth.

Local changes:

- Renamed `MySQLLexer` to `MySqlFullGrammerLexer`.
- Renamed `MySQLParser` to `MySqlFullGrammerParser`.
- Updated `tokenVocab` to reference the renamed lexer.
- Java helper/base classes live under
  `com.relationdetector.mysql.fullgrammer.v5_7`.
- `MySQLLexerBase` and `MySQLParserBase` default `serverVersion` to `50700`,
  so grammar predicates for 8.0+ syntax do not pass in this profile.
- MySQL server/session `SQL_MODE` runtime flags are represented by
  `MySqlGrammarSqlMode` and `MySqlGrammarSqlModes`. These classes are grammar
  helpers for MySQL options such as `ANSI_QUOTES` and `PIPES_AS_CONCAT`; they
  are not related to the product-level `parser.mode` setting.

This grammar backs the versioned full-grammer profile. Runtime selection is
controlled by `parser.mode`; token-event remains the fallback when the profile
cannot be selected or parsing fails.
