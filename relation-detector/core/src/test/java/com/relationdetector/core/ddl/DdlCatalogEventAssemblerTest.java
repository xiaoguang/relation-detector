package com.relationdetector.core.ddl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.DdlCatalogEvent;
import com.relationdetector.contracts.parse.DdlEvent;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.SqlStatementRecord;

final class DdlCatalogEventAssemblerTest {
    @Test
    void assemblesOneClosedCatalogEventFromTypedDdlEvents() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "ignored by catalog assembler",
                StatementSourceType.DDL_FILE,
                "schema.sql",
                1,
                8,
                Map.of("sourceStatementId", "schema.sql#1"));
        SourceProvenance provenance = SourceProvenance.source("schema.sql", 2).rebase(statement);
        List<DdlEvent> events = List.of(
                ddlColumn(provenance, "orders", "id"),
                ddlColumn(provenance, "orders", "customer_id"),
                ddlColumn(provenance, "customers", "id"),
                new DdlEvent(StructuredParseEventType.DDL_FOREIGN_KEY, provenance,
                        "orders", "customer_id", "customers", "id",
                        "", "", "", "", 1, 1),
                new DdlEvent(StructuredParseEventType.DDL_INDEX, provenance,
                        "", "", "", "", "customers", "id",
                        "TARGET_UNIQUE", "PRIMARY_KEY", 1, 1));

        DdlCatalogEvent event = new DdlCatalogEventAssembler().assemble(statement, events);

        assertEquals(2, event.tables().size());
        assertEquals(3, event.columns().size());
        assertEquals(2, event.constraints().size());
        assertEquals(1, event.indexes().size());
        assertTrue(event.gaps().isEmpty());
        assertEquals("FOREIGN_KEY", event.constraints().stream()
                .filter(constraint -> constraint.constraintType().equals("FOREIGN_KEY"))
                .findFirst().orElseThrow().constraintType());
        assertEquals(List.of("customer_id"), event.constraints().stream()
                .filter(constraint -> constraint.constraintType().equals("FOREIGN_KEY"))
                .findFirst().orElseThrow().columns());
    }

    @Test
    void preservesTypedViewIdentityWithoutReadingSqlText() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "this text is deliberately not parsed",
                StatementSourceType.VIEW,
                "views.sql",
                1,
                1,
                Map.of("sourceObjectName", "reporting.order_summary"));

        DdlCatalogEvent event = new DdlCatalogEventAssembler().assemble(statement, List.of());

        assertEquals(1, event.tables().size());
        assertEquals("reporting", event.tables().get(0).schema());
        assertEquals("order_summary", event.tables().get(0).tableName());
        assertEquals("VIEW", event.tables().get(0).tableType());
        assertTrue(event.gaps().isEmpty());
    }

    @Test
    void referencesDoNotInventTableOrColumnDeclarations() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "not inspected",
                StatementSourceType.DDL_FILE,
                "indexes.sql",
                1,
                1,
                Map.of("sourceStatementId", "indexes.sql#1"));
        SourceProvenance provenance = SourceProvenance.source("indexes.sql", 1).rebase(statement);

        DdlCatalogEvent event = new DdlCatalogEventAssembler().assemble(statement, List.of(
                new DdlEvent(StructuredParseEventType.DDL_INDEX, provenance,
                        "", "", "", "", "orders", "customer_id",
                        "SOURCE_INDEX", "INDEX", 1, 1)));

        assertTrue(event.tables().isEmpty());
        assertTrue(event.columns().isEmpty());
        assertEquals(1, event.indexes().size());
    }

    @Test
    void alterTableReferenceColumnsDoNotBecomeNewCatalogDeclarations() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "not inspected",
                StatementSourceType.DDL_FILE,
                "alter-table.sql",
                1,
                1,
                Map.of("sourceStatementId", "alter-table.sql#1"));
        SourceProvenance provenance = SourceProvenance.source("alter-table.sql", 1).rebase(statement);

        DdlCatalogEvent event = new DdlCatalogEventAssembler().assemble(statement, List.of(
                new DdlEvent(StructuredParseEventType.DDL_COLUMN, provenance,
                        "", "", "", "", "orders", "customer_id",
                        "", "REFERENCE", 1, 1),
                new DdlEvent(StructuredParseEventType.DDL_COLUMN, provenance,
                        "", "", "", "", "customers", "id",
                        "", "REFERENCE", 1, 1),
                new DdlEvent(StructuredParseEventType.DDL_FOREIGN_KEY, provenance,
                        "orders", "customer_id", "customers", "id",
                        "", "", "", "", 1, 1)));

        assertTrue(event.tables().isEmpty());
        assertTrue(event.columns().isEmpty());
        assertEquals(1, event.constraints().size());
    }

    @Test
    void completeTypedIndexRemainsAuditableWhenTheSqlParserReportsSyntaxRecovery() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "not inspected",
                StatementSourceType.DDL_FILE,
                "indexes.sql",
                1,
                1,
                Map.of(
                        "sourceStatementId", "indexes.sql#1",
                        "syntaxErrors", 1));
        SourceProvenance provenance = SourceProvenance.source("indexes.sql", 1).rebase(statement);

        DdlCatalogEvent event = new DdlCatalogEventAssembler().assemble(statement, List.of(
                new DdlEvent(StructuredParseEventType.DDL_INDEX, provenance,
                        "", "", "", "", "orders", "customer_id",
                        "SOURCE_INDEX", "INDEX", 1, 1)));

        assertEquals(1, event.indexes().size());
        assertTrue(event.gaps().isEmpty());
    }

    @Test
    void syntaxRecoveryWithoutCatalogFactsDoesNotOverrideExplicitScopeCoverage() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "not inspected",
                StatementSourceType.DDL_FILE,
                "auxiliary-ddl.sql",
                1,
                1,
                Map.of(
                        "sourceStatementId", "auxiliary-ddl.sql#1",
                        "syntaxErrors", 1));

        DdlCatalogEvent event = new DdlCatalogEventAssembler().assemble(statement, List.of());

        assertTrue(event.tables().isEmpty());
        assertTrue(event.columns().isEmpty());
        assertTrue(event.constraints().isEmpty());
        assertTrue(event.indexes().isEmpty());
        assertTrue(event.gaps().isEmpty());
    }

    private DdlEvent ddlColumn(SourceProvenance provenance, String table, String column) {
        return new DdlEvent(StructuredParseEventType.DDL_COLUMN, provenance,
                "", "", "", "", table, column, "", "", 1, 1);
    }
}
