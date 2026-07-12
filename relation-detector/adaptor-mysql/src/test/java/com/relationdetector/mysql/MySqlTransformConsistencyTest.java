package com.relationdetector.mysql;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.mysql.tokenevent.MySqlTokenEventStructuredSqlParser;

class MySqlTransformConsistencyTest {
    @Test
    void unqualifiedArithmeticSelfUpdateIsResolvedOnlyToWriteTarget() {
        SqlStatementRecord statement = statement("""
                UPDATE purchase_order_items
                SET received_qty = received_qty + v_accepted_qty
                WHERE id = p_order_item_id;
                """);

        for (NamedParser parser : parsers()) {
            List<DataLineageCandidate> lineages = extract(parser.parser(), statement);
            assertLineage(parser.name(), lineages,
                    "purchase_order_items.received_qty", "purchase_order_items.received_qty",
                    LineageFlowKind.VALUE, LineageTransformType.ARITHMETIC);
            assertTrue(lineages.stream().noneMatch(lineage -> lineage.sources().stream()
                            .anyMatch(source -> source.displayName().contains("v_accepted_qty"))),
                    () -> parser.name() + " must not bind a routine variable as a physical column: " + lineages);
        }
    }

    @Test
    void arithmeticDominatesNestedCoalesceForEveryMysqlParser() {
        SqlStatementRecord statement = statement("""
                INSERT INTO sales_commissions (commission_amount)
                SELECT ROUND(soi.amount * COALESCE(cr.commission_rate, 0.02), 2)
                FROM sales_order_items soi
                LEFT JOIN commission_rules cr ON cr.product_category_id = soi.product_id;
                """);

        for (NamedParser parser : parsers()) {
            List<DataLineageCandidate> lineages = extract(parser.parser(), statement);
            assertLineage(parser.name(), lineages, "sales_order_items.amount", "sales_commissions.commission_amount",
                    LineageFlowKind.VALUE, LineageTransformType.ARITHMETIC);
            assertLineage(parser.name(), lineages, "commission_rules.commission_rate",
                    "sales_commissions.commission_amount", LineageFlowKind.VALUE, LineageTransformType.ARITHMETIC);
        }
    }

    @Test
    void conditionalBranchValuesAndPredicatesKeepDistinctRolesForEveryMysqlParser() {
        SqlStatementRecord statement = statement("""
                INSERT INTO shipment_summary (actual_delivery_date)
                SELECT IF(s.status = 'delivered', s.shipped_at, DATE_ADD(s.shipped_at, INTERVAL 3 DAY))
                FROM shipments s;
                """);

        for (NamedParser parser : parsers()) {
            List<DataLineageCandidate> lineages = extract(parser.parser(), statement);
            assertLineage(parser.name(), lineages, "shipments.shipped_at",
                    "shipment_summary.actual_delivery_date", LineageFlowKind.VALUE,
                    LineageTransformType.CASE_WHEN);
            assertLineage(parser.name(), lineages, "shipments.status",
                    "shipment_summary.actual_delivery_date", LineageFlowKind.CONTROL,
                    LineageTransformType.CASE_WHEN);
        }
    }

    @Test
    void intervalArithmeticDominatesNestedCoalesceForEveryMysqlParser() {
        SqlStatementRecord statement = statement("""
                INSERT INTO project_costs (cost_date)
                SELECT DATE_ADD(
                    p.start_date,
                    INTERVAL FLOOR(RAND() * DATEDIFF(COALESCE(p.actual_end_date, CURDATE()), p.start_date)) DAY)
                FROM projects p;
                """);

        for (NamedParser parser : parsers()) {
            List<DataLineageCandidate> lineages = extract(parser.parser(), statement);
            assertLineage(parser.name(), lineages, "projects.start_date", "project_costs.cost_date",
                    LineageFlowKind.VALUE, LineageTransformType.ARITHMETIC);
            assertLineage(parser.name(), lineages, "projects.actual_end_date", "project_costs.cost_date",
                    LineageFlowKind.VALUE, LineageTransformType.ARITHMETIC);
        }
    }

    @Test
    void scalarAggregateCaseProducesOneCanonicalControlObservationForEveryMysqlParser() {
        SqlStatementRecord statement = statement("""
                UPDATE supplier_products sp
                SET quality_score = COALESCE((
                    SELECT ROUND(COUNT(CASE WHEN ir.inspection_result = 'qualified' THEN 1 END) * 100.0
                        / NULLIF(COUNT(*), 0), 2)
                    FROM inspection_reports ir
                    JOIN product_batches pb ON ir.batch_id = pb.id
                    WHERE pb.supplier_id = sp.supplier_id
                      AND ir.product_id = sp.product_id
                ), 100);
                """);

        List<String> expected = targetFingerprints(
                extract(parsers().get(0).parser(), statement), "supplier_products.quality_score");
        for (NamedParser parser : parsers().subList(1, parsers().size())) {
            assertEquals(expected,
                    targetFingerprints(extract(parser.parser(), statement), "supplier_products.quality_score"),
                    () -> parser.name() + " scalar aggregate control observations differ from token-event");
        }
    }

    @Test
    void scalarProjectionTransformIgnoresArithmeticInLocatorPredicateForEveryMysqlParser() {
        SqlStatementRecord statement = statement("""
                INSERT INTO damage_report_items (batch_id)
                SELECT (
                    SELECT pb.id
                    FROM product_batches pb
                    WHERE pb.product_id = FLOOR(RAND() * 50) + 1
                    LIMIT 1)
                FROM damage_reports dr;
                """);

        for (NamedParser parser : parsers()) {
            List<DataLineageCandidate> lineages = extract(parser.parser(), statement);
            assertLineage(parser.name(), lineages, "product_batches.id", "damage_report_items.batch_id",
                    LineageFlowKind.VALUE, LineageTransformType.DIRECT);
        }
    }

    private List<NamedParser> parsers() {
        return List.of(
                new NamedParser("token-event", new MySqlTokenEventStructuredSqlParser()),
                new NamedParser("mysql/5.7",
                        new com.relationdetector.mysql.fullgrammar.v5_7.FullGrammarDialectModule().sqlParser()),
                new NamedParser("mysql/8.0",
                        new com.relationdetector.mysql.fullgrammar.v8_0.FullGrammarDialectModule().sqlParser()));
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
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "mysql-transform-consistency.sql", 1, 1,
                Map.of());
    }

    private record NamedParser(String name, StructuredSqlParser parser) {
    }
}
