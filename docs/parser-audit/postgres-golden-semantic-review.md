# PostgreSQL Golden Semantic Review

This report records the PostgreSQL correctness audit pass for root token-event and versioned full-grammer fixtures.

## Scope

| Fixture group | Fixtures | Relations | Lineage | Notes |
| --- | ---: | ---: | ---: | --- |
| PostgreSQL root token-event | 100 | 886 | 52 | Fallback parser golden. |
| PostgreSQL full-grammer v16 | 100 | 1195 | 68 | Version-strict full-grammer golden. |
| PostgreSQL full-grammer v17 | 102 | 1198 | 90 | Includes v17-only fixtures. |
| PostgreSQL full-grammer v18 | 103 | 1198 | 89 | Includes v18-only fixtures. |

## Decisions

| Classification | Count | Decision |
| --- | ---: | --- |
| `CONFIRMED_MISSING` | 17 fixture comparisons | Fixed parser and refreshed golden. |
| `CONFIRMED_EXTRA` | 0 | No confirmed false-positive PostgreSQL golden entries in this pass. |
| `PARSER_GAP` | 0 for the fixed audit slice | High-confidence gaps found in this pass were fixed before golden refresh. |
| `EXPECTED_VERSION_DELTA` | Present | Version-only PostgreSQL syntax remains isolated in v17/v18 fixtures. |
| `REVIEW_NEEDED` | 0 | No item from this pass needs user adjudication. |

## Confirmed Fixes

### DDL FK / Index Evidence

**Context.** Root PostgreSQL token-event DDL previously dropped valid PostgreSQL FK/index details when a table-level FK carried `DEFERRABLE INITIALLY DEFERRED` or when an index column carried an opclass such as `varchar_pattern_ops`.

**Fix.** `CommonRelationSql.g4` now accepts typed FK tails and typed index-part options. This is grammar structure support, not regex or special-name filtering.

**Golden impact.** `postgres-basic-correctness-case-01-ddl` gained the previously missing DDL facts, including `DDL_FOREIGN_KEY`, `SOURCE_INDEX`, and `TARGET_UNIQUE` aggregation for the same source-target endpoint.

### CTE / Derived Projection Equality

**Context.** Several root token-event SQL fixtures contained explicit equality predicates through CTE or derived-table projections. Examples:

```sql
WITH regional_revenues AS (
    SELECT region_id, entity_id, gross_sales,
           'TAX_CODE_' || tax_jurisdiction AS jurisdiction_string
    FROM financial_subsidiaries
),
tax_rules AS (
    SELECT rule_id, jurisdiction_string, rate_multiplier
    FROM corporate_tax_matrix
)
SELECT ...
FROM regional_revenues r
JOIN tax_rules t ON r.jurisdiction_string = t.jurisdiction_string;
```

```sql
SELECT a.id, b.info_summary
FROM accounts a
JOIN (
    SELECT account_id, STRING_AGG(meta_val, '-') AS info_summary
    FROM account_metadata
    GROUP BY account_id
) b ON a.id = b.account_id;
```

**Fix.** PostgreSQL token-event now tracks the single physical rowset in a query scope and assigns unqualified select-list columns to that rowset when the scope is unambiguous. Projection resolution can then map derived aliases back to physical columns.

**Golden impact.** Root token-event gained confirmed co-occurrence / FK-like relationships in generated complex query fixtures and sample-data query fixtures.

### Aggregate And Scalar Subquery Lineage

**Context.** PostgreSQL root token-event missed clear write lineage from aggregate subqueries and derived aggregate projections:

```sql
UPDATE users u
SET total_spent = COALESCE(o_summary.actual_total, 0.00),
    level = CASE WHEN o_summary.actual_total >= 10000 THEN 'VIP' ELSE 'REGULAR' END
FROM (
    SELECT user_id, SUM(pay_amount) AS actual_total
    FROM orders
    GROUP BY user_id
) o_summary
WHERE u.id = o_summary.user_id;
```

```sql
UPDATE product_batches
SET current_qty = (
    SELECT SUM(quantity)
    FROM inventory
    WHERE batch_id = NEW.batch_id
)
WHERE id = NEW.batch_id;
```

**Fix.** PostgreSQL token-event expression analysis now evaluates scalar subqueries rule-by-rule from the typed `selectStatement` context instead of only accepting bare-column subqueries.

**Golden impact.** Root and versioned sample-data golden gained physical aggregate lineage such as `inventory.quantity -> product_batches.current_qty` and supplier metrics such as `purchase_order_items.received_qty -> supplier_products.total_order_qty`.

### MERGE Relationship And Lineage

**Context.** Root token-event had no typed `MERGE` statement support, so clear `MERGE ... ON` relationship and `INSERT (...) VALUES (...)` lineage were absent:

```sql
MERGE INTO target_orders AS t
USING source_orders AS s
ON t.source_order_id = s.id
WHEN NOT MATCHED THEN
  INSERT (source_order_id) VALUES (s.id);
```

**Fix.** `PostgresRelationSql.g4` now has a typed token-event `mergeStatement` subset. `PostgresTokenEventParseTreeVisitor` emits `PREDICATE_EQUALITY` for the `ON` predicate and `MERGE_WRITE_MAPPING` for typed `UPDATE SET` / `INSERT VALUES` actions.

**Golden impact.** `sql-merge-using` now records the explicit relation `target_orders.source_order_id -> source_orders.id` and lineage `source_orders.id -> target_orders.source_order_id`.

### Full-Grammer Nested Projection Scope

**Context.** PostgreSQL full-grammer missed one valid relationship in
`sample-data/postgres/18/04-queries/03-complex-queries-batch3.sql`. The
`latest_performance` CTE projected unqualified columns from a derived table:

