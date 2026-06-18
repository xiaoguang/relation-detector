CREATE TABLE users (
  id BIGINT,
  email TEXT
);

CREATE TABLE orders (
  user_email TEXT,
  CONSTRAINT fk_orders_users_email
    FOREIGN KEY (user_email) REFERENCES users(email)
);

CREATE UNIQUE INDEX users_email_active_uq
  ON users (email)
  WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX users_email_expr_uq
  ON users ((lower(email)));
