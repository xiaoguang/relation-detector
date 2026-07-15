package com.relationdetector.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.core.relation.StructuredRelationshipExtractor;
import com.relationdetector.oracle.tokenevent.OracleTokenEventStructuredSqlParser;

class OracleTransformConsistencyTest {
    @Test
    void caseBranchesAndPredicatesKeepDistinctRolesForEveryOracleParser() {
        SqlStatementRecord statement = statement("""
                INSERT INTO shipment_summary (actual_delivery_date)
                SELECT CASE
                    WHEN s.status = 'delivered' THEN s.shipped_at
                    ELSE s.shipped_at + INTERVAL '3' DAY
                END
                FROM shipments s
                """);

        List<String> expected = List.of(
                "CONTROL:CASE_WHEN:[shipments.status]",
                "VALUE:CASE_WHEN:[shipments.shipped_at]");
        for (NamedParser parser : parsers()) {
            assertEquals(expected,
                    targetFingerprints(extract(parser.parser(), statement),
                            "shipment_summary.actual_delivery_date"),
                    () -> parser.name() + " CASE VALUE/CONTROL roles differ");
        }
    }

    @Test
    void scalarAggregateSeparatesProjectionValueFromDirectLocatorControlForEveryOracleParser() {
        SqlStatementRecord statement = statement("""
                UPDATE supplier_products sp
                SET total_order_qty = (
                    SELECT SUM(poi.quantity)
                    FROM purchase_order_items poi
                    JOIN purchase_orders po ON poi.order_id = po.id
                    WHERE poi.product_id = sp.product_id
                      AND po.supplier_id = sp.supplier_id
                )
                """);

        List<String> expected = List.of(
                "CONTROL:DIRECT:[purchase_order_items.order_id, purchase_order_items.product_id, purchase_orders.id, purchase_orders.supplier_id, supplier_products.product_id, supplier_products.supplier_id]",
                "VALUE:AGGREGATE:[purchase_order_items.quantity]");
        for (NamedParser parser : parsers()) {
            assertEquals(expected,
                    targetFingerprints(extract(parser.parser(), statement),
                            "supplier_products.total_order_qty"),
                    () -> parser.name() + " scalar VALUE/CONTROL roles differ");
        }
    }

    @Test
    void nestedScalarLocatorInsideCoalesceRemainsDirectControlForEveryOracleParser() {
        SqlStatementRecord statement = statement("""
                UPDATE supplier_products sp
                SET return_rate = COALESCE((
                    SELECT SUM(pri.return_qty) / NULLIF(SUM(pri.return_qty) + (
                        SELECT SUM(poi.received_qty)
                        FROM purchase_order_items poi
                        JOIN purchase_orders po ON poi.order_id = po.id
                        WHERE poi.product_id = sp.product_id
                          AND po.supplier_id = sp.supplier_id
                    ), 0)
                    FROM purchase_returns pr
                    JOIN purchase_return_items pri ON pr.id = pri.return_id
                    WHERE pr.supplier_id = sp.supplier_id
                      AND pri.product_id = sp.product_id
                ), 0)
                """);

        List<String> expected = List.of(
                "CONTROL:DIRECT:[purchase_order_items.order_id, purchase_order_items.product_id, "
                        + "purchase_orders.id, purchase_orders.supplier_id, purchase_return_items.product_id, "
                        + "purchase_return_items.return_id, purchase_returns.id, purchase_returns.supplier_id, "
                        + "supplier_products.product_id, supplier_products.supplier_id]");
        for (NamedParser parser : parsers()) {
            assertEquals(expected,
                    targetFlowFingerprints(extract(parser.parser(), statement),
                            "supplier_products.return_rate", "CONTROL"),
                    () -> parser.name() + " nested scalar locator transform differs");
        }
    }

