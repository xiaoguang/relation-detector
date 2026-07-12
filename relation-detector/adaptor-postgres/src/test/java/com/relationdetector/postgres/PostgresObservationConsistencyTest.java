package com.relationdetector.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.ScriptFrameRequest;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.provenance.SemanticObservationFingerprint;
import com.relationdetector.core.relation.StructuredRelationshipExtractor;
import com.relationdetector.postgres.fullgrammar.v18.FullGrammarDialectModule;
import com.relationdetector.postgres.script.PostgresScriptFramer;
import com.relationdetector.postgres.tokenevent.PostgresRelationSqlLexer;
import com.relationdetector.postgres.tokenevent.PostgresRelationSqlParser;
import com.relationdetector.postgres.tokenevent.PostgresTokenEventStructuredSqlParser;

class PostgresObservationConsistencyTest {
    private final StructuredSqlParser token = new PostgresTokenEventStructuredSqlParser();
    private final StructuredSqlParser full = new FullGrammarDialectModule().sqlParser();

    @Test
    void joinNestedExistsAndInHaveTheSameSemanticObservations() {
        assertConsistent("""
                SELECT d.id
                FROM departments d
                LEFT JOIN inventory i ON EXISTS (
                    SELECT 1
                    FROM warehouses w
                    WHERE w.manager_id IN (
                        SELECT id FROM employees WHERE department_id = d.id
                    )
                      AND i.warehouse_id = w.id
                );
                """);
    }

    @Test
    void unaryAggregateInUnionDoesNotDiscardLaterJoinObservations() {
        assertConsistent("""
                WITH waterfall AS (
                    SELECT SUM(soi.amount) AS amount
                    FROM sales_order_items soi
                    JOIN sales_orders so ON soi.order_id = so.id
                    UNION ALL
                    SELECT -SUM(dri.loss_amount)
                    FROM damage_report_items dri
                    JOIN damage_reports dr ON dri.report_id = dr.id
                )
                SELECT amount FROM waterfall;
                """);
    }

    @Test
    void nestedScalarSubqueriesInsideArithmeticFunctionRemainTraversable() {
        assertConsistent("""
                SELECT ROUND(
                    (SELECT SUM(soi.amount - soi.quantity * p.purchase_price)
                     FROM sales_order_items soi
                     JOIN sales_orders so ON soi.order_id = so.id
                     JOIN products p ON soi.product_id = p.id)
                    - COALESCE((SELECT SUM(dri.loss_amount)
                                FROM damage_report_items dri
                                JOIN damage_reports dr ON dri.report_id = dr.id), 0)
                , 2);
                """);
    }

    @Test
    void nestedScalarSubqueriesWithFiltersRemainTraversable() {
        assertConsistent("""
                SELECT ROUND(
                    (SELECT SUM(soi.amount - soi.quantity * p.purchase_price) - SUM(so.discount_amount)
                     FROM sales_order_items soi
                     JOIN sales_orders so ON soi.order_id = so.id
                     JOIN products p ON soi.product_id = p.id
                     WHERE so.order_date >= CURRENT_DATE - INTERVAL '12 months'
                       AND so.status NOT IN ('draft', 'cancelled'))
                    - COALESCE((SELECT SUM(sr.refund_amount - sr.restock_fee)
                                FROM sales_returns sr
                                WHERE sr.status NOT IN ('rejected')), 0)
                    - COALESCE((SELECT SUM(dri.loss_amount)
                                FROM damage_report_items dri
                                JOIN damage_reports dr ON dri.report_id = dr.id
                                WHERE dr.status IN ('approved', 'executed')), 0)
                , 2);
                """);
    }

    @Test
    void naturalWaterfallQueryHasTheSameSemanticObservations() throws Exception {
        Path path = Path.of("..", "sample-data", "postgres", "18", "04-queries",
                "09-real-world-scenarios.sql");
        String sql = Files.readString(path);
        SqlStatementRecord statement = new PostgresScriptFramer().frame(new ScriptFrameRequest(
                        sql, path.toString(), StatementSourceType.PLAIN_SQL))
                .statements().stream()
                .filter(candidate -> candidate.startLine() == 765)
                .findFirst()
                .orElseThrow();

        assertConsistent(statement);
    }

    private void assertConsistent(String sql) {
        SqlStatementRecord statement = new SqlStatementRecord(
                sql, StatementSourceType.PLAIN_SQL, "postgres-observation-consistency.sql",
                1, sql.lines().count(), Map.of());
        assertConsistent(statement);
    }

    private void assertConsistent(SqlStatementRecord statement) {
        List<SemanticObservationFingerprint> tokenObservations = observations(token, statement);
        List<SemanticObservationFingerprint> fullObservations = observations(full, statement);

        assertFalse(tokenObservations.isEmpty(), "The compact parser must produce typed observations");
        assertEquals(tokenObservations, fullObservations,
                () -> "fallbacks=" + selectItemFallbacks(statement.sql())
                        + " token=" + tokenObservations + " full=" + fullObservations);
    }

    private List<SemanticObservationFingerprint> observations(
            StructuredSqlParser parser,
            SqlStatementRecord statement
    ) {
        var structured = parser.parseSql(statement, null);
        return new StructuredRelationshipExtractor().extract(statement, structured).stream()
                .flatMap(candidate -> SemanticObservationFingerprint.relationships(candidate).stream())
                .sorted()
                .toList();
    }

    private List<String> selectItemFallbacks(String sql) {
        var parser = new PostgresRelationSqlParser(new CommonTokenStream(
                new PostgresRelationSqlLexer(CharStreams.fromString(sql))));
        List<String> result = new java.util.ArrayList<>();
        collectFallbacks(parser.script(), result);
        return result;
    }

    private void collectFallbacks(ParseTree tree, List<String> result) {
        if (tree instanceof PostgresRelationSqlParser.SelectItemFallbackContext fallback) {
            result.add(fallback.getText());
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectFallbacks(tree.getChild(index), result);
        }
    }
}
