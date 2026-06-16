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
        boolean found = relations.stream().anyMatch(r ->
                r.source().isColumnLevel()
                        && r.target().isColumnLevel()
                        && r.source().table().tableName().equals(sourceTable)
                        && r.source().column().columnName().equals(sourceColumn)
                        && r.target().table().tableName().equals(targetTable)
                        && r.target().column().columnName().equals(targetColumn)
                        && r.evidence().stream().anyMatch(e -> e.type() == EvidenceType.SQL_LOG_JOIN));
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
