-- ============================================================
-- ERP系统存储过程 - Oracle 26ai
-- 包含: 部门管理, 货品管理, 员工管理, 请购, 采购, 进货入库,
--       销售, 退库, 工资发放, 审计, 对账, 出纳, 结算
-- ============================================================

-- ============================================================
-- 辅助函数: 生成随机工号/单号
-- ============================================================

CREATE OR REPLACE FUNCTION generate_employee_no()
RETURN VARCHAR2 AS
BEGIN
    RETURN TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || LPAD((FLOOR(DBMS_RANDOM.VALUE * 9999) + 1), 4, '0');
END;
/


-- ============================================================
-- 1. 部门管理
-- ============================================================

-- 插入部门
CREATE OR REPLACE PROCEDURE sp_create_department(
    p_parent_id NUMBER,
    p_name VARCHAR2,
    p_code VARCHAR2,
    p_budget NUMBER,
    p_headcount_plan NUMBER
)
AS
    v_new_id NUMBER(19);
BEGIN
    INSERT INTO departments (parent_id, name, code, budget, headcount_plan)
    VALUES (p_parent_id, p_name, p_code, p_budget, p_headcount_plan)
    RETURNING id INTO v_new_id;

    INSERT INTO audit_log (action, target_type, target_id, new_value)
    VALUES ('create_department', 'department', v_new_id,
            JSON_OBJECT('name' VALUE p_name, 'code' VALUE p_code, 'budget' VALUE p_budget));

    -- 返回新ID
    -- RAISE NOTICE 'new_department_id: %', v_new_id;
END;
/


-- 更新部门经理
CREATE OR REPLACE PROCEDURE sp_assign_department_manager(
    p_department_id NUMBER,
    p_manager_id NUMBER
)
AS
    v_old_manager_id NUMBER(19);
BEGIN
    SELECT manager_id INTO v_old_manager_id FROM departments WHERE id = p_department_id;

    UPDATE departments SET manager_id = p_manager_id WHERE id = p_department_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, old_value, new_value)
    VALUES ('assign_manager', 'department', p_department_id, p_manager_id,
            JSON_OBJECT('manager_id' VALUE v_old_manager_id),
            JSON_OBJECT('manager_id' VALUE p_manager_id));

    -- RAISE NOTICE 'success: %', FOUND;
END;
/


-- ============================================================
-- 2. 门店/仓库管理
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_warehouse(
    p_name VARCHAR2,
    p_code VARCHAR2,
    p_address VARCHAR2,
    p_manager_id NUMBER,
    p_type VARCHAR2,
    p_capacity_m3 NUMBER
)
AS
    v_new_id NUMBER(19);
BEGIN
    INSERT INTO warehouses (name, code, address, manager_id, type, capacity_m3)
    VALUES (p_name, p_code, p_address, p_manager_id, p_type, p_capacity_m3)
    RETURNING id INTO v_new_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_warehouse', 'warehouse', v_new_id, p_manager_id,
            JSON_OBJECT('name' VALUE p_name, 'code' VALUE p_code, 'type' VALUE p_type));

    -- RAISE NOTICE 'new_warehouse_id: %', v_new_id;
END;
/


-- ============================================================
-- 3. 货品管理
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_product(
    p_sku VARCHAR2,
    p_name VARCHAR2,
    p_category_id NUMBER,
    p_unit VARCHAR2,
    p_spec VARCHAR2,
    p_brand VARCHAR2,
    p_barcode VARCHAR2,
    p_purchase_price NUMBER,
    p_wholesale_price NUMBER,
    p_retail_price NUMBER,
    p_min_stock NUMBER,
    p_max_stock NUMBER,
    p_batch_managed NUMBER,
    p_shelf_life_days NUMBER
)
AS
    v_new_id NUMBER(19);
BEGIN
    INSERT INTO products (sku, name, category_id, unit, spec, brand, barcode,
        purchase_price, wholesale_price, retail_price, min_stock, max_stock,
        batch_managed, shelf_life_days)
    VALUES (p_sku, p_name, p_category_id, p_unit, p_spec, p_brand, p_barcode,
        p_purchase_price, p_wholesale_price, p_retail_price, p_min_stock, p_max_stock,
        p_batch_managed, p_shelf_life_days)
    RETURNING id INTO v_new_id;

    INSERT INTO audit_log (action, target_type, target_id, new_value)
    VALUES ('create_product', 'product', v_new_id,
            JSON_OBJECT('sku' VALUE p_sku, 'name' VALUE p_name, 'retail_price' VALUE p_retail_price));

    -- RAISE NOTICE 'new_product_id: %', v_new_id;
END;
/


-- 批量创建批号
CREATE OR REPLACE PROCEDURE sp_create_batch(
    p_product_id NUMBER,
    p_batch_no VARCHAR2,
    p_production_date DATE,
    p_expiry_date DATE,
    p_supplier_id NUMBER,
    p_purchase_price NUMBER,
    p_initial_qty NUMBER
)
AS
    v_new_id NUMBER(19);
BEGIN
    INSERT INTO product_batches (product_id, batch_no, production_date, expiry_date,
        supplier_id, purchase_price, initial_qty, current_qty)
    VALUES (p_product_id, p_batch_no, p_production_date, p_expiry_date,
        p_supplier_id, p_purchase_price, p_initial_qty, p_initial_qty)
    RETURNING id INTO v_new_id;

    -- RAISE NOTICE 'new_batch_id: %', v_new_id;
END;
/


-- ============================================================
-- 4. 员工管理
-- ============================================================

-- 入职员工
CREATE OR REPLACE PROCEDURE sp_hire_employee(
    p_name VARCHAR2,
    p_gender VARCHAR2,
    p_id_card VARCHAR2,
    p_phone VARCHAR2,
    p_email VARCHAR2,
    p_birth_date DATE,
    p_hire_date DATE,
    p_department_id NUMBER,
    p_position_id NUMBER,
    p_manager_id NUMBER,
    p_salary NUMBER,
    p_bank_name VARCHAR2,
    p_bank_account VARCHAR2,
    p_address VARCHAR2
)
AS
    v_employee_no VARCHAR2(20);
    v_new_id NUMBER(19);
    v_pos_headcount NUMBER(10);
    v_current_count NUMBER(10);
