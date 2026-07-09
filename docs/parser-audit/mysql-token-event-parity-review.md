# MySQL token-event 与 v8_0 full-grammer sample-data 审计

本文记录本轮对 `mysql-token-event-root` 与 `mysql-v8_0-full` 的 sample-data CLI JSON 对比结论。统计来自：

- `relation-detector/target/sample-data-parser-cli/results/mysql-token-event-root.json`
- `relation-detector/target/sample-data-parser-cli/results/mysql-v8_0-full.json`

## 当前统计

| Parser | Fixtures | SQL / DDL | Relations | Lineage | NAMING_MATCH | Diagnostics |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| MySQL token-event root | 38 | 32 / 6 | 347 | 242 | 238 | 0 |
| MySQL full-grammer v8_0 | 38 | 32 / 6 | 366 | 253 | 244 | 0 |

## 本轮已修复

| 类别 | SQL 上下文 | 处理 |
| --- | --- | --- |
| routine `UNION` / `DISTINCT` / `ON DUPLICATE KEY UPDATE` | `sp_refresh_semantic_dimensions` 中的维度刷新 SQL | MySQL token-event grammar 增加 typed `UNION`、`DISTINCT`、`ON DUPLICATE KEY UPDATE` 结构，使 `INSERT SELECT` 不再在第一段 query 后截断 |
| select-list boolean expression lineage | `YEAR(d.calendar_date) = 2026 AS is_current_fiscal_year`、`pc.name = '女装' OR ... AS is_womenwear` | MySQL token-event visitor 将 select-list 谓词作为表达式来源追踪，不把它当 relationship predicate |
| function keyword expression | `CONCAT('MRP-', ..., REPLACE(...))` | 将 `REPLACE` 纳入 MySQL token-event function identifier 范围，恢复 `mrp_runs.run_no` 的表达式来源 |
| DDL column inventory | MySQL token-event DDL 文件 | MySQL token-event DDL visitor 输出 `DDL_COLUMN` inventory，使 top-level `namingEvidence` 不再只依赖 SQL predicate |
| DDL FK endpoint naming evidence | 跨 DDL 文件的 FK endpoints | top-level `namingEvidence` 现在也从已确认的 `DDL_FOREIGN_KEY` relationship candidate 抽取，不要求 source/target column inventory 出现在同一个 DDL 文件中 |

## Lineage 差异审计

集合对比结果：

| 差异 | 数量 | 判断 |
| --- | ---: | --- |
| token-event 独有 source-target lineage pair | 0 | 当前未发现 token-event 独有 source-target pair false positive。 |
| v8_0 独有 source-target lineage pair | 14 | 混合了确认 token-event gap 与 v8_0 更宽 source set；不应按数量直接硬追。 |

代表性 `CONFIRMED_TOKEN_EVENT_GAP`：

| Lineage | 说明 |
| --- | --- |
| `repair_order_parts.quantity -> inventory_transactions.quantity_change` | 明确 `INSERT SELECT` 算术表达式，token-event 还需补 arithmetic source tracing |
| `purchase_returns.purchase_order_id -> purchase_return_items.batch_id/product_id/unit_price` | 明确退货明细派生写入，token-event 对部分 scalar subquery / joined source propagation 仍窄于 full-grammer |
| `sales_orders.customer_id -> shipments.receiver_name/receiver_phone/to_address` | 明确业务查询映射，token-event 仍有部分 data/procedure 表达式覆盖差距 |

代表性 `LIKELY_V8_BROAD_SOURCE_SET`：

| Lineage | 判断 |
| --- | --- |
| `fixed_assets.id -> fixed_assets.accumulated_depreciation` | `id` 更像行过滤/定位字段，不应轻易作为数值来源固化 |
| 多列 `purchase_orders/purchase_order_items/purchase_returns/... -> supplier_products.*` 聚合 | v8_0 将 join/filter/grouping 字段也合入 aggregate source set，token-event 当前更窄；需要后续统一 source-set 口径，而不是简单补数量 |

代表性 `TRANSFORM_GRANULARITY_DELTA`：

- `sales_orders.order_date -> fiscal_calendar.fiscal_year/month/quarter`
- `sales_orders.status -> shipments.status`
- `product_categories.name -> category_dim.is_womenwear`

这些差异更多是 transform type 粒度不同，不代表业务字段缺失。

## NAMING_MATCH 差异审计

集合对比结果：

| 差异 | 数量 | 判断 |
| --- | ---: | --- |
| token-event 独有 namingEvidence | 0 | token-event 没有发明额外命名证据 |
| v8_0 独有 direct namingEvidence | 6 | 来自 v8_0 更宽 SQL predicate 覆盖和自然资产表达差异，不是 DDL inventory 缺失 |

剩余 v8_0-only 命中包括：

- `customers.employee_id -> employees.id`
- `employees.manager_id -> employees.id`
- `production_operations.predecessor_operation_id -> production_operations.id`
- `purchase_receipt_items.order_item_id -> purchase_order_items.id`
- `serial_numbers.purchase_receipt_id -> purchase_receipts.id`
- `serial_numbers.return_id -> sales_returns.id`
- `serial_numbers.sales_order_id -> sales_orders.id`

前面那些 `_id -> 任意 id` 的宽命中已经被收紧删除。剩余 6 条能从 8.0 SQL 上下文解释；其中 `purchase_receipt_items.order_item_id -> purchase_order_items.id` 来自 routine join，`serial_numbers.return_id -> sales_returns.id` 来自 select-list scalar subquery predicate，当前更准确地归类为 token-event typed visitor coverage gap，而不是 v8_0 false positive。

## 当前仍需修复的 token-event gap

| Gap | SQL 上下文 | 判断 |
| --- | --- | --- |
| `purchase_receipt_items.order_item_id -> purchase_order_items.id` | `sample-data/mysql/8.0/02-procedures/02-procedures-supplement.sql` 中 `LEFT JOIN purchase_receipt_items pri ON poi.id = pri.order_item_id` | `CONFIRMED_TOKEN_EVENT_GAP`：routine join predicate 被 v8_0 full-grammer 捕获，token-event root 没有输出对应 relation/namingEvidence。 |
| `serial_numbers.return_id -> sales_returns.id` | `sample-data/mysql/8.0/04-queries/03-complex-queries-batch3.sql` 中 select-list scalar subquery `WHERE sr.id = sn.return_id` | `CONFIRMED_TOKEN_EVENT_GAP`：select-list scalar subquery predicate 没有进入 token-event relationship/namingEvidence。 |

## 结论

- MySQL token-event 已补上多项 high-value parser gap：routine `UNION` / `ON DUPLICATE`、boolean select item、keyword function、DDL column inventory 和部分 scalar aggregate source tracing。
- 当前未发现 token-event 独有 source-target lineage false positive。
- 剩余 v8_0-only lineage / namingEvidence 不全部等于 token-event bug；其中一部分是 v8_0 source set 更宽或自然 SQL 资产覆盖差异，上表两项是仍确认的 token-event typed visitor gap。
- 当前没有 `REVIEW_NEEDED`。
