WITH "a" AS (
  SELECT o.id AS order_id, o.customer_id
  FROM "public"."orders" o
  JOIN "public"."customers" c ON o.customer_id = c.id
),
b AS (
  SELECT a.order_id, c.region_id
  FROM "a" a
  JOIN "public"."customers" c ON a.customer_id = c.id
),
c AS (
  SELECT b.order_id, b.region_id
  FROM b
)
SELECT *
FROM c
JOIN "public"."regions" r ON c.region_id = r.id
JOIN invoices i ON i.order_id = c.order_id;
