-- PostgreSQL 18 virtual generated column DDL.
CREATE TABLE ledger_entries (
    id bigint PRIMARY KEY,
    amount numeric NOT NULL,
    fee numeric NOT NULL,
    total numeric GENERATED ALWAYS AS (amount + fee) VIRTUAL
);
