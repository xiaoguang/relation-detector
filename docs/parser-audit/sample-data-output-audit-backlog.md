# Sample-Data Output Audit Backlog

This document records the July 2026 sample-data JSON/SQL audit and the repair outcome. It is intentionally separate from generated reports: generated summaries describe current output, while this file explains why facts changed and which guardrails prevent the same defects from returning.

Audit inputs:

- Generated JSON: `relation-detector/target/sample-data-parser-cli/results/*.json`
- SQL assets: `relation-detector/sample-data/**`
- Parser comparison: `docs/parser-audit/parser-comparison-summary.md`
- MySQL version audit: `docs/parser-audit/mysql57-vs-mysql80-naming-review.md`

## Current Acceptance Checks

The repaired corpus passes all of the following checks:

- JSON summary counts match their direct, derived, and total arrays.
- `warning-codes.tsv` reports `NONE 0` for every parser category.
- `rawEvidence.source` contains repo-relative paths or canonical object names, never local absolute paths.
- Every relationship `NAMING_MATCH.evidenceRef` resolves to top-level `namingEvidence.id`.
- Naming evidence has raw structural provenance from DDL, SQL, routine, or metadata observations.
- No derived non-adjacent cycle, derived self-loop, or duplicate derived fact is present.
- Lineage evidence uses the stable `DATA_LINEAGE` evidence type.
- `SampleDataSchemaConsistencyTest` validates emitted relation/lineage endpoints against the corresponding version's typed DDL inventory.
- `DialectSqlAssetHygieneTest` rejects cross-dialect syntax and duplicate SQL Server natural-data assets.

These checks prove structural and schema consistency. Parser capability claims still come from targeted tests, full correctness golden, and semantic-equivalent benchmarks together.

## Resolved Items

### 1. SQL Server ALTER TABLE foreign keys

Status: `RESOLVED_PARSER_GAP`

- Token-event now has a typed `ALTER TABLE ... ADD ... FOREIGN KEY` path.
- Full-grammar DDL collection handles the same typed constraint context.
- `dbo.departments.manager_id -> dbo.employees.id` and DDL column inventory are covered by token-event and all five full-grammar versions.

### 2. SQL Server sample-data schema inconsistency

Status: `RESOLVED_SQL_ASSET_GAP`

The procedure, query, and data assets now use declared SQL Server columns. Budget, payment, AR/AP invoice, sales fact, shift assignment, return, tax, warehouse, and picking-task statements were rewritten against the DDL contract. The schema-consistency gate prevents an emitted endpoint from silently referring to an undeclared column.

### 3. SQL Server duplicate data files

Status: `RESOLVED_SQL_ASSET_GAP`

`03-data/04-return-damage-data.sql` and `03-data/05-massive-data-generator.sql` are distinct natural assets rather than copies of master data. Hygiene tests reject duplicate natural SQL content and the retired probe markers.

### 4. Common natural benchmark schema inconsistency

Status: `RESOLVED_SQL_ASSET_GAP`

The common schema and natural processes now agree on order identifiers, reconciliation amounts, stocktake quantities, cashier references, and payments. A portable `payments` table was added because the natural business corpus already contained real payment processes. Parser-coverage-only files remain outside the common natural CLI benchmark.

### 5. MySQL 5.7 mixed asset/parser defects

Status: `RESOLVED_AND_CLASSIFIED`

MySQL 5.7 assets now use declared commission, inventory, return, MRP, BOM, sales-fact, and aging columns. Nested return/batch rewrites no longer leak `product_batches.order_id`. Remaining 5.7/8.0 differences are classified as version or SQL-asset deltas; numeric equality is not an acceptance requirement.

### 6. Cross-dialect invalid invoice and position SQL

Status: `RESOLVED_SQL_ASSET_GAP`

- The three-way-matching query joins invoices through `three_way_matching.invoice_id`; it no longer references nonexistent `invoices.reference_id`.
- Employee onboarding derives salary bases from `(positions.min_salary + positions.max_salary) / 2`; the earlier `positions.base_salary` finding was a real asset defect, not an audit false alarm.

### 7. MySQL scalar-subquery scope and flow roles

Status: `RESOLVED_PARSER_GAP`

Token-event and full-grammar separate scalar-subquery projection sources (`VALUE`) from JOIN/WHERE/correlated locator sources (`CONTROL`). Nested alias scopes do not invent `product_batches.order_id`, and negative arithmetic keeps its actual operand.

### 8. CASE/IF/function transform semantics

Status: `RESOLVED_FOR_AUDITED_FAMILIES`

