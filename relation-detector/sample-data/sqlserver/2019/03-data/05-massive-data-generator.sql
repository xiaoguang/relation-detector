-- Natural bulk inventory initialization used for scale-oriented sample data.
-- The procedure creates operational batches, warehouse balances, and opening
-- inventory transactions from existing product, supplier, and warehouse facts.

-- relation-detector-fixture-source:sqlserver.sp_seed_inventory_capacity
CREATE OR ALTER PROCEDURE [dbo].[sp_seed_inventory_capacity]
AS
BEGIN
    INSERT INTO [dbo].[product_batches] (
        [product_id], [batch_no], [production_date], [expiry_date], [supplier_id],
        [purchase_price], [initial_qty], [current_qty], [status], [created_at]
    )
    SELECT
        p.[id],
        CONCAT('OPEN-', p.[sku]),
        DATEADD(day, -30, CAST(CURRENT_TIMESTAMP AS DATE)),
        CASE WHEN p.[shelf_life_days] IS NULL THEN NULL
             ELSE DATEADD(day, p.[shelf_life_days] - 30, CAST(CURRENT_TIMESTAMP AS DATE)) END,
        preferred_supplier.[supplier_id],
        COALESCE(preferred_supplier.[supplier_price], p.[purchase_price]),
        CASE WHEN p.[min_stock] > 0 THEN p.[min_stock] * 2 ELSE 100 END,
        CASE WHEN p.[min_stock] > 0 THEN p.[min_stock] * 2 ELSE 100 END,
        'available',
        CURRENT_TIMESTAMP
    FROM [dbo].[products] AS p
    OUTER APPLY (
        SELECT TOP (1) sp.[supplier_id], sp.[supplier_price]
        FROM [dbo].[supplier_products] AS sp
        WHERE sp.[product_id] = p.[id]
        ORDER BY sp.[is_preferred] DESC, sp.[supplier_price], sp.[id]
    ) AS preferred_supplier
    WHERE p.[status] = 'active'
      AND NOT EXISTS (
          SELECT 1
          FROM [dbo].[product_batches] AS existing_batch
          WHERE existing_batch.[product_id] = p.[id]
            AND existing_batch.[batch_no] = CONCAT('OPEN-', p.[sku])
      );

    INSERT INTO [dbo].[inventory] (
        [product_id], [batch_id], [warehouse_id], [shelf_location], [quantity],
        [locked_quantity], [available_quantity], [last_stocktake_date], [updated_at]
    )
    SELECT
        pb.[product_id],
        pb.[id],
        w.[id],
        CONCAT('OPEN-', w.[code]),
        pb.[current_qty],
        0,
        pb.[current_qty],
        CAST(CURRENT_TIMESTAMP AS DATE),
        CURRENT_TIMESTAMP
    FROM [dbo].[product_batches] AS pb
    CROSS JOIN [dbo].[warehouses] AS w
    WHERE pb.[batch_no] = CONCAT('OPEN-', (SELECT p.[sku] FROM [dbo].[products] AS p WHERE p.[id] = pb.[product_id]))
      AND w.[status] = 'active'
      AND NOT EXISTS (
          SELECT 1
          FROM [dbo].[inventory] AS existing_inventory
          WHERE existing_inventory.[product_id] = pb.[product_id]
            AND existing_inventory.[batch_id] = pb.[id]
            AND existing_inventory.[warehouse_id] = w.[id]
      );

    INSERT INTO [dbo].[inventory_transactions] (
        [product_id], [batch_id], [warehouse_id], [transaction_type], [quantity_change],
        [before_qty], [after_qty], [reference_type], [reference_id], [operator_id], [remark], [created_at]
    )
    SELECT
        i.[product_id], i.[batch_id], i.[warehouse_id], 'opening_balance',
        i.[quantity], 0, i.[quantity], 'inventory', i.[id], w.[manager_id],
        'opening inventory generated from active product capacity', CURRENT_TIMESTAMP
    FROM [dbo].[inventory] AS i
    INNER JOIN [dbo].[warehouses] AS w ON w.[id] = i.[warehouse_id]
    WHERE i.[shelf_location] = CONCAT('OPEN-', w.[code])
      AND NOT EXISTS (
          SELECT 1
          FROM [dbo].[inventory_transactions] AS existing_transaction
          WHERE existing_transaction.[reference_type] = 'inventory'
            AND existing_transaction.[reference_id] = i.[id]
            AND existing_transaction.[transaction_type] = 'opening_balance'
      );
END;
-- relation-detector-fixture-end
