-- Portable SQL/PSM-style procedure declarations. Bodies are intentionally simple and mirror the parser-ready blocks in 04-process-bodies-for-golden.sql.

CREATE PROCEDURE sp_approve_requisition()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_approve_sales_return()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_assign_department_manager()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_assign_service_ticket()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_auto_replenishment_suggestion()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_batch_expiry_tracking()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_calculate_cash_flow()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_calculate_commission()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_calculate_monthly_pl()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_category_sales_vs_expiry()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_change_product_price()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_check_customer_credit()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_check_department_budget()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_close_accounting_period()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_compare_suppliers_for_product()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_contract_expiry_alert()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_create_batch()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_create_cashier_journal()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_create_damage_report()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_create_department()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_create_performance_review()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_create_product()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_create_purchase_order()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_create_purchase_requisition()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_create_purchase_return()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_create_reconciliation()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_create_sales_order()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_create_sales_return()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_create_settlement()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_create_shipment()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_create_stock_transfer()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_create_voucher()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_create_warehouse()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_customer_category_trend_by_store()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_customer_recent_orders()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_customer_store_preference()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_customer_store_purchase_history()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_daily_expiry_alert()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_depreciate_assets()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_employee_salary_history()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_evaluate_supplier()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_execute_damage_report()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_expiry_heatmap()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_file_tax_return()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_find_best_supplier()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_generate_all_business_data()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_generate_ar_aging()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_generate_attendance()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_generate_purchase_data()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_generate_sales_data()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_grant_role_to_employee()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_hire_employee()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_inventory_turnover()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_issue_work_order_materials()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_monthly_store_ranking()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_pick_and_pack()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_poor_attendance_report()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_post_stocktake()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_post_voucher()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_process_approval()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_process_expired_batches()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_process_salary()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_process_sales_return_refund()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_product_batch_detail()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_promote_to_manager()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_receive_purchase()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_resign_employee()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_return_financial_impact()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_return_full_trace()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_return_rate_analysis()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_sales_performance_ranking()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_scan_serial_number()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_settle_consignment()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_stocktake()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_store_audit_pl()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_store_bestsellers()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_store_dashboard()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_store_expiry_dashboard()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_store_performance_compare()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_store_product_affinity()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_store_sales_forecast()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_submit_approval()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_supplier_geographic_analysis()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_three_way_matching()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_transfer_inventory()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_update_supplier_metrics()
BEGIN ATOMIC
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
END;

CREATE PROCEDURE sp_validate_promotion()
BEGIN ATOMIC
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
END;

-- Procedures originally declared from MySQL data-generation scripts.

CREATE PROCEDURE sp_gen_attendance()
BEGIN ATOMIC
  INSERT INTO attendance (id)
  SELECT employees.id
  FROM employees;

  SELECT attendance.id
  FROM attendance;
END;

CREATE PROCEDURE sp_gen_batches_inventory()
BEGIN ATOMIC
  INSERT INTO inventory (id)
  SELECT employees.id
  FROM employees;

  SELECT inventory.id
  FROM inventory;
END;

CREATE PROCEDURE sp_gen_customers()
BEGIN ATOMIC
  INSERT INTO customers (id)
  SELECT employees.id
  FROM employees;

  SELECT customers.id
  FROM customers;
END;

CREATE PROCEDURE sp_gen_employees()
BEGIN ATOMIC
  INSERT INTO employees (id)
  SELECT departments.id
  FROM departments;

  SELECT employees.id
  FROM employees;
END;

CREATE PROCEDURE sp_gen_finance_data()
BEGIN ATOMIC
  INSERT INTO vouchers (id)
  SELECT employees.id
  FROM employees;

  SELECT vouchers.id
  FROM vouchers;
END;

CREATE PROCEDURE sp_gen_org_structure()
BEGIN ATOMIC
  INSERT INTO departments (id)
  SELECT employees.id
  FROM employees;

  SELECT departments.id
  FROM departments;
END;

CREATE PROCEDURE sp_gen_other_data()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;

  SELECT audit_log.id
  FROM audit_log;
END;

CREATE PROCEDURE sp_gen_products()
BEGIN ATOMIC
  INSERT INTO products (id)
  SELECT employees.id
  FROM employees;

  SELECT products.id
  FROM products;
END;

CREATE PROCEDURE sp_gen_purchase_orders()
BEGIN ATOMIC
  INSERT INTO purchase_orders (id)
  SELECT employees.id
  FROM employees;

  SELECT purchase_orders.id
  FROM purchase_orders;
END;

CREATE PROCEDURE sp_gen_sales_orders()
BEGIN ATOMIC
  INSERT INTO sales_orders (id)
  SELECT employees.id
  FROM employees;

  SELECT sales_orders.id
  FROM sales_orders;
END;

CREATE PROCEDURE sp_gen_suppliers()
BEGIN ATOMIC
  INSERT INTO suppliers (id)
  SELECT employees.id
  FROM employees;

  SELECT suppliers.id
  FROM suppliers;
END;

CREATE PROCEDURE sp_generate_massive_data()
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;

  SELECT audit_log.id
  FROM audit_log;
END;
