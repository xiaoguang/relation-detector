-- ============================================================
-- ERP系统存储过程 - PostgreSQL 18
-- 包含: 部门管理, 货品管理, 员工管理, 请购, 采购, 进货入库,
--       销售, 退库, 工资发放, 审计, 对账, 出纳, 结算
-- ============================================================

-- ============================================================
-- 辅助函数: 生成随机工号/单号
-- ============================================================

CREATE OR REPLACE FUNCTION generate_employee_no()
RETURNS VARCHAR(20) AS $$
BEGIN
    RETURN TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || LPAD((FLOOR(RANDOM() * 9999) + 1)::TEXT, 4, '0');
END;
$$ LANGUAGE plpgsql;


-- ============================================================
-- 1. 部门管理
-- ============================================================

-- 插入部门
CREATE OR REPLACE PROCEDURE sp_create_department(
    p_parent_id BIGINT,
    p_name VARCHAR(100),
    p_code VARCHAR(20),
    p_budget DECIMAL(18,2),
    p_headcount_plan INTEGER
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_new_id BIGINT;
BEGIN
    INSERT INTO departments (parent_id, name, code, budget, headcount_plan)
    VALUES (p_parent_id, p_name, p_code, p_budget, p_headcount_plan)
    RETURNING id INTO v_new_id;

    INSERT INTO audit_log (action, target_type, target_id, new_value)
    VALUES ('create_department', 'department', v_new_id,
            jsonb_build_object('name', p_name, 'code', p_code, 'budget', p_budget));

    -- 返回新ID
    RAISE NOTICE 'new_department_id: %', v_new_id;
END;
$$;


-- 更新部门经理
CREATE OR REPLACE PROCEDURE sp_assign_department_manager(
    p_department_id BIGINT,
    p_manager_id BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_old_manager_id BIGINT;
BEGIN
    SELECT manager_id INTO v_old_manager_id FROM departments WHERE id = p_department_id;

    UPDATE departments SET manager_id = p_manager_id WHERE id = p_department_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, old_value, new_value)
    VALUES ('assign_manager', 'department', p_department_id, p_manager_id,
            jsonb_build_object('manager_id', v_old_manager_id),
            jsonb_build_object('manager_id', p_manager_id));

    RAISE NOTICE 'success: %', FOUND;
END;
$$;


-- ============================================================
-- 2. 门店/仓库管理
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_warehouse(
    p_name VARCHAR(100),
    p_code VARCHAR(20),
    p_address VARCHAR(200),
    p_manager_id BIGINT,
    p_type warehouse_type,
    p_capacity_m3 DECIMAL(10,3)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_new_id BIGINT;
BEGIN
    INSERT INTO warehouses (name, code, address, manager_id, type, capacity_m3)
    VALUES (p_name, p_code, p_address, p_manager_id, p_type, p_capacity_m3)
    RETURNING id INTO v_new_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_warehouse', 'warehouse', v_new_id, p_manager_id,
            jsonb_build_object('name', p_name, 'code', p_code, 'type', p_type));

    RAISE NOTICE 'new_warehouse_id: %', v_new_id;
END;
$$;


-- ============================================================
-- 3. 货品管理
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_product(
    p_sku VARCHAR(50),
    p_name VARCHAR(200),
    p_category_id BIGINT,
    p_unit VARCHAR(20),
    p_spec VARCHAR(100),
    p_brand VARCHAR(100),
    p_barcode VARCHAR(50),
    p_purchase_price DECIMAL(12,2),
    p_wholesale_price DECIMAL(12,2),
    p_retail_price DECIMAL(12,2),
    p_min_stock INTEGER,
    p_max_stock INTEGER,
    p_batch_managed BOOLEAN,
    p_shelf_life_days INTEGER
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_new_id BIGINT;
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
            jsonb_build_object('sku', p_sku, 'name', p_name, 'retail_price', p_retail_price));

    RAISE NOTICE 'new_product_id: %', v_new_id;
END;
$$;


-- 批量创建批号
CREATE OR REPLACE PROCEDURE sp_create_batch(
    p_product_id BIGINT,
    p_batch_no VARCHAR(50),
    p_production_date DATE,
    p_expiry_date DATE,
    p_supplier_id BIGINT,
    p_purchase_price DECIMAL(12,2),
    p_initial_qty INTEGER
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_new_id BIGINT;
BEGIN
    INSERT INTO product_batches (product_id, batch_no, production_date, expiry_date,
        supplier_id, purchase_price, initial_qty, current_qty)
    VALUES (p_product_id, p_batch_no, p_production_date, p_expiry_date,
        p_supplier_id, p_purchase_price, p_initial_qty, p_initial_qty)
    RETURNING id INTO v_new_id;

    RAISE NOTICE 'new_batch_id: %', v_new_id;
END;
$$;


-- ============================================================
-- 4. 员工管理
-- ============================================================

-- 入职员工
CREATE OR REPLACE PROCEDURE sp_hire_employee(
    p_name VARCHAR(50),
    p_gender gender_type,
    p_id_card VARCHAR(18),
    p_phone VARCHAR(20),
    p_email VARCHAR(100),
    p_birth_date DATE,
    p_hire_date DATE,
    p_department_id BIGINT,
    p_position_id BIGINT,
    p_manager_id BIGINT,
    p_salary DECIMAL(12,2),
    p_bank_name VARCHAR(100),
    p_bank_account VARCHAR(50),
    p_address VARCHAR(200)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_employee_no VARCHAR(20);
    v_new_id BIGINT;
    v_pos_headcount INTEGER;
    v_current_count INTEGER;
BEGIN
    -- 检查编制
    SELECT headcount INTO v_pos_headcount FROM positions WHERE id = p_position_id;
    SELECT COUNT(*) INTO v_current_count FROM employees
    WHERE position_id = p_position_id AND status IN ('active','probation','leave');

    IF v_current_count >= v_pos_headcount THEN
        RAISE EXCEPTION '该岗位编制已满，无法入职';
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
            jsonb_build_object('name', p_name, 'employee_no', v_employee_no,
                               'department_id', p_department_id, 'salary', p_salary));

    RAISE NOTICE 'new_employee_id: %, employee_no: %', v_new_id, v_employee_no;
END;
$$;


-- 晋升为经理
CREATE OR REPLACE PROCEDURE sp_promote_to_manager(
    p_employee_id BIGINT,
    p_new_position_id BIGINT,
    p_new_salary DECIMAL(12,2),
    p_effective_date DATE
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_old_salary DECIMAL(12,2);
    v_old_position_id BIGINT;
    v_department_id BIGINT;
    v_pos_name VARCHAR(100);
BEGIN
    SELECT salary, position_id, department_id
    INTO v_old_salary, v_old_position_id, v_department_id
    FROM employees WHERE id = p_employee_id;

    IF v_old_salary IS NULL THEN
        RAISE EXCEPTION '员工不存在';
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
            jsonb_build_object('position_id', v_old_position_id, 'salary', v_old_salary),
            jsonb_build_object('position_id', p_new_position_id, 'salary', p_new_salary));

    RAISE NOTICE 'success: %', FOUND;
END;
$$;


-- 员工离职
CREATE OR REPLACE PROCEDURE sp_resign_employee(
    p_employee_id BIGINT,
    p_resignation_date DATE,
    p_reason VARCHAR(500)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_old_status emp_status;
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
            jsonb_build_object('status', v_old_status),
            jsonb_build_object('status', 'resigned', 'resignation_date', p_resignation_date, 'reason', p_reason));

    RAISE NOTICE 'success: %', FOUND;
END;
$$;


-- ============================================================
-- 5. 请购流程
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_purchase_requisition(
    p_department_id BIGINT,
    p_requester_id BIGINT,
    p_required_date DATE,
    p_urgency urgency_type,
    p_items_json JSONB,
    p_remark VARCHAR(500)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_req_no VARCHAR(30);
    v_req_id BIGINT;
    v_total DECIMAL(18,2) := 0.00;
    v_idx INTEGER := 0;
    v_item_count INTEGER;
    v_item JSONB;
    v_product_id BIGINT;
    v_qty INTEGER;
    v_price DECIMAL(12,2);
BEGIN
    v_req_no := 'PR-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || LPAD((FLOOR(RANDOM() * 9999) + 1)::TEXT, 4, '0');
    v_item_count := jsonb_array_length(p_items_json);

    IF v_item_count = 0 THEN
        RAISE EXCEPTION '请购明细不能为空';
    END IF;

    INSERT INTO purchase_requisitions (requisition_no, department_id, requester_id,
        requisition_date, required_date, urgency, status, remark)
    VALUES (v_req_no, p_department_id, p_requester_id, CURRENT_DATE, p_required_date,
        p_urgency, 'pending', p_remark)
    RETURNING id INTO v_req_id;

    -- 遍历JSON数组
    FOR v_item IN SELECT * FROM jsonb_array_elements(p_items_json)
    LOOP
        v_product_id := (v_item->>'product_id')::BIGINT;
        v_qty := (v_item->>'quantity')::INTEGER;
        v_price := (v_item->>'estimated_price')::DECIMAL(12,2);

        INSERT INTO purchase_requisition_items (requisition_id, product_id, quantity, estimated_price)
        VALUES (v_req_id, v_product_id, v_qty, v_price);

        v_total := v_total + (v_qty * v_price);
    END LOOP;

    UPDATE purchase_requisitions SET total_amount = v_total WHERE id = v_req_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_requisition', 'purchase_requisition', v_req_id, p_requester_id,
            jsonb_build_object('requisition_no', v_req_no, 'total_amount', v_total, 'urgency', p_urgency));

    RAISE NOTICE 'requisition_id: %, requisition_no: %, total_amount: %', v_req_id, v_req_no, v_total;
END;
$$;


-- 审批请购单
CREATE OR REPLACE PROCEDURE sp_approve_requisition(
    p_requisition_id BIGINT,
    p_approver_id BIGINT,
    p_approved BOOLEAN,
    p_remark VARCHAR(500)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_old_status VARCHAR(20);
    v_new_status VARCHAR(20);
BEGIN
    SELECT status INTO v_old_status FROM purchase_requisitions WHERE id = p_requisition_id;

    IF v_old_status != 'pending' THEN
        RAISE EXCEPTION '只能审批待审批状态的请购单';
    END IF;

    v_new_status := CASE WHEN p_approved THEN 'approved' ELSE 'rejected' END;

    UPDATE purchase_requisitions
    SET status = v_new_status::req_status,
        remark = COALESCE(remark, '') || ' | 审批意见: ' || p_remark
    WHERE id = p_requisition_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, old_value, new_value)
    VALUES ('approve_requisition', 'purchase_requisition', p_requisition_id, p_approver_id,
            jsonb_build_object('status', v_old_status),
            jsonb_build_object('status', v_new_status, 'remark', p_remark));

    RAISE NOTICE 'success: %', FOUND;
END;
$$;


-- ============================================================
-- 6. 采购流程
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_purchase_order(
    p_supplier_id BIGINT,
    p_requisition_id BIGINT,
    p_department_id BIGINT,
    p_purchaser_id BIGINT,
    p_expected_delivery_date DATE,
    p_payment_terms VARCHAR(100),
    p_items_json JSONB,
    p_remark VARCHAR(500)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_order_no VARCHAR(30);
    v_order_id BIGINT;
    v_total DECIMAL(18,2) := 0.00;
    v_item_count INTEGER;
    v_item JSONB;
    v_product_id BIGINT;
    v_qty INTEGER;
    v_price DECIMAL(12,2);
BEGIN
    v_order_no := 'PO-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || LPAD((FLOOR(RANDOM() * 9999) + 1)::TEXT, 4, '0');
    v_item_count := jsonb_array_length(p_items_json);

    IF v_item_count = 0 THEN
        RAISE EXCEPTION '采购明细不能为空';
    END IF;

    INSERT INTO purchase_orders (order_no, supplier_id, requisition_id, department_id,
        purchaser_id, order_date, expected_delivery_date, payment_terms, status, remark)
    VALUES (v_order_no, p_supplier_id, p_requisition_id, p_department_id,
        p_purchaser_id, CURRENT_DATE, p_expected_delivery_date, p_payment_terms, 'ordered', p_remark)
    RETURNING id INTO v_order_id;

    FOR v_item IN SELECT * FROM jsonb_array_elements(p_items_json)
    LOOP
        v_product_id := (v_item->>'product_id')::BIGINT;
        v_qty := (v_item->>'quantity')::INTEGER;
        v_price := (v_item->>'unit_price')::DECIMAL(12,2);

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
            jsonb_build_object('order_no', v_order_no, 'supplier_id', p_supplier_id, 'total_amount', v_total));

    RAISE NOTICE 'order_id: %, order_no: %, total_amount: %', v_order_id, v_order_no, v_total;
END;
$$;


-- ============================================================
-- 7. 进货入库流程
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_receive_purchase(
    p_order_id BIGINT,
    p_warehouse_id BIGINT,
    p_receiver_id BIGINT,
    p_items_json JSONB,
    p_inspection_result VARCHAR(500),
    p_remark VARCHAR(500)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_receipt_no VARCHAR(30);
    v_receipt_id BIGINT;
    v_order_status VARCHAR(50);
    v_total_qty INTEGER := 0;
    v_total_amount DECIMAL(18,2) := 0.00;
    v_item JSONB;
    v_order_item_id BIGINT;
    v_product_id BIGINT;
    v_received_qty INTEGER;
    v_accepted_qty INTEGER;
    v_rejected_qty INTEGER;
    v_unit_price DECIMAL(12,2);
    v_batch_no VARCHAR(50);
    v_production_date DATE;
    v_expiry_date DATE;
    v_batch_id BIGINT;
    v_before_qty INTEGER;
    v_receipt_status receipt_status;
BEGIN
    SELECT status INTO v_order_status FROM purchase_orders WHERE id = p_order_id;
    IF v_order_status NOT IN ('ordered', 'partially_received') THEN
        RAISE EXCEPTION '采购单状态不允许收货';
    END IF;

    v_receipt_no := 'RC-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || LPAD((FLOOR(RANDOM() * 9999) + 1)::TEXT, 4, '0');

    v_receipt_status := CASE WHEN p_inspection_result IS NULL THEN 'received'::receipt_status ELSE 'inspected'::receipt_status END;

    INSERT INTO purchase_receipts (receipt_no, order_id, warehouse_id, receiver_id,
        receipt_date, status, inspection_result, remark)
    VALUES (v_receipt_no, p_order_id, p_warehouse_id, p_receiver_id, CURRENT_DATE,
        v_receipt_status, p_inspection_result, p_remark)
    RETURNING id INTO v_receipt_id;

    FOR v_item IN SELECT * FROM jsonb_array_elements(p_items_json)
    LOOP
        v_order_item_id := (v_item->>'order_item_id')::BIGINT;
        v_product_id := (v_item->>'product_id')::BIGINT;
        v_received_qty := (v_item->>'received_qty')::INTEGER;
        v_accepted_qty := (v_item->>'accepted_qty')::INTEGER;
        v_rejected_qty := (v_item->>'rejected_qty')::INTEGER;
        v_unit_price := (v_item->>'unit_price')::DECIMAL(12,2);
        v_batch_no := v_item->>'batch_no';
        v_production_date := (v_item->>'production_date')::DATE;
        v_expiry_date := (v_item->>'expiry_date')::DATE;

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
        ON CONFLICT (product_id, batch_id, warehouse_id)
        DO UPDATE SET quantity = inventory.quantity + v_accepted_qty;

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
    IF NOT EXISTS (
        SELECT 1 FROM purchase_order_items
        WHERE order_id = p_order_id AND received_qty < quantity
    ) THEN
        UPDATE purchase_orders SET status = 'received' WHERE id = p_order_id;
    END IF;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('receive_purchase', 'purchase_receipt', v_receipt_id, p_receiver_id,
            jsonb_build_object('receipt_no', v_receipt_no, 'order_id', p_order_id,
                               'total_qty', v_total_qty, 'total_amount', v_total_amount));

    RAISE NOTICE 'receipt_id: %, receipt_no: %, total_qty: %, total_amount: %',
        v_receipt_id, v_receipt_no, v_total_qty, v_total_amount;
END;
$$;


-- ============================================================
-- 8. 销售流程
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_sales_order(
    p_customer_id BIGINT,
    p_salesperson_id BIGINT,
    p_warehouse_id BIGINT,
    p_discount_amount DECIMAL(18,2),
    p_payment_method payment_method,
    p_items_json JSONB,
    p_remark VARCHAR(500)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_order_no VARCHAR(30);
    v_order_id BIGINT;
    v_total_amount DECIMAL(18,2) := 0.00;
    v_item JSONB;
    v_product_id BIGINT;
    v_batch_id BIGINT;
    v_qty INTEGER;
    v_price DECIMAL(12,2);
    v_discount DECIMAL(12,2);
    v_line_amount DECIMAL(18,2);
    v_available INTEGER;
    v_before_qty INTEGER;
    v_credit_limit DECIMAL(18,2);
    v_balance DECIMAL(18,2);
BEGIN
    v_order_no := 'SO-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || LPAD((FLOOR(RANDOM() * 9999) + 1)::TEXT, 4, '0');

    -- 检查信用额度
    SELECT credit_limit, balance INTO v_credit_limit, v_balance FROM customers WHERE id = p_customer_id;

    INSERT INTO sales_orders (order_no, customer_id, salesperson_id, warehouse_id,
        order_date, discount_amount, payment_method, status, remark)
    VALUES (v_order_no, p_customer_id, p_salesperson_id, p_warehouse_id,
        CURRENT_DATE, p_discount_amount, p_payment_method, 'confirmed', p_remark)
    RETURNING id INTO v_order_id;

    FOR v_item IN SELECT * FROM jsonb_array_elements(p_items_json)
    LOOP
        v_product_id := (v_item->>'product_id')::BIGINT;
        v_batch_id := (v_item->>'batch_id')::BIGINT;
        v_qty := (v_item->>'quantity')::INTEGER;
        v_price := (v_item->>'unit_price')::DECIMAL(12,2);
        v_discount := COALESCE((v_item->>'discount')::DECIMAL(12,2), 0);

        -- 检查库存
        SELECT COALESCE(available_quantity, 0) INTO v_available
        FROM inventory
        WHERE product_id = v_product_id
          AND (batch_id = v_batch_id OR (v_batch_id IS NULL AND batch_id IS NULL))
          AND warehouse_id = p_warehouse_id;

        IF v_available < v_qty THEN
            RAISE EXCEPTION '库存不足: 产品ID=%, 可用=%, 需求=%', v_product_id, v_available, v_qty;
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
            jsonb_build_object('order_no', v_order_no, 'customer_id', p_customer_id,
                               'total_amount', v_total_amount - p_discount_amount));

    RAISE NOTICE 'order_id: %, order_no: %, total_amount: %',
        v_order_id, v_order_no, v_total_amount - p_discount_amount;
END;
$$;


-- ============================================================
-- 9. 退库流程
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_sales_return(
    p_order_id BIGINT,
    p_warehouse_id BIGINT,
    p_handler_id BIGINT,
    p_return_reason VARCHAR(500),
    p_return_type sales_return_type,
    p_restock_fee DECIMAL(12,2),
    p_items_json JSONB
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_return_no VARCHAR(30);
    v_return_id BIGINT;
    v_customer_id BIGINT;
    v_total_amount DECIMAL(18,2) := 0.00;
    v_item JSONB;
    v_order_item_id BIGINT;
    v_product_id BIGINT;
    v_batch_id BIGINT;
    v_return_qty INTEGER;
    v_unit_price DECIMAL(12,2);
    v_before_qty INTEGER;
BEGIN
    SELECT customer_id INTO v_customer_id FROM sales_orders WHERE id = p_order_id;

    v_return_no := 'SR-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || LPAD((FLOOR(RANDOM() * 9999) + 1)::TEXT, 4, '0');

    INSERT INTO sales_returns (return_no, order_id, customer_id, warehouse_id, handler_id,
        return_date, return_reason, return_type, restock_fee, status)
    VALUES (v_return_no, p_order_id, v_customer_id, p_warehouse_id, p_handler_id,
        CURRENT_DATE, p_return_reason, p_return_type, p_restock_fee, 'approved')
    RETURNING id INTO v_return_id;

    FOR v_item IN SELECT * FROM jsonb_array_elements(p_items_json)
    LOOP
        v_order_item_id := (v_item->>'order_item_id')::BIGINT;
        v_product_id := (v_item->>'product_id')::BIGINT;
        v_batch_id := (v_item->>'batch_id')::BIGINT;
        v_return_qty := (v_item->>'return_qty')::INTEGER;
        v_unit_price := (v_item->>'unit_price')::DECIMAL(12,2);

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
        ON CONFLICT (product_id, batch_id, warehouse_id)
        DO UPDATE SET quantity = inventory.quantity + v_return_qty;

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
            jsonb_build_object('return_no', v_return_no, 'order_id', p_order_id,
                               'total_amount', v_total_amount, 'reason', p_return_reason));

    RAISE NOTICE 'return_id: %, return_no: %, total_amount: %, refund_amount: %',
        v_return_id, v_return_no, v_total_amount, v_total_amount - p_restock_fee;
END;
$$;


-- ============================================================
-- 10. 工资发放
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_process_salary(
    p_salary_month VARCHAR(7),
    p_processed_by BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_emp_record RECORD;
    v_overtime_pay DECIMAL(12,2);
    v_bonus DECIMAL(12,2);
    v_deduction DECIMAL(12,2);
    v_ss_personal DECIMAL(12,2);
    v_hf_personal DECIMAL(12,2);
    v_income_tax DECIMAL(12,2);
    v_net_pay DECIMAL(12,2);
    v_ss_company DECIMAL(12,2);
    v_hf_company DECIMAL(12,2);
    v_payment_no VARCHAR(30);
    v_taxable DECIMAL(12,2);
    v_overtime_hours DECIMAL(4,1);
    v_late_count INTEGER;
    v_payment_date DATE;
BEGIN
    -- 检查是否已发放
    IF EXISTS (SELECT 1 FROM salary_payments WHERE salary_month = p_salary_month LIMIT 1) THEN
        RAISE EXCEPTION '% 工资已发放，不能重复处理', p_salary_month;
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

        v_overtime_pay := ROUND((v_emp_record.salary / 21.75 / 8 * v_overtime_hours * 1.5)::NUMERIC, 2);

        -- 迟到扣款
        SELECT COUNT(*) INTO v_late_count
        FROM attendance
        WHERE employee_id = v_emp_record.id
          AND TO_CHAR(attendance_date, 'YYYY-MM') = p_salary_month
          AND status = 'late';

        v_deduction := v_late_count * 50;

        -- 社保和公积金（个人部分）
        v_ss_personal := ROUND((v_emp_record.salary * 0.08)::NUMERIC, 2);
        v_hf_personal := ROUND((v_emp_record.salary * 0.12)::NUMERIC, 2);

        -- 个税计算
        v_taxable := v_emp_record.salary + v_overtime_pay - v_ss_personal - v_hf_personal - 5000;
        IF v_taxable <= 0 THEN
            v_income_tax := 0;
        ELSIF v_taxable <= 3000 THEN
            v_income_tax := ROUND((v_taxable * 0.03)::NUMERIC, 2);
        ELSIF v_taxable <= 12000 THEN
            v_income_tax := ROUND((v_taxable * 0.10 - 210)::NUMERIC, 2);
        ELSIF v_taxable <= 25000 THEN
            v_income_tax := ROUND((v_taxable * 0.20 - 1410)::NUMERIC, 2);
        ELSE
            v_income_tax := ROUND((v_taxable * 0.25 - 2660)::NUMERIC, 2);
        END IF;

        v_bonus := 0;
        v_net_pay := v_emp_record.salary + v_overtime_pay + v_bonus - v_deduction
                    - v_ss_personal - v_hf_personal - v_income_tax;

        -- 公司部分
        v_ss_company := ROUND((v_emp_record.salary * 0.16)::NUMERIC, 2);
        v_hf_company := ROUND((v_emp_record.salary * 0.12)::NUMERIC, 2);

        v_payment_no := 'SAL-' || REPLACE(p_salary_month, '-', '') || '-' || LPAD(v_emp_record.id::TEXT, 5, '0');

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
            jsonb_build_object('salary_month', p_salary_month, 'payment_date', v_payment_date));

    RAISE NOTICE '工资发放完成: %', p_salary_month;
END;
$$;


-- ============================================================
-- 11. 出纳日记账
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_cashier_journal(
    p_account_id BIGINT,
    p_cashier_id BIGINT,
    p_journal_type journal_type,
    p_amount DECIMAL(18,2),
    p_counterparty VARCHAR(200),
    p_reference_type VARCHAR(50),
    p_reference_id BIGINT,
    p_bank_account VARCHAR(50),
    p_check_no VARCHAR(50),
    p_remark VARCHAR(500)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_journal_no VARCHAR(30);
    v_journal_id BIGINT;
    v_old_balance DECIMAL(18,2);
BEGIN
    v_journal_no := 'CJ-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || LPAD((FLOOR(RANDOM() * 9999) + 1)::TEXT, 4, '0');

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
            jsonb_build_object('journal_no', v_journal_no, 'type', p_journal_type, 'amount', p_amount));

    RAISE NOTICE 'journal_id: %, journal_no: %', v_journal_id, v_journal_no;
END;
$$;


-- ============================================================
-- 12. 对账
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_reconciliation(
    p_account_id BIGINT,
    p_period_start DATE,
    p_period_end DATE,
    p_bank_balance DECIMAL(18,2),
    p_prepared_by BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_recon_no VARCHAR(30);
    v_recon_id BIGINT;
    v_book_balance DECIMAL(18,2);
    v_unrec_income DECIMAL(18,2) := 0.00;
    v_unrec_expense DECIMAL(18,2) := 0.00;
    v_difference DECIMAL(18,2);
    v_adjusted DECIMAL(18,2);
BEGIN
    v_recon_no := 'REC-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || LPAD((FLOOR(RANDOM() * 9999) + 1)::TEXT, 4, '0');

    -- 获取账面余额
    SELECT current_balance INTO v_book_balance FROM accounts WHERE id = p_account_id;

    -- 未达收入
    SELECT COALESCE(SUM(amount), 0) INTO v_unrec_income
    FROM cashier_journals
    WHERE account_id = p_account_id
      AND journal_date BETWEEN p_period_start AND p_period_end
      AND journal_type IN ('cash_in', 'bank_in')
      AND status = 'pending';

    -- 未达支出
    SELECT COALESCE(SUM(amount), 0) INTO v_unrec_expense
    FROM cashier_journals
    WHERE account_id = p_account_id
      AND journal_date BETWEEN p_period_start AND p_period_end
      AND journal_type IN ('cash_out', 'bank_out')
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
    INSERT INTO reconciliation_items (reconciliation_id, journal_id, transaction_date,
        description, debit_amount, credit_amount, is_matched)
    SELECT
        v_recon_id, cj.id, cj.journal_date,
        cj.journal_type::TEXT || ' - ' || COALESCE(cj.counterparty, '') || ' ' || COALESCE(cj.remark, ''),
        CASE WHEN cj.journal_type IN ('bank_in', 'cash_in') THEN cj.amount ELSE 0 END,
        CASE WHEN cj.journal_type IN ('bank_out', 'cash_out') THEN cj.amount ELSE 0 END,
        FALSE
    FROM cashier_journals cj
    WHERE cj.account_id = p_account_id
      AND cj.journal_date BETWEEN p_period_start AND p_period_end;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_reconciliation', 'reconciliation', v_recon_id, p_prepared_by,
            jsonb_build_object('recon_no', v_recon_no, 'book_balance', v_book_balance,
                               'bank_balance', p_bank_balance, 'difference', v_difference));

    RAISE NOTICE 'reconciliation_id: %, recon_no: %, book_balance: %, bank_balance: %, difference: %, adjusted_balance: %',
        v_recon_id, v_recon_no, v_book_balance, p_bank_balance, v_difference, v_adjusted;
END;
$$;


-- ============================================================
-- 13. 结算
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_settlement(
    p_settlement_type settlement_type,
    p_party_id BIGINT,
    p_period_start DATE,
    p_period_end DATE,
    p_payment_due_date DATE,
    p_payment_method settlement_payment_method,
    p_items_json JSONB,
    p_prepared_by BIGINT,
    p_remark VARCHAR(500)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_settle_no VARCHAR(30);
    v_settle_id BIGINT;
    v_total_amount DECIMAL(18,2) := 0.00;
    v_item JSONB;
    v_ref_type VARCHAR(50);
    v_ref_id BIGINT;
    v_amount DECIMAL(18,2);
BEGIN
    v_settle_no := 'SET-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || LPAD((FLOOR(RANDOM() * 9999) + 1)::TEXT, 4, '0');

    INSERT INTO settlements (settlement_no, settlement_type, party_id, settlement_date,
        period_start, period_end, total_amount, payment_due_date, payment_method,
        prepared_by, status, remark)
    VALUES (v_settle_no, p_settlement_type, p_party_id, CURRENT_DATE,
        p_period_start, p_period_end, 0, p_payment_due_date, p_payment_method,
        p_prepared_by, 'pending', p_remark)
    RETURNING id INTO v_settle_id;

    FOR v_item IN SELECT * FROM jsonb_array_elements(p_items_json)
    LOOP
        v_ref_type := v_item->>'reference_type';
        v_ref_id := (v_item->>'reference_id')::BIGINT;
        v_amount := (v_item->>'amount')::DECIMAL(18,2);

        INSERT INTO settlement_items (settlement_id, reference_type, reference_id, amount)
        VALUES (v_settle_id, v_ref_type, v_ref_id, v_amount);

        v_total_amount := v_total_amount + v_amount;
    END LOOP;

    UPDATE settlements SET total_amount = v_total_amount WHERE id = v_settle_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_settlement', 'settlement', v_settle_id, p_prepared_by,
            jsonb_build_object('settlement_no', v_settle_no, 'type', p_settlement_type, 'total_amount', v_total_amount));

    RAISE NOTICE 'settlement_id: %, settlement_no: %, total_amount: %', v_settle_id, v_settle_no, v_total_amount;
END;
$$;


-- ============================================================
-- 14. 财务凭证
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_voucher(
    p_voucher_date DATE,
    p_voucher_type voucher_type,
    p_reference_type VARCHAR(50),
    p_reference_id BIGINT,
    p_prepared_by BIGINT,
    p_summary VARCHAR(500),
    p_items_json JSONB
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_voucher_no VARCHAR(30);
    v_voucher_id BIGINT;
    v_total_debit DECIMAL(18,2) := 0.00;
    v_total_credit DECIMAL(18,2) := 0.00;
    v_item JSONB;
    v_account_id BIGINT;
    v_direction entry_direction;
    v_amount DECIMAL(18,2);
    v_line_summary VARCHAR(500);
    v_line_no INTEGER := 0;
BEGIN
    v_voucher_no := 'V-' || TO_CHAR(p_voucher_date, 'YYYYMMDD') || '-' || LPAD((FLOOR(RANDOM() * 9999) + 1)::TEXT, 4, '0');

    INSERT INTO vouchers (voucher_no, voucher_date, voucher_type,
        reference_type, reference_id, prepared_by, summary)
    VALUES (v_voucher_no, p_voucher_date, p_voucher_type,
        p_reference_type, p_reference_id, p_prepared_by, p_summary)
    RETURNING id INTO v_voucher_id;

    FOR v_item IN SELECT * FROM jsonb_array_elements(p_items_json)
    LOOP
        v_line_no := v_line_no + 1;
        v_account_id := (v_item->>'account_id')::BIGINT;
        v_direction := (v_item->>'direction')::entry_direction;
        v_amount := (v_item->>'amount')::DECIMAL(18,2);
        v_line_summary := v_item->>'summary';

        INSERT INTO voucher_items (voucher_id, account_id, line_no, direction, amount, summary)
        VALUES (v_voucher_id, v_account_id, v_line_no, v_direction, v_amount, v_line_summary);

        IF v_direction = 'debit' THEN
            v_total_debit := v_total_debit + v_amount;
        ELSE
            v_total_credit := v_total_credit + v_amount;
        END IF;
    END LOOP;

    IF ABS(v_total_debit - v_total_credit) > 0.01 THEN
        RAISE EXCEPTION '借贷不平衡: 借方=%, 贷方=%', v_total_debit, v_total_credit;
    END IF;

    UPDATE vouchers SET total_debit = v_total_debit, total_credit = v_total_credit WHERE id = v_voucher_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_voucher', 'voucher', v_voucher_id, p_prepared_by,
            jsonb_build_object('voucher_no', v_voucher_no, 'total_debit', v_total_debit, 'total_credit', v_total_credit));

    RAISE NOTICE 'voucher_id: %, voucher_no: %, total_debit: %, total_credit: %',
        v_voucher_id, v_voucher_no, v_total_debit, v_total_credit;
END;
$$;


-- 过账凭证
CREATE OR REPLACE PROCEDURE sp_post_voucher(
    p_voucher_id BIGINT,
    p_posted_by BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_status VARCHAR(20);
    v_rec RECORD;
BEGIN
    SELECT status INTO v_status FROM vouchers WHERE id = p_voucher_id;

    IF v_status NOT IN ('reviewed', 'draft') THEN
        RAISE EXCEPTION '只能过账已审核或草稿状态的凭证';
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
            jsonb_build_object('status', 'posted'));

    RAISE NOTICE '凭证过账成功';
END;
$$;


-- ============================================================
-- 15. 批量生成考勤数据
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_generate_attendance(
    p_year_month VARCHAR(7)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_emp_record RECORD;
    v_day INTEGER;
    v_days_in_month INTEGER;
    v_att_date DATE;
    v_day_of_week INTEGER;
    v_clock_in TIME;
    v_clock_out TIME;
    v_status attendance_status;
    v_late_min INTEGER;
    v_rand_val DOUBLE PRECISION;
    v_month_start DATE;
BEGIN
    v_month_start := TO_DATE(p_year_month || '-01', 'YYYY-MM-DD');
    v_days_in_month := EXTRACT(DAY FROM (date_trunc('month', v_month_start) + INTERVAL '1 month' - INTERVAL '1 day'));

    FOR v_emp_record IN
        SELECT id FROM employees WHERE status IN ('active', 'probation')
    LOOP
        v_day := 1;
        WHILE v_day <= v_days_in_month LOOP
            v_att_date := v_month_start + (v_day - 1);
            v_day_of_week := EXTRACT(DOW FROM v_att_date);

            -- 周末不上班 (PG: 0=Sunday, 6=Saturday)
            IF v_day_of_week IN (0, 6) THEN
                v_day := v_day + 1;
                CONTINUE;
            END IF;

            v_rand_val := RANDOM();

            IF v_rand_val < 0.05 THEN
                v_status := 'absent';
                v_clock_in := NULL;
                v_clock_out := NULL;
                v_late_min := 0;
            ELSIF v_rand_val < 0.15 THEN
                v_status := 'late';
                v_late_min := FLOOR(RANDOM() * 60) + 1;
                v_clock_in := ('08:00:00'::TIME + (v_late_min || ' minutes')::INTERVAL);
                v_clock_out := ('17:00:00'::TIME + (FLOOR(RANDOM() * 60) || ' minutes')::INTERVAL);
            ELSIF v_rand_val < 0.20 THEN
                v_status := 'early';
                v_late_min := 0;
                v_clock_in := '08:00:00'::TIME;
                v_clock_out := ('17:00:00'::TIME - ((FLOOR(RANDOM() * 60) + 1) || ' minutes')::INTERVAL);
            ELSE
                v_status := 'normal';
                v_late_min := 0;
                v_clock_in := ('08:00:00'::TIME + (FLOOR(RANDOM() * 10) || ' minutes')::INTERVAL);
                v_clock_out := ('17:00:00'::TIME + (FLOOR(RANDOM() * 60) || ' minutes')::INTERVAL);
            END IF;

            INSERT INTO attendance (employee_id, attendance_date, clock_in, clock_out, status, late_minutes)
            VALUES (v_emp_record.id, v_att_date,
                CASE WHEN v_clock_in IS NULL THEN NULL ELSE (v_att_date + v_clock_in)::TIMESTAMP(0) END,
                CASE WHEN v_clock_out IS NULL THEN NULL ELSE (v_att_date + v_clock_out)::TIMESTAMP(0) END,
                v_status, v_late_min)
            ON CONFLICT (employee_id, attendance_date)
            DO UPDATE SET
                clock_in = EXCLUDED.clock_in,
                clock_out = EXCLUDED.clock_out,
                status = EXCLUDED.status,
                late_minutes = EXCLUDED.late_minutes;

            v_day := v_day + 1;
        END LOOP;
    END LOOP;

    RAISE NOTICE '考勤数据生成完成: %', p_year_month;
END;
$$;