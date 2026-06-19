package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.StructuredSqlEvent;
import com.relationdetector.api.Enums.StatementSourceType;
import com.relationdetector.api.Enums.StructuredParseEventType;

class TokenEventSqlNoiseAndUsingTest {
    private final TokenEventStructuredSqlParser parser = new TokenEventStructuredSqlParser(SqlDialect.MYSQL);
    private final TokenEventRelationExtractor visitor = new TokenEventRelationExtractor();
    private final TokenEventStructuredSqlParser tokenEventParser = new TokenEventStructuredSqlParser(SqlDialect.MYSQL);
    private final TokenEventRelationExtractor tokenEventVisitor = new TokenEventRelationExtractor();

    @Test
    void joinUsingColumnsAreNotExtractedAsTableReferencesButRemainTableCoOccurrenceEvidence() {
        SqlStatementRecord statement = statement("""
                SELECT *
                FROM orders o
                JOIN order_tags ot USING (order_id)
                """);

        StructuredParseResult structured = parser.parseSql(statement, null);
        List<String> tables = tableNames(structured.events());

        assertTrue(tables.contains("orders"));
        assertTrue(tables.contains("order_tags"));
        assertFalse(tables.contains("order_id"), "JOIN USING column names must not become TABLE_REFERENCE events");
        assertEquals(List.of("CO_OCCURRENCE:orders.order_id->order_tags.order_id:SQL_LOG_COLUMN_CO_OCCURRENCE"),
                visitor.extract(statement, structured).stream().map(this::relationFingerprint).toList(),
                "JOIN USING should produce precise column-level weak co-occurrence, not legacy table-level co-occurrence");
    }

    @Test
    void deleteAndMergeUsingStillExtractRealSourceTables() {
        StructuredParseResult deleteStructured = parser.parseSql(statement("""
                DELETE FROM o
                USING orders o
                JOIN users u ON o.user_id = u.id
                WHERE u.id IS NULL
                """), null);
        StructuredParseResult mergeStructured = parser.parseSql(statement("""
                MERGE INTO target_orders t
                USING source_orders s
                ON t.source_order_id = s.id
                WHEN MATCHED THEN UPDATE SET synced_at = CURRENT_TIMESTAMP
                """), null);

        assertTrue(tableNames(deleteStructured.events()).containsAll(List.of("orders", "users")));
        assertTrue(tableNames(mergeStructured.events()).containsAll(List.of("target_orders", "source_orders")));
    }

    @Test
    void systemSchemaReferencesAndTruncatedTokensDoNotBecomeRelationships() {
        SqlStatementRecord systemStatement = statement("""
                SELECT *
                FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE k
                JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS t USING (CONSTRAINT_NAME, TABLE_NAME)
                """);
        SqlStatementRecord truncatedStatement = statement("""
                SELECT *
                FROM users u
                JOIN INFORMATI... i ON u.id = i.user_id
                """);

        assertTrue(visitor.extract(systemStatement, parser.parseSql(systemStatement, null)).isEmpty(),
                "system catalog tables must not produce business relationships");
        List<RelationshipCandidate> truncatedRelations = visitor.extract(truncatedStatement, parser.parseSql(truncatedStatement, null));
        assertTrue(truncatedRelations.stream().noneMatch(relation ->
                        relation.source().displayName().contains("INFORMATI")
                                || relation.target().displayName().contains("INFORMATI")),
                () -> "truncated tokens must not become relationship endpoints: " + truncatedRelations);
    }

    @Test
    void tokenEventV2CoversNoiseAndUsingWithoutLegacyTableCoOccurrence() {
        SqlStatementRecord usingStatement = statement("""
                SELECT *
                FROM orders o
                JOIN order_tags ot USING (order_id)
                """);
        StructuredParseResult usingStructured = tokenEventParser.parseSql(usingStatement, null);

        assertTrue(tableNames(usingStructured.events()).contains("orders"));
        assertTrue(tableNames(usingStructured.events()).contains("order_tags"));
        assertFalse(tableNames(usingStructured.events()).contains("order_id"),
                "Token/Event must not treat JOIN USING column names as tables");
        assertEquals(List.of("CO_OCCURRENCE:orders.order_id->order_tags.order_id:SQL_LOG_COLUMN_CO_OCCURRENCE"),
                tokenEventVisitor.extract(usingStatement, usingStructured).stream().map(this::relationFingerprint).toList(),
                "Token/Event golden now keeps JOIN USING as precise column-level co-occurrence");

        SqlStatementRecord systemStatement = statement("""
                SELECT *
                FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE k
                JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS t USING (CONSTRAINT_NAME, TABLE_NAME)
                """);
        assertTrue(tokenEventVisitor.extract(systemStatement, tokenEventParser.parseSql(systemStatement, null)).isEmpty(),
                "Token/Event must not produce business relationships from system catalog references");
    }

    private SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "antlr-noise.sql", 1, sql.lines().count(), Map.of());
    }

    private List<String> tableNames(List<StructuredSqlEvent> events) {
        return events.stream()
                .filter(event -> event.type() == StructuredParseEventType.TABLE_REFERENCE)
                .map(event -> String.valueOf(event.attributes().get("table")))
                .collect(Collectors.toList());
    }

    private String relationFingerprint(RelationshipCandidate candidate) {
        return candidate.relationType() + ":"
                + candidate.source().displayName() + "->"
                + candidate.target().displayName() + ":"
                + candidate.evidence().get(0).type();
    }
}
