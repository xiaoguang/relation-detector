package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.Enums.EvidenceType;
import com.relationdetector.api.Enums.RelationType;
import com.relationdetector.api.Enums.StatementSourceType;

/**
 * Complex SQL fixtures inspired by official MySQL/PostgreSQL CREATE
 * PROCEDURE/FUNCTION/TRIGGER/VIEW syntax pages.
 *
 * <p>The statements are intentionally long and contain procedural wrappers,
 * nested predicates, joins, and subqueries. The assertions document the current
 * parser contract: it is expected to find explicit aliased equality relations
 * and simple IN-subquery relations inside long object bodies.
 */
class SimpleSqlRelationParserComplexSqlTest {
    private final SimpleSqlRelationParser parser = new SimpleSqlRelationParser();

    @Test
    void parsesLongMysqlProcedureBodyWithJoinsAndNestedSubqueries() {
        String sql = """
                CREATE PROCEDURE rebuild_order_risk(IN p_tenant_id BIGINT)
                BEGIN
                  INSERT INTO order_risk_snapshots(order_id, user_id, payment_id, shipment_id)
                  SELECT o.id, u.id, p.id, s.id
                  FROM orders o
                  JOIN users u ON o.user_id = u.id
                  JOIN payments p ON p.order_id = o.id
                  JOIN shipments s ON s.order_id = o.id
                  JOIN carriers ca ON s.carrier_id = ca.id
                  WHERE o.tenant_id = p_tenant_id
                    AND o.customer_id IN (
                      SELECT c.id
                      FROM customers c
                      JOIN accounts a ON c.account_id = a.id
                      WHERE a.tenant_id = p_tenant_id
                    )
                    AND EXISTS (
                      SELECT 1
                      FROM invoices i
                      JOIN orders oi ON i.order_id = oi.id
                      WHERE oi.id = o.id
                    );
                END
                """;

        List<RelationshipCandidate> relations = parse(sql, StatementSourceType.PROCEDURE);

        assertColumnRelation(relations, "orders", "user_id", "users", "id", EvidenceType.PROCEDURE_JOIN);
        assertColumnRelation(relations, "payments", "order_id", "orders", "id", EvidenceType.PROCEDURE_JOIN);
        assertColumnRelation(relations, "shipments", "order_id", "orders", "id", EvidenceType.PROCEDURE_JOIN);
        assertColumnRelation(relations, "shipments", "carrier_id", "carriers", "id", EvidenceType.PROCEDURE_JOIN);
        assertColumnRelation(relations, "customers", "account_id", "accounts", "id", EvidenceType.PROCEDURE_JOIN);
        assertColumnRelation(relations, "orders", "customer_id", "customers", "id", EvidenceType.SQL_LOG_SUBQUERY_IN);
        assertColumnRelation(relations, "invoices", "order_id", "orders", "id", EvidenceType.PROCEDURE_JOIN);
    }

    @Test
    void parsesLongMysqlTriggerBodyAndMarksEvidenceAsTriggerReference() {
        String sql = """
                CREATE TRIGGER after_order_insert
                AFTER INSERT ON orders
                FOR EACH ROW
                BEGIN
                  INSERT INTO audit_logs(order_id, user_id, employee_id, message)
                  SELECT o.id, u.id, e.id, 'created'
                  FROM orders o
                  JOIN users u ON o.user_id = u.id
                  JOIN employees e ON o.employee_id = e.id
                  JOIN departments d ON e.department_id = d.id
                  WHERE o.id = NEW.id;
                END
                """;

        List<RelationshipCandidate> relations = parse(sql, StatementSourceType.TRIGGER);

        assertColumnRelation(relations, "orders", "user_id", "users", "id", EvidenceType.TRIGGER_REFERENCE);
        assertColumnRelation(relations, "orders", "employee_id", "employees", "id", EvidenceType.TRIGGER_REFERENCE);
        assertColumnRelation(relations, "employees", "department_id", "departments", "id", EvidenceType.TRIGGER_REFERENCE);
    }

