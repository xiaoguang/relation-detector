-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/invisible_indexes.test
-- - MySQL 8.0 Reference Manual: CREATE INDEX Statement
-- ALTER INDEX operations should be parsed without creating relationships.

CREATE TABLE order_search_tokens (
  token_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  token_value VARCHAR(128) NOT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (token_id)
) ENGINE=InnoDB;

ALTER TABLE order_search_tokens
  ADD INDEX order_token_idx (order_id, token_value),
  ADD UNIQUE KEY token_unique_idx (token_value),
  ADD INDEX created_desc_idx (created_at DESC) INVISIBLE;

ALTER TABLE order_search_tokens RENAME INDEX order_token_idx TO order_token_lookup_idx;
ALTER TABLE order_search_tokens ALTER INDEX created_desc_idx VISIBLE;
ALTER TABLE order_search_tokens ALTER INDEX token_unique_idx INVISIBLE;
