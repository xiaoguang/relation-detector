CREATE TABLE accounts (
  id BIGINT,
  account_no TEXT,
  deleted_at TIMESTAMP
);

CREATE TABLE invoices (
  account_no TEXT,
  archived_account_no TEXT,
  CONSTRAINT fk_invoices_accounts
    FOREIGN KEY (account_no) REFERENCES accounts(account_no),
  CONSTRAINT fk_invoices_archived_accounts
    FOREIGN KEY (archived_account_no) REFERENCES accounts(account_no)
);

CREATE UNIQUE INDEX accounts_no_covering_uq
  ON accounts (account_no) INCLUDE (id);

CREATE UNIQUE INDEX accounts_no_active_uq
  ON accounts (account_no) INCLUDE (id) WHERE deleted_at IS NULL;
