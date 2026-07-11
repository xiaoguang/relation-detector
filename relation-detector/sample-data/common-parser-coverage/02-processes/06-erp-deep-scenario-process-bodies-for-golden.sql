-- Parser-ready process bodies for the ERP deep scenario extension.

-- relation-detector-fixture-source: PROCEDURE:portable.sp_run_mrp_for_plan
INSERT INTO mrp_runs (id, plan_id, created_by)
SELECT production_plans.id, production_plans.id, production_plans.planner_id
FROM production_plans;

INSERT INTO mrp_run_items (run_id, parent_product_id, component_product_id, suggested_supplier_id)
SELECT mrp_runs.id, production_plans.product_id, boms.child_product_id, supplier_products.supplier_id
FROM mrp_runs
JOIN production_plans ON production_plans.id = mrp_runs.plan_id
JOIN boms ON boms.parent_product_id = production_plans.product_id
LEFT JOIN supplier_products ON supplier_products.product_id = boms.child_product_id;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_calculate_work_order_actual_cost
INSERT INTO work_order_costs (work_order_id, material_cost, labor_cost, overhead_cost)
SELECT work_orders.id, material_issue_items.unit_cost, operation_reports.labor_minutes, operation_reports.machine_minutes
FROM work_orders
LEFT JOIN material_issues ON material_issues.work_order_id = work_orders.id
LEFT JOIN material_issue_items ON material_issue_items.issue_id = material_issues.id
LEFT JOIN work_order_operations ON work_order_operations.work_order_id = work_orders.id
LEFT JOIN operation_reports ON operation_reports.work_order_operation_id = work_order_operations.id;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_post_finished_goods_receipt
INSERT INTO inventory_cost_layers (product_id, batch_id, warehouse_id, source_id)
SELECT finished_goods_receipts.product_id, finished_goods_receipts.batch_id, finished_goods_receipts.warehouse_id, finished_goods_receipts.id
FROM finished_goods_receipts;

INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id, reference_id)
SELECT finished_goods_receipts.product_id, finished_goods_receipts.batch_id, finished_goods_receipts.warehouse_id, finished_goods_receipts.id
FROM finished_goods_receipts;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_create_ar_invoice_from_sales_order
INSERT INTO ar_invoices (sales_order_id, customer_id)
SELECT sales_orders.id, sales_orders.customer_id
FROM sales_orders
JOIN customers ON customers.id = sales_orders.customer_id;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_create_ap_invoice_from_purchase_order
INSERT INTO ap_invoices (purchase_order_id, supplier_id)
SELECT purchase_orders.id, purchase_orders.supplier_id
FROM purchase_orders
JOIN suppliers ON suppliers.id = purchase_orders.supplier_id;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_post_cogs_for_sales_order
INSERT INTO cogs_entries (sales_order_id, sales_order_item_id, product_id, batch_id)
SELECT sales_orders.id, sales_order_items.id, sales_order_items.product_id, sales_order_items.batch_id
FROM sales_orders
JOIN sales_order_items ON sales_order_items.order_id = sales_orders.id
JOIN products ON products.id = sales_order_items.product_id
LEFT JOIN inventory_cost_layers ON inventory_cost_layers.product_id = sales_order_items.product_id;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_generate_picking_task_for_order
INSERT INTO picking_tasks (sales_order_id, warehouse_id)
SELECT sales_orders.id, sales_orders.warehouse_id
FROM sales_orders;

INSERT INTO picking_task_items (picking_task_id, sales_order_item_id, product_id, batch_id, location_id)
SELECT picking_tasks.id, sales_order_items.id, sales_order_items.product_id, sales_order_items.batch_id, inventory_location_balances.location_id
FROM picking_tasks
JOIN sales_order_items ON sales_order_items.order_id = picking_tasks.sales_order_id
JOIN inventory_location_balances ON inventory_location_balances.product_id = sales_order_items.product_id;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_refresh_budget_usage
UPDATE budget_items
SET used_amount = voucher_items.amount
FROM account_subjects
JOIN accounts ON accounts.code = account_subjects.subject_code
JOIN voucher_items ON voucher_items.account_id = accounts.id
JOIN vouchers ON vouchers.id = voucher_items.voucher_id
WHERE account_subjects.id = budget_items.subject_id;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_apply_customer_master_data_change
UPDATE customers
SET address = master_data_change_items.new_value
FROM master_data_change_requests
JOIN master_data_change_items ON master_data_change_items.request_id = master_data_change_requests.id
WHERE master_data_change_requests.master_id = customers.id;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_issue_repair_order_parts
INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id, reference_id)
SELECT repair_order_parts.product_id, repair_order_parts.batch_id, repair_order_parts.issued_from_warehouse_id, repair_order_parts.repair_order_id
FROM repair_order_parts
JOIN repair_orders ON repair_orders.id = repair_order_parts.repair_order_id;

