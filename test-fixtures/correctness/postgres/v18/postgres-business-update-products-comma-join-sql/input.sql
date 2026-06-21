-- PostgreSQL business case 1 equivalent: INNER JOIN rewritten as comma rowsets plus WHERE equality.
UPDATE products p
SET is_on_sale = 1,
    promo_price = p.original_price * 0.9
FROM shops s, merchants m
WHERE p.shop_id = s.id
  AND s.merchant_id = m.id
  AND m.status = 'ACTIVE'
  AND s.rating >= 4.5;
