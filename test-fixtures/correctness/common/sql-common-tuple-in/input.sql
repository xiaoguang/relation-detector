SELECT o.id
FROM orders o
WHERE (o.region_id, o.customer_id) IN (
  SELECT cr.region_id, cr.customer_id
  FROM customer_regions cr
);
