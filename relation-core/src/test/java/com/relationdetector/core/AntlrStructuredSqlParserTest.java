package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.StructuredSqlEvent;
import com.relationdetector.api.WarningMessage;
import com.relationdetector.api.Enums.StatementSourceType;
import com.relationdetector.api.Enums.StructuredParseEventType;

/**
 * TDD coverage for the ANTLR migration layer.
 *
 * <p>The first ANTLR implementation is intentionally a tolerant structural
 * frontend. These tests lock down the behavior we need before replacing the
 * lightweight parser rule-by-rule: the parser must produce structured events,
 * the relation extractor must preserve existing relationship output, and
 * unresolved dynamic SQL must be visible to operators instead of disappearing.
 */
class AntlrStructuredSqlParserTest {
    @Test
    void antlrParserEmitsTableAndColumnEqualityEvents() {
        String sql = "SELECT * FROM orders o JOIN users u ON o.user_id = u.id";

        StructuredParseResult result = new AntlrStructuredSqlParser(SqlDialect.MYSQL)
                .parseSql(record(sql, StatementSourceType.PLAIN_SQL), null);

        assertEquals("ANTLR", result.backend());
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.TABLE_REFERENCE
                        && "orders".equals(event.attributes().get("table"))
                        && "o".equals(event.attributes().get("alias"))));
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.COLUMN_EQUALITY
                        && "o".equals(event.attributes().get("leftAlias"))
                        && "user_id".equals(event.attributes().get("leftColumn"))
                        && "u".equals(event.attributes().get("rightAlias"))
                        && "id".equals(event.attributes().get("rightColumn"))));
    }

    @Test
    void relationExtractionVisitorPreservesSimpleParserRelations() {
        String sql = """
                WITH recent_orders AS (
                  SELECT o.id, o.user_id FROM orders o
                )
                SELECT *
                FROM recent_orders ro
                JOIN users u ON ro.user_id = u.id
                """;
        SqlStatementRecord statement = record(sql, StatementSourceType.VIEW);
        StructuredParseResult result = new AntlrStructuredSqlParser(SqlDialect.POSTGRES).parseSql(statement, null);

        List<RelationshipCandidate> relations = new RelationExtractionVisitor().extract(statement, result);

        assertTrue(relations.stream().anyMatch(relation ->
                relation.source().displayName().equals("orders.user_id")
                        && relation.target().displayName().equals("users.id")),
                () -> "ANTLR extraction must preserve lineage-aware relations from the current parser: " + relations);
    }

    @Test
    void dynamicSqlIsReportedAsUnresolvedWarning() {
        String sql = """
                CREATE PROCEDURE rebuild_dynamic()
                BEGIN
                  SET @s = 'SELECT * FROM orders o JOIN users u ON o.user_id = u.id';
                  PREPARE stmt FROM @s;
                  EXECUTE stmt;
                END
                """;

        StructuredParseResult result = new AntlrStructuredSqlParser(SqlDialect.MYSQL)
                .parseSql(record(sql, StatementSourceType.PROCEDURE), null);

        WarningMessage warning = result.warnings().stream()
                .filter(w -> w.code().equals("DYNAMIC_SQL_UNRESOLVED"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected dynamic SQL warning"));
        assertTrue(String.valueOf(warning.attributes().get("rawStatement")).contains("PREPARE stmt"));
        assertEquals("PROCEDURE", warning.attributes().get("statementSourceType"));
    }

    @Test
    void shadowParserKeepsPrimaryOutputAndAddsComparisonEvent() {
        String sql = "SELECT * FROM orders o JOIN users u ON o.user_id = u.id";
        SqlStatementRecord statement = record(sql, StatementSourceType.NATIVE_LOG);

        ShadowSqlRelationParser parser = new ShadowSqlRelationParser(
                new SimpleSqlRelationParser(),
                new AntlrStructuredSqlParser(SqlDialect.MYSQL),
                new RelationExtractionVisitor());

        ShadowSqlRelationParser.Result result = parser.parseWithDiagnostics(statement, null);

        assertFalse(result.relationships().isEmpty());
        assertEquals(result.relationships().size(), result.primaryCount());
        assertTrue(result.diagnostics().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.PARSER_COMPARISON
                        && Integer.valueOf(result.primaryCount()).equals(event.attributes().get("primaryCount"))
                        && Integer.valueOf(result.shadowCount()).equals(event.attributes().get("shadowCount"))));
    }

    private SqlStatementRecord record(String sql, StatementSourceType sourceType) {
        return new SqlStatementRecord(sql, sourceType, "antlr-test.sql", 1, 1, java.util.Map.of());
    }
}
