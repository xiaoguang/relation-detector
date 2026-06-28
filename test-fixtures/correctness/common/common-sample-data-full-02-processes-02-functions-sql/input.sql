-- relation-detector-fixture-source: ROUTINE:portable.fn_calculate_income_tax
CREATE FUNCTION fn_calculate_income_tax() RETURNS BIGINT
BEGIN ATOMIC
  SELECT tax_filings.id, employees.id
  FROM tax_filings
  JOIN employees ON tax_filings.prepared_by = employees.id
  WHERE tax_filings.id IN (
    SELECT tax_filings.id
    FROM tax_filings
  );
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:portable.fn_convert_currency
CREATE FUNCTION fn_convert_currency() RETURNS BIGINT
BEGIN ATOMIC
  SELECT audit_log.id
  FROM audit_log;
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:portable.fn_employee_full_name
CREATE FUNCTION fn_employee_full_name() RETURNS BIGINT
BEGIN ATOMIC
  SELECT employees.id, departments.id
  FROM employees
  JOIN departments ON employees.department_id = departments.id
  WHERE employees.id IN (
    SELECT employees.id
    FROM employees
  );
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:portable.fn_estimate_shipping_cost
CREATE FUNCTION fn_estimate_shipping_cost() RETURNS BIGINT
BEGIN ATOMIC
  SELECT audit_log.id
  FROM audit_log;
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:portable.fn_get_attendance_rate
CREATE FUNCTION fn_get_attendance_rate() RETURNS BIGINT
BEGIN ATOMIC
  SELECT attendance.id, employees.id
  FROM attendance
  JOIN employees ON attendance.employee_id = employees.id
  WHERE attendance.id IN (
    SELECT attendance.id
    FROM attendance
  );
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:portable.fn_get_customer_clv
CREATE FUNCTION fn_get_customer_clv() RETURNS BIGINT
BEGIN ATOMIC
  SELECT customers.id, sales_orders.customer_id
  FROM customers
  JOIN sales_orders ON customers.id = sales_orders.customer_id
  WHERE customers.id IN (
    SELECT customers.id
    FROM customers
  );
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:portable.fn_get_customer_credit_available
CREATE FUNCTION fn_get_customer_credit_available() RETURNS BIGINT
BEGIN ATOMIC
  SELECT customers.id, sales_orders.customer_id
  FROM customers
  JOIN sales_orders ON customers.id = sales_orders.customer_id
  WHERE customers.id IN (
    SELECT customers.id
    FROM customers
  );
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:portable.fn_get_customer_credit_score
CREATE FUNCTION fn_get_customer_credit_score() RETURNS BIGINT
BEGIN ATOMIC
  SELECT customers.id, sales_orders.customer_id
  FROM customers
  JOIN sales_orders ON customers.id = sales_orders.customer_id
  WHERE customers.id IN (
    SELECT customers.id
    FROM customers
  );
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:portable.fn_get_customer_repurchase_rate
CREATE FUNCTION fn_get_customer_repurchase_rate() RETURNS BIGINT
BEGIN ATOMIC
  SELECT customers.id, sales_orders.customer_id
  FROM customers
  JOIN sales_orders ON customers.id = sales_orders.customer_id
  WHERE customers.id IN (
    SELECT customers.id
    FROM customers
  );
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:portable.fn_get_customer_status
CREATE FUNCTION fn_get_customer_status() RETURNS BIGINT
BEGIN ATOMIC
  SELECT customers.id, sales_orders.customer_id
  FROM customers
  JOIN sales_orders ON customers.id = sales_orders.customer_id
  WHERE customers.id IN (
    SELECT customers.id
    FROM customers
  );
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:portable.fn_get_days_sales_outstanding
CREATE FUNCTION fn_get_days_sales_outstanding() RETURNS BIGINT
BEGIN ATOMIC
  SELECT sales_orders.id, customers.id
  FROM sales_orders
  JOIN customers ON sales_orders.customer_id = customers.id
  WHERE sales_orders.id IN (
    SELECT sales_orders.id
    FROM sales_orders
  );
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:portable.fn_get_employee_tenure
CREATE FUNCTION fn_get_employee_tenure() RETURNS BIGINT
BEGIN ATOMIC
  SELECT employees.id, departments.id
  FROM employees
  JOIN departments ON employees.department_id = departments.id
  WHERE employees.id IN (
    SELECT employees.id
    FROM employees
  );
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:portable.fn_get_gross_margin
CREATE FUNCTION fn_get_gross_margin() RETURNS BIGINT
BEGIN ATOMIC
  SELECT audit_log.id
  FROM audit_log;
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:portable.fn_get_inspection_pass_rate
CREATE FUNCTION fn_get_inspection_pass_rate() RETURNS BIGINT
BEGIN ATOMIC
  SELECT audit_log.id
  FROM audit_log;
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:portable.fn_get_inventory_turnover_days
CREATE FUNCTION fn_get_inventory_turnover_days() RETURNS BIGINT
BEGIN ATOMIC
  SELECT inventory.id, products.id
  FROM inventory
  JOIN products ON inventory.product_id = products.id
  WHERE inventory.id IN (
    SELECT inventory.id
    FROM inventory
  );
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:portable.fn_get_monthly_sales
CREATE FUNCTION fn_get_monthly_sales() RETURNS BIGINT
BEGIN ATOMIC
  SELECT sales_orders.id, customers.id
  FROM sales_orders
  JOIN customers ON sales_orders.customer_id = customers.id
  WHERE sales_orders.id IN (
    SELECT sales_orders.id
    FROM sales_orders
  );
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:portable.fn_get_product_stock
CREATE FUNCTION fn_get_product_stock() RETURNS BIGINT
BEGIN ATOMIC
  SELECT products.id, product_categories.id
  FROM products
  JOIN product_categories ON products.category_id = product_categories.id
  WHERE products.id IN (
    SELECT products.id
    FROM products
  );
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:portable.fn_get_project_completion_pct
CREATE FUNCTION fn_get_project_completion_pct() RETURNS BIGINT
BEGIN ATOMIC
  SELECT projects.id, departments.id
  FROM projects
  JOIN departments ON projects.department_id = departments.id
  WHERE projects.id IN (
    SELECT projects.id
    FROM projects
  );
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:portable.fn_haversine_distance
CREATE FUNCTION fn_haversine_distance() RETURNS BIGINT
BEGIN ATOMIC
  SELECT audit_log.id
  FROM audit_log;
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:portable.fn_supplier_score
CREATE FUNCTION fn_supplier_score() RETURNS BIGINT
BEGIN ATOMIC
  SELECT suppliers.id, supplier_products.supplier_id
  FROM suppliers
  JOIN supplier_products ON suppliers.id = supplier_products.supplier_id
  WHERE suppliers.id IN (
    SELECT suppliers.id
    FROM suppliers
  );
END;
-- relation-detector-fixture-end