BEGIN
    -- 检查编制
    SELECT headcount INTO v_pos_headcount FROM positions WHERE id = p_position_id;
    SELECT COUNT(*) INTO v_current_count FROM employees
    WHERE position_id = p_position_id AND status IN ('active','probation','leave');

    IF v_current_count >= v_pos_headcount THEN
        RAISE_APPLICATION_ERROR(-20000, '该岗位编制已满，无法入职');
    END IF;

    -- 生成工号
    v_employee_no := generate_employee_no();

    INSERT INTO employees (employee_no, name, gender, id_card, phone, email,
        birth_date, hire_date, department_id, position_id, manager_id, salary,
        social_security_base, housing_fund_base, bank_name, bank_account, address, status)
    VALUES (v_employee_no, p_name, p_gender, p_id_card, p_phone, p_email,
        p_birth_date, p_hire_date, p_department_id, p_position_id, p_manager_id, p_salary,
        p_salary, p_salary, p_bank_name, p_bank_account, p_address, 'probation')
    RETURNING id INTO v_new_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('hire_employee', 'employee', v_new_id, v_new_id,
            JSON_OBJECT('name' VALUE p_name, 'employee_no' VALUE v_employee_no, 'department_id' VALUE p_department_id, 'salary' VALUE p_salary));

    -- RAISE NOTICE 'new_employee_id: %, employee_no: %', v_new_id, v_employee_no;
END;
/


-- 晋升为经理
CREATE OR REPLACE PROCEDURE sp_promote_to_manager(
    p_employee_id NUMBER,
    p_new_position_id NUMBER,
    p_new_salary NUMBER,
    p_effective_date DATE
)
AS
    v_old_salary NUMBER(12,2);
    v_old_position_id NUMBER(19);
    v_department_id NUMBER(19);
    v_pos_name VARCHAR2(100);
BEGIN
    SELECT salary, position_id, department_id
    INTO v_old_salary, v_old_position_id, v_department_id
    FROM employees WHERE id = p_employee_id;

    IF v_old_salary IS NULL THEN
        RAISE_APPLICATION_ERROR(-20000, '员工不存在');
    END IF;

    -- 更新员工职位和薪资
    UPDATE employees
    SET position_id = p_new_position_id,
        salary = p_new_salary,
        social_security_base = p_new_salary,
        housing_fund_base = p_new_salary,
        status = 'active'
    WHERE id = p_employee_id;

    -- 如果是管理岗位，更新部门经理
    SELECT name INTO v_pos_name FROM positions WHERE id = p_new_position_id;
    IF v_pos_name LIKE '%经理%' THEN
        UPDATE departments SET manager_id = p_employee_id WHERE id = v_department_id;
    END IF;

    -- 记录薪资变动
    INSERT INTO employee_salary_log (employee_id, old_salary, new_salary, change_reason, effective_date)
    VALUES (p_employee_id, v_old_salary, p_new_salary, '晋升为经理', p_effective_date);

    INSERT INTO audit_log (action, target_type, target_id, employee_id, old_value, new_value)
    VALUES ('promote_to_manager', 'employee', p_employee_id, p_employee_id,
            JSON_OBJECT('position_id' VALUE v_old_position_id, 'salary' VALUE v_old_salary),
            JSON_OBJECT('position_id' VALUE p_new_position_id, 'salary' VALUE p_new_salary));

    -- RAISE NOTICE 'success: %', FOUND;
END;
/


-- 员工离职
CREATE OR REPLACE PROCEDURE sp_resign_employee(
    p_employee_id NUMBER,
    p_resignation_date DATE,
    p_reason VARCHAR2
)
AS
    v_old_status VARCHAR2(40);
BEGIN
    SELECT status INTO v_old_status FROM employees WHERE id = p_employee_id;

    UPDATE employees
    SET status = 'resigned',
        resignation_date = p_resignation_date,
        resignation_reason = p_reason
    WHERE id = p_employee_id;

    -- 如果离职员工是部门经理，清除部门经理引用
    UPDATE departments SET manager_id = NULL WHERE manager_id = p_employee_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, old_value, new_value)
    VALUES ('resign_employee', 'employee', p_employee_id, p_employee_id,
            JSON_OBJECT('status' VALUE v_old_status),
            JSON_OBJECT('status' VALUE 'resigned', 'resignation_date' VALUE p_resignation_date, 'reason' VALUE p_reason));

    -- RAISE NOTICE 'success: %', FOUND;
END;
/


-- ============================================================
-- 5. 请购流程
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_purchase_requisition(
    p_department_id NUMBER,
    p_requester_id NUMBER,
    p_required_date DATE,
    p_urgency VARCHAR2,
    p_items_json CLOB,
    p_remark VARCHAR2
)
AS
    v_req_no VARCHAR2(30);
    v_req_id NUMBER(19);
    v_total NUMBER(18,2) := 0.00;
    v_idx NUMBER(10) := 0;
    v_item_count NUMBER(10);
    v_item CLOB;
    v_product_id NUMBER(19);
    v_qty NUMBER(10);
    v_price NUMBER(12,2);
BEGIN
    v_req_no := 'PR-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || LPAD((FLOOR(DBMS_RANDOM.VALUE * 9999) + 1), 4, '0');
    v_item_count := JSON_ARRAY_T.parse(p_items_json).get_size;

    IF v_item_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20000, '请购明细不能为空');
    END IF;

    INSERT INTO purchase_requisitions (requisition_no, department_id, requester_id,
        requisition_date, required_date, urgency, status, remark)
    VALUES (v_req_no, p_department_id, p_requester_id, CURRENT_DATE, p_required_date,
        p_urgency, 'pending', p_remark)
    RETURNING id INTO v_req_id;

    -- 遍历JSON数组
    FOR v_item IN (SELECT item_json FROM JSON_TABLE(p_items_json, '$[*]' COLUMNS (item_json CLOB FORMAT JSON PATH '$')))
    LOOP
        v_product_id := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.product_id'));
        v_qty := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.quantity'));
        v_price := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.estimated_price'));

        INSERT INTO purchase_requisition_items (requisition_id, product_id, quantity, estimated_price)
        VALUES (v_req_id, v_product_id, v_qty, v_price);

        v_total := v_total + (v_qty * v_price);
    END LOOP;

    UPDATE purchase_requisitions SET total_amount = v_total WHERE id = v_req_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_requisition', 'purchase_requisition', v_req_id, p_requester_id,
            JSON_OBJECT('requisition_no' VALUE v_req_no, 'total_amount' VALUE v_total, 'urgency' VALUE p_urgency));

    -- RAISE NOTICE 'requisition_id: %, requisition_no: %, total_amount: %', v_req_id, v_req_no, v_total;
