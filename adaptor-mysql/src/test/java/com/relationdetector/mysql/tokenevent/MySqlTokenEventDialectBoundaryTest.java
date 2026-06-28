package com.relationdetector.mysql.tokenevent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.core.relation.TokenEventSqlRelationParser;
import com.relationdetector.mysql.MySqlDatabaseAdaptor;

/**
 * Verifies that MySQL owns the MySQL-flavored token-event parser selection instead of
 * making core guess which dialect a statement belongs to.
 */
class MySqlTokenEventDialectBoundaryTest {
    @Test
    void adaptorExposesMysqlTokenEventSqlAndDdlParsers() {
        MySqlDatabaseAdaptor adaptor = new MySqlDatabaseAdaptor();

        assertEquals(MySqlTokenEventStructuredSqlParser.class, adaptor.structuredSqlParser().orElseThrow().getClass());
        assertEquals(MySqlTokenEventStructuredDdlParser.class, adaptor.structuredDdlParser().orElseThrow().getClass());
    }

    @Test
    void mysqlTokenEventDdlParserUsesTypedMysqlDdlVisitor() {
        var result = new MySqlTokenEventStructuredDdlParser().parseDdl(
                "CREATE TABLE orders(user_id BIGINT REFERENCES users(id));",
                "mysql-ddl.sql",
                null);

        assertEquals("MySqlRelationSql", result.attributes().get("grammar"));
        assertEquals("MySqlTokenEventParseTreeVisitor", result.attributes().get("eventBuilder"));
        assertFalse(result.attributes().containsKey("ddlEventVisitor"));
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.DDL_FOREIGN_KEY
                        && "orders".equals(event.attributes().get("sourceTable"))
                        && "user_id".equals(event.attributes().get("sourceColumn"))
                        && "users".equals(event.attributes().get("targetTable"))
                        && "id".equals(event.attributes().get("targetColumn"))));
    }

    @Test
    void mysqlTokenEventDdlParserEmitsTypedIndexEvidence() {
        var result = new MySqlTokenEventStructuredDdlParser().parseDdl(
                "CREATE INDEX users_email_idx ON users (email);",
                "mysql-index.sql",
                null);

        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.DDL_INDEX
                        && "SOURCE_INDEX".equals(event.attributes().get("role"))
                        && "users".equals(event.attributes().get("table"))
                        && "email".equals(event.attributes().get("column"))),
                () -> "Typed DDL visitor should emit ordinary index evidence: " + result.events());
    }

    @Test
    void mysqlTokenEventSqlParserAcceptsBacktickQuotedJoin() {
        var result = new MySqlTokenEventStructuredSqlParser().parseSql(new SqlStatementRecord(
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
    void mysqlTokenEventSqlParserUsesMysqlGrammarAndEventBuilder() {
        var result = new MySqlTokenEventStructuredSqlParser().parseSql(new SqlStatementRecord(
                "SELECT * FROM `orders` o JOIN `users` u ON o.`user_id` = u.`id`",
                StatementSourceType.PLAIN_SQL,
                "mysql.sql",
                1,
                1,
                java.util.Map.of()), null);

        assertEquals("MySqlRelationSql", result.attributes().get("grammar"));
        assertEquals("MySqlRelationSqlLexer", result.attributes().get("lexer"));
        assertEquals("MySqlRelationSqlParser", result.attributes().get("parser"));
        assertEquals("MySqlTokenEventParseTreeVisitor", result.attributes().get("eventBuilder"));
        assertFalse(result.attributes().containsKey("legacySupplementBuilder"));
        assertEquals(true, result.attributes().get("tokenEventPrimary"));
    }

    @Test
    void mysqlTokenEventSqlParserKeepsTypedEventsAfterUnsupportedFragment() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "SELECT * FROM (; SELECT * FROM orders o JOIN users u ON o.user_id = u.id",
                StatementSourceType.PLAIN_SQL,
                "mysql-partial.sql",
                1,
                1,
                java.util.Map.of());

        java.util.List<RelationshipCandidate> relationships =
                new TokenEventSqlRelationParser(new MySqlTokenEventStructuredSqlParser()).parse(statement);

        assertHasRelation(relationships, "orders.user_id", "users.id",
                "Token-event should keep typed parse-tree events that ANTLR recovers after an unsupported fragment");
    }

    @Test
    void mysqlAdaptorSqlParserUsesTokenEventRelationParser() {
        SqlRelationParser parser = new MySqlDatabaseAdaptor().sqlRelationParser();
        assertTrue(parser instanceof TokenEventSqlRelationParser);

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
    void mysqlRelationVisitorOwnsMysqlOnlyStraightJoinCompatibility() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "SELECT * FROM orders o STRAIGHT_JOIN users u ON o.user_id = u.id",
                StatementSourceType.PLAIN_SQL,
                "mysql-straight-join.sql",
                1,
                1,
                java.util.Map.of());
        java.util.List<RelationshipCandidate> relations =
                new TokenEventSqlRelationParser(new MySqlTokenEventStructuredSqlParser()).parse(statement);

        assertTrue(relations.stream().anyMatch(relation ->
                relation.source().displayName().equals("orders.user_id")
                        && relation.target().displayName().equals("users.id")),
                () -> "MySQL token-event builder must own STRAIGHT_JOIN rowset compatibility: " + relations);
    }

    @Test
    void mysqlRelationVisitorOwnsMysqlSelectStraightJoinModifierCompatibility() {
        java.util.List<RelationshipCandidate> relations = mysqlRelations(
                "SELECT STRAIGHT_JOIN * FROM orders o JOIN users u ON o.user_id = u.id");

        assertHasRelation(relations, "orders.user_id", "users.id",
                "MySQL token-event builder must own SELECT STRAIGHT_JOIN modifier compatibility");
    }

    @Test
    void mysqlRelationVisitorOwnsPartitionAndIndexHintCompatibility() {
        java.util.List<RelationshipCandidate> partitionRelations = mysqlRelations(
                "SELECT * FROM orders PARTITION (p202501) o JOIN users u ON o.user_id = u.id");
        java.util.List<RelationshipCandidate> hintRelations = mysqlRelations(
                "SELECT * FROM orders o FORCE INDEX FOR JOIN (idx_orders_user) JOIN users u USE INDEX (PRIMARY) ON o.user_id = u.id");

        assertHasRelation(partitionRelations, "orders.user_id", "users.id",
                "MySQL token-event builder must own PARTITION table-reference compatibility");
        assertHasRelation(hintRelations, "orders.user_id", "users.id",
                "MySQL token-event builder must own FORCE/USE INDEX hint compatibility");
    }

    @Test
    void mysqlRelationVisitorOwnsOdbcOuterJoinCompatibility() {
        java.util.List<RelationshipCandidate> relations = mysqlRelations(
                "SELECT * FROM { OJ orders o LEFT OUTER JOIN users u ON o.user_id = u.id }");

        assertHasRelation(relations, "orders.user_id", "users.id",
                "MySQL token-event builder must own ODBC { OJ ... } outer join compatibility");
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
    void mysqlRelationVisitorOwnsMysqlMultiTableDmlCompatibility() {
        java.util.List<RelationshipCandidate> updateRelations = mysqlRelations(
                "UPDATE orders o JOIN users u ON o.user_id = u.id SET o.status = 'PAID'");
        java.util.List<RelationshipCandidate> commaUpdateRelations = mysqlRelations(
                "UPDATE orders o, users u SET o.status = 'PAID' WHERE o.user_id = u.id");
        java.util.List<RelationshipCandidate> deleteRelations = mysqlRelations(
                "DELETE o FROM orders o JOIN users u ON o.user_id = u.id");
        java.util.List<RelationshipCandidate> deleteUsingRelations = mysqlRelations(
                "DELETE FROM o USING orders o JOIN users u ON o.user_id = u.id");

        assertHasRelation(updateRelations, "orders.user_id", "users.id",
                "MySQL token-event builder must own UPDATE JOIN compatibility");
        assertHasRelation(commaUpdateRelations, "orders.user_id", "users.id",
                "MySQL token-event builder must own comma UPDATE compatibility");
        assertHasRelation(deleteRelations, "orders.user_id", "users.id",
                "MySQL token-event builder must own DELETE target FROM compatibility");
        assertHasRelation(deleteUsingRelations, "orders.user_id", "users.id",
                "MySQL token-event builder must own DELETE FROM target USING compatibility");
    }

    @Test
    void mysqlRelationVisitorKeepsJoinsWhenSelectListContainsMysqlAggregateOptions() {
        java.util.List<RelationshipCandidate> relations = mysqlRelations("""
                SELECT
                    GROUP_CONCAT(DISTINCT CONCAT(p.sku, ':', pb.batch_no)
                        ORDER BY pb.expiry_date SEPARATOR '; ') AS batch_details
                FROM product_batches pb
                JOIN products p ON pb.product_id = p.id
                WHERE pb.status = 'active'
                GROUP BY p.id
                """);

        assertHasRelation(relations, "product_batches.product_id", "products.id",
                "MySQL token-event typed grammar should keep JOIN predicates when select-list expressions are too rich to analyze");
    }

    @Test
    void mysqlRelationVisitorDoesNotTreatPostgresOnlyRowsetModifiersAsTables() {
        java.util.List<RelationshipCandidate> relations = mysqlRelations("""
                SELECT *
                FROM ONLY orders o TABLESAMPLE SYSTEM (10)
                JOIN users u ON o.user_id = u.id
                """);

        assertNoForbiddenTables(relations, "ONLY", "TABLESAMPLE", "SYSTEM");
    }

    @Test
    void mysqlRelationVisitorDoesNotTreatPostgresJoinUsingAliasAsMysqlUsingJoin() {
        java.util.List<RelationshipCandidate> relations = mysqlRelations(
                "SELECT * FROM orders o JOIN order_tags ot USING (order_id) AS joined_order_tags");

        assertTrue(relations.isEmpty(),
                () -> "MySQL visitor must not infer JOIN USING co-occurrence from PostgreSQL-only USING alias syntax: " + relations);
    }

    @Test
    void mysqlRelationVisitorDoesNotTreatPostgresRowsFromAsPhysicalTables() {
        java.util.List<RelationshipCandidate> relations = mysqlRelations("""
                SELECT *
                FROM users u
                JOIN ROWS FROM (json_to_recordset('[{"user_id":1}]') AS (user_id bigint)) AS input_ids(user_id)
                  ON input_ids.user_id = u.id
                """);

        assertTrue(relations.isEmpty(),
                () -> "MySQL visitor must not infer relations from PostgreSQL ROWS FROM function rowsets: " + relations);
    }

    private java.util.List<RelationshipCandidate> mysqlRelations(String sql) {
        SqlStatementRecord statement = new SqlStatementRecord(
                sql, StatementSourceType.PLAIN_SQL, "mysql-dialect-boundary.sql", 1, 1, java.util.Map.of());
        return new TokenEventSqlRelationParser(new MySqlTokenEventStructuredSqlParser()).parse(statement);
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

    private void assertNoForbiddenTables(java.util.List<RelationshipCandidate> relations, String... forbiddenTables) {
        java.util.Set<String> forbidden = java.util.Arrays.stream(forbiddenTables)
                .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(relations.stream().noneMatch(relation ->
                forbidden.contains(relation.source().table().tableName().toLowerCase(java.util.Locale.ROOT))
                        || forbidden.contains(relation.target().table().tableName().toLowerCase(java.util.Locale.ROOT))),
                () -> "Forbidden rowset modifiers must not become physical tables: " + relations);
    }
}
