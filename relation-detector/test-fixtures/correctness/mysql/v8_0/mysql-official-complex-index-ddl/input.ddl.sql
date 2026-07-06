-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/functional_index.test
-- - mysql/mysql-server mysql-test/t/invisible_indexes.test
-- - MySQL 8.0 Reference Manual: CREATE INDEX Statement

CREATE TABLE customer_identity (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  email VARCHAR(255) NOT NULL,
  normalized_email VARCHAR(255) GENERATED ALWAYS AS (LOWER(email)) STORED,
  external_ref VARCHAR(128),
  PRIMARY KEY (id),
  UNIQUE KEY tenant_email_unique (tenant_id, normalized_email),
  KEY external_ref_prefix_idx (external_ref(24)),
  KEY generated_email_idx (normalized_email),
  KEY inline_expr_idx ((LOWER(email)))
);

CREATE UNIQUE INDEX tenant_external_unique
  ON customer_identity (tenant_id, external_ref(32));