END;
/


-- 审批请购单
CREATE OR REPLACE PROCEDURE sp_approve_requisition(
    p_requisition_id NUMBER,
    p_approver_id NUMBER,
    p_approved NUMBER,
    p_remark VARCHAR2
)
AS
    v_old_status VARCHAR2(20);
    v_new_status VARCHAR2(20);
BEGIN
    SELECT status INTO v_old_status FROM purchase_requisitions WHERE id = p_requisition_id;

    IF v_old_status != 'pending' THEN
        RAISE_APPLICATION_ERROR(-20000, '只能审批待审批状态的请购单');
    END IF;

    v_new_status := CASE WHEN p_approved THEN 'approved' ELSE 'rejected' END;

    UPDATE purchase_requisitions
    SET status = CAST(v_new_status AS value_label),
        remark = COALESCE(remark, '') || ' | 审批意见: ' || p_remark
    WHERE id = p_requisition_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, old_value, new_value)
    VALUES ('approve_requisition', 'purchase_requisition', p_requisition_id, p_approver_id,
            JSON_OBJECT('status' VALUE v_old_status),
            JSON_OBJECT('status' VALUE v_new_status, 'remark' VALUE p_remark));

    -- RAISE NOTICE 'success: %', FOUND;
END;
/


-- ============================================================
-- 6. 采购流程
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_purchase_order(
    p_supplier_id NUMBER,
    p_requisition_id NUMBER,
    p_department_id NUMBER,
    p_purchaser_id NUMBER,
    p_expected_delivery_date DATE,
    p_payment_terms VARCHAR2,
    p_items_json CLOB,
    p_remark VARCHAR2
)
AS
    v_order_no VARCHAR2(30);
    v_order_id NUMBER(19);
    v_total NUMBER(18,2) := 0.00;
    v_item_count NUMBER(10);
    v_item CLOB;
    v_product_id NUMBER(19);
    v_qty NUMBER(10);
    v_price NUMBER(12,2);
BEGIN
    v_order_no := 'PO-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || LPAD((FLOOR(DBMS_RANDOM.VALUE * 9999) + 1), 4, '0');
    v_item_count := JSON_ARRAY_T.parse(p_items_json).get_size;

    IF v_item_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20000, '采购明细不能为空');
    END IF;

    INSERT INTO purchase_orders (order_no, supplier_id, requisition_id, department_id,
        purchaser_id, order_date, expected_delivery_date, payment_terms, status, remark)
    VALUES (v_order_no, p_supplier_id, p_requisition_id, p_department_id,
        p_purchaser_id, CURRENT_DATE, p_expected_delivery_date, p_payment_terms, 'ordered', p_remark)
    RETURNING id INTO v_order_id;

    FOR v_item IN (SELECT item_json FROM JSON_TABLE(p_items_json, '$[*]' COLUMNS (item_json CLOB FORMAT JSON PATH '$')))
    LOOP
        v_product_id := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.product_id'));
        v_qty := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.quantity'));
        v_price := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.unit_price'));

        INSERT INTO purchase_order_items (order_id, product_id, quantity, unit_price)
        VALUES (v_order_id, v_product_id, v_qty, v_price);

        v_total := v_total + (v_qty * v_price);
    END LOOP;

    UPDATE purchase_orders SET total_amount = v_total WHERE id = v_order_id;

    -- 更新请购单状态
    IF p_requisition_id IS NOT NULL THEN
        UPDATE purchase_requisitions SET status = 'ordered' WHERE id = p_requisition_id;
    END IF;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_purchase_order', 'purchase_order', v_order_id, p_purchaser_id,
            JSON_OBJECT('order_no' VALUE v_order_no, 'supplier_id' VALUE p_supplier_id, 'total_amount' VALUE v_total));

    -- RAISE NOTICE 'order_id: %, order_no: %, total_amount: %', v_order_id, v_order_no, v_total;
END;
/


-- ============================================================
-- 7. 进货入库流程
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_receive_purchase(
    p_order_id NUMBER,
    p_warehouse_id NUMBER,
    p_receiver_id NUMBER,
    p_items_json CLOB,
    p_inspection_result VARCHAR2,
    p_remark VARCHAR2
)
AS
    v_receipt_no VARCHAR2(30);
    v_receipt_id NUMBER(19);
    v_order_status VARCHAR2(50);
    v_total_qty NUMBER(10) := 0;
    v_total_amount NUMBER(18,2) := 0.00;
    v_item CLOB;
    v_order_item_id NUMBER(19);
    v_product_id NUMBER(19);
    v_received_qty NUMBER(10);
    v_accepted_qty NUMBER(10);
    v_rejected_qty NUMBER(10);
    v_unit_price NUMBER(12,2);
    v_batch_no VARCHAR2(50);
    v_production_date DATE;
    v_expiry_date DATE;
    v_batch_id NUMBER(19);
    v_before_qty NUMBER(10);
    v_receipt_status VARCHAR2(40);
