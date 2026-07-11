# MySQL 5.7 / 8.0 sample-data parity 审计

本文记录 `mysql-v5_7-full` 与 `mysql-v8_0-full` 在自然 sample-data CLI 上的差异。输入来自：

- `relation-detector/target/sample-data-parser-cli/results/mysql-v5_7-full-derived-fresh.json`
- `relation-detector/target/sample-data-parser-cli/results/mysql-v8_0-full-derived-fresh.json`

统计由 `relation-detector/test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh` 生成。

## 审计口径

MySQL 5.7 与 8.0 的 sample-data 不再按“数量必须相等”验收。5.7 是 8.0 业务资产的兼容改写版本：CTE、窗口函数、部分 8.0-only 写法会被改成 derived table、聚合子查询、变量或普通 DML。因此 `Rel/Lin/Name/Der*` 的数量差异必须逐项分类，而不是直接判定为 parser gap。

分类只使用以下几类：

- `EXPECTED_VERSION_DELTA`：由 MySQL 5.7 / 8.0 官方语法能力差异造成。
- `EXPECTED_SQL_ASSET_DELTA`：由 5.7 兼容改写或 8.0 自然 SQL 资产差异造成，SQL 结构本身可解释。
- `MYSQL57_PARSER_GAP`：5.7 SQL 中存在明确结构，但 v5_7 full-grammer 漏识别。
- `MYSQL80_FALSE_POSITIVE`：8.0 输出没有 SQL/DDL/metadata/profile 结构证据支撑，应收紧 parser 或 golden。
- `REVIEW_NEEDED`：仅凭 SQL 结构无法判断，需人工审核。

当前结论：不要求 5.7 与 8.0 数量 parity；只要求所有差异能落入上述分类，且 `MYSQL57_PARSER_GAP` / `MYSQL80_FALSE_POSITIVE` 不能残留。

## 当前统计

| Parser | Fixtures | SQL / DDL | Rel | Lin | Name | Diag | DerRel | DerLin | DerName |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| MySQL full-grammer v5_7 | 38 | 32 / 6 | 330 | 281 | 971 | 0 | 999 | 70 | 727 |
| MySQL full-grammer v8_0 | 38 | 32 / 6 | 361 | 281 | 1019 | 0 | 1077 | 59 | 771 |

集合去重后：

| Set | v5_7 | v8_0 | Intersection | v5_7 only | v8_0 only |
| --- | ---: | ---: | ---: | ---: | ---: |
| direct relationships | 330 | 361 | 328 | 2 | 33 |
| direct lineage source-target pairs | 381 | 410 | 365 | 16 | 45 |
| top-level namingEvidence total | 971 | 1019 | 971 | 0 | 48 |
| top-level namingEvidence direct only | 244 | 248 | 244 | 0 | 4 |

## 本轮确认并修复的 false positive

`mysql-v8_0-full` 曾额外输出：

```text
CO_OCCURRENCE: positions.id -> sales_order_items.product_id
```

SQL 上下文在 `relation-detector/sample-data/mysql/8.0/04-queries/09-real-world-scenarios.sql`。同一条业务查询中，CTE 内部使用 `products p`，外层员工绩效查询又使用 `positions p`。full-grammer 事件流被压平成同一 alias map 后，`p.id = soi.product_id` 被错误解析为 `positions.id = sales_order_items.product_id`。

修复方式：

- `RowsetScopeSink` 在恢复 rowset scope 时同步恢复 alias index。
- `TokenEventRelationExtractor` 对同一事件组内指向不同物理表的重复 alias 标记为 ambiguous，并拒绝用该 alias 解析关系。

修复后 `positions.id -> sales_order_items.product_id` 数量为 0；`mysql-v8_0-full` `Diag` 仍为 0。

## v5_7-only 差异判断

v5_7 direct naming evidence is now a complete subset of v8_0: there are no v5_7-only direct naming facts. The two v5_7-only direct relationships are natural compatibility-query equalities:

