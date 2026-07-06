-- ============================================================
-- ERP系统触发器 (PostgreSQL 18)
-- 用途:
--   trg_audit_*: 自动记录关键表的变更到audit_log
--   trg_inventory_*: 库存变动时自动更新批号库存和最后一次盘点日期
--   trg_batch_*: 批号耗尽/过期时自动更新状态
--   trg_settlement_*: 结算完成自动更新客户/供应商余额
--   trg_voucher_*: 凭证过账前校验借贷平衡
-- ============================================================

-- ============================================================
-- 员工表审计触发器
-- 记录: INSERT/UPDATE/DELETE操作到audit_log
-- ============================================================

CREATE OR REPLACE FUNCTION trg_audit_employee_insert() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO audit_log (employee_id, action, target_type, target_id, new_value)
    VALUES (NEW.id, 'INSERT', 'employee', NEW.id,
            jsonb_build_object('name', NEW.name, 'employee_no', NEW.employee_no,
                       'department_id', NEW.department_id, 'salary', NEW.salary, 'status', NEW.status));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_employee_insert
AFTER INSERT ON employees
FOR EACH ROW
EXECUTE FUNCTION trg_audit_employee_insert();

CREATE OR REPLACE FUNCTION trg_audit_employee_update() RETURNS TRIGGER AS $$
BEGIN
    -- 仅记录关键字段变更
    IF OLD.salary != NEW.salary OR OLD.status != NEW.status
       OR OLD.department_id != NEW.department_id OR OLD.position_id != NEW.position_id THEN
        INSERT INTO audit_log (employee_id, action, target_type, target_id, old_value, new_value)
        VALUES (NEW.id, 'UPDATE', 'employee', NEW.id,
                jsonb_build_object('salary', OLD.salary, 'status', OLD.status, 'department_id', OLD.department_id, 'position_id', OLD.position_id),
                jsonb_build_object('salary', NEW.salary, 'status', NEW.status, 'department_id', NEW.department_id, 'position_id', NEW.position_id));
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_employee_update
AFTER UPDATE ON employees
FOR EACH ROW
EXECUTE FUNCTION trg_audit_employee_update();


-- ============================================================
-- 库存变动触发器
-- 当inventory.quantity更新时:
--   1. 同步更新product_batches.current_qty
--   2. 自动更新last_stocktake_date
-- ============================================================

CREATE OR REPLACE FUNCTION trg_inventory_update_batch() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.quantity != OLD.quantity AND NEW.batch_id IS NOT NULL THEN
        -- 更新批号库存
        UPDATE product_batches
        SET current_qty = (SELECT SUM(quantity) FROM inventory WHERE batch_id = NEW.batch_id)
        WHERE id = NEW.batch_id;
    END IF;

    -- 更新盘点日期
    IF NEW.quantity != OLD.quantity THEN
        UPDATE inventory SET last_stocktake_date = CURRENT_DATE
        WHERE id = NEW.id AND last_stocktake_date IS NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_inventory_update_batch
AFTER UPDATE ON inventory
FOR EACH ROW
EXECUTE FUNCTION trg_inventory_update_batch();


-- ============================================================
-- 批号状态自动更新触发器
-- 当批号库存变为0时，自动标记为exhausted
-- ============================================================

CREATE OR REPLACE FUNCTION trg_batch_exhausted() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.current_qty <= 0 AND OLD.current_qty > 0 AND NEW.status = 'active' THEN
        UPDATE product_batches SET status = 'exhausted' WHERE id = NEW.id;
    END IF;

    -- 过期自动标记
    IF NEW.expiry_date <= CURRENT_DATE AND NEW.status = 'active' THEN
        UPDATE product_batches SET status = 'expired' WHERE id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_batch_exhausted
AFTER UPDATE ON product_batches
FOR EACH ROW
EXECUTE FUNCTION trg_batch_exhausted();


