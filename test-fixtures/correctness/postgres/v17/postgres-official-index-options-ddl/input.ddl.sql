-- PostgreSQL official regression/docs inspired: create_index.sql and CREATE INDEX.
CREATE TABLE public.users (
  id BIGINT PRIMARY KEY,
  email TEXT,
  locale TEXT
);

CREATE TABLE public.orders (
  id BIGINT PRIMARY KEY,
  user_id BIGINT,
  user_email TEXT,
  CONSTRAINT fk_orders_users FOREIGN KEY (user_id) REFERENCES public.users(id)
);

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS users_email_locale_uq
  ON ONLY public.users USING btree
  (email COLLATE "C" text_ops ASC NULLS LAST, locale DESC NULLS FIRST);
