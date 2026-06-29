-- PostgreSQL translations of the MySQL basic-correctness proc_* procedures.
-- These objects keep the relation/lineage-bearing business DML in PostgreSQL PL/pgSQL form.

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_call_generate_po
CREATE OR REPLACE PROCEDURE proc_batch_call_generate_po(p_count integer DEFAULT 1, p_login_name text DEFAULT NULL)
LANGUAGE plpgsql
AS $$
DECLARE
    i integer := 0;
BEGIN
    WHILE i < COALESCE(p_count, 0) LOOP
        CALL proc_generate_purchase_order_from_requisition(p_login_name);
        i := i + 1;
    END LOOP;
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_generate_purchase_inbound
CREATE OR REPLACE PROCEDURE proc_batch_generate_purchase_inbound(p_batch_count integer DEFAULT 1, p_login_name text DEFAULT NULL)
LANGUAGE plpgsql
AS $$
DECLARE
    i integer := 0;
BEGIN
    WHILE i < COALESCE(p_batch_count, 0) LOOP
        CALL proc_generate_purchase_inbound_from_order(p_login_name);
        i := i + 1;
    END LOOP;
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_insert_purchase_requisition
CREATE OR REPLACE PROCEDURE proc_batch_insert_purchase_requisition(p_login_name text DEFAULT NULL, p_start_str text DEFAULT NULL, p_stop_str text DEFAULT NULL, p_total_count integer DEFAULT 0)
LANGUAGE plpgsql
AS $$
DECLARE
    i integer := 0;
BEGIN
    WHILE i < COALESCE(p_total_count, 0) LOOP
        CALL proc_insert_purchase_requisition(p_start_str, p_stop_str, 1);
        i := i + 1;
    END LOOP;
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_batch_mock_retail_orders
CREATE OR REPLACE PROCEDURE proc_batch_mock_retail_orders(p_login_name text DEFAULT NULL)
LANGUAGE plpgsql
AS $$
BEGIN
    CREATE TEMPORARY TABLE tmp_mock_batch_results (
        status text,
        order_id bigint,
        bill_no text,
        mock_time timestamp,
        operator text,
        item_count integer,
        total_amount numeric,
        remark text
    );
    CALL proc_worker_daily_distribution(1, CURRENT_DATE, 0);
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_create_order_mock_retail
CREATE OR REPLACE PROCEDURE proc_create_order_mock_retail(p_bill_no text, p_login_id bigint, p_user_id bigint, p_timestamp text)
LANGUAGE plpgsql
AS $$
DECLARE
    v_head_id bigint;
    v_depot_id bigint;
    v_tenant_id bigint;
    v_mock_dt timestamp;
BEGIN
    SELECT tenant_id INTO v_tenant_id
    FROM jsh_user
    WHERE id = p_login_id
    LIMIT 1;

    SELECT id INTO v_depot_id
    FROM jsh_depot
    WHERE tenant_id = v_tenant_id AND enabled = true
    LIMIT 1;

    CREATE TEMPORARY TABLE tmp_available_categories (cat_id bigint PRIMARY KEY);
    INSERT INTO tmp_available_categories (cat_id)
    SELECT DISTINCT m.category_id
    FROM jsh_material_current_stock mcs
    JOIN jsh_material m ON mcs.material_id = m.id
    WHERE mcs.depot_id = v_depot_id
      AND mcs.current_number > 0;

    CREATE TEMPORARY TABLE tmp_pdf_selection (cat_id bigint, target_ratio numeric);
    CREATE TEMPORARY TABLE tmp_affinity_buffer (cat_id bigint, t_ratio numeric);
    CREATE TEMPORARY TABLE tmp_final_categories (cat_id bigint PRIMARY KEY, t_ratio numeric);

    INSERT INTO jsh_depot_head (type, sub_type, default_number, number, create_time, oper_time, creator, status, tenant_id, delete_flag)
    VALUES ('出库', '零售', p_bill_no, p_bill_no, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, p_user_id, '0', v_tenant_id, '0');
    v_head_id := 1;

    SELECT mcs.material_id, m.id, jme.material_id
    FROM jsh_material_current_stock mcs
    JOIN jsh_material m ON mcs.material_id = m.id
    LEFT JOIN jsh_material_extend jme ON jme.material_id = mcs.material_id
    WHERE mcs.depot_id = v_depot_id
      AND mcs.current_number > 0;

    INSERT INTO jsh_depot_item (
        header_id, material_id, material_extend_id, material_unit,
        oper_number, basic_number, unit_price, all_price, depot_id, tenant_id
    )
    VALUES (v_head_id, 1, 1, 'unit', 1, 1, 1, 1, v_depot_id, v_tenant_id);

    UPDATE jsh_material_current_stock mcs
    SET current_number = mcs.current_number - jdi.oper_number
    FROM jsh_depot_item jdi
    WHERE jdi.material_id = mcs.material_id
      AND jdi.depot_id = mcs.depot_id
      AND jdi.header_id = v_head_id;
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_generate_purchase_inbound_from_order
CREATE OR REPLACE PROCEDURE proc_generate_purchase_inbound_from_order(p_login_name text DEFAULT NULL)
LANGUAGE plpgsql
AS $$
DECLARE
    v_new_header_id bigint;
