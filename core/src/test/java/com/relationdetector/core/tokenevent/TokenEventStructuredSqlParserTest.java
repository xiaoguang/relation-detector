package com.relationdetector.core.tokenevent;

import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.lineage.*;
import com.relationdetector.core.relation.*;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;

/**
 * TDD coverage for the token-event SQL parser layer.
 *
 * <p>The token-event parser still uses ANTLR underneath for lexer/parser
 * support, but this test guards the business event layer: the parser must
 * produce structured events, the relation extractor must preserve output, and
 * unresolved dynamic SQL must be visible to operators instead of disappearing.
 */
class TokenEventStructuredSqlParserTest {
    @Test
    void antlrParserEmitsTableAndColumnEqualityEvents() {
        String sql = "SELECT * FROM orders o JOIN users u ON o.user_id = u.id";

        StructuredParseResult result = new TokenEventStructuredSqlParser(SqlDialect.MYSQL)
                .parseSql(record(sql, StatementSourceType.PLAIN_SQL), null);

        assertEquals("ANTLR_COMMON_TOKEN_EVENT", result.backend());
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.ROWSET_REFERENCE
                        && "orders".equals(event.attributes().get("table"))
                        && "o".equals(event.attributes().get("alias"))));
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.PREDICATE_EQUALITY
                        && "o".equals(event.attributes().get("leftAlias"))
                        && "user_id".equals(event.attributes().get("leftColumn"))
                        && "u".equals(event.attributes().get("rightAlias"))
                        && "id".equals(event.attributes().get("rightColumn"))));
    }

    @Test
    void relationExtractionVisitorResolvesLineageAwareRelations() {
        String sql = """
                WITH recent_orders AS (
                  SELECT o.id, o.user_id FROM orders o
                )
                SELECT *
                FROM recent_orders ro
                JOIN users u ON ro.user_id = u.id
                """;
        SqlStatementRecord statement = record(sql, StatementSourceType.VIEW);
        StructuredParseResult result = new TokenEventStructuredSqlParser(SqlDialect.POSTGRES).parseSql(statement, null);

        List<RelationshipCandidate> relations = new TokenEventRelationExtractor().extract(statement, result);

        assertTrue(relations.stream().anyMatch(relation ->
                relation.source().displayName().equals("orders.user_id")
                        && relation.target().displayName().equals("users.id")),
                () -> "ANTLR extraction must resolve lineage-aware relations: " + relations);
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

        StructuredParseResult result = new TokenEventStructuredSqlParser(SqlDialect.MYSQL)
                .parseSql(record(sql, StatementSourceType.PROCEDURE), null);

        WarningMessage warning = result.warnings().stream()
                .filter(w -> w.code().equals("DYNAMIC_SQL_UNRESOLVED"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected dynamic SQL warning"));
        assertTrue(String.valueOf(warning.attributes().get("rawStatement")).contains("PREPARE stmt"));
        assertEquals("PROCEDURE", warning.attributes().get("statementSourceType"));
    }

    @Test
    void antlrSqlRelationParserReturnsRelationExtractorOutputDirectly() {
        String sql = "SELECT * FROM orders o JOIN users u ON o.user_id = u.id";
        SqlStatementRecord statement = record(sql, StatementSourceType.NATIVE_LOG);

        TokenEventSqlRelationParser parser = new TokenEventSqlRelationParser(
                new TokenEventStructuredSqlParser(SqlDialect.MYSQL),
                new TokenEventRelationExtractor());

        List<RelationshipCandidate> relationships = parser.parse(statement, null);

        assertTrue(relationships.stream().anyMatch(relation ->
                relation.source().displayName().equals("orders.user_id")
                        && relation.target().displayName().equals("users.id")));
    }

    private SqlStatementRecord record(String sql, StatementSourceType sourceType) {
        return new SqlStatementRecord(sql, sourceType, "antlr-test.sql", 1, 1, java.util.Map.of());
    }
}
