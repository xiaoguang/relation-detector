# token-event Cleanup Golden Diff

本报告记录当前工作区 correctness golden 相对 Git `HEAD` 的关系/血缘差异，用于确认 token-event primary 清理后识别结果是否发生变化。

## 结论

- `expected-lineage.json`：无 tracked diff，字段血缘 golden 没有变化。
- `expected-relations.json`：15 个文件有变化。
- 新增 relation：76 条。
- 删除 relation：103 条。
- 变化类型整体符合 token-event primary 的策略：删除旧表级弱共现，新增更明确的列级弱共现或 FK-like；另有 2 条 MySQL `IN` 子查询方向修正。

## 分类统计

| 类型 | 数量 | 解释 |
|---|---:|---|
| 新增 `CO_OCCURRENCE ... SQL_LOG_COLUMN_CO_OCCURRENCE` | 60 | 原来只能输出表级共现，现在能定位到两侧物理列，但方向不足以判 FK-like |
| 新增 `FK_LIKE ... SQL_LOG_JOIN` | 14 | token-event 能通过 alias/CTE/derived/DML 回溯到更明确的 FK-like 端点 |
| 新增 `FK_LIKE ... SQL_LOG_SUBQUERY_IN` | 2 | 旧方向被修正为 canonical FK-like 方向 |
| 删除 `CO_OCCURRENCE ... SQL_LOG_TABLE_CO_OCCURRENCE` | 101 | 旧的表级弱共现被列级/FK-like 替代，或因过宽而删除 |
| 删除 `FK_LIKE ... SQL_LOG_SUBQUERY_IN` | 2 | 对应 2 条旧反向关系，已由 canonical 方向替代 |

## 明确方向修正

| 文件 | 删除旧方向 | 新增 canonical 方向 |
|---|---|---|
| `test-fixtures/correctness/mysql/mysql-official-cte-nested-sql/expected-relations.json` | `FK_LIKE:purchase_orders.id->purchase_order_approvals.purchase_order_id:SQL_LOG_SUBQUERY_IN` | `FK_LIKE:purchase_order_approvals.purchase_order_id->purchase_orders.id:SQL_LOG_SUBQUERY_IN` |
| `test-fixtures/correctness/mysql/mysql-official-derived-subquery-sql/expected-relations.json` | `FK_LIKE:orders.id->shipments.order_id:SQL_LOG_SUBQUERY_IN` | `FK_LIKE:shipments.order_id->orders.id:SQL_LOG_SUBQUERY_IN` |

## 二次复核结论

第一次机械 diff 只看同向 fingerprint，因此把一部分“已由反向 canonical FK-like 或列级关系替代”的变化误归为“无替代删除”。二次复核改为按无序表对检查后，结论如下：

| 项目 | 数量 | 结论 |
|---|---:|---|
| 删除的表级弱共现总数 | 101 | 都是旧的弱 `CO_OCCURRENCE:table->table` |
| 已由同一组表的列级/FK-like 关系替代 | 69 | 接受，属于精度提升 |
| 没有同表对替代 | 32 | 需要逐条看 SQL 语义 |
| 32 条中确认属于安全降噪 | 22 | 接受删除 |
| 32 条中建议补抽取能力 | 10 | token-event 还有可改进点 |

## 建议补抽取的 10 条

这些不是“保留旧表级共现”的理由；旧输出仍然太粗。更好的修复是让 token-event 输出列级弱共现或 FK-like。

