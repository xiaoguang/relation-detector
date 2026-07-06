UPDATE products p, shops s, merchants m, discount_policies dp
SET
    p.promo_price = p.original_price * 0.90,
    p.is_on_sale = 1,
    p.updated_at = NOW()
WHERE
    p.shop_id = s.id
    AND s.merchant_id = m.id
    AND p.category_id = dp.category_id
    AND m.status = 'ACTIVE'
    AND s.rating >= 4.5
    AND dp.policy_type = 'SUMMER_PROMO'
    AND p.stock_quantity > 100;
