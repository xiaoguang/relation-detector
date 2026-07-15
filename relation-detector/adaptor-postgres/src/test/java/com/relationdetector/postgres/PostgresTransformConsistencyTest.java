package com.relationdetector.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.postgres.tokenevent.PostgresTokenEventStructuredSqlParser;

class PostgresTransformConsistencyTest {
    @Test
    void caseBranchesAndPredicatesKeepDistinctRolesForEveryPostgresParser() {
        SqlStatementRecord statement = statement("""
                INSERT INTO shipment_summary (actual_delivery_date)
                SELECT CASE
                    WHEN s.status = 'delivered' THEN s.shipped_at
                    ELSE s.shipped_at + INTERVAL '3 days'
                END
                FROM shipments s;
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
    void scalarAggregateSeparatesProjectionValueFromDirectLocatorControlForEveryPostgresParser() {
        SqlStatementRecord statement = statement("""
                UPDATE supplier_products sp
                SET total_order_qty = (
                    SELECT SUM(poi.quantity)
                    FROM purchase_order_items poi
                    JOIN purchase_orders po ON poi.order_id = po.id
                    WHERE poi.product_id = sp.product_id
                      AND po.supplier_id = sp.supplier_id
                );
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
    void updateWhereLocatorControlsOnlyTheWrittenColumnForEveryPostgresParser() {
        SqlStatementRecord statement = statement("""
                UPDATE supplier_products sp
                SET quality_score = sp.quality_score + 1
                WHERE sp.status = 'active'
                  AND sp.product_id = 42;
                """);

        List<String> expected = List.of(
                "CONTROL:DIRECT:[supplier_products.product_id, supplier_products.status]",
                "VALUE:ARITHMETIC:[supplier_products.quality_score]");
        for (NamedParser parser : parsers()) {
            assertEquals(expected,
                    targetFingerprints(extract(parser.parser(), statement),
                            "supplier_products.quality_score"),
                    () -> parser.name() + " update locator scope differs");
        }
    }

    @Test
    void unqualifiedUpdateLocatorResolvesToTheWriteTargetForEveryPostgresParser() {
        SqlStatementRecord statement = statement("""
                UPDATE departments
                SET manager_id = 1
                WHERE id = 1;
                """);

        List<String> expected = List.of(
                "CONTROL:DIRECT:[departments.id]");
        for (NamedParser parser : parsers()) {
            assertEquals(expected,
                    targetFingerprints(extract(parser.parser(), statement),
                            "departments.manager_id"),
                    () -> parser.name() + " unqualified UPDATE locator differs");
        }
    }

    @Test
    void nestedScalarLocatorInsideCoalesceRemainsDirectControlForEveryPostgresParser() {
        SqlStatementRecord statement = statement("""
                UPDATE supplier_products sp
                SET return_rate = COALESCE((
                    SELECT SUM(pri.return_qty)
                    FROM purchase_returns pr
                    JOIN purchase_return_items pri ON pr.id = pri.return_id
                    WHERE pr.supplier_id = sp.supplier_id
                      AND pri.product_id = sp.product_id
                ), 0);
                """);

        List<String> expected = List.of(
                "CONTROL:DIRECT:[purchase_return_items.product_id, purchase_return_items.return_id, purchase_returns.id, purchase_returns.supplier_id, supplier_products.product_id, supplier_products.supplier_id]",
                "VALUE:AGGREGATE:[purchase_return_items.return_qty]");
        for (NamedParser parser : parsers()) {
            assertEquals(expected,
                    targetFingerprints(extract(parser.parser(), statement),
                            "supplier_products.return_rate"),
                    () -> parser.name() + " nested scalar locator transform differs");
        }
    }

    @Test
    void nullSafeUpdateLocatorKeepsBothPhysicalColumnsForEveryPostgresParser() {
        SqlStatementRecord statement = statement("""
                UPDATE inventory i
                SET quantity = sti.counted_quantity
                FROM stocktake_items sti
                WHERE i.product_id = sti.product_id
                  AND i.batch_id IS NOT DISTINCT FROM sti.batch_id;
                """);

        List<String> expected = List.of(
                "CONTROL:DIRECT:[inventory.batch_id, inventory.product_id, stocktake_items.batch_id, stocktake_items.product_id]",
                "VALUE:DIRECT:[stocktake_items.counted_quantity]");
        for (NamedParser parser : parsers()) {
            assertEquals(expected,
                    targetFingerprints(extract(parser.parser(), statement), "inventory.quantity"),
                    () -> parser.name() + " null-safe UPDATE locator differs");
        }
    }

    @Test
    void scalarGroupingIsSeparateAggregateControlForEveryPostgresParser() {
        SqlStatementRecord statement = statement("""
                UPDATE supplier_products sp
                SET total_order_qty = (
                    SELECT SUM(poi.quantity)
                    FROM purchase_order_items poi
                    WHERE poi.product_id = sp.product_id
                    GROUP BY poi.product_id
                );
                """);

        List<String> expected = List.of(
                "CONTROL:AGGREGATE:[purchase_order_items.product_id]",
                "CONTROL:DIRECT:[purchase_order_items.product_id, supplier_products.product_id]",
                "VALUE:AGGREGATE:[purchase_order_items.quantity]");
        for (NamedParser parser : parsers()) {
            assertEquals(expected,
                    targetFingerprints(extract(parser.parser(), statement),
                            "supplier_products.total_order_qty"),
                    () -> parser.name() + " scalar grouping role differs");
        }
    }

    @Test
    void windowPartitionAndOrderAreScopedWindowControlsForEveryPostgresParser() {
        SqlStatementRecord statement = statement("""
                INSERT INTO customer_rank (rank_no)
                SELECT ROW_NUMBER() OVER (
                    PARTITION BY o.region_id
                    ORDER BY o.total_amount DESC
                )
                FROM sales_orders o;
                """);

        List<String> expected = List.of(
                "CONTROL:WINDOW_DERIVED:[sales_orders.region_id, sales_orders.total_amount]");
        for (NamedParser parser : parsers()) {
            assertEquals(expected,
                    targetFingerprints(extract(parser.parser(), statement), "customer_rank.rank_no"),
                    () -> parser.name() + " window CONTROL role differs");
        }
    }

    private List<NamedParser> parsers() {
        return List.of(
                new NamedParser("token-event", new PostgresTokenEventStructuredSqlParser()),
                new NamedParser("postgres/16",
                        new com.relationdetector.postgres.fullgrammar.v16.FullGrammarDialectModule().sqlParser()),
                new NamedParser("postgres/17",
                        new com.relationdetector.postgres.fullgrammar.v17.FullGrammarDialectModule().sqlParser()),
                new NamedParser("postgres/18",
                        new com.relationdetector.postgres.fullgrammar.v18.FullGrammarDialectModule().sqlParser()));
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

    private SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL,
                "postgres-transform-consistency.sql", 1, sql.lines().count(), Map.of());
    }

    private record NamedParser(String name, StructuredSqlParser parser) {
    }
}
