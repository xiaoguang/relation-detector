package com.relationdetector.sqlserver;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.core.relation.StructuredRelationshipExtractor;
import com.relationdetector.sqlserver.tokenevent.SqlServerTokenEventStructuredSqlParser;

class SqlServerTransformConsistencyTest {
    @Test
    void caseValuesControlsAndArithmeticMatchAcrossParsers() {
        SqlStatementRecord statement = statement("""
                UPDATE so
                SET [paid_amount] = so.[paid_amount] + p.[amount],
                    [status] = CASE WHEN so.[paid_amount] + p.[amount] >= so.[total_amount]
                                    THEN 'paid' ELSE so.[status] END
                FROM [dbo].[sales_orders] AS so
                INNER JOIN [dbo].[payments] AS p ON p.[order_id] = so.[id];
                """);

        for (NamedParser parser : parsers()) {
            List<DataLineageCandidate> lineages = extract(parser.parser(), statement);
            assertLineage(parser.name(), lineages, "dbo.sales_orders.paid_amount", "dbo.sales_orders.paid_amount",
                    LineageFlowKind.VALUE, LineageTransformType.ARITHMETIC);
            assertLineage(parser.name(), lineages, "dbo.payments.amount", "dbo.sales_orders.paid_amount",
                    LineageFlowKind.VALUE, LineageTransformType.ARITHMETIC);
            assertLineage(parser.name(), lineages, "dbo.sales_orders.status", "dbo.sales_orders.status",
                    LineageFlowKind.VALUE, LineageTransformType.CASE_WHEN);
            assertLineage(parser.name(), lineages, "dbo.sales_orders.total_amount", "dbo.sales_orders.status",
                    LineageFlowKind.CONTROL, LineageTransformType.CASE_WHEN);
        }
    }

    @Test
    void concatCastAndArithmeticProjectionTransformsMatchAcrossParsers() {
        SqlStatementRecord statement = statement("""
                INSERT INTO [dbo].[projection_results] ([report_no], [effective_from], [gross_margin])
                SELECT CONCAT('DMG-', w.[code]),
                       CAST(pc.[created_at] AS DATE),
                       soi.[amount] - COALESCE(ce.[cogs_amount], 0)
                FROM [dbo].[warehouses] AS w
                JOIN [dbo].[product_categories] AS pc ON pc.[id] = w.[id]
                JOIN [dbo].[sales_order_items] AS soi ON soi.[id] = pc.[id]
                LEFT JOIN [dbo].[cogs_entries] AS ce ON ce.[order_item_id] = soi.[id];
                """);

        for (NamedParser parser : parsers()) {
            List<DataLineageCandidate> lineages = extract(parser.parser(), statement);
            assertLineage(parser.name(), lineages, "dbo.warehouses.code", "dbo.projection_results.report_no",
                    LineageFlowKind.VALUE, LineageTransformType.CONCAT_FORMAT);
            assertLineage(parser.name(), lineages, "dbo.product_categories.created_at",
                    "dbo.projection_results.effective_from", LineageFlowKind.VALUE,
                    LineageTransformType.FUNCTION_CALL);
            assertLineage(parser.name(), lineages, "dbo.sales_order_items.amount",
                    "dbo.projection_results.gross_margin", LineageFlowKind.VALUE,
                    LineageTransformType.ARITHMETIC);
            assertLineage(parser.name(), lineages, "dbo.cogs_entries.cogs_amount",
                    "dbo.projection_results.gross_margin", LineageFlowKind.VALUE,
                    LineageTransformType.ARITHMETIC);
        }
    }

