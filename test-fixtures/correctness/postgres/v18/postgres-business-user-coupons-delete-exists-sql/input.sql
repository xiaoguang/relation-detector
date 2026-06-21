-- PostgreSQL business case 8 equivalent: correlated EXISTS/NOT EXISTS version of the nested coupon cleanup.
DELETE FROM user_coupons uc
USING coupons c
INNER JOIN merchants m ON c.merchant_id = m.id
WHERE uc.coupon_id = c.id
  AND m.compliance_status = 'SUSPENDED'
  AND (
      c.expire_at < NOW()
      OR (
          c.created_at < NOW() - INTERVAL '90 days'
          AND NOT EXISTS (
              SELECT 1
              FROM coupon_redemptions cr
              WHERE cr.coupon_id = c.id
              GROUP BY cr.coupon_id
              HAVING COUNT(cr.id) >= 5
          )
      )
  );
