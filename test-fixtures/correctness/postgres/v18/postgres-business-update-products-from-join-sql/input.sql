-- PostgreSQL business case 1: UPDATE FROM with an explicit INNER JOIN.
UPDATE products p
SET is_on_sale = 1,
    promo_price = p.original_price * 0.9
FROM shops s
INNER JOIN merchants m ON s.merchant_id = m.id
WHERE p.shop_id = s.id
  AND m.status = 'ACTIVE'
  AND s.rating >= 4.5;
