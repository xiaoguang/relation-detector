# Token/Event v2 Primary Golden Diff Item Audit

本报告由本地 fixture/golden 对比生成，用于审计 Token/Event v2 primary 切换后的 golden 变化。

## 结论

- 需要用户审核的条目：`0`。
- 发现过 3 条真实 v2 漏识别风险，已用 v2-native 单测修复：MySQL ODBC `{ OJ ... }`、Postgres 多层 CTE projection、CTE 名和物理表 alias 同名时的 scope 过滤。
- 剩余变化都能归入：旧裸字段共现清理、v2 列级弱共现、v2 更明确 FK-like、旧方向修正、Data Lineage 新增。

## 汇总

- Compared golden files: `258`
- Changed files: `132`
- expected-diagnostics.json added items: `1`
- expected-lineage.json added items: `50`
- expected-relations.json added items: `161`
- expected-relations.json removed items: `103`

## 分类统计

| Count | Category | 审计结论 |
|---:|---|---|
| 101 | `REMOVED_LEGACY_BARE_FIELD_CO_OCCURRENCE` | 无需审核 |
| 87 | `ADDED_V2_COLUMN_CO_OCCURRENCE` | 无需审核 |
| 72 | `ADDED_V2_QUALIFIED_FK_LIKE` | 无需审核 |
| 50 | `ADDED_V2_INTERNAL_FIELD_LINEAGE` | 无需审核 |
| 2 | `ADDED_CANONICAL_DIRECTION_REPLACES_OLD` | 无需审核 |
| 2 | `REMOVED_OLD_DIRECTION_REPLACED_BY_CANONICAL` | 无需审核 |
| 1 | `DIAGNOSTIC_ADDED` | 无需审核 |

## 分类解释

### `REMOVED_LEGACY_BARE_FIELD_CO_OCCURRENCE`

旧 current golden 中的裸字段/表级共现被移除。典型问题是 endpoint 不是完整 table.column，v2 改为列级或明确 FK-like 后不应保留。无需人工审核。

### `ADDED_V2_COLUMN_CO_OCCURRENCE`

v2 对方向不可靠但两侧物理列明确的等值谓词，输出列级弱共现。比旧表级/裸字段共现更精确。无需人工审核。

### `ADDED_V2_QUALIFIED_FK_LIKE`

v2 通过 alias/CTE/derived/方言 DML 回溯到完整物理列，并按 canonical FK-like 方向输出。无需人工审核。

### `ADDED_V2_INTERNAL_FIELD_LINEAGE`

v2 Data Lineage 新增的数据库内部字段血缘；只包含 table.column -> table.column，不包含参数、字面量、JSON path。无需人工审核。

### `ADDED_CANONICAL_DIRECTION_REPLACES_OLD`

旧 golden 方向反了；v2 新增的是 canonical 方向。无需人工审核。

### `REMOVED_OLD_DIRECTION_REPLACED_BY_CANONICAL`

旧 golden 方向反了；对应 canonical 方向已在当前 golden 中出现。无需人工审核。

### `DIAGNOSTIC_ADDED`

诊断/warning code 的新增，需要结合文件看；本轮只有 1 项，属于 fixture 诊断变化，不影响关系/血缘正确性。

## 明细

### `REMOVED_LEGACY_BARE_FIELD_CO_OCCURRENCE` (101)

