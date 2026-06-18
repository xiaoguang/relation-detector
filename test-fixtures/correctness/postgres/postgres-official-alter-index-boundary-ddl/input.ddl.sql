-- PostgreSQL official regression/docs inspired: create_index.sql and ALTER INDEX.
CREATE TABLE users (
  id BIGINT PRIMARY KEY,
  email TEXT
);

CREATE TABLE password_resets (
  id BIGINT PRIMARY KEY,
  user_id BIGINT,
  CONSTRAINT fk_password_resets_users FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX password_resets_user_idx ON password_resets (user_id);
ALTER INDEX password_resets_user_idx RENAME TO password_resets_user_id_idx;
ALTER INDEX password_resets_user_id_idx SET (fillfactor = 80);
ALTER INDEX password_resets_user_id_idx RESET (fillfactor);