| Relationship | 判断 |
| --- | --- |
| `employee_roles.role_id -> role_permissions.role_id` | `EXPECTED_SQL_ASSET_DELTA`: the 5.7 compatibility query joins the two role-assignment tables directly. |
| `product_batches.product_id -> purchase_order_items.product_id` | `EXPECTED_SQL_ASSET_DELTA`: the 5.7 return/batch rewrite uses this direct typed equality instead of the 8.0 statement shape. |

The 16 v5_7-only lineage source-target pairs come from natural 5.7 compatibility processes: payroll voucher creation, return voucher creation, commission period projection, and locked-inventory updates. Each is backed by a typed `INSERT ... SELECT` or `UPDATE` expression and is classified as `EXPECTED_SQL_ASSET_DELTA`, not a parser gap.

## v8_0-only 差异判断

v8_0-only direct naming evidence has 4 entries:

| Evidence | 判断 |
| --- | --- |
| `purchase_receipt_items.order_item_id -> purchase_order_items.id` | `EXPECTED_SQL_ASSET_DELTA`：8.0 资产覆盖采购收货明细到订单明细。 |
| `serial_numbers.purchase_receipt_id -> purchase_receipts.id` | `EXPECTED_SQL_ASSET_DELTA`：8.0 资产覆盖序列号入库来源。 |
| `serial_numbers.return_id -> sales_returns.id` | `EXPECTED_SQL_ASSET_DELTA`：8.0 资产覆盖序列号退货来源。 |
| `serial_numbers.sales_order_id -> sales_orders.id` | `EXPECTED_SQL_ASSET_DELTA`：8.0 资产覆盖序列号销售来源。 |

v8_0-only relationship has 33 entries. They are primarily natural-query/routine `CO_OCCURRENCE` facts from 8.0 statement shapes, including polymorphic `reference_id` / `party_id`, BOM parent/child, workflow, supplier-quality, and return analysis. They are not promoted to `NAMING_MATCH` without a matching top-level naming-evidence fact.

v8_0-only lineage source-target pair has 45 entries, concentrated in supplier-quality/procurement-performance updates, serial/return provenance, and natural 8.0 derived writes:

- `inspection_reports.inspection_result -> supplier_products.quality_score`
- `purchase_order_items` / `purchase_orders` / `supplier_products` / `purchase_returns` 聚合到 `supplier_products.return_rate`
- 同类聚合到 `supplier_products.total_order_qty`
- 同类聚合到 `supplier_products.last_order_date`
- 同类聚合到 `supplier_products.total_order_count`
- `sales_order_items.* -> inventory_transactions.before_qty/after_qty` 等 8.0 自然派生写入

这些来自 8.0 自然业务 SQL 中的供应商绩效写入，当前判断为 `EXPECTED_SQL_ASSET_DELTA`。

## 结论

- `MYSQL80_FALSE_POSITIVE`：已确认并修复 1 条 alias shadowing 造成的 `positions.id -> sales_order_items.product_id` 误识别。
- `MYSQL57_PARSER_GAP`：当前未确认。
- `MYSQL80_FALSE_POSITIVE`：修复后未发现新的 confirmed false positive。
- `EXPECTED_SQL_ASSET_DELTA` / `EXPECTED_VERSION_DELTA`：解释当前剩余差异。
- `REVIEW_NEEDED`：无。

## 维护备注

后续 parser 修复不应把 MySQL 5.7 / 8.0 的 sample-data 数量差异当成自动追平目标。例如 token-event scalar aggregate UPDATE 的 source tracing 修复，属于 token-event 覆盖能力补齐；它不改变本审计对 `mysql-v5_7-full` 与 `mysql-v8_0-full` 的判断口径。只有当某一侧 SQL 中存在明确结构而对应 full-grammer 漏识别时，才归类为 `MYSQL57_PARSER_GAP` 或 `MYSQL80_FALSE_POSITIVE` 并进入 parser/golden 修复。