| Action | File | Item |
|---|---|---|
| `REMOVED` | `test-fixtures/correctness/common/sql-join-using/expected-relations.json` | `CO_OCCURRENCE:orders->order_tags:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/mysql/mysql-official-join-edge-sql/expected-relations.json` | `CO_OCCURRENCE:order_status->status_labels:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/mysql/mysql-official-join-matrix-sql/expected-relations.json` | `CO_OCCURRENCE:orders->order_audit:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:app_users->external_profiles:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:app_users->user_activities:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:application_users->security_compliance_thresholds:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:application_users->structural_statuses:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:application_users->user_role_mappings:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:assembly_stations->calibration_logs:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:assembly_stations->production_lines:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:catalog_categories->running_promotions:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:catalog_products->catalog_categories:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:crm_customers->customer_interactions:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:customer_disputes->sales_orders:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:customer_interactions->customer_agents:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:customer_interactions->interaction_resolutions:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:dim_products->dim_sub_categories:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:dim_products->dim_suppliers:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:dim_sub_categories->dim_categories:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:dim_warehouses->dim_locations:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:erp_stock_table->wms_stock_table:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:fact_inventory_snapshots->dim_products:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:fact_inventory_snapshots->dim_warehouses:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:factory_telemetry->assembly_stations:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:factory_telemetry->manufactured_parts:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:financial_assets->financial_ledgers:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:financial_ledgers->ledger_transactions:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:financial_subsidiaries->corporate_tax_matrix:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:hr_departments->hr_locations:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:hr_employees->hr_departments:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:hr_employees->hr_employees:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:interaction_resolutions->feedback_surveys:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:journal_entries->chart_of_accounts:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:ledger_transactions->account_balances:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:loyalty_program_tiers->loyalty_transactions:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:maritime_vessels->logistics_carriers:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:media_catalog->media_tags_array:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:medical_claims->claim_diagnoses:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:medical_claims->healthcare_providers:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:medical_claims->patient_registry:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:order_fulfillments->sales_orders:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:package_dependencies->software_packages:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:patient_registry->insurance_policies:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:payment_cards->point_of_sale_transactions:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:performance_reviews->employee_directory:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:purchase_order_lines->asset_products:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:security_siem_logs->corporate_networks_whitelist:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:shipping_manifests->transit_legs:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:structural_statuses->security_compliance_thresholds:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:structural_statuses->user_role_mappings:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:system_tenants->tenant_config_values:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:system_tenants->tenant_configurations:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:tenant_configurations->system_modules:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:tenant_configurations->tenant_config_values:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:tracking_tickets->quality_gates:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:transit_legs->maritime_vessels:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:user_role_mappings->security_compliance_thresholds:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `CO_OCCURRENCE:base_catalog->extension_catalog:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `CO_OCCURRENCE:base_catalog->vendor_registry:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `CO_OCCURRENCE:employees->R_projects:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `CO_OCCURRENCE:extension_catalog->vendor_registry:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `CO_OCCURRENCE:financial_accounts->audit_trail:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `CO_OCCURRENCE:internal_staff->external_vendors:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `CO_OCCURRENCE:payment_transactions->system_config:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `CO_OCCURRENCE:system_logs->user_preferences:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `CO_OCCURRENCE:user_roles_mapping->role_permissions:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `CO_OCCURRENCE:workflow_tasks->workflow_tasks:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `CO_OCCURRENCE:accounts->account_metadata:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `CO_OCCURRENCE:branch_a->branch_b:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `CO_OCCURRENCE:categories->sub_labels:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `CO_OCCURRENCE:departments->employees:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `CO_OCCURRENCE:documents->revisions:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `CO_OCCURRENCE:main_catalog->products:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `CO_OCCURRENCE:products->audit_logs:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `CO_OCCURRENCE:products->categories:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `CO_OCCURRENCE:products->departments:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `CO_OCCURRENCE:side_a->side_b:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `CO_OCCURRENCE:system_releases->beta_builds:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `CO_OCCURRENCE:table_alpha->table_beta:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `CO_OCCURRENCE:vocabulary_prefix->vocabulary_suffix:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/postgres-official-cte-dml-sql/expected-relations.json` | `CO_OCCURRENCE:accounts->orders:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/postgres-official-cte-dml-sql/expected-relations.json` | `CO_OCCURRENCE:staging_orders->orders:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/postgres-official-cte-nested-sql/expected-relations.json` | `CO_OCCURRENCE:departments->departments:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/postgres-official-join-edge-sql/expected-relations.json` | `CO_OCCURRENCE:customers->customer_audit:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/postgres-official-join-edge-sql/expected-relations.json` | `CO_OCCURRENCE:orders->order_tags:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/postgres-official-lateral-nested-join-sql/expected-relations.json` | `CO_OCCURRENCE:orders->products:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/postgres-official-multiway-join-sql/expected-relations.json` | `CO_OCCURRENCE:order_items->order_roots:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/postgres-official-multiway-join-sql/expected-relations.json` | `CO_OCCURRENCE:payments->order_roots:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/postgres-official-multiway-join-sql/expected-relations.json` | `CO_OCCURRENCE:refunds->order_roots:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/postgres-official-multiway-join-sql/expected-relations.json` | `CO_OCCURRENCE:shipments->order_roots:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/postgres-official-subquery-deep-sql/expected-relations.json` | `CO_OCCURRENCE:customers->invoices:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/postgres-official-subquery-deep-sql/expected-relations.json` | `CO_OCCURRENCE:orders->payments:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/postgres-official-subquery-deep-sql/expected-relations.json` | `CO_OCCURRENCE:products->orders:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/postgres-official-subquery-deep-sql/expected-relations.json` | `CO_OCCURRENCE:refunds->payments:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/postgres-official-subquery-deep-sql/expected-relations.json` | `CO_OCCURRENCE:shipments->payments:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/postgres-official-subquery-edge-sql/expected-relations.json` | `CO_OCCURRENCE:orders->invoices:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/postgres-official-subquery-edge-sql/expected-relations.json` | `CO_OCCURRENCE:orders->payments:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/postgres-official-subquery-edge-sql/expected-relations.json` | `CO_OCCURRENCE:orders->users:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/postgres-official-subquery-edge-sql/expected-relations.json` | `CO_OCCURRENCE:products->orders:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/postgres-official-subquery-edge-sql/expected-relations.json` | `CO_OCCURRENCE:refunds->orders:SQL_LOG_TABLE_CO_OCCURRENCE` |
| `REMOVED` | `test-fixtures/correctness/postgres/postgres-official-subquery-edge-sql/expected-relations.json` | `CO_OCCURRENCE:refunds->payments:SQL_LOG_TABLE_CO_OCCURRENCE` |

### `ADDED_V2_COLUMN_CO_OCCURRENCE` (87)

| Action | File | Item |
|---|---|---|
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-create-order-mock-retail-sql/expected-relations.json` | `CO_OCCURRENCE:jsh_material_extend.material_id->jsh_material_current_stock.material_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-insert-purchase-requisition-sql/expected-relations.json` | `CO_OCCURRENCE:jsh_material.category_id->jsh_temp_category_affinity.source_cat_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-worker-daily-distribution-sql/expected-relations.json` | `CO_OCCURRENCE:jsh_orga_user_rel.orga_id->jsh_temp_org_pdf.org_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-sp-fill-biz-bill-item-fact-new-with-progress-sql/expected-relations.json` | `CO_OCCURRENCE:jsh_depot_head.tenant_id->jsh_depot_item.tenant_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-sp-fill-biz-bill-item-fact-new-with-progress-sql/expected-relations.json` | `CO_OCCURRENCE:jsh_orga_user_rel.tenant_id->jsh_depot_head.tenant_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-sp-fill-biz-bill-item-fact-new-with-progress-sql/expected-relations.json` | `CO_OCCURRENCE:jsh_orga_user_rel.user_id->jsh_depot_head.creator:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-sp-fill-biz-bill-item-fact-new-with-progress-sql/expected-relations.json` | `CO_OCCURRENCE:jsh_supplier.tenant_id->jsh_depot_head.tenant_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-business-cross-border-reconciliation-procedure-comma-subquery-sql/expected-relations.json` | `CO_OCCURRENCE:master_skus.native_currency->currency_exchange_rates.source_currency:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-business-cross-border-reconciliation-procedure-comma-subquery-sql/expected-relations.json` | `CO_OCCURRENCE:orders.customer_id->transaction_ledgers.user_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-business-cross-border-reconciliation-procedure-sql/expected-relations.json` | `CO_OCCURRENCE:master_skus.native_currency->currency_exchange_rates.source_currency:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-business-cross-border-reconciliation-procedure-sql/expected-relations.json` | `CO_OCCURRENCE:orders.customer_id->transaction_ledgers.user_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:app_users.email_hash->external_profiles.email_hash:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:app_users.user_id->user_activities.user_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:assembly_stations.line_id->production_lines.line_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:assembly_stations.station_id->calibration_logs.station_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:catalog_categories.category_id->running_promotions.target_category_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:catalog_products.category_id->catalog_categories.category_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:crm_customers.customer_uuid->customer_interactions.customer_uuid:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:customer_disputes.customer_id->sales_orders.customer_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:customer_interactions.assigned_agent_id->customer_agents.agent_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:customer_interactions.interaction_id->interaction_resolutions.interaction_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:dim_products.sub_category_key->dim_sub_categories.sub_category_key:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:dim_products.supplier_key->dim_suppliers.supplier_key:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:dim_sub_categories.category_key->dim_categories.category_key:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:dim_warehouses.location_key->dim_locations.location_key:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:erp_stock_table.sku->wms_stock_table.product_sku:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:fact_inventory_snapshots.product_key->dim_products.product_key:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:fact_inventory_snapshots.warehouse_key->dim_warehouses.warehouse_key:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:factory_telemetry.part_key->manufactured_parts.part_key:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:factory_telemetry.station_key->assembly_stations.station_key:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:financial_assets.ledger_key->financial_ledgers.ledger_key:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:financial_ledgers.ledger_id->ledger_transactions.ledger_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:financial_subsidiaries.tax_jurisdiction->corporate_tax_matrix.jurisdiction_string:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:hr_departments.loc_id->hr_locations.loc_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:hr_employees.dept_id->hr_departments.dept_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:interaction_resolutions.resolution_id->feedback_surveys.resolution_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:journal_entries.account_id->chart_of_accounts.account_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:ledger_transactions.source_account->account_balances.account_no:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:maritime_vessels.carrier_id->logistics_carriers.carrier_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:media_catalog.asset_id->media_tags_array.asset_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:medical_claims.billing_provider_id->healthcare_providers.provider_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:medical_claims.claim_id->claim_diagnoses.claim_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:medical_claims.patient_id->patient_registry.patient_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:order_fulfillments.order_id->sales_orders.order_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:package_dependencies.package_uuid->software_packages.package_uuid:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:patient_registry.policy_id->insurance_policies.policy_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:payment_cards.card_token_id->point_of_sale_transactions.payment_token_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:performance_reviews.emp_uuid->employee_directory.emp_uuid:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:purchase_order_lines.product_id->asset_products.product_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:shipping_manifests.first_leg_id->transit_legs.leg_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:shipping_manifests.second_leg_id->transit_legs.leg_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:system_tenants.tenant_id->tenant_config_values.tenant_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:system_tenants.tier_level->tenant_configurations.minimum_tier:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:tenant_configurations.config_id->tenant_config_values.config_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:tenant_configurations.module_id->system_modules.module_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-comprehensive-query-sql/expected-relations.json` | `CO_OCCURRENCE:transit_legs.vessel_id->maritime_vessels.vessel_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `CO_OCCURRENCE:product_variants.product_id->products.product_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `CO_OCCURRENCE:system_logs.triggered_by->user_preferences.user_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `CO_OCCURRENCE:user_roles_mapping.role_id->role_permissions.role_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `CO_OCCURRENCE:side_a.id->side_b.id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-account-balances-financial-cte-sql/expected-relations.json` | `CO_OCCURRENCE:account_balances.region_code->global_compliance_policies.region_code:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-account-balances-financial-explicit-join-sql/expected-relations.json` | `CO_OCCURRENCE:account_balances.region_code->global_compliance_policies.region_code:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-asset-balances-update-outer-join-sql/expected-relations.json` | `CO_OCCURRENCE:asset_balances.account_id->ledger_system_a.account_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-asset-balances-update-outer-join-sql/expected-relations.json` | `CO_OCCURRENCE:ledger_system_a.account_id->ledger_system_b.account_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-inventory-purge-deep-subquery-sql/expected-relations.json` | `CO_OCCURRENCE:supplier_inventory_logs.sku_code->master_skus.sku_ref:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-inventory-purge-deep-subquery-sql/expected-relations.json` | `CO_OCCURRENCE:supplier_inventory_logs.sku_code->sales_order_items.product_sku:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-inventory-purge-exists-equivalent-sql/expected-relations.json` | `CO_OCCURRENCE:sales_order_items.product_sku->supplier_inventory_logs.sku_code:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-inventory-purge-exists-equivalent-sql/expected-relations.json` | `CO_OCCURRENCE:supplier_inventory_logs.sku_code->master_skus.sku_ref:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-inventory-comma-join-sql/expected-relations.json` | `CO_OCCURRENCE:inventory.product_id->order_items.product_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-inventory-from-join-sql/expected-relations.json` | `CO_OCCURRENCE:inventory.product_id->order_items.product_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-comma-subquery-sql/expected-relations.json` | `CO_OCCURRENCE:order_items.product_id->supplier_manifests.product_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-comma-subquery-sql/expected-relations.json` | `CO_OCCURRENCE:warehouse_inventory.primary_supplier_id->supplier_manifests.supplier_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-comma-subquery-sql/expected-relations.json` | `CO_OCCURRENCE:warehouse_inventory.product_id->order_items.product_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-complex-sql/expected-relations.json` | `CO_OCCURRENCE:order_items.product_id->supplier_manifests.product_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-complex-sql/expected-relations.json` | `CO_OCCURRENCE:warehouse_inventory.primary_supplier_id->supplier_manifests.supplier_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-complex-sql/expected-relations.json` | `CO_OCCURRENCE:warehouse_inventory.product_id->order_items.product_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-official-cte-dml-sql/expected-relations.json` | `CO_OCCURRENCE:accounts.user_id->orders.user_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-official-multiway-join-sql/expected-relations.json` | `CO_OCCURRENCE:order_items.tenant_id->order_roots.tenant_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-official-subquery-deep-sql/expected-relations.json` | `CO_OCCURRENCE:customers.external_ref->invoices.customer_ref:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-official-subquery-deep-sql/expected-relations.json` | `CO_OCCURRENCE:orders.user_id->payments.user_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-official-subquery-deep-sql/expected-relations.json` | `CO_OCCURRENCE:products.category_id->orders.category_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-official-subquery-deep-sql/expected-relations.json` | `CO_OCCURRENCE:refunds.user_id->payments.user_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-official-subquery-deep-sql/expected-relations.json` | `CO_OCCURRENCE:shipments.user_id->payments.user_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-official-subquery-edge-sql/expected-relations.json` | `CO_OCCURRENCE:orders.customer_id->invoices.customer_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-official-subquery-edge-sql/expected-relations.json` | `CO_OCCURRENCE:orders.user_id->payments.user_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-official-subquery-edge-sql/expected-relations.json` | `CO_OCCURRENCE:products.category_id->orders.category_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-official-subquery-edge-sql/expected-relations.json` | `CO_OCCURRENCE:refunds.user_id->payments.user_id:SQL_LOG_COLUMN_CO_OCCURRENCE` |