    @Test
    void scalarAggregateSeparatesProjectionValueFromDirectLocatorControlAcrossParsers() {
        SqlStatementRecord statement = statement("""
                UPDATE sp
                SET [total_order_qty] = (
                    SELECT SUM(poi.[quantity])
                    FROM [dbo].[purchase_order_items] AS poi
                    JOIN [dbo].[purchase_orders] AS po ON poi.[order_id] = po.[id]
                    WHERE poi.[product_id] = sp.[product_id]
                      AND po.[supplier_id] = sp.[supplier_id]
                )
                FROM [dbo].[supplier_products] AS sp;
                """);

        List<String> expected = List.of(
                "CONTROL:DIRECT:[dbo.purchase_order_items.order_id, dbo.purchase_order_items.product_id, dbo.purchase_orders.id, dbo.purchase_orders.supplier_id, dbo.supplier_products.product_id, dbo.supplier_products.supplier_id]",
                "VALUE:AGGREGATE:[dbo.purchase_order_items.quantity]");
        for (NamedParser parser : parsers()) {
            assertEquals(expected,
                    targetFingerprints(extract(parser.parser(), statement),
                            "dbo.supplier_products.total_order_qty"),
                    () -> parser.name() + " scalar VALUE/CONTROL roles differ");
        }
    }

    @Test
    void updateAndMergeLocatorsControlOnlyTheirWriteScopeAcrossParsers() {
        SqlStatementRecord update = statement("""
                UPDATE sp
                SET [total_order_qty] = sp.[total_order_qty] + u.[delta_qty]
                FROM [dbo].[supplier_products] AS sp
                JOIN [dbo].[supplier_updates] AS u
                  ON u.[product_id] = sp.[product_id]
                 AND (u.[batch_id] = sp.[batch_id]
                      OR (u.[batch_id] IS NULL AND sp.[batch_id] IS NULL))
                WHERE sp.[supplier_id] = 7;
                """);
        SqlStatementRecord merge = statement("""
                MERGE INTO [dbo].[supplier_products] AS sp
                USING [dbo].[supplier_price_updates] AS u
                ON sp.[product_id] = u.[product_id]
                   AND sp.[supplier_id] = u.[supplier_id]
                WHEN MATCHED THEN
                  UPDATE SET sp.[purchase_price] = u.[new_price]
                WHEN NOT MATCHED THEN
                  INSERT ([product_id], [supplier_id], [last_changed_by])
                  VALUES (u.[product_id], u.[supplier_id], u.[changed_by]);
                """);

        for (NamedParser parser : parsers()) {
            assertEquals(List.of(
                            "CONTROL:DIRECT:[dbo.supplier_products.batch_id, dbo.supplier_products.product_id, dbo.supplier_products.supplier_id, dbo.supplier_updates.batch_id, dbo.supplier_updates.product_id]",
                            "VALUE:ARITHMETIC:[dbo.supplier_products.total_order_qty, dbo.supplier_updates.delta_qty]"),
                    targetFingerprints(extract(parser.parser(), update),
                            "dbo.supplier_products.total_order_qty"),
                    () -> parser.name() + " UPDATE locator roles differ");
            assertEquals(List.of(
                            "CONTROL:DIRECT:[dbo.supplier_price_updates.product_id, dbo.supplier_price_updates.supplier_id, dbo.supplier_products.product_id, dbo.supplier_products.supplier_id]",
                            "VALUE:DIRECT:[dbo.supplier_price_updates.new_price]"),
                    targetFingerprints(extract(parser.parser(), merge),
                            "dbo.supplier_products.purchase_price"),
                    () -> parser.name() + " MERGE locator roles differ");
            assertEquals(List.of(
                            "CONTROL:DIRECT:[dbo.supplier_price_updates.product_id, dbo.supplier_price_updates.supplier_id, dbo.supplier_products.product_id, dbo.supplier_products.supplier_id]",
                            "VALUE:DIRECT:[dbo.supplier_price_updates.supplier_id]"),
                    targetFingerprints(extract(parser.parser(), merge),
                            "dbo.supplier_products.supplier_id"),
                    () -> parser.name() + " MERGE INSERT locator roles differ");
        }
    }

