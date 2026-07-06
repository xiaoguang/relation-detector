-- Portable seed data targets, expressed as INSERT ... SELECT for common parser compatibility.

INSERT INTO departments (id)
SELECT 1;

INSERT INTO positions (id)
SELECT 2;

INSERT INTO employees (id)
SELECT 3;

INSERT INTO employee_salary_log (id)
SELECT 4;

INSERT INTO attendance (id)
SELECT 5;

INSERT INTO leave_records (id)
SELECT 6;

INSERT INTO roles (id)
SELECT 7;

INSERT INTO permissions (id)
SELECT 8;

INSERT INTO role_permissions (id)
SELECT 9;

INSERT INTO employee_roles (id)
SELECT 10;

INSERT INTO audit_log (id)
SELECT 11;

INSERT INTO product_categories (id)
SELECT 12;

INSERT INTO suppliers (id)
SELECT 13;

INSERT INTO products (id)
SELECT 14;

INSERT INTO supplier_products (id)
SELECT 15;

INSERT INTO product_batches (id)
SELECT 16;

INSERT INTO warehouses (id)
SELECT 17;

INSERT INTO inventory (id)
SELECT 18;

INSERT INTO inventory_transactions (id)
SELECT 19;

INSERT INTO purchase_requisitions (id)
SELECT 20;

INSERT INTO purchase_requisition_items (id)
SELECT 21;

INSERT INTO purchase_orders (id)
SELECT 22;

INSERT INTO purchase_order_items (id)
SELECT 23;

INSERT INTO purchase_receipts (id)
SELECT 24;

INSERT INTO purchase_receipt_items (id)
SELECT 25;

INSERT INTO customers (id)
SELECT 26;

INSERT INTO sales_orders (id)
SELECT 27;

INSERT INTO sales_order_items (id)
SELECT 28;

INSERT INTO sales_returns (id)
SELECT 29;

INSERT INTO sales_return_items (id)
SELECT 30;

INSERT INTO purchase_returns (id)
SELECT 31;

INSERT INTO purchase_return_items (id)
SELECT 32;

INSERT INTO damage_reports (id)
SELECT 33;

INSERT INTO damage_report_items (id)
SELECT 34;

INSERT INTO accounts (id)
SELECT 35;

INSERT INTO vouchers (id)
SELECT 36;

INSERT INTO voucher_items (id)
SELECT 37;

INSERT INTO cashier_journals (id)
SELECT 38;

INSERT INTO salary_payments (id)
SELECT 39;

INSERT INTO reconciliations (id)
SELECT 40;

INSERT INTO reconciliation_items (id)
SELECT 41;

INSERT INTO settlements (id)
SELECT 42;

INSERT INTO settlement_items (id)
SELECT 43;

INSERT INTO shipments (id)
SELECT 44;

INSERT INTO shipping_tracks (id)
SELECT 45;

INSERT INTO commission_rules (id)
SELECT 46;

INSERT INTO sales_commissions (id)
SELECT 47;

INSERT INTO promotions (id)
SELECT 48;

INSERT INTO promotion_products (id)
SELECT 49;

INSERT INTO promotion_usages (id)
SELECT 50;

INSERT INTO invoices (id)
SELECT 51;

INSERT INTO three_way_matching (id)
SELECT 52;

INSERT INTO fixed_assets (id)
SELECT 53;

INSERT INTO depreciation_log (id)
SELECT 54;

INSERT INTO boms (id)
SELECT 55;

INSERT INTO work_orders (id)
SELECT 56;

INSERT INTO work_order_materials (id)
SELECT 57;

INSERT INTO service_tickets (id)
SELECT 58;

INSERT INTO contracts (id)
SELECT 59;

INSERT INTO contract_milestones (id)
SELECT 60;

INSERT INTO ar_aging_snapshots (id)
SELECT 61;

INSERT INTO ap_aging_snapshots (id)
SELECT 62;

INSERT INTO tax_invoices (id)
SELECT 63;

INSERT INTO tax_filings (id)
SELECT 64;

INSERT INTO inspection_standards (id)
SELECT 65;

INSERT INTO inspection_reports (id)
SELECT 66;

INSERT INTO approval_workflows (id)
SELECT 67;

INSERT INTO approval_nodes (id)
SELECT 68;

INSERT INTO approval_instances (id)
SELECT 69;

INSERT INTO approval_records (id)
SELECT 70;

INSERT INTO cash_flow_forecasts (id)
SELECT 71;

INSERT INTO projects (id)
SELECT 72;

INSERT INTO project_costs (id)
SELECT 73;

INSERT INTO exchange_rates (id)
SELECT 74;

INSERT INTO foreign_currency_accounts (id)
SELECT 75;

INSERT INTO performance_reviews (id)
SELECT 76;

INSERT INTO kpi_indicators (id)
SELECT 77;

INSERT INTO serial_numbers (id)
SELECT 78;

INSERT INTO serial_number_logs (id)
SELECT 79;

INSERT INTO consignment_inventory (id)
SELECT 80;

INSERT INTO consignment_consumptions (id)
SELECT 81;

INSERT INTO price_change_logs (id)
SELECT 82;

INSERT INTO tenants (id)
SELECT 83;

INSERT INTO ledger_books (id)
SELECT 84;

INSERT INTO customer_addresses (id)
SELECT 85;

INSERT INTO supplier_addresses (id)
SELECT 86;

INSERT INTO tax_rates (id)
SELECT 87;

INSERT INTO accounting_periods (id)
SELECT 88;

INSERT INTO period_close_jobs (id)
SELECT 89;

INSERT INTO payment_receipts (id)
SELECT 90;

INSERT INTO payment_receipt_allocations (id)
SELECT 91;

INSERT INTO stocktakes (id)
SELECT 92;

INSERT INTO stocktake_items (id)
SELECT 93;

INSERT INTO stock_transfers (id)
SELECT 94;

INSERT INTO stock_transfer_items (id)
SELECT 95;

INSERT INTO inventory_reservations (id)
SELECT 96;

INSERT INTO production_routes (id)
SELECT 97;

INSERT INTO production_operations (id)
SELECT 98;

INSERT INTO employee_shifts (id)
SELECT 99;

INSERT INTO employee_shift_assignments (id)
SELECT 100;
