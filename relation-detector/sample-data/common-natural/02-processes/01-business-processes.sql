-- Portable natural SQL/PSM-style business processes for CLI sample-data statistics.
-- Parser coverage mirror bodies live under sample-data/common-parser-coverage.

CREATE PROCEDURE sp_record_cashier_reconciliation()
BEGIN ATOMIC
  INSERT INTO reconciliation_items (reconciliation_id, journal_id, account_id, system_amount, bank_amount, difference_amount)
  SELECT reconciliations.id, cashier_journals.id, cashier_journals.account_id, cashier_journals.amount, cashier_journals.amount, 0
  FROM reconciliations
  JOIN cashier_journals ON cashier_journals.account_id = reconciliations.account_id;
END;

CREATE PROCEDURE sp_refresh_sales_commission()
BEGIN ATOMIC
  INSERT INTO sales_commissions (employee_id, order_id, order_item_id, base_amount, commission_amount)
  SELECT sales_orders.salesperson_id, sales_orders.id, sales_order_items.id, sales_order_items.amount, sales_order_items.amount * 0.05
  FROM sales_orders
  JOIN sales_order_items ON sales_order_items.order_id = sales_orders.id;
END;

CREATE PROCEDURE sp_post_stocktake_adjustment()
BEGIN ATOMIC
  INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id, quantity_change, reference_id)
  SELECT stocktake_items.product_id, inventory.batch_id, stocktakes.warehouse_id, stocktake_items.counted_qty - stocktake_items.system_qty, stocktakes.id
  FROM stocktake_items
  JOIN stocktakes ON stocktakes.id = stocktake_items.stocktake_id
  JOIN inventory ON inventory.product_id = stocktake_items.product_id;
END;

CREATE PROCEDURE sp_create_ar_invoice()
BEGIN ATOMIC
  INSERT INTO ar_invoices (sales_order_id, customer_id, invoice_amount, paid_amount)
  SELECT sales_orders.id, sales_orders.customer_id, sales_orders.total_amount, sales_orders.paid_amount
  FROM sales_orders
  JOIN customers ON customers.id = sales_orders.customer_id;
END;

CREATE PROCEDURE sp_record_customer_payment()
BEGIN ATOMIC
  INSERT INTO payments (customer_id, sales_order_id, account_id, amount)
  SELECT sales_orders.customer_id, sales_orders.id, cashier_journals.account_id, cashier_journals.amount
  FROM cashier_journals
  JOIN sales_orders ON sales_orders.id = cashier_journals.sales_order_id;
END;