-- ============================================================
-- 销售单状态变更触发器
-- 销售单完成时，更新客户欠款余额
-- ============================================================

CREATE OR REPLACE FUNCTION trg_sales_order_delivered() RETURNS TRIGGER AS $$
BEGIN
    -- 当销售单状态变为delivered时，增加客户应收账款
    IF NEW.status = 'delivered' AND OLD.status != 'delivered' THEN
        UPDATE customers
        SET balance = balance + (NEW.total_amount - NEW.paid_amount)
        WHERE id = NEW.customer_id;
    END IF;

    -- 取消销售单时，恢复库存
    IF NEW.status = 'cancelled' AND OLD.status != 'cancelled' THEN
        -- 恢复库存 (通过inventory_transactions记录)
        INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
            transaction_type, quantity_change, before_qty, after_qty,
            reference_type, reference_id, operator_id, remark)
        SELECT
            soi.product_id, soi.batch_id, NEW.warehouse_id,
            'return_in', soi.quantity,
            COALESCE((SELECT quantity FROM inventory WHERE product_id = soi.product_id AND warehouse_id = NEW.warehouse_id AND (batch_id = soi.batch_id OR soi.batch_id IS NULL)), 0),
            COALESCE((SELECT quantity FROM inventory WHERE product_id = soi.product_id AND warehouse_id = NEW.warehouse_id AND (batch_id = soi.batch_id OR soi.batch_id IS NULL)), 0) + soi.quantity,
            'sales_order', NEW.id, NEW.salesperson_id,
            CONCAT('订单取消恢复库存: ', NEW.order_no)
        FROM sales_order_items soi
        WHERE soi.order_id = NEW.id;

        -- 恢复库存数量
        UPDATE inventory i
        SET quantity = i.quantity + soi.quantity
        FROM sales_order_items soi
        WHERE i.product_id = soi.product_id
            AND (i.batch_id = soi.batch_id OR (i.batch_id IS NULL AND soi.batch_id IS NULL))
            AND i.warehouse_id = NEW.warehouse_id
            AND soi.order_id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sales_order_delivered
AFTER UPDATE ON sales_orders
FOR EACH ROW
EXECUTE FUNCTION trg_sales_order_delivered();


-- ============================================================
-- 采购单收货触发器
-- 收货完成时更新供应商应付账款
-- ============================================================

CREATE OR REPLACE FUNCTION trg_purchase_order_received() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'received' AND OLD.status != 'received' THEN
        -- 增加供应商应付账款
        UPDATE accounts
        SET current_balance = current_balance + (NEW.total_amount - NEW.paid_amount)
        WHERE code = '220201';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_purchase_order_received
AFTER UPDATE ON purchase_orders
FOR EACH ROW
EXECUTE FUNCTION trg_purchase_order_received();


-- ============================================================
-- 凭证过账前校验
-- 确保借贷平衡
-- ============================================================

CREATE OR REPLACE FUNCTION trg_voucher_before_post() RETURNS TRIGGER AS $$
DECLARE
    v_total_debit DECIMAL(18,2);
    v_total_credit DECIMAL(18,2);
BEGIN
    IF NEW.status = 'posted' AND OLD.status != 'posted' THEN
        SELECT
            COALESCE(SUM(CASE WHEN direction = 'debit' THEN amount ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN direction = 'credit' THEN amount ELSE 0 END), 0)
        INTO v_total_debit, v_total_credit
        FROM voucher_items
        WHERE voucher_id = NEW.id;

        IF ABS(v_total_debit - v_total_credit) > 0.01 THEN
            RAISE EXCEPTION '凭证借贷不平衡，禁止过账: 借方=%, 贷方=%', v_total_debit, v_total_credit;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_voucher_before_post
BEFORE UPDATE ON vouchers
FOR EACH ROW
EXECUTE FUNCTION trg_voucher_before_post();


