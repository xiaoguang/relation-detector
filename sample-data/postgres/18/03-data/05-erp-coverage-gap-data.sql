-- ============================================================
-- ERP coverage gap seed data - PostgreSQL 16/17/18 compatible
-- Purpose: align PostgreSQL seed coverage with the MySQL 8.0 ERP sample
--          for return/damage, logistics, depreciation, review and pricing.
-- ============================================================

INSERT INTO shipments (
    id, shipment_no, order_id, warehouse_id, carrier, tracking_no,
    shipping_method, shipping_fee, package_count, weight_kg, status,
    picker_id, packer_id, shipped_at, delivered_at,
    estimated_delivery_date, actual_delivery_date,
    from_address, to_address, receiver_name, receiver_phone
) VALUES
(9001, 'SH-SAMPLE-9001', 9001, 1, '顺丰速运', 'TN-SAMPLE-9001',
 'express', 35.00, 2, 8.500, 'delivered',
 43, 44, '2026-02-13 09:20:00', '2026-02-15 15:10:00',
 '2026-02-15', '2026-02-15',
 '上海市松江区物流园A区', '上海市闵行区申虹路88号', '上海仓收货组', '021-60000002');

INSERT INTO shipping_tracks (id, shipment_id, track_time, location, status_desc, operator) VALUES
(9001, 9001, '2026-02-13 09:20:00', '上海分拣中心', '已揽收', '系统'),
(9002, 9001, '2026-02-14 08:30:00', '上海转运中心', '运输中', '系统'),
(9003, 9001, '2026-02-15 15:10:00', '上海配送站', '已签收', '配送员');

INSERT INTO purchase_returns (
    id, return_no, purchase_order_id, purchase_receipt_id, supplier_id,
    warehouse_id, handler_id, return_date, return_reason, return_type,
    total_amount, refund_received, shipping_fee, status, approved_by, approved_at
) VALUES
(9001, 'PRT-SAMPLE-9001', 9001, 9001, 1,
 1, 42, '2026-02-16', '来料抽检发现外观缺陷', 'quality',
 3600.00, 2880.00, 80.00, 'refunded', 26, '2026-02-17 11:00:00');

INSERT INTO purchase_return_items (id, return_id, product_id, batch_id, return_qty, unit_price, reason) VALUES
(9001, 9001, 1, 1, 4, 900.00, '外观划伤');

INSERT INTO damage_reports (
    id, report_no, warehouse_id, report_type, report_date, reported_by,
    total_quantity, total_loss_amount, status, approved_by, approved_at,
    executed_by, executed_at, description
) VALUES
(9001, 'DMG-SAMPLE-9001', 1, 'damage', '2026-02-18', 45,
 3, 1350.00, 'executed', 42, '2026-02-18 14:00:00',
 45, '2026-02-18 16:00:00', '盘点发现运输破损');

INSERT INTO damage_report_items (id, report_id, product_id, batch_id, quantity, unit_cost, reason) VALUES
(9001, 9001, 1, 1, 3, 450.00, '运输破损');

INSERT INTO depreciation_log (
    id, asset_id, depreciation_date, depreciation_amount,
    before_accumulated, after_accumulated, before_net_value, after_net_value
) VALUES
(9001, 1, '2026-02-28', 1277.78, 1277.78, 2555.56, 46722.22, 45444.44);

INSERT INTO performance_reviews (
    id, review_no, employee_id, reviewer_id, review_period, review_type,
    performance_score, competency_score, attitude_score, attendance_score,
    self_assessment, reviewer_comment, improvement_plan,
    salary_adjustment, promotion_recommendation, status
) VALUES
(9001, 'PR-SAMPLE-9001', 9, 5, '2026-02', 'monthly',
 88.00, 86.00, 90.00, 95.00,
 '完成月度销售目标并协同处理重点客户问题',
 '业绩稳定，需继续提升跨部门协同',
 '下月负责华东重点客户复购跟进',
 500.00, FALSE, 'reviewed');

INSERT INTO price_change_logs (
    id, product_id, price_type, old_price, new_price,
    change_reason, effective_date, changed_by, approved_by
) VALUES
(9001, 1, 'retail', 899.00, 929.00, '原材料价格上涨后调整零售价', '2026-03-01', 36, 26);
