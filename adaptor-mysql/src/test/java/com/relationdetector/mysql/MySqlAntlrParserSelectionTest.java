package com.relationdetector.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.Collectors.SqlRelationParser;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.Enums.StatementSourceType;
import com.relationdetector.api.Enums.StructuredParseEventType;
import com.relationdetector.core.AntlrSqlRelationParser;

/**
 * Verifies that MySQL owns the MySQL-flavored ANTLR parser selection instead of
 * making core guess which dialect a statement belongs to.
 */
class MySqlAntlrParserSelectionTest {
    @Test
    void adaptorExposesMysqlAntlrSqlAndDdlParsers() {
        MySqlDatabaseAdaptor adaptor = new MySqlDatabaseAdaptor();

        assertEquals(MySqlAntlrSqlParser.class, adaptor.structuredSqlParser().orElseThrow().getClass());
        assertEquals(MySqlAntlrDdlParser.class, adaptor.structuredDdlParser().orElseThrow().getClass());
    }

    @Test
    void mysqlAntlrDdlParserUsesMysqlDdlStructuredEventVisitor() {
        StructuredParseResult result = new MySqlAntlrDdlParser().parseDdl(
                "CREATE TABLE orders(user_id BIGINT REFERENCES users(id));",
                "mysql-ddl.sql",
                null);

        assertEquals("MySqlDdlStructuredEventVisitor", result.attributes().get("ddlEventVisitor"));
    }

    @Test
    void mysqlAntlrDdlParserDoesNotTreatPostgresIncludeIndexAsMysqlUniqueEvidence() {
        StructuredParseResult result = new MySqlAntlrDdlParser().parseDdl(
                "CREATE UNIQUE INDEX users_email_uq ON users (email) INCLUDE (id);",
                "postgres-include-index.sql",
                null);

        assertTrue(result.events().stream().noneMatch(event ->
                event.type() == StructuredParseEventType.DDL_INDEX
                        && "TARGET_UNIQUE".equals(event.attributes().get("role"))
                        && "users".equals(event.attributes().get("table"))
                        && "email".equals(event.attributes().get("column"))),
                () -> "MySQL DDL visitor must not accept PostgreSQL INCLUDE index as unique evidence: " + result.events());
    }

    @Test
    void mysqlAntlrSqlParserAcceptsBacktickQuotedJoin() {
        StructuredParseResult result = new MySqlAntlrSqlParser().parseSql(new SqlStatementRecord(
                "SELECT * FROM `orders` o JOIN `users` u ON o.`user_id` = u.`id`",
                StatementSourceType.PLAIN_SQL,
                "mysql.sql",
                1,
                1,
                java.util.Map.of()), null);

        assertTrue(result.events().stream().anyMatch(event ->
                "orders".equals(event.attributes().get("table"))
                        && "o".equals(event.attributes().get("alias"))));
    }

    @Test
    void mysqlAntlrSqlParserUsesMysqlGrammarAndStructuredEventVisitor() {
        StructuredParseResult result = new MySqlAntlrSqlParser().parseSql(new SqlStatementRecord(
                "SELECT * FROM `orders` o JOIN `users` u ON o.`user_id` = u.`id`",
                StatementSourceType.PLAIN_SQL,
                "mysql.sql",
                1,
                1,
                java.util.Map.of()), null);

        assertEquals("MySqlRelationSql", result.attributes().get("grammar"));
        assertEquals("MySqlRelationSqlLexer", result.attributes().get("lexer"));
        assertEquals("MySqlRelationSqlParser", result.attributes().get("parser"));
        assertEquals("MySqlStructuredSqlEventVisitor", result.attributes().get("eventVisitor"));
    }

    @Test
    void mysqlAdaptorSqlParserUsesAntlrRelationParser() {
        SqlRelationParser parser = new MySqlDatabaseAdaptor().sqlRelationParser();
        assertTrue(parser instanceof AntlrSqlRelationParser);

        java.util.List<RelationshipCandidate> relationships = parser.parse(new SqlStatementRecord(
                "SELECT * FROM `orders` o JOIN `users` u ON o.`user_id` = u.`id`",
                StatementSourceType.NATIVE_LOG,
                "mysql-antlr.sql",
                1,
                1,
                java.util.Map.of()), null);

        assertTrue(relationships.stream().anyMatch(relation ->
                relation.source().displayName().equals("orders.user_id")
                        && relation.target().displayName().equals("users.id")));
    }