    @Test
    void derivedProjectionLocatorDoesNotPullUnrelatedValueSourcesAcrossParsers() {
        SqlStatementRecord statement = statement("""
                UPDATE p
                SET [purchase_price] = supplier_cost.[preferred_price]
                FROM [dbo].[products] AS p
                INNER JOIN (
                    SELECT sp.[product_id], MIN(sp.[supplier_price]) AS [preferred_price]
                    FROM [dbo].[supplier_products] AS sp
                    WHERE sp.[is_preferred] = 1
                    GROUP BY sp.[product_id]
                ) AS supplier_cost ON supplier_cost.[product_id] = p.[id];
                """);

        List<String> expected = List.of(
                "CONTROL:DIRECT:[dbo.products.id, dbo.supplier_products.is_preferred, dbo.supplier_products.product_id]",
                "VALUE:AGGREGATE:[dbo.supplier_products.supplier_price]");
        for (NamedParser parser : parsers()) {
            assertEquals(expected,
                    targetFingerprints(extract(parser.parser(), statement),
                            "dbo.products.purchase_price"),
                    () -> parser.name() + " derived projection locator roles differ");
        }
    }

    @Test
    void insertSelectJoinAndFilterLocatorsControlOnlyItsMappedTargetsAcrossParsers() {
        SqlStatementRecord statement = statement("""
                INSERT INTO [dbo].[paid_order_audit] ([customer_id], [order_id], [action])
                SELECT c.[id], o.[id], 'paid_order'
                FROM [dbo].[customers] AS c
                JOIN [dbo].[sales_orders] AS o ON o.[customer_id] = c.[id]
                WHERE o.[status] = 'paid';
                """);

        for (NamedParser parser : parsers()) {
            assertEquals(List.of(
                            "CONTROL:DIRECT:[dbo.customers.id, dbo.sales_orders.customer_id, dbo.sales_orders.status]",
                            "VALUE:DIRECT:[dbo.customers.id]"),
                    targetFingerprints(extract(parser.parser(), statement),
                            "dbo.paid_order_audit.customer_id"),
                    () -> parser.name() + " first INSERT locator roles differ");
            assertEquals(List.of(
                            "CONTROL:DIRECT:[dbo.customers.id, dbo.sales_orders.customer_id, dbo.sales_orders.status]",
                            "VALUE:DIRECT:[dbo.sales_orders.id]"),
                    targetFingerprints(extract(parser.parser(), statement),
                            "dbo.paid_order_audit.order_id"),
                    () -> parser.name() + " second INSERT locator roles differ");
            assertEquals(List.of(
                            "CONTROL:DIRECT:[dbo.customers.id, dbo.sales_orders.customer_id, dbo.sales_orders.status]"),
                    targetFingerprints(extract(parser.parser(), statement),
                            "dbo.paid_order_audit.action"),
                    () -> parser.name() + " literal INSERT locator roles differ");
        }
    }

    @Test
    void nestedScalarAndExistsLocatorsMatchAcrossParsers() {
        SqlStatementRecord statement = statement("""
                INSERT INTO [dbo].[inventory_opening] ([batch_id])
                SELECT pb.[id]
                FROM [dbo].[product_batches] AS pb
                WHERE pb.[batch_no] = (
                    SELECT p.[sku]
                    FROM [dbo].[products] AS p
                    WHERE p.[id] = pb.[product_id]
                )
                  AND NOT EXISTS (
                    SELECT 1
                    FROM [dbo].[inventory] AS i
                    WHERE i.[batch_id] = pb.[id]
                );
                """);

        List<String> expected = List.of(
                "CONTROL:DIRECT:[dbo.inventory.batch_id, dbo.product_batches.batch_no, dbo.product_batches.id, dbo.product_batches.product_id, dbo.products.id, dbo.products.sku]",
                "VALUE:DIRECT:[dbo.product_batches.id]");
        for (NamedParser parser : parsers()) {
            assertEquals(expected,
                    targetFingerprints(extract(parser.parser(), statement),
                            "dbo.inventory_opening.batch_id"),
                    () -> parser.name() + " nested locator roles differ");
        }
    }

