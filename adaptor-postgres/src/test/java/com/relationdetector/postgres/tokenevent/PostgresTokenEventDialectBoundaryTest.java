package com.relationdetector.postgres.tokenevent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.core.relation.TokenEventSqlRelationParser;
import com.relationdetector.postgres.PostgresDatabaseAdaptor;

/**
 * Verifies that PostgreSQL owns PostgreSQL-flavored token-event parser selection.
 */
class PostgresTokenEventDialectBoundaryTest {
    @Test
    void adaptorExposesPostgresTokenEventSqlAndDdlParsers() {
        PostgresDatabaseAdaptor adaptor = new PostgresDatabaseAdaptor();

        assertEquals(PostgresTokenEventStructuredSqlParser.class, adaptor.structuredSqlParser().orElseThrow().getClass());
        assertEquals(PostgresTokenEventStructuredDdlParser.class, adaptor.structuredDdlParser().orElseThrow().getClass());
    }

    @Test
    void postgresTokenEventDdlParserUsesPostgresDdlStructuredEventVisitor() {
        var result = new PostgresTokenEventStructuredDdlParser().parseDdl(
                "CREATE TABLE orders(user_id BIGINT REFERENCES users(id));",
                "postgres-ddl.sql",
                null);

        assertEquals("PostgresDdlStructuredEventVisitor", result.attributes().get("ddlEventVisitor"));
    }

