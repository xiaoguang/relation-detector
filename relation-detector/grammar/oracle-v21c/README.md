# Oracle 21c Full-Grammar Profile

This directory owns the `oracle/21c` grammar surface. It is not a token-event
facade: the profile uses its own generated lexer/parser, accepts the documented
21c `SQL_MACRO` function-header syntax covered by tests, and rejects the
documented 26ai `VECTOR` boundary currently encoded in the grammar. It is an
official-version-scoped ANTLR projection, not yet a complete conversion of every
Oracle SQL/PLSQL production.

Source-of-truth target: Oracle Database 21c SQL Language Reference and PL/SQL
Language Reference.

Vendored base:

- Upstream: `antlr/grammars-v4/sql/plsql`
- Commit: `994628b6d261f5313b72e76039818549352684ce`
- Local rename: `PlSqlLexer.g4` / `PlSqlParser.g4` to
  `OracleFullGrammarLexer.g4` / `OracleFullGrammarParser.g4`
- License: upstream Apache-2.0 headers retained in grammar and base class files

Local 21c cut: inherits the 19c boundary and accepts `SQL_MACRO(SCALAR)` while
rejecting 26ai `VECTOR`.
