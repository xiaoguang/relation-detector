package com.relationdetector.postgres.fullgrammar.v16;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.relation.StructuredRelationshipExtractor;

class PostgresFullGrammarExpressionAnalyzerTest {
    @Test
    void nullSafeEqualityBeforeTrailingAndExposesTypedOperands() {
        String sql = """
                UPDATE inventory_location_balances ilb
                SET locked_quantity = ilb.locked_quantity + pti.required_qty
                FROM picking_task_items pti
                JOIN picking_tasks pt ON pt.id = pti.picking_task_id
                WHERE pti.location_id = ilb.location_id
                  AND pti.product_id = ilb.product_id
                  AND pti.batch_id IS NOT DISTINCT FROM ilb.batch_id
                  AND pt.id = v_task_id;
                """;
        var lexer = new PostgresFullGrammarLexer(CharStreams.fromString(sql));
        var parser = new PostgresFullGrammarParser(new CommonTokenStream(lexer));
        var root = parser.root();
        var adapter = new PostgresParseTreeAdapter();
        var matches = new java.util.ArrayList<String>();
        new PostgresFullGrammarParserBaseVisitor<Void>() {
            @Override
            public Void visitA_expr_is_not(PostgresFullGrammarParser.A_expr_is_notContext ctx) {
                if (ctx.DISTINCT() != null) {
                    adapter.directEqualities(ctx).forEach(operands -> matches.add(
                            operands.left().getText() + "->" + operands.right().getText()));
                }
                return visitChildren(ctx);
            }
        }.visit(root);

        assertTrue(matches.contains("pti.batch_id->ilb.batch_id"),
                () -> "Typed null-safe equality operands were not exposed: " + matches
                        + " tree=" + root.toStringTree(parser));
    }

    @Test
    void distinctCountKeepsItsPhysicalValueSource() {
        var lexer = new PostgresFullGrammarLexer(CharStreams.fromString(
                "SELECT COUNT(DISTINCT po.id) FROM purchase_orders po"));
        var parser = new PostgresFullGrammarParser(new CommonTokenStream(lexer));
        var root = parser.root();
        var analyses = new java.util.ArrayList<com.relationdetector.core.fullgrammar.FullGrammarExpressionAnalysis>();
        var adapter = new PostgresParseTreeAdapter();
        new PostgresFullGrammarParserBaseVisitor<Void>() {
            @Override
            public Void visitFunc_application(PostgresFullGrammarParser.Func_applicationContext ctx) {
                if ("COUNT".equalsIgnoreCase(ctx.func_name().getText())) {
                    analyses.add(new com.relationdetector.postgres.fullgrammar.common.PostgresExpressionAnalyzer(
                            adapter).analyze(ctx, "po"));
                }
                return visitChildren(ctx);
            }
        }.visit(root);

        assertTrue(analyses.stream().anyMatch(analysis ->
                        analysis.sourceAliases().equals(List.of("po"))
                                && analysis.sourceColumns().equals(List.of("id"))
                                && "AGGREGATE".equals(analysis.transformType())),
                () -> analyses + " tree=" + root.toStringTree(parser));
    }

    @Test
    void scalarProjectionAdapterReturnsOnlySelectedAggregateExpression() {
        String sql = """
                UPDATE supplier_products sp SET return_rate =
                  (SELECT SUM(pri.return_qty) FROM purchase_return_items pri
                   JOIN purchase_returns pr ON pr.id = pri.return_id
                   WHERE pr.supplier_id = sp.supplier_id)
                  / NULLIF((SELECT SUM(poi.received_qty) FROM purchase_order_items poi
                            JOIN purchase_orders po ON poi.order_id = po.id
                            WHERE po.supplier_id = sp.supplier_id), 0);
                """;
        var lexer = new PostgresFullGrammarLexer(CharStreams.fromString(sql));
        var parser = new PostgresFullGrammarParser(new CommonTokenStream(lexer));
        var root = parser.root();
        var adapter = new PostgresParseTreeAdapter();
        var analyses = new java.util.ArrayList<com.relationdetector.core.fullgrammar.FullGrammarExpressionAnalysis>();
        new PostgresFullGrammarParserBaseVisitor<Void>() {
            @Override
            public Void visitSelect_with_parens(PostgresFullGrammarParser.Select_with_parensContext ctx) {
                adapter.selectProjectionExpressions(ctx).forEach(expression -> analyses.add(
                        new com.relationdetector.postgres.fullgrammar.common.PostgresExpressionAnalyzer(adapter)
                                .analyze(expression, "")));
                return visitChildren(ctx);
            }
        }.visit(root);

        assertTrue(analyses.stream().anyMatch(value -> value.sourceColumns().equals(List.of("return_qty"))),
                () -> analyses + " tree=" + root.toStringTree(parser));
        assertTrue(analyses.stream().anyMatch(value -> value.sourceColumns().equals(List.of("received_qty"))),
                () -> analyses + " tree=" + root.toStringTree(parser));
        assertTrue(analyses.stream().allMatch(value -> value.sourceColumns().stream().allMatch(
                        column -> column.equals("return_qty") || column.equals("received_qty"))),
                () -> analyses + " tree=" + root.toStringTree(parser));
    }

