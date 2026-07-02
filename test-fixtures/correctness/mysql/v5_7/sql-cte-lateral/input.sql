SELECT ro.order_id, u.email
FROM (
  SELECT o.id AS order_id, o.user_id
  FROM `orders` AS o
  WHERE o.created_at >= CURRENT_DATE - INTERVAL 7 DAY
) AS ro
JOIN `users` AS u ON ro.user_id = u.id;
