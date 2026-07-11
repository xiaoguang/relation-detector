-- Natural purchase-return and warehouse-damage transactions.
-- T-SQL 2016-compatible baseline shared by all SQL Server sample versions.

INSERT INTO [dbo].[purchase_returns] (
    [return_no], [purchase_order_id], [purchase_receipt_id], [supplier_id],
    [warehouse_id], [handler_id], [return_date], [return_reason], [return_type],
    [total_amount], [refund_received], [status], [approved_by], [approved_at], [created_at]
)
SELECT TOP (15)
    CONCAT('PRT-', po.[order_no]),
    po.[id],
    receipt.[id],
    po.[supplier_id],
    COALESCE(receipt.[warehouse_id], 1),
    po.[purchaser_id],
    COALESCE(receipt.[receipt_date], po.[actual_delivery_date], po.[order_date]),
    'quality inspection return',
    'quality',
    po.[total_amount] * 0.10,
    po.[paid_amount] * 0.10,
    CASE WHEN receipt.[inspection_result] = 'rejected' THEN 'approved' ELSE 'pending' END,
    CASE WHEN receipt.[inspection_result] = 'rejected' THEN receipt.[receiver_id] ELSE NULL END,
    CASE WHEN receipt.[inspection_result] = 'rejected' THEN receipt.[receipt_date] ELSE NULL END,
    CURRENT_TIMESTAMP
FROM [dbo].[purchase_orders] AS po
OUTER APPLY (
    SELECT TOP (1) pr.[id], pr.[warehouse_id], pr.[receiver_id], pr.[receipt_date], pr.[inspection_result]
    FROM [dbo].[purchase_receipts] AS pr
    WHERE pr.[order_id] = po.[id]
    ORDER BY pr.[receipt_date] DESC, pr.[id] DESC
) AS receipt
WHERE po.[status] IN ('received', 'closed')
ORDER BY po.[order_date] DESC, po.[id] DESC;

INSERT INTO [dbo].[purchase_return_items] (
    [return_id], [product_id], [batch_id], [return_qty], [unit_price], [amount], [reason]
)
SELECT
    pr.[id],
    order_item.[product_id],
    batch.[id],
    CASE WHEN order_item.[received_qty] > 0 THEN order_item.[received_qty] ELSE 1 END,
    order_item.[unit_price],
    CASE WHEN order_item.[received_qty] > 0 THEN order_item.[received_qty] ELSE 1 END * order_item.[unit_price],
    pr.[return_reason]
FROM [dbo].[purchase_returns] AS pr
CROSS APPLY (
    SELECT TOP (1) poi.[product_id], poi.[unit_price], poi.[received_qty]
    FROM [dbo].[purchase_order_items] AS poi
    WHERE poi.[order_id] = pr.[purchase_order_id]
    ORDER BY poi.[id]
) AS order_item
OUTER APPLY (
    SELECT TOP (1) pb.[id]
    FROM [dbo].[product_batches] AS pb
    WHERE pb.[product_id] = order_item.[product_id]
      AND (pb.[supplier_id] = pr.[supplier_id] OR pb.[supplier_id] IS NULL)
    ORDER BY pb.[production_date] DESC, pb.[id] DESC
) AS batch;

INSERT INTO [dbo].[damage_reports] (
    [report_no], [warehouse_id], [report_type], [report_date], [reported_by],
    [total_quantity], [total_loss_amount], [status], [approved_by], [approved_at], [description], [created_at]
)
SELECT
    CONCAT('DMG-', w.[code], '-', CONVERT(NVARCHAR(8), CURRENT_TIMESTAMP, 112)),
    w.[id],
    'expired',
    CAST(CURRENT_TIMESTAMP AS DATE),
    COALESCE(w.[manager_id], 1),
    SUM(CASE WHEN pb.[expiry_date] < CAST(CURRENT_TIMESTAMP AS DATE) THEN i.[quantity] ELSE 0 END),
    SUM(CASE WHEN pb.[expiry_date] < CAST(CURRENT_TIMESTAMP AS DATE) THEN i.[quantity] * p.[purchase_price] ELSE 0 END),
    'approved',
    COALESCE(w.[manager_id], 1),
    CURRENT_TIMESTAMP,
    'expired inventory identified during warehouse review',
    CURRENT_TIMESTAMP
FROM [dbo].[warehouses] AS w
INNER JOIN [dbo].[inventory] AS i ON i.[warehouse_id] = w.[id]
INNER JOIN [dbo].[products] AS p ON p.[id] = i.[product_id]
LEFT JOIN [dbo].[product_batches] AS pb ON pb.[id] = i.[batch_id]
GROUP BY w.[id], w.[code], w.[manager_id]
HAVING SUM(CASE WHEN pb.[expiry_date] < CAST(CURRENT_TIMESTAMP AS DATE) THEN i.[quantity] ELSE 0 END) > 0;

INSERT INTO [dbo].[damage_report_items] (
    [report_id], [product_id], [batch_id], [quantity], [unit_cost], [loss_amount], [reason]
)
SELECT
    dr.[id],
    i.[product_id],
    i.[batch_id],
    i.[quantity],
    p.[purchase_price],
    i.[quantity] * p.[purchase_price],
    'expired batch'
FROM [dbo].[damage_reports] AS dr
INNER JOIN [dbo].[inventory] AS i ON i.[warehouse_id] = dr.[warehouse_id]
INNER JOIN [dbo].[products] AS p ON p.[id] = i.[product_id]
INNER JOIN [dbo].[product_batches] AS pb ON pb.[id] = i.[batch_id]
WHERE dr.[report_type] = 'expired'
  AND pb.[expiry_date] < dr.[report_date];
