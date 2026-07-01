# Oracle 12c Full-Grammer Profile

This directory owns the `oracle/12c` grammar surface. It is not a token-event
facade: the profile uses its own generated lexer/parser and rejects the
documented 19c/21c/26ai syntax boundaries currently encoded in the grammar. It
is an official-version-scoped ANTLR projection, not yet a complete conversion of
every Oracle SQL/PLSQL production.

Source-of-truth target: Oracle Database 12c Release 2 / 12.2 SQL Language
Reference and PL/SQL Language Reference.
