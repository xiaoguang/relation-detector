-- PostgreSQL official create_index.sql/docs inspired: storage parameters,
-- TABLESPACE, and access-method-specific options. Complex indexes should not
-- create FK-like relations by themselves; the declared FK below is the only
-- expected relationship.
CREATE TABLE public.users (
  id BIGINT PRIMARY KEY,
  email TEXT
);

CREATE TABLE public.orders (
  id BIGINT PRIMARY KEY,
  user_id BIGINT REFERENCES public.users(id),
  status TEXT,
  created_at TIMESTAMPTZ,
  payload JSONB
);

CREATE INDEX CONCURRENTLY IF NOT EXISTS orders_status_created_idx
  ON ONLY public.orders USING btree
  (status ASC NULLS LAST, created_at DESC NULLS FIRST)
  WITH (fillfactor = 70, deduplicate_items = off)
  TABLESPACE fastspace;

CREATE INDEX orders_payload_gin_idx
  ON public.orders USING gin (payload jsonb_path_ops)
  WITH (fastupdate = off);

CREATE INDEX orders_created_brin_idx
  ON public.orders USING brin (created_at)
  WITH (pages_per_range = 32, autosummarize = true);