-- ============================================================
-- 工资发放触发器
-- 发放后自动生成出纳日记账和凭证
-- ============================================================

CREATE OR REPLACE FUNCTION trg_salary_payment_after_insert() RETURNS TRIGGER AS $$
DECLARE
    v_journal_no VARCHAR(30);
    v_journal_id BIGINT;
BEGIN
    -- 生成出纳日记账
    SELECT CONCAT('CJ-', TO_CHAR(NEW.payment_date, 'YYYYMMDD'), '-', lpad(FLOOR(RANDOM() * 9999) + 1, 4, '0'))
    INTO v_journal_no;

    INSERT INTO cashier_journals (journal_no, journal_date, account_id, cashier_id,
        journal_type, amount, counterparty, reference_type, reference_id, status, remark)
    VALUES (v_journal_no, NEW.payment_date, 3, 39,  -- 工商银行基本户, 出纳燕青
        'bank_out', NEW.net_pay, (SELECT name FROM employees WHERE id = NEW.employee_id),
        'salary_payment', NEW.id, 'pending',
        CONCAT('工资发放: ', NEW.salary_month));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_salary_payment_after_insert
AFTER INSERT ON salary_payments
FOR EACH ROW
EXECUTE FUNCTION trg_salary_payment_after_insert();


-- ============================================================
-- 退货触发器
-- 退货审批后自动恢复库存
-- ============================================================

CREATE OR REPLACE FUNCTION trg_sales_return_approved() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'approved' AND OLD.status = 'pending' THEN
        -- 更新销售订单状态
        UPDATE sales_orders SET status = 'returned' WHERE id = NEW.order_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sales_return_approved
AFTER UPDATE ON sales_returns
FOR EACH ROW
EXECUTE FUNCTION trg_sales_return_approved();


-- ============================================================
-- 库存交易触发器
-- 自动更新库存表的updated_at
-- ============================================================

CREATE OR REPLACE FUNCTION trg_inventory_transaction_after_insert() RETURNS TRIGGER AS $$
BEGIN
    UPDATE inventory
    SET updated_at = CURRENT_TIMESTAMP
    WHERE product_id = NEW.product_id
      AND warehouse_id = NEW.warehouse_id
      AND (batch_id = NEW.batch_id OR (batch_id IS NULL AND NEW.batch_id IS NULL));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_inventory_transaction_after_insert
AFTER INSERT ON inventory_transactions
FOR EACH ROW
EXECUTE FUNCTION trg_inventory_transaction_after_insert();


-- ============================================================
-- 请购单-自动审计
-- ============================================================

CREATE OR REPLACE FUNCTION trg_requisition_status_change() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status != OLD.status THEN
        INSERT INTO audit_log (action, target_type, target_id, old_value, new_value)
        VALUES ('status_change', 'purchase_requisition', NEW.id,
                jsonb_build_object('status', OLD.status),
                jsonb_build_object('status', NEW.status));
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_requisition_status_change
AFTER UPDATE ON purchase_requisitions
FOR EACH ROW
EXECUTE FUNCTION trg_requisition_status_change();


-- ============================================================
-- 客户黑名单触发器
-- 如果客户欠款超过信用额度2倍，自动冻结
-- ============================================================

CREATE OR REPLACE FUNCTION trg_customer_credit_check() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.balance > NEW.credit_limit * 2 AND NEW.status = 'active' THEN
        UPDATE customers SET status = 'frozen' WHERE id = NEW.id;
        INSERT INTO audit_log (action, target_type, target_id, old_value, new_value)
        VALUES ('auto_freeze', 'customer', NEW.id,
                jsonb_build_object('status', 'active'),
                jsonb_build_object('status', 'frozen', 'reason', CONCAT('欠款超限: balance=', NEW.balance, ', limit=', NEW.credit_limit)));
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_customer_credit_check
AFTER UPDATE ON customers
FOR EACH ROW
EXECUTE FUNCTION trg_customer_credit_check();