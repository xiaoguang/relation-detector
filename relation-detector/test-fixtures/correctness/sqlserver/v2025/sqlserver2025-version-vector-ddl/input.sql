CREATE TABLE dbo.product_embeddings (
    id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    embedding VECTOR(3) NULL,
    CONSTRAINT pk_product_embeddings PRIMARY KEY (id),
    CONSTRAINT fk_product_embeddings_product
        FOREIGN KEY (product_id) REFERENCES dbo.products(id)
);