    @Test
    void fullGrammarSeparatesScalarAggregateValueAndControls() {
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
        var structured = new FullGrammarDialectModule().sqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor().extract(statement, structured).stream()
                .map(PostgresFullGrammarExpressionAnalyzerTest::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains(
                        "VALUE:AGGREGATE:purchase_order_items.quantity->supplier_products.total_order_qty"),
                () -> fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.stream().anyMatch(value -> value.startsWith("CONTROL:CASE_WHEN:")
                        && value.contains("purchase_order_items.order_id")
                        && value.contains("purchase_orders.id")
                        && value.contains("supplier_products.product_id")
                        && value.endsWith("->supplier_products.total_order_qty")),
                () -> fingerprints + " events=" + structured.events());
    }

    @Test
    void analyzerReadsCaseExpressionThroughFullGrammarEvents() {
        var result = new FullGrammarDialectModule()
                .sqlParser()
                .parseSql(statement("""
                UPDATE users u
                SET risk_band = CASE WHEN u.risk_score > 80 THEN 'HIGH' ELSE u.risk_band END
                ;
                """), null);

        List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement("""
                        UPDATE users u
                        SET risk_band = CASE WHEN u.risk_score > 80 THEN 'HIGH' ELSE u.risk_band END
                        ;
                        """), result)
                .stream()
                .map(PostgresFullGrammarExpressionAnalyzerTest::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains("CONTROL:CASE_WHEN:users.risk_score->users.risk_band"),
                () -> fingerprints.toString());
        assertTrue(fingerprints.contains("VALUE:CASE_WHEN:users.risk_band->users.risk_band"),
                () -> fingerprints.toString());
    }

    @Test
    void nestedInSubqueryDoesNotBindInnerUnqualifiedColumnToMiddleScope() {
        SqlStatementRecord statement = statement("""
                SELECT a.account_id,
                       (SELECT count(*) FROM pg10_transactions
                        WHERE account_id IN (
                            SELECT account_id FROM pg10_accounts
                            WHERE branch_code = a.branch_code
                              AND risk_score <= a.risk_score
                        )
                       ) AS peer_branch_weekly_txns
                FROM pg10_accounts a;
                """);

        var structured = new FullGrammarDialectModule().sqlParser().parseSql(statement, null);
        List<RelationshipCandidate> relationships = new StructuredRelationshipExtractor().extract(statement, structured);

        assertTrue(relationships.stream().anyMatch(candidate ->
                fingerprint(candidate).equals("CO_OCCURRENCE:pg10_accounts.account_id->pg10_transactions.account_id:SQL_LOG_SUBQUERY_IN")));
        assertFalse(relationships.stream().anyMatch(candidate ->
                fingerprint(candidate).equals("CO_OCCURRENCE:pg10_transactions.branch_code->pg10_accounts.branch_code:SQL_LOG_JOIN")));
    }

    @Test
    void scalarProjectionSubqueryEmitsNestedJoinRelationship() {
        SqlStatementRecord statement = statement("""
                SELECT
                    pb.batch_no,
                    (SELECT string_agg(DISTINCT CONCAT(w.name, ':', i.quantity), ', ')
                     FROM inventory i
                     JOIN warehouses w ON i.warehouse_id = w.id
                     WHERE i.batch_id = pb.id AND i.quantity > 0) AS current_stock_distribution
                FROM product_batches pb;
                """);

        var structured = new FullGrammarDialectModule().sqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredRelationshipExtractor().extract(statement, structured)
                .stream()
                .map(PostgresFullGrammarExpressionAnalyzerTest::fingerprint)
                .toList();

        assertTrue(fingerprints.contains("CO_OCCURRENCE:inventory.warehouse_id->warehouses.id:SQL_LOG_JOIN"),
                () -> "Missing nested scalar subquery join. Actual=" + fingerprints);
    }

    @Test
    void analyzerMarksExpressionProjectionAsNonRelationColumnExpression() {
        var result = new FullGrammarDialectModule()
                .sqlParser()
                .parseSql(statement("""
                SELECT u.account_status
                FROM application_users u
                WHERE u.account_status IN (
                    SELECT 'STATUS_' || status_label
                    FROM structural_statuses
                );
                """), null);

        assertFalse(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.IN_SUBQUERY_PREDICATE
                        && "structural_statuses".equals(event.innerTable())));

    }

    @Test
    void analyzerClassifiesPostgresConcatOperatorAsConcatFormat() {
        var result = new FullGrammarDialectModule()
                .sqlParser()
                .parseSql(statement("""
                UPDATE order_ledgers l
                SET remarks = 'User risk level: ' || u.risk_level || ' | Order Rank: ' || fo.rnk
                FROM fraud_orders fo, users u
                WHERE l.order_id = fo.order_id
                  AND fo.user_id = u.id;
                """), null);

        var assignment = result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.UPDATE_ASSIGNMENT)
                .filter(event -> "remarks".equals(event.targetColumn()))
                .findFirst()
                .orElseThrow();

        assertEquals("CONCAT_FORMAT", assignment.expression().transformType().name());
        assertEquals(List.of("u", "fo"), assignment.expression().sourceAliases());
        assertEquals(List.of("risk_level", "rnk"), assignment.expression().sourceColumns());
    }

    @Test
    void fullGrammarPlainInsertSelectEmitsLineage() {
        SqlStatementRecord statement = statement("""
                INSERT INTO shipments (shipment_no, order_id, warehouse_id, to_address, receiver_phone)
                SELECT
                    CONCAT('SH-', CAST(so.id AS text)),
                    so.id,
                    so.warehouse_id,
                    c.address,
                    c.phone
                FROM sales_orders so
                JOIN customers c ON c.id = so.customer_id;
                """);

        var structured = new FullGrammarDialectModule().sqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor().extract(statement, structured).stream()
                .map(PostgresFullGrammarExpressionAnalyzerTest::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains("VALUE:CONCAT_FORMAT:sales_orders.id->shipments.shipment_no"),
                () -> "Full-grammar INSERT SELECT should map expression source to target. Actual="
                        + fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.contains("VALUE:DIRECT:sales_orders.id->shipments.order_id"),
                () -> "Full-grammar INSERT SELECT should map direct source to target. Actual="
                        + fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.contains("VALUE:DIRECT:sales_orders.warehouse_id->shipments.warehouse_id"),
                () -> "Full-grammar INSERT SELECT should map direct source to target. Actual="
                        + fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.contains("VALUE:DIRECT:customers.address->shipments.to_address"),
                () -> "Full-grammar INSERT SELECT should map joined source to target. Actual="
                        + fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.contains("VALUE:DIRECT:customers.phone->shipments.receiver_phone"),
                () -> "Full-grammar INSERT SELECT should map joined source to target. Actual="
                        + fingerprints + " events=" + structured.events());
    }

    @Test
    void wildcardCteLayersPreservePhysicalProjectionLineage() {
        SqlStatementRecord statement = statement("""
                MERGE INTO inventory_target t
                USING (
                    WITH aggregated AS (
                        SELECT s.sku, SUM(s.quantity_delta) AS total_qty_delta
                        FROM inventory_source s
                        GROUP BY s.sku
                    ),
                    validated AS (
                        SELECT a.* FROM aggregated a
                    ),
                    risk_assessed AS (
                        SELECT v.* FROM validated v
                    )
                    SELECT * FROM risk_assessed
                ) s
                ON t.sku = s.sku
                WHEN MATCHED THEN UPDATE SET
                    quantity = t.quantity + s.total_qty_delta;
                """);

        var structured = new FullGrammarDialectModule().sqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor().extract(statement, structured).stream()
                .map(PostgresFullGrammarExpressionAnalyzerTest::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.stream().anyMatch(fingerprint ->
                        fingerprint.contains("inventory_source.total_qty_delta")
                                && fingerprint.endsWith("->inventory_target.quantity")),
                () -> "Typed wildcard projections must preserve the anchored CTE output. Actual="
                        + fingerprints + " events=" + structured.events());
    }

    private static SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, Map.of());
    }

    private static String fingerprint(RelationshipCandidate candidate) {
        EvidenceType evidenceType = candidate.evidence().isEmpty() ? null : candidate.evidence().get(0).type();
        return candidate.relationType() + ":"
                + candidate.source().displayName() + "->" + candidate.target().displayName()
                + ":" + evidenceType;
    }

    private static String lineageFingerprint(DataLineageCandidate lineage) {
        return lineage.flowKind() + ":"
                + lineage.transformType() + ":"
                + lineage.sources().stream()
                        .map(com.relationdetector.contracts.model.Endpoint::displayName)
                        .collect(java.util.stream.Collectors.joining(","))
                + "->" + lineage.target().displayName();
    }

}
