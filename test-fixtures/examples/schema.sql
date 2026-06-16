CREATE TABLE users (
  id BIGINT PRIMARY KEY,
  name VARCHAR(100)
);

CREATE TABLE orders (
  id BIGINT PRIMARY KEY,
  user_id BIGINT,
  amount DECIMAL(10, 2),
  CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id)
);

