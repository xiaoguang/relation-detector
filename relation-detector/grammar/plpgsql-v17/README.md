# PostgreSQL 17 PL/pgSQL Grammar

## Responsibility

This module provides the version-scoped PL/pgSQL procedural shell for the PostgreSQL 17 full-grammar profile. It frames procedural structure and static SQL spans; PostgreSQL SQL semantics remain owned by the v17 full SQL parser.

## Upstream source

Source of truth: the official PostgreSQL repository tag `REL_17_10`, commit `25c49f3a4a742ba283f5cc43cc7f1d361552e917`, especially `src/pl/plpgsql/src/pl_gram.y` and `src/pl/plpgsql/src/pl_scanner.c`.

## Derivation

The checked-in ANTLR rules translate the relevant yacc/scanner procedural boundaries into `PlPgSqlLexer.g4` and `PlPgSqlParser.g4`. SQL query, DML, DDL, predicate, and expression productions are deliberately excluded.

## Supported statement families

Declarations, nested blocks, IF/ELSIF/ELSE, CASE, LOOP/WHILE/FOR/FOREACH, EXIT/CONTINUE, EXCEPTION, assignment, RETURN variants, RAISE, PERFORM/CALL, cursors, dynamic EXECUTE markers, and typed static SQL boundaries are supported.

## Static SQL dispatch

`com.relationdetector.postgres.plpgsql.v17.PlPgSqlShellCollector` returns exact static-SQL source spans. They are parsed by the PostgreSQL 17 full SQL parser; this module does not generate facts.

## Version boundaries

This artifact is loaded only by the PostgreSQL 17 full profile. PostgreSQL 17 SQL boundaries, including MERGE RETURNING support, are enforced by the v17 embedded SQL parser.

## Known gaps

Dynamic SQL remains unresolved and is reported diagnostically. Unsupported procedural statements preserve surrounding recognized statements and produce source-located diagnostics.

## Offline build

Maven generates Java from the checked-in G4 files with ANTLR 4.13.2 and does not contact PostgreSQL. Generated package: `com.relationdetector.postgres.plpgsql.v17`.
