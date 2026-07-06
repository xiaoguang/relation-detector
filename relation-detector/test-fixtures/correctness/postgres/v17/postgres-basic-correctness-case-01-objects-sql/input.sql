-- Generated from PostgreSQL SQL sources for postgres-basic-correctness-case-01.
-- Refresh with PostgresBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-source: TRIGGER:pg_trigger:rna.rna_audit
CREATE TRIGGER rna_audit BEFORE UPDATE ON case_01.rna FOR EACH ROW EXECUTE FUNCTION trigger_fct_rna_audit()
-- relation-detector-fixture-end

-- relation-detector-fixture-source: TRIGGER:pg_trigger:rnc_database.rnc_database_audit
CREATE TRIGGER rnc_database_audit BEFORE UPDATE ON case_01.rnc_database FOR EACH ROW EXECUTE FUNCTION trigger_fct_rnc_database_audit()
-- relation-detector-fixture-end

-- relation-detector-fixture-source: TRIGGER:pg_trigger:rnc_modifications.rnc_modifications_tr
CREATE TRIGGER rnc_modifications_tr BEFORE INSERT ON case_01.rnc_modifications FOR EACH ROW WHEN ((new.id IS NULL)) EXECUTE FUNCTION trigger_fct_rnc_modifications_tr()
-- relation-detector-fixture-end

-- relation-detector-fixture-source: TRIGGER:pg_trigger:rnc_release.rnc_release_audit
CREATE TRIGGER rnc_release_audit BEFORE UPDATE ON case_01.rnc_release FOR EACH ROW EXECUTE FUNCTION trigger_fct_rnc_release_audit()
-- relation-detector-fixture-end

-- relation-detector-fixture-source: TRIGGER:pg_trigger:xref.xref_pk_update
CREATE TRIGGER xref_pk_update BEFORE INSERT ON case_01.xref FOR EACH ROW EXECUTE FUNCTION trigger_fct_xref_pk_update()
-- relation-detector-fixture-end

-- relation-detector-fixture-source: TRIGGER:pg_trigger:xref.xref_trigger_insert
CREATE TRIGGER xref_trigger_insert BEFORE INSERT ON case_01.xref FOR EACH ROW EXECUTE FUNCTION xref_insert_trigger()
-- relation-detector-fixture-end

