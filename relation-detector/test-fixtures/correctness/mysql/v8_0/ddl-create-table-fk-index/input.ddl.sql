CREATE TABLE users (
  id bigint NOT NULL,
  email varchar(255),
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email)
);

CREATE TABLE orders (
  id bigint NOT NULL,
  user_id bigint NOT NULL,
  PRIMARY KEY (id),
  KEY idx_orders_user_id (user_id),
  CONSTRAINT fk_orders_users FOREIGN KEY (user_id) REFERENCES users(id)
);