    @Test
    void parsesLongPostgresViewDefinitionWithMultipleJoinRelations() {
        String sql = """
                CREATE VIEW reporting.order_dashboard AS
                SELECT o.id, u.name, c.name AS customer_name, a.status, p.id AS payment_id
                FROM public.orders o
                JOIN public.users u ON o.user_id = u.id
                JOIN public.customers c ON o.customer_id = c.id
                JOIN public.accounts a ON c.account_id = a.id
                LEFT JOIN public.payments p ON p.order_id = o.id
                WHERE EXISTS (
                  SELECT 1
                  FROM public.shipments s
                  JOIN public.carriers ca ON s.carrier_id = ca.id
                  WHERE s.order_id = o.id
                )
                """;

        List<RelationshipCandidate> relations = parse(sql, StatementSourceType.VIEW);

        assertColumnRelation(relations, "orders", "user_id", "users", "id", EvidenceType.VIEW_JOIN);
        assertColumnRelation(relations, "orders", "customer_id", "customers", "id", EvidenceType.VIEW_JOIN);
        assertColumnRelation(relations, "customers", "account_id", "accounts", "id", EvidenceType.VIEW_JOIN);
        assertColumnRelation(relations, "payments", "order_id", "orders", "id", EvidenceType.VIEW_JOIN);
        assertColumnRelation(relations, "shipments", "carrier_id", "carriers", "id", EvidenceType.VIEW_JOIN);
        assertColumnRelation(relations, "shipments", "order_id", "orders", "id", EvidenceType.VIEW_JOIN);
    }

    @Test
    void parsesLongPostgresFunctionBodyAndKeepsFunctionEvidenceAsProcedureJoin() {
        String sql = """
                CREATE FUNCTION reporting.user_revenue(p_user_id bigint)
                RETURNS numeric
                LANGUAGE plpgsql
                AS $$
                DECLARE total numeric;
                BEGIN
                  SELECT sum(p.amount)
                  INTO total
                  FROM payments p
                  JOIN orders o ON p.order_id = o.id
                  JOIN users u ON o.user_id = u.id
                  JOIN customers c ON o.customer_id = c.id
                  WHERE u.id = p_user_id
                    AND c.account_id IN (
                      SELECT a.id
                      FROM accounts a
                      JOIN account_groups ag ON a.account_group_id = ag.id
                    );
                  RETURN total;
                END;
                $$;
                """;

        List<RelationshipCandidate> relations = parse(sql, StatementSourceType.FUNCTION);

        assertColumnRelation(relations, "payments", "order_id", "orders", "id", EvidenceType.PROCEDURE_JOIN);
        assertColumnRelation(relations, "orders", "user_id", "users", "id", EvidenceType.PROCEDURE_JOIN);
        assertColumnRelation(relations, "orders", "customer_id", "customers", "id", EvidenceType.PROCEDURE_JOIN);
        assertColumnRelation(relations, "accounts", "account_group_id", "account_groups", "id", EvidenceType.PROCEDURE_JOIN);
        assertColumnRelation(relations, "customers", "account_id", "accounts", "id", EvidenceType.SQL_LOG_SUBQUERY_IN);
    }