BEGIN
    SELECT status INTO v_order_status FROM purchase_orders WHERE id = p_order_id;
    IF v_order_status NOT IN ('ordered', 'partially_received') THEN
        RAISE_APPLICATION_ERROR(-20000, '采购单状态不允许收货');
    END IF;

    v_receipt_no := 'RC-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || LPAD((FLOOR(DBMS_RANDOM.VALUE * 9999) + 1), 4, '0');

    v_receipt_status := CASE WHEN p_inspection_result IS NULL THEN 'received' ELSE 'inspected' END;

    INSERT INTO purchase_receipts (receipt_no, order_id, warehouse_id, receiver_id,
        receipt_date, status, inspection_result, remark)
    VALUES (v_receipt_no, p_order_id, p_warehouse_id, p_receiver_id, CURRENT_DATE,
        v_receipt_status, p_inspection_result, p_remark)
    RETURNING id INTO v_receipt_id;

    FOR v_item IN (SELECT item_json FROM JSON_TABLE(p_items_json, '$[*]' COLUMNS (item_json CLOB FORMAT JSON PATH '$')))
    LOOP
        v_order_item_id := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.order_item_id'));
        v_product_id := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.product_id'));
        v_received_qty := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.received_qty'));
        v_accepted_qty := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.accepted_qty'));
        v_rejected_qty := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.rejected_qty'));
        v_unit_price := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.unit_price'));
        v_batch_no := JSON_VALUE(v_item.item_json, '$.batch_no');
        v_production_date := (JSON_VALUE(v_item.item_json, '$.production_date'));
        v_expiry_date := (JSON_VALUE(v_item.item_json, '$.expiry_date'));

        -- 创建批号
        INSERT INTO product_batches (product_id, batch_no, production_date, expiry_date,
            purchase_price, initial_qty, current_qty)
        VALUES (v_product_id, v_batch_no, v_production_date, v_expiry_date,
            v_unit_price, v_accepted_qty, v_accepted_qty)
        RETURNING id INTO v_batch_id;

        -- 入库明细
        INSERT INTO purchase_receipt_items (receipt_id, order_item_id, product_id, batch_id,
            received_qty, accepted_qty, rejected_qty, unit_price, production_date, expiry_date)
        VALUES (v_receipt_id, v_order_item_id, v_product_id, v_batch_id,
            v_received_qty, v_accepted_qty, v_rejected_qty, v_unit_price, v_production_date, v_expiry_date);

        -- 更新库存
        SELECT COALESCE(SUM(quantity), 0) INTO v_before_qty
        FROM inventory WHERE product_id = v_product_id AND warehouse_id = p_warehouse_id;

        INSERT INTO inventory (product_id, batch_id, warehouse_id, quantity)
        VALUES (v_product_id, v_batch_id, p_warehouse_id, v_accepted_qty)
        ;

        -- 库存变动日志
        INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
            transaction_type, quantity_change, before_qty, after_qty,
            reference_type, reference_id, operator_id)
        VALUES (v_product_id, v_batch_id, p_warehouse_id, 'purchase_in',
            v_accepted_qty, v_before_qty, v_before_qty + v_accepted_qty,
            'purchase_receipt', v_receipt_id, p_receiver_id);

        -- 更新采购单明细的收货数量
        UPDATE purchase_order_items
        SET received_qty = received_qty + v_accepted_qty
        WHERE id = v_order_item_id;

        v_total_qty := v_total_qty + v_accepted_qty;
        v_total_amount := v_total_amount + (v_accepted_qty * v_unit_price);
    END LOOP;

    UPDATE purchase_receipts
    SET total_qty = v_total_qty, total_amount = v_total_amount
    WHERE id = v_receipt_id;

    -- 更新采购单状态
    UPDATE purchase_orders
    SET status = 'partially_received',
        actual_delivery_date = CURRENT_DATE
    WHERE id = p_order_id;

    -- 检查是否全部收货完成
     (
        SELECT 1 FROM purchase_order_items
        WHERE order_id = p_order_id AND received_qty < quantity
    ) THEN
        UPDATE purchase_orders SET status = 'received' WHERE id = p_order_id;
    END IF;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('receive_purchase', 'purchase_receipt', v_receipt_id, p_receiver_id,
            JSON_OBJECT('receipt_no' VALUE v_receipt_no, 'order_id' VALUE p_order_id, 'total_qty' VALUE v_total_qty, 'total_amount' VALUE v_total_amount));

    -- RAISE NOTICE 'receipt_id: %, receipt_no: %, total_qty: %, total_amount: %',
        v_receipt_id, v_receipt_no, v_total_qty, v_total_amount;
END;
/


-- ============================================================
-- 8. 销售流程
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_sales_order(
    p_customer_id NUMBER,
    p_salesperson_id NUMBER,
    p_warehouse_id NUMBER,
    p_discount_amount NUMBER,
    p_payment_method VARCHAR2,
    p_items_json CLOB,
    p_remark VARCHAR2
)
AS
    v_order_no VARCHAR2(30);
    v_order_id NUMBER(19);
    v_total_amount NUMBER(18,2) := 0.00;
    v_item CLOB;
    v_product_id NUMBER(19);
    v_batch_id NUMBER(19);
    v_qty NUMBER(10);
    v_price NUMBER(12,2);
    v_discount NUMBER(12,2);
    v_line_amount NUMBER(18,2);
    v_available NUMBER(10);
    v_before_qty NUMBER(10);
    v_credit_limit NUMBER(18,2);
    v_balance NUMBER(18,2);
BEGIN
    v_order_no := 'SO-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || LPAD((FLOOR(DBMS_RANDOM.VALUE * 9999) + 1), 4, '0');

    -- 检查信用额度
    SELECT credit_limit, balance INTO v_credit_limit, v_balance FROM customers WHERE id = p_customer_id;

    INSERT INTO sales_orders (order_no, customer_id, salesperson_id, warehouse_id,
        order_date, discount_amount, payment_method, status, remark)
    VALUES (v_order_no, p_customer_id, p_salesperson_id, p_warehouse_id,
        CURRENT_DATE, p_discount_amount, p_payment_method, 'confirmed', p_remark)
    RETURNING id INTO v_order_id;

    FOR v_item IN (SELECT item_json FROM JSON_TABLE(p_items_json, '$[*]' COLUMNS (item_json CLOB FORMAT JSON PATH '$')))
    LOOP
        v_product_id := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.product_id'));
        v_batch_id := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.batch_id'));
        v_qty := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.quantity'));
        v_price := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.unit_price'));
        v_discount := COALESCE(TO_NUMBER(JSON_VALUE(v_item.item_json, '$.discount')), 0);

        -- 检查库存
        SELECT COALESCE(available_quantity, 0) INTO v_available
        FROM inventory
        WHERE product_id = v_product_id
          AND (batch_id = v_batch_id OR (v_batch_id IS NULL AND batch_id IS NULL))
          AND warehouse_id = p_warehouse_id;

        IF v_available < v_qty THEN
            RAISE_APPLICATION_ERROR(-20000, '库存不足: 产品ID=%, 可用=%, 需求=%');
        END IF;

        v_line_amount := (v_qty * v_price) - v_discount;

        INSERT INTO sales_order_items (order_id, product_id, batch_id, quantity, unit_price, discount, amount)
        VALUES (v_order_id, v_product_id, v_batch_id, v_qty, v_price, v_discount, v_line_amount);

        -- 扣减库存
        SELECT quantity INTO v_before_qty
        FROM inventory
        WHERE product_id = v_product_id
          AND (batch_id = v_batch_id OR (v_batch_id IS NULL AND batch_id IS NULL))
          AND warehouse_id = p_warehouse_id;

        UPDATE inventory
        SET quantity = quantity - v_qty
        WHERE product_id = v_product_id
          AND (batch_id = v_batch_id OR (v_batch_id IS NULL AND batch_id IS NULL))
          AND warehouse_id = p_warehouse_id;

        -- 更新批号库存
        IF v_batch_id IS NOT NULL THEN
            UPDATE product_batches SET current_qty = current_qty - v_qty WHERE id = v_batch_id;
        END IF;

        -- 库存变动日志
        INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
            transaction_type, quantity_change, before_qty, after_qty,
            reference_type, reference_id, operator_id)
        VALUES (v_product_id, v_batch_id, p_warehouse_id, 'sales_out',
            -v_qty, v_before_qty, v_before_qty - v_qty,
            'sales_order', v_order_id, p_salesperson_id);

        v_total_amount := v_total_amount + v_line_amount;
    END LOOP;

    UPDATE sales_orders
    SET total_amount = v_total_amount - p_discount_amount
    WHERE id = v_order_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_sales_order', 'sales_order', v_order_id, p_salesperson_id,
            JSON_OBJECT('order_no' VALUE v_order_no, 'customer_id' VALUE p_customer_id, 'total_amount' VALUE v_total_amount - p_discount_amount));

    -- RAISE NOTICE 'order_id: %, order_no: %, total_amount: %',
        v_order_id, v_order_no, v_total_amount - p_discount_amount;