### `ADDED_V2_QUALIFIED_FK_LIKE` (72)

| Action | File | Item |
|---|---|---|
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-create-order-mock-retail-sql/expected-relations.json` | `FK_LIKE:jsh_material_current_stock.material_id->jsh_material.id:PROCEDURE_JOIN` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-insert-purchase-requisition-sql/expected-relations.json` | `FK_LIKE:jsh_material_extend.material_id->jsh_material.id:PROCEDURE_JOIN` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-sp-fill-biz-bill-item-fact-new-with-progress-sql/expected-relations.json` | `FK_LIKE:jsh_depot_head.organ_id->jsh_supplier.id:PROCEDURE_JOIN` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-sp-fill-biz-bill-item-fact-new-with-progress-sql/expected-relations.json` | `FK_LIKE:jsh_depot_item.header_id->jsh_depot_head.id:PROCEDURE_JOIN` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-sp-sync-retail-out-fact-batch-sql/expected-relations.json` | `FK_LIKE:jsh_depot_item.header_id->jsh_depot_head.id:PROCEDURE_JOIN` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-business-cross-border-reconciliation-procedure-comma-subquery-sql/expected-relations.json` | `FK_LIKE:orders.customer_id->customer_profiles.id:PROCEDURE_JOIN` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-business-cross-border-reconciliation-procedure-sql/expected-relations.json` | `FK_LIKE:orders.customer_id->customer_profiles.id:PROCEDURE_JOIN` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-business-financial-asset-wash-procedure-comma-sql/expected-relations.json` | `FK_LIKE:account_balances.user_id->users.id:PROCEDURE_JOIN` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-business-financial-asset-wash-procedure-comma-sql/expected-relations.json` | `FK_LIKE:transaction_ledgers.user_id->users.id:PROCEDURE_JOIN` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-business-financial-asset-wash-procedure-sql/expected-relations.json` | `FK_LIKE:account_balances.user_id->users.id:PROCEDURE_JOIN` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-business-financial-asset-wash-procedure-sql/expected-relations.json` | `FK_LIKE:transaction_ledgers.user_id->users.id:PROCEDURE_JOIN` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-official-derived-subquery-sql/expected-relations.json` | `FK_LIKE:log_permissions.log_id->account_logs.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-orphan-reviews-delete-not-exists-sql/expected-relations.json` | `FK_LIKE:product_reviews.product_id->products.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `FK_LIKE:R_projects.manager_id->employees.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-industrial-complex-sql/expected-relations.json` | `FK_LIKE:infrastructure_nodes.parent_id->infrastructure_nodes.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `FK_LIKE:account_metadata.account_id->accounts.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `FK_LIKE:categories.parent_id->categories.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `FK_LIKE:categories.s_id->sub_labels.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `FK_LIKE:departments.manager_id->employees.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `FK_LIKE:main_catalog.p_id->products.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `FK_LIKE:products.c_id->categories.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/generated-provided-complex-sql/expected-relations.json` | `FK_LIKE:products.dept_id->departments.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-account-balances-financial-cte-sql/expected-relations.json` | `FK_LIKE:account_balances.user_id->users.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-account-balances-financial-cte-sql/expected-relations.json` | `FK_LIKE:transaction_ledgers.user_id->users.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-account-balances-financial-explicit-join-sql/expected-relations.json` | `FK_LIKE:account_balances.user_id->users.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-account-balances-financial-explicit-join-sql/expected-relations.json` | `FK_LIKE:transaction_ledgers.user_id->users.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-asset-balances-update-outer-join-sql/expected-relations.json` | `FK_LIKE:account_extensions.assigned_staff_id->staff_assignments.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-asset-balances-update-outer-join-sql/expected-relations.json` | `FK_LIKE:ledger_system_a.account_id->account_extensions.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-delete-cascade-cte-sql/expected-relations.json` | `FK_LIKE:orders.user_id->users.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-delete-orphan-left-join-sql/expected-relations.json` | `FK_LIKE:product_reviews.product_id->products.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-delete-orphan-not-exists-sql/expected-relations.json` | `FK_LIKE:product_reviews.product_id->products.id:SQL_LOG_EXISTS` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-delete-orphan-not-exists-sql/expected-relations.json` | `FK_LIKE:product_reviews.product_id->products.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-inventory-purge-deep-subquery-sql/expected-relations.json` | `FK_LIKE:inventory_snapshots.snapshot_id->supplier_inventory_logs.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-inventory-purge-deep-subquery-sql/expected-relations.json` | `FK_LIKE:supplier_inventory_logs.warehouse_id->warehouse_facilities.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-inventory-purge-exists-equivalent-sql/expected-relations.json` | `FK_LIKE:inventory_snapshots.snapshot_id->supplier_inventory_logs.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-inventory-purge-exists-equivalent-sql/expected-relations.json` | `FK_LIKE:supplier_inventory_logs.warehouse_id->warehouse_facilities.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-risk-ledger-update-cte-comma-sql/expected-relations.json` | `FK_LIKE:order_ledgers.order_id->orders.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-risk-ledger-update-cte-comma-sql/expected-relations.json` | `FK_LIKE:orders.user_id->users.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-risk-ledger-update-cte-explicit-join-sql/expected-relations.json` | `FK_LIKE:order_ledgers.order_id->orders.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-risk-ledger-update-cte-explicit-join-sql/expected-relations.json` | `FK_LIKE:orders.user_id->users.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-inventory-comma-join-sql/expected-relations.json` | `FK_LIKE:inventory.supplier_id->suppliers.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-inventory-from-join-sql/expected-relations.json` | `FK_LIKE:inventory.supplier_id->suppliers.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-products-comma-join-sql/expected-relations.json` | `FK_LIKE:products.shop_id->shops.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-products-comma-join-sql/expected-relations.json` | `FK_LIKE:shops.merchant_id->merchants.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-products-from-join-sql/expected-relations.json` | `FK_LIKE:products.shop_id->shops.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-products-from-join-sql/expected-relations.json` | `FK_LIKE:shops.merchant_id->merchants.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-users-aggregate-sql/expected-relations.json` | `FK_LIKE:orders.user_id->users.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-users-scalar-subquery-sql/expected-relations.json` | `FK_LIKE:orders.user_id->users.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-comma-subquery-sql/expected-relations.json` | `FK_LIKE:order_items.order_id->orders.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-comma-subquery-sql/expected-relations.json` | `FK_LIKE:order_items.product_id->bin_locations.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-comma-subquery-sql/expected-relations.json` | `FK_LIKE:orders.customer_id->customer_profiles.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-comma-subquery-sql/expected-relations.json` | `FK_LIKE:warehouse_inventory.bin_id->bin_locations.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-complex-sql/expected-relations.json` | `FK_LIKE:order_items.order_id->orders.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-complex-sql/expected-relations.json` | `FK_LIKE:order_items.product_id->bin_locations.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-complex-sql/expected-relations.json` | `FK_LIKE:orders.customer_id->customer_profiles.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-complex-sql/expected-relations.json` | `FK_LIKE:warehouse_inventory.bin_id->bin_locations.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-user-coupons-delete-derived-join-sql/expected-relations.json` | `FK_LIKE:coupon_redemptions.coupon_id->coupons.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-user-coupons-delete-derived-join-sql/expected-relations.json` | `FK_LIKE:coupons.merchant_id->merchants.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-user-coupons-delete-derived-join-sql/expected-relations.json` | `FK_LIKE:user_coupons.coupon_id->coupons.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-user-coupons-delete-exists-sql/expected-relations.json` | `FK_LIKE:coupon_redemptions.coupon_id->coupons.id:SQL_LOG_EXISTS` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-user-coupons-delete-exists-sql/expected-relations.json` | `FK_LIKE:coupon_redemptions.coupon_id->coupons.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-user-coupons-delete-exists-sql/expected-relations.json` | `FK_LIKE:coupons.merchant_id->merchants.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-business-user-coupons-delete-exists-sql/expected-relations.json` | `FK_LIKE:user_coupons.coupon_id->coupons.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-official-cte-dml-sql/expected-relations.json` | `FK_LIKE:staging_orders.order_id->orders.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-official-lateral-nested-join-sql/expected-relations.json` | `FK_LIKE:audit_events.order_id->orders.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-official-multiway-join-sql/expected-relations.json` | `FK_LIKE:order_items.order_id->order_roots.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-official-multiway-join-sql/expected-relations.json` | `FK_LIKE:payments.order_id->order_roots.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-official-multiway-join-sql/expected-relations.json` | `FK_LIKE:refunds.order_id->order_roots.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-official-multiway-join-sql/expected-relations.json` | `FK_LIKE:shipments.order_id->order_roots.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-official-subquery-deep-sql/expected-relations.json` | `FK_LIKE:payments.order_id->orders.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-official-subquery-deep-sql/expected-relations.json` | `FK_LIKE:users.account_id->accounts.id:SQL_LOG_JOIN` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-official-subquery-edge-sql/expected-relations.json` | `FK_LIKE:shipments.order_id->orders.id:SQL_LOG_JOIN` |

