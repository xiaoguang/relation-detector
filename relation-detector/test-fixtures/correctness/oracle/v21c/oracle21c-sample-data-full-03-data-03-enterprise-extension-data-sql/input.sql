-- ============================================================
-- ERP企业级扩展测试数据 - Oracle 21c
-- 覆盖: 多租户/账套、地址、税率、会计期间、收付款、
--       库存盘点/调拨/预留、工艺路线/工序、班次排班
-- ============================================================

INSERT INTO tenants (id, tenant_code, tenant_name, legal_entity_name, tax_no, status) VALUES
(1, 'T001', '华东运营主体', '上海华东智造商贸有限公司', '91310000MA1ERP001X', 'active'),
(2, 'T002', '华南运营主体', '广州华南智造商贸有限公司', '91440000MA1ERP002Y', 'active');

INSERT INTO ledger_books (id, tenant_id, book_code, book_name, base_currency, fiscal_year_start_month, is_default, status) VALUES
(1, 1, 'BOOK-CNY-2026', '华东人民币主账套', 'CNY', 1, 1, 'active'),
(2, 2, 'BOOK-CNY-2026', '华南人民币主账套', 'CNY', 1, 1, 'active');

INSERT INTO customer_addresses (customer_id, address_type, receiver_name, receiver_phone, province, city, district, street, postal_code, is_default) VALUES
(1, 'registered', '采购中心', '021-60000001', '上海市', '上海市', '浦东新区', '世纪大道100号', '200120', 1),
(1, 'shipping', '上海仓收货组', '021-60000002', '上海市', '上海市', '闵行区', '申虹路88号', '201100', 0),
(2, 'shipping', '杭州门店', '0571-60000003', '浙江省', '杭州市', '西湖区', '文三路66号', '310012', 1);

INSERT INTO supplier_addresses (supplier_id, address_type, contact_name, contact_phone, province, city, district, street, postal_code, is_default) VALUES
(1, 'registered', '供应商财务', '0755-70000001', '广东省', '深圳市', '南山区', '科技园路1号', '518000', 1),
(1, 'warehouse', '供应商发货组', '0755-70000002', '广东省', '深圳市', '宝安区', '物流园9号', '518100', 0),
(2, 'warehouse', '华东发货组', '0512-70000003', '江苏省', '苏州市', '工业园区', '星湖街12号', '215000', 1);

INSERT INTO tax_rates (id, tax_code, tax_name, tax_type, rate, effective_from, effective_to, status) VALUES
(1, 'VAT13', '增值税13%', 'vat', 0.1300, '2020-01-01', NULL, 'active'),
(2, 'VAT06', '增值税6%', 'vat', 0.0600, '2020-01-01', NULL, 'active'),
(3, 'SURTAX12', '附加税12%', 'surtax', 0.1200, '2020-01-01', NULL, 'active');

INSERT INTO accounting_periods (id, ledger_book_id, period_code, period_start, period_end, status, closed_by, closed_at) VALUES
(1, 1, '2026-01', '2026-01-01', '2026-01-31', 'closed', 34, '2026-02-02 18:00:00'),
(2, 1, '2026-02', '2026-02-01', '2026-02-28', 'open', NULL, NULL),
(3, 2, '2026-01', '2026-01-01', '2026-01-31', 'closed', 34, '2026-02-03 18:00:00');

INSERT INTO period_close_jobs (period_id, job_code, job_name, status, started_at, finished_at, message) VALUES
(1, 'INV_REVALUE', '库存成本重算', 'success', '2026-02-02 09:00:00', '2026-02-02 09:20:00', '已按移动平均价重算库存成本'),
(1, 'AR_AP_AGING', '应收应付账龄快照', 'success', '2026-02-02 09:20:00', '2026-02-02 09:35:00', '已生成账龄快照'),
(2, 'INV_REVALUE', '库存成本重算', 'pending', NULL, NULL, NULL);

INSERT INTO payment_receipts (id, receipt_no, receipt_type, party_type, party_id, account_id, receipt_date, amount, currency, status, handled_by, confirmed_at, remark) VALUES
(1, 'PR-202601-0001', 'customer_receipt', 'customer', 1, 1, '2026-01-15', 128000.00, 'CNY', 'confirmed', 39, '2026-01-15 16:00:00', '客户回款'),
(2, 'PR-202601-0002', 'supplier_payment', 'supplier', 1, 2, '2026-01-20', 56000.00, 'CNY', 'confirmed', 40, '2026-01-20 15:20:00', '供应商付款');

INSERT INTO payment_receipt_allocations (receipt_id, reference_type, reference_id, allocated_amount) VALUES
(1, 'sales_order', 1, 128000.00),
(2, 'purchase_order', 1, 56000.00);

INSERT INTO stocktakes (id, stocktake_no, warehouse_id, stocktake_date, stocktake_type, status, created_by, reviewed_by, posted_at) VALUES
(1, 'STK-202601-0001', 1, '2026-01-31', 'full', 'posted', 43, 42, '2026-01-31 20:00:00'),
(2, 'STK-202602-0001', 2, '2026-02-15', 'cycle', 'counting', 44, NULL, NULL);

