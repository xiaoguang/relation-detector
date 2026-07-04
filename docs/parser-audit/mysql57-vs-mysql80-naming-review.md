# MySQL 5.7 与 MySQL 8.0 NAMING_MATCH 审计

本文记录 `mysql-v5_7-full` 与 `mysql-v8_0-full` 在 sample-data correctness golden 中的 top-level `namingEvidence` 差异。

- `test-fixtures/correctness/mysql/v5_7/**/expected-naming-evidence.json`
- `test-fixtures/correctness/mysql/v8_0/**/expected-naming-evidence.json`

## 当前统计

| Parser | Fixtures | SQL / DDL | Relations | Lineage | NAMING_MATCH | Diagnostics |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| MySQL full-grammer v5_7 sample-data | 37 | 31 / 6 | 562 | 285 | 256 | 0 |
| MySQL full-grammer v8_0 sample-data | 37 | 31 / 6 | 785 | 273 | 418 | 0 |

上表的 `NAMING_MATCH` 是 sample-data fixture 级 fingerprint 总量。去重为 `source -> target + rule` 后，当前集合大小为：

| Set | Count |
| --- | ---: |
| v5_7 top-level namingEvidence unique set | 95 |
| v8_0 top-level namingEvidence unique set | 164 |
| intersection | 90 |

## 集合差异

| 差异 | 数量 | 总体判断 |
| --- | ---: | --- |
| v5_7-only namingEvidence | 5 | 来自 5.7 语义改写后的表/列命名，属于 `EXPECTED_VERSION_DELTA` |
| v8_0-only namingEvidence | 74 | 主要来自 8.0 自然 ERP 资产中更完整的表/列、DDL FK 和业务谓词覆盖，属于 `ASSET_COVERAGE_DELTA`；未确认 parser gap |

## 本轮处理结论

本轮逐条审计后做了两类修正：

- 收紧 8.0 过宽命中：`ID_SUFFIX_TO_ID` 不再表示“任意 `_id` 可以指向任意表的 `id`”。现在只有 source id 前缀和 target table stem 有可解释关系时才命中，例如 `order_id -> sales_orders.id`、`component_product_id -> products.id`。`position_id -> products.id`、`product_id -> positions.id`、`reference_id -> purchase_orders.id` 这类宽命中已被删除。
- 合理补齐 naming evidence 池：DDL 已经确认的 `DDL_FOREIGN_KEY` relationship candidate 也会进入 top-level `namingEvidence` 抽取流程。这样跨 DDL 文件的 `ap_invoices.purchase_order_id -> purchase_orders.id`、`payments.customer_id -> customers.id`、`sales_fact.region_dim_id -> region_dim.id` 等不再依赖同一文件内的 column inventory 同时出现。

## v5_7-only 样例

| Naming evidence | 判断 |
| --- | --- |
| `ar_aging_snapshots.customer_id -> customers.id` | 5.7 兼容资产中保留的账龄快照客户维度命名，合理 |
| `boms.component_product_id -> products.id` | 5.7 改写使用 `component_product_id`，与 8.0 的 `child_product_id` / `parent_product_id` 命名不同 |
| `boms.product_id -> products.id` | 5.7 兼容 SQL 中保留的直接 product 维度命名，合理 |
| `mrp_run_items.mrp_run_id -> mrp_runs.id` | 5.7 改写使用 `mrp_run_id`，与 8.0 的 `run_id` 命名不同 |
| `mrp_runs.production_plan_id -> production_plans.id` | 5.7 改写使用完整业务名，8.0 使用较短 `plan_id` |

## v8_0-only 样例

剩余 v8_0-only 项数量较多，不再能用“少量自然命名差异”概括。逐类看，它们主要来自 8.0 sample-data 中覆盖更完整的 ERP 深水区对象：

- 应收应付、付款、收款、期间结账：`ap_invoices.*_id`、`ar_invoices.customer_id`、`payment_receipt_allocations.receipt_id`、`period_close_jobs.period_id`。
- 采购、退货、质检、序列号：`purchase_receipt_items.*_id`、`purchase_return_items.*_id`、`inspection_reports.batch_id`、`serial_numbers.*_id`。
- 库存、盘点、调拨、拣货：`inventory_transactions.batch_id`、`stock_transfers.*_warehouse_id`、`stocktake_items.product_id`、`picking_task_items.*_id`。
- 生产、MRP、工艺、工单：`mrp_run_items.*_id`、`production_operations.*_operation_id`、`work_orders.product_id`、`work_order_operations.*_id`。
- 售后、维修、销售事实：`repair_orders.*_id`、`repair_order_parts.*_id`、`sales_fact.*_id`。

这些 v8_0-only 命名证据能从 8.0 SQL/DDL 资产结构解释；当前没有发现“8.0 过宽命中导致应删除”的项。5.7 侧 NAMING_MATCH 明显更低，主要是 5.7 兼容资产没有等量承载这些自然 8.0 深水区对象和谓词，而不是 v5_7 full-grammer visitor 漏解析。

## 结论

- 5.7 低 NAMING_MATCH 的当前主要原因是 SQL 资产覆盖差异和 5.7 兼容改写差异；不是已确认 parser gap。
- 8.0 当前 top-level namingEvidence 中没有确认需要收紧的 false positive。
- 当前 v5_7 与 v8_0 的差异结论是 `ASSET_COVERAGE_DELTA` / `EXPECTED_VERSION_DELTA`，没有需要用户审核的 `REVIEW_NEEDED`。
