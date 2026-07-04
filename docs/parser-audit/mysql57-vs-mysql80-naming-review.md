# MySQL 5.7 与 MySQL 8.0 NAMING_MATCH 审计

本文记录 `mysql-v5_7-full` 与 `mysql-v8_0-full` 在 sample-data CLI JSON 中的 top-level `namingEvidence` 差异。统计来自：

- `target/sample-data-parser-cli/results/mysql-v5_7-full.json`
- `target/sample-data-parser-cli/results/mysql-v8_0-full.json`

## 当前统计

| Parser | Fixtures | SQL / DDL | Relations | Lineage | NAMING_MATCH | Diagnostics |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| MySQL full-grammer v5_7 | 38 | 32 / 6 | 346 | 265 | 243 | 0 |
| MySQL full-grammer v8_0 | 38 | 32 / 6 | 397 | 254 | 245 | 0 |

## 集合差异

| 差异 | 数量 | 总体判断 |
| --- | ---: | --- |
| v5_7-only namingEvidence | 4 | 来自 5.7 语义改写后的表/列命名，属于 `EXPECTED_VERSION_DELTA` |
| v8_0-only namingEvidence | 6 | 来自 8.0 自然 SQL 资产中的额外业务谓词，属于 `EXPECTED_VERSION_DELTA` / `ASSET_COVERAGE_DELTA` |

## 本轮处理结论

本轮逐条审计后做了两类修正：

- 收紧 8.0 过宽命中：`ID_SUFFIX_TO_ID` 不再表示“任意 `_id` 可以指向任意表的 `id`”。现在只有 source id 前缀和 target table stem 有可解释关系时才命中，例如 `order_id -> sales_orders.id`、`component_product_id -> products.id`。`position_id -> products.id`、`product_id -> positions.id`、`reference_id -> purchase_orders.id` 这类宽命中已被删除。
- 合理补齐 naming evidence 池：DDL 已经确认的 `DDL_FOREIGN_KEY` relationship candidate 也会进入 top-level `namingEvidence` 抽取流程。这样跨 DDL 文件的 `ap_invoices.purchase_order_id -> purchase_orders.id`、`payments.customer_id -> customers.id`、`sales_fact.region_dim_id -> region_dim.id` 等不再依赖同一文件内的 column inventory 同时出现。

## v5_7-only 样例

| Naming evidence | 判断 |
| --- | --- |
| `boms.component_product_id -> products.id` | 5.7 改写使用 `component_product_id`，与 8.0 的 `child_product_id` / `parent_product_id` 命名不同 |
| `boms.product_id -> products.id` | 5.7 兼容 SQL 中保留的直接 product 维度命名，合理 |
| `mrp_run_items.mrp_run_id -> mrp_runs.id` | 5.7 改写使用 `mrp_run_id`，与 8.0 的 `run_id` 命名不同 |
| `mrp_runs.production_plan_id -> production_plans.id` | 5.7 改写使用完整业务名，8.0 使用较短 `plan_id` |

## v8_0-only 样例

剩余 v8_0-only 项都能从 8.0 sample SQL 的额外谓词或业务命名解释：

- `employees.manager_id -> employees.id`
- `production_operations.predecessor_operation_id -> production_operations.id`
- `purchase_receipt_items.order_item_id -> purchase_order_items.id`
- `serial_numbers.purchase_receipt_id -> purchase_receipts.id`
- `serial_numbers.return_id -> sales_returns.id`
- `serial_numbers.sales_order_id -> sales_orders.id`

这些不是 5.7 parser gap；5.7 对应业务资产采用了更短或不同的兼容 SQL，因此 top-level naming evidence 集合不要求逐条完全相同。

## 结论

- 5.7 低 NAMING_MATCH 的主要根因已经确认并修复：不是 5.7 grammar 本身无法解析，而是 naming evidence 抽取原先没有从 DDL FK relationship candidate 补全跨文件命名证据。
- 8.0 的宽 `_id -> id` false positive 已收紧，相关 relationship 不再引用这些噪声 `NAMING_MATCH`。
- 当前 v5_7 与 v8_0 只剩 4 / 6 条自然资产差异，没有需要用户审核的 `REVIEW_NEEDED`。
