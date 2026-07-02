-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/functional_index.test
-- - MySQL 5.7 Reference Manual: CREATE INDEX Statement

CREATE TABLE metric_events (
  id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  payload JSON,
  metric_name VARCHAR(255),
  metric_value DECIMAL(18,4),
  PRIMARY KEY (id),
  KEY metric_abs_idx ((ABS(metric_value))),
  KEY metric_expr_mix_idx ((account_id + id), metric_name),
  KEY payload_account_idx ((CAST(payload->>"$.accountId" AS UNSIGNED)))
);

CREATE INDEX metric_name_prefix_idx ON metric_events (metric_name(32));
CREATE INDEX metric_json_name_idx ON metric_events ((CAST(payload->>"$.name" AS CHAR(30))));
