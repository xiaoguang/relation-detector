package com.relationdetector.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.Collectors.SqlRelationParser;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.Enums.RelationType;
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
    void postgresAntlrDdlParserUsesPostgresDdlStructuredEventVisitor() {
        StructuredParseResult result = new PostgresAntlrDdlParser().parseDdl(
                "CREATE TABLE orders(user_id BIGINT REFERENCES users(id));",
                "postgres-ddl.sql",
                null);

        assertEquals("PostgresDdlStructuredEventVisitor", result.attributes().get("ddlEventVisitor"));
    }

    @Test
    void postgresAntlrDdlParserDoesNotTreatMysqlFulltextAsPostgresIndexEvidence() {
        StructuredParseResult result = new PostgresAntlrDdlParser().parseDdl(
                "CREATE FULLTEXT INDEX users_bio_idx ON users (bio);",
                "mysql-fulltext-index.sql",
                null);

        assertTrue(result.events().stream().noneMatch(event ->
                event.type() == StructuredParseEventType.DDL_INDEX
                        && "users".equals(event.attributes().get("table"))
                        && "bio".equals(event.attributes().get("column"))),
                () -> "Postgres DDL visitor must not accept MySQL FULLTEXT index as source evidence: " + result.events());
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

    @Test
    void postgresRelationVisitorDoesNotInheritMysqlOnlyStraightJoinFallback() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "SELECT * FROM orders o STRAIGHT_JOIN users u ON o.user_id = u.id",
                StatementSourceType.PLAIN_SQL,
                "postgres-straight-join.sql",
                1,
                1,
                java.util.Map.of());
        StructuredParseResult structured = new StructuredParseResult(
                "ANTLR", "POSTGRESQL", "postgres-straight-join.sql", java.util.List.of(), java.util.List.of(), java.util.Map.of());

        java.util.List<RelationshipCandidate> relations = new PostgresRelationExtractionVisitor().extract(statement, structured);

        assertTrue(relations.isEmpty(),
                () -> "Postgres visitor must not inherit MySQL-only STRAIGHT_JOIN fallback: " + relations);
    }

    @Test
    void postgresRelationVisitorDoesNotInheritMysqlOnlyOdbcPartitionIndexHintOrJsonTableFallbacks() {
        java.util.List<String> mysqlOnlySql = java.util.List.of(
                "SELECT STRAIGHT_JOIN * FROM orders o JOIN users u ON o.user_id = u.id",
                "SELECT * FROM { OJ orders o LEFT OUTER JOIN users u ON o.user_id = u.id }",
                "SELECT * FROM orders PARTITION (p202501) o JOIN users u ON o.user_id = u.id",
                "SELECT * FROM orders o FORCE INDEX FOR JOIN (idx_orders_user) JOIN users u USE INDEX (PRIMARY) ON o.user_id = u.id",
                """
                SELECT *
                FROM orders o
                JOIN JSON_TABLE(o.payload, '$[*]' COLUMNS (user_id BIGINT PATH '$.user_id')) jt
                  ON jt.user_id = o.user_id
                JOIN users u ON o.user_id = u.id
                """
        );

        for (String sql : mysqlOnlySql) {
            java.util.List<RelationshipCandidate> relations = postgresRelations(sql);

            assertTrue(relations.isEmpty(),
                    () -> "Postgres visitor must not infer relations from MySQL-only syntax. SQL: " + sql
                            + " Actual: " + relations);
            assertNoForbiddenTables(relations, "JSON_TABLE", "jt", "p202501", "FORCE", "INDEX", "OJ");
        }
    }

    @Test
    void postgresRelationVisitorDoesNotInheritMysqlOnlyMultiTableDmlFallbacks() {
        java.util.List<String> mysqlOnlySql = java.util.List.of(
                "UPDATE orders o JOIN users u ON o.user_id = u.id SET o.status = 'PAID'",
                "UPDATE orders o, users u SET o.status = 'PAID' WHERE o.user_id = u.id",
                "DELETE o FROM orders o JOIN users u ON o.user_id = u.id",
                "DELETE FROM o USING orders o JOIN users u ON o.user_id = u.id"
        );

        for (String sql : mysqlOnlySql) {
            java.util.List<RelationshipCandidate> relations = postgresRelations(sql);

            assertTrue(relations.isEmpty(),
                    () -> "Postgres visitor must not infer relations from MySQL-only multi-table DML. SQL: " + sql
                            + " Actual: " + relations);
            assertNoForbiddenTables(relations, "o", "SET");
        }
    }

    @Test
    void postgresRelationVisitorOwnsOnlyAndTableSampleFallback() {
        SqlStatementRecord statement = new SqlStatementRecord(
                """
                SELECT *
                FROM ONLY orders o TABLESAMPLE SYSTEM (10)
                JOIN users u ON o.user_id = u.id
                """,
                StatementSourceType.PLAIN_SQL,
                "postgres-only-tablesample.sql",
                1,
                1,
                java.util.Map.of());
        StructuredParseResult structured = new StructuredParseResult(
                "ANTLR", "POSTGRESQL", "postgres-only-tablesample.sql", java.util.List.of(), java.util.List.of(), java.util.Map.of());

        java.util.List<RelationshipCandidate> relations = new PostgresRelationExtractionVisitor().extract(statement, structured);

        assertTrue(relations.stream().anyMatch(relation ->
                relation.source().displayName().equals("orders.user_id")
                        && relation.target().displayName().equals("users.id")),
                () -> "Postgres visitor must own ONLY/TABLESAMPLE rowset fallback: " + relations);
        assertFalse(relations.stream().anyMatch(relation ->
                relation.source().table().tableName().equalsIgnoreCase("ONLY")
                        || relation.target().table().tableName().equalsIgnoreCase("ONLY")
                        || relation.source().table().tableName().equalsIgnoreCase("TABLESAMPLE")
                        || relation.target().table().tableName().equalsIgnoreCase("TABLESAMPLE")),
                () -> "Postgres rowset modifiers must not become physical tables: " + relations);
    }

    @Test
    void postgresRelationVisitorKeepsJoinUsingAliasAsTableCoOccurrence() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "SELECT * FROM orders o JOIN order_tags ot USING (order_id) AS joined_order_tags",
                StatementSourceType.PLAIN_SQL,
                "postgres-join-using-alias.sql",
                1,
                1,
                java.util.Map.of());
        StructuredParseResult structured = new StructuredParseResult(
                "ANTLR", "POSTGRESQL", "postgres-join-using-alias.sql", java.util.List.of(), java.util.List.of(), java.util.Map.of());

        java.util.List<RelationshipCandidate> relations = new PostgresRelationExtractionVisitor().extract(statement, structured);

        assertTrue(relations.stream().anyMatch(relation ->
                relation.relationType() == RelationType.CO_OCCURRENCE
                        && relation.source().displayName().equals("orders")
                        && relation.target().displayName().equals("order_tags")),
                () -> "Postgres JOIN USING alias should preserve table co-occurrence only: " + relations);
    }

    @Test
    void postgresRelationVisitorIgnoresRowsFromFunctionRowsetsAndMaterializedCtes() {
        SqlStatementRecord statement = new SqlStatementRecord(
                """
                WITH recent_orders AS MATERIALIZED (
                  SELECT o.id, o.user_id FROM orders o
                )
                SELECT *
                FROM recent_orders ro
                JOIN ROWS FROM (json_to_recordset('[{"user_id":1}]') AS (user_id bigint)) AS input_ids(user_id)
                  ON input_ids.user_id = ro.user_id
                JOIN users u ON ro.user_id = u.id
                """,
                StatementSourceType.PLAIN_SQL,
                "postgres-rows-from-materialized.sql",
                1,
                1,
                java.util.Map.of());
        StructuredParseResult structured = new StructuredParseResult(
                "ANTLR", "POSTGRESQL", "postgres-rows-from-materialized.sql", java.util.List.of(), java.util.List.of(), java.util.Map.of());

        java.util.List<RelationshipCandidate> relations = new PostgresRelationExtractionVisitor().extract(statement, structured);

        assertFalse(relations.stream().anyMatch(relation ->
                relation.source().table().tableName().equalsIgnoreCase("recent_orders")
                        || relation.target().table().tableName().equalsIgnoreCase("recent_orders")
                        || relation.source().table().tableName().equalsIgnoreCase("ROWS")
                        || relation.target().table().tableName().equalsIgnoreCase("ROWS")
                        || relation.source().table().tableName().equalsIgnoreCase("json_to_recordset")
                        || relation.target().table().tableName().equalsIgnoreCase("json_to_recordset")
                        || relation.source().table().tableName().equalsIgnoreCase("input_ids")
                        || relation.target().table().tableName().equalsIgnoreCase("input_ids")),
                () -> "Postgres CTE/function rowsets must not become physical tables: " + relations);
    }

    private java.util.List<RelationshipCandidate> postgresRelations(String sql) {
        SqlStatementRecord statement = new SqlStatementRecord(
                sql, StatementSourceType.PLAIN_SQL, "postgres-dialect-boundary.sql", 1, 1, java.util.Map.of());
        StructuredParseResult structured = new StructuredParseResult(
                "ANTLR", "POSTGRESQL", "postgres-dialect-boundary.sql", java.util.List.of(), java.util.List.of(), java.util.Map.of());
        return new PostgresRelationExtractionVisitor().extract(statement, structured);
    }

    private void assertNoForbiddenTables(java.util.List<RelationshipCandidate> relations, String... forbiddenTables) {
        java.util.Set<String> forbidden = java.util.Arrays.stream(forbiddenTables)
                .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        assertFalse(relations.stream().anyMatch(relation ->
                forbidden.contains(relation.source().table().tableName().toLowerCase(java.util.Locale.ROOT))
                        || forbidden.contains(relation.target().table().tableName().toLowerCase(java.util.Locale.ROOT))),
                () -> "Forbidden MySQL-only rowsets must not become Postgres physical tables: " + relations);
    }
}
