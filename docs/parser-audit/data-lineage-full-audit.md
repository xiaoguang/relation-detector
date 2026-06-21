# Data Lineage Full Audit

This file is generated from `test-fixtures/correctness` by `DataLineageAuditGeneratorTest`. Do not edit it by hand.

The report lists every correctness fixture and explains whether Data Lineage v1 already has golden coverage, can propose golden coverage, needs manual review, or is not applicable.

## Overview

| Classification | Count |
| --- | ---: |
| TOTAL | 229 |
| EXISTING_GOLD | 45 |
| SUGGESTED_GOLD | 0 |
| PENDING_REVIEW | 0 |
| NOT_APPLICABLE | 184 |

## `mysql-basic-correctness-case-01-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/ddl/show-create-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL SHOW CREATE TABLE for basic-correctness-case-01.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-table: case_01.GRAPH_CHECKPOINT
CREATE TABLE `GRAPH_CHECKPOINT` (
  `checkpoint_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `thread_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `node_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
```

## `mysql-basic-correctness-case-02-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/mysql/basic-correctness/case-02/ddl/show-create-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL SHOW CREATE TABLE for basic-correctness-case-02.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-table: case_02.GRAPH_CHECKPOINT
CREATE TABLE `GRAPH_CHECKPOINT` (
  `checkpoint_id` varchar(36) NOT NULL,
  `thread_id` varchar(36) NOT NULL,
  `node_id` varchar(255) DEFAULT NULL,
```

## `mysql-basic-correctness-case-03-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/mysql/basic-correctness/case-03/ddl/show-create-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL SHOW CREATE TABLE for basic-correctness-case-03.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-table: case_03.biz_bill_item_fact
CREATE TABLE `biz_bill_item_fact` (
  `factId` bigint NOT NULL AUTO_INCREMENT COMMENT '事实明细ID，语义层生成',
  `tenantId` bigint NOT NULL COMMENT '租户ID，来源: jsh_depot_head.tenant_id / jsh_depot_item.tenant_id',
  `sourceOrderId` bigint NOT NULL COMMENT '主单ID，来源: jsh_depot_head.id',
```

## `mysql-basic-correctness-case-04-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/mysql/basic-correctness/case-04/ddl/show-create-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL SHOW CREATE TABLE for basic-correctness-case-04.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-table: case_04.act_tool_info
CREATE TABLE `act_tool_info` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `parameters` longtext COLLATE utf8mb4_unicode_ci,
```

## `mysql-ddl-create-table-fk-index`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/mysql/ddl-create-table-fk-index/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
CREATE TABLE users (
  id bigint NOT NULL,
  email varchar(255),
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email)
);

CREATE TABLE orders (
```

## `mysql-official-alter-index-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-official-alter-index-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/invisible_indexes.test
-- - MySQL 8.0 Reference Manual: CREATE INDEX Statement
-- ALTER INDEX operations should be parsed without creating relationships.

CREATE TABLE order_search_tokens (
  token_id BIGINT NOT NULL,
```

## `mysql-official-complex-index-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/mysql/mysql-official-complex-index-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/functional_index.test
-- - mysql/mysql-server mysql-test/t/invisible_indexes.test
-- - MySQL 8.0 Reference Manual: CREATE INDEX Statement

CREATE TABLE customer_identity (
  id BIGINT NOT NULL,
```

## `mysql-official-functional-index-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/mysql/mysql-official-functional-index-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/functional_index.test
-- - MySQL 8.0 Reference Manual: CREATE INDEX Statement

CREATE TABLE metric_events (
  id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
```

## `mysql-official-index-options-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-official-index-options-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/create_index.test
-- - MySQL 8.0 Reference Manual: CREATE INDEX Statement
-- These index options are parser coverage only; they must not create FK-like
-- relationships by themselves.

CREATE TABLE search_documents (
```

## `mysql-official-invisible-index-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/mysql/mysql-official-invisible-index-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/invisible_indexes.test
-- - MySQL 8.0 Reference Manual: CREATE INDEX Statement

CREATE TABLE inventory_snapshots (
  snapshot_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
```

## `mysql-official-special-index-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-official-special-index-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/functional_index.test
-- - MySQL 8.0 Reference Manual: CREATE INDEX Statement
-- FULLTEXT, SPATIAL, and JSON expression indexes are useful DDL parser
-- coverage, but none of them is a relationship on its own.

CREATE TABLE geo_assets (
```

## `basic-correctness-case-01-functions-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-functions.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL information_schema.ROUTINES functions for basic-correctness-case-01.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

```

## `basic-correctness-case-01-procedure-internal-flush-buffer-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | requires cross-statement temporary-table lineage beyond Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL information_schema.ROUTINES procedures for basic-correctness-case-01.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_call_generate_po
BEGIN
    -- [1. 局部控制变量声明]
    DECLARE i INT DEFAULT 0;
```

## `basic-correctness-case-01-procedure-proc-batch-call-generate-po-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | requires cross-statement temporary-table lineage beyond Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL information_schema.ROUTINES procedures for basic-correctness-case-01.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_call_generate_po
BEGIN
    -- [1. 局部控制变量声明]
    DECLARE i INT DEFAULT 0;
```

## `basic-correctness-case-01-procedure-proc-batch-generate-purchase-inbound-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | requires cross-statement temporary-table lineage beyond Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL information_schema.ROUTINES procedures for basic-correctness-case-01.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_call_generate_po
BEGIN
    -- [1. 局部控制变量声明]
    DECLARE i INT DEFAULT 0;
```

## `basic-correctness-case-01-procedure-proc-batch-insert-purchase-requisition-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | requires cross-statement temporary-table lineage beyond Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL information_schema.ROUTINES procedures for basic-correctness-case-01.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_call_generate_po
BEGIN
    -- [1. 局部控制变量声明]
    DECLARE i INT DEFAULT 0;
```

## `basic-correctness-case-01-procedure-proc-batch-mock-retail-orders-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | requires cross-statement temporary-table lineage beyond Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL information_schema.ROUTINES procedures for basic-correctness-case-01.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_call_generate_po
BEGIN
    -- [1. 局部控制变量声明]
    DECLARE i INT DEFAULT 0;
```

## `basic-correctness-case-01-procedure-proc-create-order-mock-retail-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-create-order-mock-retail-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:jsh_material_current_stock.current_number,jsh_depot_item.oper_number->jsh_material_current_stock.current_number`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:jsh_material_current_stock.current_number,jsh_depot_item.oper_number->jsh_material_current_stock.current_number`

**Input Preview**

```sql
-- Generated from MySQL information_schema.ROUTINES procedures for basic-correctness-case-01.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_call_generate_po
BEGIN
    -- [1. 局部控制变量声明]
    DECLARE i INT DEFAULT 0;
```

## `basic-correctness-case-01-procedure-proc-generate-purchase-inbound-from-order-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | requires cross-statement temporary-table lineage beyond Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-inbound-from-order-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL information_schema.ROUTINES procedures for basic-correctness-case-01.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_call_generate_po
BEGIN
    -- [1. 局部控制变量声明]
    DECLARE i INT DEFAULT 0;
```

## `basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | requires cross-statement temporary-table lineage beyond Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL information_schema.ROUTINES procedures for basic-correctness-case-01.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_call_generate_po
BEGIN
    -- [1. 局部控制变量声明]
    DECLARE i INT DEFAULT 0;
```

## `basic-correctness-case-01-procedure-proc-init-yearly-weights-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | requires cross-statement temporary-table lineage beyond Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL information_schema.ROUTINES procedures for basic-correctness-case-01.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_call_generate_po
BEGIN
    -- [1. 局部控制变量声明]
    DECLARE i INT DEFAULT 0;
```

## `basic-correctness-case-01-procedure-proc-insert-purchase-requisition-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | requires cross-statement temporary-table lineage beyond Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL information_schema.ROUTINES procedures for basic-correctness-case-01.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_call_generate_po
BEGIN
    -- [1. 局部控制变量声明]
    DECLARE i INT DEFAULT 0;
```

## `basic-correctness-case-01-procedure-proc-refresh-org-pdf-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-refresh-org-pdf-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:CUMULATIVE:jsh_temp_org_pdf.weight->jsh_temp_org_pdf.cdf_end`
- `CONTROL:CASE_WHEN:jsh_organization.org_no->jsh_temp_org_pdf.weight`
- `VALUE:DIRECT:jsh_organization.id->jsh_temp_org_pdf.org_id`
- `VALUE:DIRECT:jsh_organization.org_abr->jsh_temp_org_pdf.remark`

**Extractor Candidate Fingerprints**

- `CONTROL:CASE_WHEN:jsh_organization.org_no->jsh_temp_org_pdf.weight`
- `VALUE:CUMULATIVE:jsh_temp_org_pdf.weight->jsh_temp_org_pdf.cdf_end`
- `VALUE:DIRECT:jsh_organization.id->jsh_temp_org_pdf.org_id`
- `VALUE:DIRECT:jsh_organization.org_abr->jsh_temp_org_pdf.remark`

**Input Preview**

```sql
-- Generated from MySQL information_schema.ROUTINES procedures for basic-correctness-case-01.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_call_generate_po
BEGIN
    -- [1. 局部控制变量声明]
    DECLARE i INT DEFAULT 0;
```

## `basic-correctness-case-01-procedure-proc-simulate-yearly-sales-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | requires cross-statement temporary-table lineage beyond Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL information_schema.ROUTINES procedures for basic-correctness-case-01.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_call_generate_po
BEGIN
    -- [1. 局部控制变量声明]
    DECLARE i INT DEFAULT 0;
```

## `basic-correctness-case-01-procedure-proc-worker-daily-distribution-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | requires cross-statement temporary-table lineage beyond Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-worker-daily-distribution-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL information_schema.ROUTINES procedures for basic-correctness-case-01.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_call_generate_po
BEGIN
    -- [1. 局部控制变量声明]
    DECLARE i INT DEFAULT 0;
```

## `basic-correctness-case-01-procedure-sp-fill-biz-bill-item-fact-new-with-progress-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | requires cross-statement temporary-table lineage beyond Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-sp-fill-biz-bill-item-fact-new-with-progress-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL information_schema.ROUTINES procedures for basic-correctness-case-01.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_call_generate_po
BEGIN
    -- [1. 局部控制变量声明]
    DECLARE i INT DEFAULT 0;
```

## `basic-correctness-case-01-procedure-sp-sync-retail-out-fact-batch-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | requires cross-statement temporary-table lineage beyond Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL information_schema.ROUTINES procedures for basic-correctness-case-01.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_call_generate_po
BEGIN
    -- [1. 局部控制变量声明]
    DECLARE i INT DEFAULT 0;
```

## `common-sql-basic-join`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/common/sql-basic-join/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
SELECT *
FROM orders o
JOIN users u ON o.user_id = u.id;
```

## `common-sql-join-using`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/common/sql-join-using/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
SELECT *
FROM orders o
JOIN order_tags ot USING (order_id);
```

## `mysql-basic-correctness-case-01-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `NATIVE_LOG` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/performance-schema-statements.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL statement log sources for basic-correctness-case-01.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

```

## `mysql-basic-correctness-case-02-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `NATIVE_LOG` |
| Input | `test-fixtures/mysql/basic-correctness/case-02/sql/performance-schema-statements.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL statement log sources for basic-correctness-case-02.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: performance_schema.events_statements_history
/* ApplicationName=DBeaver 26.0.3 - Metadata */ SELECT DISTINCT A.REFERENCED_TABLE_SCHEMA AS PKTABLE_CAT,NULL AS PKTABLE_SCHEM, A.REFERENCED_TABLE_NAME AS PKTABLE_NAME, A.REFERENCED_COLUMN_NAME AS PKCOLUMN_NAME, A.TABLE_SCHEMA AS FKTABLE_CAT, NULL AS FKTABLE_SCHEM, A.TABLE_NAME AS FKTABLE_NAME, A.COLUMN_NAME AS FKCOLUMN_NAME, A.ORDINAL_POSITION AS KEY_SEQ,CASE WHEN R.UPDATE_RULE='CASCADE' THEN 0 WHEN R.UPDATE_RULE='SET NULL' THEN 2 WHEN R.UPDATE_RULE='SET DEFAULT' THEN 4 WHEN R.UPDATE_RULE='RESTRICT' THEN 1 WHEN R.UPDATE_RULE='NO ACTION' THEN 1 ELSE 1 END  AS UPDATE_RULE,CASE WHE
```

## `mysql-basic-correctness-case-03-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `NATIVE_LOG` |
| Input | `test-fixtures/mysql/basic-correctness/case-03/sql/performance-schema-statements.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL statement log sources for basic-correctness-case-03.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: performance_schema.events_statements_history
/* ApplicationName=DBeaver 26.0.3 - Metadata */ SELECT DISTINCT A.REFERENCED_TABLE_SCHEMA AS PKTABLE_CAT,NULL AS PKTABLE_SCHEM, A.REFERENCED_TABLE_NAME AS PKTABLE_NAME, A.REFERENCED_COLUMN_NAME AS PKCOLUMN_NAME, A.TABLE_SCHEMA AS FKTABLE_CAT, NULL AS FKTABLE_SCHEM, A.TABLE_NAME AS FKTABLE_NAME, A.COLUMN_NAME AS FKCOLUMN_NAME, A.ORDINAL_POSITION AS KEY_SEQ,CASE WHEN R.UPDATE_RULE='CASCADE' THEN 0 WHEN R.UPDATE_RULE='SET NULL' THEN 2 WHEN R.UPDATE_RULE='SET DEFAULT' THEN 4 WHEN R.UPDATE_RULE='RESTRICT' THEN 1 WHEN R.UPDATE_RULE='NO ACTION' THEN 1 ELSE 1 END  AS UPDATE_RULE,CASE WHE
```

## `mysql-basic-correctness-case-04-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `NATIVE_LOG` |
| Input | `test-fixtures/mysql/basic-correctness/case-04/sql/performance-schema-statements.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from MySQL statement log sources for basic-correctness-case-04.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: performance_schema.events_statements_history
/* ApplicationName=DBeaver 26.0.3 - Metadata */ SELECT DISTINCT A.REFERENCED_TABLE_SCHEMA AS PKTABLE_CAT,NULL AS PKTABLE_SCHEM, A.REFERENCED_TABLE_NAME AS PKTABLE_NAME, A.REFERENCED_COLUMN_NAME AS PKCOLUMN_NAME, A.TABLE_SCHEMA AS FKTABLE_CAT, NULL AS FKTABLE_SCHEM, A.TABLE_NAME AS FKTABLE_NAME, A.COLUMN_NAME AS FKCOLUMN_NAME, A.ORDINAL_POSITION AS KEY_SEQ,CASE WHEN R.UPDATE_RULE='CASCADE' THEN 0 WHEN R.UPDATE_RULE='SET NULL' THEN 2 WHEN R.UPDATE_RULE='SET DEFAULT' THEN 4 WHEN R.UPDATE_RULE='RESTRICT' THEN 1 WHEN R.UPDATE_RULE='NO ACTION' THEN 1 ELSE 1 END  AS UPDATE_RULE,CASE WHE
```

## `mysql-business-cross-border-reconciliation-procedure-comma-subquery-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/mysql-business-cross-border-reconciliation-procedure-comma-subquery-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- MySQL business procedure companion to mysql-business-cross-border-reconciliation-procedure-sql.
-- INNER rowsets are rewritten as comma rowsets. Non-INNER semantics are represented with
-- correlated scalar subqueries / EXISTS instead of forced comma joins.
-- relation-detector-fixture-source: PROCEDURE:finance.sp_cross_border_reconciliation_engine_comma_subquery
CREATE PROCEDURE sp_cross_border_reconciliation_engine_comma_subquery(
    IN p_input_matrix_json JSON,
    IN p_target_currency VARCHAR(10),
    IN p_risk_threshold NUMERIC(5,2)
```

## `mysql-business-cross-border-reconciliation-procedure-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/mysql-business-cross-border-reconciliation-procedure-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- MySQL business procedure case: JSON_TABLE input, CTEs, simulated FULL OUTER JOIN
-- via LEFT/RIGHT UNION ALL, correlated subquery, and final comma rowset filtering.
-- Parameter JSON, literal filters, dynamic result set columns, and Data Lineage are
-- semantic references only; formal relationship gold records physical table relationships.
-- relation-detector-fixture-source: PROCEDURE:finance.sp_cross_border_reconciliation_engine
CREATE PROCEDURE sp_cross_border_reconciliation_engine(
    IN p_input_matrix_json JSON,
    IN p_target_currency VARCHAR(10),
```

## `mysql-business-financial-asset-wash-procedure-comma-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/mysql-business-financial-asset-wash-procedure-comma-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-business-financial-asset-wash-procedure-comma-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:account_balances.max_credit_limit->account_balances.adjusted_limit`
- `VALUE:CONCAT_FORMAT:dormant_risk_scores.country_code,dormant_risk_scores.days_since_last_active,dormant_risk_scores.wealth_tile,user_financial_snapshot.primary_categories->account_balances.compliance_notes`
- `VALUE:COALESCE:account_balances.risk_flags->account_balances.risk_flags`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:account_balances.max_credit_limit->account_balances.adjusted_limit`
- `VALUE:COALESCE:account_balances.risk_flags->account_balances.risk_flags`
- `VALUE:CONCAT_FORMAT:dormant_risk_scores.country_code,dormant_risk_scores.days_since_last_active,dormant_risk_scores.wealth_tile,user_financial_snapshot.primary_categories->account_balances.compliance_notes`

**Input Preview**

```sql
-- MySQL business procedure case equivalent to mysql-business-financial-asset-wash-procedure-sql.
-- INNER JOIN portions are rewritten as comma rowsets plus WHERE equality predicates.
-- Expected fingerprints must match mysql-business-financial-asset-wash-procedure-sql.
-- relation-detector-fixture-source: PROCEDURE:finance.sp_financial_asset_wash_update_comma
CREATE PROCEDURE sp_financial_asset_wash_update_comma(
    IN p_input_ledger_json JSON,
    IN p_max_limit_cap NUMERIC(16,4)
)
```

## `mysql-business-financial-asset-wash-procedure-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/mysql-business-financial-asset-wash-procedure-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-business-financial-asset-wash-procedure-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:account_balances.max_credit_limit->account_balances.adjusted_limit`
- `VALUE:CONCAT_FORMAT:dormant_risk_scores.country_code,dormant_risk_scores.days_since_last_active,dormant_risk_scores.wealth_tile,user_financial_snapshot.primary_categories->account_balances.compliance_notes`
- `VALUE:COALESCE:account_balances.risk_flags->account_balances.risk_flags`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:account_balances.max_credit_limit->account_balances.adjusted_limit`
- `VALUE:COALESCE:account_balances.risk_flags->account_balances.risk_flags`
- `VALUE:CONCAT_FORMAT:dormant_risk_scores.country_code,dormant_risk_scores.days_since_last_active,dormant_risk_scores.wealth_tile,user_financial_snapshot.primary_categories->account_balances.compliance_notes`

**Input Preview**

```sql
-- MySQL business procedure case: JSON_TABLE input, nested CTEs, window functions,
-- multi-table UPDATE, and a comma rowset against policy configuration.
-- Parameter JSON and Data Lineage are semantic references only; formal relationship
-- gold records physical table relationships.
-- relation-detector-fixture-source: PROCEDURE:finance.sp_financial_asset_wash_update
CREATE PROCEDURE sp_financial_asset_wash_update(
    IN p_input_ledger_json JSON,
    IN p_max_limit_cap NUMERIC(16,4)
```

## `mysql-commerce-promotion-update-comma-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-commerce-promotion-update-comma-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-commerce-promotion-update-comma-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:products.original_price->products.promo_price`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:products.original_price->products.promo_price`

**Input Preview**

```sql
UPDATE products p, shops s, merchants m, discount_policies dp
SET
    p.promo_price = p.original_price * 0.90,
    p.is_on_sale = 1,
    p.updated_at = NOW()
WHERE
    p.shop_id = s.id
    AND s.merchant_id = m.id
```

## `mysql-commerce-promotion-update-explicit-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-commerce-promotion-update-explicit-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-commerce-promotion-update-explicit-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:products.original_price->products.promo_price`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:products.original_price->products.promo_price`

**Input Preview**

```sql
UPDATE products p
INNER JOIN shops s ON p.shop_id = s.id
INNER JOIN merchants m ON s.merchant_id = m.id
INNER JOIN discount_policies dp ON p.category_id = dp.category_id
SET
    p.promo_price = p.original_price * 0.90,
    p.is_on_sale = 1,
    p.updated_at = NOW()
```

## `mysql-invalid-orders-delete-comma-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-invalid-orders-delete-comma-join-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
DELETE o, oi
FROM orders o, order_items oi, users u
WHERE
    o.id = oi.order_id
    AND o.user_id = u.id
    AND o.payment_status = 'UNPAID'
    AND o.created_at < NOW() - INTERVAL 7 DAY
    AND u.risk_level = 'HIGH';
```

## `mysql-invalid-orders-delete-explicit-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-invalid-orders-delete-explicit-join-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
DELETE o, oi
FROM orders o
INNER JOIN order_items oi ON o.id = oi.order_id
INNER JOIN users u ON o.user_id = u.id
WHERE
    o.payment_status = 'UNPAID'
    AND o.created_at < NOW() - INTERVAL 7 DAY
    AND u.risk_level = 'HIGH';
```

## `mysql-official-cte-dml-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-official-cte-dml-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-official-cte-dml-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:accounts.id->orders.audit_account_id`

**Extractor Candidate Fingerprints**

- `VALUE:DIRECT:accounts.id->orders.audit_account_id`

**Input Preview**

```sql
-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/include/with_non_recursive.inc
-- - MySQL 8.0 Reference Manual: WITH (Common Table Expressions), UPDATE, DELETE
-- CTE rowsets are logical rowsets. They must not be emitted as physical tables
-- even when the CTE feeds UPDATE or DELETE.

WITH candidate_orders(order_id, user_id) AS (
```

## `mysql-official-cte-nested-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-official-cte-nested-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/include/with_non_recursive.inc
-- - MySQL 8.0 Reference Manual: WITH (Common Table Expressions)
-- The case focuses on CTE scope and lineage. CTE names must not be emitted as
-- physical tables.

WITH recent_orders AS (
```

## `mysql-official-derived-subquery-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-official-derived-subquery-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/derived.test
-- - mysql/mysql-server mysql-test/t/subquery_exists.test
-- - MySQL 8.0 Reference Manual: Subqueries and Derived Tables
-- Derived aliases must not be emitted as physical tables.

SELECT m2.id, d.pla_id
```

## `mysql-official-join-edge-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-official-join-edge-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/join.test
-- - MySQL 8.0 Reference Manual: JOIN Clause
-- This file focuses on MySQL-specific join forms that are easy to regress:
-- STRAIGHT_JOIN, NATURAL JOIN, nested parenthesized joins, index hints, and
-- ODBC escaped outer joins.
```

## `mysql-official-join-matrix-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-official-join-matrix-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/join.test
-- - MySQL 8.0 Reference Manual: JOIN Clause
-- This file keeps only standalone SQL statements that exercise relation extraction.

SELECT o.id, oi.order_id
FROM orders AS o
```

## `mysql-official-lateral-derived-edge-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-official-lateral-derived-edge-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/derived.test
-- - MySQL 8.0 Reference Manual: Derived Tables and Lateral Derived Tables
-- Derived and lateral aliases are logical rowsets and must not be emitted as
-- physical tables.

SELECT o.id, projected.product_id
```

## `mysql-official-recursive-cte-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-official-recursive-cte-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/include/with_recursive.inc
-- - MySQL 8.0 Reference Manual: WITH (Common Table Expressions)
-- Recursive CTE rowsets are logical rowsets; they must not be emitted as
-- physical tables.

WITH RECURSIVE employee_tree AS (
```

## `mysql-official-subquery-edge-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-official-subquery-edge-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/subquery_exists.test
-- - MySQL 8.0 Reference Manual: Subqueries
-- This fixture stresses row/tuple subqueries, ANY/SOME/ALL, correlated
-- subqueries, and scalar subquery equality.

SELECT o.id
```

## `mysql-orphan-reviews-delete-left-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-orphan-reviews-delete-left-join-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
DELETE pr
FROM product_reviews pr
LEFT JOIN products p ON pr.product_id = p.id
WHERE
    p.id IS NULL
    AND pr.created_at < NOW() - INTERVAL 1 MONTH;
```

## `mysql-orphan-reviews-delete-not-exists-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-orphan-reviews-delete-not-exists-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
DELETE pr
FROM product_reviews pr
WHERE
    NOT EXISTS (
        SELECT 1
        FROM products p
        WHERE p.id = pr.product_id
    )
```

## `mysql-sql-cte-lateral`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/sql-cte-lateral/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
WITH recent_orders AS (
  SELECT o.id AS order_id, o.user_id
  FROM `orders` AS o
  WHERE o.created_at >= CURRENT_DATE - INTERVAL 7 DAY
)
SELECT ro.order_id, u.email
FROM recent_orders ro
JOIN LATERAL (
```

## `mysql-sql-delete-left-join`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/sql-delete-left-join/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
DELETE FROM o
USING orders AS o
LEFT JOIN users AS u ON o.user_id = u.id
WHERE u.id IS NULL;
```

## `mysql-sql-multi-table-update`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/sql-multi-table-update/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
UPDATE orders o, users u
JOIN accounts a ON u.account_id = a.id
SET o.reviewed_at = CURRENT_TIMESTAMP
WHERE o.user_id = u.id
  AND o.status = 'PAID'
  AND a.closed_at IS NULL;
```

## `mysql-sql-system-log-noise`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `NATIVE_LOG` |
| Input | `test-fixtures/correctness/mysql/sql-system-log-noise/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
SELECT kcu.TABLE_SCHEMA, kcu.TABLE_NAME, tc.CONSTRAINT_TYPE
FROM information_schema.KEY_COLUMN_USAGE kcu
JOIN information_schema.TABLE_CONSTRAINTS tc
  ON kcu.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
 AND kcu.CONSTRAINT_NAME = tc.CONSTRAINT_NAME;
```

## `mysql-supply-chain-update-comma-and-subquery-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-supply-chain-update-comma-and-subquery-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-supply-chain-update-comma-and-subquery-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:AGGREGATE:supplier_manifests.supply_price,warehouse_inventory.product_id,supplier_manifests.product_id,warehouse_inventory.primary_supplier_id,supplier_manifests.supplier_id,supplier_manifests.manifest_id,warehouse_inventory.default_unit_cost,order_items.quantity->order_items.estimated_cost`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

**Extractor Candidate Fingerprints**

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:AGGREGATE:supplier_manifests.supply_price,warehouse_inventory.product_id,supplier_manifests.product_id,warehouse_inventory.primary_supplier_id,supplier_manifests.supplier_id,supplier_manifests.manifest_id,warehouse_inventory.default_unit_cost,order_items.quantity->order_items.estimated_cost`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

**Input Preview**

```sql
UPDATE warehouse_inventory wi,
       bin_locations bl,
       order_items oi,
       (
           SELECT
               o.id AS order_id,
               o.customer_id,
               c.risk_score,
```

## `mysql-supply-chain-update-explicit-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-supply-chain-update-explicit-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-supply-chain-update-explicit-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:AGGREGATE:supplier_manifests.supply_price,warehouse_inventory.default_unit_cost,order_items.quantity->order_items.estimated_cost`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

**Extractor Candidate Fingerprints**

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:AGGREGATE:supplier_manifests.supply_price,warehouse_inventory.default_unit_cost,order_items.quantity->order_items.estimated_cost`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

**Input Preview**

```sql
UPDATE warehouse_inventory wi
INNER JOIN bin_locations bl ON wi.bin_id = bl.id AND bl.zone_type = 'PICKING'
INNER JOIN order_items oi ON wi.product_id = oi.product_id
INNER JOIN (
    SELECT
        o.id AS order_id,
        o.customer_id,
        c.risk_score,
```

## `mysql-user-spending-comma-join-update-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-user-spending-comma-join-update-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-user-spending-comma-join-update-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:AGGREGATE:orders.pay_amount->users.level`
- `VALUE:AGGREGATE:orders.pay_amount->users.total_spent`

**Extractor Candidate Fingerprints**

- `CONTROL:AGGREGATE:orders.pay_amount->users.level`
- `VALUE:AGGREGATE:orders.pay_amount->users.total_spent`

**Input Preview**

```sql
UPDATE users u,
(
    SELECT user_id, SUM(pay_amount) AS actual_total
    FROM orders
    WHERE order_status = 'PAID'
    GROUP BY user_id
) o_summary
SET
```

## `mysql-user-spending-left-join-update-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-user-spending-left-join-update-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-user-spending-left-join-update-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:AGGREGATE:orders.pay_amount->users.level`
- `VALUE:AGGREGATE:orders.pay_amount->users.total_spent`

**Extractor Candidate Fingerprints**

- `CONTROL:AGGREGATE:orders.pay_amount->users.level`
- `VALUE:AGGREGATE:orders.pay_amount->users.total_spent`

**Input Preview**

```sql
UPDATE users u
LEFT JOIN (
    SELECT user_id, SUM(pay_amount) AS actual_total
    FROM orders
    WHERE order_status = 'PAID'
    GROUP BY user_id
) o_summary ON u.id = o_summary.user_id
SET
```

## `postgres-basic-correctness-case-01-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/postgres-basic-correctness-case-01-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from PostgreSQL catalog for postgres-basic-correctness-case-01.
-- Refresh with PostgresBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-table: case_01.auth_permission
CREATE TABLE case_01.auth_permission (
  id integer DEFAULT nextval('auth_permission_id_seq'::regclass) NOT NULL,
  name character varying(255) NOT NULL,
  content_type_id integer NOT NULL,
```

## `postgres-ddl-alter-table-fk`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/ddl-alter-table-fk/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
CREATE TABLE users (
  id bigint PRIMARY KEY,
  email text
);

CREATE TABLE orders (
  id bigint PRIMARY KEY,
  user_id bigint NOT NULL
```

## `postgres-ddl-partial-index-boundary`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/ddl-partial-index-boundary/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
CREATE TABLE users (
  id BIGINT,
  email TEXT
);

CREATE TABLE orders (
  user_email TEXT,
  CONSTRAINT fk_orders_users_email
```

## `postgres-ddl-unique-include-index`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/ddl-unique-include-index/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
CREATE TABLE accounts (
  id BIGINT,
  account_no TEXT,
  deleted_at TIMESTAMP
);

CREATE TABLE invoices (
  account_no TEXT,
```

## `postgres-official-alter-index-boundary-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/postgres-official-alter-index-boundary-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: create_index.sql and ALTER INDEX.
CREATE TABLE users (
  id BIGINT PRIMARY KEY,
  email TEXT
);

CREATE TABLE password_resets (
  id BIGINT PRIMARY KEY,
```

## `postgres-official-expression-access-method-index-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/postgres-official-expression-access-method-index-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: create_index.sql and CREATE INDEX.
CREATE TABLE documents (
  id BIGINT PRIMARY KEY,
  account_id BIGINT,
  body TEXT,
  tags TEXT[],
  geom BOX,
  payload JSONB
```

## `postgres-official-index-include-partial-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/postgres-official-index-include-partial-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: create_index.sql and CREATE INDEX.
CREATE TABLE accounts (
  id BIGINT PRIMARY KEY,
  account_no TEXT,
  deleted_at TIMESTAMP
);

CREATE TABLE ledger_entries (
```

## `postgres-official-index-opclass-expression-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/postgres-official-index-opclass-expression-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official CREATE INDEX inspired: expression indexes, function-call
-- expressions, opclass parameters, collation, sort order, and NULLS options.
CREATE TABLE customers (
  id BIGINT PRIMARY KEY,
  external_ref TEXT UNIQUE,
  email TEXT,
  metadata JSONB
);
```

## `postgres-official-index-options-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/postgres-official-index-options-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: create_index.sql and CREATE INDEX.
CREATE TABLE public.users (
  id BIGINT PRIMARY KEY,
  email TEXT,
  locale TEXT
);

CREATE TABLE public.orders (
```

## `postgres-official-index-partition-boundary-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/postgres-official-index-partition-boundary-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official CREATE INDEX inspired: ONLY and partition-oriented index
-- syntax. ALTER INDEX ATTACH PARTITION is a parser-stability boundary and must
-- not create relationships.
CREATE TABLE tenants (
  id BIGINT PRIMARY KEY
);

CREATE TABLE tenant_orders (
```

## `postgres-official-index-storage-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/postgres-official-index-storage-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official create_index.sql/docs inspired: storage parameters,
-- TABLESPACE, and access-method-specific options. Complex indexes should not
-- create FK-like relations by themselves; the declared FK below is the only
-- expected relationship.
CREATE TABLE public.users (
  id BIGINT PRIMARY KEY,
  email TEXT
);
```

## `postgres17-basic-correctness-case-01-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-basic-correctness-case-01-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from PostgreSQL catalog for postgres-basic-correctness-case-01.
-- Refresh with PostgresBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-table: case_01.auth_permission
CREATE TABLE case_01.auth_permission (
  id integer DEFAULT nextval('auth_permission_id_seq'::regclass) NOT NULL,
  name character varying(255) NOT NULL,
  content_type_id integer NOT NULL,
```

## `postgres17-ddl-alter-table-fk`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v17/ddl-alter-table-fk/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
CREATE TABLE users (
  id bigint PRIMARY KEY,
  email text
);

CREATE TABLE orders (
  id bigint PRIMARY KEY,
  user_id bigint NOT NULL
```

## `postgres17-ddl-partial-index-boundary`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v17/ddl-partial-index-boundary/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
CREATE TABLE users (
  id BIGINT,
  email TEXT
);

CREATE TABLE orders (
  user_email TEXT,
  CONSTRAINT fk_orders_users_email
```

## `postgres17-ddl-unique-include-index`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v17/ddl-unique-include-index/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
CREATE TABLE accounts (
  id BIGINT,
  account_no TEXT,
  deleted_at TIMESTAMP
);

CREATE TABLE invoices (
  account_no TEXT,
```

## `postgres17-official-alter-index-boundary-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-official-alter-index-boundary-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: create_index.sql and ALTER INDEX.
CREATE TABLE users (
  id BIGINT PRIMARY KEY,
  email TEXT
);

CREATE TABLE password_resets (
  id BIGINT PRIMARY KEY,
```

## `postgres17-official-expression-access-method-index-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-official-expression-access-method-index-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: create_index.sql and CREATE INDEX.
CREATE TABLE documents (
  id BIGINT PRIMARY KEY,
  account_id BIGINT,
  body TEXT,
  tags TEXT[],
  geom BOX,
  payload JSONB
```

## `postgres17-official-index-include-partial-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-official-index-include-partial-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: create_index.sql and CREATE INDEX.
CREATE TABLE accounts (
  id BIGINT PRIMARY KEY,
  account_no TEXT,
  deleted_at TIMESTAMP
);

CREATE TABLE ledger_entries (
```

## `postgres17-official-index-opclass-expression-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-official-index-opclass-expression-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official CREATE INDEX inspired: expression indexes, function-call
-- expressions, opclass parameters, collation, sort order, and NULLS options.
CREATE TABLE customers (
  id BIGINT PRIMARY KEY,
  external_ref TEXT UNIQUE,
  email TEXT,
  metadata JSONB
);
```

## `postgres17-official-index-options-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-official-index-options-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: create_index.sql and CREATE INDEX.
CREATE TABLE public.users (
  id BIGINT PRIMARY KEY,
  email TEXT,
  locale TEXT
);

CREATE TABLE public.orders (
```

## `postgres17-official-index-partition-boundary-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-official-index-partition-boundary-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official CREATE INDEX inspired: ONLY and partition-oriented index
-- syntax. ALTER INDEX ATTACH PARTITION is a parser-stability boundary and must
-- not create relationships.
CREATE TABLE tenants (
  id BIGINT PRIMARY KEY
);

CREATE TABLE tenant_orders (
```

## `postgres17-official-index-storage-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-official-index-storage-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official create_index.sql/docs inspired: storage parameters,
-- TABLESPACE, and access-method-specific options. Complex indexes should not
-- create FK-like relations by themselves; the declared FK below is the only
-- expected relationship.
CREATE TABLE public.users (
  id BIGINT PRIMARY KEY,
  email TEXT
);
```

## `postgres18-basic-correctness-case-01-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-basic-correctness-case-01-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from PostgreSQL catalog for postgres-basic-correctness-case-01.
-- Refresh with PostgresBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-table: case_01.auth_permission
CREATE TABLE case_01.auth_permission (
  id integer DEFAULT nextval('auth_permission_id_seq'::regclass) NOT NULL,
  name character varying(255) NOT NULL,
  content_type_id integer NOT NULL,
```

## `postgres18-ddl-alter-table-fk`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v18/ddl-alter-table-fk/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
CREATE TABLE users (
  id bigint PRIMARY KEY,
  email text
);

CREATE TABLE orders (
  id bigint PRIMARY KEY,
  user_id bigint NOT NULL
```

## `postgres18-ddl-partial-index-boundary`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v18/ddl-partial-index-boundary/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
CREATE TABLE users (
  id BIGINT,
  email TEXT
);

CREATE TABLE orders (
  user_email TEXT,
  CONSTRAINT fk_orders_users_email
```

## `postgres18-ddl-unique-include-index`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v18/ddl-unique-include-index/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
CREATE TABLE accounts (
  id BIGINT,
  account_no TEXT,
  deleted_at TIMESTAMP
);

CREATE TABLE invoices (
  account_no TEXT,
```

## `postgres18-official-alter-index-boundary-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-official-alter-index-boundary-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: create_index.sql and ALTER INDEX.
CREATE TABLE users (
  id BIGINT PRIMARY KEY,
  email TEXT
);

CREATE TABLE password_resets (
  id BIGINT PRIMARY KEY,
```

## `postgres18-official-expression-access-method-index-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-official-expression-access-method-index-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: create_index.sql and CREATE INDEX.
CREATE TABLE documents (
  id BIGINT PRIMARY KEY,
  account_id BIGINT,
  body TEXT,
  tags TEXT[],
  geom BOX,
  payload JSONB
```

## `postgres18-official-index-include-partial-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-official-index-include-partial-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: create_index.sql and CREATE INDEX.
CREATE TABLE accounts (
  id BIGINT PRIMARY KEY,
  account_no TEXT,
  deleted_at TIMESTAMP
);

CREATE TABLE ledger_entries (
```

## `postgres18-official-index-opclass-expression-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-official-index-opclass-expression-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official CREATE INDEX inspired: expression indexes, function-call
-- expressions, opclass parameters, collation, sort order, and NULLS options.
CREATE TABLE customers (
  id BIGINT PRIMARY KEY,
  external_ref TEXT UNIQUE,
  email TEXT,
  metadata JSONB
);
```

## `postgres18-official-index-options-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-official-index-options-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: create_index.sql and CREATE INDEX.
CREATE TABLE public.users (
  id BIGINT PRIMARY KEY,
  email TEXT,
  locale TEXT
);

CREATE TABLE public.orders (
```

## `postgres18-official-index-partition-boundary-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-official-index-partition-boundary-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official CREATE INDEX inspired: ONLY and partition-oriented index
-- syntax. ALTER INDEX ATTACH PARTITION is a parser-stability boundary and must
-- not create relationships.
CREATE TABLE tenants (
  id BIGINT PRIMARY KEY
);

CREATE TABLE tenant_orders (
```

## `postgres18-official-index-storage-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-official-index-storage-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official create_index.sql/docs inspired: storage parameters,
-- TABLESPACE, and access-method-specific options. Complex indexes should not
-- create FK-like relations by themselves; the declared FK below is the only
-- expected relationship.
CREATE TABLE public.users (
  id BIGINT PRIMARY KEY,
  email TEXT
);
```

## `postgres18-temporal-constraints-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-temporal-constraints-ddl/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL 18 temporal constraints: PERIOD columns are temporal coverage metadata.
CREATE TABLE subscriptions (
    customer_id bigint NOT NULL,
    valid_at tstzrange NOT NULL,
    PRIMARY KEY (customer_id, valid_at WITHOUT OVERLAPS)
);

CREATE TABLE invoices (
```

## `postgres18-virtual-generated-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-virtual-generated-ddl/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL 18 virtual generated column DDL.
CREATE TABLE ledger_entries (
    id bigint PRIMARY KEY,
    amount numeric NOT NULL,
    fee numeric NOT NULL,
    total numeric GENERATED ALWAYS AS (amount + fee) VIRTUAL
);
```

## `postgres-basic-correctness-case-01-objects-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-basic-correctness-case-01-objects-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from PostgreSQL SQL sources for postgres-basic-correctness-case-01.
-- Refresh with PostgresBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: TRIGGER:pg_trigger:rna.rna_audit
CREATE TRIGGER rna_audit BEFORE UPDATE ON case_01.rna FOR EACH ROW EXECUTE FUNCTION trigger_fct_rna_audit()
-- relation-detector-fixture-end

-- relation-detector-fixture-source: TRIGGER:pg_trigger:rnc_database.rnc_database_audit
```

## `postgres-basic-correctness-case-01-statements-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `NATIVE_LOG` |
| Input | `test-fixtures/correctness/postgres/postgres-basic-correctness-case-01-statements-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from PostgreSQL SQL sources for postgres-basic-correctness-case-01.
-- Refresh with PostgresBasicCorrectnessFixtureExporter.

```

## `postgres-business-account-balances-financial-cte-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-account-balances-financial-cte-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-business-account-balances-financial-cte-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 10: financial CTE update with aggregate CTEs and comma rowsets.
-- Future data-lineage boundary: merchant_category, country_code, and activity dates contribute to compliance_notes.
WITH user_financial_snapshot AS (
    SELECT
        t.user_id,
        COUNT(DISTINCT t.currency) AS active_currencies,
        SUM(CASE WHEN t.direction = 'INFLOW' THEN t.amount ELSE -t.amount END) AS net_cash_flow,
        ROUND(AVG(t.amount)::numeric, 2) AS avg_transaction_size,
```

## `postgres-business-account-balances-financial-explicit-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-account-balances-financial-explicit-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-business-account-balances-financial-explicit-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 10 equivalent: explicit JOIN version of the final financial rowsets.
WITH user_financial_snapshot AS (
    SELECT
        t.user_id,
        COUNT(DISTINCT t.currency) AS active_currencies,
        SUM(CASE WHEN t.direction = 'INFLOW' THEN t.amount ELSE -t.amount END) AS net_cash_flow,
        ROUND(AVG(t.amount)::numeric, 2) AS avg_transaction_size,
        STRING_AGG(DISTINCT t.merchant_category, '; ' ORDER BY t.merchant_category) AS primary_categories,
```

## `postgres-business-asset-balances-update-outer-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-asset-balances-update-outer-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-business-asset-balances-update-outer-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 9: UPDATE FROM with FULL OUTER, INNER, and LEFT joins.
-- The source sample used sys_a/sys_b in SET; this fixture fixes those aliases to the derived rowset columns.
-- Future data-lineage boundary: ledger balances and staff operator_name drive asset_balances fields.
UPDATE asset_balances ab
SET computed_balance = COALESCE(unified_ledgers.balance_a, 0.00) + COALESCE(unified_ledgers.balance_b, 0.00),
    discrepancy_flag = CASE WHEN unified_ledgers.balance_a != unified_ledgers.balance_b THEN 1 ELSE 0 END,
    last_checked_by = s.operator_name
FROM (
```

## `postgres-business-cross-border-reconciliation-function-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/postgres/postgres-business-cross-border-reconciliation-function-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business function case: nested CTEs, array unnesting, FULL/LEFT joins,
-- and a correlated subquery inside a returned set function.
-- relation-detector-fixture-source: FUNCTION:finance.sp_cross_border_reconciliation_engine
CREATE TYPE order_reconciliation_row AS (
    reconciliation_id   UUID,
    merchant_id         INT,
    sku_code            VARCHAR(50),
    original_amount     NUMERIC(16,4),
```

## `postgres-business-delete-cascade-cte-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-delete-cascade-cte-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 3: data-modifying CTE cascade delete.
WITH deleted_orders AS (
    DELETE FROM orders o
    USING users u
    WHERE o.user_id = u.id
      AND o.payment_status = 'UNPAID'
      AND u.risk_level = 'HIGH'
    RETURNING o.id
```

## `postgres-business-delete-orphan-left-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-delete-orphan-left-join-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 4: orphan cleanup through DELETE USING and LEFT JOIN.
DELETE FROM product_reviews pr
USING product_reviews pr_alias
LEFT JOIN products p ON pr_alias.product_id = p.id
WHERE pr.id = pr_alias.id
  AND p.id IS NULL;
```

## `postgres-business-delete-orphan-not-exists-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-delete-orphan-not-exists-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 4 equivalent: orphan cleanup through NOT EXISTS.
DELETE FROM product_reviews pr
WHERE NOT EXISTS (
    SELECT 1
    FROM products p
    WHERE pr.product_id = p.id
);
```

## `postgres-business-inventory-purge-deep-subquery-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-inventory-purge-deep-subquery-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 11: DELETE USING with deep nested subqueries, LEFT/FULL/INNER joins, and regex tools.
DELETE FROM inventory_snapshots isc
USING (
    SELECT
        core_inv.snapshot_id,
        core_inv.sku_code,
        UPPER(REGEXP_REPLACE(core_inv.batch_no, '[^a-zA-Z0-9]', '', 'g')) AS cleaned_batch
    FROM (
```

## `postgres-business-inventory-purge-exists-equivalent-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-inventory-purge-exists-equivalent-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 11 equivalent: correlated subquery form for the low-sales inventory purge.
DELETE FROM inventory_snapshots isc
USING supplier_inventory_logs i
INNER JOIN warehouse_facilities wf ON i.warehouse_id = wf.id
INNER JOIN master_skus ms ON i.sku_code = ms.sku_ref
WHERE isc.snapshot_id = i.id
  AND ms.is_perishable = true
  AND isc.logged_date < NOW() - INTERVAL '180 days'
```

## `postgres-business-risk-ledger-update-cte-comma-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-risk-ledger-update-cte-comma-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-business-risk-ledger-update-cte-comma-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 7: nested CTE UPDATE with window function and comma rowsets.
-- Future data-lineage boundary: users.risk_level contributes to order_ledgers.remarks via string concat.
WITH active_users AS (
    SELECT id, risk_level
    FROM users
    WHERE status = 'ACTIVE' AND risk_level IN ('HIGH', 'MEDIUM')
),
fraud_orders AS (
```

## `postgres-business-risk-ledger-update-cte-explicit-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-risk-ledger-update-cte-explicit-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-business-risk-ledger-update-cte-explicit-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 7 equivalent: explicit INNER JOIN version of the final CTE rowsets.
WITH active_users AS (
    SELECT id, risk_level
    FROM users
    WHERE status = 'ACTIVE' AND risk_level IN ('HIGH', 'MEDIUM')
),
fraud_orders AS (
    SELECT
```

## `postgres-business-risk-settlement-function-comma-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | requires cross-statement temporary-table lineage beyond Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/postgres/postgres-business-risk-settlement-function-comma-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business function case: comma-rowset equivalent of the users join.
-- Expected fingerprints must match postgres-business-risk-settlement-function-sql.
-- relation-detector-fixture-source: FUNCTION:finance.fn_risk_settlement_engine_comma
CREATE OR REPLACE FUNCTION fn_risk_settlement_engine_comma(
    p_user_ids INT[],
    p_amounts NUMERIC[],
    p_risk_flags TEXT[]
)
```

## `postgres-business-risk-settlement-function-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | requires cross-statement temporary-table lineage beyond Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/postgres/postgres-business-risk-settlement-function-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business function case: temp array inputs are joined to users through CTEs.
-- The sample output in the prompt appears copied from a different account_balances case;
-- this fixture treats only relationships inferable from this SQL as expected.
-- relation-detector-fixture-source: FUNCTION:finance.fn_risk_settlement_engine
CREATE OR REPLACE FUNCTION fn_risk_settlement_engine(
    p_user_ids INT[],
    p_amounts NUMERIC[],
    p_risk_flags TEXT[]
```

## `postgres-business-update-inventory-comma-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-update-inventory-comma-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-business-update-inventory-comma-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 5 equivalent: INNER JOIN rewritten as comma rowsets plus WHERE equality.
UPDATE inventory i
SET stock_reserved = i.stock_reserved + oi.quantity,
    last_ordered_from = s.supplier_name
FROM order_items oi, suppliers s
WHERE i.product_id = oi.product_id
  AND i.supplier_id = s.id
  AND oi.status = 'PENDING';
```

## `postgres-business-update-inventory-from-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-update-inventory-from-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-business-update-inventory-from-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 5: UPDATE FROM with one ambiguous equality and one FK-like join.
UPDATE inventory i
SET stock_reserved = i.stock_reserved + oi.quantity,
    last_ordered_from = s.supplier_name
FROM order_items oi
INNER JOIN suppliers s ON i.supplier_id = s.id
WHERE i.product_id = oi.product_id
  AND oi.status = 'PENDING';
```

## `postgres-business-update-products-comma-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-update-products-comma-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-business-update-products-comma-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 1 equivalent: INNER JOIN rewritten as comma rowsets plus WHERE equality.
UPDATE products p
SET is_on_sale = 1,
    promo_price = p.original_price * 0.9
FROM shops s, merchants m
WHERE p.shop_id = s.id
  AND s.merchant_id = m.id
  AND m.status = 'ACTIVE'
```

## `postgres-business-update-products-from-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-update-products-from-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-business-update-products-from-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 1: UPDATE FROM with an explicit INNER JOIN.
UPDATE products p
SET is_on_sale = 1,
    promo_price = p.original_price * 0.9
FROM shops s
INNER JOIN merchants m ON s.merchant_id = m.id
WHERE p.shop_id = s.id
  AND m.status = 'ACTIVE'
```

## `postgres-business-update-users-aggregate-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-update-users-aggregate-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-business-update-users-aggregate-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 2: UPDATE FROM aggregate derived table.
UPDATE users u
SET total_spent = COALESCE(o_summary.actual_total, 0.00),
    level = CASE
        WHEN o_summary.actual_total >= 10000 THEN 'VIP'
        WHEN o_summary.actual_total >= 5000 THEN 'GOLD'
        ELSE 'REGULAR'
    END
```

## `postgres-business-update-users-scalar-subquery-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-update-users-scalar-subquery-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-business-update-users-scalar-subquery-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 2 equivalent: aggregate relation expressed through correlated scalar subqueries.
UPDATE users u
SET total_spent = COALESCE((
        SELECT SUM(o.pay_amount)
        FROM orders o
        WHERE o.user_id = u.id
          AND o.order_status = 'PAID'
    ), 0.00),
```

## `postgres-business-update-warehouse-comma-subquery-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-comma-subquery-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-comma-subquery-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 6 equivalent: INNER joins as comma rowsets, LEFT aggregate as derived subquery relation.
UPDATE warehouse_inventory wi
SET stock_reserved = wi.stock_reserved + oi.quantity,
    last_audit_status = CASE
        WHEN latest_orders.risk_score > 80 THEN 'HOLD_FOR_REVIEW'
        WHEN wi.stock_available - oi.quantity < 10 THEN 'LOW_STOCK_WARNING'
        ELSE 'ALLOCATED'
    END
```

## `postgres-business-update-warehouse-complex-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-complex-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-complex-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 6: complex UPDATE FROM with nested derived tables and window function projection.
UPDATE warehouse_inventory wi
SET stock_reserved = wi.stock_reserved + oi.quantity,
    last_audit_status = CASE
        WHEN latest_orders.risk_score > 80 THEN 'HOLD_FOR_REVIEW'
        WHEN wi.stock_available - oi.quantity < 10 THEN 'LOW_STOCK_WARNING'
        ELSE 'ALLOCATED'
    END
```

## `postgres-business-user-coupons-delete-derived-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-user-coupons-delete-derived-join-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 8: DELETE USING with nested aggregate derived table and outer INNER JOIN.
DELETE FROM user_coupons uc
USING (
    SELECT c_sub.id AS coupon_id, c_sub.merchant_id
    FROM coupons c_sub
    LEFT JOIN (
        SELECT coupon_id, COUNT(id) AS usage_cnt
        FROM coupon_redemptions
```

## `postgres-business-user-coupons-delete-exists-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-user-coupons-delete-exists-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 8 equivalent: correlated EXISTS/NOT EXISTS version of the nested coupon cleanup.
DELETE FROM user_coupons uc
USING coupons c
INNER JOIN merchants m ON c.merchant_id = m.id
WHERE uc.coupon_id = c.id
  AND m.compliance_status = 'SUSPENDED'
  AND (
      c.expire_at < NOW()
```

## `postgres-generated-comprehensive-query-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/examples/comprehensive-query-sql-001-030.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Comprehensive query SQL examples, CASE 01-30.
-- These statements are parser stress samples for joins, CTEs, subqueries,
-- recursive queries, derived tables, and complex expression-heavy predicates.

-- ============================================================================
-- CASE 01: Snowflake Schema Multi-Stage Warehouse Join with Path Concatenation
-- ============================================================================
SELECT 
```

## `postgres-generated-industrial-complex-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/examples/industrial-complex-sql-001-050.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Industrial complex SQL examples, SQL 001-050.
-- These statements are saved as parser stress samples and are not expected to run
-- against the project test databases without matching demo schemas.

-- SQL 001: 窗口函数无缝嵌套多层 CASE WHEN 与字段双竖线动态组合
SELECT 
    tr.transaction_id,
    'TX_' || tr.merchant_code || '_' || TO_CHAR(tr.created_at, 'YYYYMMDD') || '_' ||
```

## `postgres-generated-provided-complex-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/generated-provided-complex-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Provided complex SQL collection.
-- Source:
-- - 001-025 from attachment 87388f7d-2c4c-499a-b085-d158b969af45/pasted-text.txt
-- - 026-050 from attachment c61899d0-fc27-4a25-974a-56bc89fb0b7f/pasted-text.txt
-- - 051-075 from the chat message on 2026-06-18.
-- Notes:
-- - SQL 052 appears truncated in the chat message and is preserved as received.
-- - SQL 053-058 were not present in the supplied text.
```

## `postgres-official-cte-dml-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-official-cte-dml-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-official-cte-dml-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:source_rows.customer_id->orders.customer_id`

**Extractor Candidate Fingerprints**

- `VALUE:DIRECT:source_rows.customer_id->orders.customer_id`

**Input Preview**

```sql
-- PostgreSQL official docs inspired: WITH Queries and data-modifying
-- statements in WITH. Covers DELETE RETURNING, INSERT SELECT, UPDATE FROM,
-- MERGE USING, MATERIALIZED, and NOT MATERIALIZED boundaries.
WITH moved_rows AS (
  DELETE FROM order_staging os
  USING orders o
  WHERE os.order_id = o.id
  RETURNING os.order_id, os.user_id
```

## `postgres-official-cte-nested-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-official-cte-nested-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: with.sql and WITH Queries.
-- Covers nested WITH, recursive CTEs, MATERIALIZED / NOT MATERIALIZED, and CTE
-- reuse without promoting CTE names to physical tables.
WITH base_orders AS MATERIALIZED (
  SELECT o.id AS order_id, o.user_id, o.customer_id
  FROM orders o
  JOIN users u ON o.user_id = u.id
),
```

## `postgres-official-join-edge-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-official-join-edge-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: join.sql and Table Expressions.
-- Covers explicit outer joins, JOIN USING alias, NATURAL join, parenthesized
-- join trees, and legacy comma join predicates.
SELECT *
FROM orders o
INNER JOIN users u ON o.user_id = u.id
LEFT OUTER JOIN payments p ON p.order_id = o.id
RIGHT JOIN shipments s ON s.order_id = o.id
```

## `postgres-official-lateral-function-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-official-lateral-function-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: Table Expressions.
-- Covers ROWS FROM, json_to_recordset, generate_series, and LATERAL function
-- rowsets without treating functions or their aliases as physical tables.
SELECT *
FROM orders o
LEFT JOIN LATERAL (
  SELECT o.user_id
) projected_user ON true
```

## `postgres-official-lateral-nested-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-official-lateral-nested-join-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official docs inspired: Table Expressions, LATERAL, ROWS FROM,
-- table functions, UNNEST WITH ORDINALITY, and nested LATERAL joins.
SELECT *
FROM orders o
LEFT JOIN LATERAL (
  SELECT li.order_id, li.product_id
  FROM line_items li
  WHERE li.order_id = o.id
```

## `postgres-official-multiway-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-official-multiway-join-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: join.sql and Table Expressions.
-- Covers multiway outer joins, parenthesized join trees, derived joins,
-- legacy comma join mixed with explicit JOIN, and multiple ON equality keys.
SELECT *
FROM order_roots o
LEFT JOIN order_items oi
  ON oi.order_id = o.id
 AND oi.tenant_id = o.tenant_id
```

## `postgres-official-subquery-deep-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-official-subquery-deep-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: subselect.sql and Subquery
-- Expressions. Covers nested correlated EXISTS, tuple IN/NOT IN, row
-- constructor ANY/SOME/ALL, scalar subquery equality, and nested subquery joins.
SELECT *
FROM orders o
WHERE EXISTS (
  SELECT 1
  FROM users u
```

## `postgres-official-subquery-edge-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-official-subquery-edge-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: subselect.sql and Subquery
-- Expressions. Covers extra parentheses, correlated EXISTS, tuple IN/NOT IN,
-- scalar subquery equality, and ANY/SOME/ALL boundaries.
SELECT *
FROM ((SELECT o.id, o.user_id FROM orders o)) projected_orders
JOIN users u ON projected_orders.user_id = u.id;

SELECT *
```

## `postgres-sql-delete-using-no-alias`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/sql-delete-using-no-alias/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
DELETE FROM orders
USING users
WHERE orders.user_id = users.id;
```

## `postgres-sql-lateral-derived`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/sql-lateral-derived/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
SELECT o.id, u.email
FROM orders o
JOIN LATERAL (
  SELECT o.user_id AS user_id
) x ON true
JOIN users u ON x.user_id = u.id;
```

## `postgres-sql-merge-using`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/sql-merge-using/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/sql-merge-using/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:source_orders.id->target_orders.source_order_id`

**Extractor Candidate Fingerprints**

- `VALUE:DIRECT:source_orders.id->target_orders.source_order_id`

**Input Preview**

```sql
MERGE INTO target_orders AS t
USING source_orders AS s
ON t.source_order_id = s.id
WHEN MATCHED AND s.cancelled_at IS NULL THEN
  UPDATE SET synced_at = CURRENT_TIMESTAMP
WHEN NOT MATCHED THEN
  INSERT (source_order_id) VALUES (s.id);
```

## `postgres-sql-multi-layer-cte`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/sql-multi-layer-cte/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
WITH "a" AS (
  SELECT o.id AS order_id, o.customer_id
  FROM "public"."orders" o
  JOIN "public"."customers" c ON o.customer_id = c.id
),
b AS (
  SELECT a.order_id, c.region_id
  FROM "a" a
```

## `postgres-sql-quoted-mixed-alias`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/sql-quoted-mixed-alias/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
SELECT *
FROM "public"."orders" o
JOIN users ON o."user_id" = users.id
JOIN "payments" ON "payments".order_id = o.id;
```

## `postgres-sql-recursive-cte`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/sql-recursive-cte/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
WITH RECURSIVE employee_paths(id, manager_id) AS (
  SELECT e.id, e.manager_id
  FROM employees e
  WHERE e.manager_id IS NULL
  UNION ALL
  SELECT e.id, e.manager_id
  FROM employees e
  JOIN employee_paths ep ON ep.id = e.manager_id
```

## `postgres-sql-unnest-ordinality`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/sql-unnest-ordinality/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
SELECT u.id
FROM users u
JOIN orders o ON o.user_id = u.id
JOIN unnest(ARRAY[1, 2, 3]) WITH ORDINALITY AS input_ids(user_id, ord)
  ON input_ids.user_id = u.id;
```

## `postgres-sql-update-from-aliases`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/sql-update-from-aliases/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
UPDATE orders o
SET status = 'PAID'
FROM users u
WHERE o.user_id = u.id;
```

## `postgres17-basic-correctness-case-01-objects-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-basic-correctness-case-01-objects-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from PostgreSQL SQL sources for postgres-basic-correctness-case-01.
-- Refresh with PostgresBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: TRIGGER:pg_trigger:rna.rna_audit
CREATE TRIGGER rna_audit BEFORE UPDATE ON case_01.rna FOR EACH ROW EXECUTE FUNCTION trigger_fct_rna_audit()
-- relation-detector-fixture-end

-- relation-detector-fixture-source: TRIGGER:pg_trigger:rnc_database.rnc_database_audit
```

## `postgres17-basic-correctness-case-01-statements-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `NATIVE_LOG` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-basic-correctness-case-01-statements-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from PostgreSQL SQL sources for postgres-basic-correctness-case-01.
-- Refresh with PostgresBasicCorrectnessFixtureExporter.

```

## `postgres17-business-account-balances-financial-cte-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-account-balances-financial-cte-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres-business-account-balances-financial-cte-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:account_balances.max_credit_limit->account_balances.adjusted_limit`
- `VALUE:CONCAT_FORMAT:account_balances.risk_flags->account_balances.risk_flags`
- `VALUE:CONCAT_FORMAT:dormant_risk_scores.country_code,dormant_risk_scores.days_since_last_active,dormant_risk_scores.wealth_tile,user_financial_snapshot.primary_categories->account_balances.compliance_notes`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 10: financial CTE update with aggregate CTEs and comma rowsets.
-- Future data-lineage boundary: merchant_category, country_code, and activity dates contribute to compliance_notes.
WITH user_financial_snapshot AS (
    SELECT
        t.user_id,
        COUNT(DISTINCT t.currency) AS active_currencies,
        SUM(CASE WHEN t.direction = 'INFLOW' THEN t.amount ELSE -t.amount END) AS net_cash_flow,
        ROUND(AVG(t.amount)::numeric, 2) AS avg_transaction_size,
```

## `postgres17-business-account-balances-financial-explicit-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-account-balances-financial-explicit-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres-business-account-balances-financial-explicit-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:account_balances.max_credit_limit->account_balances.adjusted_limit`
- `VALUE:CONCAT_FORMAT:account_balances.risk_flags->account_balances.risk_flags`
- `VALUE:CONCAT_FORMAT:dormant_risk_scores.country_code,dormant_risk_scores.days_since_last_active,dormant_risk_scores.wealth_tile,user_financial_snapshot.primary_categories->account_balances.compliance_notes`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 10 equivalent: explicit JOIN version of the final financial rowsets.
WITH user_financial_snapshot AS (
    SELECT
        t.user_id,
        COUNT(DISTINCT t.currency) AS active_currencies,
        SUM(CASE WHEN t.direction = 'INFLOW' THEN t.amount ELSE -t.amount END) AS net_cash_flow,
        ROUND(AVG(t.amount)::numeric, 2) AS avg_transaction_size,
        STRING_AGG(DISTINCT t.merchant_category, '; ' ORDER BY t.merchant_category) AS primary_categories,
```

## `postgres17-business-asset-balances-update-outer-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-asset-balances-update-outer-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres-business-asset-balances-update-outer-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:ledger_system_a.balance,ledger_system_b.balance->asset_balances.discrepancy_flag`
- `VALUE:COALESCE:ledger_system_a.balance,ledger_system_b.balance->asset_balances.computed_balance`
- `VALUE:DIRECT:staff_assignments.operator_name->asset_balances.last_checked_by`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 9: UPDATE FROM with FULL OUTER, INNER, and LEFT joins.
-- The source sample used sys_a/sys_b in SET; this fixture fixes those aliases to the derived rowset columns.
-- Future data-lineage boundary: ledger balances and staff operator_name drive asset_balances fields.
UPDATE asset_balances ab
SET computed_balance = COALESCE(unified_ledgers.balance_a, 0.00) + COALESCE(unified_ledgers.balance_b, 0.00),
    discrepancy_flag = CASE WHEN unified_ledgers.balance_a != unified_ledgers.balance_b THEN 1 ELSE 0 END,
    last_checked_by = s.operator_name
FROM (
```

## `postgres17-business-cross-border-reconciliation-function-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-cross-border-reconciliation-function-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business function case: nested CTEs, array unnesting, FULL/LEFT joins,
-- and a correlated subquery inside a returned set function.
-- relation-detector-fixture-source: FUNCTION:finance.sp_cross_border_reconciliation_engine
CREATE TYPE order_reconciliation_row AS (
    reconciliation_id   UUID,
    merchant_id         INT,
    sku_code            VARCHAR(50),
    original_amount     NUMERIC(16,4),
```

## `postgres17-business-delete-cascade-cte-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-delete-cascade-cte-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 3: data-modifying CTE cascade delete.
WITH deleted_orders AS (
    DELETE FROM orders o
    USING users u
    WHERE o.user_id = u.id
      AND o.payment_status = 'UNPAID'
      AND u.risk_level = 'HIGH'
    RETURNING o.id
```

## `postgres17-business-delete-orphan-left-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-delete-orphan-left-join-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 4: orphan cleanup through DELETE USING and LEFT JOIN.
DELETE FROM product_reviews pr
USING product_reviews pr_alias
LEFT JOIN products p ON pr_alias.product_id = p.id
WHERE pr.id = pr_alias.id
  AND p.id IS NULL;
```

## `postgres17-business-delete-orphan-not-exists-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-delete-orphan-not-exists-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 4 equivalent: orphan cleanup through NOT EXISTS.
DELETE FROM product_reviews pr
WHERE NOT EXISTS (
    SELECT 1
    FROM products p
    WHERE pr.product_id = p.id
);
```

## `postgres17-business-inventory-purge-deep-subquery-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-inventory-purge-deep-subquery-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 11: DELETE USING with deep nested subqueries, LEFT/FULL/INNER joins, and regex tools.
DELETE FROM inventory_snapshots isc
USING (
    SELECT
        core_inv.snapshot_id,
        core_inv.sku_code,
        UPPER(REGEXP_REPLACE(core_inv.batch_no, '[^a-zA-Z0-9]', '', 'g')) AS cleaned_batch
    FROM (
```

## `postgres17-business-inventory-purge-exists-equivalent-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-inventory-purge-exists-equivalent-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 11 equivalent: correlated subquery form for the low-sales inventory purge.
DELETE FROM inventory_snapshots isc
USING supplier_inventory_logs i
INNER JOIN warehouse_facilities wf ON i.warehouse_id = wf.id
INNER JOIN master_skus ms ON i.sku_code = ms.sku_ref
WHERE isc.snapshot_id = i.id
  AND ms.is_perishable = true
  AND isc.logged_date < NOW() - INTERVAL '180 days'
```

## `postgres17-business-risk-ledger-update-cte-comma-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-risk-ledger-update-cte-comma-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres-business-risk-ledger-update-cte-comma-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:CONCAT_FORMAT:users.risk_level,fraud_orders.rnk->order_ledgers.remarks`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 7: nested CTE UPDATE with window function and comma rowsets.
-- Future data-lineage boundary: users.risk_level contributes to order_ledgers.remarks via string concat.
WITH active_users AS (
    SELECT id, risk_level
    FROM users
    WHERE status = 'ACTIVE' AND risk_level IN ('HIGH', 'MEDIUM')
),
fraud_orders AS (
```

## `postgres17-business-risk-ledger-update-cte-explicit-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-risk-ledger-update-cte-explicit-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres-business-risk-ledger-update-cte-explicit-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:CONCAT_FORMAT:users.risk_level,fraud_orders.rnk->order_ledgers.remarks`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 7 equivalent: explicit INNER JOIN version of the final CTE rowsets.
WITH active_users AS (
    SELECT id, risk_level
    FROM users
    WHERE status = 'ACTIVE' AND risk_level IN ('HIGH', 'MEDIUM')
),
fraud_orders AS (
    SELECT
```

## `postgres17-business-risk-settlement-function-comma-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | requires cross-statement temporary-table lineage beyond Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-risk-settlement-function-comma-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business function case: comma-rowset equivalent of the users join.
-- Expected fingerprints must match postgres-business-risk-settlement-function-sql.
-- relation-detector-fixture-source: FUNCTION:finance.fn_risk_settlement_engine_comma
CREATE OR REPLACE FUNCTION fn_risk_settlement_engine_comma(
    p_user_ids INT[],
    p_amounts NUMERIC[],
    p_risk_flags TEXT[]
)
```

## `postgres17-business-risk-settlement-function-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | requires cross-statement temporary-table lineage beyond Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-risk-settlement-function-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business function case: temp array inputs are joined to users through CTEs.
-- The sample output in the prompt appears copied from a different account_balances case;
-- this fixture treats only relationships inferable from this SQL as expected.
-- relation-detector-fixture-source: FUNCTION:finance.fn_risk_settlement_engine
CREATE OR REPLACE FUNCTION fn_risk_settlement_engine(
    p_user_ids INT[],
    p_amounts NUMERIC[],
    p_risk_flags TEXT[]
```

## `postgres17-business-update-inventory-comma-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-update-inventory-comma-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres-business-update-inventory-comma-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:inventory.stock_reserved,order_items.quantity->inventory.stock_reserved`
- `VALUE:DIRECT:suppliers.supplier_name->inventory.last_ordered_from`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 5 equivalent: INNER JOIN rewritten as comma rowsets plus WHERE equality.
UPDATE inventory i
SET stock_reserved = i.stock_reserved + oi.quantity,
    last_ordered_from = s.supplier_name
FROM order_items oi, suppliers s
WHERE i.product_id = oi.product_id
  AND i.supplier_id = s.id
  AND oi.status = 'PENDING';
```

## `postgres17-business-update-inventory-from-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-update-inventory-from-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres-business-update-inventory-from-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:inventory.stock_reserved,order_items.quantity->inventory.stock_reserved`
- `VALUE:DIRECT:suppliers.supplier_name->inventory.last_ordered_from`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 5: UPDATE FROM with one ambiguous equality and one FK-like join.
UPDATE inventory i
SET stock_reserved = i.stock_reserved + oi.quantity,
    last_ordered_from = s.supplier_name
FROM order_items oi
INNER JOIN suppliers s ON i.supplier_id = s.id
WHERE i.product_id = oi.product_id
  AND oi.status = 'PENDING';
```

## `postgres17-business-update-products-comma-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-update-products-comma-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres-business-update-products-comma-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:products.original_price->products.promo_price`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 1 equivalent: INNER JOIN rewritten as comma rowsets plus WHERE equality.
UPDATE products p
SET is_on_sale = 1,
    promo_price = p.original_price * 0.9
FROM shops s, merchants m
WHERE p.shop_id = s.id
  AND s.merchant_id = m.id
  AND m.status = 'ACTIVE'
```

## `postgres17-business-update-products-from-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-update-products-from-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres-business-update-products-from-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:products.original_price->products.promo_price`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 1: UPDATE FROM with an explicit INNER JOIN.
UPDATE products p
SET is_on_sale = 1,
    promo_price = p.original_price * 0.9
FROM shops s
INNER JOIN merchants m ON s.merchant_id = m.id
WHERE p.shop_id = s.id
  AND m.status = 'ACTIVE'
```

## `postgres17-business-update-users-aggregate-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-update-users-aggregate-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres-business-update-users-aggregate-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:AGGREGATE:orders.pay_amount->users.level`
- `VALUE:AGGREGATE:orders.pay_amount->users.total_spent`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 2: UPDATE FROM aggregate derived table.
UPDATE users u
SET total_spent = COALESCE(o_summary.actual_total, 0.00),
    level = CASE
        WHEN o_summary.actual_total >= 10000 THEN 'VIP'
        WHEN o_summary.actual_total >= 5000 THEN 'GOLD'
        ELSE 'REGULAR'
    END
```

## `postgres17-business-update-users-scalar-subquery-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-update-users-scalar-subquery-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres-business-update-users-scalar-subquery-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:orders.pay_amount,orders.user_id,users.id,orders.order_status->users.level`
- `VALUE:AGGREGATE:orders.pay_amount,orders.user_id,users.id,orders.order_status->users.total_spent`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 2 equivalent: aggregate relation expressed through correlated scalar subqueries.
UPDATE users u
SET total_spent = COALESCE((
        SELECT SUM(o.pay_amount)
        FROM orders o
        WHERE o.user_id = u.id
          AND o.order_status = 'PAID'
    ), 0.00),
```

## `postgres17-business-update-warehouse-comma-subquery-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-update-warehouse-comma-subquery-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres-business-update-warehouse-comma-subquery-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 6 equivalent: INNER joins as comma rowsets, LEFT aggregate as derived subquery relation.
UPDATE warehouse_inventory wi
SET stock_reserved = wi.stock_reserved + oi.quantity,
    last_audit_status = CASE
        WHEN latest_orders.risk_score > 80 THEN 'HOLD_FOR_REVIEW'
        WHEN wi.stock_available - oi.quantity < 10 THEN 'LOW_STOCK_WARNING'
        ELSE 'ALLOCATED'
    END
```

## `postgres17-business-update-warehouse-complex-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-update-warehouse-complex-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres-business-update-warehouse-complex-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 6: complex UPDATE FROM with nested derived tables and window function projection.
UPDATE warehouse_inventory wi
SET stock_reserved = wi.stock_reserved + oi.quantity,
    last_audit_status = CASE
        WHEN latest_orders.risk_score > 80 THEN 'HOLD_FOR_REVIEW'
        WHEN wi.stock_available - oi.quantity < 10 THEN 'LOW_STOCK_WARNING'
        ELSE 'ALLOCATED'
    END
```

## `postgres17-business-user-coupons-delete-derived-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-user-coupons-delete-derived-join-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 8: DELETE USING with nested aggregate derived table and outer INNER JOIN.
DELETE FROM user_coupons uc
USING (
    SELECT c_sub.id AS coupon_id, c_sub.merchant_id
    FROM coupons c_sub
    LEFT JOIN (
        SELECT coupon_id, COUNT(id) AS usage_cnt
        FROM coupon_redemptions
```

## `postgres17-business-user-coupons-delete-exists-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-business-user-coupons-delete-exists-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 8 equivalent: correlated EXISTS/NOT EXISTS version of the nested coupon cleanup.
DELETE FROM user_coupons uc
USING coupons c
INNER JOIN merchants m ON c.merchant_id = m.id
WHERE uc.coupon_id = c.id
  AND m.compliance_status = 'SUSPENDED'
  AND (
      c.expire_at < NOW()
```

## `postgres17-generated-comprehensive-query-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/examples/comprehensive-query-sql-001-030.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Comprehensive query SQL examples, CASE 01-30.
-- These statements are parser stress samples for joins, CTEs, subqueries,
-- recursive queries, derived tables, and complex expression-heavy predicates.

-- ============================================================================
-- CASE 01: Snowflake Schema Multi-Stage Warehouse Join with Path Concatenation
-- ============================================================================
SELECT 
```

## `postgres17-generated-industrial-complex-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/examples/industrial-complex-sql-001-050.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Industrial complex SQL examples, SQL 001-050.
-- These statements are saved as parser stress samples and are not expected to run
-- against the project test databases without matching demo schemas.

-- SQL 001: 窗口函数无缝嵌套多层 CASE WHEN 与字段双竖线动态组合
SELECT 
    tr.transaction_id,
    'TX_' || tr.merchant_code || '_' || TO_CHAR(tr.created_at, 'YYYYMMDD') || '_' ||
```

## `postgres17-generated-provided-complex-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/generated-provided-complex-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Provided complex SQL collection.
-- Source:
-- - 001-025 from attachment 87388f7d-2c4c-499a-b085-d158b969af45/pasted-text.txt
-- - 026-050 from attachment c61899d0-fc27-4a25-974a-56bc89fb0b7f/pasted-text.txt
-- - 051-075 from the chat message on 2026-06-18.
-- Notes:
-- - SQL 052 appears truncated in the chat message and is preserved as received.
-- - SQL 053-058 were not present in the supplied text.
```

## `postgres17-json-table-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-json-table-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL 17 SQL/JSON rowset: JSON_TABLE is a function rowset, not a physical business table.
SELECT o.id, jt.product_id, p.sku
FROM orders o
JOIN users u ON o.user_id = u.id
CROSS JOIN JSON_TABLE(
    o.payload,
    '$.items[*]'
    COLUMNS (
```

## `postgres17-merge-returning-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-merge-returning-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-merge-returning-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:staging_account_balances.balance->account_balances.balance`

**Extractor Candidate Fingerprints**

- `VALUE:DIRECT:staging_account_balances.balance->account_balances.balance`
- `VALUE:DIRECT:staging_account_balances.user_id->account_balances.user_id`

**Input Preview**

```sql
-- PostgreSQL 17 MERGE extension: NOT MATCHED BY SOURCE plus RETURNING merge_action().
MERGE INTO account_balances ab
USING staging_account_balances s
ON ab.user_id = s.user_id
WHEN MATCHED THEN
    UPDATE SET balance = s.balance
WHEN NOT MATCHED BY SOURCE THEN
    DELETE
```

## `postgres17-official-cte-dml-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-official-cte-dml-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres-official-cte-dml-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:source_rows.customer_id->orders.customer_id`

**Extractor Candidate Fingerprints**

- `VALUE:DIRECT:source_rows.customer_id->orders.customer_id`

**Input Preview**

```sql
-- PostgreSQL official docs inspired: WITH Queries and data-modifying
-- statements in WITH. Covers DELETE RETURNING, INSERT SELECT, UPDATE FROM,
-- MERGE USING, MATERIALIZED, and NOT MATERIALIZED boundaries.
WITH moved_rows AS (
  DELETE FROM order_staging os
  USING orders o
  WHERE os.order_id = o.id
  RETURNING os.order_id, os.user_id
```

## `postgres17-official-cte-nested-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-official-cte-nested-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: with.sql and WITH Queries.
-- Covers nested WITH, recursive CTEs, MATERIALIZED / NOT MATERIALIZED, and CTE
-- reuse without promoting CTE names to physical tables.
WITH base_orders AS MATERIALIZED (
  SELECT o.id AS order_id, o.user_id, o.customer_id
  FROM orders o
  JOIN users u ON o.user_id = u.id
),
```

## `postgres17-official-join-edge-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-official-join-edge-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: join.sql and Table Expressions.
-- Covers explicit outer joins, JOIN USING alias, NATURAL join, parenthesized
-- join trees, and legacy comma join predicates.
SELECT *
FROM orders o
INNER JOIN users u ON o.user_id = u.id
LEFT OUTER JOIN payments p ON p.order_id = o.id
RIGHT JOIN shipments s ON s.order_id = o.id
```

## `postgres17-official-lateral-function-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-official-lateral-function-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: Table Expressions.
-- Covers ROWS FROM, json_to_recordset, generate_series, and LATERAL function
-- rowsets without treating functions or their aliases as physical tables.
SELECT *
FROM orders o
LEFT JOIN LATERAL (
  SELECT o.user_id
) projected_user ON true
```

## `postgres17-official-lateral-nested-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-official-lateral-nested-join-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official docs inspired: Table Expressions, LATERAL, ROWS FROM,
-- table functions, UNNEST WITH ORDINALITY, and nested LATERAL joins.
SELECT *
FROM orders o
LEFT JOIN LATERAL (
  SELECT li.order_id, li.product_id
  FROM line_items li
  WHERE li.order_id = o.id
```

## `postgres17-official-multiway-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-official-multiway-join-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: join.sql and Table Expressions.
-- Covers multiway outer joins, parenthesized join trees, derived joins,
-- legacy comma join mixed with explicit JOIN, and multiple ON equality keys.
SELECT *
FROM order_roots o
LEFT JOIN order_items oi
  ON oi.order_id = o.id
 AND oi.tenant_id = o.tenant_id
```

## `postgres17-official-subquery-deep-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-official-subquery-deep-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: subselect.sql and Subquery
-- Expressions. Covers nested correlated EXISTS, tuple IN/NOT IN, row
-- constructor ANY/SOME/ALL, scalar subquery equality, and nested subquery joins.
SELECT *
FROM orders o
WHERE EXISTS (
  SELECT 1
  FROM users u
```

## `postgres17-official-subquery-edge-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres-official-subquery-edge-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: subselect.sql and Subquery
-- Expressions. Covers extra parentheses, correlated EXISTS, tuple IN/NOT IN,
-- scalar subquery equality, and ANY/SOME/ALL boundaries.
SELECT *
FROM ((SELECT o.id, o.user_id FROM orders o)) projected_orders
JOIN users u ON projected_orders.user_id = u.id;

SELECT *
```

## `postgres17-sql-delete-using-no-alias`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/sql-delete-using-no-alias/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
DELETE FROM orders
USING users
WHERE orders.user_id = users.id;
```

## `postgres17-sql-lateral-derived`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/sql-lateral-derived/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
SELECT o.id, u.email
FROM orders o
JOIN LATERAL (
  SELECT o.user_id AS user_id
) x ON true
JOIN users u ON x.user_id = u.id;
```

## `postgres17-sql-merge-using`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/sql-merge-using/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/sql-merge-using/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:source_orders.id->target_orders.source_order_id`

**Extractor Candidate Fingerprints**

- `VALUE:DIRECT:source_orders.id->target_orders.source_order_id`

**Input Preview**

```sql
MERGE INTO target_orders AS t
USING source_orders AS s
ON t.source_order_id = s.id
WHEN MATCHED AND s.cancelled_at IS NULL THEN
  UPDATE SET synced_at = CURRENT_TIMESTAMP
WHEN NOT MATCHED THEN
  INSERT (source_order_id) VALUES (s.id);
```

## `postgres17-sql-multi-layer-cte`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/sql-multi-layer-cte/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
WITH "a" AS (
  SELECT o.id AS order_id, o.customer_id
  FROM "public"."orders" o
  JOIN "public"."customers" c ON o.customer_id = c.id
),
b AS (
  SELECT a.order_id, c.region_id
  FROM "a" a
```

## `postgres17-sql-quoted-mixed-alias`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/sql-quoted-mixed-alias/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
SELECT *
FROM "public"."orders" o
JOIN users ON o."user_id" = users.id
JOIN "payments" ON "payments".order_id = o.id;
```

## `postgres17-sql-recursive-cte`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/sql-recursive-cte/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
WITH RECURSIVE employee_paths(id, manager_id) AS (
  SELECT e.id, e.manager_id
  FROM employees e
  WHERE e.manager_id IS NULL
  UNION ALL
  SELECT e.id, e.manager_id
  FROM employees e
  JOIN employee_paths ep ON ep.id = e.manager_id
```

## `postgres17-sql-unnest-ordinality`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/sql-unnest-ordinality/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
SELECT u.id
FROM users u
JOIN orders o ON o.user_id = u.id
JOIN unnest(ARRAY[1, 2, 3]) WITH ORDINALITY AS input_ids(user_id, ord)
  ON input_ids.user_id = u.id;
```

## `postgres17-sql-update-from-aliases`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/sql-update-from-aliases/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
UPDATE orders o
SET status = 'PAID'
FROM users u
WHERE o.user_id = u.id;
```

## `postgres18-basic-correctness-case-01-objects-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-basic-correctness-case-01-objects-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from PostgreSQL SQL sources for postgres-basic-correctness-case-01.
-- Refresh with PostgresBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: TRIGGER:pg_trigger:rna.rna_audit
CREATE TRIGGER rna_audit BEFORE UPDATE ON case_01.rna FOR EACH ROW EXECUTE FUNCTION trigger_fct_rna_audit()
-- relation-detector-fixture-end

-- relation-detector-fixture-source: TRIGGER:pg_trigger:rnc_database.rnc_database_audit
```

## `postgres18-basic-correctness-case-01-statements-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `NATIVE_LOG` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-basic-correctness-case-01-statements-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Generated from PostgreSQL SQL sources for postgres-basic-correctness-case-01.
-- Refresh with PostgresBasicCorrectnessFixtureExporter.

```

## `postgres18-business-account-balances-financial-cte-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-account-balances-financial-cte-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres-business-account-balances-financial-cte-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:account_balances.max_credit_limit->account_balances.adjusted_limit`
- `VALUE:CONCAT_FORMAT:account_balances.risk_flags->account_balances.risk_flags`
- `VALUE:CONCAT_FORMAT:dormant_risk_scores.country_code,dormant_risk_scores.days_since_last_active,dormant_risk_scores.wealth_tile,user_financial_snapshot.primary_categories->account_balances.compliance_notes`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 10: financial CTE update with aggregate CTEs and comma rowsets.
-- Future data-lineage boundary: merchant_category, country_code, and activity dates contribute to compliance_notes.
WITH user_financial_snapshot AS (
    SELECT
        t.user_id,
        COUNT(DISTINCT t.currency) AS active_currencies,
        SUM(CASE WHEN t.direction = 'INFLOW' THEN t.amount ELSE -t.amount END) AS net_cash_flow,
        ROUND(AVG(t.amount)::numeric, 2) AS avg_transaction_size,
```

## `postgres18-business-account-balances-financial-explicit-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-account-balances-financial-explicit-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres-business-account-balances-financial-explicit-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:account_balances.max_credit_limit->account_balances.adjusted_limit`
- `VALUE:CONCAT_FORMAT:account_balances.risk_flags->account_balances.risk_flags`
- `VALUE:CONCAT_FORMAT:dormant_risk_scores.country_code,dormant_risk_scores.days_since_last_active,dormant_risk_scores.wealth_tile,user_financial_snapshot.primary_categories->account_balances.compliance_notes`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 10 equivalent: explicit JOIN version of the final financial rowsets.
WITH user_financial_snapshot AS (
    SELECT
        t.user_id,
        COUNT(DISTINCT t.currency) AS active_currencies,
        SUM(CASE WHEN t.direction = 'INFLOW' THEN t.amount ELSE -t.amount END) AS net_cash_flow,
        ROUND(AVG(t.amount)::numeric, 2) AS avg_transaction_size,
        STRING_AGG(DISTINCT t.merchant_category, '; ' ORDER BY t.merchant_category) AS primary_categories,
```

## `postgres18-business-asset-balances-update-outer-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-asset-balances-update-outer-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres-business-asset-balances-update-outer-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:ledger_system_a.balance,ledger_system_b.balance->asset_balances.discrepancy_flag`
- `VALUE:COALESCE:ledger_system_a.balance,ledger_system_b.balance->asset_balances.computed_balance`
- `VALUE:DIRECT:staff_assignments.operator_name->asset_balances.last_checked_by`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 9: UPDATE FROM with FULL OUTER, INNER, and LEFT joins.
-- The source sample used sys_a/sys_b in SET; this fixture fixes those aliases to the derived rowset columns.
-- Future data-lineage boundary: ledger balances and staff operator_name drive asset_balances fields.
UPDATE asset_balances ab
SET computed_balance = COALESCE(unified_ledgers.balance_a, 0.00) + COALESCE(unified_ledgers.balance_b, 0.00),
    discrepancy_flag = CASE WHEN unified_ledgers.balance_a != unified_ledgers.balance_b THEN 1 ELSE 0 END,
    last_checked_by = s.operator_name
FROM (
```

## `postgres18-business-cross-border-reconciliation-function-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-cross-border-reconciliation-function-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business function case: nested CTEs, array unnesting, FULL/LEFT joins,
-- and a correlated subquery inside a returned set function.
-- relation-detector-fixture-source: FUNCTION:finance.sp_cross_border_reconciliation_engine
CREATE TYPE order_reconciliation_row AS (
    reconciliation_id   UUID,
    merchant_id         INT,
    sku_code            VARCHAR(50),
    original_amount     NUMERIC(16,4),
```

## `postgres18-business-delete-cascade-cte-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-delete-cascade-cte-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 3: data-modifying CTE cascade delete.
WITH deleted_orders AS (
    DELETE FROM orders o
    USING users u
    WHERE o.user_id = u.id
      AND o.payment_status = 'UNPAID'
      AND u.risk_level = 'HIGH'
    RETURNING o.id
```

## `postgres18-business-delete-orphan-left-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-delete-orphan-left-join-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 4: orphan cleanup through DELETE USING and LEFT JOIN.
DELETE FROM product_reviews pr
USING product_reviews pr_alias
LEFT JOIN products p ON pr_alias.product_id = p.id
WHERE pr.id = pr_alias.id
  AND p.id IS NULL;
```

## `postgres18-business-delete-orphan-not-exists-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-delete-orphan-not-exists-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 4 equivalent: orphan cleanup through NOT EXISTS.
DELETE FROM product_reviews pr
WHERE NOT EXISTS (
    SELECT 1
    FROM products p
    WHERE pr.product_id = p.id
);
```

## `postgres18-business-inventory-purge-deep-subquery-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-inventory-purge-deep-subquery-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 11: DELETE USING with deep nested subqueries, LEFT/FULL/INNER joins, and regex tools.
DELETE FROM inventory_snapshots isc
USING (
    SELECT
        core_inv.snapshot_id,
        core_inv.sku_code,
        UPPER(REGEXP_REPLACE(core_inv.batch_no, '[^a-zA-Z0-9]', '', 'g')) AS cleaned_batch
    FROM (
```

## `postgres18-business-inventory-purge-exists-equivalent-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-inventory-purge-exists-equivalent-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 11 equivalent: correlated subquery form for the low-sales inventory purge.
DELETE FROM inventory_snapshots isc
USING supplier_inventory_logs i
INNER JOIN warehouse_facilities wf ON i.warehouse_id = wf.id
INNER JOIN master_skus ms ON i.sku_code = ms.sku_ref
WHERE isc.snapshot_id = i.id
  AND ms.is_perishable = true
  AND isc.logged_date < NOW() - INTERVAL '180 days'
```

## `postgres18-business-risk-ledger-update-cte-comma-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-risk-ledger-update-cte-comma-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres-business-risk-ledger-update-cte-comma-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:CONCAT_FORMAT:users.risk_level,fraud_orders.rnk->order_ledgers.remarks`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 7: nested CTE UPDATE with window function and comma rowsets.
-- Future data-lineage boundary: users.risk_level contributes to order_ledgers.remarks via string concat.
WITH active_users AS (
    SELECT id, risk_level
    FROM users
    WHERE status = 'ACTIVE' AND risk_level IN ('HIGH', 'MEDIUM')
),
fraud_orders AS (
```

## `postgres18-business-risk-ledger-update-cte-explicit-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-risk-ledger-update-cte-explicit-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres-business-risk-ledger-update-cte-explicit-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:CONCAT_FORMAT:users.risk_level,fraud_orders.rnk->order_ledgers.remarks`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 7 equivalent: explicit INNER JOIN version of the final CTE rowsets.
WITH active_users AS (
    SELECT id, risk_level
    FROM users
    WHERE status = 'ACTIVE' AND risk_level IN ('HIGH', 'MEDIUM')
),
fraud_orders AS (
    SELECT
```

## `postgres18-business-risk-settlement-function-comma-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | requires cross-statement temporary-table lineage beyond Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-risk-settlement-function-comma-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business function case: comma-rowset equivalent of the users join.
-- Expected fingerprints must match postgres-business-risk-settlement-function-sql.
-- relation-detector-fixture-source: FUNCTION:finance.fn_risk_settlement_engine_comma
CREATE OR REPLACE FUNCTION fn_risk_settlement_engine_comma(
    p_user_ids INT[],
    p_amounts NUMERIC[],
    p_risk_flags TEXT[]
)
```

## `postgres18-business-risk-settlement-function-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | requires cross-statement temporary-table lineage beyond Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-risk-settlement-function-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business function case: temp array inputs are joined to users through CTEs.
-- The sample output in the prompt appears copied from a different account_balances case;
-- this fixture treats only relationships inferable from this SQL as expected.
-- relation-detector-fixture-source: FUNCTION:finance.fn_risk_settlement_engine
CREATE OR REPLACE FUNCTION fn_risk_settlement_engine(
    p_user_ids INT[],
    p_amounts NUMERIC[],
    p_risk_flags TEXT[]
```

## `postgres18-business-update-inventory-comma-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-update-inventory-comma-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres-business-update-inventory-comma-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:inventory.stock_reserved,order_items.quantity->inventory.stock_reserved`
- `VALUE:DIRECT:suppliers.supplier_name->inventory.last_ordered_from`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 5 equivalent: INNER JOIN rewritten as comma rowsets plus WHERE equality.
UPDATE inventory i
SET stock_reserved = i.stock_reserved + oi.quantity,
    last_ordered_from = s.supplier_name
FROM order_items oi, suppliers s
WHERE i.product_id = oi.product_id
  AND i.supplier_id = s.id
  AND oi.status = 'PENDING';
```

## `postgres18-business-update-inventory-from-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-update-inventory-from-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres-business-update-inventory-from-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:inventory.stock_reserved,order_items.quantity->inventory.stock_reserved`
- `VALUE:DIRECT:suppliers.supplier_name->inventory.last_ordered_from`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 5: UPDATE FROM with one ambiguous equality and one FK-like join.
UPDATE inventory i
SET stock_reserved = i.stock_reserved + oi.quantity,
    last_ordered_from = s.supplier_name
FROM order_items oi
INNER JOIN suppliers s ON i.supplier_id = s.id
WHERE i.product_id = oi.product_id
  AND oi.status = 'PENDING';
```

## `postgres18-business-update-products-comma-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-update-products-comma-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres-business-update-products-comma-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:products.original_price->products.promo_price`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 1 equivalent: INNER JOIN rewritten as comma rowsets plus WHERE equality.
UPDATE products p
SET is_on_sale = 1,
    promo_price = p.original_price * 0.9
FROM shops s, merchants m
WHERE p.shop_id = s.id
  AND s.merchant_id = m.id
  AND m.status = 'ACTIVE'
```

## `postgres18-business-update-products-from-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-update-products-from-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres-business-update-products-from-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:products.original_price->products.promo_price`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 1: UPDATE FROM with an explicit INNER JOIN.
UPDATE products p
SET is_on_sale = 1,
    promo_price = p.original_price * 0.9
FROM shops s
INNER JOIN merchants m ON s.merchant_id = m.id
WHERE p.shop_id = s.id
  AND m.status = 'ACTIVE'
```

## `postgres18-business-update-users-aggregate-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-update-users-aggregate-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres-business-update-users-aggregate-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:AGGREGATE:orders.pay_amount->users.level`
- `VALUE:AGGREGATE:orders.pay_amount->users.total_spent`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 2: UPDATE FROM aggregate derived table.
UPDATE users u
SET total_spent = COALESCE(o_summary.actual_total, 0.00),
    level = CASE
        WHEN o_summary.actual_total >= 10000 THEN 'VIP'
        WHEN o_summary.actual_total >= 5000 THEN 'GOLD'
        ELSE 'REGULAR'
    END
```

## `postgres18-business-update-users-scalar-subquery-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-update-users-scalar-subquery-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres-business-update-users-scalar-subquery-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:orders.pay_amount,orders.user_id,users.id,orders.order_status->users.level`
- `VALUE:AGGREGATE:orders.pay_amount,orders.user_id,users.id,orders.order_status->users.total_spent`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 2 equivalent: aggregate relation expressed through correlated scalar subqueries.
UPDATE users u
SET total_spent = COALESCE((
        SELECT SUM(o.pay_amount)
        FROM orders o
        WHERE o.user_id = u.id
          AND o.order_status = 'PAID'
    ), 0.00),
```

## `postgres18-business-update-warehouse-comma-subquery-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-update-warehouse-comma-subquery-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres-business-update-warehouse-comma-subquery-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 6 equivalent: INNER joins as comma rowsets, LEFT aggregate as derived subquery relation.
UPDATE warehouse_inventory wi
SET stock_reserved = wi.stock_reserved + oi.quantity,
    last_audit_status = CASE
        WHEN latest_orders.risk_score > 80 THEN 'HOLD_FOR_REVIEW'
        WHEN wi.stock_available - oi.quantity < 10 THEN 'LOW_STOCK_WARNING'
        ELSE 'ALLOCATED'
    END
```

## `postgres18-business-update-warehouse-complex-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-update-warehouse-complex-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres-business-update-warehouse-complex-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 6: complex UPDATE FROM with nested derived tables and window function projection.
UPDATE warehouse_inventory wi
SET stock_reserved = wi.stock_reserved + oi.quantity,
    last_audit_status = CASE
        WHEN latest_orders.risk_score > 80 THEN 'HOLD_FOR_REVIEW'
        WHEN wi.stock_available - oi.quantity < 10 THEN 'LOW_STOCK_WARNING'
        ELSE 'ALLOCATED'
    END
```

## `postgres18-business-user-coupons-delete-derived-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-user-coupons-delete-derived-join-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 8: DELETE USING with nested aggregate derived table and outer INNER JOIN.
DELETE FROM user_coupons uc
USING (
    SELECT c_sub.id AS coupon_id, c_sub.merchant_id
    FROM coupons c_sub
    LEFT JOIN (
        SELECT coupon_id, COUNT(id) AS usage_cnt
        FROM coupon_redemptions
```

## `postgres18-business-user-coupons-delete-exists-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-business-user-coupons-delete-exists-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL business case 8 equivalent: correlated EXISTS/NOT EXISTS version of the nested coupon cleanup.
DELETE FROM user_coupons uc
USING coupons c
INNER JOIN merchants m ON c.merchant_id = m.id
WHERE uc.coupon_id = c.id
  AND m.compliance_status = 'SUSPENDED'
  AND (
      c.expire_at < NOW()
```

## `postgres18-generated-comprehensive-query-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/examples/comprehensive-query-sql-001-030.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Comprehensive query SQL examples, CASE 01-30.
-- These statements are parser stress samples for joins, CTEs, subqueries,
-- recursive queries, derived tables, and complex expression-heavy predicates.

-- ============================================================================
-- CASE 01: Snowflake Schema Multi-Stage Warehouse Join with Path Concatenation
-- ============================================================================
SELECT 
```

## `postgres18-generated-industrial-complex-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/examples/industrial-complex-sql-001-050.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Industrial complex SQL examples, SQL 001-050.
-- These statements are saved as parser stress samples and are not expected to run
-- against the project test databases without matching demo schemas.

-- SQL 001: 窗口函数无缝嵌套多层 CASE WHEN 与字段双竖线动态组合
SELECT 
    tr.transaction_id,
    'TX_' || tr.merchant_code || '_' || TO_CHAR(tr.created_at, 'YYYYMMDD') || '_' ||
```

## `postgres18-generated-provided-complex-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/generated-provided-complex-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Provided complex SQL collection.
-- Source:
-- - 001-025 from attachment 87388f7d-2c4c-499a-b085-d158b969af45/pasted-text.txt
-- - 026-050 from attachment c61899d0-fc27-4a25-974a-56bc89fb0b7f/pasted-text.txt
-- - 051-075 from the chat message on 2026-06-18.
-- Notes:
-- - SQL 052 appears truncated in the chat message and is preserved as received.
-- - SQL 053-058 were not present in the supplied text.
```

## `postgres18-official-cte-dml-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-official-cte-dml-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres-official-cte-dml-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:source_rows.customer_id->orders.customer_id`

**Extractor Candidate Fingerprints**

- `VALUE:DIRECT:source_rows.customer_id->orders.customer_id`

**Input Preview**

```sql
-- PostgreSQL official docs inspired: WITH Queries and data-modifying
-- statements in WITH. Covers DELETE RETURNING, INSERT SELECT, UPDATE FROM,
-- MERGE USING, MATERIALIZED, and NOT MATERIALIZED boundaries.
WITH moved_rows AS (
  DELETE FROM order_staging os
  USING orders o
  WHERE os.order_id = o.id
  RETURNING os.order_id, os.user_id
```

## `postgres18-official-cte-nested-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-official-cte-nested-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: with.sql and WITH Queries.
-- Covers nested WITH, recursive CTEs, MATERIALIZED / NOT MATERIALIZED, and CTE
-- reuse without promoting CTE names to physical tables.
WITH base_orders AS MATERIALIZED (
  SELECT o.id AS order_id, o.user_id, o.customer_id
  FROM orders o
  JOIN users u ON o.user_id = u.id
),
```

## `postgres18-official-join-edge-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-official-join-edge-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: join.sql and Table Expressions.
-- Covers explicit outer joins, JOIN USING alias, NATURAL join, parenthesized
-- join trees, and legacy comma join predicates.
SELECT *
FROM orders o
INNER JOIN users u ON o.user_id = u.id
LEFT OUTER JOIN payments p ON p.order_id = o.id
RIGHT JOIN shipments s ON s.order_id = o.id
```

## `postgres18-official-lateral-function-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-official-lateral-function-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: Table Expressions.
-- Covers ROWS FROM, json_to_recordset, generate_series, and LATERAL function
-- rowsets without treating functions or their aliases as physical tables.
SELECT *
FROM orders o
LEFT JOIN LATERAL (
  SELECT o.user_id
) projected_user ON true
```

## `postgres18-official-lateral-nested-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-official-lateral-nested-join-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official docs inspired: Table Expressions, LATERAL, ROWS FROM,
-- table functions, UNNEST WITH ORDINALITY, and nested LATERAL joins.
SELECT *
FROM orders o
LEFT JOIN LATERAL (
  SELECT li.order_id, li.product_id
  FROM line_items li
  WHERE li.order_id = o.id
```

## `postgres18-official-multiway-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-official-multiway-join-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: join.sql and Table Expressions.
-- Covers multiway outer joins, parenthesized join trees, derived joins,
-- legacy comma join mixed with explicit JOIN, and multiple ON equality keys.
SELECT *
FROM order_roots o
LEFT JOIN order_items oi
  ON oi.order_id = o.id
 AND oi.tenant_id = o.tenant_id
```

## `postgres18-official-subquery-deep-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-official-subquery-deep-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: subselect.sql and Subquery
-- Expressions. Covers nested correlated EXISTS, tuple IN/NOT IN, row
-- constructor ANY/SOME/ALL, scalar subquery equality, and nested subquery joins.
SELECT *
FROM orders o
WHERE EXISTS (
  SELECT 1
  FROM users u
```

## `postgres18-official-subquery-edge-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres-official-subquery-edge-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL official regression/docs inspired: subselect.sql and Subquery
-- Expressions. Covers extra parentheses, correlated EXISTS, tuple IN/NOT IN,
-- scalar subquery equality, and ANY/SOME/ALL boundaries.
SELECT *
FROM ((SELECT o.id, o.user_id FROM orders o)) projected_orders
JOIN users u ON projected_orders.user_id = u.id;

SELECT *
```

## `postgres18-returning-old-new-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-returning-old-new-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-returning-old-new-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:account_balances.balance,transaction_ledgers.amount->account_balances.balance`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- PostgreSQL 18 RETURNING old/new: pseudo row values must not become physical tables.
UPDATE account_balances ab
SET balance = ab.balance + tx.amount
FROM transaction_ledgers tx
WHERE ab.user_id = tx.user_id
RETURNING old.balance AS previous_balance, new.balance AS updated_balance, tx.amount;
```

## `postgres18-sql-delete-using-no-alias`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/sql-delete-using-no-alias/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
DELETE FROM orders
USING users
WHERE orders.user_id = users.id;
```

## `postgres18-sql-lateral-derived`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/sql-lateral-derived/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
SELECT o.id, u.email
FROM orders o
JOIN LATERAL (
  SELECT o.user_id AS user_id
) x ON true
JOIN users u ON x.user_id = u.id;
```

## `postgres18-sql-merge-using`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/sql-merge-using/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/sql-merge-using/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:source_orders.id->target_orders.source_order_id`

**Extractor Candidate Fingerprints**

- `VALUE:DIRECT:source_orders.id->target_orders.source_order_id`

**Input Preview**

```sql
MERGE INTO target_orders AS t
USING source_orders AS s
ON t.source_order_id = s.id
WHEN MATCHED AND s.cancelled_at IS NULL THEN
  UPDATE SET synced_at = CURRENT_TIMESTAMP
WHEN NOT MATCHED THEN
  INSERT (source_order_id) VALUES (s.id);
```

## `postgres18-sql-multi-layer-cte`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/sql-multi-layer-cte/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
WITH "a" AS (
  SELECT o.id AS order_id, o.customer_id
  FROM "public"."orders" o
  JOIN "public"."customers" c ON o.customer_id = c.id
),
b AS (
  SELECT a.order_id, c.region_id
  FROM "a" a
```

## `postgres18-sql-quoted-mixed-alias`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/sql-quoted-mixed-alias/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
SELECT *
FROM "public"."orders" o
JOIN users ON o."user_id" = users.id
JOIN "payments" ON "payments".order_id = o.id;
```

## `postgres18-sql-recursive-cte`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/sql-recursive-cte/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
WITH RECURSIVE employee_paths(id, manager_id) AS (
  SELECT e.id, e.manager_id
  FROM employees e
  WHERE e.manager_id IS NULL
  UNION ALL
  SELECT e.id, e.manager_id
  FROM employees e
  JOIN employee_paths ep ON ep.id = e.manager_id
```

## `postgres18-sql-unnest-ordinality`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/sql-unnest-ordinality/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
SELECT u.id
FROM users u
JOIN orders o ON o.user_id = u.id
JOIN unnest(ARRAY[1, 2, 3]) WITH ORDINALITY AS input_ids(user_id, ord)
  ON input_ids.user_id = u.id;
```

## `postgres18-sql-update-from-aliases`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/sql-update-from-aliases/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
UPDATE orders o
SET status = 'PAID'
FROM users u
WHERE o.user_id = u.id;
```

