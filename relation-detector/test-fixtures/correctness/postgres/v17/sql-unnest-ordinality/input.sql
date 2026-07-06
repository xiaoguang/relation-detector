SELECT u.id
FROM users u
JOIN orders o ON o.user_id = u.id
JOIN unnest(ARRAY[1, 2, 3]) WITH ORDINALITY AS input_ids(user_id, ord)
  ON input_ids.user_id = u.id;
