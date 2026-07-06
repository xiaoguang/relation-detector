-- Generated from MySQL statement log sources for basic-correctness-case-03.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: performance_schema.events_statements_history
/* ApplicationName=DBeaver 26.0.3 - Metadata */ SELECT DISTINCT A.REFERENCED_TABLE_SCHEMA AS PKTABLE_CAT,NULL AS PKTABLE_SCHEM, A.REFERENCED_TABLE_NAME AS PKTABLE_NAME, A.REFERENCED_COLUMN_NAME AS PKCOLUMN_NAME, A.TABLE_SCHEMA AS FKTABLE_CAT, NULL AS FKTABLE_SCHEM, A.TABLE_NAME AS FKTABLE_NAME, A.COLUMN_NAME AS FKCOLUMN_NAME, A.ORDINAL_POSITION AS KEY_SEQ,CASE WHEN R.UPDATE_RULE='CASCADE' THEN 0 WHEN R.UPDATE_RULE='SET NULL' THEN 2 WHEN R.UPDATE_RULE='SET DEFAULT' THEN 4 WHEN R.UPDATE_RULE='RESTRICT' THEN 1 WHEN R.UPDATE_RULE='NO ACTION' THEN 1 ELSE 1 END  AS UPDATE_RULE,CASE WHEN R.DELETE_RULE='CASCADE' THEN 0 WHEN R.DELETE_RULE='SET NULL' THEN 2 WHEN R.DELETE_RULE='SET DEFAULT' THEN 4 WHEN R.DELETE_RULE='RESTRICT' THEN 1 WHEN R.DELETE_RULE='NO ACTION' THEN 1 ELSE 1 END  AS DELETE_RULE, A.CONSTRAINT_NAME AS FK_NAME, R.UNIQUE_CONSTRAINT_NAME AS PK_NAME,7 AS DEFERRABILITY FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE A JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS B USING (CONSTRAINT_NAME, TABLE_NAME) JOIN INFORMATI...
-- relation-detector-fixture-end

-- relation-detector-fixture-source: performance_schema.events_statements_history
SELECT @@session.transaction_read_only
-- relation-detector-fixture-end

-- relation-detector-fixture-source: performance_schema.events_statements_history
SELECT @@session.transaction_read_only
-- relation-detector-fixture-end

-- relation-detector-fixture-source: performance_schema.events_statements_history
SELECT @@session.transaction_read_only
-- relation-detector-fixture-end