    @Test
    void recordsTableCoOccurrenceForCommaSeparatedTablesWithoutJoinCondition() {
        String sql = """
                CREATE VIEW reporting.login_debug AS
                SELECT u.id, l.action
                FROM users u, audit_logs l, security_events se
                WHERE l.action = 'LOGIN'
                """;

        List<RelationshipCandidate> relations = parse(sql, StatementSourceType.VIEW);

        Set<String> tablePairs = relations.stream()
                .filter(r -> r.relationType() == RelationType.CO_OCCURRENCE)
                .map(r -> r.source().table().tableName() + "->" + r.target().table().tableName())
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "users->audit_logs",
                "users->security_events",
                "audit_logs->security_events"), tablePairs);
    }

    @Test
    void parsesLeftRightAndFullOuterJoinEqualityAsColumnRelationsWithJoinKindAttributes() {
        String sql = """
                CREATE VIEW reporting.outer_join_debug AS
                SELECT o.id, p.id, s.id, r.id
                FROM orders o
                LEFT JOIN payments p ON p.order_id = o.id
                RIGHT OUTER JOIN shipments s ON s.order_id = o.id
                FULL OUTER JOIN refunds r ON r.order_id = o.id
                """;

        List<RelationshipCandidate> relations = parse(sql, StatementSourceType.VIEW);

        assertColumnRelation(relations, "payments", "order_id", "orders", "id", EvidenceType.VIEW_JOIN);
        assertColumnRelation(relations, "shipments", "order_id", "orders", "id", EvidenceType.VIEW_JOIN);
        assertColumnRelation(relations, "refunds", "order_id", "orders", "id", EvidenceType.VIEW_JOIN);
        assertEvidenceAttribute(relations, "payments", "order_id", "joinKind", "LEFT_JOIN");
        assertEvidenceAttribute(relations, "shipments", "order_id", "joinKind", "RIGHT_JOIN");
        assertEvidenceAttribute(relations, "refunds", "order_id", "joinKind", "FULL_JOIN");
    }

    @Test
    void treatsJoinUsingAndNaturalJoinAsWeakTableLevelEvidence() {
        String sql = """
                CREATE VIEW reporting.implicit_join_debug AS
                SELECT *
                FROM orders o
                JOIN order_tags ot USING (order_id)
                NATURAL LEFT JOIN order_tag_audit ota
                """;

        List<RelationshipCandidate> relations = parse(sql, StatementSourceType.VIEW);

        assertTableCoOccurrence(relations, "orders", "order_tags");
        assertTableCoOccurrence(relations, "order_tags", "order_tag_audit");
        assertFalse(relations.stream().anyMatch(r -> r.relationType() == RelationType.FK_LIKE),
                () -> "USING/NATURAL should not invent column-level FK-like relations: " + describe(relations));
    }

    @Test
    void avoidsPseudoRelationsToCteNamesWhileStillParsingRelationsInsideCteBodies() {
        String sql = """
                WITH recent_orders AS (
                  SELECT o.id, o.user_id
                  FROM orders o
                  JOIN users u ON o.user_id = u.id
                ),
                paid_recent_orders AS (
                  SELECT ro.id, p.id AS payment_id
                  FROM recent_orders ro
                  JOIN payments p ON p.order_id = ro.id
                )
                SELECT *
                FROM paid_recent_orders pro
                JOIN invoices i ON i.payment_id = pro.payment_id
                """;

        List<RelationshipCandidate> relations = parse(sql, StatementSourceType.PLAIN_SQL);

        assertColumnRelation(relations, "orders", "user_id", "users", "id", EvidenceType.SQL_LOG_JOIN);
        assertFalse(relations.stream().anyMatch(r ->
                        r.source().displayName().contains("recent_orders")
                                || r.target().displayName().contains("recent_orders")
                                || r.source().displayName().contains("paid_recent_orders")
                                || r.target().displayName().contains("paid_recent_orders")),
                () -> "CTE aliases should not be emitted as physical table relations: " + describe(relations));
    }

    @Test
    void tracesCteOutputColumnsBackToBaseColumnsAcrossMultipleCteLayers() {
        String sql = """
                WITH recent_orders AS (
                  SELECT o.id AS order_id, o.user_id, c.region_id
                  FROM orders o
                  JOIN customers c ON o.customer_id = c.id
                ),
                regional_orders AS (
                  SELECT ro.order_id, ro.region_id
                  FROM recent_orders ro
                )
                SELECT *
                FROM regional_orders x
                JOIN regions r ON x.region_id = r.id
                JOIN invoices i ON i.order_id = x.order_id
                """;

        List<RelationshipCandidate> relations = parse(sql, StatementSourceType.PLAIN_SQL);

        assertColumnRelation(relations, "orders", "customer_id", "customers", "id", EvidenceType.SQL_LOG_JOIN);
        assertColumnRelation(relations, "customers", "region_id", "regions", "id", EvidenceType.SQL_LOG_JOIN);
        assertColumnRelation(relations, "invoices", "order_id", "orders", "id", EvidenceType.SQL_LOG_JOIN);
        assertFalse(relations.stream().anyMatch(r ->
                        r.source().displayName().contains("recent_orders")
                                || r.target().displayName().contains("recent_orders")
                                || r.source().displayName().contains("regional_orders")
                                || r.target().displayName().contains("regional_orders")),
                () -> "Lineage-resolved CTE relations should point to base tables only: " + describe(relations));
    }

    @Test
    void tracesDerivedTableColumnsThroughNestedSubqueries() {
        String sql = """
                SELECT *
                FROM (
                  SELECT inner_orders.order_id, inner_orders.customer_id
                  FROM (
                    SELECT o.id AS order_id, o.customer_id
                    FROM orders o
                    JOIN users u ON o.user_id = u.id
                  ) AS inner_orders
                ) AS projected_orders
                JOIN customers c ON projected_orders.customer_id = c.id
                JOIN invoices i ON i.order_id = projected_orders.order_id
                """;

        List<RelationshipCandidate> relations = parse(sql, StatementSourceType.PLAIN_SQL);

        assertColumnRelation(relations, "orders", "user_id", "users", "id", EvidenceType.SQL_LOG_JOIN);
        assertColumnRelation(relations, "orders", "customer_id", "customers", "id", EvidenceType.SQL_LOG_JOIN);
        assertColumnRelation(relations, "invoices", "order_id", "orders", "id", EvidenceType.SQL_LOG_JOIN);
        assertFalse(relations.stream().anyMatch(r ->
                        r.source().displayName().contains("projected_orders")
                                || r.target().displayName().contains("projected_orders")
                                || r.source().displayName().contains("inner_orders")
                                || r.target().displayName().contains("inner_orders")),
                () -> "Derived table lineage should resolve to base tables, not derived aliases: " + describe(relations));
    }

    @Test
    void doesNotInferColumnRelationFromExpressionBasedLineage() {
        String sql = """
                WITH normalized_user_keys AS (
                  SELECT COALESCE(a.user_id, b.user_id) AS user_id
                  FROM account_events a
                  LEFT JOIN backup_account_events b ON b.account_event_id = a.id
                )
                SELECT *
                FROM normalized_user_keys nuk
                JOIN users u ON nuk.user_id = u.id
                """;

        List<RelationshipCandidate> relations = parse(sql, StatementSourceType.PLAIN_SQL);

        assertColumnRelation(relations, "backup_account_events", "account_event_id",
                "account_events", "id", EvidenceType.SQL_LOG_JOIN);
        assertFalse(relations.stream().anyMatch(r ->
                        r.relationType() == RelationType.FK_LIKE
                                && r.target().isColumnLevel()
                                && r.target().table().tableName().equals("users")
                                && r.target().column().columnName().equals("id")),
                () -> "Expression output COALESCE(a.user_id, b.user_id) must not become a precise FK-like relation: "
                        + describe(relations));
    }

    @Test
    void parsesCommaJoinBusinessPredicatesWhileIgnoringTempInputFilterTables() {
        String sql = """
                CREATE PROCEDURE rebuild_user_order_report(
                  IN p_status_csv TEXT,
                  IN p_user_id_csv TEXT
                )
                BEGIN
                  CREATE TEMPORARY TABLE selected_user_ids(user_id BIGINT);
                  CREATE TEMPORARY TABLE selected_status_codes(status_code VARCHAR(32));

                  SELECT o.id, u.id, c.id
                  FROM orders o,
                       users u,
                       customers c,
                       selected_user_ids sui,
                       selected_status_codes ssc
                  WHERE o.user_id = u.id
                    AND o.created_user_id = u.id
                    AND o.updated_user_id = u.id
                    AND o.customer_id = c.id
                    AND sui.user_id = u.id
                    AND ssc.status_code = o.status_code
                    AND o.deleted_at IS NULL
                    AND c.tenant_id = p_tenant_id;
                END
                """;

        List<RelationshipCandidate> relations = parse(sql, StatementSourceType.PROCEDURE);

        assertColumnRelation(relations, "orders", "user_id", "users", "id", EvidenceType.PROCEDURE_JOIN);
        assertColumnRelation(relations, "orders", "created_user_id", "users", "id", EvidenceType.PROCEDURE_JOIN);
        assertColumnRelation(relations, "orders", "updated_user_id", "users", "id", EvidenceType.PROCEDURE_JOIN);
        assertColumnRelation(relations, "orders", "customer_id", "customers", "id", EvidenceType.PROCEDURE_JOIN);
        assertNoRelationContainingTable(relations, "selected_user_ids");
        assertNoRelationContainingTable(relations, "selected_status_codes");
    }

    @Test
    void localTempTableIgnoreScopeDoesNotLeakBeyondCurrentFunctionParse() {
        String functionSql = """
                CREATE FUNCTION filter_users(p_user_ids text)
                RETURNS TABLE(user_id bigint)
                LANGUAGE plpgsql
                AS $$
                BEGIN
                  CREATE TEMP TABLE selected_user_ids(user_id BIGINT);

                  RETURN QUERY
                  SELECT u.id
                  FROM users u,
                       selected_user_ids sui
                  WHERE sui.user_id = u.id;
                END;
                $$;
                """;

        List<RelationshipCandidate> functionRelations = parse(functionSql, StatementSourceType.FUNCTION);

        assertNoRelationContainingTable(functionRelations, "selected_user_ids");

        String plainSql = """
                SELECT *
                FROM selected_user_ids sui
                JOIN users u ON sui.user_id = u.id
                """;

        List<RelationshipCandidate> plainRelations = parse(plainSql, StatementSourceType.PLAIN_SQL);

        assertColumnRelation(plainRelations, "selected_user_ids", "user_id", "users", "id", EvidenceType.SQL_LOG_JOIN);
    }

    private List<RelationshipCandidate> parse(String sql, StatementSourceType sourceType) {
        return parser.parse(new SqlStatementRecord(sql, sourceType, "complex-fixture.sql", 1, 1, java.util.Map.of()));
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
                + targetTable + "." + targetColumn + " with " + evidenceType
                + ". Actual: " + describe(relations));
    }

    private void assertTableCoOccurrence(List<RelationshipCandidate> relations, String leftTable, String rightTable) {
        boolean found = relations.stream().anyMatch(r ->
                r.relationType() == RelationType.CO_OCCURRENCE
                        && r.source().table().tableName().equals(leftTable)
                        && r.target().table().tableName().equals(rightTable));
        assertTrue(found, () -> "Missing table co-occurrence "
                + leftTable + " -> " + rightTable + ". Actual: " + describe(relations));
    }

    private void assertEvidenceAttribute(
            List<RelationshipCandidate> relations,
            String sourceTable,
            String sourceColumn,
            String attribute,
            String value
    ) {
        boolean found = relations.stream().anyMatch(r ->
                r.source().isColumnLevel()
                        && r.source().table().tableName().equals(sourceTable)
                        && r.source().column().columnName().equals(sourceColumn)
                        && r.evidence().stream().anyMatch(e -> value.equals(String.valueOf(e.attributes().get(attribute)))));
        assertTrue(found, () -> "Missing evidence attribute "
                + attribute + "=" + value + " for " + sourceTable + "." + sourceColumn
                + ". Actual: " + describe(relations));
    }

    private void assertNoRelationContainingTable(List<RelationshipCandidate> relations, String tableName) {
        assertFalse(relations.stream().anyMatch(r ->
                        r.source().table().tableName().equals(tableName)
                                || r.target().table().tableName().equals(tableName)),
                () -> "Filter/input temp table should not become relationship evidence: "
                        + tableName + ". Actual: " + describe(relations));
    }

    private String describe(List<RelationshipCandidate> relations) {
        return relations.stream()
                .map(r -> r.source().displayName() + " -> " + r.target().displayName()
                        + " " + r.relationType() + " " + r.evidence().stream()
                        .map(e -> e.type().name()).collect(Collectors.joining(",")))
                .collect(Collectors.joining("; "));
    }
}