END;
/


-- ============================================================
-- 9. 退库流程
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_sales_return(
    p_order_id NUMBER,
    p_warehouse_id NUMBER,
    p_handler_id NUMBER,
    p_return_reason VARCHAR2,
    p_return_type VARCHAR2(40),
    p_restock_fee NUMBER,
    p_items_json CLOB
)
AS
    v_return_no VARCHAR2(30);
    v_return_id NUMBER(19);
    v_customer_id NUMBER(19);
    v_total_amount NUMBER(18,2) := 0.00;
    v_item CLOB;
    v_order_item_id NUMBER(19);
    v_product_id NUMBER(19);
    v_batch_id NUMBER(19);
    v_return_qty NUMBER(10);
    v_unit_price NUMBER(12,2);
    v_before_qty NUMBER(10);
BEGIN
    SELECT customer_id INTO v_customer_id FROM sales_orders WHERE id = p_order_id;

    v_return_no := 'SR-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || LPAD((FLOOR(DBMS_RANDOM.VALUE * 9999) + 1), 4, '0');

    INSERT INTO sales_returns (return_no, order_id, customer_id, warehouse_id, handler_id,
        return_date, return_reason, return_type, restock_fee, status)
    VALUES (v_return_no, p_order_id, v_customer_id, p_warehouse_id, p_handler_id,
        CURRENT_DATE, p_return_reason, p_return_type, p_restock_fee, 'approved')
    RETURNING id INTO v_return_id;

    FOR v_item IN (SELECT item_json FROM JSON_TABLE(p_items_json, '$[*]' COLUMNS (item_json CLOB FORMAT JSON PATH '$')))
    LOOP
        v_order_item_id := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.order_item_id'));
        v_product_id := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.product_id'));
        v_batch_id := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.batch_id'));
        v_return_qty := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.return_qty'));
        v_unit_price := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.unit_price'));

        INSERT INTO sales_return_items (return_id, order_item_id, product_id, batch_id,
            return_qty, unit_price, amount, status)
        VALUES (v_return_id, v_order_item_id, v_product_id, v_batch_id,
            v_return_qty, v_unit_price, v_return_qty * v_unit_price, 'received');

        -- 更新销售单明细退货数量
        UPDATE sales_order_items
        SET returned_qty = returned_qty + v_return_qty
        WHERE id = v_order_item_id;

        -- 恢复库存
        SELECT COALESCE(quantity, 0) INTO v_before_qty
        FROM inventory
        WHERE product_id = v_product_id
          AND (batch_id = v_batch_id OR (v_batch_id IS NULL AND batch_id IS NULL))
          AND warehouse_id = p_warehouse_id;

        INSERT INTO inventory (product_id, batch_id, warehouse_id, quantity)
        VALUES (v_product_id, v_batch_id, p_warehouse_id, v_return_qty)
        ;

        IF v_batch_id IS NOT NULL THEN
            UPDATE product_batches SET current_qty = current_qty + v_return_qty WHERE id = v_batch_id;
        END IF;

        INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
            transaction_type, quantity_change, before_qty, after_qty,
            reference_type, reference_id, operator_id)
        VALUES (v_product_id, v_batch_id, p_warehouse_id, 'return_in',
            v_return_qty, v_before_qty, v_before_qty + v_return_qty,
            'sales_return', v_return_id, p_handler_id);

        v_total_amount := v_total_amount + (v_return_qty * v_unit_price);
    END LOOP;

    UPDATE sales_returns
    SET total_amount = v_total_amount, refund_amount = v_total_amount - p_restock_fee
    WHERE id = v_return_id;

    -- 更新销售单状态
    UPDATE sales_orders SET status = 'returned' WHERE id = p_order_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_sales_return', 'sales_return', v_return_id, p_handler_id,
            JSON_OBJECT('return_no' VALUE v_return_no, 'order_id' VALUE p_order_id, 'total_amount' VALUE v_total_amount, 'reason' VALUE p_return_reason));

    -- RAISE NOTICE 'return_id: %, return_no: %, total_amount: %, refund_amount: %',
        v_return_id, v_return_no, v_total_amount, v_total_amount - p_restock_fee;
END;
/


-- ============================================================
-- 10. 工资发放
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_process_salary(
    p_salary_month VARCHAR2,
    p_processed_by NUMBER
)
AS
    v_overtime_pay NUMBER(12,2);
    v_bonus NUMBER(12,2);
    v_deduction NUMBER(12,2);
    v_ss_personal NUMBER(12,2);
    v_hf_personal NUMBER(12,2);
    v_income_tax NUMBER(12,2);
    v_net_pay NUMBER(12,2);
    v_ss_company NUMBER(12,2);
    v_hf_company NUMBER(12,2);
    v_payment_no VARCHAR2(30);
    v_taxable NUMBER(12,2);
    v_overtime_hours NUMBER(4,1);
    v_late_count NUMBER(10);
    v_payment_date DATE;
