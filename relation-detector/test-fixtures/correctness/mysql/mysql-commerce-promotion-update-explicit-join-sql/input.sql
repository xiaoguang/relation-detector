UPDATE products p
INNER JOIN shops s ON p.shop_id = s.id
INNER JOIN merchants m ON s.merchant_id = m.id
INNER JOIN discount_policies dp ON p.category_id = dp.category_id
SET
    p.promo_price = p.original_price * 0.90,
    p.is_on_sale = 1,
    p.updated_at = NOW()
WHERE
    m.status = 'ACTIVE'
    AND s.rating >= 4.5
    AND dp.policy_type = 'SUMMER_PROMO'
    AND p.stock_quantity > 100;
