-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/create_index.test
-- - MySQL 8.0 Reference Manual: CREATE INDEX Statement
-- These index options are parser coverage only; they must not create FK-like
-- relationships by themselves.

CREATE TABLE search_documents (
  doc_id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  body TEXT,
  ranking_score DECIMAL(12,4),
  PRIMARY KEY (doc_id),
  KEY tenant_score_desc_idx USING BTREE (tenant_id, ranking_score DESC) KEY_BLOCK_SIZE = 8 COMMENT 'tenant score order',
  FULLTEXT KEY body_fulltext_idx (title, body) WITH PARSER ngram COMMENT 'full text parser'
) ENGINE=InnoDB;

CREATE INDEX title_hash_idx USING HASH
  ON search_documents (title)
  COMMENT 'hash option coverage'
  ALGORITHM DEFAULT
  LOCK DEFAULT;
