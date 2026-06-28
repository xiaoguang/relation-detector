# Data Lineage Full Audit

This file is generated from `test-fixtures/correctness` by `DataLineageAuditGeneratorTest`. Do not edit it by hand.

The report lists every correctness fixture and explains whether Data Lineage v1 already has golden coverage, can propose golden coverage, needs manual review, or is not applicable.

## Overview

| Classification | Count |
| --- | ---: |
| TOTAL | 622 |
| EXISTING_GOLD | 135 |
| SUGGESTED_GOLD | 0 |
| PENDING_REVIEW | 0 |
| NOT_APPLICABLE | 487 |

## `common-sample-data-portable-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/common/sample-data-portable-ddl/input.ddl.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Portable ERP schema generated from sample-data/mysql/8.0 object inventory.
-- SQL intentionally uses a common portable subset for relation-detector common token-event golden.

CREATE TABLE departments (
  id BIGINT PRIMARY KEY,
  parent_id BIGINT,
  name VARCHAR,
  code VARCHAR UNIQUE,
```

## `commonsample-data-full-01-schema-01-tables-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/portable/01-schema/01-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Portable ERP schema generated from sample-data/mysql/8.0 object inventory.
-- SQL intentionally uses a common portable subset for relation-detector common token-event golden.

CREATE TABLE departments (
  id BIGINT PRIMARY KEY,
  parent_id BIGINT,
  name VARCHAR,
  code VARCHAR UNIQUE,
```

## `commonsample-data-full-01-schema-02-views-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/portable/01-schema/02-views.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Portable view catalog. These definitions document the six MySQL ERP views using simple SELECT shapes.

CREATE VIEW v_account_balance AS
SELECT accounts.id
FROM accounts
JOIN vouchers ON accounts.id = vouchers.id;

CREATE VIEW v_dept_headcount AS
```

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

## `mysql-sample-data-enterprise-extension-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/mysql/8.0/01-schema/06-enterprise-extension-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统企业级扩展表
-- 覆盖: 多租户/账套、地址、税率、会计期间、收付款、
--       库存盘点/调拨/预留、工艺路线/工序、班次排班
-- 数据库: MySQL 8.0
-- ============================================================

USE erp_system;
```

## `mysql80-mysql-basic-correctness-case-01-ddl`

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

## `mysql80-mysql-basic-correctness-case-02-ddl`

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

## `mysql80-mysql-basic-correctness-case-03-ddl`

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

## `mysql80-mysql-basic-correctness-case-04-ddl`

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

## `mysql80-mysql-ddl-create-table-fk-index`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/mysql/v8_0/ddl-create-table-fk-index/input.ddl.sql` |
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

## `mysql80-mysql-official-alter-index-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-official-alter-index-ddl/input.ddl.sql` |
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

## `mysql80-mysql-official-complex-index-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-official-complex-index-ddl/input.ddl.sql` |
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

## `mysql80-mysql-official-functional-index-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-official-functional-index-ddl/input.ddl.sql` |
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

## `mysql80-mysql-official-index-options-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-official-index-options-ddl/input.ddl.sql` |
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

## `mysql80-mysql-official-invisible-index-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-official-invisible-index-ddl/input.ddl.sql` |
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

## `mysql80-mysql-official-special-index-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-official-special-index-ddl/input.ddl.sql` |
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

## `mysql80-sample-data-enterprise-extension-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/mysql/8.0/01-schema/06-enterprise-extension-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统企业级扩展表
-- 覆盖: 多租户/账套、地址、税率、会计期间、收付款、
--       库存盘点/调拨/预留、工艺路线/工序、班次排班
-- 数据库: MySQL 8.0
-- ============================================================

USE erp_system;
```

## `mysql80sample-data-full-01-schema-01-tables-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/mysql/8.0/01-schema/01-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统完整数据库设计
-- 模块: HR, 权限, 货品, 批号, 库存, 采购, 销售, 财务
-- 数据库: MySQL 8.0
-- ============================================================

CREATE DATABASE IF NOT EXISTS erp_system
  CHARACTER SET utf8mb4
```

## `mysql80sample-data-full-01-schema-02-indexes-and-views-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/mysql/8.0/01-schema/02-indexes-and-views.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 索引补充 - 覆盖跨表查询常用路径
-- ============================================================

USE erp_system;

-- 库存与批号关联查询
CREATE INDEX idx_inv_batch_warehouse ON inventory(batch_id, warehouse_id);
```

## `mysql80sample-data-full-01-schema-04-supplementary-tables-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/mysql/8.0/01-schema/04-supplementary-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统补充表: 发货物流、销售提成、促销活动、
--   三单匹配、固定资产、BOM生产工单、客服工单
-- 关系说明:
--   shipments -> sales_orders (1:1), 通过tracking_no追踪物流
--   sales_commissions -> sales_orders + employees (N:1:1), 按销售额计算提成
--   promotions -> sales_order_items (N:M), 通过promotion_items关联
--   invoices -> purchase_orders + purchase_receipts (三单匹配)
```

## `mysql80sample-data-full-01-schema-05-third-batch-tables-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/mysql/8.0/01-schema/05-third-batch-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统第三批补充表: 合同管理、AR/AP账龄、税务管理、
--   质检、审批流引擎、现金流预测、项目成本、多币种汇率、
--   绩效考核、序列号追踪、寄售库存、价格变更历史
-- 关系说明:
--   contracts -> sales_orders/purchase_orders (1:1), 管理合同条款和里程碑
--   ar_aging / ap_aging: 账龄分析用，按月计算应收账款/应付账款
--   tax_invoices: 增值税发票管理，进项税/销项税
```

## `mysqlsample-data-full-01-schema-01-tables-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/mysql/8.0/01-schema/01-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统完整数据库设计
-- 模块: HR, 权限, 货品, 批号, 库存, 采购, 销售, 财务
-- 数据库: MySQL 8.0
-- ============================================================

CREATE DATABASE IF NOT EXISTS erp_system
  CHARACTER SET utf8mb4
```

## `mysqlsample-data-full-01-schema-02-indexes-and-views-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/mysql/8.0/01-schema/02-indexes-and-views.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 索引补充 - 覆盖跨表查询常用路径
-- ============================================================

USE erp_system;

-- 库存与批号关联查询
CREATE INDEX idx_inv_batch_warehouse ON inventory(batch_id, warehouse_id);
```

## `mysqlsample-data-full-01-schema-04-supplementary-tables-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/mysql/8.0/01-schema/04-supplementary-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统补充表: 发货物流、销售提成、促销活动、
--   三单匹配、固定资产、BOM生产工单、客服工单
-- 关系说明:
--   shipments -> sales_orders (1:1), 通过tracking_no追踪物流
--   sales_commissions -> sales_orders + employees (N:1:1), 按销售额计算提成
--   promotions -> sales_order_items (N:M), 通过promotion_items关联
--   invoices -> purchase_orders + purchase_receipts (三单匹配)
```

## `mysqlsample-data-full-01-schema-05-third-batch-tables-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/mysql/8.0/01-schema/05-third-batch-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统第三批补充表: 合同管理、AR/AP账龄、税务管理、
--   质检、审批流引擎、现金流预测、项目成本、多币种汇率、
--   绩效考核、序列号追踪、寄售库存、价格变更历史
-- 关系说明:
--   contracts -> sales_orders/purchase_orders (1:1), 管理合同条款和里程碑
--   ar_aging / ap_aging: 账龄分析用，按月计算应收账款/应付账款
--   tax_invoices: 增值税发票管理，进项税/销项税
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
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-inbound-from-order-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:jsh_depot_item.oper_number,jsh_material_extend.purchase_decimal->jsh_depot_item.all_price`
- `VALUE:ARITHMETIC:jsh_depot_item.oper_number,jsh_material_extend.purchase_decimal->jsh_depot_item.tax_last_money`
- `VALUE:ARITHMETIC:jsh_material_current_stock.current_number,jsh_depot_item.oper_number->jsh_material_current_stock.current_number`
- `VALUE:DIRECT:jsh_depot_item.another_depot_id->jsh_depot_item.another_depot_id`
- `VALUE:DIRECT:jsh_depot_item.basic_number->jsh_depot_item.basic_number`
- `VALUE:DIRECT:jsh_depot_item.delete_flag->jsh_depot_item.delete_flag`
- `VALUE:DIRECT:jsh_depot_item.id->jsh_depot_item.link_id`
- `VALUE:DIRECT:jsh_depot_item.material_extend_id->jsh_depot_item.material_extend_id`
- `VALUE:DIRECT:jsh_depot_item.material_id->jsh_depot_item.material_id`
- `VALUE:DIRECT:jsh_depot_item.material_type->jsh_depot_item.material_type`
- `VALUE:DIRECT:jsh_depot_item.material_unit->jsh_depot_item.material_unit`
- `VALUE:DIRECT:jsh_depot_item.oper_number->jsh_depot_item.oper_number`
- `VALUE:DIRECT:jsh_depot_item.purchase_unit_price->jsh_depot_item.purchase_unit_price`
- `VALUE:DIRECT:jsh_depot_item.remark->jsh_depot_item.remark`
- `VALUE:DIRECT:jsh_depot_item.sku->jsh_depot_item.sku`
- `VALUE:DIRECT:jsh_depot_item.sn_list->jsh_depot_item.sn_list`
- `VALUE:DIRECT:jsh_depot_item.tax_money->jsh_depot_item.tax_money`
- `VALUE:DIRECT:jsh_depot_item.tax_rate->jsh_depot_item.tax_rate`
- `VALUE:DIRECT:jsh_depot_item.tax_unit_price->jsh_depot_item.tax_unit_price`
- `VALUE:DIRECT:jsh_depot_item.tenant_id->jsh_depot_item.tenant_id`
- `VALUE:DIRECT:jsh_material_extend.purchase_decimal->jsh_depot_item.unit_price`
- `VALUE:FUNCTION_CALL:jsh_material.expiry_num->jsh_depot_item.expiration_date`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:jsh_depot_item.oper_number,jsh_material_extend.purchase_decimal->jsh_depot_item.all_price`
- `VALUE:ARITHMETIC:jsh_depot_item.oper_number,jsh_material_extend.purchase_decimal->jsh_depot_item.tax_last_money`
- `VALUE:ARITHMETIC:jsh_material_current_stock.current_number,jsh_depot_item.oper_number->jsh_material_current_stock.current_number`
- `VALUE:DIRECT:jsh_depot_item.another_depot_id->jsh_depot_item.another_depot_id`
- `VALUE:DIRECT:jsh_depot_item.basic_number->jsh_depot_item.basic_number`
- `VALUE:DIRECT:jsh_depot_item.delete_flag->jsh_depot_item.delete_flag`
- `VALUE:DIRECT:jsh_depot_item.id->jsh_depot_item.link_id`
- `VALUE:DIRECT:jsh_depot_item.material_extend_id->jsh_depot_item.material_extend_id`
- `VALUE:DIRECT:jsh_depot_item.material_id->jsh_depot_item.material_id`
- `VALUE:DIRECT:jsh_depot_item.material_type->jsh_depot_item.material_type`
- `VALUE:DIRECT:jsh_depot_item.material_unit->jsh_depot_item.material_unit`
- `VALUE:DIRECT:jsh_depot_item.oper_number->jsh_depot_item.oper_number`
- `VALUE:DIRECT:jsh_depot_item.purchase_unit_price->jsh_depot_item.purchase_unit_price`
- `VALUE:DIRECT:jsh_depot_item.remark->jsh_depot_item.remark`
- `VALUE:DIRECT:jsh_depot_item.sku->jsh_depot_item.sku`
- `VALUE:DIRECT:jsh_depot_item.sn_list->jsh_depot_item.sn_list`
- `VALUE:DIRECT:jsh_depot_item.tax_money->jsh_depot_item.tax_money`
- `VALUE:DIRECT:jsh_depot_item.tax_rate->jsh_depot_item.tax_rate`
- `VALUE:DIRECT:jsh_depot_item.tax_unit_price->jsh_depot_item.tax_unit_price`
- `VALUE:DIRECT:jsh_depot_item.tenant_id->jsh_depot_item.tenant_id`
- `VALUE:DIRECT:jsh_material_extend.purchase_decimal->jsh_depot_item.unit_price`
- `VALUE:FUNCTION_CALL:jsh_material.expiry_num->jsh_depot_item.expiration_date`

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
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:jsh_depot_item.oper_number,jsh_material_extend.purchase_decimal->jsh_depot_item.all_price`
- `VALUE:ARITHMETIC:jsh_depot_item.oper_number,jsh_material_extend.purchase_decimal->jsh_depot_item.tax_last_money`
- `VALUE:DIRECT:jsh_depot_item.basic_number->jsh_depot_item.basic_number`
- `VALUE:DIRECT:jsh_depot_item.delete_flag->jsh_depot_item.delete_flag`
- `VALUE:DIRECT:jsh_depot_item.depot_id->jsh_depot_item.depot_id`
- `VALUE:DIRECT:jsh_depot_item.id->jsh_depot_item.link_id`
- `VALUE:DIRECT:jsh_depot_item.material_extend_id->jsh_depot_item.material_extend_id`
- `VALUE:DIRECT:jsh_depot_item.material_id->jsh_depot_item.material_id`
- `VALUE:DIRECT:jsh_depot_item.material_type->jsh_depot_item.material_type`
- `VALUE:DIRECT:jsh_depot_item.material_unit->jsh_depot_item.material_unit`
- `VALUE:DIRECT:jsh_depot_item.oper_number->jsh_depot_item.oper_number`
- `VALUE:DIRECT:jsh_depot_item.purchase_unit_price->jsh_depot_item.purchase_unit_price`
- `VALUE:DIRECT:jsh_depot_item.remark->jsh_depot_item.remark`
- `VALUE:DIRECT:jsh_depot_item.sku->jsh_depot_item.sku`
- `VALUE:DIRECT:jsh_depot_item.tax_money->jsh_depot_item.tax_money`
- `VALUE:DIRECT:jsh_depot_item.tax_rate->jsh_depot_item.tax_rate`
- `VALUE:DIRECT:jsh_depot_item.tax_unit_price->jsh_depot_item.tax_unit_price`
- `VALUE:DIRECT:jsh_depot_item.tenant_id->jsh_depot_item.tenant_id`
- `VALUE:DIRECT:jsh_material_extend.purchase_decimal->jsh_depot_item.unit_price`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:jsh_depot_item.oper_number,jsh_material_extend.purchase_decimal->jsh_depot_item.all_price`
- `VALUE:ARITHMETIC:jsh_depot_item.oper_number,jsh_material_extend.purchase_decimal->jsh_depot_item.tax_last_money`
- `VALUE:DIRECT:jsh_depot_item.basic_number->jsh_depot_item.basic_number`
- `VALUE:DIRECT:jsh_depot_item.delete_flag->jsh_depot_item.delete_flag`
- `VALUE:DIRECT:jsh_depot_item.depot_id->jsh_depot_item.depot_id`
- `VALUE:DIRECT:jsh_depot_item.id->jsh_depot_item.link_id`
- `VALUE:DIRECT:jsh_depot_item.material_extend_id->jsh_depot_item.material_extend_id`
- `VALUE:DIRECT:jsh_depot_item.material_id->jsh_depot_item.material_id`
- `VALUE:DIRECT:jsh_depot_item.material_type->jsh_depot_item.material_type`
- `VALUE:DIRECT:jsh_depot_item.material_unit->jsh_depot_item.material_unit`
- `VALUE:DIRECT:jsh_depot_item.oper_number->jsh_depot_item.oper_number`
- `VALUE:DIRECT:jsh_depot_item.purchase_unit_price->jsh_depot_item.purchase_unit_price`
- `VALUE:DIRECT:jsh_depot_item.remark->jsh_depot_item.remark`
- `VALUE:DIRECT:jsh_depot_item.sku->jsh_depot_item.sku`
- `VALUE:DIRECT:jsh_depot_item.tax_money->jsh_depot_item.tax_money`
- `VALUE:DIRECT:jsh_depot_item.tax_rate->jsh_depot_item.tax_rate`
- `VALUE:DIRECT:jsh_depot_item.tax_unit_price->jsh_depot_item.tax_unit_price`
- `VALUE:DIRECT:jsh_depot_item.tenant_id->jsh_depot_item.tenant_id`
- `VALUE:DIRECT:jsh_material_extend.purchase_decimal->jsh_depot_item.unit_price`

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
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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
| Classification | `NOT_APPLICABLE` |
| Reason | local temporary table sources are excluded from Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-refresh-org-pdf-sql/expected-lineage.json` |

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

## `basic-correctness-case-01-procedure-proc-simulate-yearly-sales-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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

## `common-sample-data-portable-data-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/common/sample-data-portable-data-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Portable seed data targets, expressed as INSERT ... SELECT for common parser compatibility.

INSERT INTO departments (id)
SELECT 1;

INSERT INTO positions (id)
SELECT 2;
```

## `common-sample-data-portable-lineage-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/common/sample-data-portable-lineage-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/common/sample-data-portable-lineage-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:customers.id->invoices.customer_id`
- `VALUE:DIRECT:sales_orders.id->invoices.id`
- `VALUE:DIRECT:sales_orders.total_amount->invoices.total_amount`

**Extractor Candidate Fingerprints**

- `VALUE:DIRECT:customers.id->invoices.customer_id`
- `VALUE:DIRECT:sales_orders.id->invoices.id`
- `VALUE:DIRECT:sales_orders.total_amount->invoices.total_amount`

**Input Preview**

```sql
-- hr_employee_department
SELECT employees.id, departments.id
FROM employees
JOIN departments ON employees.department_id = departments.id
WHERE EXISTS (
  SELECT 1
  FROM departments
  WHERE departments.id = employees.department_id
```

## `common-sample-data-portable-process-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/common/sample-data-portable-process-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/common/sample-data-portable-process-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:accounts.id->cashier_journals.account_id`
- `VALUE:DIRECT:accounts.id->cashier_journals.id`
- `VALUE:DIRECT:accounts.id->reconciliations.account_id`
- `VALUE:DIRECT:accounts.id->reconciliations.id`
- `VALUE:DIRECT:approval_workflows.id->approval_instances.id`
- `VALUE:DIRECT:approval_workflows.id->approval_instances.workflow_id`
- `VALUE:DIRECT:customers.id->sales_orders.customer_id`
- `VALUE:DIRECT:customers.id->sales_orders.id`
- `VALUE:DIRECT:customers.id->service_tickets.customer_id`
- `VALUE:DIRECT:customers.id->service_tickets.id`
- `VALUE:DIRECT:departments.id->departments.id`
- `VALUE:DIRECT:departments.id->departments.parent_id`
- `VALUE:DIRECT:departments.id->employees.department_id`
- `VALUE:DIRECT:departments.id->employees.id`
- `VALUE:DIRECT:departments.id->purchase_requisitions.department_id`
- `VALUE:DIRECT:departments.id->purchase_requisitions.id`
- `VALUE:DIRECT:employees.id->attendance.employee_id`
- `VALUE:DIRECT:employees.id->attendance.id`
- `VALUE:DIRECT:employees.id->audit_log.id`
- `VALUE:DIRECT:employees.id->contracts.id`
- `VALUE:DIRECT:employees.id->contracts.prepared_by`
- `VALUE:DIRECT:employees.id->customers.id`
- `VALUE:DIRECT:employees.id->departments.id`
- `VALUE:DIRECT:employees.id->inventory.id`
- `VALUE:DIRECT:employees.id->performance_reviews.employee_id`
- `VALUE:DIRECT:employees.id->performance_reviews.id`
- `VALUE:DIRECT:employees.id->products.id`
- `VALUE:DIRECT:employees.id->purchase_orders.id`
- `VALUE:DIRECT:employees.id->sales_orders.id`
- `VALUE:DIRECT:employees.id->suppliers.id`
- `VALUE:DIRECT:employees.id->tax_filings.id`
- `VALUE:DIRECT:employees.id->tax_filings.prepared_by`
- `VALUE:DIRECT:employees.id->vouchers.id`
- `VALUE:DIRECT:employees.id->vouchers.prepared_by`
- `VALUE:DIRECT:employees.id->warehouses.id`
- `VALUE:DIRECT:employees.id->warehouses.manager_id`
- `VALUE:DIRECT:invoices.id->three_way_matching.id`
- `VALUE:DIRECT:invoices.id->three_way_matching.invoice_id`
- `VALUE:DIRECT:ledger_books.id->accounting_periods.id`
- `VALUE:DIRECT:ledger_books.id->accounting_periods.ledger_book_id`
- `VALUE:DIRECT:product_categories.id->products.category_id`
- `VALUE:DIRECT:product_categories.id->products.id`
- `VALUE:DIRECT:products.id->inventory.id`
- `VALUE:DIRECT:products.id->inventory.product_id`
- `VALUE:DIRECT:products.id->serial_numbers.id`
- `VALUE:DIRECT:products.id->serial_numbers.product_id`
- `VALUE:DIRECT:promotion_products.promotion_id->promotions.id`
- `VALUE:DIRECT:purchase_orders.id->purchase_returns.id`
- `VALUE:DIRECT:purchase_orders.id->purchase_returns.purchase_order_id`
- `VALUE:DIRECT:sales_orders.customer_id->customers.id`
- `VALUE:DIRECT:sales_orders.id->sales_returns.id`
- `VALUE:DIRECT:sales_orders.id->sales_returns.order_id`
- `VALUE:DIRECT:sales_orders.id->shipments.id`
- `VALUE:DIRECT:sales_orders.id->shipments.order_id`
- `VALUE:DIRECT:supplier_products.supplier_id->suppliers.id`
- `VALUE:DIRECT:suppliers.id->purchase_orders.id`
- `VALUE:DIRECT:suppliers.id->purchase_orders.supplier_id`
- `VALUE:DIRECT:vouchers.id->settlements.id`
- `VALUE:DIRECT:vouchers.id->settlements.voucher_id`
- `VALUE:DIRECT:warehouses.id->damage_reports.id`
- `VALUE:DIRECT:warehouses.id->damage_reports.warehouse_id`
- `VALUE:DIRECT:warehouses.id->stock_transfers.from_warehouse_id`
- `VALUE:DIRECT:warehouses.id->stock_transfers.id`
- `VALUE:DIRECT:warehouses.id->stocktakes.id`
- `VALUE:DIRECT:warehouses.id->stocktakes.warehouse_id`
- `VALUE:DIRECT:work_orders.id->work_order_materials.id`
- `VALUE:DIRECT:work_orders.id->work_order_materials.work_order_id`

**Extractor Candidate Fingerprints**

- `VALUE:DIRECT:accounts.id->cashier_journals.account_id`
- `VALUE:DIRECT:accounts.id->cashier_journals.id`
- `VALUE:DIRECT:accounts.id->reconciliations.account_id`
- `VALUE:DIRECT:accounts.id->reconciliations.id`
- `VALUE:DIRECT:approval_workflows.id->approval_instances.id`
- `VALUE:DIRECT:approval_workflows.id->approval_instances.workflow_id`
- `VALUE:DIRECT:customers.id->sales_orders.customer_id`
- `VALUE:DIRECT:customers.id->sales_orders.id`
- `VALUE:DIRECT:customers.id->service_tickets.customer_id`
- `VALUE:DIRECT:customers.id->service_tickets.id`
- `VALUE:DIRECT:departments.id->departments.id`
- `VALUE:DIRECT:departments.id->departments.parent_id`
- `VALUE:DIRECT:departments.id->employees.department_id`
- `VALUE:DIRECT:departments.id->employees.id`
- `VALUE:DIRECT:departments.id->purchase_requisitions.department_id`
- `VALUE:DIRECT:departments.id->purchase_requisitions.id`
- `VALUE:DIRECT:employees.id->attendance.employee_id`
- `VALUE:DIRECT:employees.id->attendance.id`
- `VALUE:DIRECT:employees.id->audit_log.id`
- `VALUE:DIRECT:employees.id->contracts.id`
- `VALUE:DIRECT:employees.id->contracts.prepared_by`
- `VALUE:DIRECT:employees.id->customers.id`
- `VALUE:DIRECT:employees.id->departments.id`
- `VALUE:DIRECT:employees.id->inventory.id`
- `VALUE:DIRECT:employees.id->performance_reviews.employee_id`
- `VALUE:DIRECT:employees.id->performance_reviews.id`
- `VALUE:DIRECT:employees.id->products.id`
- `VALUE:DIRECT:employees.id->purchase_orders.id`
- `VALUE:DIRECT:employees.id->sales_orders.id`
- `VALUE:DIRECT:employees.id->suppliers.id`
- `VALUE:DIRECT:employees.id->tax_filings.id`
- `VALUE:DIRECT:employees.id->tax_filings.prepared_by`
- `VALUE:DIRECT:employees.id->vouchers.id`
- `VALUE:DIRECT:employees.id->vouchers.prepared_by`
- `VALUE:DIRECT:employees.id->warehouses.id`
- `VALUE:DIRECT:employees.id->warehouses.manager_id`
- `VALUE:DIRECT:invoices.id->three_way_matching.id`
- `VALUE:DIRECT:invoices.id->three_way_matching.invoice_id`
- `VALUE:DIRECT:ledger_books.id->accounting_periods.id`
- `VALUE:DIRECT:ledger_books.id->accounting_periods.ledger_book_id`
- `VALUE:DIRECT:product_categories.id->products.category_id`
- `VALUE:DIRECT:product_categories.id->products.id`
- `VALUE:DIRECT:products.id->inventory.id`
- `VALUE:DIRECT:products.id->inventory.product_id`
- `VALUE:DIRECT:products.id->serial_numbers.id`
- `VALUE:DIRECT:products.id->serial_numbers.product_id`
- `VALUE:DIRECT:promotion_products.promotion_id->promotions.id`
- `VALUE:DIRECT:purchase_orders.id->purchase_returns.id`
- `VALUE:DIRECT:purchase_orders.id->purchase_returns.purchase_order_id`
- `VALUE:DIRECT:sales_orders.customer_id->customers.id`
- `VALUE:DIRECT:sales_orders.id->sales_returns.id`
- `VALUE:DIRECT:sales_orders.id->sales_returns.order_id`
- `VALUE:DIRECT:sales_orders.id->shipments.id`
- `VALUE:DIRECT:sales_orders.id->shipments.order_id`
- `VALUE:DIRECT:supplier_products.supplier_id->suppliers.id`
- `VALUE:DIRECT:suppliers.id->purchase_orders.id`
- `VALUE:DIRECT:suppliers.id->purchase_orders.supplier_id`
- `VALUE:DIRECT:vouchers.id->settlements.id`
- `VALUE:DIRECT:vouchers.id->settlements.voucher_id`
- `VALUE:DIRECT:warehouses.id->damage_reports.id`
- `VALUE:DIRECT:warehouses.id->damage_reports.warehouse_id`
- `VALUE:DIRECT:warehouses.id->stock_transfers.from_warehouse_id`
- `VALUE:DIRECT:warehouses.id->stock_transfers.id`
- `VALUE:DIRECT:warehouses.id->stocktakes.id`
- `VALUE:DIRECT:warehouses.id->stocktakes.warehouse_id`
- `VALUE:DIRECT:work_orders.id->work_order_materials.id`
- `VALUE:DIRECT:work_orders.id->work_order_materials.work_order_id`

**Input Preview**

```sql
-- relation-detector-fixture-source: PROCEDURE:portable.sp_approve_requisition
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_approve_sales_return
INSERT INTO sales_returns (id, order_id)
```

## `common-sample-data-portable-relations-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/common/sample-data-portable-relations-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/common/sample-data-portable-relations-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:customers.id->invoices.customer_id`
- `VALUE:DIRECT:sales_orders.id->invoices.id`
- `VALUE:DIRECT:sales_orders.total_amount->invoices.total_amount`

**Extractor Candidate Fingerprints**

- `VALUE:DIRECT:customers.id->invoices.customer_id`
- `VALUE:DIRECT:sales_orders.id->invoices.id`
- `VALUE:DIRECT:sales_orders.total_amount->invoices.total_amount`

**Input Preview**

```sql
-- hr_employee_department
SELECT employees.id, departments.id
FROM employees
JOIN departments ON employees.department_id = departments.id
WHERE EXISTS (
  SELECT 1
  FROM departments
  WHERE departments.id = employees.department_id
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

## `common-sql-common-aggregate-in-negative`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/common/sql-common-aggregate-in-negative/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
SELECT o.customer_id
FROM orders o
WHERE o.total_amount IN (
  SELECT SUM(p.amount)
  FROM payments p
);
```

## `common-sql-common-case-update-lineage`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/common/sql-common-case-update-lineage/input.sql` |
| Expected lineage | `test-fixtures/correctness/common/sql-common-case-update-lineage/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:customer_scores.score,customer_scores.base_score->customer_scores.score_bucket`

**Extractor Candidate Fingerprints**

- `CONTROL:CASE_WHEN:customer_scores.score,customer_scores.base_score->customer_scores.score_bucket`

**Input Preview**

```sql
UPDATE customer_scores cs
SET score_bucket = CASE
  WHEN cs.score > 90 THEN cs.score
  ELSE cs.base_score
END;
```

## `common-sql-common-comma-join`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/common/sql-common-comma-join/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
SELECT o.id, c.name
FROM orders o, customers c
WHERE o.customer_id = c.id;
```

## `common-sql-common-cte-exists-in`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/common/sql-common-cte-exists-in/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
WITH active_customers AS (
  SELECT c.id, c.region_id
  FROM customers c
  WHERE EXISTS (
    SELECT 1
    FROM customer_flags f
    WHERE f.customer_id = c.id
  )
```

## `common-sql-common-cte-insert-lineage`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/common/sql-common-cte-insert-lineage/input.sql` |
| Expected lineage | `test-fixtures/correctness/common/sql-common-cte-insert-lineage/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:customers.id->customer_region_rollup.customer_id`
- `VALUE:DIRECT:customers.region_id->customer_region_rollup.region_id`

**Extractor Candidate Fingerprints**

- `VALUE:DIRECT:customers.id->customer_region_rollup.customer_id`
- `VALUE:DIRECT:customers.region_id->customer_region_rollup.region_id`

**Input Preview**

```sql
INSERT INTO customer_region_rollup (customer_id, region_id)
WITH active_customers AS (
  SELECT c.id, c.region_id
  FROM customers c
)
SELECT ac.id, ac.region_id
FROM active_customers ac;
```

## `common-sql-common-delete-exists`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/common/sql-common-delete-exists/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
DELETE FROM orders o
WHERE EXISTS (
  SELECT 1
  FROM customers c
  WHERE c.id = o.customer_id
);
```

## `common-sql-common-derived-table`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/common/sql-common-derived-table/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
SELECT o.id, dc.id AS customer_id
FROM orders o
JOIN (
  SELECT c.id
  FROM customers c
) dc ON o.customer_id = dc.id;
```

## `common-sql-common-function-equality-negative`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/common/sql-common-function-equality-negative/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
SELECT c.id, u.id
FROM customers c
JOIN users u ON lower(c.email) = u.email;
```

## `common-sql-common-insert-select-lineage`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/common/sql-common-insert-select-lineage/input.sql` |
| Expected lineage | `test-fixtures/correctness/common/sql-common-insert-select-lineage/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:orders.customer_id->customer_rollup.customer_id`
- `VALUE:DIRECT:orders.total_amount->customer_rollup.total_amount`

**Extractor Candidate Fingerprints**

- `VALUE:DIRECT:orders.customer_id->customer_rollup.customer_id`
- `VALUE:DIRECT:orders.total_amount->customer_rollup.total_amount`

**Input Preview**

```sql
INSERT INTO customer_rollup (customer_id, total_amount)
SELECT o.customer_id, o.total_amount
FROM orders o;
```

## `common-sql-common-literal-in-like-negative`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/common/sql-common-literal-in-like-negative/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
SELECT c.id
FROM customers c
WHERE c.status IN ('ACTIVE', 'VIP')
  AND c.name LIKE 'A%';
```

## `common-sql-common-multi-join`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/common/sql-common-multi-join/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
SELECT o.id, c.name, p.sku
FROM orders o
JOIN customers c ON o.customer_id = c.id
JOIN order_items oi ON oi.order_id = o.id
JOIN products p ON oi.product_id = p.id;
```

## `common-sql-common-scalar-in`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/common/sql-common-scalar-in/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
SELECT o.id
FROM orders o
WHERE o.customer_id IN (
  SELECT c.id
  FROM customers c
);
```

## `common-sql-common-self-join`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/common/sql-common-self-join/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
SELECT e.id, m.id AS manager_id
FROM employees e
JOIN employees m ON e.manager_id = m.id;
```

## `common-sql-common-tuple-in`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/common/sql-common-tuple-in/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
SELECT o.id
FROM orders o
WHERE (o.region_id, o.customer_id) IN (
  SELECT cr.region_id, cr.customer_id
  FROM customer_regions cr
);
```

## `common-sql-common-update-set-lineage`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/common/sql-common-update-set-lineage/input.sql` |
| Expected lineage | `test-fixtures/correctness/common/sql-common-update-set-lineage/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:customer_rollup.total_amount,orders.total_amount->customer_rollup.total_amount`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:customer_rollup.total_amount,orders.total_amount->customer_rollup.total_amount`

**Input Preview**

```sql
UPDATE customer_rollup cr
SET total_amount = cr.total_amount + (
  SELECT o.total_amount
  FROM orders o
  WHERE o.customer_id = cr.customer_id
);
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

## `commonsample-data-full-01-schema-02-views-views-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `VIEW` |
| Input | `test-fixtures/correctness/common/common-sample-data-full-01-schema-02-views-views-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/common/common-sample-data-full-01-schema-02-views-views-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: VIEW:portable.v_account_balance
SELECT accounts.id
FROM accounts
JOIN vouchers ON accounts.id = vouchers.id
-- relation-detector-fixture-end

-- relation-detector-fixture-source: VIEW:portable.v_dept_headcount
SELECT departments.id
```

## `commonsample-data-full-02-processes-01-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/common/common-sample-data-full-02-processes-01-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/common/common-sample-data-full-02-processes-01-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:portable.sp_approve_requisition
CREATE PROCEDURE sp_approve_requisition()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;
-- relation-detector-fixture-end
```

## `commonsample-data-full-02-processes-02-functions-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/common/common-sample-data-full-02-processes-02-functions-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/common/common-sample-data-full-02-processes-02-functions-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:portable.fn_calculate_income_tax
CREATE FUNCTION fn_calculate_income_tax() RETURNS BIGINT
BEGIN ATOMIC
  SELECT tax_filings.id, employees.id
  FROM tax_filings
  JOIN employees ON tax_filings.prepared_by = employees.id
  WHERE tax_filings.id IN (
    SELECT tax_filings.id
```

## `commonsample-data-full-02-processes-03-triggers-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/common/common-sample-data-full-02-processes-03-triggers-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/common/common-sample-data-full-02-processes-03-triggers-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:employees.id->audit_log.id`

**Extractor Candidate Fingerprints**

- `VALUE:DIRECT:employees.id->audit_log.id`

**Input Preview**

```sql
-- relation-detector-fixture-source: TRIGGER:portable.trg_audit_employee_insert
CREATE TRIGGER trg_audit_employee_insert
AFTER INSERT ON employees
REFERENCING NEW ROW AS new_row
FOR EACH ROW
BEGIN ATOMIC
  SELECT employees.id, departments.id
  FROM employees
```

## `commonsample-data-full-02-processes-04-process-bodies-for-golden-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/common/common-sample-data-full-02-processes-04-process-bodies-for-golden-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/common/common-sample-data-full-02-processes-04-process-bodies-for-golden-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:accounts.id->cashier_journals.account_id`
- `VALUE:DIRECT:accounts.id->cashier_journals.id`
- `VALUE:DIRECT:accounts.id->reconciliations.account_id`
- `VALUE:DIRECT:accounts.id->reconciliations.id`
- `VALUE:DIRECT:approval_workflows.id->approval_instances.id`
- `VALUE:DIRECT:approval_workflows.id->approval_instances.workflow_id`
- `VALUE:DIRECT:customers.id->sales_orders.customer_id`
- `VALUE:DIRECT:customers.id->sales_orders.id`
- `VALUE:DIRECT:customers.id->service_tickets.customer_id`
- `VALUE:DIRECT:customers.id->service_tickets.id`
- `VALUE:DIRECT:departments.id->departments.id`
- `VALUE:DIRECT:departments.id->departments.parent_id`
- `VALUE:DIRECT:departments.id->employees.department_id`
- `VALUE:DIRECT:departments.id->employees.id`
- `VALUE:DIRECT:departments.id->purchase_requisitions.department_id`
- `VALUE:DIRECT:departments.id->purchase_requisitions.id`
- `VALUE:DIRECT:employees.id->attendance.employee_id`
- `VALUE:DIRECT:employees.id->attendance.id`
- `VALUE:DIRECT:employees.id->audit_log.id`
- `VALUE:DIRECT:employees.id->contracts.id`
- `VALUE:DIRECT:employees.id->contracts.prepared_by`
- `VALUE:DIRECT:employees.id->customers.id`
- `VALUE:DIRECT:employees.id->departments.id`
- `VALUE:DIRECT:employees.id->inventory.id`
- `VALUE:DIRECT:employees.id->performance_reviews.employee_id`
- `VALUE:DIRECT:employees.id->performance_reviews.id`
- `VALUE:DIRECT:employees.id->products.id`
- `VALUE:DIRECT:employees.id->purchase_orders.id`
- `VALUE:DIRECT:employees.id->sales_orders.id`
- `VALUE:DIRECT:employees.id->suppliers.id`
- `VALUE:DIRECT:employees.id->tax_filings.id`
- `VALUE:DIRECT:employees.id->tax_filings.prepared_by`
- `VALUE:DIRECT:employees.id->vouchers.id`
- `VALUE:DIRECT:employees.id->vouchers.prepared_by`
- `VALUE:DIRECT:employees.id->warehouses.id`
- `VALUE:DIRECT:employees.id->warehouses.manager_id`
- `VALUE:DIRECT:invoices.id->three_way_matching.id`
- `VALUE:DIRECT:invoices.id->three_way_matching.invoice_id`
- `VALUE:DIRECT:ledger_books.id->accounting_periods.id`
- `VALUE:DIRECT:ledger_books.id->accounting_periods.ledger_book_id`
- `VALUE:DIRECT:product_categories.id->products.category_id`
- `VALUE:DIRECT:product_categories.id->products.id`
- `VALUE:DIRECT:products.id->inventory.id`
- `VALUE:DIRECT:products.id->inventory.product_id`
- `VALUE:DIRECT:products.id->serial_numbers.id`
- `VALUE:DIRECT:products.id->serial_numbers.product_id`
- `VALUE:DIRECT:promotion_products.promotion_id->promotions.id`
- `VALUE:DIRECT:purchase_orders.id->purchase_returns.id`
- `VALUE:DIRECT:purchase_orders.id->purchase_returns.purchase_order_id`
- `VALUE:DIRECT:sales_orders.customer_id->customers.id`
- `VALUE:DIRECT:sales_orders.id->sales_returns.id`
- `VALUE:DIRECT:sales_orders.id->sales_returns.order_id`
- `VALUE:DIRECT:sales_orders.id->shipments.id`
- `VALUE:DIRECT:sales_orders.id->shipments.order_id`
- `VALUE:DIRECT:supplier_products.supplier_id->suppliers.id`
- `VALUE:DIRECT:suppliers.id->purchase_orders.id`
- `VALUE:DIRECT:suppliers.id->purchase_orders.supplier_id`
- `VALUE:DIRECT:vouchers.id->settlements.id`
- `VALUE:DIRECT:vouchers.id->settlements.voucher_id`
- `VALUE:DIRECT:warehouses.id->damage_reports.id`
- `VALUE:DIRECT:warehouses.id->damage_reports.warehouse_id`
- `VALUE:DIRECT:warehouses.id->stock_transfers.from_warehouse_id`
- `VALUE:DIRECT:warehouses.id->stock_transfers.id`
- `VALUE:DIRECT:warehouses.id->stocktakes.id`
- `VALUE:DIRECT:warehouses.id->stocktakes.warehouse_id`
- `VALUE:DIRECT:work_orders.id->work_order_materials.id`
- `VALUE:DIRECT:work_orders.id->work_order_materials.work_order_id`

**Extractor Candidate Fingerprints**

- `VALUE:DIRECT:accounts.id->cashier_journals.account_id`
- `VALUE:DIRECT:accounts.id->cashier_journals.id`
- `VALUE:DIRECT:accounts.id->reconciliations.account_id`
- `VALUE:DIRECT:accounts.id->reconciliations.id`
- `VALUE:DIRECT:approval_workflows.id->approval_instances.id`
- `VALUE:DIRECT:approval_workflows.id->approval_instances.workflow_id`
- `VALUE:DIRECT:customers.id->sales_orders.customer_id`
- `VALUE:DIRECT:customers.id->sales_orders.id`
- `VALUE:DIRECT:customers.id->service_tickets.customer_id`
- `VALUE:DIRECT:customers.id->service_tickets.id`
- `VALUE:DIRECT:departments.id->departments.id`
- `VALUE:DIRECT:departments.id->departments.parent_id`
- `VALUE:DIRECT:departments.id->employees.department_id`
- `VALUE:DIRECT:departments.id->employees.id`
- `VALUE:DIRECT:departments.id->purchase_requisitions.department_id`
- `VALUE:DIRECT:departments.id->purchase_requisitions.id`
- `VALUE:DIRECT:employees.id->attendance.employee_id`
- `VALUE:DIRECT:employees.id->attendance.id`
- `VALUE:DIRECT:employees.id->audit_log.id`
- `VALUE:DIRECT:employees.id->contracts.id`
- `VALUE:DIRECT:employees.id->contracts.prepared_by`
- `VALUE:DIRECT:employees.id->customers.id`
- `VALUE:DIRECT:employees.id->departments.id`
- `VALUE:DIRECT:employees.id->inventory.id`
- `VALUE:DIRECT:employees.id->performance_reviews.employee_id`
- `VALUE:DIRECT:employees.id->performance_reviews.id`
- `VALUE:DIRECT:employees.id->products.id`
- `VALUE:DIRECT:employees.id->purchase_orders.id`
- `VALUE:DIRECT:employees.id->sales_orders.id`
- `VALUE:DIRECT:employees.id->suppliers.id`
- `VALUE:DIRECT:employees.id->tax_filings.id`
- `VALUE:DIRECT:employees.id->tax_filings.prepared_by`
- `VALUE:DIRECT:employees.id->vouchers.id`
- `VALUE:DIRECT:employees.id->vouchers.prepared_by`
- `VALUE:DIRECT:employees.id->warehouses.id`
- `VALUE:DIRECT:employees.id->warehouses.manager_id`
- `VALUE:DIRECT:invoices.id->three_way_matching.id`
- `VALUE:DIRECT:invoices.id->three_way_matching.invoice_id`
- `VALUE:DIRECT:ledger_books.id->accounting_periods.id`
- `VALUE:DIRECT:ledger_books.id->accounting_periods.ledger_book_id`
- `VALUE:DIRECT:product_categories.id->products.category_id`
- `VALUE:DIRECT:product_categories.id->products.id`
- `VALUE:DIRECT:products.id->inventory.id`
- `VALUE:DIRECT:products.id->inventory.product_id`
- `VALUE:DIRECT:products.id->serial_numbers.id`
- `VALUE:DIRECT:products.id->serial_numbers.product_id`
- `VALUE:DIRECT:promotion_products.promotion_id->promotions.id`
- `VALUE:DIRECT:purchase_orders.id->purchase_returns.id`
- `VALUE:DIRECT:purchase_orders.id->purchase_returns.purchase_order_id`
- `VALUE:DIRECT:sales_orders.customer_id->customers.id`
- `VALUE:DIRECT:sales_orders.id->sales_returns.id`
- `VALUE:DIRECT:sales_orders.id->sales_returns.order_id`
- `VALUE:DIRECT:sales_orders.id->shipments.id`
- `VALUE:DIRECT:sales_orders.id->shipments.order_id`
- `VALUE:DIRECT:supplier_products.supplier_id->suppliers.id`
- `VALUE:DIRECT:suppliers.id->purchase_orders.id`
- `VALUE:DIRECT:suppliers.id->purchase_orders.supplier_id`
- `VALUE:DIRECT:vouchers.id->settlements.id`
- `VALUE:DIRECT:vouchers.id->settlements.voucher_id`
- `VALUE:DIRECT:warehouses.id->damage_reports.id`
- `VALUE:DIRECT:warehouses.id->damage_reports.warehouse_id`
- `VALUE:DIRECT:warehouses.id->stock_transfers.from_warehouse_id`
- `VALUE:DIRECT:warehouses.id->stock_transfers.id`
- `VALUE:DIRECT:warehouses.id->stocktakes.id`
- `VALUE:DIRECT:warehouses.id->stocktakes.warehouse_id`
- `VALUE:DIRECT:work_orders.id->work_order_materials.id`
- `VALUE:DIRECT:work_orders.id->work_order_materials.work_order_id`

**Input Preview**

```sql
-- relation-detector-fixture-source: PROCEDURE:portable.sp_approve_requisition
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_approve_sales_return
INSERT INTO sales_returns (id, order_id)
```

## `commonsample-data-full-03-data-01-master-data-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/portable/03-data/01-master-data.sql` |
| Expected lineage | `test-fixtures/correctness/common/common-sample-data-full-03-data-01-master-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- Portable seed data targets, expressed as INSERT ... SELECT for common parser compatibility.

INSERT INTO departments (id)
SELECT 1;

INSERT INTO positions (id)
SELECT 2;
```

## `commonsample-data-full-04-queries-01-business-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/portable/04-queries/01-business-queries.sql` |
| Expected lineage | `test-fixtures/correctness/common/common-sample-data-full-04-queries-01-business-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:customers.id->invoices.customer_id`
- `VALUE:DIRECT:sales_orders.id->invoices.id`
- `VALUE:DIRECT:sales_orders.total_amount->invoices.total_amount`

**Extractor Candidate Fingerprints**

- `VALUE:DIRECT:customers.id->invoices.customer_id`
- `VALUE:DIRECT:sales_orders.id->invoices.id`
- `VALUE:DIRECT:sales_orders.total_amount->invoices.total_amount`

**Input Preview**

```sql
-- hr_employee_department
SELECT employees.id, departments.id
FROM employees
JOIN departments ON employees.department_id = departments.id
WHERE EXISTS (
  SELECT 1
  FROM departments
  WHERE departments.id = employees.department_id
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
- `VALUE:COALESCE:account_balances.risk_flags->account_balances.risk_flags`
- `VALUE:CONCAT_FORMAT:dormant_risk_scores.country_code,dormant_risk_scores.days_since_last_active,dormant_risk_scores.wealth_tile,user_financial_snapshot.primary_categories->account_balances.compliance_notes`

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
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/mysql-business-financial-asset-wash-procedure-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-business-financial-asset-wash-procedure-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

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
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/mysql-official-cte-dml-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-official-cte-dml-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

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

## `mysql-sample-data-enterprise-extension-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/04-queries/10-enterprise-extension-queries.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP企业级扩展分析查询
-- 覆盖: 盘点差异、调拨履约、收付款核销、会计期间、
--       工艺路线、地址与税率
-- 数据库: MySQL 8.0
-- ============================================================

USE erp_system;
```

## `mysql-sample-data-enterprise-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/sample-data-enterprise-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/sample-data-enterprise-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Input Preview**

```sql
-- relation-detector-fixture-source: PROCEDURE:sample_data.sp_post_stocktake
CREATE PROCEDURE sp_post_stocktake(
    IN p_stocktake_id BIGINT UNSIGNED,
    IN p_posted_by BIGINT UNSIGNED
)
BEGIN
    DECLARE v_warehouse_id BIGINT UNSIGNED;
```

## `mysql-sample-data-real-world-scenarios-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/04-queries/09-real-world-scenarios.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/sample-data-real-world-scenarios-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统真实业务场景SQL查询 - 第九批
-- 覆盖: Procure-to-Pay全链路、Order-to-Cash全链路、
--       产品真实利润、员工人效、库存持有成本、资金周转周期、
--       信用风险监控、批号全链路追溯、毛利瀑布、预算滚动预测、
--       供应商集中度风险、月度关账核对、需求预测准确率、
--       仓库库容利用率、提成核对、价格弹性分析
-- ============================================================
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
- `VALUE:AGGREGATE:supplier_manifests.supply_price,warehouse_inventory.default_unit_cost,order_items.quantity->order_items.estimated_cost`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

**Extractor Candidate Fingerprints**

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:AGGREGATE:supplier_manifests.supply_price,warehouse_inventory.default_unit_cost,order_items.quantity->order_items.estimated_cost`
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

## `mysql80-basic-correctness-case-01-functions-sql`

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

## `mysql80-basic-correctness-case-01-procedure-internal-flush-buffer-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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

## `mysql80-basic-correctness-case-01-procedure-proc-batch-call-generate-po-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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

## `mysql80-basic-correctness-case-01-procedure-proc-batch-generate-purchase-inbound-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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

## `mysql80-basic-correctness-case-01-procedure-proc-batch-insert-purchase-requisition-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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

## `mysql80-basic-correctness-case-01-procedure-proc-batch-mock-retail-orders-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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

## `mysql80-basic-correctness-case-01-procedure-proc-create-order-mock-retail-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/basic-correctness-case-01-procedure-proc-create-order-mock-retail-sql/expected-lineage.json` |

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

## `mysql80-basic-correctness-case-01-procedure-proc-generate-purchase-inbound-from-order-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/basic-correctness-case-01-procedure-proc-generate-purchase-inbound-from-order-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:AGGREGATE:jsh_depot_item.all_price->jsh_depot_head.change_amount`
- `VALUE:AGGREGATE:jsh_depot_item.all_price->jsh_depot_head.total_price`
- `VALUE:AGGREGATE:jsh_depot_item.tax_last_money->jsh_depot_head.discount_last_money`
- `VALUE:ARITHMETIC:jsh_depot_item.oper_number,jsh_material_extend.purchase_decimal->jsh_depot_item.all_price`
- `VALUE:ARITHMETIC:jsh_depot_item.oper_number,jsh_material_extend.purchase_decimal->jsh_depot_item.tax_last_money`
- `VALUE:ARITHMETIC:jsh_material_current_stock.current_number,jsh_depot_item.oper_number->jsh_material_current_stock.current_number`
- `VALUE:DIRECT:jsh_depot_item.another_depot_id->jsh_depot_item.another_depot_id`
- `VALUE:DIRECT:jsh_depot_item.basic_number->jsh_depot_item.basic_number`
- `VALUE:DIRECT:jsh_depot_item.delete_flag->jsh_depot_item.delete_flag`
- `VALUE:DIRECT:jsh_depot_item.id->jsh_depot_item.link_id`
- `VALUE:DIRECT:jsh_depot_item.material_extend_id->jsh_depot_item.material_extend_id`
- `VALUE:DIRECT:jsh_depot_item.material_id->jsh_depot_item.material_id`
- `VALUE:DIRECT:jsh_depot_item.material_type->jsh_depot_item.material_type`
- `VALUE:DIRECT:jsh_depot_item.material_unit->jsh_depot_item.material_unit`
- `VALUE:DIRECT:jsh_depot_item.oper_number->jsh_depot_item.oper_number`
- `VALUE:DIRECT:jsh_depot_item.purchase_unit_price->jsh_depot_item.purchase_unit_price`
- `VALUE:DIRECT:jsh_depot_item.remark->jsh_depot_item.remark`
- `VALUE:DIRECT:jsh_depot_item.sku->jsh_depot_item.sku`
- `VALUE:DIRECT:jsh_depot_item.sn_list->jsh_depot_item.sn_list`
- `VALUE:DIRECT:jsh_depot_item.tax_money->jsh_depot_item.tax_money`
- `VALUE:DIRECT:jsh_depot_item.tax_rate->jsh_depot_item.tax_rate`
- `VALUE:DIRECT:jsh_depot_item.tax_unit_price->jsh_depot_item.tax_unit_price`
- `VALUE:DIRECT:jsh_depot_item.tenant_id->jsh_depot_item.tenant_id`
- `VALUE:DIRECT:jsh_material_extend.purchase_decimal->jsh_depot_item.unit_price`
- `VALUE:FUNCTION_CALL:jsh_material.expiry_num->jsh_depot_item.expiration_date`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:jsh_depot_item.oper_number,jsh_material_extend.purchase_decimal->jsh_depot_item.all_price`
- `VALUE:ARITHMETIC:jsh_depot_item.oper_number,jsh_material_extend.purchase_decimal->jsh_depot_item.tax_last_money`
- `VALUE:ARITHMETIC:jsh_material_current_stock.current_number,jsh_depot_item.oper_number->jsh_material_current_stock.current_number`
- `VALUE:DIRECT:jsh_depot_item.another_depot_id->jsh_depot_item.another_depot_id`
- `VALUE:DIRECT:jsh_depot_item.basic_number->jsh_depot_item.basic_number`
- `VALUE:DIRECT:jsh_depot_item.delete_flag->jsh_depot_item.delete_flag`
- `VALUE:DIRECT:jsh_depot_item.id->jsh_depot_item.link_id`
- `VALUE:DIRECT:jsh_depot_item.material_extend_id->jsh_depot_item.material_extend_id`
- `VALUE:DIRECT:jsh_depot_item.material_id->jsh_depot_item.material_id`
- `VALUE:DIRECT:jsh_depot_item.material_type->jsh_depot_item.material_type`
- `VALUE:DIRECT:jsh_depot_item.material_unit->jsh_depot_item.material_unit`
- `VALUE:DIRECT:jsh_depot_item.oper_number->jsh_depot_item.oper_number`
- `VALUE:DIRECT:jsh_depot_item.purchase_unit_price->jsh_depot_item.purchase_unit_price`
- `VALUE:DIRECT:jsh_depot_item.remark->jsh_depot_item.remark`
- `VALUE:DIRECT:jsh_depot_item.sku->jsh_depot_item.sku`
- `VALUE:DIRECT:jsh_depot_item.sn_list->jsh_depot_item.sn_list`
- `VALUE:DIRECT:jsh_depot_item.tax_money->jsh_depot_item.tax_money`
- `VALUE:DIRECT:jsh_depot_item.tax_rate->jsh_depot_item.tax_rate`
- `VALUE:DIRECT:jsh_depot_item.tax_unit_price->jsh_depot_item.tax_unit_price`
- `VALUE:DIRECT:jsh_depot_item.tenant_id->jsh_depot_item.tenant_id`
- `VALUE:DIRECT:jsh_material_extend.purchase_decimal->jsh_depot_item.unit_price`
- `VALUE:FUNCTION_CALL:jsh_material.expiry_num->jsh_depot_item.expiration_date`

**Input Preview**

```sql
-- Generated from MySQL information_schema.ROUTINES procedures for basic-correctness-case-01.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_call_generate_po
BEGIN
    -- [1. 局部控制变量声明]
    DECLARE i INT DEFAULT 0;
```

## `mysql80-basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:AGGREGATE:jsh_depot_item.all_price->jsh_depot_head.total_price`
- `VALUE:AGGREGATE:jsh_depot_item.tax_last_money->jsh_depot_head.discount_last_money`
- `VALUE:ARITHMETIC:jsh_depot_item.oper_number,jsh_material_extend.purchase_decimal->jsh_depot_item.all_price`
- `VALUE:ARITHMETIC:jsh_depot_item.oper_number,jsh_material_extend.purchase_decimal->jsh_depot_item.tax_last_money`
- `VALUE:DIRECT:jsh_depot_item.basic_number->jsh_depot_item.basic_number`
- `VALUE:DIRECT:jsh_depot_item.delete_flag->jsh_depot_item.delete_flag`
- `VALUE:DIRECT:jsh_depot_item.depot_id->jsh_depot_item.depot_id`
- `VALUE:DIRECT:jsh_depot_item.id->jsh_depot_item.link_id`
- `VALUE:DIRECT:jsh_depot_item.material_extend_id->jsh_depot_item.material_extend_id`
- `VALUE:DIRECT:jsh_depot_item.material_id->jsh_depot_item.material_id`
- `VALUE:DIRECT:jsh_depot_item.material_type->jsh_depot_item.material_type`
- `VALUE:DIRECT:jsh_depot_item.material_unit->jsh_depot_item.material_unit`
- `VALUE:DIRECT:jsh_depot_item.oper_number->jsh_depot_item.oper_number`
- `VALUE:DIRECT:jsh_depot_item.purchase_unit_price->jsh_depot_item.purchase_unit_price`
- `VALUE:DIRECT:jsh_depot_item.remark->jsh_depot_item.remark`
- `VALUE:DIRECT:jsh_depot_item.sku->jsh_depot_item.sku`
- `VALUE:DIRECT:jsh_depot_item.tax_money->jsh_depot_item.tax_money`
- `VALUE:DIRECT:jsh_depot_item.tax_rate->jsh_depot_item.tax_rate`
- `VALUE:DIRECT:jsh_depot_item.tax_unit_price->jsh_depot_item.tax_unit_price`
- `VALUE:DIRECT:jsh_depot_item.tenant_id->jsh_depot_item.tenant_id`
- `VALUE:DIRECT:jsh_material_extend.purchase_decimal->jsh_depot_item.unit_price`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:jsh_depot_item.oper_number,jsh_material_extend.purchase_decimal->jsh_depot_item.all_price`
- `VALUE:ARITHMETIC:jsh_depot_item.oper_number,jsh_material_extend.purchase_decimal->jsh_depot_item.tax_last_money`
- `VALUE:DIRECT:jsh_depot_item.basic_number->jsh_depot_item.basic_number`
- `VALUE:DIRECT:jsh_depot_item.delete_flag->jsh_depot_item.delete_flag`
- `VALUE:DIRECT:jsh_depot_item.depot_id->jsh_depot_item.depot_id`
- `VALUE:DIRECT:jsh_depot_item.id->jsh_depot_item.link_id`
- `VALUE:DIRECT:jsh_depot_item.material_extend_id->jsh_depot_item.material_extend_id`
- `VALUE:DIRECT:jsh_depot_item.material_id->jsh_depot_item.material_id`
- `VALUE:DIRECT:jsh_depot_item.material_type->jsh_depot_item.material_type`
- `VALUE:DIRECT:jsh_depot_item.material_unit->jsh_depot_item.material_unit`
- `VALUE:DIRECT:jsh_depot_item.oper_number->jsh_depot_item.oper_number`
- `VALUE:DIRECT:jsh_depot_item.purchase_unit_price->jsh_depot_item.purchase_unit_price`
- `VALUE:DIRECT:jsh_depot_item.remark->jsh_depot_item.remark`
- `VALUE:DIRECT:jsh_depot_item.sku->jsh_depot_item.sku`
- `VALUE:DIRECT:jsh_depot_item.tax_money->jsh_depot_item.tax_money`
- `VALUE:DIRECT:jsh_depot_item.tax_rate->jsh_depot_item.tax_rate`
- `VALUE:DIRECT:jsh_depot_item.tax_unit_price->jsh_depot_item.tax_unit_price`
- `VALUE:DIRECT:jsh_depot_item.tenant_id->jsh_depot_item.tenant_id`
- `VALUE:DIRECT:jsh_material_extend.purchase_decimal->jsh_depot_item.unit_price`

**Input Preview**

```sql
-- Generated from MySQL information_schema.ROUTINES procedures for basic-correctness-case-01.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_call_generate_po
BEGIN
    -- [1. 局部控制变量声明]
    DECLARE i INT DEFAULT 0;
```

## `mysql80-basic-correctness-case-01-procedure-proc-init-yearly-weights-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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

## `mysql80-basic-correctness-case-01-procedure-proc-insert-purchase-requisition-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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

## `mysql80-basic-correctness-case-01-procedure-proc-refresh-org-pdf-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/basic-correctness-case-01-procedure-proc-refresh-org-pdf-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:jsh_organization.org_abr->jsh_temp_org_pdf.weight`
- `CONTROL:CASE_WHEN:jsh_organization.org_no->jsh_temp_org_pdf.weight`
- `VALUE:CUMULATIVE:jsh_temp_org_pdf.weight->jsh_temp_org_pdf.cdf_end`
- `VALUE:DIRECT:jsh_organization.id->jsh_temp_org_pdf.org_id`
- `VALUE:DIRECT:jsh_organization.org_abr->jsh_temp_org_pdf.remark`

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

## `mysql80-basic-correctness-case-01-procedure-proc-simulate-yearly-sales-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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

## `mysql80-basic-correctness-case-01-procedure-proc-worker-daily-distribution-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/basic-correctness-case-01-procedure-proc-worker-daily-distribution-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:CUMULATIVE:jsh_temp_hour_pdf.hour_val,jsh_temp_hour_pdf.weight->jsh_temp_mock_plan.mock_timestamp_str`
- `VALUE:DIRECT:jsh_orga_user_rel.user_id,jsh_orga_user_rel.orga_id,jsh_temp_org_pdf.org_id,jsh_orga_user_rel.delete_flag->jsh_temp_mock_plan.user_id`

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

## `mysql80-basic-correctness-case-01-procedure-sp-fill-biz-bill-item-fact-new-with-progress-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/basic-correctness-case-01-procedure-sp-fill-biz-bill-item-fact-new-with-progress-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:jsh_depot_head.sub_type,jsh_depot_head.link_apply->biz_bill_item_fact_new.purchaseApplyLinkNo`
- `CONTROL:CASE_WHEN:jsh_depot_head.sub_type,jsh_depot_head.link_number->biz_bill_item_fact_new.purchaseOrderLinkNo`
- `CONTROL:CASE_WHEN:jsh_depot_head.sub_type,jsh_depot_item.another_depot_id->biz_bill_item_fact_new.inWarehouseId`
- `CONTROL:CASE_WHEN:jsh_depot_head.sub_type,jsh_depot_item.depot_id->biz_bill_item_fact_new.outWarehouseId`
- `CONTROL:CASE_WHEN:jsh_depot_head.type,jsh_depot_head.sub_type->biz_bill_item_fact_new.inventoryDirection`
- `CONTROL:CASE_WHEN:jsh_depot_head.type,jsh_depot_head.sub_type->biz_bill_item_fact_new.salesDirection`
- `CONTROL:CASE_WHEN:jsh_supplier.type,jsh_depot_head.sub_type,jsh_depot_head.organ_id->biz_bill_item_fact_new.customerId`
- `CONTROL:CASE_WHEN:jsh_supplier.type,jsh_depot_head.sub_type,jsh_depot_head.organ_id->biz_bill_item_fact_new.memberId`
- `CONTROL:CASE_WHEN:jsh_supplier.type,jsh_depot_head.sub_type,jsh_depot_head.organ_id->biz_bill_item_fact_new.supplierId`
- `VALUE:AGGREGATE:jsh_orga_user_rel.orga_id->biz_bill_item_fact_new.storeId`
- `VALUE:COALESCE:jsh_depot_item.tax_last_money,jsh_depot_item.all_price->biz_bill_item_fact_new.amount`
- `VALUE:DIRECT:jsh_depot_head.creator->biz_bill_item_fact_new.creator`
- `VALUE:DIRECT:jsh_depot_head.id->biz_bill_item_fact_new.sourceOrderId`
- `VALUE:DIRECT:jsh_depot_head.number->biz_bill_item_fact_new.sourceOrderNo`
- `VALUE:DIRECT:jsh_depot_head.oper_time->biz_bill_item_fact_new.businessDate`
- `VALUE:DIRECT:jsh_depot_head.sub_type->biz_bill_item_fact_new.sourceSubType`
- `VALUE:DIRECT:jsh_depot_head.type->biz_bill_item_fact_new.sourceType`
- `VALUE:DIRECT:jsh_depot_item.depot_id->biz_bill_item_fact_new.warehouseId`
- `VALUE:DIRECT:jsh_depot_item.id->biz_bill_item_fact_new.sourceOrderItemId`
- `VALUE:DIRECT:jsh_depot_item.material_id->biz_bill_item_fact_new.productId`
- `VALUE:DIRECT:jsh_depot_item.oper_number->biz_bill_item_fact_new.quantity`
- `VALUE:DIRECT:jsh_depot_item.tenant_id->biz_bill_item_fact_new.tenantId`

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

## `mysql80-basic-correctness-case-01-procedure-sp-sync-retail-out-fact-batch-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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

## `mysql80-mysql-basic-correctness-case-01-sql`

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

## `mysql80-mysql-basic-correctness-case-02-sql`

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

## `mysql80-mysql-basic-correctness-case-03-sql`

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

## `mysql80-mysql-basic-correctness-case-04-sql`

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

## `mysql80-mysql-business-cross-border-reconciliation-procedure-comma-subquery-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-business-cross-border-reconciliation-procedure-comma-subquery-sql/input.sql` |
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

## `mysql80-mysql-business-cross-border-reconciliation-procedure-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-business-cross-border-reconciliation-procedure-sql/input.sql` |
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

## `mysql80-mysql-business-financial-asset-wash-procedure-comma-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-business-financial-asset-wash-procedure-comma-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql-business-financial-asset-wash-procedure-comma-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:account_balances.max_credit_limit->account_balances.adjusted_limit`
- `VALUE:COALESCE:account_balances.risk_flags->account_balances.risk_flags`
- `VALUE:CONCAT_FORMAT:dormant_risk_scores.country_code,dormant_risk_scores.days_since_last_active,dormant_risk_scores.wealth_tile,user_financial_snapshot.primary_categories->account_balances.compliance_notes`

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

## `mysql80-mysql-business-financial-asset-wash-procedure-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-business-financial-asset-wash-procedure-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql-business-financial-asset-wash-procedure-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:account_balances.max_credit_limit->account_balances.adjusted_limit`
- `VALUE:COALESCE:account_balances.risk_flags->account_balances.risk_flags`
- `VALUE:CONCAT_FORMAT:dormant_risk_scores.country_code,dormant_risk_scores.days_since_last_active,dormant_risk_scores.wealth_tile,user_financial_snapshot.primary_categories->account_balances.compliance_notes`

**Extractor Candidate Fingerprints**

- None

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

## `mysql80-mysql-commerce-promotion-update-comma-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-commerce-promotion-update-comma-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql-commerce-promotion-update-comma-join-sql/expected-lineage.json` |

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

## `mysql80-mysql-commerce-promotion-update-explicit-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-commerce-promotion-update-explicit-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql-commerce-promotion-update-explicit-join-sql/expected-lineage.json` |

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

## `mysql80-mysql-invalid-orders-delete-comma-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-invalid-orders-delete-comma-join-sql/input.sql` |
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

## `mysql80-mysql-invalid-orders-delete-explicit-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-invalid-orders-delete-explicit-join-sql/input.sql` |
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

## `mysql80-mysql-official-cte-dml-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-official-cte-dml-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql-official-cte-dml-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:accounts.id->orders.audit_account_id`

**Extractor Candidate Fingerprints**

- None

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

## `mysql80-mysql-official-cte-nested-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-official-cte-nested-sql/input.sql` |
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

## `mysql80-mysql-official-derived-subquery-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-official-derived-subquery-sql/input.sql` |
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

## `mysql80-mysql-official-join-edge-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-official-join-edge-sql/input.sql` |
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

## `mysql80-mysql-official-join-matrix-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-official-join-matrix-sql/input.sql` |
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

## `mysql80-mysql-official-lateral-derived-edge-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-official-lateral-derived-edge-sql/input.sql` |
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

## `mysql80-mysql-official-recursive-cte-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-official-recursive-cte-sql/input.sql` |
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

## `mysql80-mysql-official-subquery-edge-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-official-subquery-edge-sql/input.sql` |
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

## `mysql80-mysql-orphan-reviews-delete-left-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-orphan-reviews-delete-left-join-sql/input.sql` |
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

## `mysql80-mysql-orphan-reviews-delete-not-exists-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-orphan-reviews-delete-not-exists-sql/input.sql` |
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

## `mysql80-mysql-sql-cte-lateral`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/sql-cte-lateral/input.sql` |
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

## `mysql80-mysql-sql-delete-left-join`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/sql-delete-left-join/input.sql` |
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

## `mysql80-mysql-sql-multi-table-update`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/sql-multi-table-update/input.sql` |
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

## `mysql80-mysql-sql-system-log-noise`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `NATIVE_LOG` |
| Input | `test-fixtures/correctness/mysql/v8_0/sql-system-log-noise/input.sql` |
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

## `mysql80-mysql-supply-chain-update-comma-and-subquery-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-supply-chain-update-comma-and-subquery-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql-supply-chain-update-comma-and-subquery-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:AGGREGATE:supplier_manifests.supply_price,warehouse_inventory.product_id,supplier_manifests.product_id,warehouse_inventory.primary_supplier_id,supplier_manifests.supplier_id,supplier_manifests.manifest_id,warehouse_inventory.default_unit_cost,order_items.quantity->order_items.estimated_cost`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

**Extractor Candidate Fingerprints**

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:AGGREGATE:supplier_manifests.supply_price,warehouse_inventory.default_unit_cost,order_items.quantity->order_items.estimated_cost`
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

## `mysql80-mysql-supply-chain-update-explicit-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-supply-chain-update-explicit-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql-supply-chain-update-explicit-join-sql/expected-lineage.json` |

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

## `mysql80-mysql-user-spending-comma-join-update-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-user-spending-comma-join-update-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql-user-spending-comma-join-update-sql/expected-lineage.json` |

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

## `mysql80-mysql-user-spending-left-join-update-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql-user-spending-left-join-update-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql-user-spending-left-join-update-sql/expected-lineage.json` |

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

## `mysql80-sample-data-enterprise-extension-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/04-queries/10-enterprise-extension-queries.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP企业级扩展分析查询
-- 覆盖: 盘点差异、调拨履约、收付款核销、会计期间、
--       工艺路线、地址与税率
-- 数据库: MySQL 8.0
-- ============================================================

USE erp_system;
```

## `mysql80-sample-data-enterprise-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/sample-data-enterprise-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/sample-data-enterprise-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Input Preview**

```sql
-- relation-detector-fixture-source: PROCEDURE:sample_data.sp_post_stocktake
CREATE PROCEDURE sp_post_stocktake(
    IN p_stocktake_id BIGINT UNSIGNED,
    IN p_posted_by BIGINT UNSIGNED
)
BEGIN
    DECLARE v_warehouse_id BIGINT UNSIGNED;
```

## `mysql80-sample-data-real-world-scenarios-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/04-queries/09-real-world-scenarios.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/sample-data-real-world-scenarios-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统真实业务场景SQL查询 - 第九批
-- 覆盖: Procure-to-Pay全链路、Order-to-Cash全链路、
--       产品真实利润、员工人效、库存持有成本、资金周转周期、
--       信用风险监控、批号全链路追溯、毛利瀑布、预算滚动预测、
--       供应商集中度风险、月度关账核对、需求预测准确率、
--       仓库库容利用率、提成核对、价格弹性分析
-- ============================================================
```

## `mysql80sample-data-full-01-schema-02-indexes-and-views-views-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `VIEW` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-01-schema-02-indexes-and-views-views-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-01-schema-02-indexes-and-views-views-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: VIEW:erp_system.v_employee_full
SELECT
    e.id,
    e.employee_no,
    e.name,
    e.gender,
    e.phone,
    e.email,
```

## `mysql80sample-data-full-01-schema-03-triggers-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `TRIGGER` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-01-schema-03-triggers-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-01-schema-03-triggers-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:inventory.quantity,sales_order_items.quantity->inventory.quantity`
- `VALUE:COALESCE:sales_order_items.product_id,sales_order_items.batch_id,sales_order_items.quantity->inventory_transactions.after_qty`
- `VALUE:COALESCE:sales_order_items.product_id,sales_order_items.batch_id->inventory_transactions.before_qty`
- `VALUE:DIRECT:sales_order_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:sales_order_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:sales_order_items.quantity->inventory_transactions.quantity_change`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: TRIGGER:erp_system.trg_audit_employee_insert
CREATE TRIGGER trg_audit_employee_insert
AFTER INSERT ON employees
FOR EACH ROW
BEGIN
    INSERT INTO audit_log (employee_id, action, target_type, target_id, new_value)
    VALUES (NEW.id, 'INSERT', 'employee', NEW.id,
            JSON_OBJECT('name', NEW.name, 'employee_no', NEW.employee_no,
```

## `mysql80sample-data-full-02-procedures-01-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-01-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-01-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:cashier_journals.journal_type,cashier_journals.amount->reconciliation_items.credit_amount`
- `CONTROL:CASE_WHEN:cashier_journals.journal_type,cashier_journals.amount->reconciliation_items.debit_amount`
- `VALUE:COALESCE:cashier_journals.journal_type,cashier_journals.counterparty,cashier_journals.remark->reconciliation_items.description`
- `VALUE:DIRECT:cashier_journals.id->reconciliation_items.journal_id`
- `VALUE:DIRECT:cashier_journals.journal_date->reconciliation_items.transaction_date`

**Extractor Candidate Fingerprints**

- `CONTROL:CASE_WHEN:cashier_journals.journal_type,cashier_journals.amount->reconciliation_items.credit_amount`
- `CONTROL:CASE_WHEN:cashier_journals.journal_type,cashier_journals.amount->reconciliation_items.debit_amount`
- `VALUE:COALESCE:cashier_journals.journal_type,cashier_journals.counterparty,cashier_journals.remark->reconciliation_items.description`
- `VALUE:DIRECT:cashier_journals.id->reconciliation_items.journal_id`
- `VALUE:DIRECT:cashier_journals.journal_date->reconciliation_items.transaction_date`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.sp_create_department
CREATE PROCEDURE sp_create_department(
    IN p_parent_id BIGINT UNSIGNED,
    IN p_name VARCHAR(100),
    IN p_code VARCHAR(20),
    IN p_budget DECIMAL(18,2),
    IN p_headcount_plan INT UNSIGNED
)
```

## `mysql80sample-data-full-02-procedures-02-procedures-supplement-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-02-procedures-supplement-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-02-procedures-supplement-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.sp_transfer_inventory
CREATE PROCEDURE sp_transfer_inventory(
    IN p_product_id BIGINT UNSIGNED,
    IN p_batch_id BIGINT UNSIGNED,
    IN p_from_warehouse_id BIGINT UNSIGNED,
    IN p_to_warehouse_id BIGINT UNSIGNED,
    IN p_quantity INT,
    IN p_operator_id BIGINT UNSIGNED,
```

## `mysql80sample-data-full-02-procedures-03-functions-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-03-functions-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-03-functions-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.fn_employee_full_name
CREATE FUNCTION fn_employee_full_name(p_employee_id BIGINT UNSIGNED)
RETURNS VARCHAR(100)
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_result VARCHAR(100);
    SELECT CONCAT(employee_no, ' - ', name) INTO v_result
```

## `mysql80sample-data-full-02-procedures-04-procedures-supplement-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-04-procedures-supplement-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-04-procedures-supplement-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:sales_commissions.bonus->sales_commissions.bonus`
- `VALUE:ARITHMETIC:sales_commissions.commission_amount,sales_commissions.base_amount->sales_commissions.commission_amount`
- `VALUE:COALESCE:commission_rules.bonus->sales_commissions.bonus`
- `VALUE:COALESCE:commission_rules.commission_rate->sales_commissions.commission_rate`
- `VALUE:COALESCE:sales_order_items.amount,commission_rules.commission_rate->sales_commissions.commission_amount`
- `VALUE:DIRECT:sales_order_items.amount->sales_commissions.base_amount`
- `VALUE:DIRECT:sales_order_items.id->sales_commissions.order_item_id`
- `VALUE:DIRECT:sales_orders.id->sales_commissions.order_id`
- `VALUE:DIRECT:sales_orders.salesperson_id->sales_commissions.employee_id`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:sales_commissions.bonus->sales_commissions.bonus`
- `VALUE:ARITHMETIC:sales_commissions.commission_amount,sales_commissions.base_amount->sales_commissions.commission_amount`
- `VALUE:COALESCE:sales_order_items.amount->sales_commissions.commission_amount`
- `VALUE:DIRECT:sales_order_items.amount->sales_commissions.base_amount`
- `VALUE:DIRECT:sales_order_items.id->sales_commissions.order_item_id`
- `VALUE:DIRECT:sales_orders.id->sales_commissions.order_id`
- `VALUE:DIRECT:sales_orders.salesperson_id->sales_commissions.employee_id`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.sp_create_shipment
CREATE PROCEDURE sp_create_shipment(
    IN p_order_id BIGINT UNSIGNED,
    IN p_carrier VARCHAR(100),
    IN p_shipping_method ENUM('express','truck','air','sea','self_pickup'),
    IN p_shipping_fee DECIMAL(12,2),
    IN p_to_address VARCHAR(300),
    IN p_receiver_name VARCHAR(50),
```

## `mysql80sample-data-full-02-procedures-05-third-batch-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-05-third-batch-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-05-third-batch-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:sales_orders.customer_id->ar_aging_snapshots.customer_id`
- `VALUE:DIRECT:sales_orders.id->ar_aging_snapshots.order_id`
- `VALUE:DIRECT:sales_orders.paid_amount->ar_aging_snapshots.paid_amount`
- `VALUE:DIRECT:sales_orders.total_amount->ar_aging_snapshots.invoice_amount`
- `VALUE:FUNCTION_CALL:sales_orders.order_date,customers.credit_days->ar_aging_snapshots.due_date`

**Extractor Candidate Fingerprints**

- `VALUE:DIRECT:sales_orders.customer_id->ar_aging_snapshots.customer_id`
- `VALUE:DIRECT:sales_orders.id->ar_aging_snapshots.order_id`
- `VALUE:DIRECT:sales_orders.paid_amount->ar_aging_snapshots.paid_amount`
- `VALUE:DIRECT:sales_orders.total_amount->ar_aging_snapshots.invoice_amount`
- `VALUE:FUNCTION_CALL:sales_orders.order_date,customers.credit_days->ar_aging_snapshots.due_date`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.sp_generate_ar_aging
CREATE PROCEDURE sp_generate_ar_aging()
BEGIN
    -- 清理当天快照
    DELETE FROM ar_aging_snapshots WHERE snapshot_date = CURDATE();

    INSERT INTO ar_aging_snapshots (snapshot_date, customer_id, order_id,
        invoice_amount, paid_amount, due_date)
```

## `mysql80sample-data-full-02-procedures-06-third-batch-functions-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-06-third-batch-functions-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-06-third-batch-functions-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.fn_get_customer_clv
CREATE FUNCTION fn_get_customer_clv(
    p_customer_id BIGINT UNSIGNED
)
RETURNS DECIMAL(18,2)
DETERMINISTIC
READS SQL DATA
BEGIN
```

## `mysql80sample-data-full-02-procedures-07-store-customer-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-07-store-customer-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-07-store-customer-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.sp_customer_store_purchase_history
CREATE PROCEDURE sp_customer_store_purchase_history(
    IN p_customer_id BIGINT UNSIGNED,
    IN p_start_date DATE,
    IN p_end_date DATE
)
BEGIN
    SELECT
```

## `mysql80sample-data-full-02-procedures-08-batch-expiry-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-08-batch-expiry-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-08-batch-expiry-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.sp_batch_expiry_tracking
CREATE PROCEDURE sp_batch_expiry_tracking(
    IN p_role ENUM('store_manager','employee','senior_mgmt','all'),
    IN p_user_id BIGINT UNSIGNED,
    IN p_warehouse_id BIGINT UNSIGNED,
    IN p_expiry_days INT
)
BEGIN
```

## `mysql80sample-data-full-02-procedures-09-return-refund-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-09-return-refund-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-09-return-refund-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.sp_approve_sales_return
CREATE PROCEDURE sp_approve_sales_return(
    IN p_return_id BIGINT UNSIGNED,
    IN p_approved BOOLEAN,
    IN p_approver_id BIGINT UNSIGNED,
    IN p_approval_comment VARCHAR(500)
)
BEGIN
```

## `mysql80sample-data-full-02-procedures-10-supplier-geo-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-10-supplier-geo-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-10-supplier-geo-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:inspection_reports.inspection_result->supplier_products.quality_score`
- `VALUE:AGGREGATE:purchase_order_items.received_qty,purchase_order_items.order_id,purchase_orders.id,purchase_order_items.product_id,supplier_products.product_id,purchase_orders.supplier_id,supplier_products.supplier_id->supplier_products.total_order_qty`
- `VALUE:AGGREGATE:purchase_orders.id,purchase_order_items.order_id,purchase_order_items.product_id,supplier_products.product_id,purchase_orders.supplier_id,supplier_products.supplier_id->supplier_products.total_order_count`
- `VALUE:AGGREGATE:purchase_orders.order_date,purchase_order_items.order_id,purchase_orders.id,purchase_order_items.product_id,supplier_products.product_id,purchase_orders.supplier_id,supplier_products.supplier_id->supplier_products.last_order_date`
- `VALUE:AGGREGATE:purchase_return_items.return_qty,purchase_order_items.received_qty,purchase_order_items.order_id,purchase_orders.id,purchase_order_items.product_id,supplier_products.product_id,purchase_orders.supplier_id,supplier_products.supplier_id,purchase_orders.order_date,purchase_returns.id,purchase_return_items.return_id,purchase_returns.supplier_id,purchase_return_items.product_id,purchase_returns.return_date->supplier_products.return_rate`

**Extractor Candidate Fingerprints**

- `CONTROL:CASE_WHEN:inspection_reports.inspection_result->supplier_products.quality_score`
- `VALUE:AGGREGATE:purchase_order_items.received_qty->supplier_products.total_order_qty`
- `VALUE:AGGREGATE:purchase_orders.id->supplier_products.total_order_count`
- `VALUE:AGGREGATE:purchase_orders.order_date->supplier_products.last_order_date`
- `VALUE:AGGREGATE:purchase_return_items.return_qty,purchase_order_items.received_qty->supplier_products.return_rate`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.fn_haversine_distance
CREATE FUNCTION fn_haversine_distance(
    p_lat1 DECIMAL(10,7),
    p_lon1 DECIMAL(10,7),
    p_lat2 DECIMAL(10,7),
    p_lon2 DECIMAL(10,7)
)
RETURNS DECIMAL(10,2)
```

## `mysql80sample-data-full-02-procedures-11-common-system-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-11-common-system-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-11-common-system-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.sp_poor_attendance_report
CREATE PROCEDURE sp_poor_attendance_report(
    IN p_year_month VARCHAR(7),
    IN p_department_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_start_date DATE;
    DECLARE v_end_date DATE;
```

## `mysql80sample-data-full-02-procedures-12-enterprise-extension-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-12-enterprise-extension-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-12-enterprise-extension-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.sp_post_stocktake
CREATE PROCEDURE sp_post_stocktake(
    IN p_stocktake_id BIGINT UNSIGNED,
    IN p_posted_by BIGINT UNSIGNED
)
BEGIN
    DECLARE v_warehouse_id BIGINT UNSIGNED;
```

## `mysql80sample-data-full-03-data-01-master-data-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/03-data/01-master-data.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-03-data-01-master-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统测试数据生成
-- 关系说明:
--   departments -> positions -> employees (1:N:N)
--   employees.manager_id 自引用形成汇报链
--   product_categories -> products -> product_batches (1:N:N)
--   suppliers -> supplier_products -> products (N:M)
--   warehouses -> inventory (1:N, 通过product_id+batch_id+warehouse_id唯一)
```

## `mysql80sample-data-full-03-data-02-supplementary-data-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/03-data/02-supplementary-data.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-03-data-02-supplementary-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:seq.hour,sales_orders.customer_id->shipping_tracks.location`
- `CONTROL:CASE_WHEN:seq.hour->shipping_tracks.status_desc`
- `VALUE:AGGREGATE:fixed_assets.id->fixed_assets.accumulated_depreciation`
- `VALUE:ARITHMETIC:boms.quantity,work_orders.completed_quantity->work_order_materials.actual_consumed`
- `VALUE:ARITHMETIC:boms.quantity,work_orders.completed_quantity->work_order_materials.issued_qty`
- `VALUE:ARITHMETIC:boms.quantity,work_orders.planned_quantity->work_order_materials.required_qty`
- `VALUE:ARITHMETIC:fixed_assets.monthly_depreciation,m.month->depreciation_log.after_accumulated`
- `VALUE:ARITHMETIC:fixed_assets.monthly_depreciation,m.month->depreciation_log.before_accumulated`
- `VALUE:ARITHMETIC:fixed_assets.purchase_amount,fixed_assets.monthly_depreciation,m.month->depreciation_log.after_net_value`
- `VALUE:ARITHMETIC:fixed_assets.purchase_amount,fixed_assets.monthly_depreciation,m.month->depreciation_log.before_net_value`
- `VALUE:ARITHMETIC:purchase_orders.order_date->invoices.invoice_date`
- `VALUE:ARITHMETIC:purchase_orders.order_date->invoices.verified_at`
- `VALUE:ARITHMETIC:purchase_orders.total_amount->invoices.tax_amount`
- `VALUE:ARITHMETIC:sales_orders.order_date->shipments.shipped_at`
- `VALUE:ARITHMETIC:sales_orders.status,sales_orders.order_date->shipments.actual_delivery_date`
- `VALUE:ARITHMETIC:sales_orders.status,sales_orders.order_date->shipments.delivered_at`
- `VALUE:ARITHMETIC:seq.num->service_tickets.resolved_at`
- `VALUE:ARITHMETIC:seq.num->service_tickets.satisfaction_score`
- `VALUE:CONCAT_FORMAT:m.month->depreciation_log.depreciation_date`
- `VALUE:CONCAT_FORMAT:purchase_orders.order_date,purchase_orders.id->invoices.invoice_no`
- `VALUE:CONCAT_FORMAT:sales_orders.order_date,sales_orders.id->shipments.shipment_no`
- `VALUE:CONCAT_FORMAT:sales_orders.order_date,sales_orders.id->shipments.tracking_no`
- `VALUE:CONCAT_FORMAT:seq.num->service_tickets.ticket_no`
- `VALUE:CONCAT_FORMAT:seq.num->work_orders.order_no`
- `VALUE:DIRECT:boms.child_product_id->work_order_materials.product_id`
- `VALUE:DIRECT:boms.id->work_orders.bom_id`
- `VALUE:DIRECT:boms.parent_product_id->work_orders.product_id`
- `VALUE:DIRECT:boms.unit->work_order_materials.unit`
- `VALUE:DIRECT:fixed_assets.id->depreciation_log.asset_id`
- `VALUE:DIRECT:fixed_assets.monthly_depreciation->depreciation_log.depreciation_amount`
- `VALUE:DIRECT:purchase_orders.status->invoices.status`
- `VALUE:DIRECT:purchase_orders.supplier_id->invoices.supplier_id`
- `VALUE:DIRECT:purchase_orders.total_amount->invoices.total_amount`
- `VALUE:DIRECT:sales_orders.customer_id->shipments.receiver_name`
- `VALUE:DIRECT:sales_orders.customer_id->shipments.receiver_phone`
- `VALUE:DIRECT:sales_orders.customer_id->shipments.to_address`
- `VALUE:DIRECT:sales_orders.id->shipments.order_id`
- `VALUE:DIRECT:sales_orders.status->shipments.status`
- `VALUE:DIRECT:sales_orders.warehouse_id->shipments.warehouse_id`
- `VALUE:DIRECT:seq.num->service_tickets.resolution`
- `VALUE:DIRECT:seq.num->service_tickets.status`
- `VALUE:DIRECT:seq.num->work_orders.status`
- `VALUE:DIRECT:shipments.id->shipping_tracks.shipment_id`
- `VALUE:DIRECT:work_orders.id->work_order_materials.work_order_id`
- `VALUE:DIRECT:work_orders.status->work_order_materials.status`
- `VALUE:FUNCTION_CALL:purchase_orders.order_date->invoices.due_date`
- `VALUE:FUNCTION_CALL:sales_orders.order_date->shipments.estimated_delivery_date`
- `VALUE:FUNCTION_CALL:seq.num->work_orders.completed_date`
- `VALUE:FUNCTION_CALL:seq.num->work_orders.due_date`
- `VALUE:FUNCTION_CALL:seq.num->work_orders.start_date`
- `VALUE:FUNCTION_CALL:shipments.shipped_at,seq.hour->shipping_tracks.track_time`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:fixed_assets.monthly_depreciation->depreciation_log.after_accumulated`
- `VALUE:ARITHMETIC:fixed_assets.monthly_depreciation->depreciation_log.before_accumulated`
- `VALUE:ARITHMETIC:fixed_assets.purchase_amount,fixed_assets.monthly_depreciation->depreciation_log.after_net_value`
- `VALUE:ARITHMETIC:fixed_assets.purchase_amount,fixed_assets.monthly_depreciation->depreciation_log.before_net_value`
- `VALUE:DIRECT:fixed_assets.id->depreciation_log.asset_id`
- `VALUE:DIRECT:fixed_assets.monthly_depreciation->depreciation_log.depreciation_amount`
- `VALUE:DIRECT:shipments.id->shipping_tracks.shipment_id`
- `VALUE:FUNCTION_CALL:shipments.shipped_at->shipping_tracks.track_time`

**Input Preview**

```sql
-- ============================================================
-- ERP系统补充数据: 发货物流、促销、提成规则、发票、
--   固定资产、BOM、工单、客服工单
-- ============================================================

USE erp_system;

-- ============================================================
```

## `mysql80sample-data-full-03-data-03-third-batch-data-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/03-data/03-third-batch-data.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-03-data-03-third-batch-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:contracts.start_date,seq.num->contract_milestones.planned_date`
- `VALUE:ARITHMETIC:contracts.total_amount,seq.num->contract_milestones.amount`
- `VALUE:ARITHMETIC:products.retail_price->price_change_logs.old_price`
- `VALUE:COALESCE:employees.manager_id->performance_reviews.reviewer_id`
- `VALUE:COALESCE:projects.start_date,projects.actual_end_date->project_costs.cost_date`
- `VALUE:CONCAT_FORMAT:employees.id->performance_reviews.review_no`
- `VALUE:CONCAT_FORMAT:products.sku,seq.num->serial_numbers.serial_no`
- `VALUE:CONCAT_FORMAT:projects.name->project_costs.description`
- `VALUE:CONCAT_FORMAT:seq.num->contracts.contract_no`
- `VALUE:CONCAT_FORMAT:seq.num->inspection_reports.report_no`
- `VALUE:DIRECT:contracts.id->contract_milestones.contract_id`
- `VALUE:DIRECT:employees.id->performance_reviews.employee_id`
- `VALUE:DIRECT:products.id->price_change_logs.product_id`
- `VALUE:DIRECT:products.id->serial_numbers.batch_id`
- `VALUE:DIRECT:products.id->serial_numbers.product_id`
- `VALUE:DIRECT:products.retail_price->price_change_logs.new_price`
- `VALUE:DIRECT:projects.id->project_costs.project_id`
- `VALUE:DIRECT:purchase_orders.id->ap_aging_snapshots.order_id`
- `VALUE:DIRECT:purchase_orders.paid_amount->ap_aging_snapshots.paid_amount`
- `VALUE:DIRECT:purchase_orders.supplier_id->ap_aging_snapshots.supplier_id`
- `VALUE:DIRECT:purchase_orders.total_amount->ap_aging_snapshots.invoice_amount`
- `VALUE:DIRECT:sales_orders.customer_id->ar_aging_snapshots.customer_id`
- `VALUE:DIRECT:sales_orders.id->ar_aging_snapshots.order_id`
- `VALUE:DIRECT:sales_orders.paid_amount->ar_aging_snapshots.paid_amount`
- `VALUE:DIRECT:sales_orders.total_amount->ar_aging_snapshots.invoice_amount`
- `VALUE:DIRECT:seq.num->contract_milestones.status`
- `VALUE:DIRECT:seq.num->contracts.status`
- `VALUE:FUNCTION_CALL:purchase_orders.order_date->ap_aging_snapshots.due_date`
- `VALUE:FUNCTION_CALL:sales_orders.order_date,customers.credit_days->ar_aging_snapshots.due_date`
- `VALUE:FUNCTION_CALL:seq.num->contract_milestones.milestone_name`
- `VALUE:FUNCTION_CALL:seq.num->contract_milestones.milestone_type`
- `VALUE:FUNCTION_CALL:seq.num->contracts.end_date`
- `VALUE:FUNCTION_CALL:seq.num->contracts.signed_date`
- `VALUE:FUNCTION_CALL:seq.num->contracts.start_date`
- `VALUE:FUNCTION_CALL:seq.num->exchange_rates.rate_date`
- `VALUE:FUNCTION_CALL:seq.num->inspection_reports.inspection_date`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:products.retail_price->price_change_logs.old_price`
- `VALUE:COALESCE:employees.manager_id->performance_reviews.reviewer_id`
- `VALUE:COALESCE:projects.start_date,projects.actual_end_date->project_costs.cost_date`
- `VALUE:CONCAT_FORMAT:employees.id->performance_reviews.review_no`
- `VALUE:CONCAT_FORMAT:projects.name->project_costs.description`
- `VALUE:DIRECT:employees.id->performance_reviews.employee_id`
- `VALUE:DIRECT:products.id->price_change_logs.product_id`
- `VALUE:DIRECT:products.retail_price->price_change_logs.new_price`
- `VALUE:DIRECT:projects.id->project_costs.project_id`
- `VALUE:DIRECT:purchase_orders.id->ap_aging_snapshots.order_id`
- `VALUE:DIRECT:purchase_orders.paid_amount->ap_aging_snapshots.paid_amount`
- `VALUE:DIRECT:purchase_orders.supplier_id->ap_aging_snapshots.supplier_id`
- `VALUE:DIRECT:purchase_orders.total_amount->ap_aging_snapshots.invoice_amount`
- `VALUE:DIRECT:sales_orders.customer_id->ar_aging_snapshots.customer_id`
- `VALUE:DIRECT:sales_orders.id->ar_aging_snapshots.order_id`
- `VALUE:DIRECT:sales_orders.paid_amount->ar_aging_snapshots.paid_amount`
- `VALUE:DIRECT:sales_orders.total_amount->ar_aging_snapshots.invoice_amount`
- `VALUE:FUNCTION_CALL:purchase_orders.order_date->ap_aging_snapshots.due_date`
- `VALUE:FUNCTION_CALL:sales_orders.order_date,customers.credit_days->ar_aging_snapshots.due_date`

**Input Preview**

```sql
-- ============================================================
-- 第三批补充数据: 合同、汇率、审批流、KPI、质检标准、项目、序列号、寄售
-- ============================================================

USE erp_system;

-- 合同数据
INSERT INTO contracts (contract_no, contract_type, party_type, party_id, subject,
```

## `mysql80sample-data-full-03-data-04-return-damage-data-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/03-data/04-return-damage-data.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-03-data-04-return-damage-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:purchase_orders.total_amount,seq.num->purchase_returns.refund_received`
- `VALUE:ARITHMETIC:purchase_orders.total_amount,seq.num->purchase_returns.total_amount`
- `VALUE:ARITHMETIC:seq.num->damage_reports.approved_at`
- `VALUE:ARITHMETIC:seq.num->damage_reports.approved_by`
- `VALUE:ARITHMETIC:seq.num->damage_reports.executed_at`
- `VALUE:ARITHMETIC:seq.num->damage_reports.executed_by`
- `VALUE:ARITHMETIC:seq.num->purchase_returns.approved_at`
- `VALUE:CONCAT_FORMAT:seq.num->damage_reports.report_no`
- `VALUE:CONCAT_FORMAT:seq.num->purchase_returns.return_no`
- `VALUE:DIRECT:damage_reports.id->damage_report_items.report_id`
- `VALUE:DIRECT:purchase_orders.id->purchase_returns.purchase_order_id`
- `VALUE:DIRECT:purchase_orders.id->purchase_returns.purchase_receipt_id`
- `VALUE:DIRECT:purchase_orders.supplier_id->purchase_returns.supplier_id`
- `VALUE:DIRECT:purchase_returns.id->purchase_return_items.return_id`
- `VALUE:DIRECT:purchase_returns.purchase_order_id->purchase_return_items.batch_id`
- `VALUE:DIRECT:purchase_returns.purchase_order_id->purchase_return_items.product_id`
- `VALUE:DIRECT:purchase_returns.purchase_order_id->purchase_return_items.unit_price`
- `VALUE:DIRECT:seq.num->damage_reports.status`
- `VALUE:DIRECT:seq.num->purchase_returns.approved_by`
- `VALUE:DIRECT:seq.num->purchase_returns.status`
- `VALUE:FUNCTION_CALL:seq.num->damage_reports.report_date`
- `VALUE:FUNCTION_CALL:seq.num->purchase_returns.return_date`

**Extractor Candidate Fingerprints**

- `VALUE:DIRECT:damage_reports.id->damage_report_items.report_id`
- `VALUE:DIRECT:purchase_returns.id->purchase_return_items.return_id`

**Input Preview**

```sql
-- ============================================================
-- 退货退款 + 报损数据生成
-- ============================================================

USE erp_system;

-- 采购退货数据
INSERT INTO purchase_returns (return_no, purchase_order_id, purchase_receipt_id, supplier_id,
```

## `mysql80sample-data-full-03-data-05-massive-data-generator-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-03-data-05-massive-data-generator-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-03-data-05-massive-data-generator-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:employees.salary->salary_payments.housing_fund_company`
- `VALUE:ARITHMETIC:employees.salary->salary_payments.housing_fund_personal`
- `VALUE:ARITHMETIC:employees.salary->salary_payments.income_tax`
- `VALUE:ARITHMETIC:employees.salary->salary_payments.net_pay`
- `VALUE:ARITHMETIC:employees.salary->salary_payments.social_security_company`
- `VALUE:ARITHMETIC:employees.salary->salary_payments.social_security_personal`
- `VALUE:ARITHMETIC:purchase_orders.order_date->invoices.invoice_date`
- `VALUE:ARITHMETIC:purchase_orders.order_date->invoices.verified_at`
- `VALUE:ARITHMETIC:purchase_orders.order_date->tax_invoices.invoice_date`
- `VALUE:ARITHMETIC:purchase_orders.total_amount->invoices.tax_amount`
- `VALUE:ARITHMETIC:purchase_orders.total_amount->tax_invoices.amount_excluding_tax`
- `VALUE:CONCAT_FORMAT:departments.code->positions.code`
- `VALUE:CONCAT_FORMAT:departments.name->positions.name`
- `VALUE:CONCAT_FORMAT:m.month,employees.id->salary_payments.payment_no`
- `VALUE:CONCAT_FORMAT:purchase_orders.id->tax_invoices.invoice_code`
- `VALUE:CONCAT_FORMAT:purchase_orders.order_date,purchase_orders.id->invoices.invoice_no`
- `VALUE:CONCAT_FORMAT:purchase_orders.order_date,purchase_orders.id->tax_invoices.invoice_no`
- `VALUE:DIRECT:departments.id->positions.department_id`
- `VALUE:DIRECT:employees.id->salary_payments.employee_id`
- `VALUE:DIRECT:employees.salary->salary_payments.base_salary`
- `VALUE:DIRECT:purchase_orders.supplier_id->invoices.supplier_id`
- `VALUE:DIRECT:purchase_orders.supplier_id->tax_invoices.party_id`
- `VALUE:DIRECT:purchase_orders.total_amount->invoices.total_amount`
- `VALUE:DIRECT:suppliers.id->supplier_products.supplier_id`
- `VALUE:FUNCTION_CALL:m.month->salary_payments.payment_date`
- `VALUE:FUNCTION_CALL:m.month->salary_payments.salary_month`
- `VALUE:FUNCTION_CALL:purchase_orders.order_date->invoices.due_date`
- `VALUE:FUNCTION_CALL:purchase_orders.order_date->tax_invoices.tax_period`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:employees.salary->salary_payments.housing_fund_company`
- `VALUE:ARITHMETIC:employees.salary->salary_payments.housing_fund_personal`
- `VALUE:ARITHMETIC:employees.salary->salary_payments.income_tax`
- `VALUE:ARITHMETIC:employees.salary->salary_payments.net_pay`
- `VALUE:ARITHMETIC:employees.salary->salary_payments.social_security_company`
- `VALUE:ARITHMETIC:employees.salary->salary_payments.social_security_personal`
- `VALUE:ARITHMETIC:purchase_orders.order_date->invoices.invoice_date`
- `VALUE:ARITHMETIC:purchase_orders.order_date->invoices.verified_at`
- `VALUE:ARITHMETIC:purchase_orders.order_date->tax_invoices.invoice_date`
- `VALUE:ARITHMETIC:purchase_orders.total_amount->invoices.tax_amount`
- `VALUE:ARITHMETIC:purchase_orders.total_amount->tax_invoices.amount_excluding_tax`
- `VALUE:CONCAT_FORMAT:departments.code->positions.code`
- `VALUE:CONCAT_FORMAT:departments.name->positions.name`
- `VALUE:CONCAT_FORMAT:employees.id->salary_payments.payment_no`
- `VALUE:CONCAT_FORMAT:purchase_orders.id->tax_invoices.invoice_code`
- `VALUE:CONCAT_FORMAT:purchase_orders.order_date,purchase_orders.id->invoices.invoice_no`
- `VALUE:CONCAT_FORMAT:purchase_orders.order_date,purchase_orders.id->tax_invoices.invoice_no`
- `VALUE:DIRECT:departments.id->positions.department_id`
- `VALUE:DIRECT:employees.id->salary_payments.employee_id`
- `VALUE:DIRECT:employees.salary->salary_payments.base_salary`
- `VALUE:DIRECT:purchase_orders.supplier_id->invoices.supplier_id`
- `VALUE:DIRECT:purchase_orders.supplier_id->tax_invoices.party_id`
- `VALUE:DIRECT:purchase_orders.total_amount->invoices.total_amount`
- `VALUE:DIRECT:suppliers.id->supplier_products.supplier_id`
- `VALUE:FUNCTION_CALL:purchase_orders.order_date->invoices.due_date`
- `VALUE:FUNCTION_CALL:purchase_orders.order_date->tax_invoices.tax_period`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.sp_generate_massive_data
CREATE PROCEDURE sp_generate_massive_data()
BEGIN
    DECLARE v_start TIMESTAMP DEFAULT NOW();

    -- 1. 清理并生成组织架构
    CALL sp_gen_org_structure();
```

## `mysql80sample-data-full-03-data-06-enterprise-extension-data-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/03-data/06-enterprise-extension-data.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-03-data-06-enterprise-extension-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP企业级扩展测试数据
-- 覆盖: 多租户/账套、地址、税率、会计期间、收付款、
--       库存盘点/调拨/预留、工艺路线/工序、班次排班
-- 数据库: MySQL 8.0
-- ============================================================

USE erp_system;
```

## `mysql80sample-data-full-04-queries-01-complex-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/04-queries/01-complex-queries.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-04-queries-01-complex-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统超复杂SQL查询集合
-- 覆盖: 多表JOIN, CTE(递归/非递归), 窗口函数, 嵌套子查询,
--       GROUP BY + HAVING, 复杂聚合组合, ROLLUP, UNION,
--       相关子查询, EXISTS, LATERAL, 条件聚合, 派生表
-- ============================================================

USE erp_system;
```

## `mysql80sample-data-full-04-queries-02-complex-queries-batch2-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/04-queries/02-complex-queries-batch2.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-04-queries-02-complex-queries-batch2-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统超复杂SQL查询集合 - 第二批
-- 覆盖: 递归CTE, 派生表JOIN, 窗口函数全系列,
--       UNION ALL 多维汇总, UNION/INTERSECT/EXCEPT模拟,
--       相关子查询嵌套, 派生表多层嵌套, 条件聚合嵌套,
--       动态分桶, 漏斗分析, 同期群分析, 留存分析
-- ============================================================
```

## `mysql80sample-data-full-04-queries-03-complex-queries-batch3-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/04-queries/03-complex-queries-batch3.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-04-queries-03-complex-queries-batch3-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第三批超复杂SQL查询: 客户消费分析 + 合同/税务/质检/项目/审批
-- 重点覆盖客户消费状态全方位分析
-- ============================================================

USE erp_system;

```

## `mysql80sample-data-full-04-queries-04-store-customer-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/04-queries/04-store-customer-queries.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-04-queries-04-store-customer-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第四批超复杂SQL查询: 门店/客户消费深度分析
-- 覆盖: 客户门店消费明细、门店畅销品、门店对比、
--        客户门店偏好、门店商品关联、门店销售预测
-- ============================================================

USE erp_system;
```

## `mysql80sample-data-full-04-queries-05-batch-expiry-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/04-queries/05-batch-expiry-queries.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-04-queries-05-batch-expiry-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第五批超复杂SQL: 批号保质期 + 类别销售/临期深度分析
-- 覆盖: 门店批号追踪、类别临期热力图、保质期预警、
--        类别动销对比、临期vs销售健康度、FIFO执行检查
-- ============================================================

USE erp_system;
```

## `mysql80sample-data-full-04-queries-06-return-damage-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/04-queries/06-return-damage-queries.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-04-queries-06-return-damage-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第六批超复杂SQL: 退货退款 + 报损分析 + 财务影响
-- ============================================================

USE erp_system;


-- ============================================================
```

## `mysql80sample-data-full-04-queries-07-supplier-analysis-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/04-queries/07-supplier-analysis-queries.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-04-queries-07-supplier-analysis-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第七批超复杂SQL: 供应商地理分析 + 智能选择 + 对比
-- 覆盖: 供应商PK、地理距离优化、物流成本、退货率、综合评分
-- ============================================================

USE erp_system;

```

## `mysql80sample-data-full-04-queries-08-common-system-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/04-queries/08-common-system-queries.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-04-queries-08-common-system-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 常用系统查询 - 模拟真实ERP系统日常使用的SQL
-- 覆盖: 多表JOIN、员工/门店/商品/客户/订单/库存/财务
--       日常查询、审批待办、报表导出、数据核对等
-- ============================================================

USE erp_system;
```

## `mysqlsample-data-full-01-schema-02-indexes-and-views-views-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `VIEW` |
| Input | `test-fixtures/correctness/mysql/mysql-sample-data-full-01-schema-02-indexes-and-views-views-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-01-schema-02-indexes-and-views-views-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: VIEW:erp_system.v_employee_full
SELECT
    e.id,
    e.employee_no,
    e.name,
    e.gender,
    e.phone,
    e.email,
```

## `mysqlsample-data-full-01-schema-03-triggers-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `TRIGGER` |
| Input | `test-fixtures/correctness/mysql/mysql-sample-data-full-01-schema-03-triggers-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-01-schema-03-triggers-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: TRIGGER:erp_system.trg_audit_employee_insert
CREATE TRIGGER trg_audit_employee_insert
AFTER INSERT ON employees
FOR EACH ROW
BEGIN
    INSERT INTO audit_log (employee_id, action, target_type, target_id, new_value)
    VALUES (NEW.id, 'INSERT', 'employee', NEW.id,
            JSON_OBJECT('name', NEW.name, 'employee_no', NEW.employee_no,
```

## `mysqlsample-data-full-02-procedures-01-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-01-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-01-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:cashier_journals.journal_type,cashier_journals.amount->reconciliation_items.credit_amount`
- `CONTROL:CASE_WHEN:cashier_journals.journal_type,cashier_journals.amount->reconciliation_items.debit_amount`
- `VALUE:COALESCE:cashier_journals.journal_type,cashier_journals.counterparty,cashier_journals.remark->reconciliation_items.description`
- `VALUE:DIRECT:cashier_journals.id->reconciliation_items.journal_id`
- `VALUE:DIRECT:cashier_journals.journal_date->reconciliation_items.transaction_date`

**Extractor Candidate Fingerprints**

- `CONTROL:CASE_WHEN:cashier_journals.journal_type,cashier_journals.amount->reconciliation_items.credit_amount`
- `CONTROL:CASE_WHEN:cashier_journals.journal_type,cashier_journals.amount->reconciliation_items.debit_amount`
- `VALUE:COALESCE:cashier_journals.journal_type,cashier_journals.counterparty,cashier_journals.remark->reconciliation_items.description`
- `VALUE:DIRECT:cashier_journals.id->reconciliation_items.journal_id`
- `VALUE:DIRECT:cashier_journals.journal_date->reconciliation_items.transaction_date`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.sp_create_department
CREATE PROCEDURE sp_create_department(
    IN p_parent_id BIGINT UNSIGNED,
    IN p_name VARCHAR(100),
    IN p_code VARCHAR(20),
    IN p_budget DECIMAL(18,2),
    IN p_headcount_plan INT UNSIGNED
)
```

## `mysqlsample-data-full-02-procedures-02-procedures-supplement-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-02-procedures-supplement-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-02-procedures-supplement-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.sp_transfer_inventory
CREATE PROCEDURE sp_transfer_inventory(
    IN p_product_id BIGINT UNSIGNED,
    IN p_batch_id BIGINT UNSIGNED,
    IN p_from_warehouse_id BIGINT UNSIGNED,
    IN p_to_warehouse_id BIGINT UNSIGNED,
    IN p_quantity INT,
    IN p_operator_id BIGINT UNSIGNED,
```

## `mysqlsample-data-full-02-procedures-03-functions-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-03-functions-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-03-functions-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.fn_employee_full_name
CREATE FUNCTION fn_employee_full_name(p_employee_id BIGINT UNSIGNED)
RETURNS VARCHAR(100)
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_result VARCHAR(100);
    SELECT CONCAT(employee_no, ' - ', name) INTO v_result
```

## `mysqlsample-data-full-02-procedures-04-procedures-supplement-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-04-procedures-supplement-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-04-procedures-supplement-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:sales_commissions.bonus->sales_commissions.bonus`
- `VALUE:ARITHMETIC:sales_commissions.commission_amount,sales_commissions.base_amount->sales_commissions.commission_amount`
- `VALUE:COALESCE:sales_order_items.amount->sales_commissions.commission_amount`
- `VALUE:DIRECT:sales_order_items.amount->sales_commissions.base_amount`
- `VALUE:DIRECT:sales_order_items.id->sales_commissions.order_item_id`
- `VALUE:DIRECT:sales_orders.id->sales_commissions.order_id`
- `VALUE:DIRECT:sales_orders.salesperson_id->sales_commissions.employee_id`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:sales_commissions.bonus->sales_commissions.bonus`
- `VALUE:ARITHMETIC:sales_commissions.commission_amount,sales_commissions.base_amount->sales_commissions.commission_amount`
- `VALUE:COALESCE:sales_order_items.amount->sales_commissions.commission_amount`
- `VALUE:DIRECT:sales_order_items.amount->sales_commissions.base_amount`
- `VALUE:DIRECT:sales_order_items.id->sales_commissions.order_item_id`
- `VALUE:DIRECT:sales_orders.id->sales_commissions.order_id`
- `VALUE:DIRECT:sales_orders.salesperson_id->sales_commissions.employee_id`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.sp_create_shipment
CREATE PROCEDURE sp_create_shipment(
    IN p_order_id BIGINT UNSIGNED,
    IN p_carrier VARCHAR(100),
    IN p_shipping_method ENUM('express','truck','air','sea','self_pickup'),
    IN p_shipping_fee DECIMAL(12,2),
    IN p_to_address VARCHAR(300),
    IN p_receiver_name VARCHAR(50),
```

## `mysqlsample-data-full-02-procedures-05-third-batch-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-05-third-batch-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-05-third-batch-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:sales_orders.customer_id->ar_aging_snapshots.customer_id`
- `VALUE:DIRECT:sales_orders.id->ar_aging_snapshots.order_id`
- `VALUE:DIRECT:sales_orders.paid_amount->ar_aging_snapshots.paid_amount`
- `VALUE:DIRECT:sales_orders.total_amount->ar_aging_snapshots.invoice_amount`
- `VALUE:FUNCTION_CALL:sales_orders.order_date,customers.credit_days->ar_aging_snapshots.due_date`

**Extractor Candidate Fingerprints**

- `VALUE:DIRECT:sales_orders.customer_id->ar_aging_snapshots.customer_id`
- `VALUE:DIRECT:sales_orders.id->ar_aging_snapshots.order_id`
- `VALUE:DIRECT:sales_orders.paid_amount->ar_aging_snapshots.paid_amount`
- `VALUE:DIRECT:sales_orders.total_amount->ar_aging_snapshots.invoice_amount`
- `VALUE:FUNCTION_CALL:sales_orders.order_date,customers.credit_days->ar_aging_snapshots.due_date`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.sp_generate_ar_aging
CREATE PROCEDURE sp_generate_ar_aging()
BEGIN
    -- 清理当天快照
    DELETE FROM ar_aging_snapshots WHERE snapshot_date = CURDATE();

    INSERT INTO ar_aging_snapshots (snapshot_date, customer_id, order_id,
        invoice_amount, paid_amount, due_date)
```

## `mysqlsample-data-full-02-procedures-06-third-batch-functions-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-06-third-batch-functions-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-06-third-batch-functions-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.fn_get_customer_clv
CREATE FUNCTION fn_get_customer_clv(
    p_customer_id BIGINT UNSIGNED
)
RETURNS DECIMAL(18,2)
DETERMINISTIC
READS SQL DATA
BEGIN
```

## `mysqlsample-data-full-02-procedures-07-store-customer-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-07-store-customer-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-07-store-customer-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.sp_customer_store_purchase_history
CREATE PROCEDURE sp_customer_store_purchase_history(
    IN p_customer_id BIGINT UNSIGNED,
    IN p_start_date DATE,
    IN p_end_date DATE
)
BEGIN
    SELECT
```

## `mysqlsample-data-full-02-procedures-08-batch-expiry-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-08-batch-expiry-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-08-batch-expiry-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.sp_batch_expiry_tracking
CREATE PROCEDURE sp_batch_expiry_tracking(
    IN p_role ENUM('store_manager','employee','senior_mgmt','all'),
    IN p_user_id BIGINT UNSIGNED,
    IN p_warehouse_id BIGINT UNSIGNED,
    IN p_expiry_days INT
)
BEGIN
```

## `mysqlsample-data-full-02-procedures-09-return-refund-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-09-return-refund-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-09-return-refund-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.sp_approve_sales_return
CREATE PROCEDURE sp_approve_sales_return(
    IN p_return_id BIGINT UNSIGNED,
    IN p_approved BOOLEAN,
    IN p_approver_id BIGINT UNSIGNED,
    IN p_approval_comment VARCHAR(500)
)
BEGIN
```

## `mysqlsample-data-full-02-procedures-10-supplier-geo-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-10-supplier-geo-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-10-supplier-geo-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:inspection_reports.inspection_result->supplier_products.quality_score`
- `VALUE:AGGREGATE:purchase_order_items.received_qty->supplier_products.total_order_qty`
- `VALUE:AGGREGATE:purchase_orders.id->supplier_products.total_order_count`
- `VALUE:AGGREGATE:purchase_orders.order_date->supplier_products.last_order_date`
- `VALUE:AGGREGATE:purchase_return_items.return_qty,purchase_order_items.received_qty->supplier_products.return_rate`

**Extractor Candidate Fingerprints**

- `CONTROL:CASE_WHEN:inspection_reports.inspection_result->supplier_products.quality_score`
- `VALUE:AGGREGATE:purchase_order_items.received_qty->supplier_products.total_order_qty`
- `VALUE:AGGREGATE:purchase_orders.id->supplier_products.total_order_count`
- `VALUE:AGGREGATE:purchase_orders.order_date->supplier_products.last_order_date`
- `VALUE:AGGREGATE:purchase_return_items.return_qty,purchase_order_items.received_qty->supplier_products.return_rate`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.fn_haversine_distance
CREATE FUNCTION fn_haversine_distance(
    p_lat1 DECIMAL(10,7),
    p_lon1 DECIMAL(10,7),
    p_lat2 DECIMAL(10,7),
    p_lon2 DECIMAL(10,7)
)
RETURNS DECIMAL(10,2)
```

## `mysqlsample-data-full-02-procedures-11-common-system-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-11-common-system-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-11-common-system-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.sp_poor_attendance_report
CREATE PROCEDURE sp_poor_attendance_report(
    IN p_year_month VARCHAR(7),
    IN p_department_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_start_date DATE;
    DECLARE v_end_date DATE;
```

## `mysqlsample-data-full-02-procedures-12-enterprise-extension-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-12-enterprise-extension-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-12-enterprise-extension-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.sp_post_stocktake
CREATE PROCEDURE sp_post_stocktake(
    IN p_stocktake_id BIGINT UNSIGNED,
    IN p_posted_by BIGINT UNSIGNED
)
BEGIN
    DECLARE v_warehouse_id BIGINT UNSIGNED;
```

## `mysqlsample-data-full-03-data-01-master-data-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/03-data/01-master-data.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-03-data-01-master-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统测试数据生成
-- 关系说明:
--   departments -> positions -> employees (1:N:N)
--   employees.manager_id 自引用形成汇报链
--   product_categories -> products -> product_batches (1:N:N)
--   suppliers -> supplier_products -> products (N:M)
--   warehouses -> inventory (1:N, 通过product_id+batch_id+warehouse_id唯一)
```

## `mysqlsample-data-full-03-data-02-supplementary-data-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/03-data/02-supplementary-data.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-03-data-02-supplementary-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:fixed_assets.monthly_depreciation->depreciation_log.after_accumulated`
- `VALUE:ARITHMETIC:fixed_assets.monthly_depreciation->depreciation_log.before_accumulated`
- `VALUE:ARITHMETIC:fixed_assets.purchase_amount,fixed_assets.monthly_depreciation->depreciation_log.after_net_value`
- `VALUE:ARITHMETIC:fixed_assets.purchase_amount,fixed_assets.monthly_depreciation->depreciation_log.before_net_value`
- `VALUE:DIRECT:fixed_assets.id->depreciation_log.asset_id`
- `VALUE:DIRECT:fixed_assets.monthly_depreciation->depreciation_log.depreciation_amount`
- `VALUE:DIRECT:shipments.id->shipping_tracks.shipment_id`
- `VALUE:FUNCTION_CALL:shipments.shipped_at->shipping_tracks.track_time`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:fixed_assets.monthly_depreciation->depreciation_log.after_accumulated`
- `VALUE:ARITHMETIC:fixed_assets.monthly_depreciation->depreciation_log.before_accumulated`
- `VALUE:ARITHMETIC:fixed_assets.purchase_amount,fixed_assets.monthly_depreciation->depreciation_log.after_net_value`
- `VALUE:ARITHMETIC:fixed_assets.purchase_amount,fixed_assets.monthly_depreciation->depreciation_log.before_net_value`
- `VALUE:DIRECT:fixed_assets.id->depreciation_log.asset_id`
- `VALUE:DIRECT:fixed_assets.monthly_depreciation->depreciation_log.depreciation_amount`
- `VALUE:DIRECT:shipments.id->shipping_tracks.shipment_id`
- `VALUE:FUNCTION_CALL:shipments.shipped_at->shipping_tracks.track_time`

**Input Preview**

```sql
-- ============================================================
-- ERP系统补充数据: 发货物流、促销、提成规则、发票、
--   固定资产、BOM、工单、客服工单
-- ============================================================

USE erp_system;

-- ============================================================
```

## `mysqlsample-data-full-03-data-03-third-batch-data-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/03-data/03-third-batch-data.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-03-data-03-third-batch-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:products.retail_price->price_change_logs.old_price`
- `VALUE:COALESCE:employees.manager_id->performance_reviews.reviewer_id`
- `VALUE:COALESCE:projects.start_date,projects.actual_end_date->project_costs.cost_date`
- `VALUE:CONCAT_FORMAT:employees.id->performance_reviews.review_no`
- `VALUE:CONCAT_FORMAT:projects.name->project_costs.description`
- `VALUE:DIRECT:employees.id->performance_reviews.employee_id`
- `VALUE:DIRECT:products.id->price_change_logs.product_id`
- `VALUE:DIRECT:products.retail_price->price_change_logs.new_price`
- `VALUE:DIRECT:projects.id->project_costs.project_id`
- `VALUE:DIRECT:purchase_orders.id->ap_aging_snapshots.order_id`
- `VALUE:DIRECT:purchase_orders.paid_amount->ap_aging_snapshots.paid_amount`
- `VALUE:DIRECT:purchase_orders.supplier_id->ap_aging_snapshots.supplier_id`
- `VALUE:DIRECT:purchase_orders.total_amount->ap_aging_snapshots.invoice_amount`
- `VALUE:DIRECT:sales_orders.customer_id->ar_aging_snapshots.customer_id`
- `VALUE:DIRECT:sales_orders.id->ar_aging_snapshots.order_id`
- `VALUE:DIRECT:sales_orders.paid_amount->ar_aging_snapshots.paid_amount`
- `VALUE:DIRECT:sales_orders.total_amount->ar_aging_snapshots.invoice_amount`
- `VALUE:FUNCTION_CALL:purchase_orders.order_date->ap_aging_snapshots.due_date`
- `VALUE:FUNCTION_CALL:sales_orders.order_date,customers.credit_days->ar_aging_snapshots.due_date`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:products.retail_price->price_change_logs.old_price`
- `VALUE:COALESCE:employees.manager_id->performance_reviews.reviewer_id`
- `VALUE:COALESCE:projects.start_date,projects.actual_end_date->project_costs.cost_date`
- `VALUE:CONCAT_FORMAT:employees.id->performance_reviews.review_no`
- `VALUE:CONCAT_FORMAT:projects.name->project_costs.description`
- `VALUE:DIRECT:employees.id->performance_reviews.employee_id`
- `VALUE:DIRECT:products.id->price_change_logs.product_id`
- `VALUE:DIRECT:products.retail_price->price_change_logs.new_price`
- `VALUE:DIRECT:projects.id->project_costs.project_id`
- `VALUE:DIRECT:purchase_orders.id->ap_aging_snapshots.order_id`
- `VALUE:DIRECT:purchase_orders.paid_amount->ap_aging_snapshots.paid_amount`
- `VALUE:DIRECT:purchase_orders.supplier_id->ap_aging_snapshots.supplier_id`
- `VALUE:DIRECT:purchase_orders.total_amount->ap_aging_snapshots.invoice_amount`
- `VALUE:DIRECT:sales_orders.customer_id->ar_aging_snapshots.customer_id`
- `VALUE:DIRECT:sales_orders.id->ar_aging_snapshots.order_id`
- `VALUE:DIRECT:sales_orders.paid_amount->ar_aging_snapshots.paid_amount`
- `VALUE:DIRECT:sales_orders.total_amount->ar_aging_snapshots.invoice_amount`
- `VALUE:FUNCTION_CALL:purchase_orders.order_date->ap_aging_snapshots.due_date`
- `VALUE:FUNCTION_CALL:sales_orders.order_date,customers.credit_days->ar_aging_snapshots.due_date`

**Input Preview**

```sql
-- ============================================================
-- 第三批补充数据: 合同、汇率、审批流、KPI、质检标准、项目、序列号、寄售
-- ============================================================

USE erp_system;

-- 合同数据
INSERT INTO contracts (contract_no, contract_type, party_type, party_id, subject,
```

## `mysqlsample-data-full-03-data-04-return-damage-data-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/03-data/04-return-damage-data.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-03-data-04-return-damage-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:damage_reports.id->damage_report_items.report_id`
- `VALUE:DIRECT:purchase_returns.id->purchase_return_items.return_id`

**Extractor Candidate Fingerprints**

- `VALUE:DIRECT:damage_reports.id->damage_report_items.report_id`
- `VALUE:DIRECT:purchase_returns.id->purchase_return_items.return_id`

**Input Preview**

```sql
-- ============================================================
-- 退货退款 + 报损数据生成
-- ============================================================

USE erp_system;

-- 采购退货数据
INSERT INTO purchase_returns (return_no, purchase_order_id, purchase_receipt_id, supplier_id,
```

## `mysqlsample-data-full-03-data-05-massive-data-generator-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/mysql/mysql-sample-data-full-03-data-05-massive-data-generator-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-03-data-05-massive-data-generator-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:employees.salary->salary_payments.housing_fund_company`
- `VALUE:ARITHMETIC:employees.salary->salary_payments.housing_fund_personal`
- `VALUE:ARITHMETIC:employees.salary->salary_payments.income_tax`
- `VALUE:ARITHMETIC:employees.salary->salary_payments.net_pay`
- `VALUE:ARITHMETIC:employees.salary->salary_payments.social_security_company`
- `VALUE:ARITHMETIC:employees.salary->salary_payments.social_security_personal`
- `VALUE:ARITHMETIC:purchase_orders.order_date->invoices.invoice_date`
- `VALUE:ARITHMETIC:purchase_orders.order_date->invoices.verified_at`
- `VALUE:ARITHMETIC:purchase_orders.order_date->tax_invoices.invoice_date`
- `VALUE:ARITHMETIC:purchase_orders.total_amount->invoices.tax_amount`
- `VALUE:ARITHMETIC:purchase_orders.total_amount->tax_invoices.amount_excluding_tax`
- `VALUE:CONCAT_FORMAT:departments.code->positions.code`
- `VALUE:CONCAT_FORMAT:departments.name->positions.name`
- `VALUE:CONCAT_FORMAT:employees.id->salary_payments.payment_no`
- `VALUE:CONCAT_FORMAT:purchase_orders.id->tax_invoices.invoice_code`
- `VALUE:CONCAT_FORMAT:purchase_orders.order_date,purchase_orders.id->invoices.invoice_no`
- `VALUE:CONCAT_FORMAT:purchase_orders.order_date,purchase_orders.id->tax_invoices.invoice_no`
- `VALUE:DIRECT:departments.id->positions.department_id`
- `VALUE:DIRECT:employees.id->salary_payments.employee_id`
- `VALUE:DIRECT:employees.salary->salary_payments.base_salary`
- `VALUE:DIRECT:purchase_orders.supplier_id->invoices.supplier_id`
- `VALUE:DIRECT:purchase_orders.supplier_id->tax_invoices.party_id`
- `VALUE:DIRECT:purchase_orders.total_amount->invoices.total_amount`
- `VALUE:DIRECT:suppliers.id->supplier_products.supplier_id`
- `VALUE:FUNCTION_CALL:purchase_orders.order_date->invoices.due_date`
- `VALUE:FUNCTION_CALL:purchase_orders.order_date->tax_invoices.tax_period`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:employees.salary->salary_payments.housing_fund_company`
- `VALUE:ARITHMETIC:employees.salary->salary_payments.housing_fund_personal`
- `VALUE:ARITHMETIC:employees.salary->salary_payments.income_tax`
- `VALUE:ARITHMETIC:employees.salary->salary_payments.net_pay`
- `VALUE:ARITHMETIC:employees.salary->salary_payments.social_security_company`
- `VALUE:ARITHMETIC:employees.salary->salary_payments.social_security_personal`
- `VALUE:ARITHMETIC:purchase_orders.order_date->invoices.invoice_date`
- `VALUE:ARITHMETIC:purchase_orders.order_date->invoices.verified_at`
- `VALUE:ARITHMETIC:purchase_orders.order_date->tax_invoices.invoice_date`
- `VALUE:ARITHMETIC:purchase_orders.total_amount->invoices.tax_amount`
- `VALUE:ARITHMETIC:purchase_orders.total_amount->tax_invoices.amount_excluding_tax`
- `VALUE:CONCAT_FORMAT:departments.code->positions.code`
- `VALUE:CONCAT_FORMAT:departments.name->positions.name`
- `VALUE:CONCAT_FORMAT:employees.id->salary_payments.payment_no`
- `VALUE:CONCAT_FORMAT:purchase_orders.id->tax_invoices.invoice_code`
- `VALUE:CONCAT_FORMAT:purchase_orders.order_date,purchase_orders.id->invoices.invoice_no`
- `VALUE:CONCAT_FORMAT:purchase_orders.order_date,purchase_orders.id->tax_invoices.invoice_no`
- `VALUE:DIRECT:departments.id->positions.department_id`
- `VALUE:DIRECT:employees.id->salary_payments.employee_id`
- `VALUE:DIRECT:employees.salary->salary_payments.base_salary`
- `VALUE:DIRECT:purchase_orders.supplier_id->invoices.supplier_id`
- `VALUE:DIRECT:purchase_orders.supplier_id->tax_invoices.party_id`
- `VALUE:DIRECT:purchase_orders.total_amount->invoices.total_amount`
- `VALUE:DIRECT:suppliers.id->supplier_products.supplier_id`
- `VALUE:FUNCTION_CALL:purchase_orders.order_date->invoices.due_date`
- `VALUE:FUNCTION_CALL:purchase_orders.order_date->tax_invoices.tax_period`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:erp_system.sp_generate_massive_data
CREATE PROCEDURE sp_generate_massive_data()
BEGIN
    DECLARE v_start TIMESTAMP DEFAULT NOW();

    -- 1. 清理并生成组织架构
    CALL sp_gen_org_structure();
```

## `mysqlsample-data-full-03-data-06-enterprise-extension-data-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/03-data/06-enterprise-extension-data.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-03-data-06-enterprise-extension-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP企业级扩展测试数据
-- 覆盖: 多租户/账套、地址、税率、会计期间、收付款、
--       库存盘点/调拨/预留、工艺路线/工序、班次排班
-- 数据库: MySQL 8.0
-- ============================================================

USE erp_system;
```

## `mysqlsample-data-full-04-queries-01-complex-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/04-queries/01-complex-queries.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-04-queries-01-complex-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统超复杂SQL查询集合
-- 覆盖: 多表JOIN, CTE(递归/非递归), 窗口函数, 嵌套子查询,
--       GROUP BY + HAVING, 复杂聚合组合, ROLLUP, UNION,
--       相关子查询, EXISTS, LATERAL, 条件聚合, 派生表
-- ============================================================

USE erp_system;
```

## `mysqlsample-data-full-04-queries-02-complex-queries-batch2-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/04-queries/02-complex-queries-batch2.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-04-queries-02-complex-queries-batch2-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统超复杂SQL查询集合 - 第二批
-- 覆盖: 递归CTE, 派生表JOIN, 窗口函数全系列,
--       UNION ALL 多维汇总, UNION/INTERSECT/EXCEPT模拟,
--       相关子查询嵌套, 派生表多层嵌套, 条件聚合嵌套,
--       动态分桶, 漏斗分析, 同期群分析, 留存分析
-- ============================================================
```

## `mysqlsample-data-full-04-queries-03-complex-queries-batch3-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/04-queries/03-complex-queries-batch3.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-04-queries-03-complex-queries-batch3-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第三批超复杂SQL查询: 客户消费分析 + 合同/税务/质检/项目/审批
-- 重点覆盖客户消费状态全方位分析
-- ============================================================

USE erp_system;

```

## `mysqlsample-data-full-04-queries-04-store-customer-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/04-queries/04-store-customer-queries.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-04-queries-04-store-customer-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第四批超复杂SQL查询: 门店/客户消费深度分析
-- 覆盖: 客户门店消费明细、门店畅销品、门店对比、
--        客户门店偏好、门店商品关联、门店销售预测
-- ============================================================

USE erp_system;
```

## `mysqlsample-data-full-04-queries-05-batch-expiry-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/04-queries/05-batch-expiry-queries.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-04-queries-05-batch-expiry-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第五批超复杂SQL: 批号保质期 + 类别销售/临期深度分析
-- 覆盖: 门店批号追踪、类别临期热力图、保质期预警、
--        类别动销对比、临期vs销售健康度、FIFO执行检查
-- ============================================================

USE erp_system;
```

## `mysqlsample-data-full-04-queries-06-return-damage-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/04-queries/06-return-damage-queries.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-04-queries-06-return-damage-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第六批超复杂SQL: 退货退款 + 报损分析 + 财务影响
-- ============================================================

USE erp_system;


-- ============================================================
```

## `mysqlsample-data-full-04-queries-07-supplier-analysis-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/04-queries/07-supplier-analysis-queries.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-04-queries-07-supplier-analysis-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第七批超复杂SQL: 供应商地理分析 + 智能选择 + 对比
-- 覆盖: 供应商PK、地理距离优化、物流成本、退货率、综合评分
-- ============================================================

USE erp_system;

```

## `mysqlsample-data-full-04-queries-08-common-system-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `MYSQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/mysql/8.0/04-queries/08-common-system-queries.sql` |
| Expected lineage | `test-fixtures/correctness/mysql/mysql-sample-data-full-04-queries-08-common-system-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 常用系统查询 - 模拟真实ERP系统日常使用的SQL
-- 覆盖: 多表JOIN、员工/门店/商品/客户/订单/库存/财务
--       日常查询、审批待办、报表导出、数据核对等
-- ============================================================

USE erp_system;
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

## `postgres-sample-data-enterprise-extension-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/postgres/18/01-schema/06-enterprise-extension-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统企业级扩展表 - PostgreSQL 18
-- 覆盖: 多租户/账套、地址、税率、会计期间、收付款、
--       库存盘点/调拨/预留、工艺路线/工序、班次排班
-- 数据库: PostgreSQL 18
-- ============================================================

-- ============================================================
```

## `postgres16-postgres-basic-correctness-case-01-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-basic-correctness-case-01-ddl/input.ddl.sql` |
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

## `postgres16-postgres-ddl-alter-table-fk`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v16/ddl-alter-table-fk/input.ddl.sql` |
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

## `postgres16-postgres-ddl-partial-index-boundary`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v16/ddl-partial-index-boundary/input.ddl.sql` |
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

## `postgres16-postgres-ddl-unique-include-index`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v16/ddl-unique-include-index/input.ddl.sql` |
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

## `postgres16-postgres-official-alter-index-boundary-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-official-alter-index-boundary-ddl/input.ddl.sql` |
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

## `postgres16-postgres-official-expression-access-method-index-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-official-expression-access-method-index-ddl/input.ddl.sql` |
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

## `postgres16-postgres-official-index-include-partial-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-official-index-include-partial-ddl/input.ddl.sql` |
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

## `postgres16-postgres-official-index-opclass-expression-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-official-index-opclass-expression-ddl/input.ddl.sql` |
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

## `postgres16-postgres-official-index-options-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-official-index-options-ddl/input.ddl.sql` |
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

## `postgres16-postgres-official-index-partition-boundary-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-official-index-partition-boundary-ddl/input.ddl.sql` |
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

## `postgres16-postgres-official-index-storage-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-official-index-storage-ddl/input.ddl.sql` |
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

## `postgres16-sample-data-enterprise-extension-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/postgres/18/01-schema/06-enterprise-extension-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统企业级扩展表 - PostgreSQL 18
-- 覆盖: 多租户/账套、地址、税率、会计期间、收付款、
--       库存盘点/调拨/预留、工艺路线/工序、班次排班
-- 数据库: PostgreSQL 18
-- ============================================================

-- ============================================================
```

## `postgres16sample-data-full-01-schema-01-tables-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/postgres/18/01-schema/01-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统完整数据库设计 - PostgreSQL 18
-- 模块: HR, 权限, 货品, 批号, 库存, 采购, 销售, 财务
-- 数据库: PostgreSQL 18
-- ============================================================

-- ============================================================
-- 0. 自定义ENUM类型
```

## `postgres16sample-data-full-01-schema-02-indexes-and-views-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/postgres/18/01-schema/02-indexes-and-views.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 索引补充 - 覆盖跨表查询常用路径
-- ============================================================

-- 库存与批号关联查询
CREATE INDEX idx_inv_batch_warehouse ON inventory(batch_id, warehouse_id);
CREATE INDEX idx_inv_product_warehouse ON inventory(product_id, warehouse_id);
```

## `postgres16sample-data-full-01-schema-04-supplementary-tables-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/postgres/18/01-schema/04-supplementary-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统补充表: 发货物流、销售提成、促销活动、
--   三单匹配、固定资产、BOM生产工单、客服工单
-- 关系说明:
--   shipments -> sales_orders (1:1), 通过tracking_no追踪物流
--   sales_commissions -> sales_orders + employees (N:1:1), 按销售额计算提成
--   promotions -> sales_order_items (N:M), 通过promotion_items关联
--   invoices -> purchase_orders + purchase_receipts (三单匹配)
```

## `postgres16sample-data-full-01-schema-05-third-batch-tables-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/postgres/18/01-schema/05-third-batch-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统第三批补充表: 合同管理、AR/AP账龄、税务管理、
--   质检、审批流引擎、现金流预测、项目成本、多币种汇率、
--   绩效考核、序列号追踪、寄售库存、价格变更历史
-- 关系说明:
--   contracts -> sales_orders/purchase_orders (1:1), 管理合同条款和里程碑
--   ar_aging / ap_aging: 账龄分析用，按月计算应收账款/应付账款
--   tax_invoices: 增值税发票管理，进项税/销项税
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

## `postgres17-sample-data-enterprise-extension-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/postgres/18/01-schema/06-enterprise-extension-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统企业级扩展表 - PostgreSQL 18
-- 覆盖: 多租户/账套、地址、税率、会计期间、收付款、
--       库存盘点/调拨/预留、工艺路线/工序、班次排班
-- 数据库: PostgreSQL 18
-- ============================================================

-- ============================================================
```

## `postgres17sample-data-full-01-schema-01-tables-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/postgres/18/01-schema/01-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统完整数据库设计 - PostgreSQL 18
-- 模块: HR, 权限, 货品, 批号, 库存, 采购, 销售, 财务
-- 数据库: PostgreSQL 18
-- ============================================================

-- ============================================================
-- 0. 自定义ENUM类型
```

## `postgres17sample-data-full-01-schema-02-indexes-and-views-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/postgres/18/01-schema/02-indexes-and-views.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 索引补充 - 覆盖跨表查询常用路径
-- ============================================================

-- 库存与批号关联查询
CREATE INDEX idx_inv_batch_warehouse ON inventory(batch_id, warehouse_id);
CREATE INDEX idx_inv_product_warehouse ON inventory(product_id, warehouse_id);
```

## `postgres17sample-data-full-01-schema-04-supplementary-tables-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/postgres/18/01-schema/04-supplementary-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统补充表: 发货物流、销售提成、促销活动、
--   三单匹配、固定资产、BOM生产工单、客服工单
-- 关系说明:
--   shipments -> sales_orders (1:1), 通过tracking_no追踪物流
--   sales_commissions -> sales_orders + employees (N:1:1), 按销售额计算提成
--   promotions -> sales_order_items (N:M), 通过promotion_items关联
--   invoices -> purchase_orders + purchase_receipts (三单匹配)
```

## `postgres17sample-data-full-01-schema-05-third-batch-tables-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/postgres/18/01-schema/05-third-batch-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统第三批补充表: 合同管理、AR/AP账龄、税务管理、
--   质检、审批流引擎、现金流预测、项目成本、多币种汇率、
--   绩效考核、序列号追踪、寄售库存、价格变更历史
-- 关系说明:
--   contracts -> sales_orders/purchase_orders (1:1), 管理合同条款和里程碑
--   ar_aging / ap_aging: 账龄分析用，按月计算应收账款/应付账款
--   tax_invoices: 增值税发票管理，进项税/销项税
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

## `postgres18-sample-data-enterprise-extension-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/postgres/18/01-schema/06-enterprise-extension-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统企业级扩展表 - PostgreSQL 18
-- 覆盖: 多租户/账套、地址、税率、会计期间、收付款、
--       库存盘点/调拨/预留、工艺路线/工序、班次排班
-- 数据库: PostgreSQL 18
-- ============================================================

-- ============================================================
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

## `postgres18sample-data-full-01-schema-01-tables-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/postgres/18/01-schema/01-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统完整数据库设计 - PostgreSQL 18
-- 模块: HR, 权限, 货品, 批号, 库存, 采购, 销售, 财务
-- 数据库: PostgreSQL 18
-- ============================================================

-- ============================================================
-- 0. 自定义ENUM类型
```

## `postgres18sample-data-full-01-schema-02-indexes-and-views-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/postgres/18/01-schema/02-indexes-and-views.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 索引补充 - 覆盖跨表查询常用路径
-- ============================================================

-- 库存与批号关联查询
CREATE INDEX idx_inv_batch_warehouse ON inventory(batch_id, warehouse_id);
CREATE INDEX idx_inv_product_warehouse ON inventory(product_id, warehouse_id);
```

## `postgres18sample-data-full-01-schema-04-supplementary-tables-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/postgres/18/01-schema/04-supplementary-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统补充表: 发货物流、销售提成、促销活动、
--   三单匹配、固定资产、BOM生产工单、客服工单
-- 关系说明:
--   shipments -> sales_orders (1:1), 通过tracking_no追踪物流
--   sales_commissions -> sales_orders + employees (N:1:1), 按销售额计算提成
--   promotions -> sales_order_items (N:M), 通过promotion_items关联
--   invoices -> purchase_orders + purchase_receipts (三单匹配)
```

## `postgres18sample-data-full-01-schema-05-third-batch-tables-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/postgres/18/01-schema/05-third-batch-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统第三批补充表: 合同管理、AR/AP账龄、税务管理、
--   质检、审批流引擎、现金流预测、项目成本、多币种汇率、
--   绩效考核、序列号追踪、寄售库存、价格变更历史
-- 关系说明:
--   contracts -> sales_orders/purchase_orders (1:1), 管理合同条款和里程碑
--   ar_aging / ap_aging: 账龄分析用，按月计算应收账款/应付账款
--   tax_invoices: 增值税发票管理，进项税/销项税
```

## `postgressample-data-full-01-schema-01-tables-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/postgres/18/01-schema/01-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统完整数据库设计 - PostgreSQL 18
-- 模块: HR, 权限, 货品, 批号, 库存, 采购, 销售, 财务
-- 数据库: PostgreSQL 18
-- ============================================================

-- ============================================================
-- 0. 自定义ENUM类型
```

## `postgressample-data-full-01-schema-02-indexes-and-views-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/postgres/18/01-schema/02-indexes-and-views.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 索引补充 - 覆盖跨表查询常用路径
-- ============================================================

-- 库存与批号关联查询
CREATE INDEX idx_inv_batch_warehouse ON inventory(batch_id, warehouse_id);
CREATE INDEX idx_inv_product_warehouse ON inventory(product_id, warehouse_id);
```

## `postgressample-data-full-01-schema-04-supplementary-tables-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/postgres/18/01-schema/04-supplementary-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统补充表: 发货物流、销售提成、促销活动、
--   三单匹配、固定资产、BOM生产工单、客服工单
-- 关系说明:
--   shipments -> sales_orders (1:1), 通过tracking_no追踪物流
--   sales_commissions -> sales_orders + employees (N:1:1), 按销售额计算提成
--   promotions -> sales_order_items (N:M), 通过promotion_items关联
--   invoices -> purchase_orders + purchase_receipts (三单匹配)
```

## `postgressample-data-full-01-schema-05-third-batch-tables-ddl`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DDL does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `DDL` |
| Source type | `DDL_FILE` |
| Input | `sample-data/postgres/18/01-schema/05-third-batch-tables.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统第三批补充表: 合同管理、AR/AP账龄、税务管理、
--   质检、审批流引擎、现金流预测、项目成本、多币种汇率、
--   绩效考核、序列号追踪、寄售库存、价格变更历史
-- 关系说明:
--   contracts -> sales_orders/purchase_orders (1:1), 管理合同条款和里程碑
--   ar_aging / ap_aging: 账龄分析用，按月计算应收账款/应付账款
--   tax_invoices: 增值税发票管理，进项税/销项税
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
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-asset-balances-update-outer-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-business-asset-balances-update-outer-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:ledger_system_a.balance,ledger_system_b.balance->asset_balances.discrepancy_flag`
- `VALUE:COALESCE:ledger_system_a.balance,ledger_system_b.balance->asset_balances.computed_balance`
- `VALUE:DIRECT:staff_assignments.operator_name->asset_balances.last_checked_by`

**Extractor Candidate Fingerprints**

- `CONTROL:CASE_WHEN:ledger_system_a.balance,ledger_system_b.balance->asset_balances.discrepancy_flag`
- `VALUE:COALESCE:ledger_system_a.balance,ledger_system_b.balance->asset_balances.computed_balance`
- `VALUE:DIRECT:staff_assignments.operator_name->asset_balances.last_checked_by`

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
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-update-inventory-comma-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-business-update-inventory-comma-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:inventory.stock_reserved,order_items.quantity->inventory.stock_reserved`
- `VALUE:DIRECT:suppliers.supplier_name->inventory.last_ordered_from`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:inventory.stock_reserved,order_items.quantity->inventory.stock_reserved`
- `VALUE:DIRECT:suppliers.supplier_name->inventory.last_ordered_from`

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
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-update-inventory-from-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-business-update-inventory-from-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:inventory.stock_reserved,order_items.quantity->inventory.stock_reserved`
- `VALUE:DIRECT:suppliers.supplier_name->inventory.last_ordered_from`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:inventory.stock_reserved,order_items.quantity->inventory.stock_reserved`
- `VALUE:DIRECT:suppliers.supplier_name->inventory.last_ordered_from`

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
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-update-products-comma-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-business-update-products-comma-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:products.original_price->products.promo_price`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:products.original_price->products.promo_price`

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
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-update-products-from-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-business-update-products-from-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:products.original_price->products.promo_price`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:products.original_price->products.promo_price`

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
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-comma-subquery-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-comma-subquery-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

**Extractor Candidate Fingerprints**

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

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
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-complex-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-complex-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

**Extractor Candidate Fingerprints**

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

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

## `postgres-edge-cases-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-edge-cases-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- SQL解析器边界测试用例
-- 目标: 各种tricky语法、边界条件、易混淆模式
-- 适用: 所有PostgreSQL版本
-- ============================================================================

-- ============================================================================
-- Part 1: 复杂JOIN语法 - 各种JOIN类型混合
```

## `postgres-extreme-nesting-withrelation-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-extreme-nesting-withrelation-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- SQL解析器边界测试用例
-- 目标: 各种tricky语法、边界条件、易混淆模式
-- 适用: 所有PostgreSQL版本
-- ============================================================================

-- ============================================================================
-- Part 1: 复杂JOIN语法 - 各种JOIN类型混合
```

## `postgres-extreme-nesting-withrelation-withlineage-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-extreme-nesting-withrelation-withlineage-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-extreme-nesting-withrelation-withlineage-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:orders.total_amount,order_items.extended_amount->orders.total_amount`
- `VALUE:CONCAT_FORMAT:customers.risk_level,orders.status->orders.risk_note`
- `VALUE:DIRECT:customers.country_code->orders.customer_country`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:orders.total_amount,order_items.extended_amount->orders.total_amount`
- `VALUE:CONCAT_FORMAT:customers.risk_level,orders.status->orders.risk_note`
- `VALUE:DIRECT:customers.country_code->orders.customer_country`

**Input Preview**

```sql
-- ============================================================================
-- SQL解析器边界测试用例
-- 目标: 各种tricky语法、边界条件、易混淆模式
-- 适用: 所有PostgreSQL版本
-- ============================================================================

-- ============================================================================
-- Part 1: 复杂JOIN语法 - 各种JOIN类型混合
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
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-official-cte-dml-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-official-cte-dml-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

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

## `postgres-pg10-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-pg10-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 10 复杂SQL测试用例
-- 特性: 声明式分区, Identity列, 逻辑复制, 并行查询增强, 哈希索引
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: 声明式分区 - 多层分区 + 子分区
```

## `postgres-pg11-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-pg11-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 11 复杂SQL测试用例
-- 特性: 存储过程(CREATE PROCEDURE + 事务控制), 分区表增强(PRIMARY KEY,
--       DEFAULT分区, 自动索引), 哈希分区, 覆盖索引(INCLUDE)
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
```

## `postgres-pg12-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-pg12-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 12 复杂SQL测试用例
-- 特性: 生成列, JSON_PATH, 物化CTE, 分区性能改进
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: 生成列与复杂表定义
```

## `postgres-pg13-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-pg13-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 13 复杂SQL测试用例
-- 特性: 增量排序, 并行哈希连接, 分区改进, LATERAL增强
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: 增量排序与并行查询 (PG13优化器特性)
```

## `postgres-pg14-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-pg14-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 14 复杂SQL测试用例
-- 特性: 多范围类型, JSON下标访问, 存储过程OUT参数, 扩展查询管道
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: 多范围类型 (multirange) 复杂操作
```

## `postgres-pg15-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-pg15-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-pg15-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 15 复杂SQL测试用例
-- 特性: MERGE语句, SQL/JSON函数(IS JSON, JSON_SCALAR, JSON_EXISTS等),
--       CLUSTER并行, 逻辑复制行过滤
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
```

## `postgres-pg16-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-pg16-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 16 复杂SQL测试用例
-- 特性: SQL/JSON构造函数(JSON_OBJECT, JSON_ARRAY, JSON_OBJECTAGG, JSON_ARRAYAGG),
--       IS JSON增强, 聚合函数增强, 并行哈希全连接
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
```

## `postgres-pg17-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/postgres-pg17-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-pg17-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 17 复杂SQL测试用例
-- 特性: JSON_TABLE, MERGE增强 (RETURNING, 多动作), 增量JSON解析,
--       COPY性能改进, 系统信息函数增强
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
```

## `postgres-sample-data-enterprise-extension-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/10-enterprise-extension-queries.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP企业级扩展分析查询 - PostgreSQL 18
-- 覆盖: 盘点差异、调拨履约、收付款核销、会计期间、
--       工艺路线、地址与税率
-- ============================================================

-- Q1: 库存盘点差异分析
SELECT
```

## `postgres-sample-data-enterprise-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/sample-data-enterprise-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/sample-data-enterprise-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Input Preview**

```sql
-- relation-detector-fixture-source: PROCEDURE:sample_data.sp_post_stocktake
CREATE OR REPLACE PROCEDURE sp_post_stocktake(
    IN p_stocktake_id BIGINT,
    IN p_posted_by BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
```

## `postgres-sample-data-pg18-specific-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/11-pg18-specific.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- PostgreSQL 18 专属语法样例
-- 说明:
--   这些 SQL 用来覆盖 PostgreSQL 18 版本能力，不参与 MySQL 8.0 业务对齐。
--   可在 PostgreSQL 18 环境中单独执行。
-- ============================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;
```

## `postgres-sample-data-real-world-scenarios-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/09-real-world-scenarios.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/sample-data-real-world-scenarios-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统真实业务场景SQL查询 - 第九批
-- 覆盖: Procure-to-Pay全链路、Order-to-Cash全链路、
--       产品真实利润、员工人效、库存持有成本、资金周转周期、
--       信用风险监控、批号全链路追溯、毛利瀑布、预算滚动预测、
--       供应商集中度风险、月度关账核对、需求预测准确率、
--       仓库库容利用率、提成核对、价格弹性分析
-- ============================================================
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
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/sql-merge-using/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/sql-merge-using/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

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

## `postgres16-edge-cases-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-edge-cases-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- SQL解析器边界测试用例
-- 目标: 各种tricky语法、边界条件、易混淆模式
-- 适用: 所有PostgreSQL版本
-- ============================================================================

-- ============================================================================
-- Part 1: 复杂JOIN语法 - 各种JOIN类型混合
```

## `postgres16-extreme-nesting-withrelation-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-extreme-nesting-withrelation-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- SQL解析器边界测试用例
-- 目标: 各种tricky语法、边界条件、易混淆模式
-- 适用: 所有PostgreSQL版本
-- ============================================================================

-- ============================================================================
-- Part 1: 复杂JOIN语法 - 各种JOIN类型混合
```

## `postgres16-extreme-nesting-withrelation-withlineage-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-extreme-nesting-withrelation-withlineage-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-extreme-nesting-withrelation-withlineage-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:orders.total_amount,order_items.extended_amount->orders.total_amount`
- `VALUE:CONCAT_FORMAT:customers.risk_level,orders.status->orders.risk_note`
- `VALUE:DIRECT:customers.country_code->orders.customer_country`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:orders.total_amount,order_items.extended_amount->orders.total_amount`
- `VALUE:CONCAT_FORMAT:customers.risk_level,orders.status->orders.risk_note`
- `VALUE:DIRECT:customers.country_code->orders.customer_country`

**Input Preview**

```sql
-- ============================================================================
-- SQL解析器边界测试用例
-- 目标: 各种tricky语法、边界条件、易混淆模式
-- 适用: 所有PostgreSQL版本
-- ============================================================================

-- ============================================================================
-- Part 1: 复杂JOIN语法 - 各种JOIN类型混合
```

## `postgres16-pg10-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-pg10-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 10 复杂SQL测试用例
-- 特性: 声明式分区, Identity列, 逻辑复制, 并行查询增强, 哈希索引
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: 声明式分区 - 多层分区 + 子分区
```

## `postgres16-pg11-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-pg11-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 11 复杂SQL测试用例
-- 特性: 存储过程(CREATE PROCEDURE + 事务控制), 分区表增强(PRIMARY KEY,
--       DEFAULT分区, 自动索引), 哈希分区, 覆盖索引(INCLUDE)
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
```

## `postgres16-pg12-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-pg12-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 12 复杂SQL测试用例
-- 特性: 生成列, JSON_PATH, 物化CTE, 分区性能改进
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: 生成列与复杂表定义
```

## `postgres16-pg13-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-pg13-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 13 复杂SQL测试用例
-- 特性: 增量排序, 并行哈希连接, 分区改进, LATERAL增强
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: 增量排序与并行查询 (PG13优化器特性)
```

## `postgres16-pg14-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-pg14-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 14 复杂SQL测试用例
-- 特性: 多范围类型, JSON下标访问, 存储过程OUT参数, 扩展查询管道
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: 多范围类型 (multirange) 复杂操作
```

## `postgres16-pg15-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-pg15-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-pg15-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:pg15_inventory_target.quantity,pg15_inventory_source.total_qty_delta->pg15_inventory_target.quantity`
- `VALUE:ARITHMETIC:pg15_inventory_target.reserved,pg15_inventory_source.total_reserved_delta->pg15_inventory_target.reserved`
- `VALUE:ARITHMETIC:pg15_inventory_target.version->pg15_inventory_target.version`
- `VALUE:COALESCE:pg15_inventory_source.latest_cost,pg15_inventory_target.cost->pg15_inventory_target.cost`
- `VALUE:COALESCE:pg15_inventory_source.latest_price,pg15_inventory_target.price->pg15_inventory_target.price`
- `VALUE:COALESCE:pg15_inventory_target.metadata,pg15_inventory_source.merged_metadata->pg15_inventory_target.metadata`
- `VALUE:DIRECT:pg15_inventory_source.latest_processed_at->pg15_inventory_target.last_updated`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 15 复杂SQL测试用例
-- 特性: MERGE语句, SQL/JSON函数(IS JSON, JSON_SCALAR, JSON_EXISTS等),
--       CLUSTER并行, 逻辑复制行过滤
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
```

## `postgres16-pg16-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-pg16-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 16 复杂SQL测试用例
-- 特性: SQL/JSON构造函数(JSON_OBJECT, JSON_ARRAY, JSON_OBJECTAGG, JSON_ARRAYAGG),
--       IS JSON增强, 聚合函数增强, 并行哈希全连接
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
```

## `postgres16-pg17-version-boundary-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | negative full-grammer version-boundary fixture; unsupported SQL is not lineage golden |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-pg17-version-boundary-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 17 复杂SQL测试用例
-- 特性: JSON_TABLE, MERGE增强 (RETURNING, 多动作), 增量JSON解析,
--       COPY性能改进, 系统信息函数增强
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
```

## `postgres16-postgres-basic-correctness-case-01-objects-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-basic-correctness-case-01-objects-sql/input.sql` |
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

## `postgres16-postgres-basic-correctness-case-01-statements-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `NATIVE_LOG` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-basic-correctness-case-01-statements-sql/input.sql` |
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

## `postgres16-postgres-business-account-balances-financial-cte-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-account-balances-financial-cte-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres-business-account-balances-financial-cte-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:account_balances.max_credit_limit->account_balances.adjusted_limit`
- `VALUE:CONCAT_FORMAT:users.country_code,user_financial_snapshot.last_activity_time,user_financial_snapshot.net_cash_flow,transaction_ledgers.merchant_category->account_balances.compliance_notes`
- `VALUE:FUNCTION_CALL:account_balances.risk_flags->account_balances.risk_flags`

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

## `postgres16-postgres-business-account-balances-financial-explicit-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-account-balances-financial-explicit-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres-business-account-balances-financial-explicit-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:account_balances.max_credit_limit->account_balances.adjusted_limit`
- `VALUE:CONCAT_FORMAT:users.country_code,user_financial_snapshot.last_activity_time,user_financial_snapshot.net_cash_flow,transaction_ledgers.merchant_category->account_balances.compliance_notes`
- `VALUE:FUNCTION_CALL:account_balances.risk_flags->account_balances.risk_flags`

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

## `postgres16-postgres-business-asset-balances-update-outer-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-asset-balances-update-outer-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres-business-asset-balances-update-outer-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:ledger_system_a.balance,ledger_system_b.balance->asset_balances.discrepancy_flag`
- `VALUE:COALESCE:ledger_system_a.balance,ledger_system_b.balance->asset_balances.computed_balance`
- `VALUE:DIRECT:staff_assignments.operator_name->asset_balances.last_checked_by`

**Extractor Candidate Fingerprints**

- `CONTROL:CASE_WHEN:ledger_system_a.balance,ledger_system_b.balance->asset_balances.discrepancy_flag`
- `VALUE:COALESCE:ledger_system_a.balance,ledger_system_b.balance->asset_balances.computed_balance`
- `VALUE:DIRECT:staff_assignments.operator_name->asset_balances.last_checked_by`

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

## `postgres16-postgres-business-cross-border-reconciliation-function-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-cross-border-reconciliation-function-sql/input.sql` |
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

## `postgres16-postgres-business-delete-cascade-cte-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-delete-cascade-cte-sql/input.sql` |
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

## `postgres16-postgres-business-delete-orphan-left-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-delete-orphan-left-join-sql/input.sql` |
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

## `postgres16-postgres-business-delete-orphan-not-exists-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-delete-orphan-not-exists-sql/input.sql` |
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

## `postgres16-postgres-business-inventory-purge-deep-subquery-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-inventory-purge-deep-subquery-sql/input.sql` |
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

## `postgres16-postgres-business-inventory-purge-exists-equivalent-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-inventory-purge-exists-equivalent-sql/input.sql` |
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

## `postgres16-postgres-business-risk-ledger-update-cte-comma-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-risk-ledger-update-cte-comma-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres-business-risk-ledger-update-cte-comma-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:CONCAT_FORMAT:users.risk_level,orders.user_id,orders.amount->order_ledgers.remarks`

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

## `postgres16-postgres-business-risk-ledger-update-cte-explicit-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-risk-ledger-update-cte-explicit-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres-business-risk-ledger-update-cte-explicit-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:CONCAT_FORMAT:users.risk_level,orders.user_id,orders.amount->order_ledgers.remarks`

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

## `postgres16-postgres-business-risk-settlement-function-comma-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | local temporary table sources are excluded from Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-risk-settlement-function-comma-sql/input.sql` |
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

## `postgres16-postgres-business-risk-settlement-function-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | local temporary table sources are excluded from Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-risk-settlement-function-sql/input.sql` |
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

## `postgres16-postgres-business-update-inventory-comma-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-update-inventory-comma-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres-business-update-inventory-comma-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:inventory.stock_reserved,order_items.quantity->inventory.stock_reserved`
- `VALUE:DIRECT:suppliers.supplier_name->inventory.last_ordered_from`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:inventory.stock_reserved,order_items.quantity->inventory.stock_reserved`
- `VALUE:DIRECT:suppliers.supplier_name->inventory.last_ordered_from`

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

## `postgres16-postgres-business-update-inventory-from-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-update-inventory-from-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres-business-update-inventory-from-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:inventory.stock_reserved,order_items.quantity->inventory.stock_reserved`
- `VALUE:DIRECT:suppliers.supplier_name->inventory.last_ordered_from`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:inventory.stock_reserved,order_items.quantity->inventory.stock_reserved`
- `VALUE:DIRECT:suppliers.supplier_name->inventory.last_ordered_from`

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

## `postgres16-postgres-business-update-products-comma-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-update-products-comma-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres-business-update-products-comma-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:products.original_price->products.promo_price`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:products.original_price->products.promo_price`

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

## `postgres16-postgres-business-update-products-from-join-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-update-products-from-join-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres-business-update-products-from-join-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:products.original_price->products.promo_price`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:products.original_price->products.promo_price`

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

## `postgres16-postgres-business-update-users-aggregate-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-update-users-aggregate-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres-business-update-users-aggregate-sql/expected-lineage.json` |

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

## `postgres16-postgres-business-update-users-scalar-subquery-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-update-users-scalar-subquery-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres-business-update-users-scalar-subquery-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:orders.pay_amount->users.level`
- `VALUE:AGGREGATE:orders.pay_amount->users.total_spent`

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

## `postgres16-postgres-business-update-warehouse-comma-subquery-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-update-warehouse-comma-subquery-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres-business-update-warehouse-comma-subquery-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

**Extractor Candidate Fingerprints**

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

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

## `postgres16-postgres-business-update-warehouse-complex-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-update-warehouse-complex-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres-business-update-warehouse-complex-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

**Extractor Candidate Fingerprints**

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

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

## `postgres16-postgres-business-user-coupons-delete-derived-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-user-coupons-delete-derived-join-sql/input.sql` |
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

## `postgres16-postgres-business-user-coupons-delete-exists-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-business-user-coupons-delete-exists-sql/input.sql` |
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

## `postgres16-postgres-generated-comprehensive-query-sql`

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

## `postgres16-postgres-generated-industrial-complex-sql`

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

## `postgres16-postgres-generated-provided-complex-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/generated-provided-complex-sql/input.sql` |
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

## `postgres16-postgres-official-cte-dml-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-official-cte-dml-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres-official-cte-dml-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:staging_orders.customer_id->orders.customer_id`

**Extractor Candidate Fingerprints**

- None

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

## `postgres16-postgres-official-cte-nested-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-official-cte-nested-sql/input.sql` |
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

## `postgres16-postgres-official-join-edge-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-official-join-edge-sql/input.sql` |
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

## `postgres16-postgres-official-lateral-function-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-official-lateral-function-sql/input.sql` |
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

## `postgres16-postgres-official-lateral-nested-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-official-lateral-nested-join-sql/input.sql` |
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

## `postgres16-postgres-official-multiway-join-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-official-multiway-join-sql/input.sql` |
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

## `postgres16-postgres-official-subquery-deep-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-official-subquery-deep-sql/input.sql` |
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

## `postgres16-postgres-official-subquery-edge-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/postgres-official-subquery-edge-sql/input.sql` |
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

## `postgres16-postgres-sql-delete-using-no-alias`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | DELETE does not write target column values in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/sql-delete-using-no-alias/input.sql` |
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

## `postgres16-postgres-sql-lateral-derived`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/sql-lateral-derived/input.sql` |
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

## `postgres16-postgres-sql-merge-using`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/sql-merge-using/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/sql-merge-using/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:DIRECT:source_orders.id->target_orders.source_order_id`

**Extractor Candidate Fingerprints**

- None

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

## `postgres16-postgres-sql-multi-layer-cte`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/sql-multi-layer-cte/input.sql` |
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

## `postgres16-postgres-sql-quoted-mixed-alias`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/sql-quoted-mixed-alias/input.sql` |
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

## `postgres16-postgres-sql-recursive-cte`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/sql-recursive-cte/input.sql` |
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

## `postgres16-postgres-sql-unnest-ordinality`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/sql-unnest-ordinality/input.sql` |
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

## `postgres16-postgres-sql-update-from-aliases`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v16/sql-update-from-aliases/input.sql` |
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

## `postgres16-sample-data-enterprise-extension-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/10-enterprise-extension-queries.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP企业级扩展分析查询 - PostgreSQL 18
-- 覆盖: 盘点差异、调拨履约、收付款核销、会计期间、
--       工艺路线、地址与税率
-- ============================================================

-- Q1: 库存盘点差异分析
SELECT
```

## `postgres16-sample-data-enterprise-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/sample-data-enterprise-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-enterprise-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Input Preview**

```sql
-- relation-detector-fixture-source: PROCEDURE:sample_data.sp_post_stocktake
CREATE OR REPLACE PROCEDURE sp_post_stocktake(
    IN p_stocktake_id BIGINT,
    IN p_posted_by BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
```

## `postgres16-sample-data-pg18-specific-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | negative full-grammer version-boundary fixture; unsupported SQL is not lineage golden |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/11-pg18-specific.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- PostgreSQL 18 专属语法样例
-- 说明:
--   这些 SQL 用来覆盖 PostgreSQL 18 版本能力，不参与 MySQL 8.0 业务对齐。
--   可在 PostgreSQL 18 环境中单独执行。
-- ============================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;
```

## `postgres16-sample-data-real-world-scenarios-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/09-real-world-scenarios.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-real-world-scenarios-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统真实业务场景SQL查询 - 第九批
-- 覆盖: Procure-to-Pay全链路、Order-to-Cash全链路、
--       产品真实利润、员工人效、库存持有成本、资金周转周期、
--       信用风险监控、批号全链路追溯、毛利瀑布、预算滚动预测、
--       供应商集中度风险、月度关账核对、需求预测准确率、
--       仓库库容利用率、提成核对、价格弹性分析
-- ============================================================
```

## `postgres16sample-data-full-01-schema-02-indexes-and-views-views-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `VIEW` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-01-schema-02-indexes-and-views-views-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-01-schema-02-indexes-and-views-views-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: VIEW:public.v_employee_full
SELECT
    e.id,
    e.employee_no,
    e.name,
    e.gender,
    e.phone,
    e.email,
```

## `postgres16sample-data-full-01-schema-03-triggers-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `TRIGGER` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-01-schema-03-triggers-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-01-schema-03-triggers-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:inventory.quantity,sales_order_items.quantity->inventory.quantity`
- `VALUE:DIRECT:sales_order_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:sales_order_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:sales_order_items.quantity->inventory_transactions.quantity_change`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:inventory.quantity,sales_order_items.quantity->inventory.quantity`
- `VALUE:DIRECT:sales_order_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:sales_order_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:sales_order_items.quantity->inventory_transactions.quantity_change`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.trg_audit_employee_insert
CREATE OR REPLACE FUNCTION trg_audit_employee_insert() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO audit_log (employee_id, action, target_type, target_id, new_value)
    VALUES (NEW.id, 'INSERT', 'employee', NEW.id,
            jsonb_build_object('name', NEW.name, 'employee_no', NEW.employee_no,
                       'department_id', NEW.department_id, 'salary', NEW.salary, 'status', NEW.status));
    RETURN NEW;
```

## `postgres16sample-data-full-02-procedures-01-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-01-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-01-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.generate_employee_no
CREATE OR REPLACE FUNCTION generate_employee_no()
RETURNS VARCHAR(20) AS $$
BEGIN
    RETURN TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || LPAD((FLOOR(RANDOM() * 9999) + 1)::TEXT, 4, '0');
END;
$$ LANGUAGE plpgsql;
```

## `postgres16sample-data-full-02-procedures-02-procedures-supplement-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-02-procedures-supplement-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-02-procedures-supplement-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_transfer_inventory
CREATE OR REPLACE PROCEDURE sp_transfer_inventory(
    IN p_product_id BIGINT,
    IN p_batch_id BIGINT,
    IN p_from_warehouse_id BIGINT,
    IN p_to_warehouse_id BIGINT,
    IN p_quantity INT,
    IN p_operator_id BIGINT,
```

## `postgres16sample-data-full-02-procedures-03-functions-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-03-functions-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-03-functions-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.fn_employee_full_name
CREATE OR REPLACE FUNCTION fn_employee_full_name(p_employee_id BIGINT)
RETURNS VARCHAR(100)
STABLE
LANGUAGE plpgsql
AS $$
DECLARE
    v_result VARCHAR(100);
```

## `postgres16sample-data-full-02-procedures-04-procedures-supplement-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-04-procedures-supplement-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-04-procedures-supplement-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:sales_commissions.bonus->sales_commissions.bonus`
- `VALUE:ARITHMETIC:sales_commissions.commission_amount,sales_commissions.base_amount->sales_commissions.commission_amount`
- `VALUE:COALESCE:sales_order_items.amount->sales_commissions.commission_amount`
- `VALUE:DIRECT:sales_order_items.amount->sales_commissions.base_amount`
- `VALUE:DIRECT:sales_order_items.id->sales_commissions.order_item_id`
- `VALUE:DIRECT:sales_orders.id->sales_commissions.order_id`
- `VALUE:DIRECT:sales_orders.salesperson_id->sales_commissions.employee_id`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:sales_commissions.bonus->sales_commissions.bonus`
- `VALUE:ARITHMETIC:sales_commissions.commission_amount,sales_commissions.base_amount->sales_commissions.commission_amount`
- `VALUE:COALESCE:sales_order_items.amount->sales_commissions.commission_amount`
- `VALUE:DIRECT:sales_order_items.amount->sales_commissions.base_amount`
- `VALUE:DIRECT:sales_order_items.id->sales_commissions.order_item_id`
- `VALUE:DIRECT:sales_orders.id->sales_commissions.order_id`
- `VALUE:DIRECT:sales_orders.salesperson_id->sales_commissions.employee_id`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_create_shipment
CREATE OR REPLACE PROCEDURE sp_create_shipment(
    IN p_order_id BIGINT,
    IN p_carrier VARCHAR(100),
    IN p_shipping_method VARCHAR(20),
    IN p_shipping_fee DECIMAL(12,2),
    IN p_to_address VARCHAR(300),
    IN p_receiver_name VARCHAR(50),
```

## `postgres16sample-data-full-02-procedures-05-third-batch-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-05-third-batch-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-05-third-batch-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_generate_ar_aging
CREATE OR REPLACE PROCEDURE sp_generate_ar_aging()
LANGUAGE plpgsql
AS $$
BEGIN
    -- 清理当天快照
    DELETE FROM ar_aging_snapshots WHERE snapshot_date = CURRENT_DATE;
```

## `postgres16sample-data-full-02-procedures-06-third-batch-functions-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-06-third-batch-functions-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-06-third-batch-functions-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.fn_get_customer_clv
CREATE OR REPLACE FUNCTION fn_get_customer_clv(
    p_customer_id BIGINT
)
RETURNS DECIMAL(18,2)
STABLE
LANGUAGE plpgsql
AS $$
```

## `postgres16sample-data-full-02-procedures-07-store-customer-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-07-store-customer-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-07-store-customer-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_customer_store_purchase_history
CREATE OR REPLACE FUNCTION sp_customer_store_purchase_history(
    p_customer_id BIGINT,
    p_start_date DATE,
    p_end_date DATE
)
RETURNS TABLE(
    purchase_date DATE,
```

## `postgres16sample-data-full-02-procedures-08-batch-expiry-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-08-batch-expiry-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-08-batch-expiry-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_batch_expiry_tracking
CREATE OR REPLACE FUNCTION sp_batch_expiry_tracking(
    p_role TEXT,
    p_user_id BIGINT,
    p_warehouse_id BIGINT,
    p_expiry_days INTEGER
)
RETURNS TABLE(
```

## `postgres16sample-data-full-02-procedures-09-return-refund-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-09-return-refund-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-09-return-refund-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_approve_sales_return
CREATE OR REPLACE PROCEDURE sp_approve_sales_return(
    IN p_return_id BIGINT,
    IN p_approved BOOLEAN,
    IN p_approver_id BIGINT,
    IN p_approval_comment VARCHAR(500),
    OUT p_result TEXT
)
```

## `postgres16sample-data-full-02-procedures-10-supplier-geo-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-10-supplier-geo-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-10-supplier-geo-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.fn_haversine_distance
CREATE OR REPLACE FUNCTION fn_haversine_distance(
    p_lat1 NUMERIC(10,7),
    p_lon1 NUMERIC(10,7),
    p_lat2 NUMERIC(10,7),
    p_lon2 NUMERIC(10,7)
)
RETURNS NUMERIC(10,2)
```

## `postgres16sample-data-full-02-procedures-11-common-system-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-11-common-system-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-11-common-system-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_poor_attendance_report
CREATE OR REPLACE FUNCTION sp_poor_attendance_report(
    p_year_month VARCHAR(7),
    p_department_id BIGINT
)
RETURNS TABLE(
    employee_id BIGINT,
    employee_name VARCHAR(100),
```

## `postgres16sample-data-full-02-procedures-12-enterprise-extension-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-12-enterprise-extension-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-12-enterprise-extension-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_post_stocktake
CREATE OR REPLACE PROCEDURE sp_post_stocktake(
    IN p_stocktake_id BIGINT,
    IN p_posted_by BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
```

## `postgres16sample-data-full-03-data-01-master-data-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/03-data/01-master-data.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-03-data-01-master-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统测试数据生成
-- 关系说明:
--   departments -> positions -> employees (1:N:N)
--   employees.manager_id 自引用形成汇报链
--   product_categories -> products -> product_batches (1:N:N)
--   suppliers -> supplier_products -> products (N:M)
--   warehouses -> inventory (1:N, 通过product_id+batch_id+warehouse_id唯一)
```

## `postgres16sample-data-full-03-data-02-supplementary-data-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/03-data/02-supplementary-data.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-03-data-02-supplementary-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统补充测试数据 - PostgreSQL 18
-- 目的: 对齐 MySQL 8.0 样例中已有但 PostgreSQL 初稿缺失的数据目标表
-- ============================================================

-- 促销与提成
INSERT INTO commission_rules (id, name, product_category_id, min_amount, max_amount, commission_rate, bonus, effective_date, status) VALUES
(1, '标准销售提成', NULL, 0.00, 99999999.99, 0.0300, 0.00, '2026-01-01', 'active');
```

## `postgres16sample-data-full-03-data-03-enterprise-extension-data-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/03-data/03-enterprise-extension-data.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-03-data-03-enterprise-extension-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP企业级扩展测试数据 - PostgreSQL 18
-- 覆盖: 多租户/账套、地址、税率、会计期间、收付款、
--       库存盘点/调拨/预留、工艺路线/工序、班次排班
-- ============================================================

INSERT INTO tenants (id, tenant_code, tenant_name, legal_entity_name, tax_no, status) VALUES
(1, 'T001', '华东运营主体', '上海华东智造商贸有限公司', '91310000MA1ERP001X', 'active'),
```

## `postgres16sample-data-full-04-queries-01-complex-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/01-complex-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-04-queries-01-complex-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统超复杂SQL查询集合 (PostgreSQL 18)
-- 覆盖: 多表JOIN, CTE(递归/非递归), 窗口函数, 嵌套子查询,
--       GROUP BY + HAVING, 复杂聚合组合, ROLLUP, UNION,
--       相关子查询, EXISTS, LATERAL, 条件聚合, 派生表
-- ============================================================

```

## `postgres16sample-data-full-04-queries-02-complex-queries-batch2-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/02-complex-queries-batch2.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-04-queries-02-complex-queries-batch2-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统超复杂SQL查询集合 - 第二批 (PostgreSQL 18)
-- 覆盖: 递归CTE, LATERAL JOIN, 窗口函数全系列,
--       GROUPING SETS/CUBE, UNION/INTERSECT/EXCEPT模拟,
--       相关子查询嵌套, 派生表多层嵌套, 条件聚合嵌套,
--       动态分桶, 漏斗分析, 同期群分析, 留存分析
-- ============================================================
```

## `postgres16sample-data-full-04-queries-03-complex-queries-batch3-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/03-complex-queries-batch3.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-04-queries-03-complex-queries-batch3-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第三批超复杂SQL查询: 客户消费分析 + 合同/税务/质检/项目/审批 (PostgreSQL 18)
-- 重点覆盖客户消费状态全方位分析
-- ============================================================


-- ============================================================
-- Q39: 客户消费分层全景图 - 五维消费画像
```

## `postgres16sample-data-full-04-queries-04-store-customer-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/04-store-customer-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-04-queries-04-store-customer-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第四批超复杂SQL查询: 门店/客户消费深度分析
-- 覆盖: 客户门店消费明细、门店畅销品、门店对比、
--        客户门店偏好、门店商品关联、门店销售预测
-- ============================================================


-- ============================================================
```

## `postgres16sample-data-full-04-queries-05-batch-expiry-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/05-batch-expiry-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-04-queries-05-batch-expiry-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第五批超复杂SQL: 批号保质期 + 类别销售/临期深度分析
-- 覆盖: 门店批号追踪、类别临期热力图、保质期预警、
--        类别动销对比、临期vs销售健康度、FIFO执行检查
-- ============================================================


-- ============================================================
```

## `postgres16sample-data-full-04-queries-06-return-damage-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/06-return-damage-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-04-queries-06-return-damage-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第六批超复杂SQL: 退货退款 + 报损分析 + 财务影响
-- ============================================================


-- ============================================================
-- Q68: 退货原因根因分析 - 按品类/门店/供应商交叉
-- 语法: CTE + 多维度交叉 + 原因占比 + 饼图数据
```

## `postgres16sample-data-full-04-queries-07-supplier-analysis-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/07-supplier-analysis-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-04-queries-07-supplier-analysis-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第七批超复杂SQL: 供应商地理分析 + 智能选择 + 对比
-- 覆盖: 供应商PK、地理距离优化、物流成本、退货率、综合评分
-- ============================================================


-- ============================================================
-- Q76: 全产品供应商覆盖率分析 - 哪些产品缺供应商
```

## `postgres16sample-data-full-04-queries-08-common-system-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/08-common-system-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-04-queries-08-common-system-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 常用系统查询 - 模拟真实ERP系统日常使用的SQL
-- 覆盖: 多表JOIN、员工/门店/商品/客户/订单/库存/财务
--       日常查询、审批待办、报表导出、数据核对等
-- ============================================================


-- ============================================================
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
- `VALUE:CONCAT_FORMAT:users.country_code,user_financial_snapshot.last_activity_time,user_financial_snapshot.net_cash_flow,transaction_ledgers.merchant_category->account_balances.compliance_notes`
- `VALUE:FUNCTION_CALL:account_balances.risk_flags->account_balances.risk_flags`

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
- `VALUE:CONCAT_FORMAT:users.country_code,user_financial_snapshot.last_activity_time,user_financial_snapshot.net_cash_flow,transaction_ledgers.merchant_category->account_balances.compliance_notes`
- `VALUE:FUNCTION_CALL:account_balances.risk_flags->account_balances.risk_flags`

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

- `CONTROL:CASE_WHEN:ledger_system_a.balance,ledger_system_b.balance->asset_balances.discrepancy_flag`
- `VALUE:COALESCE:ledger_system_a.balance,ledger_system_b.balance->asset_balances.computed_balance`
- `VALUE:DIRECT:staff_assignments.operator_name->asset_balances.last_checked_by`

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

- `VALUE:CONCAT_FORMAT:users.risk_level,orders.user_id,orders.amount->order_ledgers.remarks`

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

- `VALUE:CONCAT_FORMAT:users.risk_level,orders.user_id,orders.amount->order_ledgers.remarks`

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
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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

- `VALUE:ARITHMETIC:inventory.stock_reserved,order_items.quantity->inventory.stock_reserved`
- `VALUE:DIRECT:suppliers.supplier_name->inventory.last_ordered_from`

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

- `VALUE:ARITHMETIC:inventory.stock_reserved,order_items.quantity->inventory.stock_reserved`
- `VALUE:DIRECT:suppliers.supplier_name->inventory.last_ordered_from`

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

- `VALUE:ARITHMETIC:products.original_price->products.promo_price`

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

- `VALUE:ARITHMETIC:products.original_price->products.promo_price`

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

- `CONTROL:CASE_WHEN:orders.pay_amount->users.level`
- `VALUE:AGGREGATE:orders.pay_amount->users.total_spent`

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

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

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

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

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

## `postgres17-edge-cases-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-edge-cases-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- SQL解析器边界测试用例
-- 目标: 各种tricky语法、边界条件、易混淆模式
-- 适用: 所有PostgreSQL版本
-- ============================================================================

-- ============================================================================
-- Part 1: 复杂JOIN语法 - 各种JOIN类型混合
```

## `postgres17-extreme-nesting-withrelation-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-extreme-nesting-withrelation-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- SQL解析器边界测试用例
-- 目标: 各种tricky语法、边界条件、易混淆模式
-- 适用: 所有PostgreSQL版本
-- ============================================================================

-- ============================================================================
-- Part 1: 复杂JOIN语法 - 各种JOIN类型混合
```

## `postgres17-extreme-nesting-withrelation-withlineage-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-extreme-nesting-withrelation-withlineage-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-extreme-nesting-withrelation-withlineage-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:orders.total_amount,order_items.extended_amount->orders.total_amount`
- `VALUE:CONCAT_FORMAT:customers.risk_level,orders.status->orders.risk_note`
- `VALUE:DIRECT:customers.country_code->orders.customer_country`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:orders.total_amount,order_items.extended_amount->orders.total_amount`
- `VALUE:CONCAT_FORMAT:customers.risk_level,orders.status->orders.risk_note`
- `VALUE:DIRECT:customers.country_code->orders.customer_country`

**Input Preview**

```sql
-- ============================================================================
-- SQL解析器边界测试用例
-- 目标: 各种tricky语法、边界条件、易混淆模式
-- 适用: 所有PostgreSQL版本
-- ============================================================================

-- ============================================================================
-- Part 1: 复杂JOIN语法 - 各种JOIN类型混合
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
- `VALUE:DIRECT:staging_account_balances.user_id->account_balances.user_id`

**Extractor Candidate Fingerprints**

- None

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

- `VALUE:DIRECT:staging_orders.customer_id->orders.customer_id`

**Extractor Candidate Fingerprints**

- None

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

## `postgres17-pg10-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-pg10-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 10 复杂SQL测试用例
-- 特性: 声明式分区, Identity列, 逻辑复制, 并行查询增强, 哈希索引
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: 声明式分区 - 多层分区 + 子分区
```

## `postgres17-pg11-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-pg11-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 11 复杂SQL测试用例
-- 特性: 存储过程(CREATE PROCEDURE + 事务控制), 分区表增强(PRIMARY KEY,
--       DEFAULT分区, 自动索引), 哈希分区, 覆盖索引(INCLUDE)
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
```

## `postgres17-pg12-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-pg12-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 12 复杂SQL测试用例
-- 特性: 生成列, JSON_PATH, 物化CTE, 分区性能改进
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: 生成列与复杂表定义
```

## `postgres17-pg13-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-pg13-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 13 复杂SQL测试用例
-- 特性: 增量排序, 并行哈希连接, 分区改进, LATERAL增强
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: 增量排序与并行查询 (PG13优化器特性)
```

## `postgres17-pg14-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-pg14-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 14 复杂SQL测试用例
-- 特性: 多范围类型, JSON下标访问, 存储过程OUT参数, 扩展查询管道
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: 多范围类型 (multirange) 复杂操作
```

## `postgres17-pg15-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-pg15-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-pg15-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:pg15_inventory_target.quantity,pg15_inventory_source.total_qty_delta->pg15_inventory_target.quantity`
- `VALUE:ARITHMETIC:pg15_inventory_target.reserved,pg15_inventory_source.total_reserved_delta->pg15_inventory_target.reserved`
- `VALUE:ARITHMETIC:pg15_inventory_target.version->pg15_inventory_target.version`
- `VALUE:COALESCE:pg15_inventory_source.latest_cost,pg15_inventory_target.cost->pg15_inventory_target.cost`
- `VALUE:COALESCE:pg15_inventory_source.latest_cost->pg15_inventory_target.cost`
- `VALUE:COALESCE:pg15_inventory_source.latest_price,pg15_inventory_target.price->pg15_inventory_target.price`
- `VALUE:COALESCE:pg15_inventory_source.latest_price->pg15_inventory_target.price`
- `VALUE:COALESCE:pg15_inventory_source.merged_metadata->pg15_inventory_target.metadata`
- `VALUE:COALESCE:pg15_inventory_target.metadata,pg15_inventory_source.merged_metadata->pg15_inventory_target.metadata`
- `VALUE:CONCAT_FORMAT:pg15_inventory_target.metadata,pg15_inventory_source.validation_status,pg15_inventory_source.change_log,pg15_inventory_source.risk_metrics,pg15_inventory_source.latest_processed_at->pg15_inventory_target.metadata`
- `VALUE:DIRECT:pg15_inventory_source.latest_processed_at->pg15_inventory_target.last_updated`
- `VALUE:DIRECT:pg15_inventory_source.sku->pg15_inventory_target.sku`
- `VALUE:DIRECT:pg15_inventory_source.total_qty_delta->pg15_inventory_target.quantity`
- `VALUE:DIRECT:pg15_inventory_source.total_reserved_delta->pg15_inventory_target.reserved`
- `VALUE:DIRECT:pg15_inventory_source.warehouse_id->pg15_inventory_target.warehouse_id`
- `VALUE:FUNCTION_CALL:pg15_inventory_source.validation_status,pg15_inventory_source.change_log,pg15_inventory_source.risk_metrics->pg15_inventory_target.metadata`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 15 复杂SQL测试用例
-- 特性: MERGE语句, SQL/JSON函数(IS JSON, JSON_SCALAR, JSON_EXISTS等),
--       CLUSTER并行, 逻辑复制行过滤
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
```

## `postgres17-pg16-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-pg16-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 16 复杂SQL测试用例
-- 特性: SQL/JSON构造函数(JSON_OBJECT, JSON_ARRAY, JSON_OBJECTAGG, JSON_ARRAYAGG),
--       IS JSON增强, 聚合函数增强, 并行哈希全连接
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
```

## `postgres17-pg17-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-pg17-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-pg17-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:pg17_product_catalog.version->pg17_product_catalog.version`
- `VALUE:COALESCE:pg17_price_updates.approver->pg17_product_catalog.updated_by`
- `VALUE:COALESCE:pg17_price_updates.attribute_updates->pg17_product_catalog.attributes`
- `VALUE:COALESCE:pg17_price_updates.stock_adjustment->pg17_product_catalog.stock_level`
- `VALUE:COALESCE:pg17_product_catalog.attributes,pg17_price_updates.attribute_updates->pg17_product_catalog.attributes`
- `VALUE:COALESCE:pg17_product_catalog.stock_level,pg17_price_updates.stock_adjustment->pg17_product_catalog.stock_level`
- `VALUE:CONCAT_FORMAT:pg17_price_updates.sku->pg17_product_catalog.name`
- `VALUE:DIRECT:pg17_price_updates.approver->pg17_product_catalog.updated_by`
- `VALUE:DIRECT:pg17_price_updates.new_price->pg17_product_catalog.base_price`
- `VALUE:DIRECT:pg17_price_updates.new_price->pg17_product_catalog.current_price`
- `VALUE:DIRECT:pg17_price_updates.sku->pg17_product_catalog.sku`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 17 复杂SQL测试用例
-- 特性: JSON_TABLE, MERGE增强 (RETURNING, 多动作), 增量JSON解析,
--       COPY性能改进, 系统信息函数增强
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
```

## `postgres17-sample-data-enterprise-extension-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/10-enterprise-extension-queries.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP企业级扩展分析查询 - PostgreSQL 18
-- 覆盖: 盘点差异、调拨履约、收付款核销、会计期间、
--       工艺路线、地址与税率
-- ============================================================

-- Q1: 库存盘点差异分析
SELECT
```

## `postgres17-sample-data-enterprise-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/sample-data-enterprise-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-enterprise-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Input Preview**

```sql
-- relation-detector-fixture-source: PROCEDURE:sample_data.sp_post_stocktake
CREATE OR REPLACE PROCEDURE sp_post_stocktake(
    IN p_stocktake_id BIGINT,
    IN p_posted_by BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
```

## `postgres17-sample-data-pg18-specific-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | negative full-grammer version-boundary fixture; unsupported SQL is not lineage golden |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/11-pg18-specific.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- PostgreSQL 18 专属语法样例
-- 说明:
--   这些 SQL 用来覆盖 PostgreSQL 18 版本能力，不参与 MySQL 8.0 业务对齐。
--   可在 PostgreSQL 18 环境中单独执行。
-- ============================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;
```

## `postgres17-sample-data-real-world-scenarios-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/09-real-world-scenarios.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-real-world-scenarios-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统真实业务场景SQL查询 - 第九批
-- 覆盖: Procure-to-Pay全链路、Order-to-Cash全链路、
--       产品真实利润、员工人效、库存持有成本、资金周转周期、
--       信用风险监控、批号全链路追溯、毛利瀑布、预算滚动预测、
--       供应商集中度风险、月度关账核对、需求预测准确率、
--       仓库库容利用率、提成核对、价格弹性分析
-- ============================================================
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

- None

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

## `postgres17sample-data-full-01-schema-02-indexes-and-views-views-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `VIEW` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-01-schema-02-indexes-and-views-views-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-01-schema-02-indexes-and-views-views-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: VIEW:public.v_employee_full
SELECT
    e.id,
    e.employee_no,
    e.name,
    e.gender,
    e.phone,
    e.email,
```

## `postgres17sample-data-full-01-schema-03-triggers-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `TRIGGER` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-01-schema-03-triggers-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-01-schema-03-triggers-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:inventory.quantity,sales_order_items.quantity->inventory.quantity`
- `VALUE:DIRECT:sales_order_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:sales_order_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:sales_order_items.quantity->inventory_transactions.quantity_change`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:inventory.quantity,sales_order_items.quantity->inventory.quantity`
- `VALUE:DIRECT:sales_order_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:sales_order_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:sales_order_items.quantity->inventory_transactions.quantity_change`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.trg_audit_employee_insert
CREATE OR REPLACE FUNCTION trg_audit_employee_insert() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO audit_log (employee_id, action, target_type, target_id, new_value)
    VALUES (NEW.id, 'INSERT', 'employee', NEW.id,
            jsonb_build_object('name', NEW.name, 'employee_no', NEW.employee_no,
                       'department_id', NEW.department_id, 'salary', NEW.salary, 'status', NEW.status));
    RETURN NEW;
```

## `postgres17sample-data-full-02-procedures-01-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-01-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-01-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.generate_employee_no
CREATE OR REPLACE FUNCTION generate_employee_no()
RETURNS VARCHAR(20) AS $$
BEGIN
    RETURN TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || LPAD((FLOOR(RANDOM() * 9999) + 1)::TEXT, 4, '0');
END;
$$ LANGUAGE plpgsql;
```

## `postgres17sample-data-full-02-procedures-02-procedures-supplement-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-02-procedures-supplement-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-02-procedures-supplement-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_transfer_inventory
CREATE OR REPLACE PROCEDURE sp_transfer_inventory(
    IN p_product_id BIGINT,
    IN p_batch_id BIGINT,
    IN p_from_warehouse_id BIGINT,
    IN p_to_warehouse_id BIGINT,
    IN p_quantity INT,
    IN p_operator_id BIGINT,
```

## `postgres17sample-data-full-02-procedures-03-functions-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-03-functions-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-03-functions-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.fn_employee_full_name
CREATE OR REPLACE FUNCTION fn_employee_full_name(p_employee_id BIGINT)
RETURNS VARCHAR(100)
STABLE
LANGUAGE plpgsql
AS $$
DECLARE
    v_result VARCHAR(100);
```

## `postgres17sample-data-full-02-procedures-04-procedures-supplement-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-04-procedures-supplement-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-04-procedures-supplement-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:sales_commissions.bonus->sales_commissions.bonus`
- `VALUE:ARITHMETIC:sales_commissions.commission_amount,sales_commissions.base_amount->sales_commissions.commission_amount`
- `VALUE:COALESCE:sales_order_items.amount->sales_commissions.commission_amount`
- `VALUE:DIRECT:sales_order_items.amount->sales_commissions.base_amount`
- `VALUE:DIRECT:sales_order_items.id->sales_commissions.order_item_id`
- `VALUE:DIRECT:sales_orders.id->sales_commissions.order_id`
- `VALUE:DIRECT:sales_orders.salesperson_id->sales_commissions.employee_id`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:sales_commissions.bonus->sales_commissions.bonus`
- `VALUE:ARITHMETIC:sales_commissions.commission_amount,sales_commissions.base_amount->sales_commissions.commission_amount`
- `VALUE:COALESCE:sales_order_items.amount->sales_commissions.commission_amount`
- `VALUE:DIRECT:sales_order_items.amount->sales_commissions.base_amount`
- `VALUE:DIRECT:sales_order_items.id->sales_commissions.order_item_id`
- `VALUE:DIRECT:sales_orders.id->sales_commissions.order_id`
- `VALUE:DIRECT:sales_orders.salesperson_id->sales_commissions.employee_id`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_create_shipment
CREATE OR REPLACE PROCEDURE sp_create_shipment(
    IN p_order_id BIGINT,
    IN p_carrier VARCHAR(100),
    IN p_shipping_method VARCHAR(20),
    IN p_shipping_fee DECIMAL(12,2),
    IN p_to_address VARCHAR(300),
    IN p_receiver_name VARCHAR(50),
```

## `postgres17sample-data-full-02-procedures-05-third-batch-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-05-third-batch-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-05-third-batch-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_generate_ar_aging
CREATE OR REPLACE PROCEDURE sp_generate_ar_aging()
LANGUAGE plpgsql
AS $$
BEGIN
    -- 清理当天快照
    DELETE FROM ar_aging_snapshots WHERE snapshot_date = CURRENT_DATE;
```

## `postgres17sample-data-full-02-procedures-06-third-batch-functions-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-06-third-batch-functions-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-06-third-batch-functions-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.fn_get_customer_clv
CREATE OR REPLACE FUNCTION fn_get_customer_clv(
    p_customer_id BIGINT
)
RETURNS DECIMAL(18,2)
STABLE
LANGUAGE plpgsql
AS $$
```

## `postgres17sample-data-full-02-procedures-07-store-customer-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-07-store-customer-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-07-store-customer-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_customer_store_purchase_history
CREATE OR REPLACE FUNCTION sp_customer_store_purchase_history(
    p_customer_id BIGINT,
    p_start_date DATE,
    p_end_date DATE
)
RETURNS TABLE(
    purchase_date DATE,
```

## `postgres17sample-data-full-02-procedures-08-batch-expiry-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-08-batch-expiry-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-08-batch-expiry-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_batch_expiry_tracking
CREATE OR REPLACE FUNCTION sp_batch_expiry_tracking(
    p_role TEXT,
    p_user_id BIGINT,
    p_warehouse_id BIGINT,
    p_expiry_days INTEGER
)
RETURNS TABLE(
```

## `postgres17sample-data-full-02-procedures-09-return-refund-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-09-return-refund-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-09-return-refund-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_approve_sales_return
CREATE OR REPLACE PROCEDURE sp_approve_sales_return(
    IN p_return_id BIGINT,
    IN p_approved BOOLEAN,
    IN p_approver_id BIGINT,
    IN p_approval_comment VARCHAR(500),
    OUT p_result TEXT
)
```

## `postgres17sample-data-full-02-procedures-10-supplier-geo-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-10-supplier-geo-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-10-supplier-geo-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.fn_haversine_distance
CREATE OR REPLACE FUNCTION fn_haversine_distance(
    p_lat1 NUMERIC(10,7),
    p_lon1 NUMERIC(10,7),
    p_lat2 NUMERIC(10,7),
    p_lon2 NUMERIC(10,7)
)
RETURNS NUMERIC(10,2)
```

## `postgres17sample-data-full-02-procedures-11-common-system-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-11-common-system-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-11-common-system-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_poor_attendance_report
CREATE OR REPLACE FUNCTION sp_poor_attendance_report(
    p_year_month VARCHAR(7),
    p_department_id BIGINT
)
RETURNS TABLE(
    employee_id BIGINT,
    employee_name VARCHAR(100),
```

## `postgres17sample-data-full-02-procedures-12-enterprise-extension-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-12-enterprise-extension-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-12-enterprise-extension-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_post_stocktake
CREATE OR REPLACE PROCEDURE sp_post_stocktake(
    IN p_stocktake_id BIGINT,
    IN p_posted_by BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
```

## `postgres17sample-data-full-03-data-01-master-data-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/03-data/01-master-data.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-03-data-01-master-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统测试数据生成
-- 关系说明:
--   departments -> positions -> employees (1:N:N)
--   employees.manager_id 自引用形成汇报链
--   product_categories -> products -> product_batches (1:N:N)
--   suppliers -> supplier_products -> products (N:M)
--   warehouses -> inventory (1:N, 通过product_id+batch_id+warehouse_id唯一)
```

## `postgres17sample-data-full-03-data-02-supplementary-data-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/03-data/02-supplementary-data.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-03-data-02-supplementary-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统补充测试数据 - PostgreSQL 18
-- 目的: 对齐 MySQL 8.0 样例中已有但 PostgreSQL 初稿缺失的数据目标表
-- ============================================================

-- 促销与提成
INSERT INTO commission_rules (id, name, product_category_id, min_amount, max_amount, commission_rate, bonus, effective_date, status) VALUES
(1, '标准销售提成', NULL, 0.00, 99999999.99, 0.0300, 0.00, '2026-01-01', 'active');
```

## `postgres17sample-data-full-03-data-03-enterprise-extension-data-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/03-data/03-enterprise-extension-data.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-03-data-03-enterprise-extension-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP企业级扩展测试数据 - PostgreSQL 18
-- 覆盖: 多租户/账套、地址、税率、会计期间、收付款、
--       库存盘点/调拨/预留、工艺路线/工序、班次排班
-- ============================================================

INSERT INTO tenants (id, tenant_code, tenant_name, legal_entity_name, tax_no, status) VALUES
(1, 'T001', '华东运营主体', '上海华东智造商贸有限公司', '91310000MA1ERP001X', 'active'),
```

## `postgres17sample-data-full-04-queries-01-complex-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/01-complex-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-04-queries-01-complex-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统超复杂SQL查询集合 (PostgreSQL 18)
-- 覆盖: 多表JOIN, CTE(递归/非递归), 窗口函数, 嵌套子查询,
--       GROUP BY + HAVING, 复杂聚合组合, ROLLUP, UNION,
--       相关子查询, EXISTS, LATERAL, 条件聚合, 派生表
-- ============================================================

```

## `postgres17sample-data-full-04-queries-02-complex-queries-batch2-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/02-complex-queries-batch2.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-04-queries-02-complex-queries-batch2-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统超复杂SQL查询集合 - 第二批 (PostgreSQL 18)
-- 覆盖: 递归CTE, LATERAL JOIN, 窗口函数全系列,
--       GROUPING SETS/CUBE, UNION/INTERSECT/EXCEPT模拟,
--       相关子查询嵌套, 派生表多层嵌套, 条件聚合嵌套,
--       动态分桶, 漏斗分析, 同期群分析, 留存分析
-- ============================================================
```

## `postgres17sample-data-full-04-queries-03-complex-queries-batch3-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/03-complex-queries-batch3.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-04-queries-03-complex-queries-batch3-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第三批超复杂SQL查询: 客户消费分析 + 合同/税务/质检/项目/审批 (PostgreSQL 18)
-- 重点覆盖客户消费状态全方位分析
-- ============================================================


-- ============================================================
-- Q39: 客户消费分层全景图 - 五维消费画像
```

## `postgres17sample-data-full-04-queries-04-store-customer-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/04-store-customer-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-04-queries-04-store-customer-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第四批超复杂SQL查询: 门店/客户消费深度分析
-- 覆盖: 客户门店消费明细、门店畅销品、门店对比、
--        客户门店偏好、门店商品关联、门店销售预测
-- ============================================================


-- ============================================================
```

## `postgres17sample-data-full-04-queries-05-batch-expiry-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/05-batch-expiry-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-04-queries-05-batch-expiry-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第五批超复杂SQL: 批号保质期 + 类别销售/临期深度分析
-- 覆盖: 门店批号追踪、类别临期热力图、保质期预警、
--        类别动销对比、临期vs销售健康度、FIFO执行检查
-- ============================================================


-- ============================================================
```

## `postgres17sample-data-full-04-queries-06-return-damage-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/06-return-damage-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-04-queries-06-return-damage-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第六批超复杂SQL: 退货退款 + 报损分析 + 财务影响
-- ============================================================


-- ============================================================
-- Q68: 退货原因根因分析 - 按品类/门店/供应商交叉
-- 语法: CTE + 多维度交叉 + 原因占比 + 饼图数据
```

## `postgres17sample-data-full-04-queries-07-supplier-analysis-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/07-supplier-analysis-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-04-queries-07-supplier-analysis-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第七批超复杂SQL: 供应商地理分析 + 智能选择 + 对比
-- 覆盖: 供应商PK、地理距离优化、物流成本、退货率、综合评分
-- ============================================================


-- ============================================================
-- Q76: 全产品供应商覆盖率分析 - 哪些产品缺供应商
```

## `postgres17sample-data-full-04-queries-08-common-system-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/08-common-system-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-04-queries-08-common-system-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 常用系统查询 - 模拟真实ERP系统日常使用的SQL
-- 覆盖: 多表JOIN、员工/门店/商品/客户/订单/库存/财务
--       日常查询、审批待办、报表导出、数据核对等
-- ============================================================


-- ============================================================
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
- `VALUE:CONCAT_FORMAT:users.country_code,user_financial_snapshot.last_activity_time,user_financial_snapshot.net_cash_flow,transaction_ledgers.merchant_category->account_balances.compliance_notes`
- `VALUE:FUNCTION_CALL:account_balances.risk_flags->account_balances.risk_flags`

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
- `VALUE:CONCAT_FORMAT:users.country_code,user_financial_snapshot.last_activity_time,user_financial_snapshot.net_cash_flow,transaction_ledgers.merchant_category->account_balances.compliance_notes`
- `VALUE:FUNCTION_CALL:account_balances.risk_flags->account_balances.risk_flags`

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

- `CONTROL:CASE_WHEN:ledger_system_a.balance,ledger_system_b.balance->asset_balances.discrepancy_flag`
- `VALUE:COALESCE:ledger_system_a.balance,ledger_system_b.balance->asset_balances.computed_balance`
- `VALUE:DIRECT:staff_assignments.operator_name->asset_balances.last_checked_by`

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

- `VALUE:CONCAT_FORMAT:users.risk_level,orders.user_id,orders.amount->order_ledgers.remarks`

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

- `VALUE:CONCAT_FORMAT:users.risk_level,orders.user_id,orders.amount->order_ledgers.remarks`

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
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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
| Reason | local temporary table sources are excluded from Data Lineage v1 |
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

- `VALUE:ARITHMETIC:inventory.stock_reserved,order_items.quantity->inventory.stock_reserved`
- `VALUE:DIRECT:suppliers.supplier_name->inventory.last_ordered_from`

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

- `VALUE:ARITHMETIC:inventory.stock_reserved,order_items.quantity->inventory.stock_reserved`
- `VALUE:DIRECT:suppliers.supplier_name->inventory.last_ordered_from`

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

- `VALUE:ARITHMETIC:products.original_price->products.promo_price`

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

- `VALUE:ARITHMETIC:products.original_price->products.promo_price`

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

- `CONTROL:CASE_WHEN:orders.pay_amount->users.level`
- `VALUE:AGGREGATE:orders.pay_amount->users.total_spent`

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

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

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

- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved`

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

## `postgres18-edge-cases-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-edge-cases-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- SQL解析器边界测试用例
-- 目标: 各种tricky语法、边界条件、易混淆模式
-- 适用: 所有PostgreSQL版本
-- ============================================================================

-- ============================================================================
-- Part 1: 复杂JOIN语法 - 各种JOIN类型混合
```

## `postgres18-extreme-nesting-withrelation-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-extreme-nesting-withrelation-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- SQL解析器边界测试用例
-- 目标: 各种tricky语法、边界条件、易混淆模式
-- 适用: 所有PostgreSQL版本
-- ============================================================================

-- ============================================================================
-- Part 1: 复杂JOIN语法 - 各种JOIN类型混合
```

## `postgres18-extreme-nesting-withrelation-withlineage-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-extreme-nesting-withrelation-withlineage-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-extreme-nesting-withrelation-withlineage-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:orders.total_amount,order_items.extended_amount->orders.total_amount`
- `VALUE:CONCAT_FORMAT:customers.risk_level,orders.status->orders.risk_note`
- `VALUE:DIRECT:customers.country_code->orders.customer_country`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:orders.total_amount,order_items.extended_amount->orders.total_amount`
- `VALUE:CONCAT_FORMAT:customers.risk_level,orders.status->orders.risk_note`
- `VALUE:DIRECT:customers.country_code->orders.customer_country`

**Input Preview**

```sql
-- ============================================================================
-- SQL解析器边界测试用例
-- 目标: 各种tricky语法、边界条件、易混淆模式
-- 适用: 所有PostgreSQL版本
-- ============================================================================

-- ============================================================================
-- Part 1: 复杂JOIN语法 - 各种JOIN类型混合
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

- `VALUE:DIRECT:staging_orders.customer_id->orders.customer_id`

**Extractor Candidate Fingerprints**

- None

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

## `postgres18-pg10-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-pg10-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 10 复杂SQL测试用例
-- 特性: 声明式分区, Identity列, 逻辑复制, 并行查询增强, 哈希索引
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: 声明式分区 - 多层分区 + 子分区
```

## `postgres18-pg11-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-pg11-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 11 复杂SQL测试用例
-- 特性: 存储过程(CREATE PROCEDURE + 事务控制), 分区表增强(PRIMARY KEY,
--       DEFAULT分区, 自动索引), 哈希分区, 覆盖索引(INCLUDE)
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
```

## `postgres18-pg12-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-pg12-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 12 复杂SQL测试用例
-- 特性: 生成列, JSON_PATH, 物化CTE, 分区性能改进
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: 生成列与复杂表定义
```

## `postgres18-pg13-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-pg13-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 13 复杂SQL测试用例
-- 特性: 增量排序, 并行哈希连接, 分区改进, LATERAL增强
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: 增量排序与并行查询 (PG13优化器特性)
```

## `postgres18-pg14-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-pg14-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 14 复杂SQL测试用例
-- 特性: 多范围类型, JSON下标访问, 存储过程OUT参数, 扩展查询管道
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: 多范围类型 (multirange) 复杂操作
```

## `postgres18-pg15-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-pg15-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-pg15-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:pg15_inventory_target.quantity,pg15_inventory_source.total_qty_delta->pg15_inventory_target.quantity`
- `VALUE:ARITHMETIC:pg15_inventory_target.reserved,pg15_inventory_source.total_reserved_delta->pg15_inventory_target.reserved`
- `VALUE:ARITHMETIC:pg15_inventory_target.version->pg15_inventory_target.version`
- `VALUE:COALESCE:pg15_inventory_source.latest_cost,pg15_inventory_target.cost->pg15_inventory_target.cost`
- `VALUE:COALESCE:pg15_inventory_source.latest_cost->pg15_inventory_target.cost`
- `VALUE:COALESCE:pg15_inventory_source.latest_price,pg15_inventory_target.price->pg15_inventory_target.price`
- `VALUE:COALESCE:pg15_inventory_source.latest_price->pg15_inventory_target.price`
- `VALUE:COALESCE:pg15_inventory_source.merged_metadata->pg15_inventory_target.metadata`
- `VALUE:COALESCE:pg15_inventory_target.metadata,pg15_inventory_source.merged_metadata->pg15_inventory_target.metadata`
- `VALUE:CONCAT_FORMAT:pg15_inventory_target.metadata,pg15_inventory_source.validation_status,pg15_inventory_source.change_log,pg15_inventory_source.risk_metrics,pg15_inventory_source.latest_processed_at->pg15_inventory_target.metadata`
- `VALUE:DIRECT:pg15_inventory_source.latest_processed_at->pg15_inventory_target.last_updated`
- `VALUE:DIRECT:pg15_inventory_source.sku->pg15_inventory_target.sku`
- `VALUE:DIRECT:pg15_inventory_source.total_qty_delta->pg15_inventory_target.quantity`
- `VALUE:DIRECT:pg15_inventory_source.total_reserved_delta->pg15_inventory_target.reserved`
- `VALUE:DIRECT:pg15_inventory_source.warehouse_id->pg15_inventory_target.warehouse_id`
- `VALUE:FUNCTION_CALL:pg15_inventory_source.validation_status,pg15_inventory_source.change_log,pg15_inventory_source.risk_metrics->pg15_inventory_target.metadata`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 15 复杂SQL测试用例
-- 特性: MERGE语句, SQL/JSON函数(IS JSON, JSON_SCALAR, JSON_EXISTS等),
--       CLUSTER并行, 逻辑复制行过滤
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
```

## `postgres18-pg16-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-pg16-sql/input.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 16 复杂SQL测试用例
-- 特性: SQL/JSON构造函数(JSON_OBJECT, JSON_ARRAY, JSON_OBJECTAGG, JSON_ARRAYAGG),
--       IS JSON增强, 聚合函数增强, 并行哈希全连接
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
```

## `postgres18-pg17-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-pg17-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-pg17-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:pg17_product_catalog.version->pg17_product_catalog.version`
- `VALUE:COALESCE:pg17_price_updates.approver->pg17_product_catalog.updated_by`
- `VALUE:COALESCE:pg17_price_updates.attribute_updates->pg17_product_catalog.attributes`
- `VALUE:COALESCE:pg17_price_updates.stock_adjustment->pg17_product_catalog.stock_level`
- `VALUE:COALESCE:pg17_product_catalog.attributes,pg17_price_updates.attribute_updates->pg17_product_catalog.attributes`
- `VALUE:COALESCE:pg17_product_catalog.stock_level,pg17_price_updates.stock_adjustment->pg17_product_catalog.stock_level`
- `VALUE:CONCAT_FORMAT:pg17_price_updates.sku->pg17_product_catalog.name`
- `VALUE:DIRECT:pg17_price_updates.approver->pg17_product_catalog.updated_by`
- `VALUE:DIRECT:pg17_price_updates.new_price->pg17_product_catalog.base_price`
- `VALUE:DIRECT:pg17_price_updates.new_price->pg17_product_catalog.current_price`
- `VALUE:DIRECT:pg17_price_updates.sku->pg17_product_catalog.sku`

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================================
-- PostgreSQL 17 复杂SQL测试用例
-- 特性: JSON_TABLE, MERGE增强 (RETURNING, 多动作), 增量JSON解析,
--       COPY性能改进, 系统信息函数增强
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
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

- `VALUE:ARITHMETIC:account_balances.balance,transaction_ledgers.amount->account_balances.balance`

**Input Preview**

```sql
-- PostgreSQL 18 RETURNING old/new: pseudo row values must not become physical tables.
UPDATE account_balances ab
SET balance = ab.balance + tx.amount
FROM transaction_ledgers tx
WHERE ab.user_id = tx.user_id
RETURNING old.balance AS previous_balance, new.balance AS updated_balance, tx.amount;
```

## `postgres18-sample-data-enterprise-extension-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/10-enterprise-extension-queries.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP企业级扩展分析查询 - PostgreSQL 18
-- 覆盖: 盘点差异、调拨履约、收付款核销、会计期间、
--       工艺路线、地址与税率
-- ============================================================

-- Q1: 库存盘点差异分析
SELECT
```

## `postgres18-sample-data-enterprise-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/sample-data-enterprise-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-enterprise-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Input Preview**

```sql
-- relation-detector-fixture-source: PROCEDURE:sample_data.sp_post_stocktake
CREATE OR REPLACE PROCEDURE sp_post_stocktake(
    IN p_stocktake_id BIGINT,
    IN p_posted_by BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
```

## `postgres18-sample-data-pg18-specific-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/11-pg18-specific.sql` |
| Expected lineage | None |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- PostgreSQL 18 专属语法样例
-- 说明:
--   这些 SQL 用来覆盖 PostgreSQL 18 版本能力，不参与 MySQL 8.0 业务对齐。
--   可在 PostgreSQL 18 环境中单独执行。
-- ============================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;
```

## `postgres18-sample-data-real-world-scenarios-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/09-real-world-scenarios.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-real-world-scenarios-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统真实业务场景SQL查询 - 第九批
-- 覆盖: Procure-to-Pay全链路、Order-to-Cash全链路、
--       产品真实利润、员工人效、库存持有成本、资金周转周期、
--       信用风险监控、批号全链路追溯、毛利瀑布、预算滚动预测、
--       供应商集中度风险、月度关账核对、需求预测准确率、
--       仓库库容利用率、提成核对、价格弹性分析
-- ============================================================
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

- None

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

## `postgres18sample-data-full-01-schema-02-indexes-and-views-views-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `VIEW` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-01-schema-02-indexes-and-views-views-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-01-schema-02-indexes-and-views-views-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: VIEW:public.v_employee_full
SELECT
    e.id,
    e.employee_no,
    e.name,
    e.gender,
    e.phone,
    e.email,
```

## `postgres18sample-data-full-01-schema-03-triggers-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `TRIGGER` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-01-schema-03-triggers-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-01-schema-03-triggers-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:inventory.quantity,sales_order_items.quantity->inventory.quantity`
- `VALUE:DIRECT:sales_order_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:sales_order_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:sales_order_items.quantity->inventory_transactions.quantity_change`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:inventory.quantity,sales_order_items.quantity->inventory.quantity`
- `VALUE:DIRECT:sales_order_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:sales_order_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:sales_order_items.quantity->inventory_transactions.quantity_change`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.trg_audit_employee_insert
CREATE OR REPLACE FUNCTION trg_audit_employee_insert() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO audit_log (employee_id, action, target_type, target_id, new_value)
    VALUES (NEW.id, 'INSERT', 'employee', NEW.id,
            jsonb_build_object('name', NEW.name, 'employee_no', NEW.employee_no,
                       'department_id', NEW.department_id, 'salary', NEW.salary, 'status', NEW.status));
    RETURN NEW;
```

## `postgres18sample-data-full-02-procedures-01-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-01-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-01-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.generate_employee_no
CREATE OR REPLACE FUNCTION generate_employee_no()
RETURNS VARCHAR(20) AS $$
BEGIN
    RETURN TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || LPAD((FLOOR(RANDOM() * 9999) + 1)::TEXT, 4, '0');
END;
$$ LANGUAGE plpgsql;
```

## `postgres18sample-data-full-02-procedures-02-procedures-supplement-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-02-procedures-supplement-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-02-procedures-supplement-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_transfer_inventory
CREATE OR REPLACE PROCEDURE sp_transfer_inventory(
    IN p_product_id BIGINT,
    IN p_batch_id BIGINT,
    IN p_from_warehouse_id BIGINT,
    IN p_to_warehouse_id BIGINT,
    IN p_quantity INT,
    IN p_operator_id BIGINT,
```

## `postgres18sample-data-full-02-procedures-03-functions-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-03-functions-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-03-functions-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.fn_employee_full_name
CREATE OR REPLACE FUNCTION fn_employee_full_name(p_employee_id BIGINT)
RETURNS VARCHAR(100)
STABLE
LANGUAGE plpgsql
AS $$
DECLARE
    v_result VARCHAR(100);
```

## `postgres18sample-data-full-02-procedures-04-procedures-supplement-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-04-procedures-supplement-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-04-procedures-supplement-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:sales_commissions.bonus->sales_commissions.bonus`
- `VALUE:ARITHMETIC:sales_commissions.commission_amount,sales_commissions.base_amount->sales_commissions.commission_amount`
- `VALUE:COALESCE:sales_order_items.amount->sales_commissions.commission_amount`
- `VALUE:DIRECT:sales_order_items.amount->sales_commissions.base_amount`
- `VALUE:DIRECT:sales_order_items.id->sales_commissions.order_item_id`
- `VALUE:DIRECT:sales_orders.id->sales_commissions.order_id`
- `VALUE:DIRECT:sales_orders.salesperson_id->sales_commissions.employee_id`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:sales_commissions.bonus->sales_commissions.bonus`
- `VALUE:ARITHMETIC:sales_commissions.commission_amount,sales_commissions.base_amount->sales_commissions.commission_amount`
- `VALUE:COALESCE:sales_order_items.amount->sales_commissions.commission_amount`
- `VALUE:DIRECT:sales_order_items.amount->sales_commissions.base_amount`
- `VALUE:DIRECT:sales_order_items.id->sales_commissions.order_item_id`
- `VALUE:DIRECT:sales_orders.id->sales_commissions.order_id`
- `VALUE:DIRECT:sales_orders.salesperson_id->sales_commissions.employee_id`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_create_shipment
CREATE OR REPLACE PROCEDURE sp_create_shipment(
    IN p_order_id BIGINT,
    IN p_carrier VARCHAR(100),
    IN p_shipping_method VARCHAR(20),
    IN p_shipping_fee DECIMAL(12,2),
    IN p_to_address VARCHAR(300),
    IN p_receiver_name VARCHAR(50),
```

## `postgres18sample-data-full-02-procedures-05-third-batch-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-05-third-batch-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-05-third-batch-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_generate_ar_aging
CREATE OR REPLACE PROCEDURE sp_generate_ar_aging()
LANGUAGE plpgsql
AS $$
BEGIN
    -- 清理当天快照
    DELETE FROM ar_aging_snapshots WHERE snapshot_date = CURRENT_DATE;
```

## `postgres18sample-data-full-02-procedures-06-third-batch-functions-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-06-third-batch-functions-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-06-third-batch-functions-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.fn_get_customer_clv
CREATE OR REPLACE FUNCTION fn_get_customer_clv(
    p_customer_id BIGINT
)
RETURNS DECIMAL(18,2)
STABLE
LANGUAGE plpgsql
AS $$
```

## `postgres18sample-data-full-02-procedures-07-store-customer-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-07-store-customer-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-07-store-customer-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_customer_store_purchase_history
CREATE OR REPLACE FUNCTION sp_customer_store_purchase_history(
    p_customer_id BIGINT,
    p_start_date DATE,
    p_end_date DATE
)
RETURNS TABLE(
    purchase_date DATE,
```

## `postgres18sample-data-full-02-procedures-08-batch-expiry-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-08-batch-expiry-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-08-batch-expiry-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_batch_expiry_tracking
CREATE OR REPLACE FUNCTION sp_batch_expiry_tracking(
    p_role TEXT,
    p_user_id BIGINT,
    p_warehouse_id BIGINT,
    p_expiry_days INTEGER
)
RETURNS TABLE(
```

## `postgres18sample-data-full-02-procedures-09-return-refund-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-09-return-refund-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-09-return-refund-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_approve_sales_return
CREATE OR REPLACE PROCEDURE sp_approve_sales_return(
    IN p_return_id BIGINT,
    IN p_approved BOOLEAN,
    IN p_approver_id BIGINT,
    IN p_approval_comment VARCHAR(500),
    OUT p_result TEXT
)
```

## `postgres18sample-data-full-02-procedures-10-supplier-geo-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-10-supplier-geo-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-10-supplier-geo-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.fn_haversine_distance
CREATE OR REPLACE FUNCTION fn_haversine_distance(
    p_lat1 NUMERIC(10,7),
    p_lon1 NUMERIC(10,7),
    p_lat2 NUMERIC(10,7),
    p_lon2 NUMERIC(10,7)
)
RETURNS NUMERIC(10,2)
```

## `postgres18sample-data-full-02-procedures-11-common-system-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-11-common-system-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-11-common-system-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_poor_attendance_report
CREATE OR REPLACE FUNCTION sp_poor_attendance_report(
    p_year_month VARCHAR(7),
    p_department_id BIGINT
)
RETURNS TABLE(
    employee_id BIGINT,
    employee_name VARCHAR(100),
```

## `postgres18sample-data-full-02-procedures-12-enterprise-extension-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-12-enterprise-extension-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-12-enterprise-extension-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_post_stocktake
CREATE OR REPLACE PROCEDURE sp_post_stocktake(
    IN p_stocktake_id BIGINT,
    IN p_posted_by BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
```

## `postgres18sample-data-full-03-data-01-master-data-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/03-data/01-master-data.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-03-data-01-master-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统测试数据生成
-- 关系说明:
--   departments -> positions -> employees (1:N:N)
--   employees.manager_id 自引用形成汇报链
--   product_categories -> products -> product_batches (1:N:N)
--   suppliers -> supplier_products -> products (N:M)
--   warehouses -> inventory (1:N, 通过product_id+batch_id+warehouse_id唯一)
```

## `postgres18sample-data-full-03-data-02-supplementary-data-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/03-data/02-supplementary-data.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-03-data-02-supplementary-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统补充测试数据 - PostgreSQL 18
-- 目的: 对齐 MySQL 8.0 样例中已有但 PostgreSQL 初稿缺失的数据目标表
-- ============================================================

-- 促销与提成
INSERT INTO commission_rules (id, name, product_category_id, min_amount, max_amount, commission_rate, bonus, effective_date, status) VALUES
(1, '标准销售提成', NULL, 0.00, 99999999.99, 0.0300, 0.00, '2026-01-01', 'active');
```

## `postgres18sample-data-full-03-data-03-enterprise-extension-data-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/03-data/03-enterprise-extension-data.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-03-data-03-enterprise-extension-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP企业级扩展测试数据 - PostgreSQL 18
-- 覆盖: 多租户/账套、地址、税率、会计期间、收付款、
--       库存盘点/调拨/预留、工艺路线/工序、班次排班
-- ============================================================

INSERT INTO tenants (id, tenant_code, tenant_name, legal_entity_name, tax_no, status) VALUES
(1, 'T001', '华东运营主体', '上海华东智造商贸有限公司', '91310000MA1ERP001X', 'active'),
```

## `postgres18sample-data-full-04-queries-01-complex-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/01-complex-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-04-queries-01-complex-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统超复杂SQL查询集合 (PostgreSQL 18)
-- 覆盖: 多表JOIN, CTE(递归/非递归), 窗口函数, 嵌套子查询,
--       GROUP BY + HAVING, 复杂聚合组合, ROLLUP, UNION,
--       相关子查询, EXISTS, LATERAL, 条件聚合, 派生表
-- ============================================================

```

## `postgres18sample-data-full-04-queries-02-complex-queries-batch2-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/02-complex-queries-batch2.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-04-queries-02-complex-queries-batch2-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统超复杂SQL查询集合 - 第二批 (PostgreSQL 18)
-- 覆盖: 递归CTE, LATERAL JOIN, 窗口函数全系列,
--       GROUPING SETS/CUBE, UNION/INTERSECT/EXCEPT模拟,
--       相关子查询嵌套, 派生表多层嵌套, 条件聚合嵌套,
--       动态分桶, 漏斗分析, 同期群分析, 留存分析
-- ============================================================
```

## `postgres18sample-data-full-04-queries-03-complex-queries-batch3-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/03-complex-queries-batch3.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-04-queries-03-complex-queries-batch3-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第三批超复杂SQL查询: 客户消费分析 + 合同/税务/质检/项目/审批 (PostgreSQL 18)
-- 重点覆盖客户消费状态全方位分析
-- ============================================================


-- ============================================================
-- Q39: 客户消费分层全景图 - 五维消费画像
```

## `postgres18sample-data-full-04-queries-04-store-customer-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/04-store-customer-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-04-queries-04-store-customer-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第四批超复杂SQL查询: 门店/客户消费深度分析
-- 覆盖: 客户门店消费明细、门店畅销品、门店对比、
--        客户门店偏好、门店商品关联、门店销售预测
-- ============================================================


-- ============================================================
```

## `postgres18sample-data-full-04-queries-05-batch-expiry-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/05-batch-expiry-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-04-queries-05-batch-expiry-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第五批超复杂SQL: 批号保质期 + 类别销售/临期深度分析
-- 覆盖: 门店批号追踪、类别临期热力图、保质期预警、
--        类别动销对比、临期vs销售健康度、FIFO执行检查
-- ============================================================


-- ============================================================
```

## `postgres18sample-data-full-04-queries-06-return-damage-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/06-return-damage-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-04-queries-06-return-damage-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第六批超复杂SQL: 退货退款 + 报损分析 + 财务影响
-- ============================================================


-- ============================================================
-- Q68: 退货原因根因分析 - 按品类/门店/供应商交叉
-- 语法: CTE + 多维度交叉 + 原因占比 + 饼图数据
```

## `postgres18sample-data-full-04-queries-07-supplier-analysis-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/07-supplier-analysis-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-04-queries-07-supplier-analysis-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第七批超复杂SQL: 供应商地理分析 + 智能选择 + 对比
-- 覆盖: 供应商PK、地理距离优化、物流成本、退货率、综合评分
-- ============================================================


-- ============================================================
-- Q76: 全产品供应商覆盖率分析 - 哪些产品缺供应商
```

## `postgres18sample-data-full-04-queries-08-common-system-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/08-common-system-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-04-queries-08-common-system-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 常用系统查询 - 模拟真实ERP系统日常使用的SQL
-- 覆盖: 多表JOIN、员工/门店/商品/客户/订单/库存/财务
--       日常查询、审批待办、报表导出、数据核对等
-- ============================================================


-- ============================================================
```

## `postgressample-data-full-01-schema-02-indexes-and-views-views-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `VIEW` |
| Input | `test-fixtures/correctness/postgres/postgres-sample-data-full-01-schema-02-indexes-and-views-views-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-01-schema-02-indexes-and-views-views-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: VIEW:public.v_employee_full
SELECT
    e.id,
    e.employee_no,
    e.name,
    e.gender,
    e.phone,
    e.email,
```

## `postgressample-data-full-01-schema-03-triggers-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `TRIGGER` |
| Input | `test-fixtures/correctness/postgres/postgres-sample-data-full-01-schema-03-triggers-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-01-schema-03-triggers-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:inventory.quantity,sales_order_items.quantity->inventory.quantity`
- `VALUE:DIRECT:sales_order_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:sales_order_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:sales_order_items.quantity->inventory_transactions.quantity_change`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:inventory.quantity,sales_order_items.quantity->inventory.quantity`
- `VALUE:DIRECT:sales_order_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:sales_order_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:sales_order_items.quantity->inventory_transactions.quantity_change`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.trg_audit_employee_insert
CREATE OR REPLACE FUNCTION trg_audit_employee_insert() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO audit_log (employee_id, action, target_type, target_id, new_value)
    VALUES (NEW.id, 'INSERT', 'employee', NEW.id,
            jsonb_build_object('name', NEW.name, 'employee_no', NEW.employee_no,
                       'department_id', NEW.department_id, 'salary', NEW.salary, 'status', NEW.status));
    RETURN NEW;
```

## `postgressample-data-full-02-procedures-01-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-01-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-01-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.generate_employee_no
CREATE OR REPLACE FUNCTION generate_employee_no()
RETURNS VARCHAR(20) AS $$
BEGIN
    RETURN TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || LPAD((FLOOR(RANDOM() * 9999) + 1)::TEXT, 4, '0');
END;
$$ LANGUAGE plpgsql;
```

## `postgressample-data-full-02-procedures-02-procedures-supplement-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-02-procedures-supplement-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-02-procedures-supplement-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_transfer_inventory
CREATE OR REPLACE PROCEDURE sp_transfer_inventory(
    IN p_product_id BIGINT,
    IN p_batch_id BIGINT,
    IN p_from_warehouse_id BIGINT,
    IN p_to_warehouse_id BIGINT,
    IN p_quantity INT,
    IN p_operator_id BIGINT,
```

## `postgressample-data-full-02-procedures-03-functions-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-03-functions-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-03-functions-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.fn_employee_full_name
CREATE OR REPLACE FUNCTION fn_employee_full_name(p_employee_id BIGINT)
RETURNS VARCHAR(100)
STABLE
LANGUAGE plpgsql
AS $$
DECLARE
    v_result VARCHAR(100);
```

## `postgressample-data-full-02-procedures-04-procedures-supplement-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-04-procedures-supplement-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-04-procedures-supplement-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:sales_commissions.bonus->sales_commissions.bonus`
- `VALUE:ARITHMETIC:sales_commissions.commission_amount,sales_commissions.base_amount->sales_commissions.commission_amount`
- `VALUE:COALESCE:sales_order_items.amount->sales_commissions.commission_amount`
- `VALUE:DIRECT:sales_order_items.amount->sales_commissions.base_amount`
- `VALUE:DIRECT:sales_order_items.id->sales_commissions.order_item_id`
- `VALUE:DIRECT:sales_orders.id->sales_commissions.order_id`
- `VALUE:DIRECT:sales_orders.salesperson_id->sales_commissions.employee_id`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:sales_commissions.bonus->sales_commissions.bonus`
- `VALUE:ARITHMETIC:sales_commissions.commission_amount,sales_commissions.base_amount->sales_commissions.commission_amount`
- `VALUE:COALESCE:sales_order_items.amount->sales_commissions.commission_amount`
- `VALUE:DIRECT:sales_order_items.amount->sales_commissions.base_amount`
- `VALUE:DIRECT:sales_order_items.id->sales_commissions.order_item_id`
- `VALUE:DIRECT:sales_orders.id->sales_commissions.order_id`
- `VALUE:DIRECT:sales_orders.salesperson_id->sales_commissions.employee_id`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_create_shipment
CREATE OR REPLACE PROCEDURE sp_create_shipment(
    IN p_order_id BIGINT,
    IN p_carrier VARCHAR(100),
    IN p_shipping_method VARCHAR(20),
    IN p_shipping_fee DECIMAL(12,2),
    IN p_to_address VARCHAR(300),
    IN p_receiver_name VARCHAR(50),
```

## `postgressample-data-full-02-procedures-05-third-batch-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-05-third-batch-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-05-third-batch-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_generate_ar_aging
CREATE OR REPLACE PROCEDURE sp_generate_ar_aging()
LANGUAGE plpgsql
AS $$
BEGIN
    -- 清理当天快照
    DELETE FROM ar_aging_snapshots WHERE snapshot_date = CURRENT_DATE;
```

## `postgressample-data-full-02-procedures-06-third-batch-functions-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `FUNCTION` |
| Input | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-06-third-batch-functions-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-06-third-batch-functions-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.fn_get_customer_clv
CREATE OR REPLACE FUNCTION fn_get_customer_clv(
    p_customer_id BIGINT
)
RETURNS DECIMAL(18,2)
STABLE
LANGUAGE plpgsql
AS $$
```

## `postgressample-data-full-02-procedures-07-store-customer-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-07-store-customer-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-07-store-customer-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_customer_store_purchase_history
CREATE OR REPLACE FUNCTION sp_customer_store_purchase_history(
    p_customer_id BIGINT,
    p_start_date DATE,
    p_end_date DATE
)
RETURNS TABLE(
    purchase_date DATE,
```

## `postgressample-data-full-02-procedures-08-batch-expiry-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-08-batch-expiry-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-08-batch-expiry-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_batch_expiry_tracking
CREATE OR REPLACE FUNCTION sp_batch_expiry_tracking(
    p_role TEXT,
    p_user_id BIGINT,
    p_warehouse_id BIGINT,
    p_expiry_days INTEGER
)
RETURNS TABLE(
```

## `postgressample-data-full-02-procedures-09-return-refund-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-09-return-refund-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-09-return-refund-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_approve_sales_return
CREATE OR REPLACE PROCEDURE sp_approve_sales_return(
    IN p_return_id BIGINT,
    IN p_approved BOOLEAN,
    IN p_approver_id BIGINT,
    IN p_approval_comment VARCHAR(500),
    OUT p_result TEXT
)
```

## `postgressample-data-full-02-procedures-10-supplier-geo-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-10-supplier-geo-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-10-supplier-geo-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.fn_haversine_distance
CREATE OR REPLACE FUNCTION fn_haversine_distance(
    p_lat1 NUMERIC(10,7),
    p_lon1 NUMERIC(10,7),
    p_lat2 NUMERIC(10,7),
    p_lon2 NUMERIC(10,7)
)
RETURNS NUMERIC(10,2)
```

## `postgressample-data-full-02-procedures-11-common-system-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-11-common-system-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-11-common-system-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_poor_attendance_report
CREATE OR REPLACE FUNCTION sp_poor_attendance_report(
    p_year_month VARCHAR(7),
    p_department_id BIGINT
)
RETURNS TABLE(
    employee_id BIGINT,
    employee_name VARCHAR(100),
```

## `postgressample-data-full-02-procedures-12-enterprise-extension-procedures-sql`

| Field | Value |
| --- | --- |
| Classification | `EXISTING_GOLD` |
| Reason | fixture already has expected-lineage.json |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PROCEDURE` |
| Input | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-12-enterprise-extension-procedures-sql/input.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-12-enterprise-extension-procedures-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Extractor Candidate Fingerprints**

- `VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change`
- `VALUE:CONCAT_FORMAT:stocktakes.stocktake_no->inventory_transactions.remark`
- `VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty`
- `VALUE:DIRECT:stocktake_items.batch_id->inventory_transactions.batch_id`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity`
- `VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty`
- `VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id`
- `VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date`

**Input Preview**

```sql
-- relation-detector-fixture-source: ROUTINE:public.sp_post_stocktake
CREATE OR REPLACE PROCEDURE sp_post_stocktake(
    IN p_stocktake_id BIGINT,
    IN p_posted_by BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
```

## `postgressample-data-full-03-data-01-master-data-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/03-data/01-master-data.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-03-data-01-master-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统测试数据生成
-- 关系说明:
--   departments -> positions -> employees (1:N:N)
--   employees.manager_id 自引用形成汇报链
--   product_categories -> products -> product_batches (1:N:N)
--   suppliers -> supplier_products -> products (N:M)
--   warehouses -> inventory (1:N, 通过product_id+batch_id+warehouse_id唯一)
```

## `postgressample-data-full-03-data-02-supplementary-data-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/03-data/02-supplementary-data.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-03-data-02-supplementary-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统补充测试数据 - PostgreSQL 18
-- 目的: 对齐 MySQL 8.0 样例中已有但 PostgreSQL 初稿缺失的数据目标表
-- ============================================================

-- 促销与提成
INSERT INTO commission_rules (id, name, product_category_id, min_amount, max_amount, commission_rate, bonus, effective_date, status) VALUES
(1, '标准销售提成', NULL, 0.00, 99999999.99, 0.0300, 0.00, '2026-01-01', 'active');
```

## `postgressample-data-full-03-data-03-enterprise-extension-data-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | write statement has no physical table.column source in Data Lineage v1 |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/03-data/03-enterprise-extension-data.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-03-data-03-enterprise-extension-data-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP企业级扩展测试数据 - PostgreSQL 18
-- 覆盖: 多租户/账套、地址、税率、会计期间、收付款、
--       库存盘点/调拨/预留、工艺路线/工序、班次排班
-- ============================================================

INSERT INTO tenants (id, tenant_code, tenant_name, legal_entity_name, tax_no, status) VALUES
(1, 'T001', '华东运营主体', '上海华东智造商贸有限公司', '91310000MA1ERP001X', 'active'),
```

## `postgressample-data-full-04-queries-01-complex-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/01-complex-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-04-queries-01-complex-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统超复杂SQL查询集合 (PostgreSQL 18)
-- 覆盖: 多表JOIN, CTE(递归/非递归), 窗口函数, 嵌套子查询,
--       GROUP BY + HAVING, 复杂聚合组合, ROLLUP, UNION,
--       相关子查询, EXISTS, LATERAL, 条件聚合, 派生表
-- ============================================================

```

## `postgressample-data-full-04-queries-02-complex-queries-batch2-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/02-complex-queries-batch2.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-04-queries-02-complex-queries-batch2-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- ERP系统超复杂SQL查询集合 - 第二批 (PostgreSQL 18)
-- 覆盖: 递归CTE, LATERAL JOIN, 窗口函数全系列,
--       GROUPING SETS/CUBE, UNION/INTERSECT/EXCEPT模拟,
--       相关子查询嵌套, 派生表多层嵌套, 条件聚合嵌套,
--       动态分桶, 漏斗分析, 同期群分析, 留存分析
-- ============================================================
```

## `postgressample-data-full-04-queries-03-complex-queries-batch3-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/03-complex-queries-batch3.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-04-queries-03-complex-queries-batch3-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第三批超复杂SQL查询: 客户消费分析 + 合同/税务/质检/项目/审批 (PostgreSQL 18)
-- 重点覆盖客户消费状态全方位分析
-- ============================================================


-- ============================================================
-- Q39: 客户消费分层全景图 - 五维消费画像
```

## `postgressample-data-full-04-queries-04-store-customer-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/04-store-customer-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-04-queries-04-store-customer-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第四批超复杂SQL查询: 门店/客户消费深度分析
-- 覆盖: 客户门店消费明细、门店畅销品、门店对比、
--        客户门店偏好、门店商品关联、门店销售预测
-- ============================================================


-- ============================================================
```

## `postgressample-data-full-04-queries-05-batch-expiry-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/05-batch-expiry-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-04-queries-05-batch-expiry-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第五批超复杂SQL: 批号保质期 + 类别销售/临期深度分析
-- 覆盖: 门店批号追踪、类别临期热力图、保质期预警、
--        类别动销对比、临期vs销售健康度、FIFO执行检查
-- ============================================================


-- ============================================================
```

## `postgressample-data-full-04-queries-06-return-damage-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/06-return-damage-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-04-queries-06-return-damage-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第六批超复杂SQL: 退货退款 + 报损分析 + 财务影响
-- ============================================================


-- ============================================================
-- Q68: 退货原因根因分析 - 按品类/门店/供应商交叉
-- 语法: CTE + 多维度交叉 + 原因占比 + 饼图数据
```

## `postgressample-data-full-04-queries-07-supplier-analysis-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/07-supplier-analysis-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-04-queries-07-supplier-analysis-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 第七批超复杂SQL: 供应商地理分析 + 智能选择 + 对比
-- 覆盖: 供应商PK、地理距离优化、物流成本、退货率、综合评分
-- ============================================================


-- ============================================================
-- Q76: 全产品供应商覆盖率分析 - 哪些产品缺供应商
```

## `postgressample-data-full-04-queries-08-common-system-queries-sql`

| Field | Value |
| --- | --- |
| Classification | `NOT_APPLICABLE` |
| Reason | no UPDATE, INSERT SELECT, or MERGE target column write |
| Database | `POSTGRESQL` |
| Parser target | `SQL` |
| Source type | `PLAIN_SQL` |
| Input | `sample-data/postgres/18/04-queries/08-common-system-queries.sql` |
| Expected lineage | `test-fixtures/correctness/postgres/postgres-sample-data-full-04-queries-08-common-system-queries-sql/expected-lineage.json` |

**Expected Lineage Fingerprints**

- None

**Extractor Candidate Fingerprints**

- None

**Input Preview**

```sql
-- ============================================================
-- 常用系统查询 - 模拟真实ERP系统日常使用的SQL
-- 覆盖: 多表JOIN、员工/门店/商品/客户/订单/库存/财务
--       日常查询、审批待办、报表导出、数据核对等
-- ============================================================


-- ============================================================
```

