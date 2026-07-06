USE erp_system;

DELIMITER //

CREATE PROCEDURE sp_mysql57_employee_payroll_summary(IN p_salary_month VARCHAR(7))
BEGIN
    INSERT INTO vouchers (voucher_no, voucher_date, voucher_type, reference_type, reference_id, total_debit, total_credit, prepared_by, status)
    SELECT
        CONCAT('PAY-', sp.employee_id, '-', sp.salary_month),
        sp.payment_date,
        'payment',
        'salary_payment',
        sp.id,
        sp.net_pay,
        sp.net_pay,
        sp.employee_id,
        'draft'
    FROM salary_payments sp
    JOIN employees e ON e.id = sp.employee_id
    WHERE sp.salary_month = p_salary_month;
END//

DELIMITER ;