    @Test
    void groupByControlsOnlyItsAggregateWriteProjectionAcrossParsers() {
        SqlStatementRecord statement = statement("""
                INSERT INTO [dbo].[supplier_quantity_summary] ([total_order_qty])
                SELECT SUM(poi.[quantity])
                FROM [dbo].[purchase_order_items] AS poi
                GROUP BY poi.[product_id], poi.[order_id];
                """);

        List<String> expected = List.of(
                "CONTROL:AGGREGATE:[dbo.purchase_order_items.order_id, dbo.purchase_order_items.product_id]",
                "VALUE:AGGREGATE:[dbo.purchase_order_items.quantity]");
        for (NamedParser parser : parsers()) {
            assertEquals(expected,
                    targetFingerprints(extract(parser.parser(), statement),
                            "dbo.supplier_quantity_summary.total_order_qty"),
                    () -> parser.name() + " GROUP BY roles differ");
        }
    }

    @Test
    void groupByControlsCaseProjectionContainingAggregateAcrossParsers() {
        SqlStatementRecord statement = statement("""
                INSERT INTO [dbo].[supplier_quantity_summary] ([total_order_qty])
                SELECT CASE WHEN SUM(poi.[quantity]) > 0
                            THEN SUM(poi.[quantity]) ELSE 0 END
                FROM [dbo].[purchase_order_items] AS poi
                GROUP BY poi.[product_id], poi.[order_id];
                """);

        List<String> expected = List.of(
                "CONTROL:AGGREGATE:[dbo.purchase_order_items.order_id, dbo.purchase_order_items.product_id]",
                "CONTROL:CASE_WHEN:[dbo.purchase_order_items.quantity]",
                "VALUE:CASE_WHEN:[dbo.purchase_order_items.quantity]");
        for (NamedParser parser : parsers()) {
            assertEquals(expected,
                    targetFingerprints(extract(parser.parser(), statement),
                            "dbo.supplier_quantity_summary.total_order_qty"),
                    () -> parser.name() + " aggregate CASE GROUP BY roles differ");
        }
    }

    @Test
    void insertControlMappingKindMatchesAcrossParsers() {
        SqlStatementRecord statement = statement("""
                INSERT INTO [dbo].[supplier_quantity_summary] ([total_order_qty])
                SELECT SUM(poi.[quantity])
                FROM [dbo].[purchase_order_items] AS poi
                WHERE poi.[quantity] > 0
                GROUP BY poi.[product_id];
                """);

        for (NamedParser parser : parsers()) {
            List<String> mappingKinds = extract(parser.parser(), statement).stream()
                    .filter(lineage -> lineage.flowKind() == LineageFlowKind.CONTROL)
                    .map(lineage -> String.valueOf(lineage.attributes().get("mappingKind")))
                    .distinct()
                    .sorted()
                    .toList();
            assertEquals(List.of("INSERT_CONTROL"), mappingKinds,
                    () -> parser.name() + " INSERT CONTROL mapping kind differs");
        }
    }

    @Test
    void windowPartitionAndOrderControlOnlyTheirRankingProjectionAcrossParsers() {
        SqlStatementRecord statement = statement("""
                INSERT INTO [dbo].[supplier_rankings] ([supplier_id], [price_rank])
                SELECT sp.[supplier_id],
                       ROW_NUMBER() OVER (
                           PARTITION BY sp.[product_id]
                           ORDER BY sp.[purchase_price]
                       )
                FROM [dbo].[supplier_products] AS sp;
                """);

        List<String> expected = List.of(
                "CONTROL:WINDOW_DERIVED:[dbo.supplier_products.product_id, dbo.supplier_products.purchase_price]");
        for (NamedParser parser : parsers()) {
            assertEquals(expected,
                    targetFingerprints(extract(parser.parser(), statement),
                            "dbo.supplier_rankings.price_rank"),
                    () -> parser.name() + " window roles differ");
        }
    }

