CREATE TABLE feature_flags (
    id NUMBER PRIMARY KEY,
    enabled BOOLEAN
);

INSERT INTO feature_flags (id, enabled)
VALUES (1, TRUE), (2, FALSE);
