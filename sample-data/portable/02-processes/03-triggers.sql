-- Portable trigger declarations. These are object-catalog examples; common golden consumes the body blocks file.

CREATE TRIGGER trg_audit_employee_insert
AFTER INSERT ON employees
REFERENCING NEW ROW AS new_row
FOR EACH ROW
BEGIN ATOMIC
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
END;

CREATE TRIGGER trg_audit_employee_update
AFTER INSERT ON employees
REFERENCING NEW ROW AS new_row
FOR EACH ROW
BEGIN ATOMIC
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
END;

CREATE TRIGGER trg_batch_exhausted
AFTER INSERT ON audit_log
REFERENCING NEW ROW AS new_row
FOR EACH ROW
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE TRIGGER trg_customer_credit_check
AFTER INSERT ON customers
REFERENCING NEW ROW AS new_row
FOR EACH ROW
BEGIN ATOMIC
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
END;

CREATE TRIGGER trg_inventory_transaction_after_insert
AFTER INSERT ON inventory_transactions
REFERENCING NEW ROW AS new_row
FOR EACH ROW
BEGIN ATOMIC
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
END;

CREATE TRIGGER trg_inventory_update_batch
AFTER INSERT ON inventory
REFERENCING NEW ROW AS new_row
FOR EACH ROW
BEGIN ATOMIC
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
END;

CREATE TRIGGER trg_purchase_order_received
AFTER INSERT ON purchase_orders
REFERENCING NEW ROW AS new_row
FOR EACH ROW
BEGIN ATOMIC
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
END;

CREATE TRIGGER trg_requisition_status_change
AFTER INSERT ON audit_log
REFERENCING NEW ROW AS new_row
FOR EACH ROW
BEGIN ATOMIC
  INSERT INTO audit_log (id)
  SELECT employees.id
  FROM employees;
END;

CREATE TRIGGER trg_salary_payment_after_insert
AFTER INSERT ON salary_payments
REFERENCING NEW ROW AS new_row
FOR EACH ROW
BEGIN ATOMIC
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
END;

CREATE TRIGGER trg_sales_order_delivered
AFTER INSERT ON sales_orders
REFERENCING NEW ROW AS new_row
FOR EACH ROW
BEGIN ATOMIC
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
END;

CREATE TRIGGER trg_sales_return_approved
AFTER INSERT ON sales_returns
REFERENCING NEW ROW AS new_row
FOR EACH ROW
BEGIN ATOMIC
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
END;

CREATE TRIGGER trg_voucher_before_post
AFTER INSERT ON vouchers
REFERENCING NEW ROW AS new_row
FOR EACH ROW
BEGIN ATOMIC
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
END;
