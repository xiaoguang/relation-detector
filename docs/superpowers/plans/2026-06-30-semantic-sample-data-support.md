# Semantic Sample Data Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add sample-data structures, procedures, data, and queries that can directly support the semantic-layer examples for payments, sales fact, region, fiscal calendar, and category dimensions.

**Architecture:** Keep the existing ERP OLTP model as the source of truth, and add a semantic-support/analytics mart layer that can be populated by procedures from OLTP tables. Mirror the same business capability into MySQL 8.0, PostgreSQL 18, and portable SQL, then add each new file to correctness golden for common, MySQL root, MySQL v8_0, PostgreSQL root, and PostgreSQL v16/v17/v18.

**Tech Stack:** SQL sample data, correctness fixture manifests, Maven correctness runner.

---

### Task 1: Add Semantic Support Tables

**Files:**
- Modify: `sample-data/mysql/8.0/01-schema/07-erp-deep-scenario-tables.sql`
- Modify: `sample-data/postgres/18/01-schema/07-erp-deep-scenario-tables.sql`
- Modify: `sample-data/portable/01-schema/03-erp-deep-scenario-tables.sql`

- [ ] Add `region_dim`, `fiscal_calendar`, `category_dim`, `payments`, and `sales_fact`.
- [ ] Use MySQL-native enums/generated columns only in MySQL, PostgreSQL enums/types only where the existing file already uses them, and portable SQL-neutral column types in portable.
- [ ] Link semantic tables back to existing ERP tables with explicit foreign keys when the relationship is physical.

### Task 2: Add Procedures That Populate Linked Business Data

**Files:**
- Modify: `sample-data/mysql/8.0/02-procedures/13-erp-deep-scenario-procedures.sql`
- Modify: `sample-data/postgres/18/02-procedures/13-erp-deep-scenario-procedures.sql`
- Modify: `sample-data/portable/02-processes/05-erp-deep-scenario-procedures.sql`
- Modify: `sample-data/portable/02-processes/06-erp-deep-scenario-process-bodies-for-golden.sql`

- [ ] Add calendar/category/region initialization procedures.
- [ ] Add complete sales-order/payment/refund procedures that insert into multiple linked ERP tables and semantic support tables.
- [ ] Add employee onboarding procedure that inserts linked HR/security/audit records.
- [ ] Keep procedure bodies parser-friendly and avoid dynamic SQL for golden coverage.

### Task 3: Add Rich Data

**Files:**
- Modify: `sample-data/mysql/8.0/03-data/07-erp-deep-scenario-data.sql`
- Modify: `sample-data/postgres/18/03-data/04-erp-deep-scenario-data.sql`
- Modify: `sample-data/portable/03-data/02-erp-deep-scenario-data.sql`

- [ ] Insert enough rows for all new semantic support tables.
- [ ] Include multiple regions, fiscal months, womenwear/non-womenwear categories, paid/failed/refunded payments, and inventory-risk examples.
- [ ] Ensure rows reference existing customers, products, warehouses, orders, and returns.

### Task 4: Add Example Queries

**Files:**
- Modify: `sample-data/mysql/8.0/04-queries/11-erp-deep-scenario-queries.sql`
- Modify: `sample-data/postgres/18/04-queries/12-erp-deep-scenario-queries.sql`
- Modify: `sample-data/portable/04-queries/02-erp-deep-scenario-queries.sql`

- [ ] Add queries for customer payment amount, active customers, inventory risk, refund rate, fiscal-year East China womenwear sales, RFM, failed payments, and semantic support reconciliation.
- [ ] Make each query return from physical sample-data tables, not imaginary documentation-only tables.

### Task 5: Add Correctness Golden

**Files:**
- Create fixture directories under `test-fixtures/correctness/common`
- Create fixture directories under `test-fixtures/correctness/mysql`
- Create fixture directories under `test-fixtures/correctness/mysql/v8_0`
- Create fixture directories under `test-fixtures/correctness/postgres`
- Create fixture directories under `test-fixtures/correctness/postgres/v16`, `v17`, and `v18`

- [ ] Add manifests for the new schema/data/procedure/query files.
- [ ] Run correctness with `-DupdateCorrectnessGold=true` for the new fixtures.
- [ ] Rerun correctness without update and inspect any semantic differences.

### Task 6: Verify

- [ ] Run fixture filters for semantic-support/sample-data additions.
- [ ] Run correctness summary and lineage audit generators.
- [ ] Run broad parser/correctness regression and full `mvn test`.
- [ ] Report parser counts and any review-needed SQL contexts.
