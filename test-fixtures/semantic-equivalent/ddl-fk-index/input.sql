-- Semantic equivalent scenario: FK plus supporting source index and target unique evidence.
CREATE TABLE customers (
    id INT PRIMARY KEY
);
CREATE TABLE sales_orders (
    id INT PRIMARY KEY,
    customer_id INT,
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);
CREATE INDEX idx_sales_orders_customer_id ON sales_orders(customer_id);
