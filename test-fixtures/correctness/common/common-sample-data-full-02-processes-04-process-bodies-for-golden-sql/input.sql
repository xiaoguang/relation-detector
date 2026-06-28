-- relation-detector-fixture-source: PROCEDURE:portable.sp_approve_requisition
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_approve_sales_return
INSERT INTO sales_returns (id, order_id)
SELECT sales_orders.id, sales_orders.id
FROM sales_orders;

SELECT sales_returns.id, sales_orders.id
FROM sales_returns
JOIN sales_orders ON sales_returns.order_id = sales_orders.id
WHERE sales_returns.id IN (
  SELECT sales_returns.id
  FROM sales_returns
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_assign_department_manager
INSERT INTO departments (id, parent_id)
SELECT departments.id, departments.id
FROM departments;

SELECT child.id, parent.id
FROM departments child
JOIN departments parent ON child.parent_id = parent.id
WHERE child.id IN (
  SELECT parent.id
  FROM departments parent
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_assign_service_ticket
INSERT INTO service_tickets (id, customer_id)
SELECT customers.id, customers.id
FROM customers;

SELECT service_tickets.id, customers.id
FROM service_tickets
JOIN customers ON service_tickets.customer_id = customers.id
WHERE service_tickets.id IN (
  SELECT service_tickets.id
  FROM service_tickets
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_auto_replenishment_suggestion
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_batch_expiry_tracking
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_calculate_cash_flow
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_calculate_commission
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_calculate_monthly_pl
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_category_sales_vs_expiry
INSERT INTO sales_orders (id, customer_id)
SELECT customers.id, customers.id
FROM customers;

SELECT sales_orders.id, customers.id
FROM sales_orders
JOIN customers ON sales_orders.customer_id = customers.id
WHERE sales_orders.id IN (
  SELECT sales_orders.id
  FROM sales_orders
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_change_product_price
INSERT INTO products (id, category_id)
SELECT product_categories.id, product_categories.id
FROM product_categories;

SELECT products.id, product_categories.id
FROM products
JOIN product_categories ON products.category_id = product_categories.id
WHERE products.id IN (
  SELECT products.id
  FROM products
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_check_customer_credit
INSERT INTO customers (id, id)
SELECT sales_orders.customer_id, sales_orders.customer_id
FROM sales_orders;

SELECT customers.id, sales_orders.customer_id
FROM customers
JOIN sales_orders ON customers.id = sales_orders.customer_id
WHERE customers.id IN (
  SELECT customers.id
  FROM customers
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_check_department_budget
INSERT INTO departments (id, parent_id)
SELECT departments.id, departments.id
FROM departments;

SELECT child.id, parent.id
FROM departments child
JOIN departments parent ON child.parent_id = parent.id
WHERE child.id IN (
  SELECT parent.id
  FROM departments parent
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_close_accounting_period
INSERT INTO accounting_periods (id, ledger_book_id)
SELECT ledger_books.id, ledger_books.id
FROM ledger_books;

SELECT accounting_periods.id, ledger_books.id
FROM accounting_periods
JOIN ledger_books ON accounting_periods.ledger_book_id = ledger_books.id
WHERE accounting_periods.id IN (
  SELECT accounting_periods.id
  FROM accounting_periods
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_compare_suppliers_for_product
INSERT INTO suppliers (id, id)
SELECT supplier_products.supplier_id, supplier_products.supplier_id
FROM supplier_products;

SELECT suppliers.id, supplier_products.supplier_id
FROM suppliers
JOIN supplier_products ON suppliers.id = supplier_products.supplier_id
WHERE suppliers.id IN (
  SELECT suppliers.id
  FROM suppliers
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_contract_expiry_alert
INSERT INTO contracts (id, prepared_by)
SELECT employees.id, employees.id
FROM employees;

SELECT contracts.id, employees.id
FROM contracts
JOIN employees ON contracts.prepared_by = employees.id
WHERE contracts.id IN (
  SELECT contracts.id
  FROM contracts
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_create_batch
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_create_cashier_journal
INSERT INTO cashier_journals (id, account_id)
SELECT accounts.id, accounts.id
FROM accounts;

SELECT cashier_journals.id, accounts.id
FROM cashier_journals
JOIN accounts ON cashier_journals.account_id = accounts.id
WHERE cashier_journals.id IN (
  SELECT cashier_journals.id
  FROM cashier_journals
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_create_damage_report
INSERT INTO damage_reports (id, warehouse_id)
SELECT warehouses.id, warehouses.id
FROM warehouses;

SELECT damage_reports.id, warehouses.id
FROM damage_reports
JOIN warehouses ON damage_reports.warehouse_id = warehouses.id
WHERE damage_reports.id IN (
  SELECT damage_reports.id
  FROM damage_reports
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_create_department
INSERT INTO departments (id, parent_id)
SELECT departments.id, departments.id
FROM departments;

SELECT child.id, parent.id
FROM departments child
JOIN departments parent ON child.parent_id = parent.id
WHERE child.id IN (
  SELECT parent.id
  FROM departments parent
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_create_performance_review
INSERT INTO performance_reviews (id, employee_id)
SELECT employees.id, employees.id
FROM employees;

SELECT performance_reviews.id, employees.id
FROM performance_reviews
JOIN employees ON performance_reviews.employee_id = employees.id
WHERE performance_reviews.id IN (
  SELECT performance_reviews.id
  FROM performance_reviews
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_create_product
INSERT INTO products (id, category_id)
SELECT product_categories.id, product_categories.id
FROM product_categories;

SELECT products.id, product_categories.id
FROM products
JOIN product_categories ON products.category_id = product_categories.id
WHERE products.id IN (
  SELECT products.id
  FROM products
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_create_purchase_order
INSERT INTO purchase_orders (id, supplier_id)
SELECT suppliers.id, suppliers.id
FROM suppliers;

SELECT purchase_orders.id, suppliers.id
FROM purchase_orders
JOIN suppliers ON purchase_orders.supplier_id = suppliers.id
WHERE purchase_orders.id IN (
  SELECT purchase_orders.id
  FROM purchase_orders
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_create_purchase_requisition
INSERT INTO purchase_requisitions (id, department_id)
SELECT departments.id, departments.id
FROM departments;

SELECT purchase_requisitions.id, departments.id
FROM purchase_requisitions
JOIN departments ON purchase_requisitions.department_id = departments.id
WHERE purchase_requisitions.id IN (
  SELECT purchase_requisitions.id
  FROM purchase_requisitions
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_create_purchase_return
INSERT INTO purchase_returns (id, purchase_order_id)
SELECT purchase_orders.id, purchase_orders.id
FROM purchase_orders;

SELECT purchase_returns.id, purchase_orders.id
FROM purchase_returns
JOIN purchase_orders ON purchase_returns.purchase_order_id = purchase_orders.id
WHERE purchase_returns.id IN (
  SELECT purchase_returns.id
  FROM purchase_returns
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_create_reconciliation
INSERT INTO reconciliations (id, account_id)
SELECT accounts.id, accounts.id
FROM accounts;

SELECT reconciliations.id, accounts.id
FROM reconciliations
JOIN accounts ON reconciliations.account_id = accounts.id
WHERE reconciliations.id IN (
  SELECT reconciliations.id
  FROM reconciliations
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_create_sales_order
INSERT INTO sales_orders (id, customer_id)
SELECT customers.id, customers.id
FROM customers;

SELECT sales_orders.id, customers.id
FROM sales_orders
JOIN customers ON sales_orders.customer_id = customers.id
WHERE sales_orders.id IN (
  SELECT sales_orders.id
  FROM sales_orders
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_create_sales_return
INSERT INTO sales_returns (id, order_id)
SELECT sales_orders.id, sales_orders.id
FROM sales_orders;

SELECT sales_returns.id, sales_orders.id
FROM sales_returns
JOIN sales_orders ON sales_returns.order_id = sales_orders.id
WHERE sales_returns.id IN (
  SELECT sales_returns.id
  FROM sales_returns
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_create_settlement
INSERT INTO settlements (id, voucher_id)
SELECT vouchers.id, vouchers.id
FROM vouchers;

SELECT settlements.id, vouchers.id
FROM settlements
JOIN vouchers ON settlements.voucher_id = vouchers.id
WHERE settlements.id IN (
  SELECT settlements.id
  FROM settlements
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_create_shipment
INSERT INTO shipments (id, order_id)
SELECT sales_orders.id, sales_orders.id
FROM sales_orders;

SELECT shipments.id, sales_orders.id
FROM shipments
JOIN sales_orders ON shipments.order_id = sales_orders.id
WHERE shipments.id IN (
  SELECT shipments.id
  FROM shipments
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_create_stock_transfer
INSERT INTO stock_transfers (id, from_warehouse_id)
SELECT warehouses.id, warehouses.id
FROM warehouses;

SELECT stock_transfers.id, warehouses.id
FROM stock_transfers
JOIN warehouses ON stock_transfers.from_warehouse_id = warehouses.id
WHERE stock_transfers.id IN (
  SELECT stock_transfers.id
  FROM stock_transfers
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_create_voucher
INSERT INTO vouchers (id, prepared_by)
SELECT employees.id, employees.id
FROM employees;

SELECT vouchers.id, employees.id
FROM vouchers
JOIN employees ON vouchers.prepared_by = employees.id
WHERE vouchers.id IN (
  SELECT vouchers.id
  FROM vouchers
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_create_warehouse
INSERT INTO warehouses (id, manager_id)
SELECT employees.id, employees.id
FROM employees;

SELECT warehouses.id, employees.id
FROM warehouses
JOIN employees ON warehouses.manager_id = employees.id
WHERE warehouses.id IN (
  SELECT warehouses.id
  FROM warehouses
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_customer_category_trend_by_store
INSERT INTO customers (id, id)
SELECT sales_orders.customer_id, sales_orders.customer_id
FROM sales_orders;

SELECT customers.id, sales_orders.customer_id
FROM customers
JOIN sales_orders ON customers.id = sales_orders.customer_id
WHERE customers.id IN (
  SELECT customers.id
  FROM customers
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_customer_recent_orders
INSERT INTO customers (id, id)
SELECT sales_orders.customer_id, sales_orders.customer_id
FROM sales_orders;

SELECT customers.id, sales_orders.customer_id
FROM customers
JOIN sales_orders ON customers.id = sales_orders.customer_id
WHERE customers.id IN (
  SELECT customers.id
  FROM customers
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_customer_store_preference
INSERT INTO customers (id, id)
SELECT sales_orders.customer_id, sales_orders.customer_id
FROM sales_orders;

SELECT customers.id, sales_orders.customer_id
FROM customers
JOIN sales_orders ON customers.id = sales_orders.customer_id
WHERE customers.id IN (
  SELECT customers.id
  FROM customers
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_customer_store_purchase_history
INSERT INTO customers (id, id)
SELECT sales_orders.customer_id, sales_orders.customer_id
FROM sales_orders;

SELECT customers.id, sales_orders.customer_id
FROM customers
JOIN sales_orders ON customers.id = sales_orders.customer_id
WHERE customers.id IN (
  SELECT customers.id
  FROM customers
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_daily_expiry_alert
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_depreciate_assets
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_employee_salary_history
INSERT INTO employees (id, department_id)
SELECT departments.id, departments.id
FROM departments;

SELECT employees.id, departments.id
FROM employees
JOIN departments ON employees.department_id = departments.id
WHERE employees.id IN (
  SELECT employees.id
  FROM employees
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_evaluate_supplier
INSERT INTO suppliers (id, id)
SELECT supplier_products.supplier_id, supplier_products.supplier_id
FROM supplier_products;

SELECT suppliers.id, supplier_products.supplier_id
FROM suppliers
JOIN supplier_products ON suppliers.id = supplier_products.supplier_id
WHERE suppliers.id IN (
  SELECT suppliers.id
  FROM suppliers
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_execute_damage_report
INSERT INTO damage_reports (id, warehouse_id)
SELECT warehouses.id, warehouses.id
FROM warehouses;

SELECT damage_reports.id, warehouses.id
FROM damage_reports
JOIN warehouses ON damage_reports.warehouse_id = warehouses.id
WHERE damage_reports.id IN (
  SELECT damage_reports.id
  FROM damage_reports
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_expiry_heatmap
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_file_tax_return
INSERT INTO tax_filings (id, prepared_by)
SELECT employees.id, employees.id
FROM employees;

SELECT tax_filings.id, employees.id
FROM tax_filings
JOIN employees ON tax_filings.prepared_by = employees.id
WHERE tax_filings.id IN (
  SELECT tax_filings.id
  FROM tax_filings
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_find_best_supplier
INSERT INTO suppliers (id, id)
SELECT supplier_products.supplier_id, supplier_products.supplier_id
FROM supplier_products;

SELECT suppliers.id, supplier_products.supplier_id
FROM suppliers
JOIN supplier_products ON suppliers.id = supplier_products.supplier_id
WHERE suppliers.id IN (
  SELECT suppliers.id
  FROM suppliers
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_generate_all_business_data
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_generate_ar_aging
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_generate_attendance
INSERT INTO attendance (id, employee_id)
SELECT employees.id, employees.id
FROM employees;

SELECT attendance.id, employees.id
FROM attendance
JOIN employees ON attendance.employee_id = employees.id
WHERE attendance.id IN (
  SELECT attendance.id
  FROM attendance
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_generate_purchase_data
INSERT INTO purchase_orders (id, supplier_id)
SELECT suppliers.id, suppliers.id
FROM suppliers;

SELECT purchase_orders.id, suppliers.id
FROM purchase_orders
JOIN suppliers ON purchase_orders.supplier_id = suppliers.id
WHERE purchase_orders.id IN (
  SELECT purchase_orders.id
  FROM purchase_orders
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_generate_sales_data
INSERT INTO sales_orders (id, customer_id)
SELECT customers.id, customers.id
FROM customers;

SELECT sales_orders.id, customers.id
FROM sales_orders
JOIN customers ON sales_orders.customer_id = customers.id
WHERE sales_orders.id IN (
  SELECT sales_orders.id
  FROM sales_orders
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_grant_role_to_employee
INSERT INTO employees (id, department_id)
SELECT departments.id, departments.id
FROM departments;

SELECT employees.id, departments.id
FROM employees
JOIN departments ON employees.department_id = departments.id
WHERE employees.id IN (
  SELECT employees.id
  FROM employees
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_hire_employee
INSERT INTO employees (id, department_id)
SELECT departments.id, departments.id
FROM departments;

SELECT employees.id, departments.id
FROM employees
JOIN departments ON employees.department_id = departments.id
WHERE employees.id IN (
  SELECT employees.id
  FROM employees
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_inventory_turnover
INSERT INTO inventory (id, product_id)
SELECT products.id, products.id
FROM products;

SELECT inventory.id, products.id
FROM inventory
JOIN products ON inventory.product_id = products.id
WHERE inventory.id IN (
  SELECT inventory.id
  FROM inventory
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_issue_work_order_materials
INSERT INTO work_order_materials (id, work_order_id)
SELECT work_orders.id, work_orders.id
FROM work_orders;

SELECT work_order_materials.id, work_orders.id
FROM work_order_materials
JOIN work_orders ON work_order_materials.work_order_id = work_orders.id
WHERE work_order_materials.id IN (
  SELECT work_order_materials.id
  FROM work_order_materials
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_monthly_store_ranking
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_pick_and_pack
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_poor_attendance_report
INSERT INTO attendance (id, employee_id)
SELECT employees.id, employees.id
FROM employees;

SELECT attendance.id, employees.id
FROM attendance
JOIN employees ON attendance.employee_id = employees.id
WHERE attendance.id IN (
  SELECT attendance.id
  FROM attendance
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_post_stocktake
INSERT INTO stocktakes (id, warehouse_id)
SELECT warehouses.id, warehouses.id
FROM warehouses;

SELECT stocktakes.id, warehouses.id
FROM stocktakes
JOIN warehouses ON stocktakes.warehouse_id = warehouses.id
WHERE stocktakes.id IN (
  SELECT stocktakes.id
  FROM stocktakes
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_post_voucher
INSERT INTO vouchers (id, prepared_by)
SELECT employees.id, employees.id
FROM employees;

SELECT vouchers.id, employees.id
FROM vouchers
JOIN employees ON vouchers.prepared_by = employees.id
WHERE vouchers.id IN (
  SELECT vouchers.id
  FROM vouchers
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_process_approval
INSERT INTO approval_instances (id, workflow_id)
SELECT approval_workflows.id, approval_workflows.id
FROM approval_workflows;

SELECT approval_instances.id, approval_workflows.id
FROM approval_instances
JOIN approval_workflows ON approval_instances.workflow_id = approval_workflows.id
WHERE approval_instances.id IN (
  SELECT approval_instances.id
  FROM approval_instances
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_process_expired_batches
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_process_salary
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_process_sales_return_refund
INSERT INTO sales_returns (id, order_id)
SELECT sales_orders.id, sales_orders.id
FROM sales_orders;

SELECT sales_returns.id, sales_orders.id
FROM sales_returns
JOIN sales_orders ON sales_returns.order_id = sales_orders.id
WHERE sales_returns.id IN (
  SELECT sales_returns.id
  FROM sales_returns
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_product_batch_detail
INSERT INTO products (id, category_id)
SELECT product_categories.id, product_categories.id
FROM product_categories;

SELECT products.id, product_categories.id
FROM products
JOIN product_categories ON products.category_id = product_categories.id
WHERE products.id IN (
  SELECT products.id
  FROM products
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_promote_to_manager
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_receive_purchase
INSERT INTO purchase_orders (id, supplier_id)
SELECT suppliers.id, suppliers.id
FROM suppliers;

SELECT purchase_orders.id, suppliers.id
FROM purchase_orders
JOIN suppliers ON purchase_orders.supplier_id = suppliers.id
WHERE purchase_orders.id IN (
  SELECT purchase_orders.id
  FROM purchase_orders
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_resign_employee
INSERT INTO employees (id, department_id)
SELECT departments.id, departments.id
FROM departments;

SELECT employees.id, departments.id
FROM employees
JOIN departments ON employees.department_id = departments.id
WHERE employees.id IN (
  SELECT employees.id
  FROM employees
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_return_financial_impact
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_return_full_trace
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_return_rate_analysis
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_sales_performance_ranking
INSERT INTO sales_orders (id, customer_id)
SELECT customers.id, customers.id
FROM customers;

SELECT sales_orders.id, customers.id
FROM sales_orders
JOIN customers ON sales_orders.customer_id = customers.id
WHERE sales_orders.id IN (
  SELECT sales_orders.id
  FROM sales_orders
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_scan_serial_number
INSERT INTO serial_numbers (id, product_id)
SELECT products.id, products.id
FROM products;

SELECT serial_numbers.id, products.id
FROM serial_numbers
JOIN products ON serial_numbers.product_id = products.id
WHERE serial_numbers.id IN (
  SELECT serial_numbers.id
  FROM serial_numbers
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_settle_consignment
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_stocktake
INSERT INTO stocktakes (id, warehouse_id)
SELECT warehouses.id, warehouses.id
FROM warehouses;

SELECT stocktakes.id, warehouses.id
FROM stocktakes
JOIN warehouses ON stocktakes.warehouse_id = warehouses.id
WHERE stocktakes.id IN (
  SELECT stocktakes.id
  FROM stocktakes
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_store_audit_pl
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_store_bestsellers
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_store_dashboard
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_store_expiry_dashboard
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_store_performance_compare
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_store_product_affinity
INSERT INTO products (id, category_id)
SELECT product_categories.id, product_categories.id
FROM product_categories;

SELECT products.id, product_categories.id
FROM products
JOIN product_categories ON products.category_id = product_categories.id
WHERE products.id IN (
  SELECT products.id
  FROM products
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_store_sales_forecast
INSERT INTO sales_orders (id, customer_id)
SELECT customers.id, customers.id
FROM customers;

SELECT sales_orders.id, customers.id
FROM sales_orders
JOIN customers ON sales_orders.customer_id = customers.id
WHERE sales_orders.id IN (
  SELECT sales_orders.id
  FROM sales_orders
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_submit_approval
INSERT INTO approval_instances (id, workflow_id)
SELECT approval_workflows.id, approval_workflows.id
FROM approval_workflows;

SELECT approval_instances.id, approval_workflows.id
FROM approval_instances
JOIN approval_workflows ON approval_instances.workflow_id = approval_workflows.id
WHERE approval_instances.id IN (
  SELECT approval_instances.id
  FROM approval_instances
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_supplier_geographic_analysis
INSERT INTO suppliers (id, id)
SELECT supplier_products.supplier_id, supplier_products.supplier_id
FROM supplier_products;

SELECT suppliers.id, supplier_products.supplier_id
FROM suppliers
JOIN supplier_products ON suppliers.id = supplier_products.supplier_id
WHERE suppliers.id IN (
  SELECT suppliers.id
  FROM suppliers
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_three_way_matching
INSERT INTO three_way_matching (id, invoice_id)
SELECT invoices.id, invoices.id
FROM invoices;

SELECT three_way_matching.id, invoices.id
FROM three_way_matching
JOIN invoices ON three_way_matching.invoice_id = invoices.id
WHERE three_way_matching.id IN (
  SELECT three_way_matching.id
  FROM three_way_matching
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_transfer_inventory
INSERT INTO inventory (id, product_id)
SELECT products.id, products.id
FROM products;

SELECT inventory.id, products.id
FROM inventory
JOIN products ON inventory.product_id = products.id
WHERE inventory.id IN (
  SELECT inventory.id
  FROM inventory
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_update_supplier_metrics
INSERT INTO suppliers (id, id)
SELECT supplier_products.supplier_id, supplier_products.supplier_id
FROM supplier_products;

SELECT suppliers.id, supplier_products.supplier_id
FROM suppliers
JOIN supplier_products ON suppliers.id = supplier_products.supplier_id
WHERE suppliers.id IN (
  SELECT suppliers.id
  FROM suppliers
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_validate_promotion
INSERT INTO promotions (id, id)
SELECT promotion_products.promotion_id, promotion_products.promotion_id
FROM promotion_products;

SELECT promotions.id, promotion_products.promotion_id
FROM promotions
JOIN promotion_products ON promotions.id = promotion_products.promotion_id
WHERE promotions.id IN (
  SELECT promotions.id
  FROM promotions
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: FUNCTION:portable.fn_calculate_income_tax
SELECT tax_filings.id, employees.id
FROM tax_filings
JOIN employees ON tax_filings.prepared_by = employees.id
WHERE tax_filings.id IN (
  SELECT tax_filings.id
  FROM tax_filings
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: FUNCTION:portable.fn_convert_currency
SELECT audit_log.id
FROM audit_log;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: FUNCTION:portable.fn_employee_full_name
SELECT employees.id, departments.id
FROM employees
JOIN departments ON employees.department_id = departments.id
WHERE employees.id IN (
  SELECT employees.id
  FROM employees
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: FUNCTION:portable.fn_estimate_shipping_cost
SELECT audit_log.id
FROM audit_log;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: FUNCTION:portable.fn_get_attendance_rate
SELECT attendance.id, employees.id
FROM attendance
JOIN employees ON attendance.employee_id = employees.id
WHERE attendance.id IN (
  SELECT attendance.id
  FROM attendance
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: FUNCTION:portable.fn_get_customer_clv
SELECT customers.id, sales_orders.customer_id
FROM customers
JOIN sales_orders ON customers.id = sales_orders.customer_id
WHERE customers.id IN (
  SELECT customers.id
  FROM customers
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: FUNCTION:portable.fn_get_customer_credit_available
SELECT customers.id, sales_orders.customer_id
FROM customers
JOIN sales_orders ON customers.id = sales_orders.customer_id
WHERE customers.id IN (
  SELECT customers.id
  FROM customers
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: FUNCTION:portable.fn_get_customer_credit_score
SELECT customers.id, sales_orders.customer_id
FROM customers
JOIN sales_orders ON customers.id = sales_orders.customer_id
WHERE customers.id IN (
  SELECT customers.id
  FROM customers
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: FUNCTION:portable.fn_get_customer_repurchase_rate
SELECT customers.id, sales_orders.customer_id
FROM customers
JOIN sales_orders ON customers.id = sales_orders.customer_id
WHERE customers.id IN (
  SELECT customers.id
  FROM customers
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: FUNCTION:portable.fn_get_customer_status
SELECT customers.id, sales_orders.customer_id
FROM customers
JOIN sales_orders ON customers.id = sales_orders.customer_id
WHERE customers.id IN (
  SELECT customers.id
  FROM customers
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: FUNCTION:portable.fn_get_days_sales_outstanding
SELECT sales_orders.id, customers.id
FROM sales_orders
JOIN customers ON sales_orders.customer_id = customers.id
WHERE sales_orders.id IN (
  SELECT sales_orders.id
  FROM sales_orders
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: FUNCTION:portable.fn_get_employee_tenure
SELECT employees.id, departments.id
FROM employees
JOIN departments ON employees.department_id = departments.id
WHERE employees.id IN (
  SELECT employees.id
  FROM employees
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: FUNCTION:portable.fn_get_gross_margin
SELECT audit_log.id
FROM audit_log;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: FUNCTION:portable.fn_get_inspection_pass_rate
SELECT audit_log.id
FROM audit_log;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: FUNCTION:portable.fn_get_inventory_turnover_days
SELECT inventory.id, products.id
FROM inventory
JOIN products ON inventory.product_id = products.id
WHERE inventory.id IN (
  SELECT inventory.id
  FROM inventory
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: FUNCTION:portable.fn_get_monthly_sales
SELECT sales_orders.id, customers.id
FROM sales_orders
JOIN customers ON sales_orders.customer_id = customers.id
WHERE sales_orders.id IN (
  SELECT sales_orders.id
  FROM sales_orders
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: FUNCTION:portable.fn_get_product_stock
SELECT products.id, product_categories.id
FROM products
JOIN product_categories ON products.category_id = product_categories.id
WHERE products.id IN (
  SELECT products.id
  FROM products
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: FUNCTION:portable.fn_get_project_completion_pct
SELECT projects.id, departments.id
FROM projects
JOIN departments ON projects.department_id = departments.id
WHERE projects.id IN (
  SELECT projects.id
  FROM projects
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: FUNCTION:portable.fn_haversine_distance
SELECT audit_log.id
FROM audit_log;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: FUNCTION:portable.fn_supplier_score
SELECT suppliers.id, supplier_products.supplier_id
FROM suppliers
JOIN supplier_products ON suppliers.id = supplier_products.supplier_id
WHERE suppliers.id IN (
  SELECT suppliers.id
  FROM suppliers
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source: TRIGGER:portable.trg_audit_employee_insert
SELECT employees.id, departments.id
FROM employees
JOIN departments ON employees.department_id = departments.id
WHERE employees.id IN (
  SELECT employees.id
  FROM employees
);

INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: TRIGGER:portable.trg_audit_employee_update
SELECT employees.id, departments.id
FROM employees
JOIN departments ON employees.department_id = departments.id
WHERE employees.id IN (
  SELECT employees.id
  FROM employees
);

INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: TRIGGER:portable.trg_batch_exhausted
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: TRIGGER:portable.trg_customer_credit_check
SELECT customers.id, sales_orders.customer_id
FROM customers
JOIN sales_orders ON customers.id = sales_orders.customer_id
WHERE customers.id IN (
  SELECT customers.id
  FROM customers
);

INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: TRIGGER:portable.trg_inventory_transaction_after_insert
SELECT inventory_transactions.id, products.id
FROM inventory_transactions
JOIN products ON inventory_transactions.product_id = products.id
WHERE inventory_transactions.id IN (
  SELECT inventory_transactions.id
  FROM inventory_transactions
);

INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: TRIGGER:portable.trg_inventory_update_batch
SELECT inventory.id, products.id
FROM inventory
JOIN products ON inventory.product_id = products.id
WHERE inventory.id IN (
  SELECT inventory.id
  FROM inventory
);

INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: TRIGGER:portable.trg_purchase_order_received
SELECT purchase_orders.id, suppliers.id
FROM purchase_orders
JOIN suppliers ON purchase_orders.supplier_id = suppliers.id
WHERE purchase_orders.id IN (
  SELECT purchase_orders.id
  FROM purchase_orders
);

INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: TRIGGER:portable.trg_requisition_status_change
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: TRIGGER:portable.trg_salary_payment_after_insert
SELECT salary_payments.id, employees.id
FROM salary_payments
JOIN employees ON salary_payments.employee_id = employees.id
WHERE salary_payments.id IN (
  SELECT salary_payments.id
  FROM salary_payments
);

INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: TRIGGER:portable.trg_sales_order_delivered
SELECT sales_orders.id, customers.id
FROM sales_orders
JOIN customers ON sales_orders.customer_id = customers.id
WHERE sales_orders.id IN (
  SELECT sales_orders.id
  FROM sales_orders
);

INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: TRIGGER:portable.trg_sales_return_approved
SELECT sales_returns.id, sales_orders.id
FROM sales_returns
JOIN sales_orders ON sales_returns.order_id = sales_orders.id
WHERE sales_returns.id IN (
  SELECT sales_returns.id
  FROM sales_returns
);

INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: TRIGGER:portable.trg_voucher_before_post
SELECT vouchers.id, employees.id
FROM vouchers
JOIN employees ON vouchers.prepared_by = employees.id
WHERE vouchers.id IN (
  SELECT vouchers.id
  FROM vouchers
);

INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_gen_attendance
INSERT INTO attendance (id)
SELECT employees.id
FROM employees;

SELECT attendance.id
FROM attendance;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_gen_batches_inventory
INSERT INTO inventory (id)
SELECT employees.id
FROM employees;

SELECT inventory.id
FROM inventory;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_gen_customers
INSERT INTO customers (id)
SELECT employees.id
FROM employees;

SELECT customers.id
FROM customers;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_gen_employees
INSERT INTO employees (id)
SELECT departments.id
FROM departments;

SELECT employees.id
FROM employees;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_gen_finance_data
INSERT INTO vouchers (id)
SELECT employees.id
FROM employees;

SELECT vouchers.id
FROM vouchers;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_gen_org_structure
INSERT INTO departments (id)
SELECT employees.id
FROM employees;

SELECT departments.id
FROM departments;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_gen_other_data
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;

SELECT audit_log.id
FROM audit_log;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_gen_products
INSERT INTO products (id)
SELECT employees.id
FROM employees;

SELECT products.id
FROM products;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_gen_purchase_orders
INSERT INTO purchase_orders (id)
SELECT employees.id
FROM employees;

SELECT purchase_orders.id
FROM purchase_orders;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_gen_sales_orders
INSERT INTO sales_orders (id)
SELECT employees.id
FROM employees;

SELECT sales_orders.id
FROM sales_orders;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_gen_suppliers
INSERT INTO suppliers (id)
SELECT employees.id
FROM employees;

SELECT suppliers.id
FROM suppliers;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:portable.sp_generate_massive_data
INSERT INTO audit_log (id)
SELECT employees.id
FROM employees;

SELECT audit_log.id
FROM audit_log;
-- relation-detector-fixture-end

