-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/functional_index.test
-- - MySQL 8.0 Reference Manual: CREATE INDEX Statement
-- FULLTEXT, SPATIAL, and JSON expression indexes are useful DDL parser
-- coverage, but none of them is a relationship on its own.

CREATE TABLE geo_assets (
  asset_id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  name VARCHAR(255),
  tags JSON,
  footprint GEOMETRY NOT NULL SRID 4326,
  description TEXT,
  PRIMARY KEY (asset_id),
  FULLTEXT KEY description_fulltext_idx (name, description),
  SPATIAL KEY footprint_spatial_idx (footprint),
  KEY tenant_tag_mv_idx (tenant_id, (CAST(tags->'$.tagIds' AS UNSIGNED ARRAY)))
) ENGINE=InnoDB;

CREATE INDEX tag_name_expr_idx
  ON geo_assets ((CAST(tags->>"$.primaryName" AS CHAR(64))));
