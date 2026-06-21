-- PostgreSQL official CREATE INDEX inspired: ONLY and partition-oriented index
-- syntax. ALTER INDEX ATTACH PARTITION is a parser-stability boundary and must
-- not create relationships.
CREATE TABLE tenants (
  id BIGINT PRIMARY KEY
);

CREATE TABLE tenant_orders (
  tenant_id BIGINT REFERENCES tenants(id),
  id BIGINT,
  created_at TIMESTAMPTZ,
  PRIMARY KEY (tenant_id, id)
);

CREATE UNIQUE INDEX tenant_orders_parent_idx
  ON ONLY tenant_orders USING btree
  (tenant_id ASC NULLS LAST, id ASC NULLS LAST)
  NULLS NOT DISTINCT;

CREATE INDEX tenant_orders_created_idx
  ON tenant_orders USING btree (created_at DESC NULLS FIRST);

ALTER INDEX tenant_orders_parent_idx
  ATTACH PARTITION tenant_orders_2025_idx;
