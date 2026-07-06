CREATE TABLE product_embeddings (
    product_id NUMBER PRIMARY KEY,
    embedding VECTOR(3, FLOAT32)
);
