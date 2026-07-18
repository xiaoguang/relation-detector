# Oracle 26ai Full-Grammar Profile

This directory owns the `oracle/26ai` grammar surface. It is not a token-event
facade: the profile uses its own generated lexer/parser and accepts the
documented 26ai `VECTOR` column type covered by tests, plus the inherited 19c
and 21c boundaries. It is an official-version-scoped ANTLR projection, not yet a
complete conversion of every Oracle SQL/PLSQL production.

Source-of-truth target: Oracle AI Database 26ai SQL Language Reference and
PL/SQL Language Reference.

Vendored base:

- Upstream: `antlr/grammars-v4/sql/plsql`
- Commit: `994628b6d261f5313b72e76039818549352684ce`
- Local rename: `PlSqlLexer.g4` / `PlSqlParser.g4` to
  `OracleFullGrammarLexer.g4` / `OracleFullGrammarParser.g4`
- License: upstream Apache-2.0 headers retained in grammar and base class files

Local 26ai cut: inherits 19c/21c boundaries and accepts the tested
`VECTOR(dimension, element_type)` column type.

The adaptor keeps a 26ai-local generated-context adapter and visitor. This
duplication is intentional because generated contexts define the version
boundary at compile time. Shared collectors accept typed adapter views and do
not import another Oracle version's parser classes.
