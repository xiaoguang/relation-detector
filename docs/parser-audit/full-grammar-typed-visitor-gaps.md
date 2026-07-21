# full-grammar Typed Visitor Gaps

This audit tracks full-grammar areas that cannot yet be represented
through typed grammar contexts. These gaps are explicit migration debt, not
production fallback behavior.

## SQL Event Generation

- `RESOLVED`: `FullGrammarTypedParseTreeEventEmitter.java` has been removed.
  SQL events are now emitted directly from MySQL/PostgreSQL full-grammar
  visitors and the shared typed event sink.
- MySQL full-grammar events are emitted by the version-local
  `MySqlFullGrammarParseTreeVisitor`; PostgreSQL full-grammar events are emitted
  by the version-local `PostgresFullGrammarParseTreeVisitor`. The similarly
  named token-event visitors belong only to the independent fallback parsers.
  The previous context-name collector and separate relation scanner have been
  removed.

Implemented typed contexts:

- MySQL: generate events directly from `selectStatement`, `fromClause`,
  `tableReference`, `joinedTable`, `commonTableExpression`,
  `updateStatement`, `deleteStatement`, `insertStatement`, and routine /
  trigger body contexts.
- PostgreSQL: generate events directly from `selectstmt`, `from_clause`,
  `table_ref`, `common_table_expr`, `updatestmt`, `deletestmt`,
  `insertstmt`, `mergestmt`, function body, and trigger contexts.

Resolved typed grammar gap:

- PostgreSQL 16 full-grammar now accepts `WITH ... MERGE INTO ... USING ...`
  and exposes typed `MergestmtContext` events for CTE declaration, target/source
  rowsets, `ON` equality, and merge write mappings. This was fixed in the
  vendored PostgreSQL 16 grammar profile instead of reintroducing token scanner
  fallback.

## Expression Event Generation

- `RESOLVED`: `FullGrammarExpressionAnalyzer.java` no longer exposes
  token-sequence analysis. full-grammar expression events call parse-tree
  analysis over expression contexts.

Implemented typed context recursion:

- MySQL: recurse over expression contexts such as `expr`, `boolPri`,
  `predicate`, `simpleExpr`, function-call, aggregate, CASE, and window
  contexts.
- PostgreSQL: recurse over expression contexts such as `a_expr`, `c_expr`,
  `columnref`, `func_application`, `func_expr`, `case_expr`, and window
  contexts.

Current typed boundary:

- Version-local `MySqlParseTreeAdapter` and `PostgresParseTreeAdapter` map
  generated context classes to typed expression roles. Shared analyzers consume
  those roles and typed accessors; they do not inspect grammar rule-name strings
  or scan terminal text to infer expression structure. Identifier text is read
  only after a generated context has established its syntactic role.

## DDL Event Generation

- `RESOLVED`: adaptor-local full-grammar DDL event collectors now emit DDL
  events from MySQL/PostgreSQL typed parse-tree contexts for CREATE TABLE,
  ALTER TABLE, and CREATE INDEX.
- The full-grammar DDL path no longer depends on `Pattern` / `Matcher`,
  `DdlStatementView`, or `DdlTokenCursor`.
- Versioned DDL correctness fixtures verify typed DDL output directly. The old
  DDL shadow/parity test has been retired so missing full-grammar evidence is
  exposed by the owning version golden.

Typed coverage:

- MySQL: generate DDL events directly from `createTable`, `tableElement`,
  `columnDefinition`, `tableConstraintDef`, `alterTable`, and `createIndex`
  contexts.
- PostgreSQL: generate DDL events directly from `createstmt`, `columnDef`,
  `tableconstraint`, `altertablestmt`, and `indexstmt` contexts.

## Current Acceptance Boundary

- full-grammar output must pass its own versioned correctness golden while
  these gaps are closed; token-event gold is not used as a cross-parser
  protection layer.
- Any event category that cannot be migrated to typed context visitor must be
  listed here with the grammar limitation and a concrete SQL/DDL sample before
  it can remain unsupported or future-scoped.

## Resolved Typed Predicate Gap

- The former SQL Server full-grammar candidate
  `dbo.accounting_periods.period_code -> dbo.sales_orders.order_date` from
  `period_code = CONVERT(NVARCHAR(7), order_date, 120)` has been removed.
  Direct relationship equality now requires two typed physical columns, or a
  projection alias whose trace resolves to one direct physical column. The same
  columns may still appear as scoped CONTROL lineage for the fiscal-calendar
  write; that does not recreate the removed relationship.
