-- Portable view catalog. These definitions document the six MySQL ERP views using simple SELECT shapes.

CREATE VIEW v_account_balance AS
SELECT accounts.id
FROM accounts
JOIN vouchers ON accounts.id = vouchers.id;

CREATE VIEW v_dept_headcount AS
SELECT departments.id
FROM departments
JOIN employees ON departments.id = employees.id;

CREATE VIEW v_employee_full AS
SELECT employees.id
FROM employees
JOIN departments ON employees.id = departments.id;

CREATE VIEW v_inventory_summary AS
SELECT inventory.id
FROM inventory
JOIN products ON inventory.id = products.id;

CREATE VIEW v_purchase_summary AS
SELECT purchase_orders.id
FROM purchase_orders
JOIN suppliers ON purchase_orders.id = suppliers.id;

CREATE VIEW v_sales_summary AS
SELECT sales_orders.id
FROM sales_orders
JOIN customers ON sales_orders.id = customers.id;
