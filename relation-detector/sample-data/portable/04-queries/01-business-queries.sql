-- hr_employee_department
SELECT employees.id, departments.id
FROM employees
JOIN departments ON employees.department_id = departments.id
WHERE EXISTS (
  SELECT 1
  FROM departments
  WHERE departments.id = employees.department_id
);

-- employee_position
SELECT employees.id, positions.id
FROM employees
JOIN positions ON employees.position_id = positions.id
WHERE EXISTS (
  SELECT 1
  FROM positions
  WHERE positions.id = employees.position_id
);

-- role_permission
SELECT role_permissions.id, permissions.id
FROM role_permissions
JOIN permissions ON role_permissions.permission_id = permissions.id
WHERE EXISTS (
  SELECT 1
  FROM permissions
  WHERE permissions.id = role_permissions.permission_id
);

-- supplier_product
SELECT supplier_products.id, suppliers.id
FROM supplier_products
JOIN suppliers ON supplier_products.supplier_id = suppliers.id
WHERE EXISTS (
  SELECT 1
  FROM suppliers
  WHERE suppliers.id = supplier_products.supplier_id
);

-- product_inventory
SELECT inventory.id, products.id
FROM inventory
JOIN products ON inventory.product_id = products.id
WHERE EXISTS (
  SELECT 1
  FROM products
  WHERE products.id = inventory.product_id
);

-- purchase_order_supplier
SELECT purchase_orders.id, suppliers.id
FROM purchase_orders
JOIN suppliers ON purchase_orders.supplier_id = suppliers.id
WHERE EXISTS (
  SELECT 1
  FROM suppliers
  WHERE suppliers.id = purchase_orders.supplier_id
);

-- purchase_receipt_order
SELECT purchase_receipts.id, purchase_orders.id
FROM purchase_receipts
JOIN purchase_orders ON purchase_receipts.order_id = purchase_orders.id
WHERE EXISTS (
  SELECT 1
  FROM purchase_orders
  WHERE purchase_orders.id = purchase_receipts.order_id
);

-- sales_order_customer
SELECT sales_orders.id, customers.id
FROM sales_orders
JOIN customers ON sales_orders.customer_id = customers.id
WHERE EXISTS (
  SELECT 1
  FROM customers
  WHERE customers.id = sales_orders.customer_id
);

-- sales_item_order
SELECT sales_order_items.id, sales_orders.id
FROM sales_order_items
JOIN sales_orders ON sales_order_items.order_id = sales_orders.id
WHERE EXISTS (
  SELECT 1
  FROM sales_orders
  WHERE sales_orders.id = sales_order_items.order_id
);

-- return_order
SELECT sales_returns.id, sales_orders.id
FROM sales_returns
JOIN sales_orders ON sales_returns.order_id = sales_orders.id
WHERE EXISTS (
  SELECT 1
  FROM sales_orders
  WHERE sales_orders.id = sales_returns.order_id
);

-- voucher_account
SELECT voucher_items.id, accounts.id
FROM voucher_items
JOIN accounts ON voucher_items.account_id = accounts.id
WHERE EXISTS (
  SELECT 1
  FROM accounts
  WHERE accounts.id = voucher_items.account_id
);

-- shipment_order
SELECT shipments.id, sales_orders.id
FROM shipments
JOIN sales_orders ON shipments.order_id = sales_orders.id
WHERE EXISTS (
  SELECT 1
  FROM sales_orders
  WHERE sales_orders.id = shipments.order_id
);

-- invoice_customer
SELECT invoices.id, customers.id
FROM invoices
JOIN customers ON invoices.customer_id = customers.id
WHERE EXISTS (
  SELECT 1
  FROM customers
  WHERE customers.id = invoices.customer_id
);

-- work_order_product
SELECT work_orders.id, products.id
FROM work_orders
JOIN products ON work_orders.product_id = products.id
WHERE EXISTS (
  SELECT 1
  FROM products
  WHERE products.id = work_orders.product_id
);

-- stocktake_warehouse
SELECT stocktakes.id, warehouses.id
FROM stocktakes
JOIN warehouses ON stocktakes.warehouse_id = warehouses.id
WHERE EXISTS (
  SELECT 1
  FROM warehouses
  WHERE warehouses.id = stocktakes.warehouse_id
);

INSERT INTO invoices (id, customer_id, total_amount)
SELECT sales_orders.id, customers.id, sales_orders.total_amount
FROM sales_orders
JOIN customers ON sales_orders.customer_id = customers.id;

UPDATE inventory
SET quantity = quantity + 1
WHERE product_id IN (
  SELECT products.id
  FROM products
);
