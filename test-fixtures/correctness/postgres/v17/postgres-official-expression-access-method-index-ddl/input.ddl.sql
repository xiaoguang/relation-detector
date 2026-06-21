-- PostgreSQL official regression/docs inspired: create_index.sql and CREATE INDEX.
CREATE TABLE documents (
  id BIGINT PRIMARY KEY,
  account_id BIGINT,
  body TEXT,
  tags TEXT[],
  geom BOX,
  payload JSONB
);

CREATE TABLE accounts (
  id BIGINT PRIMARY KEY
);

ALTER TABLE documents
  ADD CONSTRAINT fk_documents_accounts
  FOREIGN KEY (account_id) REFERENCES accounts(id);

CREATE INDEX documents_lower_body_idx ON documents (lower(body));
CREATE INDEX documents_tags_gin_idx ON documents USING gin (tags) WITH (fastupdate = off);
CREATE INDEX documents_geom_gist_idx ON documents USING gist (geom) WITH (buffering = auto);
CREATE INDEX documents_payload_hash_idx ON documents USING hash ((payload ->> 'external_id'));
CREATE INDEX documents_id_brin_idx ON documents USING brin (id) WITH (pages_per_range = 32);