BEGIN
    -- 检查是否已发放
    IF EXISTS (SELECT 1 FROM salary_payments WHERE salary_month = p_salary_month FETCH FIRST 1 ROWS ONLY) THEN
        RAISE_APPLICATION_ERROR(-20000, '% 工资已发放，不能重复处理');
    END IF;

    v_payment_date := CURRENT_DATE;

    FOR v_emp_record IN
        SELECT id, salary FROM employees WHERE status IN ('active', 'probation')
    LOOP
        -- 计算加班费
        SELECT COALESCE(SUM(overtime_hours), 0) INTO v_overtime_hours
        FROM attendance
        WHERE employee_id = v_emp_record.id
          AND TO_CHAR(attendance_date, 'YYYY-MM') = p_salary_month;

        v_overtime_pay := ROUND((v_emp_record.salary / 21.75 / 8 * v_overtime_hours * 1.5), 2);

        -- 迟到扣款
        SELECT COUNT(*) INTO v_late_count
        FROM attendance
        WHERE employee_id = v_emp_record.id
          AND TO_CHAR(attendance_date, 'YYYY-MM') = p_salary_month
          AND status = 'late';

        v_deduction := v_late_count * 50;

        -- 社保和公积金（个人部分）
        v_ss_personal := ROUND((v_emp_record.salary * 0.08), 2);
        v_hf_personal := ROUND((v_emp_record.salary * 0.12), 2);

        -- 个税计算
        v_taxable := v_emp_record.salary + v_overtime_pay - v_ss_personal - v_hf_personal - 5000;
        IF v_taxable <= 0 THEN
            v_income_tax := 0;
        ELSIF v_taxable <= 3000 THEN
            v_income_tax := ROUND((v_taxable * 0.03), 2);
        ELSIF v_taxable <= 12000 THEN
            v_income_tax := ROUND((v_taxable * 0.10 - 210), 2);
        ELSIF v_taxable <= 25000 THEN
            v_income_tax := ROUND((v_taxable * 0.20 - 1410), 2);
        ELSE
            v_income_tax := ROUND((v_taxable * 0.25 - 2660), 2);
        END IF;

        v_bonus := 0;
        v_net_pay := v_emp_record.salary + v_overtime_pay + v_bonus - v_deduction
                    - v_ss_personal - v_hf_personal - v_income_tax;

        -- 公司部分
        v_ss_company := ROUND((v_emp_record.salary * 0.16), 2);
        v_hf_company := ROUND((v_emp_record.salary * 0.12), 2);

        v_payment_no := 'SAL-' || REPLACE(p_salary_month, '-', '') || '-' || LPAD(v_emp_record.id, 5, '0');

        INSERT INTO salary_payments (payment_no, employee_id, payment_date, salary_month,
            base_salary, overtime_pay, bonus, deduction,
            social_security_personal, housing_fund_personal, income_tax,
            net_pay, social_security_company, housing_fund_company,
            status, paid_at)
        VALUES (v_payment_no, v_emp_record.id, v_payment_date, p_salary_month,
            v_emp_record.salary, v_overtime_pay, v_bonus, v_deduction,
            v_ss_personal, v_hf_personal, v_income_tax,
            v_net_pay, v_ss_company, v_hf_company,
            'paid', CURRENT_TIMESTAMP);

    END LOOP;

    INSERT INTO audit_log (action, target_type, employee_id, new_value)
    VALUES ('process_salary', 'salary', p_processed_by,
            JSON_OBJECT('salary_month' VALUE p_salary_month, 'payment_date' VALUE v_payment_date));

    -- RAISE NOTICE '工资发放完成: %', p_salary_month;
END;
/


-- ============================================================
-- 11. 出纳日记账
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_cashier_journal(
    p_account_id NUMBER,
    p_cashier_id NUMBER,
    p_journal_type VARCHAR2(40),
    p_amount NUMBER,
    p_counterparty VARCHAR2,
    p_reference_type VARCHAR2,
    p_reference_id NUMBER,
    p_bank_account VARCHAR2,
    p_check_no VARCHAR2,
    p_remark VARCHAR2
)
AS
    v_journal_no VARCHAR2(30);
    v_journal_id NUMBER(19);
    v_old_balance NUMBER(18,2);
BEGIN
    v_journal_no := 'CJ-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || LPAD((FLOOR(DBMS_RANDOM.VALUE * 9999) + 1), 4, '0');

    INSERT INTO cashier_journals (journal_no, journal_date, account_id, cashier_id,
        journal_type, amount, counterparty, reference_type, reference_id, bank_account, check_no, remark)
    VALUES (v_journal_no, CURRENT_DATE, p_account_id, p_cashier_id,
        p_journal_type, p_amount, p_counterparty, p_reference_type, p_reference_id, p_bank_account, p_check_no, p_remark)
    RETURNING id INTO v_journal_id;

    -- 更新账户余额
    SELECT current_balance INTO v_old_balance FROM accounts WHERE id = p_account_id;

    IF p_journal_type IN ('cash_in', 'bank_in') THEN
        UPDATE accounts SET current_balance = current_balance + p_amount WHERE id = p_account_id;
    ELSIF p_journal_type IN ('cash_out', 'bank_out') THEN
        UPDATE accounts SET current_balance = current_balance - p_amount WHERE id = p_account_id;
    END IF;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_journal', 'cashier_journal', v_journal_id, p_cashier_id,
            JSON_OBJECT('journal_no' VALUE v_journal_no, 'type' VALUE p_journal_type, 'amount' VALUE p_amount));

    -- RAISE NOTICE 'journal_id: %, journal_no: %', v_journal_id, v_journal_no;
END;
/


-- ============================================================
-- 12. 对账
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_reconciliation(
    p_account_id NUMBER,
    p_period_start DATE,
    p_period_end DATE,
    p_bank_balance NUMBER,
    p_prepared_by NUMBER
)
AS
    v_recon_no VARCHAR2(30);
    v_recon_id NUMBER(19);
    v_book_balance NUMBER(18,2);
    v_unrec_income NUMBER(18,2) := 0.00;
    v_unrec_expense NUMBER(18,2) := 0.00;
    v_difference NUMBER(18,2);
    v_adjusted NUMBER(18,2);
