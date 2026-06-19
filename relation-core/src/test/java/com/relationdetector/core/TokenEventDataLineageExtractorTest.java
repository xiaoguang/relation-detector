package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.relationdetector.api.DataLineageCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.Enums.StatementSourceType;

class TokenEventDataLineageExtractorTest {
    private final TokenEventDataLineageExtractor extractor = new TokenEventDataLineageExtractor();

    @Test
    void extractsAggregateLineageThroughDerivedUpdate() {
        String sql = """
                UPDATE users u
                LEFT JOIN (
                    SELECT user_id, SUM(pay_amount) AS actual_total
                    FROM orders
                    GROUP BY user_id
                ) o_summary ON u.id = o_summary.user_id
                SET u.total_spent = COALESCE(o_summary.actual_total, 0.00)
                """;

        assertEquals(List.of("VALUE:AGGREGATE:orders.pay_amount->users.total_spent"),
                lineageFingerprints(sql, SqlDialect.MYSQL));
    }

    @Test
    void extractsInsertSelectLineageByTargetColumnPosition() {
        String sql = """
                INSERT INTO user_spending_snapshots (user_id, total_spent)
                SELECT u.id, SUM(o.pay_amount)
                FROM users u
                JOIN orders o ON o.user_id = u.id
                GROUP BY u.id
                """;

        assertEquals(List.of(
                        "VALUE:DIRECT:users.id->user_spending_snapshots.user_id",
                        "VALUE:AGGREGATE:orders.pay_amount->user_spending_snapshots.total_spent"),
                lineageFingerprints(sql, SqlDialect.POSTGRES));
    }

    @Test
    void extractsMergeInsertValuesLineage() {
        String sql = """
                MERGE INTO target_orders AS t
                USING source_orders AS s
                ON t.source_order_id = s.id
                WHEN NOT MATCHED THEN
                  INSERT (source_order_id) VALUES (s.id)
                """;

        assertEquals(List.of("VALUE:DIRECT:source_orders.id->target_orders.source_order_id"),
                lineageFingerprints(sql, SqlDialect.POSTGRES));
    }

    @Test
    void skipsLineageWhoseTargetIsExplicitProcedureLocalTemporaryTable() {
        String sql = """
                CREATE PROCEDURE rebuild_tmp()
                BEGIN
                    CREATE TEMPORARY TABLE tmp_rollup (order_amount DECIMAL(10,2));
                    INSERT INTO tmp_rollup (order_amount)
                    SELECT o.amount
                    FROM orders o;
                END
                """;

        assertTrue(lineageFingerprints(sql, SqlDialect.MYSQL).isEmpty());
    }

    private List<String> lineageFingerprints(String sql, SqlDialect dialect) {
        SqlStatementRecord statement = new SqlStatementRecord(
                sql,
                StatementSourceType.PLAIN_SQL,
                "token-event-lineage-unit.sql",
                1,
                1,
                Map.of());
        return extractor.extract(statement, new TokenEventStructuredSqlParser(dialect).parseSql(statement, null))
                .stream()
                .map(TokenEventDataLineageExtractorTest::fingerprint)
                .toList();
    }

    private static String fingerprint(DataLineageCandidate candidate) {
        return candidate.flowKind() + ":"
                + candidate.transformType() + ":"
                + candidate.sources().stream()
                        .map(com.relationdetector.api.Endpoint::displayName)
                        .collect(Collectors.joining(","))
                + "->" + candidate.target().displayName();
    }
}
