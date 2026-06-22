UPDATE orders o, users u
JOIN accounts a ON u.account_id = a.id
SET o.reviewed_at = CURRENT_TIMESTAMP
WHERE o.user_id = u.id
  AND o.status = 'PAID'
  AND a.closed_at IS NULL;
