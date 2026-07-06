-- ============================================================
-- SQL Server ERP natural business query samples.
-- These queries are intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- High-density relationship probes live under test-fixtures/semantic-equivalent.
-- ============================================================

-- Payment request approval amount.
SELECT pr.[request_no],
       s.[name] AS [supplier_name],
       e.[name] AS [requested_by],
       SUM(pri.[request_amount]) AS [request_amount]
FROM [dbo].[payment_requests] AS pr
INNER JOIN [dbo].[suppliers] AS s ON pr.[supplier_id] = s.[id]
LEFT JOIN [dbo].[employees] AS e ON pr.[requested_by] = e.[id]
INNER JOIN [dbo].[payment_request_items] AS pri ON pri.[request_id] = pr.[id]
GROUP BY pr.[request_no], s.[name], e.[name];

-- Warehouse location balance.
SELECT w.[code] AS [warehouse_code],
       wz.[zone_code],
       wl.[location_code],
       p.[sku],
       SUM(ilb.[quantity]) AS [quantity],
       SUM(ilb.[reserved_quantity]) AS [reserved_quantity]
FROM [dbo].[inventory_location_balances] AS ilb
INNER JOIN [dbo].[warehouse_locations] AS wl ON ilb.[location_id] = wl.[id]
INNER JOIN [dbo].[warehouse_zones] AS wz ON wl.[zone_id] = wz.[id]
INNER JOIN [dbo].[warehouses] AS w ON ilb.[warehouse_id] = w.[id]
INNER JOIN [dbo].[products] AS p ON ilb.[product_id] = p.[id]
GROUP BY w.[code], wz.[zone_code], wl.[location_code], p.[sku];

-- Tax invoice verification summary.
SELECT tr.[tax_name],
       ti.[verification_status],
       COUNT(ti.[id]) AS [invoice_count],
       SUM(ti.[tax_amount]) AS [tax_amount]
FROM [dbo].[tax_invoices] AS ti
INNER JOIN [dbo].[tax_rates] AS tr ON ti.[tax_rate_id] = tr.[id]
GROUP BY tr.[tax_name], ti.[verification_status];