`LineageTransformClassifier` provides the shared precedence used by the affected MySQL, Oracle, and SQL Server analyzers. Audited aggregate, CASE/IF, arithmetic, coalesce, date/function, and concat expressions now keep their intended transform and flow roles. New expression families still require a targeted test before being declared covered.

### 9. Oracle token/full relation coverage

Status: `RESOLVED_PARSER_GAP`

The four confirmed natural relations are covered independently by token-event and full-grammar:

- `cashier_journals.reference_id -> sales_orders.id`
- `cashier_journals.reference_id -> purchase_orders.id`
- `purchase_orders.department_id -> departments.id`
- `purchase_receipt_items.order_item_id -> purchase_order_items.id`

The repair added typed `OPEN cursor FOR SELECT`, Oracle `LISTAGG ... WITHIN GROUP`, CTE projection scope resolution, SELECT-list scalar-subquery traversal, and unique-rowset resolution for unqualified subquery columns. Function-to-function equality such as `TO_CHAR(month) = TO_CHAR(date)` is explicitly excluded from direct column-equality relationships.

### 10. Transform parity infrastructure

Status: `RESOLVED_FOR_CONFIRMED_CASES`

Confirmed token/full transform mismatches are covered by unit tests and the shared classifier. This is not a claim that every vendor function has been classified; uncovered syntax belongs in a new evidence-backed audit item rather than a count-parity rule.

### 11. Naming-evidence provenance

Status: `RESOLVED_OBSERVABILITY_GAP`

`NamingEvidenceExtractor` preserves raw relationship, DDL-column, and metadata catalog observations. The grouped naming summary may use the generic label `naming heuristic`, but its `rawEvidence` points to concrete repo-relative SQL/DDL/object provenance. `namingEvidence` remains the only source of relationship `NAMING_MATCH` references.

### 12. Derived naming parser-mode consistency

Status: `RESOLVED_SHARED_CORE_GAP`

`DerivedPathInferenceService` indexes direct directional facts from the top-level naming pool. A derived relationship no longer depends on whether a parser happened to attach the direct `NAMING_MATCH` summary to an intermediate relationship. SQL Server token-event and all five full-grammar profiles now produce the same `sales_fact.category_dim_id -> product_categories.id` transitive naming fact, and every derived relationship reference resolves to the top-level naming evidence id.

### 13. Derived lineage canonical facts and observations

Status: `RESOLVED_OBSERVABILITY_GAP`

Derived lineage identity is the fact kind plus source, target, and canonical endpoint path. Direct edge `flowKind/transformType` variants are retained as raw observations instead of increasing `derivedDataLineageCount`. Non-adjacent endpoint re-entry is rejected; adjacent non-trivial self-updates remain eligible. The current 38 JSON outputs contain no duplicate canonical path or non-adjacent cycle.

### 14. Naming observation completeness

Status: `RESOLVED_OBSERVABILITY_GAP`

Endpoint matching is performed once per normalized endpoint pair, while evidence construction merges every relationship, DDL-column, and metadata observation from both endpoints. Exact parser duplicates are folded with an `occurrenceCount`; summary observation counts sum that value rather than merely counting raw-evidence objects.

### 15. Cross-parser CASE and scalar-subquery roles

Status: `RESOLVED_FOR_AUDITED_FAMILIES`

CASE result expressions are `VALUE`, CASE selectors/predicates are `CONTROL`; scalar-subquery projections and aggregate/function inputs are `VALUE`, while JOIN/WHERE/HAVING/correlated locator columns are `CONTROL`. Aggregate transform outranks an outer `COALESCE`, and typed function/operator contexts distinguish `FUNCTION_CALL`, `CONCAT_FORMAT`, arithmetic, and direct writes. Oracle token-event and all four full profiles now have identical audited relationship, lineage, and naming sets on the natural corpus.

### 16. Oracle and common natural SQL assets

Status: `RESOLVED_SQL_ASSET_GAP`

Oracle natural assets use `VIRTUAL` generated columns and omit empty parentheses on zero-argument routine definitions; unconfirmed `STORED` syntax is not treated as a positive feature. Common natural has one canonical `payments` declaration with all fields used by its processes. Hygiene and schema-consistency tests protect both rules.

## Validation

The repair is accepted only after all of these run on the same working tree:

```bash
# Fast, scoped feedback while changing a dialect or shared core.
bash relation-detector/scripts/test-scope.sh core,mysql,postgres,oracle,sqlserver,assets

# Final acceptance: full fixture golden, all parser CLI cases, report validation,
# and the complete Maven suite. The script uses bounded fixture/case parallelism
# and preserves deterministic result ordering.
bash relation-detector/scripts/verify-all.sh
```
