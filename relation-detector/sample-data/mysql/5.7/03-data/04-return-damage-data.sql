-- ============================================================
-- 退货退款 + 报损数据生成
-- ============================================================

USE erp_system;

-- 采购退货数据
INSERT INTO purchase_returns (return_no, purchase_order_id, purchase_receipt_id, supplier_id,
    warehouse_id, handler_id, return_date, return_reason, return_type, total_amount, refund_received, status, approved_by, approved_at)
SELECT
    CONCAT('PRT-', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL seq.num DAY), '%Y%m%d'), '-', LPAD(seq.num, 3, '0')),
    po.id,
    (SELECT id FROM purchase_receipts WHERE order_id = po.id LIMIT 1),
    po.supplier_id,
    FLOOR(RAND() * 2) + 1,
    FLOOR(RAND() * 5) + 42,
    DATE_SUB(CURDATE(), INTERVAL seq.num DAY),
    ELT(FLOOR(RAND() * 4) + 1, '质量问题-不合格率超标', '临近保质期', '采购过量', '供应商发错货'),
    ELT(FLOOR(RAND() * 4) + 1, 'quality', 'expiry', 'overstock', 'wrong_delivery'),
    ROUND(po.total_amount * 0.1 * (seq.num + 1), 2),
    ROUND(po.total_amount * 0.1 * seq.num * 0.8, 2),
    IF(seq.num < 5, 'refunded', IF(seq.num < 10, 'returned', 'approved')),
    IF(seq.num < 10, 26, NULL),
    IF(seq.num < 10, DATE_SUB(CURDATE(), INTERVAL seq.num - 2 DAY), NULL)
FROM purchase_orders po
CROSS JOIN (SELECT 0 AS num UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
             UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9
             UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14) seq
WHERE po.status = 'received'
LIMIT 15;

-- 采购退货明细
INSERT INTO purchase_return_items (return_id, product_id, batch_id, return_qty, unit_price)
SELECT
    pr.id,
    poi.product_id,
    first_batch.batch_id,
    FLOOR(5 + RAND() * 20),
    poi.unit_price
FROM purchase_returns pr
JOIN (
    SELECT order_id, MIN(id) AS order_item_id
    FROM purchase_order_items
    GROUP BY order_id
) first_item ON first_item.order_id = pr.purchase_order_id
JOIN purchase_order_items poi ON poi.id = first_item.order_item_id
LEFT JOIN (
    SELECT product_id, MIN(id) AS batch_id
    FROM product_batches
    GROUP BY product_id
) first_batch ON first_batch.product_id = poi.product_id;

-- 报损数据
INSERT INTO damage_reports (report_no, warehouse_id, report_type, report_date,
    reported_by, total_quantity, total_loss_amount, status, approved_by, approved_at, executed_by, executed_at)
SELECT
    CONCAT('DMG-', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL seq.num DAY), '%Y%m%d'), '-', LPAD(seq.num, 3, '0')),
    FLOOR(RAND() * 2) + 1,
    ELT(FLOOR(RAND() * 4) + 1, 'damage', 'expired', 'obsolescence', 'other'),
    DATE_SUB(CURDATE(), INTERVAL seq.num DAY),
    FLOOR(RAND() * 10) + 45,
    FLOOR(10 + RAND() * 50),
    ROUND(500 + RAND() * 5000, 2),
    IF(seq.num < 5, 'executed', IF(seq.num < 10, 'approved', 'pending')),
    IF(seq.num < 10, FLOOR(RAND() * 5) + 42, NULL),
    IF(seq.num < 10, DATE_SUB(CURDATE(), INTERVAL seq.num - 1 DAY), NULL),
    IF(seq.num < 5, FLOOR(RAND() * 10) + 45, NULL),
    IF(seq.num < 5, DATE_SUB(CURDATE(), INTERVAL seq.num - 2 DAY), NULL)
FROM (SELECT 0 AS num UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
      UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9
      UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14) seq;

-- 报损明细
INSERT INTO damage_report_items (report_id, product_id, batch_id, quantity, unit_cost)
SELECT
    dr.id,
    FLOOR(RAND() * 50) + 1,
    (SELECT id FROM product_batches WHERE product_id = FLOOR(RAND() * 50) + 1 LIMIT 1),
    FLOOR(5 + RAND() * 30),
    (SELECT purchase_price FROM products WHERE id = FLOOR(RAND() * 50) + 1)
FROM damage_reports dr;