    @Test
    void typedLiteralGuardMarksConditionalRelationshipAcrossParsers() {
        SqlStatementRecord statement = statement("""
                SELECT pr.[id]
                FROM [dbo].[payment_receipts] AS pr
                JOIN [dbo].[customers] AS c
                  ON pr.[party_type] = 'customer'
                 AND pr.[party_id] = c.[id];
                """);

        for (NamedParser parser : parsers()) {
            List<RelationshipCandidate> relationships = new StructuredRelationshipExtractor()
                    .extract(statement, parser.parser().parseSql(statement, null));
            assertTrue(relationships.stream().anyMatch(relationship ->
                            relationship.evidence().stream().anyMatch(evidence ->
                                            Boolean.TRUE.equals(evidence.attributes().get("conditional"))
                                                    && "dbo.payment_receipts.party_type".equals(
                                                            evidence.attributes().get("discriminatorEndpoint"))
                                                    && "customer".equals(
                                                            evidence.attributes().get("discriminatorValue")))),
                    () -> parser.name() + " missing typed conditional relationship; actual="
                            + relationships.stream().map(relationship -> relationship.source().displayName()
                                    + "->" + relationship.target().displayName() + ":"
                                    + relationship.evidence().stream().map(evidence -> evidence.attributes()).toList())
                                    .toList());
        }
    }

    private List<NamedParser> parsers() {
        return List.of(
                new NamedParser("token-event", new SqlServerTokenEventStructuredSqlParser()),
                new NamedParser("sqlserver/2016",
                        new com.relationdetector.sqlserver.fullgrammar.v2016.FullGrammarDialectModule().sqlParser()),
                new NamedParser("sqlserver/2017",
                        new com.relationdetector.sqlserver.fullgrammar.v2017.FullGrammarDialectModule().sqlParser()),
                new NamedParser("sqlserver/2019",
                        new com.relationdetector.sqlserver.fullgrammar.v2019.FullGrammarDialectModule().sqlParser()),
                new NamedParser("sqlserver/2022",
                        new com.relationdetector.sqlserver.fullgrammar.v2022.FullGrammarDialectModule().sqlParser()),
                new NamedParser("sqlserver/2025",
                        new com.relationdetector.sqlserver.fullgrammar.v2025.FullGrammarDialectModule().sqlParser()));
    }

    private List<DataLineageCandidate> extract(StructuredSqlParser parser, SqlStatementRecord statement) {
        return new StructuredDataLineageExtractor().extract(statement, parser.parseSql(statement, null));
    }

    private void assertLineage(
            String parser,
            List<DataLineageCandidate> lineages,
            String source,
            String target,
            LineageFlowKind flow,
            LineageTransformType transform
    ) {
        assertTrue(lineages.stream().anyMatch(lineage -> lineage.flowKind() == flow
                        && lineage.transformType() == transform
                        && lineage.target().displayName().equals(target)
                        && lineage.sources().stream().anyMatch(endpoint -> endpoint.displayName().equals(source))),
                () -> parser + " missing " + flow + "/" + transform + " " + source + " -> " + target
                        + "; actual=" + describe(lineages));
    }

    private List<String> describe(List<DataLineageCandidate> lineages) {
        return lineages.stream().map(lineage -> lineage.flowKind() + "/" + lineage.transformType() + ":"
                + lineage.sources().stream().map(endpoint -> endpoint.displayName()).toList()
                + "->" + lineage.target().displayName()).toList();
    }

    private List<String> targetFingerprints(List<DataLineageCandidate> lineages, String target) {
        return lineages.stream()
                .filter(lineage -> lineage.target().displayName().equals(target))
                .map(lineage -> lineage.flowKind() + ":" + lineage.transformType() + ":"
                        + lineage.sources().stream().map(endpoint -> endpoint.displayName()).sorted().toList())
                .sorted()
                .toList();
    }

    private SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "sqlserver-transform-consistency.sql", 1, 1,
                Map.of());
    }

    private record NamedParser(String name, StructuredSqlParser parser) {
    }
}
