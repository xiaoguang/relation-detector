package com.relationdetector.postgres;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.postgres.tokenevent.PostgresTokenEventStructuredSqlParser;

class PostgresSelfUpdateLineageTest {
    @Test
    void tokenAndAllFullProfilesResolveUnqualifiedArithmeticSelfUpdate() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                UPDATE purchase_order_items
                SET received_qty = received_qty + v_accepted_qty
                WHERE id = p_order_item_id;
                """, StatementSourceType.PLAIN_SQL, "postgres-self-update.sql", 1, 3, Map.of());
        List<StructuredSqlParser> parsers = List.of(
                new PostgresTokenEventStructuredSqlParser(),
                new com.relationdetector.postgres.fullgrammar.v16.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.postgres.fullgrammar.v17.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.postgres.fullgrammar.v18.FullGrammarDialectModule().sqlParser());

        for (StructuredSqlParser parser : parsers) {
            var result = parser.parseSql(statement, null);
            var lineages = new StructuredDataLineageExtractor().extract(statement, result);
            assertTrue(lineages.stream().anyMatch(lineage ->
                            lineage.flowKind() == LineageFlowKind.VALUE
                                    && lineage.transformType() == LineageTransformType.ARITHMETIC
                                    && "purchase_order_items.received_qty".equals(lineage.target().displayName())
                                    && lineage.sources().stream().anyMatch(source ->
                                    "purchase_order_items.received_qty".equals(source.displayName()))),
                    () -> parser.getClass().getSimpleName() + " missing self-update; lineages=" + lineages
                            + " events=" + result.events());
        }
    }

    @Test
    void tokenAndAllFullProfilesKeepOuterArithmeticAroundCaseSelfUpdate() {
        assertSelfUpdateAcrossParsers("""
                UPDATE sales_orders
                SET paid_amount = paid_amount
                    + CASE WHEN p_status = 'paid' THEN p_amount ELSE 0.00 END
                WHERE id = p_order_id;
                """, "sales_orders", "paid_amount");
    }

    @Test
    void tokenAndAllFullProfilesKeepTopLevelCaseWhenBranchesContainArithmetic() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO shipments (delivered_at)
                SELECT CASE
                         WHEN so.status = 'delivered' THEN so.order_date + INTERVAL '2 days'
                         ELSE so.order_date
                       END
                FROM sales_orders so;
                """, StatementSourceType.PLAIN_SQL, "postgres-top-level-case.sql", 1, 7, Map.of());

        for (StructuredSqlParser parser : parsers()) {
            var result = parser.parseSql(statement, null);
            var lineages = new StructuredDataLineageExtractor().extract(statement, result);
            assertTrue(lineages.stream().anyMatch(lineage ->
                            lineage.flowKind() == LineageFlowKind.VALUE
                                    && lineage.transformType() == LineageTransformType.CASE_WHEN
                                    && "shipments.delivered_at".equals(lineage.target().displayName())
                                    && lineage.sources().stream().anyMatch(source ->
                                    "sales_orders.order_date".equals(source.displayName()))),
                    () -> parser.getClass().getSimpleName()
                            + " promoted branch arithmetic over top-level CASE; lineages=" + lineages
                            + " events=" + result.events());
        }
    }

    private void assertSelfUpdateAcrossParsers(String sql, String table, String column) {
        SqlStatementRecord statement = new SqlStatementRecord(
                sql, StatementSourceType.PLAIN_SQL, "postgres-case-self-update.sql", 1, 4, Map.of());
        for (StructuredSqlParser parser : parsers()) {
            var result = parser.parseSql(statement, null);
            var lineages = new StructuredDataLineageExtractor().extract(statement, result);
            assertTrue(lineages.stream().anyMatch(lineage ->
                            lineage.flowKind() == LineageFlowKind.VALUE
                                    && lineage.transformType() == LineageTransformType.ARITHMETIC
                                    && (table + "." + column).equals(lineage.target().displayName())
                                    && lineage.sources().stream().anyMatch(source ->
                                    (table + "." + column).equals(source.displayName()))),
                    () -> parser.getClass().getSimpleName() + " missing CASE arithmetic self-update; lineages="
                            + lineages + " events=" + result.events());
        }
    }

    private List<StructuredSqlParser> parsers() {
        return List.of(
                new PostgresTokenEventStructuredSqlParser(),
                new com.relationdetector.postgres.fullgrammar.v16.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.postgres.fullgrammar.v17.FullGrammarDialectModule().sqlParser(),
                new com.relationdetector.postgres.fullgrammar.v18.FullGrammarDialectModule().sqlParser());
    }
}
