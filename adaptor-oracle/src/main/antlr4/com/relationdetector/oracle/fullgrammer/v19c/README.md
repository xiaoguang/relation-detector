# Oracle 19c Full-Grammer Profile

This directory owns the `oracle/19c` grammar surface. It is not a token-event
facade: the profile uses its own generated lexer/parser, accepts the documented
19c `MEMOPTIMIZE FOR READ` table syntax covered by tests, and rejects the
documented 21c/26ai boundaries currently encoded in the grammar. It is an
official-version-scoped ANTLR projection, not yet a complete conversion of every
Oracle SQL/PLSQL production.

Source-of-truth target: Oracle Database 19c SQL Language Reference and PL/SQL
Language Reference.
