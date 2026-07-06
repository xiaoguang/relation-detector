-- PostgreSQL business case 11: DELETE USING with deep nested subqueries, LEFT/FULL/INNER joins, and regex tools.
DELETE FROM inventory_snapshots isc
USING (
    SELECT
        core_inv.snapshot_id,
        core_inv.sku_code,
        UPPER(REGEXP_REPLACE(core_inv.batch_no, '[^a-zA-Z0-9]', '', 'g')) AS cleaned_batch
    FROM (
        SELECT
            i.id AS snapshot_id,
            i.sku_code,
            i.batch_no,
            i.warehouse_id
        FROM supplier_inventory_logs i
        LEFT JOIN (
            SELECT
                product_sku,
                SUM(quantity_sold) AS total_units,
                COUNT(DISTINCT order_id) AS discrete_sales
            FROM sales_order_items
            WHERE order_timestamp >= (CURRENT_DATE - INTERVAL '90 days') AT TIME ZONE 'UTC'
            GROUP BY product_sku
            HAVING SUM(quantity_sold) < 10 AND COUNT(DISTINCT order_id) <= 2
        ) sales_summary ON i.sku_code = sales_summary.product_sku
        WHERE i.archive_status = 'PENDING_PURGE'
           OR (sales_summary.total_units IS NULL AND SUBSTRING(i.sku_code FROM 1 FOR 3) IN ('OLD', 'TMP'))
    ) core_inv
    FULL OUTER JOIN warehouse_facilities wf ON core_inv.warehouse_id = wf.id
    WHERE wf.operational_status = 'DECOMMISSIONED'
       OR wf.country_iso = 'XX'
) metadata_engine
INNER JOIN master_skus ms ON metadata_engine.sku_code = ms.sku_ref
WHERE isc.snapshot_id = metadata_engine.snapshot_id
  AND ms.is_perishable = true
  AND isc.logged_date < NOW() - INTERVAL '180 days';
