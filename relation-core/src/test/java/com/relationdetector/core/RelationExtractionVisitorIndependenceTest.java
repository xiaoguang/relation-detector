package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.StructuredSqlEvent;
import com.relationdetector.api.Enums.RelationType;
import com.relationdetector.api.Enums.StatementSourceType;
import com.relationdetector.api.Enums.StructuredParseEventType;

/**
 * Guards the most important ANTLR-primary migration boundary.
 *
 * <p>These tests deliberately give {@link RelationExtractionVisitor} structured
 * events that do not match the raw SQL text. The visitor is allowed to use its
 * own text-level fallback for ANTLR gaps, but it must not delegate to
 * {@link SimpleSqlRelationParser}; the evidence details below make that boundary
 * visible.
 */
class RelationExtractionVisitorIndependenceTest {
    @Test
    void extractsRelationsFromStructuredEventsWhenRawSqlHasNoJoin() {
        SqlStatementRecord statement = record("SELECT 1");
        StructuredParseResult structured = structured(List.of(
                table("FROM", "orders", "o", 1),
                table("JOIN", "users", "u", 1),
                equality("o", "user_id", "u", "id", 1)
        ));

        List<RelationshipCandidate> relations = new RelationExtractionVisitor().extract(statement, structured);

        assertEquals(1, relations.size(), () -> "Expected event-derived relation, got: " + relations);
        RelationshipCandidate relation = relations.get(0);
        assertEquals(RelationType.FK_LIKE, relation.relationType());
        assertEquals("orders.user_id", relation.source().displayName());
        assertEquals("users.id", relation.target().displayName());
        assertTrue(relation.evidence().get(0).detail().contains("ANTLR equality"));
    }

    @Test
    void emptyStructuredEventsUseVisitorFallbackRatherThanSimpleParser() {
        SqlStatementRecord statement = record("SELECT * FROM orders o JOIN users u ON o.user_id = u.id");
        StructuredParseResult structured = structured(List.of());

        List<RelationshipCandidate> relations = new RelationExtractionVisitor().extract(statement, structured);

        assertEquals(1, relations.size(), () -> "Expected visitor-owned raw equality fallback: " + relations);
        assertTrue(relations.get(0).evidence().get(0).detail().contains("ANTLR raw equality"),
                () -> "Empty events must not delegate to Simple parser evidence: " + relations.get(0).evidence());
    }

    private SqlStatementRecord record(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "independence.sql", 1, 1, Map.of());
    }

    private StructuredParseResult structured(List<StructuredSqlEvent> events) {
        return new StructuredParseResult("ANTLR", "MYSQL", "independence.sql", events, List.of(), Map.of());
    }

    private StructuredSqlEvent table(String keyword, String table, String alias, long line) {
        return new StructuredSqlEvent(StructuredParseEventType.TABLE_REFERENCE, "independence.sql", line,
                Map.of("keyword", keyword, "qualifiedTable", table, "table", table, "alias", alias));
    }

    private StructuredSqlEvent equality(String leftAlias, String leftColumn, String rightAlias, String rightColumn, long line) {
        return new StructuredSqlEvent(StructuredParseEventType.COLUMN_EQUALITY, "independence.sql", line,
                Map.of("leftAlias", leftAlias, "leftColumn", leftColumn,
                        "rightAlias", rightAlias, "rightColumn", rightColumn));
    }
}
