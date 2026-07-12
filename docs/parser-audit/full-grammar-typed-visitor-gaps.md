# full-grammar Typed Visitor Gaps

This audit tracks full-grammar areas that cannot yet be represented
through typed grammar contexts. These gaps are explicit migration debt, not
production fallback behavior.

## SQL Event Generation

- `RESOLVED`: `FullGrammarTypedParseTreeEventEmitter.java` has been removed.
  SQL events are now emitted directly from MySQL/PostgreSQL full-grammar
  visitors and the shared typed event sink.
- `MySqlTokenEventParseTreeVisitor.java` and
  `PostgresTokenEventParseTreeVisitor.java` now own full-grammar event
  emission directly; the previous context-name collector and separate relation
  scanner have been removed.

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

Future hardening:

- The analyzer still uses context names and terminal values to map grammar
  contexts to existing transform enums. A deeper version can override each
  expression rule explicitly per dialect, but it no longer depends on token
  sequence scanning.

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

## Current Typed Predicate Gap

- SQL Server full-grammar still has one observed weak relation candidate that
  should be tightened if `CO_OCCURRENCE` is limited to direct column equality:
  `dbo.accounting_periods.period_code -> dbo.sales_orders.order_date` from
  `period_code = CONVERT(NVARCHAR(7), order_date, 120)` in
  `sample-data/sqlserver/2025/03-data/07-erp-deep-scenario-data.sql`.
  The predicate is typed, but the collector currently treats the
  column-to-function comparison as column co-occurrence. This is not a SQL
  asset gap and should be fixed in the full-grammar predicate collector.
