package com.relationdetector.postgres;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Offline guard for the manual PostgreSQL basic-correctness exporter.
 *
 * <p>The real exporter connects to a read-only PostgreSQL database manually.
 * These tests keep the deterministic pieces under normal CI: catalog rows are
 * reconstructed into stable DDL text, generated fixtures use anonymized case
 * names, and no connection secret is allowed into output files.
 */
class PostgresBasicCorrectnessFixtureExporterTest {
    @Test
    void derivesStableCaseNamingFromCaseNumber() {
        PostgresBasicCorrectnessFixtureExporter.CaseNaming naming =
                PostgresBasicCorrectnessFixtureExporter.caseNaming("2");

        assertEquals("postgres-basic-correctness-case-02", naming.caseId());
        assertEquals("case_02", naming.anonymizedSchema());
        assertTrue(naming.rawRoot().toString()
                .endsWith("test-fixtures/postgres/basic-correctness/case-02"));
    }

    @Test
    void rebuildsStablePostgresDdlFromCatalogFacts() {
        PostgresBasicCorrectnessFixtureExporter.TableDefinition table =
                new PostgresBasicCorrectnessFixtureExporter.TableDefinition(
                        "public",
                        "orders",
                        List.of(
                                new PostgresBasicCorrectnessFixtureExporter.ColumnDefinition(
                                        "id", "bigint", "NO", null, null, 1),
                                new PostgresBasicCorrectnessFixtureExporter.ColumnDefinition(
                                        "user_id", "bigint", "NO", null, null, 2),
                                new PostgresBasicCorrectnessFixtureExporter.ColumnDefinition(
                                        "status", "text", "YES", "'NEW'::text", null, 3)
                        ),
                        List.of(
                                new PostgresBasicCorrectnessFixtureExporter.ConstraintDefinition(
                                        "orders_pkey", "PRIMARY KEY (id)", "p"),
                                new PostgresBasicCorrectnessFixtureExporter.ConstraintDefinition(
                                        "orders_user_fk", "FOREIGN KEY (user_id) REFERENCES users(id) NOT VALID", "f")
                        ),
                        List.of(new PostgresBasicCorrectnessFixtureExporter.IndexDefinition(
                                "orders_user_idx", "CREATE INDEX orders_user_idx ON public.orders USING btree (user_id)"))
                );

        String ddl = PostgresBasicCorrectnessFixtureExporter.writeDdlFixture(
                "postgres-basic-correctness-case-01",
                "case_01",
                List.of(table));

        assertTrue(ddl.contains("-- Generated from PostgreSQL catalog for postgres-basic-correctness-case-01."));
        assertTrue(ddl.contains("-- relation-detector-fixture-table: case_01.orders"));
        assertTrue(ddl.contains("CREATE TABLE case_01.orders ("));
        assertTrue(ddl.contains("id bigint NOT NULL"));
        assertTrue(ddl.contains("status text DEFAULT 'NEW'::text"));
        assertTrue(ddl.contains("CONSTRAINT orders_pkey PRIMARY KEY (id)"));
        assertTrue(ddl.contains("CONSTRAINT orders_user_fk FOREIGN KEY (user_id) REFERENCES users(id) NOT VALID"));
        assertTrue(ddl.contains("CREATE INDEX orders_user_idx ON case_01.orders USING btree (user_id);"));
    }

    @Test
    void generatedFixturesDoNotLeakConnectionSecrets() {
        String ddl = PostgresBasicCorrectnessFixtureExporter.writeDdlFixture(
                "postgres-basic-correctness-case-01",
                "case_01",
                List.of());
        String sql = PostgresBasicCorrectnessFixtureExporter.writeSqlFixture(
                "postgres-basic-correctness-case-01",
                List.of(new PostgresBasicCorrectnessFixtureExporter.SqlSample(
                        "pg_stat_statements",
                        "SELECT * FROM orders o JOIN users u ON o.user_id = u.id")));

        String combined = ddl + sql;
        assertFalse(combined.contains("fixture-export-host.example"));
        assertFalse(combined.contains("fixture_export_user"));
        assertFalse(combined.contains("SECRET_SHOULD_NOT_APPEAR"));
    }
}
