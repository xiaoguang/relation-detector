CREATE TABLE dbo.customers (
    customer_id INT NOT NULL,
    customer_name NVARCHAR(100) NOT NULL,
    region_code NVARCHAR(20) NOT NULL,
    CONSTRAINT pk_customers PRIMARY KEY (customer_id)
);

CREATE TABLE dbo.orders (
    order_id INT IDENTITY(1, 1) NOT NULL,
    customer_id INT NOT NULL,
    order_date DATE NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (order_id),
    CONSTRAINT fk_orders_customers FOREIGN KEY (customer_id) REFERENCES dbo.customers(customer_id)
);

CREATE INDEX ix_orders_customer_id ON dbo.orders(customer_id);

CREATE TABLE dbo.payments (
    payment_id INT IDENTITY(1, 1) NOT NULL,
    order_id INT NOT NULL,
    paid_at DATETIME2 NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (payment_id),
    CONSTRAINT fk_payments_orders FOREIGN KEY (order_id) REFERENCES dbo.orders(order_id)
);

CREATE INDEX ix_payments_order_id ON dbo.payments(order_id);

CREATE TABLE dbo.sales_fact (
    customer_id INT NOT NULL,
    order_id INT NOT NULL,
    paid_amount DECIMAL(18, 2) NOT NULL,
    last_paid_at DATETIME2 NULL,
    CONSTRAINT pk_sales_fact PRIMARY KEY (customer_id, order_id),
    CONSTRAINT fk_sales_fact_customers FOREIGN KEY (customer_id) REFERENCES dbo.customers(customer_id),
    CONSTRAINT fk_sales_fact_orders FOREIGN KEY (order_id) REFERENCES dbo.orders(order_id)
);