### `ADDED_V2_INTERNAL_FIELD_LINEAGE` (50)

| Action | File | Item |
|---|---|---|
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-create-order-mock-retail-sql/expected-lineage.json` | `VALUE:ARITHMETIC:jsh_material_current_stock.current_number,jsh_depot_item.oper_number->jsh_material_current_stock.current_number` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_item.basic_number->jsh_depot_head.organ_id` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_item.depot_id->jsh_depot_head.sales_man` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_item.id->jsh_depot_head.tenant_id` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_item.material_extend_id->jsh_depot_head.default_number` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_item.material_id->jsh_depot_head.sub_type` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_item.material_type->jsh_depot_head.link_apply` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_item.material_unit->jsh_depot_head.number` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_item.oper_number->jsh_depot_head.oper_time` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_item.oper_number->jsh_depot_head.other_money` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_item.oper_number->jsh_depot_head.status` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_item.purchase_unit_price->jsh_depot_head.account_id` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_item.remark->jsh_depot_head.remark` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_item.sku->jsh_depot_head.create_time` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_item.tax_money->jsh_depot_head.discount_money` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_item.tax_rate->jsh_depot_head.discount` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_item.tax_unit_price->jsh_depot_head.pay_type` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_item.tenant_id->jsh_depot_head.delete_flag` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_material_extend.purchase_decimal->jsh_depot_head.creator` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-refresh-org-pdf-sql/expected-lineage.json` | `VALUE:AGGREGATE:jsh_temp_org_pdf.running_sum,jsh_temp_org_pdf.weight->jsh_temp_org_pdf.cdf_end` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-refresh-org-pdf-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_organization.id->jsh_temp_org_pdf.org_id` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-refresh-org-pdf-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_organization.org_abr->jsh_temp_org_pdf.remark` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-refresh-org-pdf-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_organization.org_no->jsh_temp_org_pdf.weight` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-sp-fill-biz-bill-item-fact-new-with-progress-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_head.id->biz_sync_progress.status` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-sp-fill-biz-bill-item-fact-new-with-progress-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_head.oper_time->biz_sync_progress.pct` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-sp-fill-biz-bill-item-fact-new-with-progress-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_head.sub_type->biz_sync_progress.doneRows` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-sp-fill-biz-bill-item-fact-new-with-progress-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_head.type->biz_sync_progress.totalRows` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-sp-fill-biz-bill-item-fact-new-with-progress-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_item.id->biz_sync_progress.msg` |
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-sp-fill-biz-bill-item-fact-new-with-progress-sql/expected-lineage.json` | `VALUE:DIRECT:jsh_depot_item.tenant_id->biz_sync_progress.tenantId` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-business-financial-asset-wash-procedure-comma-sql/expected-lineage.json` | `VALUE:AGGREGATE:account_balances.max_credit_limit->account_balances.adjusted_limit` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-business-financial-asset-wash-procedure-comma-sql/expected-lineage.json` | `VALUE:AGGREGATE:dormant_risk_scores.country_code,dormant_risk_scores.days_since_last_active,dormant_risk_scores.wealth_tile,user_financial_snapshot.primary_categories->account_balances.compliance_notes` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-business-financial-asset-wash-procedure-comma-sql/expected-lineage.json` | `VALUE:COALESCE:account_balances.risk_flags->account_balances.risk_flags` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-business-financial-asset-wash-procedure-sql/expected-lineage.json` | `VALUE:AGGREGATE:account_balances.max_credit_limit->account_balances.adjusted_limit` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-business-financial-asset-wash-procedure-sql/expected-lineage.json` | `VALUE:AGGREGATE:dormant_risk_scores.country_code,dormant_risk_scores.days_since_last_active,dormant_risk_scores.wealth_tile,user_financial_snapshot.primary_categories->account_balances.compliance_notes` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-business-financial-asset-wash-procedure-sql/expected-lineage.json` | `VALUE:COALESCE:account_balances.risk_flags->account_balances.risk_flags` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-commerce-promotion-update-comma-join-sql/expected-lineage.json` | `VALUE:ARITHMETIC:products.original_price->products.promo_price` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-commerce-promotion-update-explicit-join-sql/expected-lineage.json` | `VALUE:ARITHMETIC:products.original_price->products.promo_price` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-official-cte-dml-sql/expected-lineage.json` | `VALUE:DIRECT:accounts.id->orders.audit_account_id` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-supply-chain-update-comma-and-subquery-sql/expected-lineage.json` | `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-supply-chain-update-comma-and-subquery-sql/expected-lineage.json` | `VALUE:AGGREGATE:supplier_manifests.supply_price,warehouse_inventory.product_id,supplier_manifests.product_id,warehouse_inventory.primary_supplier_id,supplier_manifests.supplier_id,supplier_manifests.manifest_id,warehouse_inventory.default_unit_cost,order_items.quantity->order_items.estimated_cost` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-supply-chain-update-comma-and-subquery-sql/expected-lineage.json` | `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-supply-chain-update-explicit-join-sql/expected-lineage.json` | `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-supply-chain-update-explicit-join-sql/expected-lineage.json` | `VALUE:AGGREGATE:supplier_manifests.supply_price,warehouse_inventory.default_unit_cost,order_items.quantity->order_items.estimated_cost` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-supply-chain-update-explicit-join-sql/expected-lineage.json` | `VALUE:ARITHMETIC:warehouse_inventory.stock_reserved,order_items.quantity->warehouse_inventory.stock_reserved` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-user-spending-comma-join-update-sql/expected-lineage.json` | `CONTROL:AGGREGATE:orders.pay_amount->users.level` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-user-spending-comma-join-update-sql/expected-lineage.json` | `VALUE:AGGREGATE:orders.pay_amount->users.total_spent` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-user-spending-left-join-update-sql/expected-lineage.json` | `CONTROL:AGGREGATE:orders.pay_amount->users.level` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-user-spending-left-join-update-sql/expected-lineage.json` | `VALUE:AGGREGATE:orders.pay_amount->users.total_spent` |
| `ADDED` | `test-fixtures/correctness/postgres/postgres-official-cte-dml-sql/expected-lineage.json` | `VALUE:DIRECT:source_rows.customer_id->orders.customer_id` |
| `ADDED` | `test-fixtures/correctness/postgres/sql-merge-using/expected-lineage.json` | `VALUE:DIRECT:source_orders.id->target_orders.source_order_id` |

### `ADDED_CANONICAL_DIRECTION_REPLACES_OLD` (2)

| Action | File | Item |
|---|---|---|
| `ADDED` | `test-fixtures/correctness/mysql/mysql-official-cte-nested-sql/expected-relations.json` | `FK_LIKE:purchase_order_approvals.purchase_order_id->purchase_orders.id:SQL_LOG_SUBQUERY_IN` |
| `ADDED` | `test-fixtures/correctness/mysql/mysql-official-derived-subquery-sql/expected-relations.json` | `FK_LIKE:shipments.order_id->orders.id:SQL_LOG_SUBQUERY_IN` |

### `REMOVED_OLD_DIRECTION_REPLACED_BY_CANONICAL` (2)

| Action | File | Item |
|---|---|---|
| `REMOVED` | `test-fixtures/correctness/mysql/mysql-official-cte-nested-sql/expected-relations.json` | `FK_LIKE:purchase_orders.id->purchase_order_approvals.purchase_order_id:SQL_LOG_SUBQUERY_IN` |
| `REMOVED` | `test-fixtures/correctness/mysql/mysql-official-derived-subquery-sql/expected-relations.json` | `FK_LIKE:orders.id->shipments.order_id:SQL_LOG_SUBQUERY_IN` |

### `DIAGNOSTIC_ADDED` (1)

| Action | File | Item |
|---|---|---|
| `ADDED` | `test-fixtures/correctness/mysql/basic-correctness-case-01-procedure-proc-worker-daily-distribution-sql/expected-diagnostics.json` | `DYNAMIC_SQL_UNRESOLVED` |

## 用户审核区

无。
