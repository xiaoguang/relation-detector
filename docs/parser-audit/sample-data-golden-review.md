# sample-data Golden 审计记录

本文记录将 `sample-data` ERP 样例 SQL 纳入 relation-detection correctness golden 时的审计结论。目标是验证样例 SQL 的 DDL relationship、SQL relationship 和 Data Lineage，同时避免把不合法方言 SQL 或 parser 误判静默固化进 golden。

## 覆盖范围

本轮采用精选业务切片，共 28 个 fixture：

| 分组 | Fixture 数 | Parser 模式 | 覆盖内容 |
| --- | ---: | --- | --- |
| MySQL root | 4 | `token-event` | enterprise extension DDL、企业查询、真实业务场景查询、3 个过程体 |
| MySQL v8_0 | 4 | `full-grammer`, `mysql/8.0` | 与 MySQL root 同源 SQL 的版本化 full-grammer golden |
| PostgreSQL root | 5 | `token-event` | enterprise extension DDL、企业查询、真实业务场景查询、3 个过程体、PG18-only 样例 |
| PostgreSQL v16 | 5 | `full-grammer`, `postgresql/16` | 通用 PostgreSQL SQL 正向；PG18-only 作为版本边界诊断 |
| PostgreSQL v17 | 5 | `full-grammer`, `postgresql/17` | 通用 PostgreSQL SQL 正向；PG18-only 作为版本边界诊断 |
| PostgreSQL v18 | 5 | `full-grammer`, `postgresql/18` | 通用 PostgreSQL SQL 正向；PG18-only 正向 |

## 人工语义判断

### MySQL enterprise extension DDL

DDL 中的 FK、PK、UNIQUE / INDEX 关系均来自明确 `CREATE TABLE` / `ALTER TABLE` 结构。root token-event 与 MySQL v8_0 full-grammer 均输出 31 条 relationship，结果一致。

### MySQL enterprise extension queries

查询中的关系来自显式 `JOIN ... ON`、派生表 join、CTE/derived projection 回溯。root token-event 与 MySQL v8_0 full-grammer 均输出 17 条 relationship，结果一致。

### MySQL enterprise procedures

`sp_post_stocktake` 中：

```sql
INSERT INTO inventory_transactions (..., quantity_change, before_qty, after_qty, ...)
SELECT
    ...,
    sti.counted_quantity - i.quantity,
    i.quantity,
    sti.counted_quantity,
    ...
FROM stocktake_items sti
JOIN inventory i ON i.product_id = sti.product_id
...
```

`quantity_change` 的来源应同时包含 `stocktake_items.counted_quantity` 和 `inventory.quantity`，transform 为 `ARITHMETIC`。此前 token-event 只取了 `INSERT ... SELECT` projection 的第一个 source column，已修复为读取完整 source 集合。

过程参数如 `p_stocktake_id`、`p_posted_by` 不是数据库内部字段来源。token-event 已按 `CREATE PROCEDURE/FUNCTION (...)` 参数声明结构识别 routine parameter，并排除其作为物理列 source；该判断基于语法位置和 `IN/OUT/INOUT` 关键字，不基于 `p_` 名字规则。

修复后 root token-event 与 MySQL v8_0 full-grammer 均输出 3 条 relationship、8 条 lineage，结果一致。

### MySQL real-world scenarios

`Q104` 批号追溯查询中，原 SQL 的多个 `GROUP_CONCAT(DISTINCT CONCAT(...))` 子查询括号不符合 MySQL 8.0 写法。本轮先修正 sample SQL，不修改 parser：

```sql
SELECT GROUP_CONCAT(DISTINCT CONCAT(...) SEPARATOR ', ')
FROM ...
WHERE ...
```

修正后，批次相关关系来自明确 `JOIN` 和 correlated subquery。另一个明确关系来自：

```sql
SELECT MAX(journal_date)
FROM cashier_journals
WHERE reference_type = 'sales_order'
  AND reference_id IN (
      SELECT id
      FROM sales_orders
      WHERE customer_id = c.id
  )
```

这表示 `cashier_journals.reference_id` 与 `sales_orders.id` 的 `SQL_LOG_SUBQUERY_IN` 关系。MySQL v8_0 full-grammer 之前因先访问 nested subquery rowset，再生成 `IN_SUBQUERY_PREDICATE`，导致外层未限定列 `reference_id` 的默认 rowset 绑定失败。已调整 MySQL typed visitor：在当前 `PredicateContext` 先生成 IN-subquery event，再递归访问子查询。

