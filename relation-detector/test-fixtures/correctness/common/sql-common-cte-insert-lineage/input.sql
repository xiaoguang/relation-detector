INSERT INTO customer_region_rollup (customer_id, region_id)
WITH active_customers AS (
  SELECT c.id, c.region_id
  FROM customers c
)
SELECT ac.id, ac.region_id
FROM active_customers ac;
