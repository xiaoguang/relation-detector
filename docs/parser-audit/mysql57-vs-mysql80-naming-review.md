# MySQL 5.7 与 MySQL 8.0 NAMING_MATCH 审计

本文记录 `mysql-v5_7-full` 与 `mysql-v8_0-full` 在 sample-data CLI JSON 中的 top-level `namingEvidence` 差异。统计来自：

- `target/sample-data-parser-cli/results/mysql-v5_7-full.json`
- `target/sample-data-parser-cli/results/mysql-v8_0-full.json`

## 当前统计

| Parser | Fixtures | SQL / DDL | Relations | Lineage | NAMING_MATCH | Diagnostics |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| MySQL full-grammer v5_7 | 38 | 32 / 6 | 346 | 265 | 99 | 0 |
| MySQL full-grammer v8_0 | 38 | 32 / 6 | 397 | 254 | 184 | 0 |

## 集合差异

| 差异 | 数量 | 总体判断 |
| --- | ---: | --- |
| v5_7-only namingEvidence | 7 | 主要来自 5.7 语义改写后的表/列命名，属于 `EXPECTED_VERSION_DELTA` |
| v8_0-only namingEvidence | 92 | 主要来自 8.0 SQL asset 更宽、full-grammer predicate 覆盖更完整，也包含部分可疑的 `_id -> id` 宽命中 |

## v5_7-only 样例

| Naming evidence | 判断 |
| --- | --- |
| `ar_aging_snapshots.customer_id -> customers.id` | 5.7 版本 SQL 中保留明确客户维度命名，合理 |
| `boms.component_product_id -> products.id` | 5.7 改写使用 `component_product_id`，与 8.0 的 `child_product_id` / `parent_product_id` 命名不同 |
| `sales_fact.category_id -> category_dim.id` | 5.7 语义改写后的维度键命名，合理 |
| `sales_fact.region_id -> region_dim.id` | 5.7 语义改写后的维度键命名，合理 |

## v8_0-only 样例

明确合理或更完整的命中：

- `payments.customer_id -> customers.id`
- `customer_addresses.customer_id -> customers.id`
- `inventory.warehouse_id -> warehouses.id`
- `mrp_runs.plan_id -> production_plans.id`
- `mrp_run_items.component_product_id -> products.id`

需要谨慎的宽命中：

- `employees.position_id -> products.id`
- `sales_order_items.product_id -> positions.id`
- `payment_receipts.party_id -> customers.id`
- `cashier_journals.reference_id -> purchase_orders.id`
- `voucher_items.voucher_id -> customers.id`

这些证据来自命名规则和已解析 predicate/DDL inventory 的组合。它们不能单独生成 relationship；如果出现在 relationship evidence 中，应继续检查是否有 SQL/DDL/metadata/profile 证据支撑。

## 结论

- 这不是单纯 “5.7 太窄” 或 “8.0 太宽” 的二选一。
- v5_7 NAMING_MATCH 少，主要有两类原因：
  - 5.7 sample SQL 是从 8.0 语义改写而来，部分表/列命名与 8.0 不完全一致。
  - v8_0 full-grammer 对 SQL predicate 和 DDL inventory 的覆盖面更宽。
- v8_0 确实存在一些命名启发式宽命中，需要后续在 `NamingEvidenceExtractor` / relationship 使用侧继续收紧资格，而不是把 92 条差异全部倒灌到 v5_7。
- 当前没有需要人工审核的 `REVIEW_NEEDED`；剩余工作是后续规则收敛与语义等价 SQL 资产继续对齐。