该修复基于 typed parse-tree context 和作用域时序，不使用正则、表名/列名白名单或特殊名字过滤。

修复后 root token-event 与 MySQL v8_0 full-grammer 均输出 53 条 relationship，结果一致。

`Q113` 门店综合业绩查询中，CTE `store_inventory` 原写法在多表 SELECT 内使用未限定裸列：

```sql
SELECT warehouse_id, SUM(quantity * p.purchase_price)
FROM inventory i
JOIN products p ON i.product_id = p.id
GROUP BY warehouse_id
```

该 SQL 对真实数据库可能因 schema 唯一性而可执行，但 relation-detection 不应在没有 metadata 的情况下猜测裸列属于第一个表。本轮将样例 SQL 改为明确限定物理来源：

```sql
SELECT i.warehouse_id, SUM(i.quantity * p.purchase_price)
FROM inventory i
JOIN products p ON i.product_id = p.id
GROUP BY i.warehouse_id
```

这不改变业务含义，也不修改 parser 规则；它让 token-event 与 full-grammer 都能基于明确 SQL 结构回溯 `store_inventory.warehouse_id -> inventory.warehouse_id`。

### PostgreSQL 通用样例

PostgreSQL root token-event 与 v16/v17/v18 full-grammer 在以下通用样例上完全一致：

- enterprise extension DDL：31 条 relationship
- enterprise extension queries：17 条 relationship
- enterprise procedures：0 条 relationship / 0 条 lineage
- real-world scenarios：53 条 relationship

未发现 literal、LIKE、参数、关键字、临时变量或 `old/new` pseudo rowset 被误识别为物理表字段关系。

### PostgreSQL 18-only 样例

PG18-only 样例在 root token-event 和 PostgreSQL v18 full-grammer 下均为正向解析，当前不产生 relationship / lineage。

PostgreSQL v16/v17 full-grammer 对同一 PG18-only 样例输出：

```text
FULL_GRAMMAR_VERSION_UNSUPPORTED_SYNTAX: 4
```

这是预期版本边界：低版本 full-grammer 不应静默接受 PG18-only 语法。

## 最终对比矩阵

| 对比 | Relationship 差异 | Lineage 差异 | Warning 差异 | 结论 |
| --- | ---: | ---: | --- | --- |
| MySQL root vs MySQL v8_0 DDL | 0 | 0 | 无 | 一致 |
| MySQL root vs MySQL v8_0 enterprise queries | 0 | 0 | 无 | 一致 |
| MySQL root vs MySQL v8_0 procedures | 0 | 0 | 无 | 一致 |
| MySQL root vs MySQL v8_0 real-world scenarios | 0 | 0 | 无 | 一致 |
| PostgreSQL root vs v16/v17/v18 通用样例 | 0 | 0 | 无 | 一致 |
| PostgreSQL PG18-only root vs v18 | 0 | 0 | 无 | 一致 |
| PostgreSQL PG18-only root vs v16/v17 | 0 | 0 | v16/v17 有版本不支持诊断 | 预期版本边界 |

## 处理项

| 类型 | SQL 上下文 | 处理 |
| --- | --- | --- |
| 样例 SQL 方言修正 | MySQL Q104 `GROUP_CONCAT(DISTINCT CONCAT(...))` correlated subquery | 修 sample SQL 为 MySQL 8.0 合法括号和 `SEPARATOR` 写法 |
| 样例 SQL 明确化 | MySQL/PostgreSQL Q113 `store_inventory` CTE 裸列 `warehouse_id` / `quantity` | 改为 `i.warehouse_id` / `i.quantity`，避免无 metadata 时猜测多表 SELECT 裸列来源 |
| full-grammer 事件生成修复 | MySQL `reference_id IN (SELECT id FROM sales_orders ...)` | 调整 MySQL typed visitor 的 IN-subquery event 生成时序 |
| token-event lineage 修复 | MySQL `INSERT ... SELECT sti.counted_quantity - i.quantity` | `INSERT_SELECT_MAPPING` 改为读取 projection 完整 source 集合 |
| token-event 参数边界修复 | MySQL `CREATE PROCEDURE ... IN p_*` 参数出现在 SELECT list | 基于 routine parameter declaration 结构排除参数作为物理 source |

## 需要人工审核的项

当前没有 `REVIEW_NEEDED` 项。
