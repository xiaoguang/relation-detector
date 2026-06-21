-- PostgreSQL official regression/docs inspired: create_index.sql and CREATE INDEX.
CREATE TABLE accounts (
  id BIGINT PRIMARY KEY,
  account_no TEXT,
  deleted_at TIMESTAMP
);

CREATE TABLE ledger_entries (
  id BIGINT PRIMARY KEY,
  account_no TEXT,
  CONSTRAINT fk_ledger_accounts FOREIGN KEY (account_no) REFERENCES accounts(account_no)
);

CREATE UNIQUE INDEX accounts_account_no_uq
  ON accounts (account_no) INCLUDE (id) NULLS NOT DISTINCT;

CREATE UNIQUE INDEX accounts_account_no_active_uq
  ON accounts (account_no) INCLUDE (id) WHERE deleted_at IS NULL;