INSERT INTO stocktake_items (stocktake_id, product_id, batch_id, book_quantity, counted_quantity, variance_reason) VALUES
(1, 1, 1, 120, 118, '盘点短少2件'),
(1, 2, 2, 80, 83, '库位移入未及时登记'),
(2, 1, 1, 45, 45, NULL);

INSERT INTO stock_transfers (id, transfer_no, from_warehouse_id, to_warehouse_id, requested_by, approved_by, transfer_date, status) VALUES
(1, 'TR-202602-0001', 1, 2, 43, 42, '2026-02-10', 'received'),
(2, 'TR-202602-0002', 2, 1, 44, NULL, '2026-02-18', 'draft');

INSERT INTO stock_transfer_items (transfer_id, product_id, batch_id, quantity, received_quantity) VALUES
(1, 1, 1, 20, 20),
(1, 2, 2, 15, 15),
(2, 1, 1, 10, 0);

INSERT INTO inventory_reservations (reservation_no, product_id, batch_id, warehouse_id, source_type, source_id, reserved_quantity, released_quantity, status, expires_at) VALUES
('RSV-202602-0001', 1, 1, 1, 'sales_order', 1, 12, 0, 'reserved', '2026-02-20 23:59:59'),
('RSV-202602-0002', 2, 2, 2, 'work_order', 1, 8, 2, 'partially_released', '2026-02-22 23:59:59');

INSERT INTO production_routes (id, product_id, route_code, route_name, version_no, status) VALUES
(1, 1, 'R-ASM-001', '标准组装路线', 'V1.0', 'active'),
(2, 2, 'R-PACK-001', '包装检验路线', 'V1.0', 'active');

INSERT INTO production_operations (id, route_id, operation_no, operation_name, work_center, standard_minutes, predecessor_operation_id) VALUES
(1, 1, 10, '备料', '原料仓', 15.00, NULL),
(2, 1, 20, '组装', '装配线A', 45.00, 1),
(3, 1, 30, '质检', '质检台', 20.00, 2),
(4, 2, 10, '包装', '包装线B', 12.00, NULL);

INSERT INTO employee_shifts (id, shift_code, shift_name, start_time, end_time, planned_hours, is_night_shift) VALUES
(1, 'D', '白班', '08:30:00', '17:30:00', 8.00, 0),
(2, 'N', '夜班', '20:30:00', '05:30:00', 8.00, 1);

INSERT INTO employee_shift_assignments (employee_id, shift_id, work_date, warehouse_id, status) VALUES
(43, 1, '2026-02-10', 1, 'confirmed'),
(44, 1, '2026-02-10', 2, 'confirmed'),
(45, 2, '2026-02-10', 1, 'planned');

-- ============================================================
-- 覆盖补齐: 让所有持久业务表都有代表性样例数据
-- ============================================================

INSERT INTO leave_records (id, employee_id, leave_type, start_date, end_date, days, reason, status, approved_by, approved_at) VALUES
(9001, 9, 'annual', '2026-02-03', '2026-02-05', 3.0, '春节后调休', 'approved', 5, '2026-01-28 10:30:00'),
(9002, 30, 'sick', '2026-02-12', '2026-02-12', 1.0, '门诊检查', 'pending', NULL, NULL);

INSERT INTO sales_orders (id, order_no, customer_id, salesperson_id, warehouse_id, order_date, delivery_date,
    total_amount, discount_amount, paid_amount, tax_amount, payment_method, status, invoice_no, remark)
VALUES
(9001, 'SO-SAMPLE-9001', 1, 5, 1, '2026-02-12', '2026-02-15',
 17980.00, 500.00, 12000.00, 2068.00, 'transfer', 'confirmed', 'INV-SAMPLE-9001', '促销使用覆盖样例销售单');

INSERT INTO sales_order_items (id, order_id, product_id, batch_id, quantity, unit_price, discount, amount, returned_qty) VALUES
(9001, 9001, 1, NULL, 20, 899.00, 500.00, 17480.00, 0);

INSERT INTO promotion_usages (id, promotion_id, order_id, customer_id, discount_applied, used_at) VALUES
(9001, 1, 9001, 1, 500.00, '2026-02-12 14:20:00');

INSERT INTO foreign_currency_accounts (id, account_id, currency, original_balance, cny_equivalent, last_revaluation_date) VALUES
(9001, 3, 'USD', 25000.00, 179500.00, '2026-01-31'),
(9002, 4, 'EUR', 12000.00, 93840.00, '2026-01-31');

INSERT INTO consignment_inventory (id, product_id, batch_id, customer_id, consigned_qty, consumed_qty, unit_price,
    consigned_date, last_consumed_date, settlement_period, status)
VALUES
(9001, 1, NULL, 1, 60, 18, 899.00, '2026-01-18', '2026-02-10', '2026-02', 'active');

INSERT INTO consignment_consumptions (id, consignment_id, consumed_qty, consumed_date, unit_price,
    confirmed_by_customer, sales_order_id, remark)
VALUES
(9001, 9001, 8, '2026-02-10', 899.00, 1, 9001, '客户确认寄售消耗并转销售结算');
