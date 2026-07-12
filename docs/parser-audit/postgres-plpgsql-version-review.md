# PostgreSQL PL/pgSQL Version Review

## Ownership

PostgreSQL routine parsing has two independent mode-preserving paths:

```text
token-event outer SQL
  -> postgres-plpgsql-token-event
  -> PostgreSQL token-event parser for each static SQL statement

v16/v17/v18 full grammar outer SQL
  -> matching plpgsql-v16/v17/v18 artifact
  -> matching v16/v17/v18 full grammar parser for each static SQL statement
```

The PL/pgSQL grammar recognizes the procedural shell. It does not reimplement
PostgreSQL SQL. Dynamic `EXECUTE` is reported as unresolved and its string is
never scanned for physical facts.

SQL-standard `BEGIN ATOMIC` bodies are produced by the outer token-event or
versioned full SQL grammar as typed statement contexts. They bypass the
PL/pgSQL parser and dispatch each statement to the current-mode SQL parser.
String bodies without an explicit `LANGUAGE` are rejected with
`POSTGRES_ROUTINE_LANGUAGE_MISSING`; an atomic body without `LANGUAGE` is SQL.

## Official Pins

| Artifact | PostgreSQL tag | Commit | Official source |
| --- | --- | --- | --- |
| `plpgsql-v16` | `REL_16_14` | `0d1c00c624fa7367d4a895f44381887757289682` | `pl_gram.y`, `pl_scanner.c` |
| `plpgsql-v17` | `REL_17_10` | `25c49f3a4a742ba283f5cc43cc7f1d361552e917` | `pl_gram.y`, `pl_scanner.c` |
| `plpgsql-v18` | `REL_18_4` | `f5cc81719e6da4cbdb1f797c48b693e91018153a` | `pl_gram.y`, `pl_scanner.c` |

The compact token-event artifact is a documented common subset, not a strict
version claim. All grammars are vendored; normal Maven builds perform no
network download.

## Statement Families

All four procedural parsers type `DECLARE`, nested blocks, IF/ELSIF/ELSE,
CASE, LOOP/WHILE/FOR/FOREACH, EXIT/CONTINUE, EXCEPTION, assignment, RETURN,
RAISE, PERFORM/CALL, cursor operations and static SQL boundaries. Static SQL
is delegated to the current parser mode. Unsupported statements retain parsed
neighbors and emit an object/statement/line diagnostic.

## Version Boundary

PostgreSQL 16 excludes `MERGE ... RETURNING` in both its full SQL grammar and
versioned PL/pgSQL shell. PostgreSQL 17 and 18 accept it and delegate the
statement to their matching full SQL parser. PostgreSQL 17 rejects the
PostgreSQL 18 `RETURNING old/new` form through a typed token predicate; v18
accepts the versioned form. No Java regex version blacklist remains.

## Verification Map

- `PostgresFullGrammarVersionBoundaryTest`: SQL and routine version probes.
- `PostgresRoutineSampleLineageTest`: natural routine/trigger facts in all modes.
- `PlPgSqlShellCollectorTest`: typed static SQL boundaries and
  procedural `INTO` masking.
- `PostgresTokenEventPlPgSqlParserTest`, `Postgres16PlPgSqlParserTest`,
  `Postgres17PlPgSqlParserTest`, and `Postgres18PlPgSqlParserTest`: shared
  procedural contract, FOREACH, recovery, and dynamic SQL diagnostics.
- `PostgresRoutineLanguageDispatcherTest`: body/language matrix and absolute source ranges.
- `PostgresRoutineParserIndependenceTest`: token/full and cross-version imports.

Current review status: no `REVIEW_NEEDED` item. Natural sample-data routines
must remain diagnostic-free; unsupported language and dynamic SQL diagnostics
are expected only in dedicated negative fixtures.

## Audited migration delta

Replacing the duplicated SQL subgrammar with current-mode static SQL dispatch
closed one historical gap in `sp_cross_border_reconciliation_engine`. The
previous golden was empty even though its `RETURN QUERY` contains three direct
physical equalities: exchange-rate currency to SKU currency, order customer to
customer profile, and the correlated order customer to transaction ledger
user. Token-event and v16/v17/v18 now emit the same three `SQL_LOG_JOIN`
observations. This is a confirmed parser-gap correction, not a version delta.
