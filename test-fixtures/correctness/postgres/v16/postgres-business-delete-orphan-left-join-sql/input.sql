-- PostgreSQL business case 4: orphan cleanup through DELETE USING and LEFT JOIN.
DELETE FROM product_reviews pr
USING product_reviews pr_alias
LEFT JOIN products p ON pr_alias.product_id = p.id
WHERE pr.id = pr_alias.id
  AND p.id IS NULL;
