SELECT *
FROM "public"."orders" o
JOIN users ON o."user_id" = users.id
JOIN "payments" ON "payments".order_id = o.id;
