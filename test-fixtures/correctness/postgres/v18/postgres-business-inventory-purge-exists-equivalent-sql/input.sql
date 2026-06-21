-- PostgreSQL business case 11 equivalent: correlated subquery form for the low-sales inventory purge.
DELETE FROM inventory_snapshots isc
USING supplier_inventory_logs i
INNER JOIN warehouse_facilities wf ON i.warehouse_id = wf.id
INNER JOIN master_skus ms ON i.sku_code = ms.sku_ref
WHERE isc.snapshot_id = i.id
  AND ms.is_perishable = true
  AND isc.logged_date < NOW() - INTERVAL '180 days'
  AND (wf.operational_status = 'DECOMMISSIONED' OR wf.country_iso = 'XX')
  AND (
      i.archive_status = 'PENDING_PURGE'
      OR (
          SUBSTRING(i.sku_code FROM 1 FOR 3) IN ('OLD', 'TMP')
          AND NOT EXISTS (
              SELECT 1
              FROM sales_order_items soi
              WHERE soi.product_sku = i.sku_code
                AND soi.order_timestamp >= (CURRENT_DATE - INTERVAL '90 days') AT TIME ZONE 'UTC'
              GROUP BY soi.product_sku
              HAVING SUM(soi.quantity_sold) >= 10 OR COUNT(DISTINCT soi.order_id) > 2
          )
      )
  );
