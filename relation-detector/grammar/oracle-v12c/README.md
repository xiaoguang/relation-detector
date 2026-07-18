# Oracle 12c Full-Grammar Profile

This directory owns the `oracle/12c` grammar surface. It is not a token-event
facade: the profile uses its own generated lexer/parser and rejects the
documented 19c/21c/26ai syntax boundaries currently encoded in the grammar. It
is an official-version-scoped ANTLR projection, not yet a complete conversion of
every Oracle SQL/PLSQL production.

Source-of-truth target: Oracle Database 12c Release 2 / 12.2 SQL Language
Reference and PL/SQL Language Reference.

Vendored base:

- Upstream: `antlr/grammars-v4/sql/plsql`
- Commit: `994628b6d261f5313b72e76039818549352684ce`
- Local rename: `PlSqlLexer.g4` / `PlSqlParser.g4` to
  `OracleFullGrammarLexer.g4` / `OracleFullGrammarParser.g4`
- License: upstream Apache-2.0 headers retained in grammar and base class files

Local 12c cut: high-version boundaries currently covered by tests are rejected
in grammar, including 19c `MEMOPTIMIZE`, 21c `SQL_MACRO`, and 26ai `VECTOR`.

The adaptor keeps a 12c-local generated-context adapter and visitor. This
duplication is intentional because generated contexts define the version
boundary at compile time. Shared collectors accept typed adapter views and do
not import another Oracle version's parser classes.
