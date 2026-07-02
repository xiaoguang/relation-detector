USE erp_system;

DELIMITER //

CREATE PROCEDURE sp_mysql57_return_refund_summary(IN p_start_date DATE, IN p_end_date DATE)
BEGIN
    INSERT INTO vouchers (voucher_no, voucher_date, voucher_type, reference_type, reference_id, total_debit, total_credit, prepared_by, status)
    SELECT
        CONCAT('SR-', sr.id),
        sr.return_date,
        'payment',
        'sales_return',
        sr.id,
        SUM(sri.amount),
        SUM(sri.amount),
        sr.created_by,
        'draft'
    FROM sales_returns sr
    JOIN sales_return_items sri ON sri.return_id = sr.id
    WHERE sr.return_date BETWEEN p_start_date AND p_end_date
    GROUP BY sr.id, sr.return_date, sr.created_by;
END//

DELIMITER ;
