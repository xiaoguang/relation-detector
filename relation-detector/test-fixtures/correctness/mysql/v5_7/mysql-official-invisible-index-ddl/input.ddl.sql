-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/invisible_indexes.test
-- - MySQL 5.7 Reference Manual: CREATE INDEX Statement

CREATE TABLE inventory_snapshots (
  snapshot_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  sku VARCHAR(64) NOT NULL,
  captured_at DATETIME NOT NULL,
  PRIMARY KEY (snapshot_id),
  KEY warehouse_idx (warehouse_id) INVISIBLE,
  KEY sku_visible_idx (sku) VISIBLE
) PARTITION BY KEY (warehouse_id) PARTITIONS 8;

ALTER TABLE inventory_snapshots ALTER INDEX warehouse_idx VISIBLE;
ALTER TABLE inventory_snapshots ALTER INDEX sku_visible_idx INVISIBLE;
