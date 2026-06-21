-- PostgreSQL official CREATE INDEX inspired: expression indexes, function-call
-- expressions, opclass parameters, collation, sort order, and NULLS options.
CREATE TABLE customers (
  id BIGINT PRIMARY KEY,
  external_ref TEXT UNIQUE,
  email TEXT,
  metadata JSONB
);

CREATE TABLE invoices (
  id BIGINT PRIMARY KEY,
  customer_ref TEXT REFERENCES customers(external_ref),
  customer_id BIGINT
);

CREATE INDEX customers_lower_email_idx
  ON customers ((lower(email)) COLLATE "C" text_pattern_ops ASC NULLS LAST);

CREATE INDEX customers_metadata_expr_idx
  ON customers USING gin ((metadata -> 'tags'));

CREATE INDEX customers_external_ref_ops_idx
  ON customers USING btree (external_ref text_ops (deduplicate_items = off) DESC NULLS FIRST);
