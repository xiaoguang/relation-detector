WITH recent_orders AS (
  SELECT o.id AS order_id, o.user_id
  FROM `orders` AS o
  WHERE o.created_at >= CURRENT_DATE - INTERVAL 7 DAY
)
SELECT ro.order_id, u.email
FROM recent_orders ro
JOIN LATERAL (
  SELECT ro.user_id AS buyer_id
) AS buyer_projection ON true
JOIN `users` AS u ON buyer_projection.buyer_id = u.id;
