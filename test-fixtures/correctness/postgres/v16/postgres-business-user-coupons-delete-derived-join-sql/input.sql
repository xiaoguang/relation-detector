-- PostgreSQL business case 8: DELETE USING with nested aggregate derived table and outer INNER JOIN.
DELETE FROM user_coupons uc
USING (
    SELECT c_sub.id AS coupon_id, c_sub.merchant_id
    FROM coupons c_sub
    LEFT JOIN (
        SELECT coupon_id, COUNT(id) AS usage_cnt
        FROM coupon_redemptions
        GROUP BY coupon_id
        HAVING COUNT(id) < 5
    ) red_summary ON c_sub.id = red_summary.coupon_id
    WHERE c_sub.expire_at < NOW()
       OR (red_summary.usage_cnt IS NULL AND c_sub.created_at < NOW() - INTERVAL '90 days')
) target_coupons
INNER JOIN merchants m ON target_coupons.merchant_id = m.id
WHERE uc.coupon_id = target_coupons.coupon_id
  AND m.compliance_status = 'SUSPENDED';
