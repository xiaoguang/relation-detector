CREATE TABLE users (
  id bigint PRIMARY KEY,
  email text
);

CREATE TABLE orders (
  id bigint PRIMARY KEY,
  user_id bigint NOT NULL
);

CREATE INDEX CONCURRENTLY idx_orders_user_id ON ONLY orders USING btree (user_id);
ALTER TABLE ONLY orders
  ADD CONSTRAINT fk_orders_users FOREIGN KEY (user_id) REFERENCES users(id) NOT VALID;