BEGIN
    v_recon_no := 'REC-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || LPAD((FLOOR(DBMS_RANDOM.VALUE * 9999) + 1), 4, '0');

    -- 获取账面余额
    SELECT current_balance INTO v_book_balance FROM accounts WHERE id = p_account_id;

    -- 未达收入
    SELECT COALESCE(SUM(amount), 0) INTO v_unrec_income
    FROM cashier_journals
    WHERE account_id = p_account_id
      AND journal_date BETWEEN p_period_start AND p_period_end
      AND VARCHAR2(40) IN ('cash_in', 'bank_in')
      AND status = 'pending';

    -- 未达支出
    SELECT COALESCE(SUM(amount), 0) INTO v_unrec_expense
    FROM cashier_journals
    WHERE account_id = p_account_id
      AND journal_date BETWEEN p_period_start AND p_period_end
      AND VARCHAR2(40) IN ('cash_out', 'bank_out')
      AND status = 'pending';

    v_difference := p_bank_balance - v_book_balance;
    v_adjusted := v_book_balance + v_unrec_income - v_unrec_expense;

    INSERT INTO reconciliations (recon_no, account_id, recon_date, period_start, period_end,
        book_balance, bank_balance, difference, unreconciled_income, unreconciled_expense,
        adjusted_balance, prepared_by, status)
    VALUES (v_recon_no, p_account_id, CURRENT_DATE, p_period_start, p_period_end,
        v_book_balance, p_bank_balance, v_difference, v_unrec_income, v_unrec_expense,
        v_adjusted, p_prepared_by, 'prepared')
    RETURNING id INTO v_recon_id;

    -- 插入对账明细
    -- relation-detector-fixture-source: ROUTINE:oracle.sp_create_reconciliation
INSERT INTO reconciliation_items (reconciliation_id, journal_id, transaction_date,
        description, debit_amount, credit_amount, is_matched)
    SELECT
        v_recon_id, cj.id, cj.journal_date,
        cj.journal_type || ' - ' || COALESCE(cj.counterparty, '') || ' ' || COALESCE(cj.remark, ''),
        CASE WHEN cj.journal_type IN ('bank_in', 'cash_in') THEN cj.amount ELSE 0 END,
        CASE WHEN cj.journal_type IN ('bank_out', 'cash_out') THEN cj.amount ELSE 0 END,
        0
    FROM cashier_journals cj
    WHERE cj.account_id = p_account_id
      AND cj.journal_date BETWEEN p_period_start AND p_period_end;
-- relation-detector-fixture-end

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_reconciliation', 'reconciliation', v_recon_id, p_prepared_by,
            JSON_OBJECT('recon_no' VALUE v_recon_no, 'book_balance' VALUE v_book_balance, 'bank_balance' VALUE p_bank_balance, 'difference' VALUE v_difference));

    -- RAISE NOTICE 'reconciliation_id: %, recon_no: %, book_balance: %, bank_balance: %, difference: %, adjusted_balance: %',
        v_recon_id, v_recon_no, v_book_balance, p_bank_balance, v_difference, v_adjusted;
END;
/


-- ============================================================
-- 13. 结算
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_settlement(
    p_settlement_type VARCHAR2(40),
    p_party_id NUMBER,
    p_period_start DATE,
    p_period_end DATE,
    p_payment_due_date DATE,
    p_payment_method VARCHAR2,
    p_items_json CLOB,
    p_prepared_by NUMBER,
    p_remark VARCHAR2
)
AS
    v_settle_no VARCHAR2(30);
    v_settle_id NUMBER(19);
    v_total_amount NUMBER(18,2) := 0.00;
    v_item CLOB;
    v_ref_type VARCHAR2(50);
    v_ref_id NUMBER(19);
    v_amount NUMBER(18,2);
BEGIN
    v_settle_no := 'SET-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || LPAD((FLOOR(DBMS_RANDOM.VALUE * 9999) + 1), 4, '0');

    INSERT INTO settlements (settlement_no, settlement_type, party_id, settlement_date,
        period_start, period_end, total_amount, payment_due_date, payment_method,
        prepared_by, status, remark)
    VALUES (v_settle_no, p_settlement_type, p_party_id, CURRENT_DATE,
        p_period_start, p_period_end, 0, p_payment_due_date, p_payment_method,
        p_prepared_by, 'pending', p_remark)
    RETURNING id INTO v_settle_id;

    FOR v_item IN (SELECT item_json FROM JSON_TABLE(p_items_json, '$[*]' COLUMNS (item_json CLOB FORMAT JSON PATH '$')))
    LOOP
        v_ref_type := JSON_VALUE(v_item.item_json, '$.reference_type');
        v_ref_id := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.reference_id'));
        v_amount := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.amount'));

        INSERT INTO settlement_items (settlement_id, reference_type, reference_id, amount)
        VALUES (v_settle_id, v_ref_type, v_ref_id, v_amount);

        v_total_amount := v_total_amount + v_amount;
    END LOOP;

    UPDATE settlements SET total_amount = v_total_amount WHERE id = v_settle_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_settlement', 'settlement', v_settle_id, p_prepared_by,
            JSON_OBJECT('settlement_no' VALUE v_settle_no, 'type' VALUE p_settlement_type, 'total_amount' VALUE v_total_amount));

    -- RAISE NOTICE 'settlement_id: %, settlement_no: %, total_amount: %', v_settle_id, v_settle_no, v_total_amount;
END;
/


-- ============================================================
-- 14. 财务凭证
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_voucher(
    p_voucher_date DATE,
    p_voucher_type VARCHAR2(40),
    p_reference_type VARCHAR2,
    p_reference_id NUMBER,
    p_prepared_by NUMBER,
    p_summary VARCHAR2,
    p_items_json CLOB
)
AS
    v_voucher_no VARCHAR2(30);
    v_voucher_id NUMBER(19);
    v_total_debit NUMBER(18,2) := 0.00;
    v_total_credit NUMBER(18,2) := 0.00;
    v_item CLOB;
    v_account_id NUMBER(19);
    v_direction VARCHAR2(10);
    v_amount NUMBER(18,2);
    v_line_summary VARCHAR2(500);
    v_line_no NUMBER(10) := 0;