    @Test
    void proceduralElsifAndCaseBranchesKeepUpdateLocatorsForEveryOracleParser() {
        SqlStatementRecord statement = procedureStatement("""
                CREATE OR REPLACE PROCEDURE sp_branch_updates(p_kind IN VARCHAR2)
                AS
                BEGIN
                    IF p_kind = 'first' THEN
                        UPDATE products SET purchase_price = 1 WHERE id = 1;
                    ELSIF p_kind = 'second' THEN
                        UPDATE products SET wholesale_price = 2 WHERE id = 2;
                    END IF;

                    CASE p_kind
                        WHEN 'retail' THEN
                            UPDATE products SET retail_price = 3 WHERE id = 3;
                        WHEN 'inactive' THEN
                            UPDATE products SET status = 'inactive' WHERE id = 4;
                    END CASE;
                END;
                """);

        for (NamedParser parser : parsers()) {
            List<DataLineageCandidate> lineages = extract(parser.parser(), statement);
            for (String target : List.of("purchase_price", "wholesale_price", "retail_price", "status")) {
                assertEquals(List.of("CONTROL:DIRECT:[products.id]"),
                        targetFlowFingerprints(lineages, "products." + target, "CONTROL"),
                        () -> parser.name() + " lost locator for products." + target);
            }
        }
    }

    @Test
    void proceduralForSelectBodyKeepsJoinRelationshipForEveryOracleParser() {
        SqlStatementRecord statement = procedureStatement("""
                CREATE OR REPLACE PROCEDURE sp_expired_batches
                AS
                BEGIN
                    FOR rec IN (
                        SELECT pb.id, i.warehouse_id
                        FROM product_batches pb
                        JOIN inventory i ON pb.id = i.batch_id
                    )
                    LOOP
                        NULL;
                    END LOOP;
                END;
                """);

        for (NamedParser parser : parsers()) {
            List<RelationshipCandidate> relationships = new StructuredRelationshipExtractor()
                    .extract(statement, parser.parser().parseSql(statement, null));
            assertEquals(1L, relationships.stream().filter(relationship ->
                            relationship.source().displayName().equals("inventory.batch_id")
                                    && relationship.target().displayName().equals("product_batches.id"))
                    .count(), () -> parser.name() + " lost FOR SELECT JOIN: " + relationships);
        }
    }

    @Test
    void updateWhereLocatorsControlOnlyTheAssignedColumnForEveryOracleParser() {
        SqlStatementRecord statement = statement("""
                UPDATE supplier_products sp
                SET total_order_qty = sp.total_order_qty + 1
                WHERE sp.product_id = 42
                  AND sp.supplier_id = 7
                """);

        List<String> expected = List.of(
                "CONTROL:DIRECT:[supplier_products.product_id, supplier_products.supplier_id]",
                "VALUE:ARITHMETIC:[supplier_products.total_order_qty]");
        for (NamedParser parser : parsers()) {
            assertEquals(expected,
                    targetFingerprints(extract(parser.parser(), statement),
                            "supplier_products.total_order_qty"),
                    () -> parser.name() + " UPDATE locator roles differ");
        }
    }

    @Test
    void mergeOnLocatorsControlOnlyTheMatchedAssignmentForEveryOracleParser() {
        SqlStatementRecord statement = statement("""
                MERGE INTO supplier_products sp
                USING supplier_price_updates u
                ON (sp.product_id = u.product_id AND sp.supplier_id = u.supplier_id)
                WHEN MATCHED THEN UPDATE SET sp.purchase_price = u.new_price
                """);

        List<String> expected = List.of(
                "CONTROL:DIRECT:[supplier_price_updates.product_id, supplier_price_updates.supplier_id, supplier_products.product_id, supplier_products.supplier_id]",
                "VALUE:DIRECT:[supplier_price_updates.new_price]");
        for (NamedParser parser : parsers()) {
            assertEquals(expected,
                    targetFingerprints(extract(parser.parser(), statement),
                            "supplier_products.purchase_price"),
                    () -> parser.name() + " MERGE locator roles differ");
        }
    }

