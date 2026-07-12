# PostgreSQL Token-Event PL/pgSQL Grammar

## Responsibility

This module provides the version-neutral PL/pgSQL procedural shell used only by the PostgreSQL token-event fallback. It frames procedural statements and exposes typed static-SQL spans. It does not parse PostgreSQL SQL semantics and does not claim version-strict PL/pgSQL coverage.

## Upstream source

The supported subset is the intersection audited against PostgreSQL `REL_16_14` (`0d1c00c624fa7367d4a895f44381887757289682`), `REL_17_10` (`25c49f3a4a742ba283f5cc43cc7f1d361552e917`), and `REL_18_4` (`f5cc81719e6da4cbdb1f797c48b693e91018153a`) in the official PostgreSQL repository. The relevant upstream sources are `src/pl/plpgsql/src/pl_gram.y` and `src/pl/plpgsql/src/pl_scanner.c`.

## Derivation

`PlPgSqlLexer.g4` and `PlPgSqlParser.g4` are a compact ANTLR transcription of the procedural statement boundaries required by relation detection. They intentionally omit PostgreSQL SELECT, DML, DDL, expression, predicate, and function grammars.

## Supported statement families

The shell types declarations, nested blocks, IF/ELSIF/ELSE, CASE, LOOP/WHILE/FOR/FOREACH, EXIT/CONTINUE, EXCEPTION handlers, assignments, RETURN variants, RAISE, PERFORM/CALL, cursor operations, dynamic EXECUTE markers, and static SQL boundaries.

## Static SQL dispatch

`PlPgSqlShellCollector` converts only typed static-SQL contexts into exact source spans. Each span is sent back to `PostgresTokenEventStructuredSqlParser`; the PL/pgSQL grammar never emits relationship or lineage events itself.

## Version boundaries

This module is the v16/v17/v18 common fallback subset. Version acceptance and rejection belong to versioned full-grammar SQL and PL/pgSQL modules, not to this compact parser.

## Known gaps

Dynamic SQL is reported as `POSTGRES_DYNAMIC_SQL_UNRESOLVED` and is never scanned. Extension languages and procedural syntax outside the audited common subset produce explicit unsupported diagnostics.

## Offline build

The build uses only the checked-in G4 sources and ANTLR 4.13.2. It performs no network download or source generation from PostgreSQL during Maven execution. Generated package: `com.relationdetector.postgres.plpgsql.tokenevent`.