UPDATE repair_orders
SET actual_cost = repair_order_parts.unit_cost
FROM repair_order_parts
WHERE repair_order_parts.repair_order_id = repair_orders.id;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_refresh_semantic_dimensions
INSERT INTO category_dim (id, source_category_id, category_code, level1_name, level2_name, leaf_name, is_womenwear, effective_from, status)
SELECT product_categories.id, product_categories.id, product_categories.code, product_categories.name, product_categories.name, product_categories.name, FALSE, DATE '2026-01-01', 'active'
FROM product_categories;

INSERT INTO fiscal_calendar (calendar_date, fiscal_year, fiscal_quarter, fiscal_month, fiscal_month_name, period_code, period_start, period_end, is_current_fiscal_year, accounting_period_id)
SELECT sales_orders.order_date, 2026, 1, 2, '2026-02', '2026-02', DATE '2026-02-01', DATE '2026-02-28', TRUE, accounting_periods.id
FROM sales_orders
LEFT JOIN accounting_periods ON accounting_periods.period_code = '2026-02';

INSERT INTO region_dim (id, region_code, region_name, province, city, district, sales_region, region_level, is_active)
SELECT warehouses.id, warehouses.code, warehouses.name, warehouses.province, warehouses.city, warehouses.district, '华东', 'city', TRUE
FROM warehouses;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_record_customer_payment
INSERT INTO payment_receipts (id, receipt_no, receipt_type, party_type, party_id, account_id, receipt_date, amount, currency, status, handled_by)
SELECT sales_orders.id, sales_orders.order_no, 'customer_receipt', 'customer', sales_orders.customer_id, accounts.id, sales_orders.order_date, sales_orders.paid_amount, 'CNY', 'confirmed', sales_orders.salesperson_id
FROM sales_orders
JOIN accounts ON accounts.id = 1;

INSERT INTO payments (id, payment_no, customer_id, order_id, receipt_id, payment_date, amount, currency, payment_method, payment_status)
SELECT payment_receipts.id, payment_receipts.receipt_no, payment_receipts.party_id, payment_receipts.id, payment_receipts.id, payment_receipts.receipt_date, payment_receipts.amount, payment_receipts.currency, 'transfer', 'paid'
FROM payment_receipts
WHERE payment_receipts.party_type = 'customer';
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_rebuild_sales_fact
INSERT INTO sales_fact (id, order_id, order_item_id, customer_id, product_id, category_dim_id, warehouse_id, region_dim_id, fiscal_date, payment_id, quantity_sold, sales_amount, paid_amount, refund_amount, net_sales_amount, gross_margin_amount, order_status, sales_channel)
SELECT sales_order_items.id, sales_orders.id, sales_order_items.id, sales_orders.customer_id, sales_order_items.product_id, category_dim.id, sales_orders.warehouse_id, region_dim.id, sales_orders.order_date, payments.id, sales_order_items.quantity, sales_order_items.amount, payments.amount, sales_returns.refund_amount, sales_order_items.amount - sales_returns.refund_amount, sales_order_items.amount - products.purchase_price, sales_orders.status, 'direct'
FROM sales_order_items
JOIN sales_orders ON sales_orders.id = sales_order_items.order_id
JOIN products ON products.id = sales_order_items.product_id
JOIN category_dim ON category_dim.source_category_id = products.category_id
JOIN warehouses ON warehouses.id = sales_orders.warehouse_id
JOIN region_dim ON region_dim.province = warehouses.province
JOIN fiscal_calendar ON fiscal_calendar.calendar_date = sales_orders.order_date
LEFT JOIN payments ON payments.order_id = sales_orders.id
LEFT JOIN sales_returns ON sales_returns.order_id = sales_orders.id;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_onboard_employee_full
INSERT INTO employees (id, employee_no, name, gender, id_card, phone, birth_date, hire_date, department_id, position_id, manager_id, salary)
SELECT positions.id, positions.code, positions.name, 'F', positions.code, positions.code, DATE '1990-01-01', CURRENT_DATE, departments.id, positions.id, departments.manager_id, (positions.min_salary + positions.max_salary) / 2
FROM departments
JOIN positions ON positions.department_id = departments.id;

INSERT INTO employee_roles (employee_id, role_id)
SELECT employees.id, roles.id
FROM employees
JOIN roles ON roles.id = 1;

INSERT INTO audit_log (employee_id, action, target_type, target_id)
SELECT employees.id, 'onboard_employee_full', 'employee', employees.id
FROM employees;
-- relation-detector-fixture-end
