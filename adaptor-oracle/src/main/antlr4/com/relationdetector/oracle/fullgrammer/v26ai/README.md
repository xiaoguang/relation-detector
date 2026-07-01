# Oracle 26ai Full-Grammer Profile

This directory owns the `oracle/26ai` grammar surface. It is not a token-event
facade: the profile uses its own generated lexer/parser and accepts the
documented 26ai `VECTOR` column type covered by tests, plus the inherited 19c
and 21c boundaries. It is an official-version-scoped ANTLR projection, not yet a
complete conversion of every Oracle SQL/PLSQL production.

Source-of-truth target: Oracle AI Database 26ai SQL Language Reference and
PL/SQL Language Reference.
