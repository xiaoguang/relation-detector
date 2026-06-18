package com.relationdetector.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.relationdetector.api.Collectors.SqlRelationParser;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.Enums.StatementSourceType;
import com.relationdetector.api.Enums.StructuredParseEventType;
import com.relationdetector.core.ShadowSqlRelationParser;

/**
 * Verifies that PostgreSQL owns PostgreSQL-flavored ANTLR parser selection.
 */
class PostgresAntlrParserSelectionTest {
    @Test
    void adaptorExposesPostgresAntlrSqlAndDdlParsers() {
        PostgresDatabaseAdaptor adaptor = new PostgresDatabaseAdaptor();

        assertEquals(PostgresAntlrSqlParser.class, adaptor.structuredSqlParser().orElseThrow().getClass());
        assertEquals(PostgresAntlrDdlParser.class, adaptor.structuredDdlParser().orElseThrow().getClass());
    }

    @Test
    void postgresAntlrSqlParserAcceptsDoubleQuotedJoin() {
        StructuredParseResult result = new PostgresAntlrSqlParser().parseSql(new SqlStatementRecord(
                "SELECT * FROM \"orders\" o JOIN \"users\" u ON o.\"user_id\" = u.\"id\"",
                StatementSourceType.PLAIN_SQL,
                "postgres.sql",
                1,
                1,
                java.util.Map.of()), null);

        assertTrue(result.events().stream().anyMatch(event ->
                "orders".equals(event.attributes().get("table"))
                        && "o".equals(event.attributes().get("alias"))));
    }

    @Test
    void postgresAntlrSqlParserUsesPostgresGrammarAndStructuredEventVisitor() {
        StructuredParseResult result = new PostgresAntlrSqlParser().parseSql(new SqlStatementRecord(
                "SELECT * FROM \"orders\" o JOIN \"users\" u ON o.\"user_id\" = u.\"id\"",
                StatementSourceType.PLAIN_SQL,
                "postgres.sql",
                1,
                1,
                java.util.Map.of()), null);

        assertEquals("PostgresRelationSql", result.attributes().get("grammar"));
        assertEquals("PostgresRelationSqlLexer", result.attributes().get("lexer"));
        assertEquals("PostgresRelationSqlParser", result.attributes().get("parser"));
        assertEquals("PostgresStructuredSqlEventVisitor", result.attributes().get("eventVisitor"));
    }

    @Test
    void postgresAntlrSqlParserDoesNotTreatMysqlBackticksAsQuotedIdentifiers() {
        StructuredParseResult result = new PostgresAntlrSqlParser().parseSql(new SqlStatementRecord(
                "SELECT * FROM `orders` o JOIN `users` u ON o.`user_id` = u.`id`",
                StatementSourceType.PLAIN_SQL,
                "postgres-backtick.sql",
                1,
                1,
                java.util.Map.of()), null);

        assertFalse(result.events().stream().anyMatch(event ->
                "orders".equals(event.attributes().get("table"))
                        || "users".equals(event.attributes().get("table"))));
    }

    @Test
    void postgresShadowParserReportsPostgresRelationVisitorWithoutChangingPrimaryOutput() {
        SqlRelationParser parser = new PostgresDatabaseAdaptor().sqlRelationParser();
        assertTrue(parser instanceof ShadowSqlRelationParser);

        ShadowSqlRelationParser.Result result = ((ShadowSqlRelationParser) parser).parseWithDiagnostics(
                new SqlStatementRecord(
                        "SELECT * FROM \"orders\" o JOIN \"users\" u ON o.\"user_id\" = u.\"id\"",
                        StatementSourceType.NATIVE_LOG,
                        "postgres-shadow.sql",
                        1,
                        1,
                        java.util.Map.of()),
                null);

        assertTrue(result.diagnostics().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.PARSER_COMPARISON
                        && "PostgresRelationExtractionVisitor".equals(event.attributes().get("relationVisitor"))));
        assertTrue(result.relationships().stream().anyMatch(relation ->
                relation.source().displayName().equals("orders.user_id")
                        && relation.target().displayName().equals("users.id")));
    }
}