| 文件 | 旧删除项 | SQL 语义 | 建议新关系 |
|---|---|---|---|
| `test-fixtures/correctness/common/sql-join-using/expected-relations.json` | `CO_OCCURRENCE:orders->order_tags:SQL_LOG_TABLE_CO_OCCURRENCE` | `JOIN ... USING (order_id)` 明确给出了列名 | `CO_OCCURRENCE:orders.order_id->order_tags.order_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `test-fixtures/correctness/mysql/mysql-official-join-matrix-sql/expected-relations.json` | `CO_OCCURRENCE:orders->order_audit:SQL_LOG_TABLE_CO_OCCURRENCE` | `JOIN order_audit USING (order_id)` 明确给出了列名 | `CO_OCCURRENCE:orders.order_id->order_audit.order_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/postgres-official-join-edge-sql/expected-relations.json` | `CO_OCCURRENCE:orders->order_tags:SQL_LOG_TABLE_CO_OCCURRENCE` | PostgreSQL `JOIN ... USING (order_id) AS ...` 明确给出了列名 | `CO_OCCURRENCE:orders.order_id->order_tags.order_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:application_users->user_role_mappings:SQL_LOG_TABLE_CO_OCCURRENCE` | correlated subquery 中 `user_id = u.user_id`，左侧可解析到 `user_role_mappings.user_id` | `CO_OCCURRENCE:user_role_mappings.user_id->application_users.user_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:hr_employees->hr_employees:SQL_LOG_TABLE_CO_OCCURRENCE` | self join：`e.manager_id = m1.emp_id` / `m1.manager_id = m2.emp_id` | 至少 `CO_OCCURRENCE:hr_employees.manager_id->hr_employees.emp_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `CO_OCCURRENCE:workflow_tasks->workflow_tasks:SQL_LOG_TABLE_CO_OCCURRENCE` | self join：`curr.predecessor_id = prev.task_id` | 至少 `CO_OCCURRENCE:workflow_tasks.predecessor_id->workflow_tasks.task_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `CO_OCCURRENCE:documents->revisions:SQL_LOG_TABLE_CO_OCCURRENCE` | `documents.id IN (SELECT doc_id FROM revisions ...)` | `FK_LIKE:revisions.doc_id->documents.id:SQL_LOG_SUBQUERY_IN` |
| `test-fixtures/correctness/postgres/postgres-official-cte-nested-sql/expected-relations.json` | `CO_OCCURRENCE:departments->departments:SQL_LOG_TABLE_CO_OCCURRENCE` | recursive CTE 回溯后是层级自引用：`child.parent_department = parent.id` | 至少 `CO_OCCURRENCE:departments.parent_department->departments.id:SQL_LOG_COLUMN_CO_OCCURRENCE`，如规则允许可升为 FK-like |
| `test-fixtures/correctness/postgres/postgres-official-subquery-edge-sql/expected-relations.json` | `CO_OCCURRENCE:orders->users:SQL_LOG_TABLE_CO_OCCURRENCE` | derived table `projected_orders.user_id = users.id` 可回溯到 `orders.user_id` | `FK_LIKE:orders.user_id->users.id:SQL_LOG_JOIN` |
| `test-fixtures/correctness/postgres/postgres-official-subquery-edge-sql/expected-relations.json` | `CO_OCCURRENCE:refunds->orders:SQL_LOG_TABLE_CO_OCCURRENCE` | `refunds.order_id NOT IN (SELECT orders.id ...)` 仍表达列级子查询关系 | `FK_LIKE:refunds.order_id->orders.id:SQL_LOG_SUBQUERY_IN` |

## 安全删除的弱表级共现

这些表级共现没有明确列等值、FK-like 方向或可审计的物理列端点。删除它们是正确降噪，不建议恢复旧表级输出。

| 文件 | 删除项 |
|---|---|
| `test-fixtures/correctness/mysql/mysql-official-join-edge-sql/expected-relations.json` | `CO_OCCURRENCE:order_status->status_labels:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:application_users->security_compliance_thresholds:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:application_users->structural_statuses:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:loyalty_program_tiers->loyalty_transactions:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:security_siem_logs->corporate_networks_whitelist:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:structural_statuses->security_compliance_thresholds:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:structural_statuses->user_role_mappings:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:tracking_tickets->quality_gates:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:user_role_mappings->security_compliance_thresholds:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `CO_OCCURRENCE:base_catalog->extension_catalog:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `CO_OCCURRENCE:base_catalog->vendor_registry:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `CO_OCCURRENCE:extension_catalog->vendor_registry:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `CO_OCCURRENCE:financial_accounts->audit_trail:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `CO_OCCURRENCE:internal_staff->external_vendors:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `CO_OCCURRENCE:payment_transactions->system_config:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `CO_OCCURRENCE:branch_a->branch_b:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `CO_OCCURRENCE:products->audit_logs:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `CO_OCCURRENCE:system_releases->beta_builds:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `CO_OCCURRENCE:table_alpha->table_beta:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `CO_OCCURRENCE:vocabulary_prefix->vocabulary_suffix:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/postgres-official-join-edge-sql/expected-relations.json` | `CO_OCCURRENCE:customers->customer_audit:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `test-fixtures/correctness/postgres/postgres-official-lateral-nested-join-sql/expected-relations.json` | `CO_OCCURRENCE:orders->products:SQL_LOG_TABLE_CO_OCCURRENCE` |

## 审计建议

我建议分两步处理：

- 新增的列级 `CO_OCCURRENCE` 比旧表级共现更精确，但仍保持弱关系类型。
- 新增的 FK-like 多数来自更明确的 `_id -> id` 或 CTE/derived 回溯，方向符合 canonical FK-like 约定。
- 方向修正的 2 条 MySQL `IN` 子查询是正确修复。
- 安全删除项应接受删除；恢复它们会让输出重新噪声化。
- 上面 10 条不应恢复旧表级共现，而应补 token-event 列级/FK-like 抽取能力，并新增 golden。

当前没有发现新增关系明显误报；真正的问题集中在“少数旧表级共现删除后，本应升级为列级/FK-like，但目前还没有补出来”的 10 条。