BEGIN
    v_voucher_no := 'V-' || TO_CHAR(p_voucher_date, 'YYYYMMDD') || '-' || LPAD((FLOOR(DBMS_RANDOM.VALUE * 9999) + 1), 4, '0');

    INSERT INTO vouchers (voucher_no, voucher_date, voucher_type,
        reference_type, reference_id, prepared_by, summary)
    VALUES (v_voucher_no, p_voucher_date, p_voucher_type,
        p_reference_type, p_reference_id, p_prepared_by, p_summary)
    RETURNING id INTO v_voucher_id;

    FOR v_item IN (SELECT item_json FROM JSON_TABLE(p_items_json, '$[*]' COLUMNS (item_json CLOB FORMAT JSON PATH '$')))
    LOOP
        v_line_no := v_line_no + 1;
        v_account_id := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.account_id'));
        v_direction := JSON_VALUE(v_item, '$.direction');
        v_amount := TO_NUMBER(JSON_VALUE(v_item.item_json, '$.amount'));
        v_line_summary := JSON_VALUE(v_item.item_json, '$.summary');

        INSERT INTO voucher_items (voucher_id, account_id, line_no, direction, amount, summary)
        VALUES (v_voucher_id, v_account_id, v_line_no, v_direction, v_amount, v_line_summary);

        IF v_direction = 'debit' THEN
            v_total_debit := v_total_debit + v_amount;
        ELSE
            v_total_credit := v_total_credit + v_amount;
        END IF;
    END LOOP;

    IF ABS(v_total_debit - v_total_credit) > 0.01 THEN
        RAISE_APPLICATION_ERROR(-20000, '借贷不平衡: 借方=%, 贷方=%');
    END IF;

    UPDATE vouchers SET total_debit = v_total_debit, total_credit = v_total_credit WHERE id = v_voucher_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_voucher', 'voucher', v_voucher_id, p_prepared_by,
            JSON_OBJECT('voucher_no' VALUE v_voucher_no, 'total_debit' VALUE v_total_debit, 'total_credit' VALUE v_total_credit));

    -- RAISE NOTICE 'voucher_id: %, voucher_no: %, total_debit: %, total_credit: %',
        v_voucher_id, v_voucher_no, v_total_debit, v_total_credit;
END;
/


-- 过账凭证
CREATE OR REPLACE PROCEDURE sp_post_voucher(
    p_voucher_id NUMBER,
    p_posted_by NUMBER
)
AS
    v_status VARCHAR2(20);
BEGIN
    SELECT status INTO v_status FROM vouchers WHERE id = p_voucher_id;

    IF v_status NOT IN ('reviewed', 'draft') THEN
        RAISE_APPLICATION_ERROR(-20000, '只能过账已审核或草稿状态的凭证');
    END IF;

    FOR v_rec IN
        SELECT account_id, direction, amount FROM voucher_items WHERE voucher_id = p_voucher_id
    LOOP
        IF v_rec.direction = 'debit' THEN
            UPDATE accounts SET current_balance = current_balance + v_rec.amount WHERE id = v_rec.account_id;
        ELSE
            UPDATE accounts SET current_balance = current_balance - v_rec.amount WHERE id = v_rec.account_id;
        END IF;
    END LOOP;

    UPDATE vouchers SET status = 'posted', posted_by = p_posted_by WHERE id = p_voucher_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('post_voucher', 'voucher', p_voucher_id, p_posted_by,
            JSON_OBJECT('status' VALUE 'posted'));

    -- RAISE NOTICE '凭证过账成功';
END;
/


-- ============================================================
-- 15. 批量生成考勤数据
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_generate_attendance(
    p_year_month VARCHAR2
)
AS
    v_day NUMBER(10);
    v_days_in_month NUMBER(10);
    v_att_date DATE;
    v_day_of_week NUMBER(10);
    v_clock_in TIMESTAMP;
    v_clock_out TIMESTAMP;
    v_status VARCHAR2(40);
    v_late_min NUMBER(10);
    v_rand_val BINARY_DOUBLE;
    v_month_start DATE;
BEGIN
    v_month_start := TO_DATE(p_year_month || '-01', 'YYYY-MM-DD');
    v_days_in_month := TO_NUMBER(TO_CHAR(LAST_DAY(v_month_start), 'DD'));

    FOR v_emp_record IN
        SELECT id FROM employees WHERE status IN ('active', 'probation')
    LOOP
        v_day := 1;
        WHILE v_day <= v_days_in_month LOOP
            v_att_date := v_month_start + (v_day - 1);
            v_day_of_week := (TO_NUMBER(TO_CHAR(v_att_date, 'D')) - 1);

            -- 周末不上班 (PG: 0=Sunday, 6=Saturday)
            IF v_day_of_week IN (0, 6) THEN
                v_day := v_day + 1;
                CONTINUE;
            END IF;

            v_rand_val := DBMS_RANDOM.VALUE;

            IF v_rand_val < 0.05 THEN
                v_status := 'absent';
                v_clock_in := NULL;
                v_clock_out := NULL;
                v_late_min := 0;
            ELSIF v_rand_val < 0.15 THEN
                v_status := 'late';
                v_late_min := FLOOR(DBMS_RANDOM.VALUE * 60) + 1;
                v_clock_in := (INTERVAL '8' HOUR + NUMTODSINTERVAL(v_late_min, 'MINUTE'));
                v_clock_out := (INTERVAL '17' HOUR + NUMTODSINTERVAL(FLOOR(DBMS_RANDOM.VALUE * 60), 'MINUTE'));
            ELSIF v_rand_val < 0.20 THEN
                v_status := 'early';
                v_late_min := 0;
                v_clock_in := INTERVAL '8' HOUR;
                v_clock_out := (INTERVAL '17' HOUR - NUMTODSINTERVAL(FLOOR(DBMS_RANDOM.VALUE * 60) + 1, 'MINUTE'));
            ELSE
                v_status := 'normal';
                v_late_min := 0;
                v_clock_in := (INTERVAL '8' HOUR + NUMTODSINTERVAL(FLOOR(DBMS_RANDOM.VALUE * 10), 'MINUTE'));
                v_clock_out := (INTERVAL '17' HOUR + NUMTODSINTERVAL(FLOOR(DBMS_RANDOM.VALUE * 60), 'MINUTE'));
            END IF;

            INSERT INTO attendance (employee_id, attendance_date, clock_in, clock_out, status, late_minutes)
            VALUES (v_emp_record.id, v_att_date,
                CASE WHEN v_clock_in IS NULL THEN NULL ELSE CAST((v_att_date + v_clock_in) AS TIMESTAMP) END,
                CASE WHEN v_clock_out IS NULL THEN NULL ELSE CAST((v_att_date + v_clock_out) AS TIMESTAMP) END,
                v_status, v_late_min)
            ;

            v_day := v_day + 1;
        END LOOP;
    END LOOP;

    -- RAISE NOTICE '考勤数据生成完成: %', p_year_month;
END;
/