```sql
WITH latest_performance AS (
    SELECT employee_id, total_score, grade, review_period
    FROM (
        SELECT employee_id, total_score, grade, review_period,
               ROW_NUMBER() OVER (PARTITION BY employee_id ORDER BY review_period DESC) AS rn
FROM performance_reviews
WHERE status = 'confirmed'
) t
WHERE rn = 1
)
SELECT ...
FROM leave_balance lb
LEFT JOIN latest_performance lp ON lb.employee_id = lp.employee_id;
```

The SQL structure is unambiguous: `lp.employee_id` comes from
`performance_reviews.employee_id`; `lb.employee_id` comes from `employees.id`
through the `leave_balance` CTE.

**Fix.** `FullGrammerTypedSqlEventSink` now restores rowset scope when leaving
SELECT and projection-owner scopes. Inner rowsets from a derived table no longer
pollute the parent CTE's default unqualified-column qualifier. This keeps the
fix in typed visitor scope management; it does not add regex, token-span
scanning, or table/column name filtering.

**Golden impact.** PostgreSQL v16/v17/v18 full-grammer sample-data golden each
gained:

```text
FK_LIKE:performance_reviews.employee_id->employees.id:SQL_LOG_JOIN
```

Root PostgreSQL token-event also gained the same relationship after its typed
CTE projection support was refreshed.

### Sample SQL Department Join Repair

**Context.** The same Q51 SQL selected and grouped by `d.name AS dept_name` but
did not introduce `departments d`. That made the sample query semantically
incomplete even though the parser could still read its structure.

**Fix.** The MySQL and PostgreSQL sample-data SQL were repaired with the
explicit business join:

```sql
LEFT JOIN departments d ON e.department_id = d.id
```

This is a fixture SQL correction, not a parser workaround.

**Golden impact.** PostgreSQL root and v16/v17/v18 full-grammer sample-data
golden each gained:

```text
FK_LIKE:employees.department_id->departments.id:SQL_LOG_JOIN
```

### Derived Rowset And Parameter Lineage Guard

**Context.** Tightening projection scope exposed a separate lineage risk in
MySQL full-grammer cumulative-projection tests: derived aliases such as
`rand_tbl` / `h`, and routine parameters such as `p_target_date`, could appear
as lineage sources before projection resolution reached the physical source
columns.

**Fix.** `TokenEventDataLineageExtractor` now filters ignored rowset tables from
final lineage sources and prefers projection candidates that resolve to stronger
physical-source evidence. This is a shared semantic-layer guard used by
token-event and full-grammer events.

**Golden impact.** No PostgreSQL golden changed for this guard. Existing MySQL
golden stayed stable while tests now explicitly assert that derived aliases and
routine parameters do not become physical lineage sources.

## Guardrails Verified

- No table-name or column-name special filtering was added.
- SQL structure recognition remains typed grammar / typed visitor based.
- Parameters, local variables, temporary tables, `NEW` / `OLD`, `EXCLUDED`, literals, and `LIKE` operands remain excluded as physical lineage or relationship endpoints.
- PostgreSQL versioned full-grammer profiles remain version-strict; no low-version grammar was loosened to accept high-version-only syntax.

## Remaining Root Token-Event vs Full-Grammer Delta

Root token-event is still a fallback parser, while versioned full-grammer is the strict primary parser when a PostgreSQL profile is selected. Same-input comparisons still show full-grammer finds more relation/lineage facts in complex lateral, nested CTE, sample-data DDL, and PG-version-specific cases. Those are classified as token-event coverage deltas, not user-review questions.

The current acceptance line is:

- Root token-event golden must contain only facts it can extract from typed structural grammar.
- Versioned full-grammer golden remains the stronger version-specific correctness target.
- Future root token-event coverage work should continue by expanding typed grammar and visitors, not by restoring scanner/token-span inference.

## Verification

The following targeted checks passed after this audit update:

```bash
mvn -pl adaptor-postgres -am -Dtest=PostgresTokenEventDialectBoundaryTest#postgresTokenEventResolvesAggregateProjectionLineageThroughDerivedUpdateFrom,PostgresTokenEventDialectBoundaryTest#postgresTokenEventResolvesScalarAggregateSubqueryLineage,PostgresTokenEventDialectBoundaryTest#postgresTokenEventExtractsMergeInsertLineage -Dsurefire.failIfNoSpecifiedTests=false test

mvn -pl cli -am -Dtest=CorrectnessFixtureRunnerTest#allCorrectnessFixturesPassGoldenExpectations -DcorrectnessFixtureFilter='test-fixtures/correctness/postgres/' -Dsurefire.failIfNoSpecifiedTests=false test

mvn -pl cli -am -Dtest='DataLineageAuditGeneratorTest,CorrectnessSummaryGeneratorTest' -DupdateDataLineageAudit=true -DupdateCorrectnessSummary=true -Dsurefire.failIfNoSpecifiedTests=false test

mvn -pl cli -am -Dtest=FullGrammerSqlBehaviorTest#postgresqlCteProjectionThroughDerivedTableUsesSingleRowsetDefaultColumnSource -Dsurefire.failIfNoSpecifiedTests=false test

mvn -pl cli -am -Dtest=FullGrammerSqlBehaviorTest#mysqlPropagatesCumulativeDerivedProjectionToPhysicalSources,FullGrammerSqlBehaviorTest#postgresqlCteProjectionThroughDerivedTableUsesSingleRowsetDefaultColumnSource -Dsurefire.failIfNoSpecifiedTests=false test
```
