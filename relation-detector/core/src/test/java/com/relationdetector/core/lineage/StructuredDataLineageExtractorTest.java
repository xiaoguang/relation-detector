package com.relationdetector.core.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.parse.ExpressionTrace;
import com.relationdetector.contracts.parse.ProjectionEvent;
import com.relationdetector.contracts.parse.RowsetEvent;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.WriteEvent;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.core.identity.NamespaceContext;

class StructuredDataLineageExtractorTest {
    @Test
    void triggerPseudoRowsetAliasInFromClauseKeepsPhysicalBindingForLineage() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "UPDATE dbo.product_batches SET current_qty = i.quantity FROM inserted i",
                StatementSourceType.TRIGGER, "sqlserver-trigger.sql", 1, 1, Map.of());
        StructuredParseResult structured = new StructuredParseResult(
                "TEST", "sqlserver", statement.sourceName(), List.of(
                new RowsetEvent(StructuredParseEventType.TRIGGER_PSEUDO_ROWSET, provenance(1),
                        "TRIGGER", "dbo.inventory", "inventory", "inserted", "inserted",
                        "dbo.inventory", ""),
                new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance(1),
                        "FROM", "inserted", "inserted", "i", "", "", ""),
                new WriteEvent(StructuredParseEventType.WRITE_TARGET, provenance(1),
                        "product_batches", "dbo.product_batches", "pb", "", "", "", "",
                        ExpressionTrace.empty()),
                new WriteEvent(StructuredParseEventType.UPDATE_ASSIGNMENT, provenance(1),
                        "", "", "", "pb", "dbo.product_batches", "current_qty", "UPDATE_SET",
                        ExpressionTrace.of(List.of("i"), List.of("quantity"),
                                LineageFlowKind.VALUE, LineageTransformType.DIRECT))), List.of(), Map.of());

        var lineages = new StructuredDataLineageExtractor().extract(statement, structured);

        assertEquals(1, lineages.size());
        assertEquals(List.of("dbo.inventory.quantity"),
                lineages.get(0).sources().stream().map(source -> source.displayName()).toList());
    }

    @Test
    void triggerPseudoRowsetResolvesToTypedTargetTable() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "UPDATE product_batches SET current_qty = :NEW.quantity",
                StatementSourceType.TRIGGER,
                "oracle-trigger.sql",
                1,
                1,
                Map.of());
        StructuredParseResult structured = new StructuredParseResult(
                "TEST",
                "oracle",
                statement.sourceName(),
                List.of(
                        new RowsetEvent(StructuredParseEventType.TRIGGER_PSEUDO_ROWSET, provenance(1),
                                "TRIGGER", "inventory", "inventory", "NEW", "NEW", "inventory", ""),
                        new WriteEvent(StructuredParseEventType.WRITE_TARGET, provenance(1),
                                "product_batches", "product_batches", "product_batches",
                                "", "", "", "", ExpressionTrace.empty()),
                        new WriteEvent(StructuredParseEventType.UPDATE_ASSIGNMENT, provenance(1),
                                "", "", "", "product_batches", "product_batches", "current_qty",
                                "UPDATE_SET",
                                ExpressionTrace.of(List.of("NEW"), List.of("quantity"),
                                        LineageFlowKind.VALUE, LineageTransformType.DIRECT))),
                List.of(),
                Map.of());

        var lineages = new StructuredDataLineageExtractor().extract(statement, structured);

        assertEquals(1, lineages.size());
        assertEquals(List.of("inventory.quantity"),
                lineages.get(0).sources().stream().map(source -> source.displayName()).toList());
        assertEquals("product_batches.current_qty", lineages.get(0).target().displayName());
    }

    @Test
    void resolvesOnlyUnqualifiedWriteTargetSelfReference() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "UPDATE purchase_order_items SET received_qty = received_qty + v_accepted_qty",
                StatementSourceType.PROCEDURE,
                "self-update.sql",
                1,
                1,
                Map.of());
        StructuredParseResult structured = new StructuredParseResult(
                "TEST",
                "mysql",
                statement.sourceName(),
                List.of(
                        new WriteEvent(StructuredParseEventType.WRITE_TARGET, provenance(1),
                                "purchase_order_items", "purchase_order_items", "purchase_order_items",
                                "", "", "", "", ExpressionTrace.empty()),
                        new WriteEvent(StructuredParseEventType.UPDATE_ASSIGNMENT, provenance(1),
                                "", "", "", "purchase_order_items", "purchase_order_items", "received_qty",
                                "UPDATE_SET",
                                ExpressionTrace.of(List.of("", ""), List.of("received_qty", "v_accepted_qty"),
                                        LineageFlowKind.VALUE, LineageTransformType.ARITHMETIC))),
                List.of(),
                Map.of());

        var lineages = new StructuredDataLineageExtractor().extract(statement, structured);

        assertEquals(1, lineages.size());
        assertEquals(List.of("purchase_order_items.received_qty"),
                lineages.get(0).sources().stream().map(source -> source.displayName()).toList());
        assertEquals("purchase_order_items.received_qty", lineages.get(0).target().displayName());
        assertEquals(LineageTransformType.ARITHMETIC, lineages.get(0).transformType());
    }

    @Test
    void copiesTypedEventLineIntoCandidateAndRawEvidence() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "INSERT INTO sales_fact(order_id) SELECT o.id FROM sales_orders o",
                StatementSourceType.PLAIN_SQL,
                "sample-data/mysql/8.0/03-data/07.sql",
                20,
                28,
                Map.of(
                        "sourceFile", "sample-data/mysql/8.0/03-data/07.sql",
                        "sourceStatementId", "sample-data/mysql/8.0/03-data/07.sql:20-28",
                        "sourceObjectType", "SQL_WRITE"));
        StructuredParseResult structured = new StructuredParseResult(
                "TEST",
                "mysql",
                statement.sourceName(),
                List.of(
                        new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance(21),
                                "FROM", "sales_orders", "sales_orders", "o", "", "", ""),
                        new WriteEvent(StructuredParseEventType.WRITE_TARGET, provenance(20),
                                "sales_fact", "sales_fact", "sales_fact", "", "", "", "",
                                ExpressionTrace.empty()),
                        new WriteEvent(StructuredParseEventType.INSERT_SELECT_MAPPING, provenance(24),
                                "", "", "", "", "sales_fact", "order_id", "INSERT_SELECT",
                                ExpressionTrace.of(List.of("o"), List.of("id"),
                                        LineageFlowKind.VALUE, LineageTransformType.DIRECT))),
                List.of(),
                Map.of());

        var lineage = new StructuredDataLineageExtractor().extract(statement, structured).get(0);

        assertEquals(24L, lineage.attributes().get("sourceLine"));
        assertEquals(24L, lineage.evidence().get(0).attributes().get("sourceLine"));
    }

    @Test
    void fullGrammarLineageKeepsOnlyFullGrammarParserOrigin() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "INSERT INTO sales_fact(order_id) SELECT o.id FROM sales_orders o",
                StatementSourceType.PLAIN_SQL, "input.sql", 10, 12,
                Map.of("sourceFile", "input.sql", "sourceStatementId", "input.sql:10-12"));
        SourceProvenance full = SourceProvenance.fullGrammar(statement, 11, "", "typed-context");
        StructuredParseResult structured = new StructuredParseResult("FULL", "mysql", statement.sourceName(),
                List.of(
                        new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, full,
                                "FROM", "sales_orders", "sales_orders", "o", "", "", ""),
                        new WriteEvent(StructuredParseEventType.WRITE_TARGET, full,
                                "sales_fact", "sales_fact", "sales_fact", "", "", "", "",
                                ExpressionTrace.empty()),
                        new WriteEvent(StructuredParseEventType.INSERT_SELECT_MAPPING, full,
                                "", "", "", "", "sales_fact", "order_id", "INSERT_SELECT",
                                ExpressionTrace.of(List.of("o"), List.of("id"),
                                        LineageFlowKind.VALUE, LineageTransformType.DIRECT))),
                List.of(), Map.of());

        var evidence = new StructuredDataLineageExtractor().extract(statement, structured).get(0).evidence().get(0);

        assertTrue(Boolean.TRUE.equals(evidence.attributes().get("fullGrammarNative")));
        assertFalse(evidence.attributes().containsKey("tokenEventNative"));
        assertEquals("typed SQL write mapping", evidence.detail());
    }

    @Test
    void unqualifiedEndpointsDoNotMatchSchemaQualifiedKnownPhysicalTables() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "INSERT INTO target_table(source_id) SELECT s.id FROM source_table s",
                StatementSourceType.PLAIN_SQL,
                "unit.sql",
                1,
                1,
                Map.of());
        StructuredParseResult structured = new StructuredParseResult(
                "TEST",
                "common",
                statement.sourceName(),
                List.of(
                        new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance(1),
                                "FROM", "source_table", "source_table", "s", "", "", ""),
                        new WriteEvent(StructuredParseEventType.WRITE_TARGET, provenance(1),
                                "target_table", "target_table", "target_table", "", "", "", "",
                                ExpressionTrace.empty()),
                        new WriteEvent(StructuredParseEventType.INSERT_SELECT_MAPPING, provenance(1),
                                "", "", "", "", "target_table", "source_id", "INSERT_SELECT",
                                ExpressionTrace.of(List.of("s"), List.of("id"),
                                        LineageFlowKind.VALUE, LineageTransformType.DIRECT))),
                List.of(),
                Map.of());

        var lineages = new StructuredDataLineageExtractor().extract(statement, structured,
                java.util.Set.of(TableId.of("shop", "source_table"), TableId.of("shop", "target_table")));

        assertEquals(List.of(), lineages);
    }

    @Test
    void scanSchemaMatchesKnownPhysicalInternallyButDoesNotRewriteEndpoints() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "INSERT INTO target_table(source_id) SELECT s.id FROM source_table s",
                StatementSourceType.PLAIN_SQL,
                "unit.sql",
                1,
                1,
                Map.of());
        StructuredParseResult structured = new StructuredParseResult(
                "TEST",
                "common",
                statement.sourceName(),
                List.of(
                        new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance(1),
                                "FROM", "source_table", "source_table", "s", "", "", ""),
                        new WriteEvent(StructuredParseEventType.WRITE_TARGET, provenance(1),
                                "target_table", "target_table", "target_table", "", "", "", "",
                                ExpressionTrace.empty()),
                        new WriteEvent(StructuredParseEventType.INSERT_SELECT_MAPPING, provenance(1),
                                "", "", "", "", "target_table", "source_id", "INSERT_SELECT",
                                ExpressionTrace.of(List.of("s"), List.of("id"),
                                        LineageFlowKind.VALUE, LineageTransformType.DIRECT))),
                List.of(),
                Map.of());

        var lineages = new StructuredDataLineageExtractor(
                value -> value == null ? "" : value.toLowerCase(),
                new NamespaceContext("", "shop", List.of()))
                .extract(statement, structured,
                        java.util.Set.of(TableId.of("shop", "source_table"),
                                TableId.of("shop", "target_table")));

        assertEquals(1, lineages.size());
        assertEquals(List.of("source_table.id"),
                lineages.get(0).sources().stream().map(endpoint -> endpoint.displayName()).toList());
        assertEquals("target_table.source_id", lineages.get(0).target().displayName());
    }

    @Test
    void dialectIdentifierRulesNormalizeSourceAndTargetColumnKeysConsistently() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "INSERT INTO target_table(source_id) SELECT s.id FROM source_table s",
                StatementSourceType.PLAIN_SQL, "unit.sql", 1, 1, Map.of());
        StructuredParseResult structured = new StructuredParseResult(
                "TEST", "oracle", statement.sourceName(), List.of(
                new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance(1),
                        "FROM", "source_table", "source_table", "s", "", "", ""),
                new WriteEvent(StructuredParseEventType.WRITE_TARGET, provenance(1),
                        "target_table", "target_table", "target_table", "", "", "", "",
                        ExpressionTrace.empty()),
                new WriteEvent(StructuredParseEventType.INSERT_SELECT_MAPPING, provenance(1),
                        "", "", "", "", "target_table", "source_id", "INSERT_SELECT",
                        ExpressionTrace.of(List.of("s"), List.of("id"),
                                LineageFlowKind.VALUE, LineageTransformType.DIRECT))), List.of(), Map.of());

        var lineage = new StructuredDataLineageExtractor(
                value -> value == null ? "" : value.toUpperCase(Locale.ROOT)).extract(statement, structured).get(0);

        assertEquals("ID", lineage.sources().get(0).column().normalizedName());
        assertEquals("SOURCE_ID", lineage.target().column().normalizedName());
    }

    @Test
    void projectionAliasesDoNotDowngradeSchemaQualifiedNames() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "INSERT INTO target_table(source_id) SELECT orders.id",
                StatementSourceType.PLAIN_SQL,
                "unit.sql",
                1,
                1,
                Map.of());
        StructuredParseResult structured = new StructuredParseResult(
                "TEST",
                "common",
                statement.sourceName(),
                List.of(
                        new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance(1),
                                "FROM", "source_table", "shop.source_table", "s", "", "", ""),
                        new ProjectionEvent(StructuredParseEventType.PROJECTION_ITEM, provenance(1),
                                "shop.orders", "id",
                                ExpressionTrace.of(List.of("s"), List.of("id"),
                                        LineageFlowKind.VALUE, LineageTransformType.DIRECT)),
                        new WriteEvent(StructuredParseEventType.WRITE_TARGET, provenance(1),
                                "target_table", "target_table", "target_table", "", "", "", "",
                                ExpressionTrace.empty()),
                        new WriteEvent(StructuredParseEventType.INSERT_SELECT_MAPPING, provenance(1),
                                "", "", "", "", "target_table", "source_id", "INSERT_SELECT",
                                ExpressionTrace.of(List.of("orders"), List.of("id"),
                                        LineageFlowKind.VALUE, LineageTransformType.DIRECT))),
                List.of(),
                Map.of());

        assertEquals(List.of(), new StructuredDataLineageExtractor().extract(statement, structured),
                "shop.orders projection must not be available through the bare orders key");
    }

    @Test
    void schemaQualifiedRowsetBindingResolvesOnlyItsExactQualifiedName() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "INSERT INTO target_table(source_id) SELECT shop.orders.id FROM shop.orders",
                StatementSourceType.PLAIN_SQL, "unit.sql", 1, 1, Map.of());
        StructuredParseResult structured = new StructuredParseResult(
                "TEST", "common", statement.sourceName(), List.of(
                new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance(1),
                        "FROM", "shop.orders", "orders", "", "", "", ""),
                new WriteEvent(StructuredParseEventType.WRITE_TARGET, provenance(1),
                        "target_table", "target_table", "target_table", "", "", "", "",
                        ExpressionTrace.empty()),
                new WriteEvent(StructuredParseEventType.INSERT_SELECT_MAPPING, provenance(1),
                        "", "", "", "", "target_table", "source_id", "INSERT_SELECT",
                        ExpressionTrace.of(List.of("shop.orders"), List.of("id"),
                                LineageFlowKind.VALUE, LineageTransformType.DIRECT))), List.of(), Map.of());

        var lineages = new StructuredDataLineageExtractor().extract(statement, structured);

        assertEquals(List.of("shop.orders.id"),
                lineages.get(0).sources().stream().map(endpoint -> endpoint.displayName()).toList());
    }

    @Test
    void duplicateVisibleTableNamesAcrossSchemasAreAmbiguous() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "INSERT INTO target_table(source_id) SELECT orders.id",
                StatementSourceType.PLAIN_SQL, "unit.sql", 1, 1, Map.of());
        StructuredParseResult structured = new StructuredParseResult(
                "TEST", "common", statement.sourceName(), List.of(
                new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance(1),
                        "FROM", "shop.orders", "orders", "", "", "", ""),
                new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance(1),
                        "JOIN", "archive.orders", "orders", "", "", "", ""),
                new WriteEvent(StructuredParseEventType.WRITE_TARGET, provenance(1),
                        "target_table", "target_table", "target_table", "", "", "", "",
                        ExpressionTrace.empty()),
                new WriteEvent(StructuredParseEventType.INSERT_SELECT_MAPPING, provenance(1),
                        "", "", "", "", "target_table", "source_id", "INSERT_SELECT",
                        ExpressionTrace.of(List.of("orders"), List.of("id"),
                                LineageFlowKind.VALUE, LineageTransformType.DIRECT))), List.of(), Map.of());

        assertEquals(List.of(), new StructuredDataLineageExtractor().extract(statement, structured));
    }

    private SourceProvenance provenance(long line) {
        return SourceProvenance.source("sample-data/mysql/8.0/03-data/07.sql", line);
    }
}
