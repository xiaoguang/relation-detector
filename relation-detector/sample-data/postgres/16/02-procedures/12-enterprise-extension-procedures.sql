-- ============================================================
-- ERP企业级扩展流程存储过程 - PostgreSQL 16
-- 覆盖: 库存盘点过账、库存调拨申请、会计期间关闭
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_post_stocktake(
    IN p_stocktake_id BIGINT,
    IN p_posted_by BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_warehouse_id BIGINT;
BEGIN
    SELECT warehouse_id INTO v_warehouse_id
    FROM stocktakes
    WHERE id = p_stocktake_id
      AND status IN ('reviewed', 'counting', 'draft');

    INSERT INTO inventory_transactions (
        product_id, batch_id, warehouse_id, transaction_type,
        quantity_change, before_qty, after_qty, reference_type, reference_id,
        operator_id, remark
    )
    SELECT
        sti.product_id,
        sti.batch_id,
        v_warehouse_id,
        'stocktake_adjust',
        sti.counted_quantity - i.quantity,
        i.quantity,
        sti.counted_quantity,
        'stocktake',
        p_stocktake_id,
        p_posted_by,
        '盘点过账: ' || st.stocktake_no
    FROM stocktake_items sti
    JOIN stocktakes st ON st.id = sti.stocktake_id
    JOIN inventory i ON i.product_id = sti.product_id
        AND i.warehouse_id = st.warehouse_id
        AND i.batch_id IS NOT DISTINCT FROM sti.batch_id
    WHERE sti.stocktake_id = p_stocktake_id
      AND sti.counted_quantity <> i.quantity;

    UPDATE inventory i
    SET quantity = sti.counted_quantity,
        last_stocktake_date = st.stocktake_date
    FROM stocktake_items sti
    JOIN stocktakes st ON st.id = sti.stocktake_id
    WHERE i.product_id = sti.product_id
      AND i.batch_id IS NOT DISTINCT FROM sti.batch_id
      AND i.warehouse_id = st.warehouse_id
      AND sti.stocktake_id = p_stocktake_id;

    UPDATE stocktakes
    SET status = 'posted',
        posted_at = CURRENT_TIMESTAMP
    WHERE id = p_stocktake_id;

    INSERT INTO audit_log (employee_id, action, target_type, target_id, new_value)
    VALUES (p_posted_by, 'post_stocktake', 'stocktake', p_stocktake_id,
            jsonb_build_object('status', 'posted', 'warehouse_id', v_warehouse_id));
END;
$$;

CREATE OR REPLACE PROCEDURE sp_create_stock_transfer(
    IN p_transfer_no VARCHAR,
    IN p_from_warehouse_id BIGINT,
    IN p_to_warehouse_id BIGINT,
    IN p_product_id BIGINT,
    IN p_batch_id BIGINT,
    IN p_quantity INTEGER,
    IN p_requested_by BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_transfer_id BIGINT;
BEGIN
    INSERT INTO stock_transfers (
        transfer_no, from_warehouse_id, to_warehouse_id,
        requested_by, transfer_date, status
    )
    VALUES (
        p_transfer_no, p_from_warehouse_id, p_to_warehouse_id,
        p_requested_by, CURRENT_DATE, 'draft'
    )
    RETURNING id INTO v_transfer_id;

    INSERT INTO stock_transfer_items (transfer_id, product_id, batch_id, quantity, received_quantity)
    VALUES (v_transfer_id, p_product_id, p_batch_id, p_quantity, 0);

    INSERT INTO inventory_reservations (
        reservation_no, product_id, batch_id, warehouse_id, source_type, source_id,
        reserved_quantity, released_quantity, status, expires_at
    )
    VALUES (
        'RSV-TR-' || v_transfer_id,
        p_product_id, p_batch_id, p_from_warehouse_id, 'transfer', v_transfer_id,
        p_quantity, 0, 'reserved', CURRENT_TIMESTAMP + INTERVAL '3 days'
    );

    INSERT INTO audit_log (employee_id, action, target_type, target_id, new_value)
    VALUES (p_requested_by, 'create_stock_transfer', 'stock_transfer', v_transfer_id,
            jsonb_build_object('from_warehouse_id', p_from_warehouse_id,
                               'to_warehouse_id', p_to_warehouse_id,
                               'product_id', p_product_id,
                               'quantity', p_quantity));
END;
$$;

CREATE OR REPLACE PROCEDURE sp_close_accounting_period(
    IN p_period_id BIGINT,
    IN p_closed_by BIGINT
)
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE accounting_periods
    SET status = 'closing'
    WHERE id = p_period_id AND status = 'open';

    UPDATE period_close_jobs
    SET status = 'success',
        started_at = COALESCE(started_at, CURRENT_TIMESTAMP),
        finished_at = CURRENT_TIMESTAMP,
        message = COALESCE(message, '关账任务完成')
    WHERE period_id = p_period_id
      AND status IN ('pending', 'running');

    UPDATE accounting_periods
    SET status = 'closed',
        closed_by = p_closed_by,
        closed_at = CURRENT_TIMESTAMP
    WHERE id = p_period_id;

    INSERT INTO audit_log (employee_id, action, target_type, target_id, new_value)
    VALUES (p_closed_by, 'close_accounting_period', 'accounting_period', p_period_id,
            jsonb_build_object('status', 'closed'));
END;
$$;
