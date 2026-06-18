package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.StructuredSqlEvent;
import com.relationdetector.api.Enums.StatementSourceType;
import com.relationdetector.api.Enums.StructuredParseEventType;

/**
 * Golden comparison for the ANTLR shadow path.
 *
 * <p>Shadow mode deliberately returns the primary parser's relationships while
 * collecting structured parser diagnostics. These tests protect both sides of
 * that migration contract: the final relationship output must stay identical to
 * the primary baseline, and the ANTLR path must at least see table references
 * and equality predicates for the same complex dialect-shaped SQL.
 */
class AntlrShadowGoldenComparisonTest {
    private final SimpleSqlRelationParser primary = new SimpleSqlRelationParser();
    private final RelationExtractionVisitor visitor = new RelationExtractionVisitor();

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenSql")
    void shadowParserKeepsPrimaryRelationshipOutputAndEmitsStructuredEvents(GoldenSql fixture) {
        SqlStatementRecord statement = new SqlStatementRecord(fixture.sql(), StatementSourceType.PLAIN_SQL,
                fixture.name() + ".sql", 1, fixture.sql().lines().count(), java.util.Map.of());
        ShadowSqlRelationParser shadow = new ShadowSqlRelationParser(
                primary,
                new AntlrStructuredSqlParser(fixture.dialect()),
                visitor);

        List<RelationshipCandidate> primaryRelations = primary.parse(statement);
        ShadowSqlRelationParser.Result result = shadow.parseWithDiagnostics(statement, null);
        StructuredParseResult structured = new AntlrStructuredSqlParser(fixture.dialect()).parseSql(statement, null);

        assertEquals(fingerprints(primaryRelations), fingerprints(result.relationships()));
        assertTrue(result.missingSimpleRelations().isEmpty(),
                () -> "ANTLR shadow extractor must not miss Simple baseline relations: " + result.missingSimpleRelations());
        assertTrue(result.diagnostics().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.PARSER_COMPARISON));
        assertHasEvent(structured.events(), StructuredParseEventType.TABLE_REFERENCE);
        assertHasEvent(structured.events(), StructuredParseEventType.COLUMN_EQUALITY);
        assertFalse(primaryRelations.isEmpty(), "golden fixture must exercise at least one relationship");
    }

    private static List<GoldenSql> goldenSql() {
        return List.of(
                new GoldenSql("mysql-cte-update-shape", SqlDialect.MYSQL, """
                        WITH recent_orders AS (
                          SELECT o.id AS order_id, o.user_id
                          FROM `orders` o
                          JOIN `users` u ON o.user_id = u.id
                        )
                        SELECT *
                        FROM recent_orders ro
                        JOIN payments p ON p.order_id = ro.order_id
                        """),
                new GoldenSql("mysql-delete-left-join-shape", SqlDialect.MYSQL, """
                        DELETE o
                        FROM orders o
                        LEFT JOIN users u ON o.user_id = u.id
                        WHERE u.id IS NULL
                        """),
                new GoldenSql("postgres-nested-cte-shape", SqlDialect.POSTGRES, """
                        WITH a AS (
                          SELECT o.id AS order_id, o.customer_id
                          FROM "public"."orders" o
                        ),
                        b AS (
                          SELECT a.order_id, c.region_id
                          FROM a
                          JOIN customers c ON a.customer_id = c.id
                        )
                        SELECT *
                        FROM b
                        JOIN regions r ON b.region_id = r.id
                        """),
                new GoldenSql("postgres-merge-shape", SqlDialect.POSTGRES, """
                        MERGE INTO target_orders t
                        USING source_orders s
                        ON t.source_order_id = s.id
                        WHEN MATCHED THEN
                          UPDATE SET synced_at = CURRENT_TIMESTAMP
                        """));
    }

    private void assertHasEvent(List<StructuredSqlEvent> events, StructuredParseEventType type) {
        assertTrue(events.stream().anyMatch(event -> event.type() == type),
                () -> "Missing structured event " + type + ". Actual: " + events);
    }

    private Set<String> fingerprints(List<RelationshipCandidate> relations) {
        return relations.stream()
                .map(r -> r.relationType() + ":"
                        + r.source().displayName() + "->" + r.target().displayName()
                        + ":" + r.evidence().stream().map(e -> e.type().name()).collect(Collectors.joining(",")))
                .collect(Collectors.toSet());
    }

    private record GoldenSql(String name, SqlDialect dialect, String sql) {
    }
}