BEGIN
    INSERT INTO jsh_depot_head (type, sub_type, number, oper_time, creator, tenant_id, delete_flag, status)
    VALUES ('入库', '采购', 'CGRK00000000001', CURRENT_TIMESTAMP, 1, 1, '0', '0');
    v_new_header_id := 1;

    INSERT INTO jsh_depot_item (
        header_id, material_id, material_extend_id, material_unit, material_type, sku,
        oper_number, basic_number, unit_price, purchase_unit_price, tax_unit_price,
        all_price, tax_money, tax_rate, tax_last_money, another_depot_id,
        remark, tenant_id, delete_flag, link_id, sn_list, expiration_date
    )
    SELECT
        v_new_header_id,
        di.material_id,
        di.material_extend_id,
        di.material_unit,
        di.material_type,
        di.sku,
        di.oper_number,
        di.basic_number,
        me.purchase_decimal,
        di.purchase_unit_price,
        di.tax_unit_price,
        di.oper_number * me.purchase_decimal,
        di.tax_money,
        di.tax_rate,
        di.oper_number * me.purchase_decimal,
        di.another_depot_id,
        di.remark,
        di.tenant_id,
        di.delete_flag,
        di.id,
        di.sn_list,
        date_add_days(CURRENT_TIMESTAMP, m.expiry_num)
    FROM jsh_depot_item di
    JOIN jsh_depot_head dh ON di.header_id = dh.id
    JOIN jsh_material m ON di.material_id = m.id
    JOIN jsh_material_extend me ON di.material_id = me.material_id
    LEFT JOIN jsh_material_current_stock mcs ON di.material_id = mcs.material_id AND di.depot_id = mcs.depot_id
    WHERE dh.sub_type = '采购订单';

    UPDATE jsh_material_current_stock mcs
    SET current_number = mcs.current_number + di.oper_number
    FROM jsh_depot_item di
    WHERE di.material_id = mcs.material_id
      AND di.depot_id = mcs.depot_id
      AND di.header_id = v_new_header_id;

    UPDATE jsh_depot_head h
    SET total_price = totals.total_price,
        change_amount = totals.total_price,
        discount_last_money = totals.discount_last_money
    FROM (
        SELECT header_id, SUM(all_price) AS total_price, SUM(tax_last_money) AS discount_last_money
        FROM jsh_depot_item
        GROUP BY header_id
    ) totals
    WHERE h.id = totals.header_id;
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_generate_purchase_order_from_requisition
CREATE OR REPLACE PROCEDURE proc_generate_purchase_order_from_requisition(p_login_name text DEFAULT NULL)
LANGUAGE plpgsql
AS $$
DECLARE
    v_new_header_id bigint;
