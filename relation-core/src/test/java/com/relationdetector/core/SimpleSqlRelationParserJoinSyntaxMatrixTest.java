package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.Enums.EvidenceType;
import com.relationdetector.api.Enums.StatementSourceType;

/**
 * SQL join syntax matrix tests.
 *
 * <p>The fixtures intentionally use small SQL statements instead of long object
 * bodies. Their purpose is to pin down syntax variants that are valid in MySQL
 * and PostgreSQL:
 *
 * <ul>
 *   <li>explicit JOIN keyword versus comma-separated FROM lists;</li>
 *   <li>all tables aliased, no tables aliased, and mixed alias usage;</li>
 *   <li>quoted identifiers, unquoted identifiers, and mixed quoted/unquoted
 *       sides of the same relation.</li>
 * </ul>
 */
class SimpleSqlRelationParserJoinSyntaxMatrixTest {
    private final SimpleSqlRelationParser parser = new SimpleSqlRelationParser();

    @Test
    void parsesExplicitJoinWithoutAliases() {
        String sql = """
                SELECT *
                FROM orders
                JOIN users ON orders.user_id = users.id
                JOIN payments ON payments.order_id = orders.id
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "orders", "user_id", "users", "id");
        assertColumnRelation(relations, "payments", "order_id", "orders", "id");
    }

    @Test
    void parsesExplicitJoinWhenOnlyOneSideUsesAlias() {
        String sql = """
                SELECT *
                FROM orders o
                JOIN users ON o.user_id = users.id
                JOIN payments p ON p.order_id = orders.id
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "orders", "user_id", "users", "id");
        assertColumnRelation(relations, "payments", "order_id", "orders", "id");
    }

    @Test
    void parsesCommaSeparatedFromListWithAliasesAndWithoutAliasesMixed() {
        String sql = """
                SELECT *
                FROM orders o, users, payments p
                WHERE o.user_id = users.id
                  AND p.order_id = orders.id
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "orders", "user_id", "users", "id");
        assertColumnRelation(relations, "payments", "order_id", "orders", "id");
    }

    @Test
    void parsesPostgresDoubleQuotedIdentifiersMixedWithUnquotedIdentifiers() {
        String sql = """
                SELECT *
                FROM "public"."orders" o
                JOIN users ON o."user_id" = users.id
                JOIN "payments" ON "payments".order_id = o.id
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "orders", "user_id", "users", "id");
        assertColumnRelation(relations, "payments", "order_id", "orders", "id");
    }

    @Test
    void parsesMysqlBacktickQuotedIdentifiersMixedWithUnquotedIdentifiers() {
        String sql = """
                SELECT *
                FROM `orders`
                JOIN users u ON `orders`.`user_id` = u.`id`
                JOIN `payments` p ON p.`order_id` = `orders`.id
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "orders", "user_id", "users", "id");
        assertColumnRelation(relations, "payments", "order_id", "orders", "id");
    }

    @Test
    void doesNotIgnoreBusinessTablesJustBecauseTheirNamesLookLikeInputFilters() {
        String sql = """
                SELECT *
                FROM filter_rules fr
                JOIN users u ON fr.user_id = u.id
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "filter_rules", "user_id", "users", "id");
    }

    @Test
    void parsesTupleEqualityComparisonByAlignedColumns() {
        String sql = """
                SELECT *
                FROM orders o
                JOIN users u
                  ON (o.tenant_id, o.user_id) = (u.tenant_id, u.id)
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "orders", "tenant_id", "users", "tenant_id");
        assertColumnRelation(relations, "orders", "user_id", "users", "id");
    }

    @Test
    void parsesTupleInSubqueryComparisonByAlignedColumns() {
        String sql = """
                SELECT *
                FROM orders o
                WHERE (o.tenant_id, o.user_id) IN (
                  SELECT u.tenant_id, u.id
                  FROM users u
                )
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "orders", "tenant_id", "users", "tenant_id", EvidenceType.SQL_LOG_SUBQUERY_IN);
        assertColumnRelation(relations, "orders", "user_id", "users", "id", EvidenceType.SQL_LOG_SUBQUERY_IN);
    }

    @Test
    void parsesPostgresUpdateFromAliases() {
        String sql = """
                UPDATE orders o
                SET status = 'PAID'
                FROM users u
                WHERE o.user_id = u.id
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "orders", "user_id", "users", "id");
    }

    @Test
    void parsesPostgresDeleteUsingAliases() {
        String sql = """
                DELETE FROM orders o
                USING users u
                WHERE o.user_id = u.id
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "orders", "user_id", "users", "id");
    }

    @Test
    void parsesPostgresUpdateFromWithoutAliases() {
        String sql = """
                UPDATE orders
                SET status = 'PAID'
                FROM users
                WHERE orders.user_id = users.id
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "orders", "user_id", "users", "id");
    }

    @Test
    void parsesPostgresDeleteUsingWithoutAliases() {
        String sql = """
                DELETE FROM orders
                USING users
                WHERE orders.user_id = users.id
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "orders", "user_id", "users", "id");
    }

    private List<RelationshipCandidate> parse(String sql) {
        return parser.parse(new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL,
                "join-syntax-matrix.sql", 1, 1, java.util.Map.of()));
    }

    private void assertColumnRelation(
            List<RelationshipCandidate> relations,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn
    ) {
        assertColumnRelation(relations, sourceTable, sourceColumn, targetTable, targetColumn, EvidenceType.SQL_LOG_JOIN);
    }

    private void assertColumnRelation(
            List<RelationshipCandidate> relations,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            EvidenceType evidenceType
    ) {
        boolean found = relations.stream().anyMatch(r ->
                r.source().isColumnLevel()
                        && r.target().isColumnLevel()
                        && r.source().table().tableName().equals(sourceTable)
                        && r.source().column().columnName().equals(sourceColumn)
                        && r.target().table().tableName().equals(targetTable)
                        && r.target().column().columnName().equals(targetColumn)
                        && r.evidence().stream().anyMatch(e -> e.type() == evidenceType));
        assertTrue(found, () -> "Missing relation "
                + sourceTable + "." + sourceColumn + " -> "
                + targetTable + "." + targetColumn
                + ". Actual: " + describe(relations));
    }

    private String describe(List<RelationshipCandidate> relations) {
        return relations.stream()
                .map(r -> r.source().displayName() + " -> " + r.target().displayName()
                        + " " + r.relationType() + " " + r.evidence().stream()
                        .map(e -> e.type().name()).collect(Collectors.joining(",")))
                .collect(Collectors.joining("; "));
    }
}
