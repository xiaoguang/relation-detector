-- relation-detector-fixture-source: VIEW:portable.v_account_balance
SELECT accounts.id
FROM accounts
JOIN vouchers ON accounts.id = vouchers.id
-- relation-detector-fixture-end

-- relation-detector-fixture-source: VIEW:portable.v_dept_headcount
SELECT departments.id
FROM departments
JOIN employees ON departments.id = employees.id
-- relation-detector-fixture-end

-- relation-detector-fixture-source: VIEW:portable.v_employee_full
SELECT employees.id
FROM employees
JOIN departments ON employees.id = departments.id
-- relation-detector-fixture-end

-- relation-detector-fixture-source: VIEW:portable.v_inventory_summary
SELECT inventory.id
FROM inventory
JOIN products ON inventory.id = products.id
-- relation-detector-fixture-end

-- relation-detector-fixture-source: VIEW:portable.v_purchase_summary
SELECT purchase_orders.id
FROM purchase_orders
JOIN suppliers ON purchase_orders.id = suppliers.id
-- relation-detector-fixture-end

-- relation-detector-fixture-source: VIEW:portable.v_sales_summary
SELECT sales_orders.id
FROM sales_orders
JOIN customers ON sales_orders.id = customers.id
-- relation-detector-fixture-end
