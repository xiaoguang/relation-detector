package com.relationdetector.cli;

import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.fullgrammer.*;
import com.relationdetector.core.tokenevent.*;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

class FullGrammerTokenEventStructuredSqlParserTest {
    @Test
    void shadowParserCarriesGrammarProfileAndKeepsTokenEventShape() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                UPDATE products p
                JOIN shops s ON p.shop_id = s.id
                SET p.audit_shop_id = s.id
                """, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 3, java.util.Map.of());

        StructuredParseResult result = FullGrammerTokenEventParserFactory.create(
                        DatabaseType.MYSQL,
                        "8.0.36",
                        new TokenEventStructuredSqlParser(SqlDialect.MYSQL))
                .parser()
                .parseSql(statement, null);

        assertEquals("FULL_GRAMMAR_TOKEN_EVENT_SHADOW", result.backend());
        assertEquals("mysql-8.0", result.attributes().get("grammarProfile"));
        assertEquals(true, result.attributes().get("fullGrammerShadow"));
        assertEquals("MYSQL_FULL_GRAMMAR_PARSE_TREE_VISITOR", result.attributes().get("fullGrammerImplementation"));
        Set<String> eventTypes = result.events().stream()
                .map(StructuredSqlEvent::type)
                .map(Enum::name)
                .collect(Collectors.toSet());
        assertTrue(eventTypes.contains("ROWSET_REFERENCE"));
        assertTrue(eventTypes.contains("PREDICATE_EQUALITY"));
        assertTrue(eventTypes.contains("UPDATE_ASSIGNMENT"));
    }

    @Test
    void fullGrammerParserDoesNotCallTokenEventFallbackForEvents() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                SELECT *
                FROM orders o
                JOIN users u ON o.user_id = u.id
                """, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 4, java.util.Map.of());

        StructuredParseResult result = FullGrammerTokenEventParserFactory.create(
                        DatabaseType.MYSQL,
                        "8.0.36",
                        (sqlStatement, context) -> {
                            throw new AssertionError("token-event fallback parser must not be called for full-grammer events");
                        })
                .parser()
                .parseSql(statement, null);

        assertEquals("FULL_GRAMMAR_TOKEN_EVENT_SHADOW", result.backend());
        assertTrue(result.events().stream().anyMatch(event -> event.type().name().equals("PREDICATE_EQUALITY")));
        assertTrue(((java.util.List<?>) result.attributes().get("fullGrammerDelegatedEventTypes")).isEmpty());
    }

    @Test
    void fullGrammerSyntaxErrorReturnsDelegateEventsAndWarning() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "SELECT * FROM orders o JOIN users u ON o.user_id = u.id @@@",
                StatementSourceType.PLAIN_SQL,
                "bad.sql",
                1,
                1,
                java.util.Map.of());

        StructuredParseResult result = FullGrammerTokenEventParserFactory.create(
                        DatabaseType.MYSQL,
                        "8.0.36",
                        new TokenEventStructuredSqlParser(SqlDialect.MYSQL))
                .parser()
                .parseSql(statement, null);

        assertTrue(result.events().stream().anyMatch(event -> event.type().name().equals("PREDICATE_EQUALITY")));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.code().equals("FULL_GRAMMAR_SQL_PARSE_WARNING")));
        assertTrue(((Number) result.attributes().get("fullGrammerSyntaxErrors")).intValue() > 0);
    }
}
