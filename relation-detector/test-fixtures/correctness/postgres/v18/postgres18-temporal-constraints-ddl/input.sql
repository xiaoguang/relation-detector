-- PostgreSQL 18 temporal constraints: PERIOD columns are temporal coverage metadata.
CREATE TABLE subscriptions (
    customer_id bigint NOT NULL,
    valid_at tstzrange NOT NULL,
    PRIMARY KEY (customer_id, valid_at WITHOUT OVERLAPS)
);

CREATE TABLE invoices (
    customer_id bigint NOT NULL,
    covered_at tstzrange NOT NULL,
    FOREIGN KEY (customer_id, PERIOD covered_at)
        REFERENCES subscriptions (customer_id, PERIOD valid_at)
);