    @Test
    void groupByControlsOnlyItsAggregateWriteProjectionForEveryOracleParser() {
        SqlStatementRecord statement = statement("""
                INSERT INTO supplier_quantity_summary (total_order_qty)
                SELECT SUM(poi.quantity)
                FROM purchase_order_items poi
                GROUP BY poi.product_id
                """);

        List<String> expected = List.of(
                "CONTROL:AGGREGATE:[purchase_order_items.product_id]",
                "VALUE:AGGREGATE:[purchase_order_items.quantity]");
        for (NamedParser parser : parsers()) {
            assertEquals(expected,
                    targetFingerprints(extract(parser.parser(), statement),
                            "supplier_quantity_summary.total_order_qty"),
                    () -> parser.name() + " GROUP BY roles differ");
        }
    }

    @Test
    void windowPartitionAndOrderControlOnlyTheirRankingProjectionForEveryOracleParser() {
        SqlStatementRecord statement = statement("""
                INSERT INTO supplier_rankings (supplier_id, price_rank)
                SELECT sp.supplier_id,
                       ROW_NUMBER() OVER (
                           PARTITION BY sp.product_id
                           ORDER BY sp.purchase_price
                       )
                FROM supplier_products sp
                """);

        List<String> expected = List.of(
                "CONTROL:WINDOW_DERIVED:[supplier_products.product_id, supplier_products.purchase_price]");
        for (NamedParser parser : parsers()) {
            assertEquals(expected,
                    targetFingerprints(extract(parser.parser(), statement),
                            "supplier_rankings.price_rank"),
                    () -> parser.name() + " window roles differ");
        }
    }

    private List<NamedParser> parsers() {
        return List.of(
                new NamedParser("token-event", new OracleTokenEventStructuredSqlParser()),
                new NamedParser("oracle/12c",
                        new com.relationdetector.oracle.fullgrammar.v12c.FullGrammarDialectModule().sqlParser()),
                new NamedParser("oracle/19c",
                        new com.relationdetector.oracle.fullgrammar.v19c.FullGrammarDialectModule().sqlParser()),
                new NamedParser("oracle/21c",
                        new com.relationdetector.oracle.fullgrammar.v21c.FullGrammarDialectModule().sqlParser()),
                new NamedParser("oracle/26ai",
                        new com.relationdetector.oracle.fullgrammar.v26ai.FullGrammarDialectModule().sqlParser()));
    }

    private List<DataLineageCandidate> extract(StructuredSqlParser parser, SqlStatementRecord statement) {
        return new StructuredDataLineageExtractor().extract(statement, parser.parseSql(statement, null));
    }

    private List<String> targetFingerprints(List<DataLineageCandidate> lineages, String target) {
        return lineages.stream()
                .filter(lineage -> lineage.target().displayName().equals(target))
                .map(lineage -> lineage.flowKind() + ":" + lineage.transformType() + ":"
                        + lineage.sources().stream().map(endpoint -> endpoint.displayName()).sorted().toList())
                .sorted()
                .toList();
    }

    private List<String> targetFlowFingerprints(
            List<DataLineageCandidate> lineages,
            String target,
            String flowKind) {
        return lineages.stream()
                .filter(lineage -> lineage.target().displayName().equals(target))
                .filter(lineage -> lineage.flowKind().name().equals(flowKind))
                .map(lineage -> lineage.flowKind() + ":" + lineage.transformType() + ":"
                        + lineage.sources().stream().map(endpoint -> endpoint.displayName()).sorted().toList())
                .sorted()
                .toList();
    }

    private SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL,
                "oracle-transform-consistency.sql", 1, sql.lines().count(), Map.of());
    }

    private SqlStatementRecord procedureStatement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "ROUTINE:sp_test", 1, sql.lines().count(), Map.of());
    }

    private record NamedParser(String name, StructuredSqlParser parser) {
    }
}