    @Test
    void mysqlRelationVisitorOwnsMysqlOnlyStraightJoinFallback() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "SELECT * FROM orders o STRAIGHT_JOIN users u ON o.user_id = u.id",
                StatementSourceType.PLAIN_SQL,
                "mysql-straight-join.sql",
                1,
                1,
                java.util.Map.of());
        StructuredParseResult structured = new StructuredParseResult(
                "ANTLR", "MYSQL", "mysql-straight-join.sql", java.util.List.of(), java.util.List.of(), java.util.Map.of());

        java.util.List<RelationshipCandidate> relations = new MySqlRelationExtractionVisitor().extract(statement, structured);

        assertTrue(relations.stream().anyMatch(relation ->
                relation.source().displayName().equals("orders.user_id")
                        && relation.target().displayName().equals("users.id")),
                () -> "MySQL visitor must own STRAIGHT_JOIN rowset fallback: " + relations);
    }

    @Test
    void mysqlRelationVisitorOwnsMysqlSelectStraightJoinModifierFallback() {
        java.util.List<RelationshipCandidate> relations = mysqlRelations(
                "SELECT STRAIGHT_JOIN * FROM orders o JOIN users u ON o.user_id = u.id");

        assertHasRelation(relations, "orders.user_id", "users.id",
                "MySQL visitor must own SELECT STRAIGHT_JOIN modifier fallback");
    }

    @Test
    void mysqlRelationVisitorOwnsPartitionAndIndexHintFallbacks() {
        java.util.List<RelationshipCandidate> partitionRelations = mysqlRelations(
                "SELECT * FROM orders PARTITION (p202501) o JOIN users u ON o.user_id = u.id");
        java.util.List<RelationshipCandidate> hintRelations = mysqlRelations(
                "SELECT * FROM orders o FORCE INDEX FOR JOIN (idx_orders_user) JOIN users u USE INDEX (PRIMARY) ON o.user_id = u.id");

        assertHasRelation(partitionRelations, "orders.user_id", "users.id",
                "MySQL visitor must own PARTITION table-reference fallback");
        assertHasRelation(hintRelations, "orders.user_id", "users.id",
                "MySQL visitor must own FORCE/USE INDEX hint fallback");
    }

    @Test
    void mysqlRelationVisitorOwnsOdbcOuterJoinFallback() {
        java.util.List<RelationshipCandidate> relations = mysqlRelations(
                "SELECT * FROM { OJ orders o LEFT OUTER JOIN users u ON o.user_id = u.id }");

        assertHasRelation(relations, "orders.user_id", "users.id",
                "MySQL visitor must own ODBC { OJ ... } outer join fallback");
    }

    @Test
    void mysqlRelationVisitorIgnoresJsonTableRowsetAndKeepsPhysicalJoin() {
        java.util.List<RelationshipCandidate> relations = mysqlRelations("""
                SELECT *
                FROM orders o
                JOIN JSON_TABLE(o.payload, '$[*]' COLUMNS (user_id BIGINT PATH '$.user_id')) jt
                  ON jt.user_id = o.user_id
                JOIN users u ON o.user_id = u.id
                """);

        assertHasRelation(relations, "orders.user_id", "users.id",
                "MySQL visitor must ignore JSON_TABLE rowset while preserving physical joins");
        assertTrue(relations.stream().noneMatch(relation ->
                relation.source().table().tableName().equalsIgnoreCase("JSON_TABLE")
                        || relation.target().table().tableName().equalsIgnoreCase("JSON_TABLE")
                        || relation.source().table().tableName().equalsIgnoreCase("jt")
                        || relation.target().table().tableName().equalsIgnoreCase("jt")),
                () -> "JSON_TABLE and its alias must not become physical tables: " + relations);
    }

    @Test
    void mysqlRelationVisitorOwnsMysqlMultiTableDmlFallbacks() {
        java.util.List<RelationshipCandidate> updateRelations = mysqlRelations(
                "UPDATE orders o JOIN users u ON o.user_id = u.id SET o.status = 'PAID'");
        java.util.List<RelationshipCandidate> commaUpdateRelations = mysqlRelations(
                "UPDATE orders o, users u SET o.status = 'PAID' WHERE o.user_id = u.id");
        java.util.List<RelationshipCandidate> deleteRelations = mysqlRelations(
                "DELETE o FROM orders o JOIN users u ON o.user_id = u.id");
        java.util.List<RelationshipCandidate> deleteUsingRelations = mysqlRelations(
                "DELETE FROM o USING orders o JOIN users u ON o.user_id = u.id");

        assertHasRelation(updateRelations, "orders.user_id", "users.id",
                "MySQL visitor must own UPDATE JOIN fallback");
        assertHasRelation(commaUpdateRelations, "orders.user_id", "users.id",
                "MySQL visitor must own comma UPDATE fallback");
        assertHasRelation(deleteRelations, "orders.user_id", "users.id",
                "MySQL visitor must own DELETE target FROM fallback");
        assertHasRelation(deleteUsingRelations, "orders.user_id", "users.id",
                "MySQL visitor must own DELETE FROM target USING fallback");
    }

    @Test
    void mysqlRelationVisitorDoesNotTreatPostgresOnlyRowsetModifiersAsTables() {
        SqlStatementRecord statement = new SqlStatementRecord(
                """
                SELECT *
                FROM ONLY orders o TABLESAMPLE SYSTEM (10)
                JOIN users u ON o.user_id = u.id
                """,
                StatementSourceType.PLAIN_SQL,
                "mysql-postgres-only-rowsets.sql",
                1,
                1,
                java.util.Map.of());
        StructuredParseResult structured = new StructuredParseResult(
                "ANTLR", "MYSQL", "mysql-postgres-only-rowsets.sql", java.util.List.of(), java.util.List.of(), java.util.Map.of());

        java.util.List<RelationshipCandidate> relations = new MySqlRelationExtractionVisitor().extract(statement, structured);

        assertTrue(relations.isEmpty(),
                () -> "MySQL visitor must not infer relations from PostgreSQL-only ONLY/TABLESAMPLE syntax: " + relations);
    }

    @Test
    void mysqlRelationVisitorDoesNotTreatPostgresJoinUsingAliasAsMysqlUsingJoin() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "SELECT * FROM orders o JOIN order_tags ot USING (order_id) AS joined_order_tags",
                StatementSourceType.PLAIN_SQL,
                "mysql-postgres-using-alias.sql",
                1,
                1,
                java.util.Map.of());
        StructuredParseResult structured = new StructuredParseResult(
                "ANTLR", "MYSQL", "mysql-postgres-using-alias.sql", java.util.List.of(), java.util.List.of(), java.util.Map.of());

        java.util.List<RelationshipCandidate> relations = new MySqlRelationExtractionVisitor().extract(statement, structured);

        assertTrue(relations.isEmpty(),
                () -> "MySQL visitor must not infer JOIN USING co-occurrence from PostgreSQL-only USING alias syntax: " + relations);
    }

    @Test
    void mysqlRelationVisitorDoesNotTreatPostgresRowsFromAsPhysicalTables() {
        SqlStatementRecord statement = new SqlStatementRecord(
                """
                SELECT *
                FROM users u
                JOIN ROWS FROM (json_to_recordset('[{"user_id":1}]') AS (user_id bigint)) AS input_ids(user_id)
                  ON input_ids.user_id = u.id
                """,
                StatementSourceType.PLAIN_SQL,
                "mysql-postgres-rows-from.sql",
                1,
                1,
                java.util.Map.of());
        StructuredParseResult structured = new StructuredParseResult(
                "ANTLR", "MYSQL", "mysql-postgres-rows-from.sql", java.util.List.of(), java.util.List.of(), java.util.Map.of());

        java.util.List<RelationshipCandidate> relations = new MySqlRelationExtractionVisitor().extract(statement, structured);

        assertTrue(relations.isEmpty(),
                () -> "MySQL visitor must not infer relations from PostgreSQL ROWS FROM function rowsets: " + relations);
    }

    private java.util.List<RelationshipCandidate> mysqlRelations(String sql) {
        SqlStatementRecord statement = new SqlStatementRecord(
                sql, StatementSourceType.PLAIN_SQL, "mysql-dialect-boundary.sql", 1, 1, java.util.Map.of());
        StructuredParseResult structured = new StructuredParseResult(
                "ANTLR", "MYSQL", "mysql-dialect-boundary.sql", java.util.List.of(), java.util.List.of(), java.util.Map.of());
        return new MySqlRelationExtractionVisitor().extract(statement, structured);
    }

    private void assertHasRelation(
            java.util.List<RelationshipCandidate> relations,
            String source,
            String target,
            String message
    ) {
        assertTrue(relations.stream().anyMatch(relation ->
                relation.source().displayName().equals(source)
                        && relation.target().displayName().equals(target)),
                () -> message + ": " + relations);
    }
}