BEGIN
    INSERT INTO jsh_depot_head (type, sub_type, number, oper_time, creator, tenant_id, delete_flag, status)
    VALUES ('其它', '采购订单', 'CGDD00000000001', CURRENT_TIMESTAMP, 1, 1, '0', '0');
    v_new_header_id := 1;

    INSERT INTO jsh_depot_item (
        header_id, material_id, material_extend_id, material_unit, material_type, sku,
        oper_number, basic_number, unit_price, purchase_unit_price, tax_unit_price,
        all_price, tax_money, tax_rate, tax_last_money, depot_id,
        remark, tenant_id, delete_flag, link_id
    )
    SELECT
        v_new_header_id,
        di.material_id,
        di.material_extend_id,
        di.material_unit,
        di.material_type,
        di.sku,
        di.oper_number,
        di.basic_number,
        me.purchase_decimal,
        di.purchase_unit_price,
        di.tax_unit_price,
        di.oper_number * me.purchase_decimal,
        di.tax_money,
        di.tax_rate,
        di.oper_number * me.purchase_decimal,
        di.depot_id,
        di.remark,
        di.tenant_id,
        di.delete_flag,
        di.id
    FROM jsh_depot_item di
    JOIN jsh_depot_head dh ON di.header_id = dh.id
    JOIN jsh_material_extend me ON di.material_id = me.material_id
    WHERE dh.sub_type = '请购单';

    UPDATE jsh_depot_head h
    SET total_price = totals.total_price,
        discount_last_money = totals.discount_last_money
    FROM (
        SELECT header_id, SUM(all_price) AS total_price, SUM(tax_last_money) AS discount_last_money
        FROM jsh_depot_item
        GROUP BY header_id
    ) totals
    WHERE h.id = totals.header_id;
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_init_yearly_weights
CREATE OR REPLACE PROCEDURE proc_init_yearly_weights(p_year integer)
LANGUAGE plpgsql
AS $$
BEGIN
    CREATE TEMPORARY TABLE tmp_yearly_weights (month_no integer, weight numeric);
    INSERT INTO tmp_yearly_weights (month_no, weight)
    SELECT 1, 1.0;
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_insert_purchase_requisition
CREATE OR REPLACE PROCEDURE proc_insert_purchase_requisition(p_start_str text DEFAULT NULL, p_stop_str text DEFAULT NULL, p_tenant_id bigint DEFAULT NULL)
LANGUAGE plpgsql
AS $$
BEGIN
    SELECT m.id, me.id, aff.target_cat_id
    FROM jsh_material m
    JOIN jsh_material_extend me ON me.material_id = m.id
    JOIN jsh_temp_category_affinity aff ON m.category_id = aff.source_cat_id
    WHERE m.tenant_id = p_tenant_id;
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_refresh_org_pdf
CREATE OR REPLACE PROCEDURE proc_refresh_org_pdf(p_tenant_id bigint DEFAULT NULL)
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO jsh_temp_org_pdf (org_id, weight, remark)
    SELECT
        o.id,
        CASE
            WHEN o.org_no <> '' THEN 10
            ELSE 1
        END,
        o.org_abr
    FROM jsh_organization o
    WHERE o.parent_id IN (
        SELECT p.id
        FROM jsh_organization p
        WHERE p.tenant_id = p_tenant_id
    );

    UPDATE jsh_temp_org_pdf
    SET weight = CASE
        WHEN (SELECT o.org_abr FROM jsh_organization o LIMIT 1) <> '' THEN 5
        ELSE 1
    END;

    UPDATE jsh_temp_org_pdf t
    SET cdf_end = running.cdf_end
    FROM (
        SELECT org_id, SUM(weight) OVER (ORDER BY org_id) AS cdf_end
        FROM jsh_temp_org_pdf
    ) running
    WHERE t.org_id = running.org_id;
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_simulate_yearly_sales
CREATE OR REPLACE PROCEDURE proc_simulate_yearly_sales(p_year integer DEFAULT NULL, p_total_annual_orders integer DEFAULT NULL, p_login_name text DEFAULT NULL)
LANGUAGE plpgsql
AS $$
DECLARE
    v_day date;
BEGIN
    v_day := make_date(COALESCE(p_year, EXTRACT(YEAR FROM CURRENT_DATE)::integer), 1, 1);
    WHILE v_day < make_date(COALESCE(p_year, EXTRACT(YEAR FROM CURRENT_DATE)::integer) + 1, 1, 1) LOOP
        CALL proc_worker_daily_distribution(1, v_day, 0);
        v_day := v_day + INTERVAL '1 day';
    END LOOP;
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: PROCEDURE:case_01.proc_worker_daily_distribution
CREATE OR REPLACE PROCEDURE proc_worker_daily_distribution(p_login_id bigint DEFAULT NULL, p_target_date date DEFAULT NULL, p_total_orders integer DEFAULT NULL)
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO jsh_temp_mock_plan (user_id, mock_timestamp_str)
    SELECT
        our.user_id,
        hp.hour_val + SUM(hp.weight) OVER (ORDER BY hp.hour_val)
    FROM jsh_orga_user_rel our
    JOIN jsh_temp_org_pdf pdf ON our.orga_id = pdf.org_id
    JOIN jsh_temp_hour_pdf hp ON hp.hour_val >= 0
    WHERE our.delete_flag = '0';
END;
$$;
-- relation-detector-fixture-end
