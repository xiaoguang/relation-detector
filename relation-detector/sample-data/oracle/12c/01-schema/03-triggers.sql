-- ============================================================
-- ERP系统触发器 (Oracle 12c)
-- 用途:
--   trg_audit_*: 自动记录关键表的变更到audit_log
--   trg_inventory_*: 库存变动时自动更新批号库存和盘点日期
--   trg_batch_*: 批号耗尽/过期时自动更新状态
--   trg_sales_*: 销售状态变化时同步客户余额和库存相关状态
--   trg_purchase_*: 采购收货时同步供应商应付账户
-- ============================================================

CREATE OR REPLACE TRIGGER trg_audit_employee_insert
AFTER INSERT ON employees
FOR EACH ROW
BEGIN
    INSERT INTO audit_log (employee_id, action, target_type, target_id, new_value)
    VALUES (:NEW.id, 'INSERT', 'employee', :NEW.id,
            'name=' || :NEW.name || '; employee_no=' || :NEW.employee_no ||
            '; department_id=' || TO_CHAR(:NEW.department_id) ||
            '; salary=' || TO_CHAR(:NEW.salary) || '; status=' || :NEW.status);
END;
/

CREATE OR REPLACE TRIGGER trg_audit_employee_update
AFTER UPDATE ON employees
FOR EACH ROW
BEGIN
    IF :OLD.salary <> :NEW.salary OR :OLD.status <> :NEW.status
       OR :OLD.department_id <> :NEW.department_id OR :OLD.position_id <> :NEW.position_id THEN
        INSERT INTO audit_log (employee_id, action, target_type, target_id, old_value, new_value)
        VALUES (:NEW.id, 'UPDATE', 'employee', :NEW.id,
                'salary=' || TO_CHAR(:OLD.salary) || '; status=' || :OLD.status ||
                '; department_id=' || TO_CHAR(:OLD.department_id) ||
                '; position_id=' || TO_CHAR(:OLD.position_id),
                'salary=' || TO_CHAR(:NEW.salary) || '; status=' || :NEW.status ||
                '; department_id=' || TO_CHAR(:NEW.department_id) ||
                '; position_id=' || TO_CHAR(:NEW.position_id));
    END IF;
END;
/

CREATE OR REPLACE TRIGGER trg_inventory_update_batch
AFTER UPDATE ON inventory
FOR EACH ROW
BEGIN
    IF :NEW.quantity <> :OLD.quantity AND :NEW.batch_id IS NOT NULL THEN
        UPDATE product_batches
        SET current_qty = :NEW.quantity
        WHERE id = :NEW.batch_id;
    END IF;

    IF :NEW.quantity <> :OLD.quantity THEN
        UPDATE inventory
        SET last_stocktake_date = CURRENT_DATE
        WHERE id = :NEW.id AND last_stocktake_date IS NULL;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER trg_batch_exhausted
AFTER UPDATE ON product_batches
FOR EACH ROW
BEGIN
    IF :NEW.current_qty <= 0 AND :OLD.current_qty > 0 AND :NEW.status = 'active' THEN
        UPDATE product_batches SET status = 'exhausted' WHERE id = :NEW.id;
    END IF;

    IF :NEW.expiry_date <= CURRENT_DATE AND :NEW.status = 'active' THEN
        UPDATE product_batches SET status = 'expired' WHERE id = :NEW.id;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER trg_sales_order_delivered
AFTER UPDATE ON sales_orders
FOR EACH ROW
BEGIN
    IF :NEW.status = 'delivered' AND :OLD.status <> 'delivered' THEN
        UPDATE customers
        SET balance = balance + (:NEW.total_amount - :NEW.paid_amount)
        WHERE id = :NEW.customer_id;
    END IF;

    IF :NEW.status = 'cancelled' AND :OLD.status <> 'cancelled' THEN
        UPDATE sales_orders
        SET status = 'cancelled'
        WHERE id = :NEW.id;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER trg_purchase_order_received
AFTER UPDATE ON purchase_orders
FOR EACH ROW
BEGIN
    IF :NEW.status = 'received' AND :OLD.status <> 'received' THEN
        UPDATE accounts
        SET current_balance = current_balance + (:NEW.total_amount - :NEW.paid_amount)
        WHERE code = '220201';
    END IF;
END;
/

CREATE OR REPLACE TRIGGER trg_sales_return_approved
AFTER UPDATE ON sales_returns
FOR EACH ROW
BEGIN
    IF :NEW.status = 'approved' AND :OLD.status = 'pending' THEN
        UPDATE sales_orders SET status = 'returned' WHERE id = :NEW.order_id;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER trg_inventory_transaction_after_insert
AFTER INSERT ON inventory_transactions
FOR EACH ROW
BEGIN
    UPDATE inventory
    SET updated_at = CURRENT_TIMESTAMP
    WHERE product_id = :NEW.product_id
      AND warehouse_id = :NEW.warehouse_id
      AND batch_id = :NEW.batch_id;
END;
/

CREATE OR REPLACE TRIGGER trg_requisition_status_change
AFTER UPDATE ON purchase_requisitions
FOR EACH ROW
BEGIN
    IF :NEW.status <> :OLD.status THEN
        INSERT INTO audit_log (action, target_type, target_id, old_value, new_value)
        VALUES ('status_change', 'purchase_requisition', :NEW.id,
                'status=' || :OLD.status,
                'status=' || :NEW.status);
    END IF;
END;
/

CREATE OR REPLACE TRIGGER trg_customer_credit_check
AFTER UPDATE ON customers
FOR EACH ROW
BEGIN
    IF :NEW.balance > :NEW.credit_limit * 2 AND :NEW.status = 'active' THEN
        UPDATE customers SET status = 'frozen' WHERE id = :NEW.id;
        INSERT INTO audit_log (action, target_type, target_id, old_value, new_value)
        VALUES ('auto_freeze', 'customer', :NEW.id,
                'status=active',
                'status=frozen; balance=' || TO_CHAR(:NEW.balance) || '; limit=' || TO_CHAR(:NEW.credit_limit));
    END IF;
END;
/