    @Test
    void postgresTokenEventDdlParserDoesNotTreatMysqlFulltextAsPostgresIndexEvidence() {
        var result = new PostgresTokenEventStructuredDdlParser().parseDdl(
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
    void postgresTokenEventSqlParserAcceptsDoubleQuotedJoin() {
        var result = new PostgresTokenEventStructuredSqlParser().parseSql(new SqlStatementRecord(
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
    void postgresTokenEventSqlParserUsesPostgresGrammarAndEventBuilder() {
        var result = new PostgresTokenEventStructuredSqlParser().parseSql(new SqlStatementRecord(
                "SELECT * FROM \"orders\" o JOIN \"users\" u ON o.\"user_id\" = u.\"id\"",
                StatementSourceType.PLAIN_SQL,
                "postgres.sql",
                1,
                1,
                java.util.Map.of()), null);

        assertEquals("PostgresRelationSql", result.attributes().get("grammar"));
        assertEquals("PostgresRelationSqlLexer", result.attributes().get("lexer"));
        assertEquals("PostgresRelationSqlParser", result.attributes().get("parser"));
        assertEquals("PostgresTokenEventSqlEventBuilder", result.attributes().get("eventBuilder"));
        assertEquals(true, result.attributes().get("tokenEventPrimary"));
    }

    @Test
    void postgresAntlrSqlParserDoesNotEmitFunctionRowsetAsTableReference() {
        var result = new PostgresTokenEventStructuredSqlParser().parseSql(new SqlStatementRecord(
                """
                SELECT *
                FROM orders o
                LEFT JOIN LATERAL ROWS FROM (
                  json_to_recordset(o.payload) AS (product_id BIGINT),
                  generate_series(1, 3)
                ) AS decoded(product_id, ordinal) ON true
                JOIN products p ON decoded.product_id = p.id
                """,
                StatementSourceType.PLAIN_SQL,
                "postgres-function-rowset.sql",
                1,
                1,
                java.util.Map.of()), null);

        assertFalse(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.TABLE_REFERENCE
                        && ("json_to_recordset".equalsIgnoreCase(String.valueOf(event.attributes().get("table")))
                        || "generate_series".equalsIgnoreCase(String.valueOf(event.attributes().get("table")))
                        || "decoded".equalsIgnoreCase(String.valueOf(event.attributes().get("table"))))),
                () -> "Postgres table functions must stay scoped rowsets, not physical table events: " + result.events());
    }

    @Test
    void postgresAntlrSqlParserDoesNotTreatMysqlBackticksAsQuotedIdentifiers() {
        var result = new PostgresTokenEventStructuredSqlParser().parseSql(new SqlStatementRecord(
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
    void postgresAdaptorSqlParserUsesTokenEventRelationParser() {
        SqlRelationParser parser = new PostgresDatabaseAdaptor().sqlRelationParser();
        assertTrue(parser instanceof TokenEventSqlRelationParser);

        java.util.List<RelationshipCandidate> relationships = parser.parse(new SqlStatementRecord(
                "SELECT * FROM \"orders\" o JOIN \"users\" u ON o.\"user_id\" = u.\"id\"",
                StatementSourceType.NATIVE_LOG,
                "postgres-antlr.sql",
                1,
                1,
                java.util.Map.of()), null);

        assertTrue(relationships.stream().anyMatch(relation ->
                relation.source().displayName().equals("orders.user_id")
                        && relation.target().displayName().equals("users.id")));
    }

    @Test
    void postgresRelationVisitorDoesNotInheritMysqlOnlyStraightJoinCompatibility() {
        java.util.List<RelationshipCandidate> relations = postgresRelations(
                "SELECT * FROM orders o STRAIGHT_JOIN users u ON o.user_id = u.id");

        assertNoForbiddenTables(relations, "STRAIGHT_JOIN");
    }

    @Test
    void postgresRelationVisitorDoesNotInheritMysqlOnlyOdbcPartitionIndexHintOrJsonTableCompatibility() {
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

            assertNoForbiddenTables(relations, "JSON_TABLE", "jt", "p202501", "FORCE", "INDEX", "OJ");
        }
    }

    @Test
    void postgresRelationVisitorDoesNotInheritMysqlOnlyMultiTableDmlCompatibility() {
        java.util.List<String> mysqlOnlySql = java.util.List.of(
                "UPDATE orders o JOIN users u ON o.user_id = u.id SET o.status = 'PAID'",
                "UPDATE orders o, users u SET o.status = 'PAID' WHERE o.user_id = u.id",
                "DELETE o FROM orders o JOIN users u ON o.user_id = u.id",
                "DELETE FROM o USING orders o JOIN users u ON o.user_id = u.id"
        );

        for (String sql : mysqlOnlySql) {
            java.util.List<RelationshipCandidate> relations = postgresRelations(sql);

            assertNoForbiddenTables(relations, "o", "SET");
        }
    }

    @Test
    void postgresRelationVisitorOwnsOnlyAndTableSampleCompatibility() {
        java.util.List<RelationshipCandidate> relations = postgresRelations("""
                SELECT *
                FROM ONLY orders o TABLESAMPLE SYSTEM (10)
                JOIN users u ON o.user_id = u.id
                """);

        assertTrue(relations.stream().anyMatch(relation ->
                relation.source().displayName().equals("orders.user_id")
                        && relation.target().displayName().equals("users.id")),
                () -> "Postgres token-event builder must own ONLY/TABLESAMPLE rowset compatibility: " + relations);
        assertFalse(relations.stream().anyMatch(relation ->
                relation.source().table().tableName().equalsIgnoreCase("ONLY")
                        || relation.target().table().tableName().equalsIgnoreCase("ONLY")
                        || relation.source().table().tableName().equalsIgnoreCase("TABLESAMPLE")
                        || relation.target().table().tableName().equalsIgnoreCase("TABLESAMPLE")),
                () -> "Postgres rowset modifiers must not become physical tables: " + relations);
    }

    @Test
    void postgresRelationVisitorKeepsJoinUsingAliasAsTableCoOccurrence() {
        java.util.List<RelationshipCandidate> relations = postgresRelations(
                "SELECT * FROM orders o JOIN order_tags ot USING (order_id) AS joined_order_tags");

        assertNoForbiddenTables(relations, "joined_order_tags", "order_id");
    }

    @Test
    void postgresRelationVisitorIgnoresRowsFromFunctionRowsetsAndMaterializedCtes() {
        java.util.List<RelationshipCandidate> relations = postgresRelations("""
                WITH recent_orders AS MATERIALIZED (
                  SELECT o.id, o.user_id FROM orders o
                )
                SELECT *
                FROM recent_orders ro
                JOIN ROWS FROM (json_to_recordset('[{"user_id":1}]') AS (user_id bigint)) AS input_ids(user_id)
                  ON input_ids.user_id = ro.user_id
                JOIN users u ON ro.user_id = u.id
                """);

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
        return new TokenEventSqlRelationParser(new PostgresTokenEventStructuredSqlParser()).parse(statement);
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
