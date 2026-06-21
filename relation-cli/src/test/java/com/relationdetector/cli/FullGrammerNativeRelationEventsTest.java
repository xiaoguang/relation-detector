package com.relationdetector.cli;

import com.relationdetector.core.fullgrammer.*;
import com.relationdetector.core.lineage.*;
import com.relationdetector.core.relation.*;
import com.relationdetector.core.tokenevent.*;

import com.relationdetector.core.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.relationdetector.api.Collectors.StructuredSqlParser;
import com.relationdetector.api.DataLineageCandidate;
import com.relationdetector.api.Endpoint;
import com.relationdetector.api.Enums.DatabaseType;
import com.relationdetector.api.Enums.StatementSourceType;
import com.relationdetector.api.Enums.StructuredParseEventType;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.StructuredSqlEvent;

class FullGrammerNativeRelationEventsTest {
    @Test
    void mysqlProducesFirstRelationBatchWithoutDelegatingThoseEventTypes() {
        SqlStatementRecord statement = statement("""
                SELECT *
                FROM orders o
                JOIN order_tags ot USING (order_id)
                WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = o.user_id)
                  AND o.customer_id IN (SELECT c.id FROM customers c)
                  AND (o.store_id, o.region_id) IN (SELECT s.id, s.region_id FROM stores s)
                """);

        StructuredParseResult result = FullGrammerTokenEventParserFactory.create(
                        DatabaseType.MYSQL,
                        "8.0.36",
                        emptyDelegate(SqlDialect.MYSQL))
                .parser()
                .parseSql(statement, null);

        assertNativeEventTypes(result, Set.of(
                StructuredParseEventType.PREDICATE_EQUALITY,
                StructuredParseEventType.JOIN_USING_COLUMNS,
                StructuredParseEventType.EXISTS_PREDICATE,
                StructuredParseEventType.IN_SUBQUERY_PREDICATE,
                StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE,
                StructuredParseEventType.ROWSET_REFERENCE));
        assertFalse(delegatedEventTypes(result).contains("PREDICATE_EQUALITY"));
        assertFalse(delegatedEventTypes(result).contains("JOIN_USING_COLUMNS"));
        assertFalse(delegatedEventTypes(result).contains("EXISTS_PREDICATE"));
        assertFalse(delegatedEventTypes(result).contains("IN_SUBQUERY_PREDICATE"));
        assertFalse(delegatedEventTypes(result).contains("TUPLE_IN_SUBQUERY_PREDICATE"));

        assertTrue(hasEvent(result, StructuredParseEventType.PREDICATE_EQUALITY, "leftAlias", "u", "rightAlias", "o"));
        assertTrue(hasEvent(result, StructuredParseEventType.JOIN_USING_COLUMNS, "leftAlias", "o", "rightAlias", "ot"));
        assertTrue(hasEvent(result, StructuredParseEventType.IN_SUBQUERY_PREDICATE, "outerAlias", "o", "innerTable", "customers"));
        assertTrue(hasEvent(result, StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE, "innerTable", "stores", "tokenEventNative", true));
    }

    @Test
    void postgresqlProducesFirstRelationBatchWithoutDelegatingThoseEventTypes() {
        SqlStatementRecord statement = statement("""
                SELECT *
                FROM orders o
                JOIN order_tags ot USING (order_id)
                WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = o.user_id)
                  AND o.customer_id IN (SELECT c.id FROM customers c)
                  AND (o.store_id, o.region_id) IN (SELECT s.id, s.region_id FROM stores s)
                """);

        StructuredParseResult result = FullGrammerTokenEventParserFactory.create(
                        DatabaseType.POSTGRESQL,
                        "16.4",
                        emptyDelegate(SqlDialect.POSTGRES))
                .parser()
                .parseSql(statement, null);

        assertNativeEventTypes(result, Set.of(
                StructuredParseEventType.PREDICATE_EQUALITY,
                StructuredParseEventType.JOIN_USING_COLUMNS,
                StructuredParseEventType.EXISTS_PREDICATE,
                StructuredParseEventType.IN_SUBQUERY_PREDICATE,
                StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE,
                StructuredParseEventType.ROWSET_REFERENCE));
        assertFalse(delegatedEventTypes(result).contains("PREDICATE_EQUALITY"));
        assertFalse(delegatedEventTypes(result).contains("JOIN_USING_COLUMNS"));
        assertFalse(delegatedEventTypes(result).contains("EXISTS_PREDICATE"));
        assertFalse(delegatedEventTypes(result).contains("IN_SUBQUERY_PREDICATE"));
        assertFalse(delegatedEventTypes(result).contains("TUPLE_IN_SUBQUERY_PREDICATE"));

        assertTrue(hasEvent(result, StructuredParseEventType.PREDICATE_EQUALITY, "leftAlias", "u", "rightAlias", "o"));
        assertTrue(hasEvent(result, StructuredParseEventType.JOIN_USING_COLUMNS, "leftAlias", "o", "rightAlias", "ot"));
        assertTrue(hasEvent(result, StructuredParseEventType.IN_SUBQUERY_PREDICATE, "outerAlias", "o", "innerTable", "customers"));
        assertTrue(hasEvent(result, StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE, "innerTable", "stores", "tokenEventNative", true));
    }

    @Test
    void postgresqlSelfJoinColumnCoOccurrenceComesFromFullGrammerNativeEvents() {
        SqlStatementRecord statement = statement("""
                SELECT *
                FROM workflow_tasks curr
                JOIN workflow_tasks prev ON curr.predecessor_id = prev.task_id
                """);

        StructuredParseResult result = FullGrammerTokenEventParserFactory.create(
                        DatabaseType.POSTGRESQL,
                        "16.4",
                        emptyDelegate(SqlDialect.POSTGRES))
                .parser()
                .parseSql(statement, null);

        assertNativeEventTypes(result, Set.of(
                StructuredParseEventType.PREDICATE_EQUALITY,
                StructuredParseEventType.ROWSET_REFERENCE));
        assertTrue(hasEvent(result, StructuredParseEventType.PREDICATE_EQUALITY,
                "leftAlias", "curr", "rightAlias", "prev"));

        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, result).stream()
                .map(FullGrammerNativeRelationEventsTest::relationFingerprint)
                .toList();

        assertEquals(List.of(
                "CO_OCCURRENCE:workflow_tasks.predecessor_id->workflow_tasks.task_id:SQL_LOG_COLUMN_CO_OCCURRENCE"),
                fingerprints);
    }

    @Test
    void postgresqlNativeInSubqueryUsesDefaultOuterAndInnerRowsetForSimpleColumns() {
        SqlStatementRecord statement = statement("""
                SELECT id, doc_title, tags
                FROM documents
                WHERE id IN (
                    SELECT doc_id
                    FROM revisions
                    WHERE change_summary NOT LIKE '%draft%'
                )
                """);

        StructuredParseResult result = FullGrammerTokenEventParserFactory.create(
                        DatabaseType.POSTGRESQL,
                        "16.4",
                        emptyDelegate(SqlDialect.POSTGRES))
                .parser()
                .parseSql(statement, null);

        assertTrue(hasEvent(result,
                StructuredParseEventType.IN_SUBQUERY_PREDICATE,
                "outerAlias",
                "documents",
                "innerTable",
                "revisions"));
        assertTrue(hasEvent(result,
                StructuredParseEventType.IN_SUBQUERY_PREDICATE,
                "outerColumn",
                "id",
                "innerColumn",
                "doc_id"));
    }

    @Test
    void postgresqlNativeInSubqueryIgnoresExpressionProjection() {
        SqlStatementRecord statement = statement("""
                SELECT u.user_id, u.account_status
                FROM application_users u
                WHERE u.account_status IN (
                    SELECT 'STATUS_' || status_label
                    FROM structural_statuses
                    WHERE system_domain = 'USER_ACCOUNTS'
                )
                """);

        StructuredParseResult result = FullGrammerTokenEventParserFactory.create(
                        DatabaseType.POSTGRESQL,
                        "16.4",
                        emptyDelegate(SqlDialect.POSTGRES))
                .parser()
                .parseSql(statement, null);

        assertFalse(hasEvent(result,
                StructuredParseEventType.IN_SUBQUERY_PREDICATE,
                "innerTable",
                "structural_statuses",
                "innerColumn",
                "status_label"));
    }

    @Test
    void mysqlProducesRowsetScopeAndLineageEventsWithoutDelegate() {
        SqlStatementRecord statement = statement("""
                CREATE TRIGGER trg_orders_audit
                AFTER INSERT ON orders
                FOR EACH ROW
                BEGIN
                    CREATE TEMPORARY TABLE tmp_orders AS SELECT NEW.id AS order_id;
                    WITH paid_orders AS (
                        SELECT o.id, o.user_id
                        FROM orders o, users u
                        WHERE o.user_id = u.id
                    )
                    UPDATE users u
                    JOIN (
                        SELECT user_id, SUM(pay_amount) AS total
                        FROM orders
                        GROUP BY user_id
                    ) s ON u.id = s.user_id
                    SET u.total_spent = COALESCE(s.total, u.total_spent);
                END
                """, StatementSourceType.TRIGGER);

        StructuredParseResult result = FullGrammerTokenEventParserFactory.create(
                        DatabaseType.MYSQL,
                        "8.0.36",
                        emptyDelegate(SqlDialect.MYSQL))
                .parser()
                .parseSql(statement, null);

        assertNativeEventTypes(result, Set.of(
                StructuredParseEventType.ROWSET_REFERENCE,
                StructuredParseEventType.CTE_DECLARATION,
                StructuredParseEventType.IGNORED_ROWSET,
                StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION,
                StructuredParseEventType.TRIGGER_TARGET_TABLE,
                StructuredParseEventType.TRIGGER_PSEUDO_ROWSET,
                StructuredParseEventType.WRITE_TARGET,
                StructuredParseEventType.UPDATE_ASSIGNMENT,
                StructuredParseEventType.PROJECTION_ITEM,
                StructuredParseEventType.EXPRESSION_SOURCE));
        assertFalse(delegatedEventTypes(result).contains("ROWSET_REFERENCE"));
        assertFalse(delegatedEventTypes(result).contains("UPDATE_ASSIGNMENT"));
        assertTrue(hasEvent(result, StructuredParseEventType.ROWSET_REFERENCE, "alias", "o", "table", "orders"));
        assertTrue(hasEvent(result, StructuredParseEventType.CTE_DECLARATION, "name", "paid_orders"));
        assertTrue(hasEvent(result, StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION, "table", "tmp_orders"));
        assertTrue(hasEvent(result, StructuredParseEventType.TRIGGER_TARGET_TABLE, "table", "orders"));
        assertTrue(hasEvent(result, StructuredParseEventType.TRIGGER_PSEUDO_ROWSET, "name", "NEW"));
        assertTrue(hasEvent(result, StructuredParseEventType.UPDATE_ASSIGNMENT, "targetColumn", "total_spent"));
        assertTrue(hasEvent(result, StructuredParseEventType.PROJECTION_ITEM, "outputAlias", "s", "outputColumn", "total"));
    }

    @Test
    void postgresqlWithMergeUsingProducesTypedEvents() {
        SqlStatementRecord statement = statement("""
                WITH source_orders AS MATERIALIZED (
                    SELECT o.id, o.customer_id
                    FROM ONLY staging_orders o TABLESAMPLE SYSTEM (10)
                )
                MERGE INTO target_orders t
                USING source_orders s ON t.source_order_id = s.id
                WHEN MATCHED THEN UPDATE SET customer_id = s.customer_id
                WHEN NOT MATCHED THEN INSERT (source_order_id, customer_id) VALUES (s.id, s.customer_id)
                """);

        StructuredParseResult result = FullGrammerTokenEventParserFactory.create(
                        DatabaseType.POSTGRESQL,
                        "16.4",
                        emptyDelegate(SqlDialect.POSTGRES))
                .parser()
                .parseSql(statement, null);

        assertEquals(0, result.attributes().get("fullGrammerSyntaxErrors"));
        assertTrue(hasEvent(result, StructuredParseEventType.CTE_DECLARATION, "name", "source_orders"));
        assertTrue(hasEvent(result, StructuredParseEventType.ROWSET_REFERENCE, "table", "target_orders", "alias", "t"));
        assertTrue(hasEvent(result, StructuredParseEventType.ROWSET_REFERENCE, "table", "source_orders", "alias", "s"));
        assertTrue(hasEvent(result, StructuredParseEventType.PREDICATE_EQUALITY,
                "leftAlias", "t", "rightAlias", "s"));
        assertTrue(hasEvent(result, StructuredParseEventType.PREDICATE_EQUALITY,
                "leftColumn", "source_order_id", "rightColumn", "id"));
        assertTrue(hasEvent(result, StructuredParseEventType.MERGE_WRITE_MAPPING, "targetColumn", "source_order_id"));
        assertTrue(hasEvent(result, StructuredParseEventType.MERGE_WRITE_MAPPING, "targetColumn", "customer_id"));
    }

    @Test
    void postgresqlAnalyzesExpressionTransformsForLineageEvents() {
        SqlStatementRecord statement = statement("""
                WITH user_totals AS (
                    SELECT o.user_id, SUM(o.pay_amount) AS paid_total,
                           DENSE_RANK() OVER (PARTITION BY o.user_id ORDER BY o.created_at DESC) AS order_rank
                    FROM orders o
                    GROUP BY o.user_id, o.created_at
                )
                UPDATE users u
                SET total_spent = COALESCE(t.paid_total, u.total_spent),
                    risk_band = CASE WHEN u.risk_score > 80 THEN 'HIGH' ELSE u.risk_band END
                FROM user_totals t
                WHERE u.id = t.user_id
                """);

        StructuredParseResult result = FullGrammerTokenEventParserFactory.create(
                        DatabaseType.POSTGRESQL,
                        "16.4",
                        emptyDelegate(SqlDialect.POSTGRES))
                .parser()
                .parseSql(statement, null);

        assertTrue(hasEvent(result, StructuredParseEventType.PROJECTION_ITEM,
                "outputColumn", "paid_total", "transformType", "AGGREGATE"));
        assertTrue(hasEvent(result, StructuredParseEventType.PROJECTION_ITEM,
                "outputColumn", "order_rank", "transformType", "WINDOW_DERIVED"));
        assertTrue(hasEvent(result, StructuredParseEventType.UPDATE_ASSIGNMENT,
                "targetColumn", "total_spent", "transformType", "COALESCE"));
        assertTrue(hasEvent(result, StructuredParseEventType.UPDATE_ASSIGNMENT,
                "targetColumn", "risk_band", "flowKind", "CONTROL"));
    }

    @Test
    void mysqlAnalyzesExpressionTransformsForLineageEvents() {
        SqlStatementRecord statement = statement("""
                UPDATE inventory i
                JOIN order_items oi ON i.product_id = oi.product_id
                SET i.reserved_quantity = i.reserved_quantity + oi.quantity,
                    i.audit_status = CASE WHEN oi.quantity > 10 THEN 'REVIEW' ELSE i.audit_status END,
                    i.audit_note = CONCAT(i.audit_note, '-', oi.sku)
                """);

        StructuredParseResult result = FullGrammerTokenEventParserFactory.create(
                        DatabaseType.MYSQL,
                        "8.0.36",
                        emptyDelegate(SqlDialect.MYSQL))
                .parser()
                .parseSql(statement, null);

        assertTrue(hasEvent(result, StructuredParseEventType.UPDATE_ASSIGNMENT,
                "targetColumn", "reserved_quantity", "transformType", "ARITHMETIC"));
        assertTrue(hasEvent(result, StructuredParseEventType.UPDATE_ASSIGNMENT,
                "targetColumn", "audit_status", "flowKind", "CONTROL"));
        assertTrue(hasEvent(result, StructuredParseEventType.UPDATE_ASSIGNMENT,
                "targetColumn", "audit_note", "transformType", "CONCAT_FORMAT"));
    }

    @Test
    void mysqlDoesNotTreatAggregateWordsInsideColumnNamesAsAggregateTransforms() {
        SqlStatementRecord statement = statement("""
                UPDATE account_balances ab
                SET ab.adjusted_limit = LEAST(ab.max_credit_limit * 0.8, 50000.00)
                """);

        StructuredParseResult result = FullGrammerTokenEventParserFactory.create(
                        DatabaseType.MYSQL,
                        "8.0.36",
                        emptyDelegate(SqlDialect.MYSQL))
                .parser()
                .parseSql(statement, null);

        assertTrue(hasEvent(result, StructuredParseEventType.UPDATE_ASSIGNMENT,
                "targetColumn", "adjusted_limit", "transformType", "ARITHMETIC"));
        assertFalse(hasEvent(result, StructuredParseEventType.UPDATE_ASSIGNMENT,
                "targetColumn", "adjusted_limit", "transformType", "AGGREGATE"));
    }

    @Test
    void mysqlRecognizesRunningCdfExpressionAsCumulativeTransform() {
        SqlStatementRecord statement = statement("""
                UPDATE jsh_temp_org_pdf t
                INNER JOIN (
                    SELECT org_id,
                           (@running_sum := @running_sum + weight) / v_total_w AS new_cdf
                    FROM jsh_temp_org_pdf
                    ORDER BY org_id ASC
                ) calculated ON t.org_id = calculated.org_id
                SET t.cdf_end = calculated.new_cdf
                """);

        StructuredParseResult result = FullGrammerTokenEventParserFactory.create(
                        DatabaseType.MYSQL,
                        "8.0.36",
                        emptyDelegate(SqlDialect.MYSQL))
                .parser()
                .parseSql(statement, null);

        assertTrue(hasEvent(result, StructuredParseEventType.PROJECTION_ITEM,
                "outputColumn", "new_cdf", "transformType", "CUMULATIVE"));
    }

    @Test
    void mysqlKeepsOnlyAcceptedSingleSourceCaseLineageForOrgPdfWeight() {
        SqlStatementRecord statement = statement("""
                INSERT INTO jsh_temp_org_pdf (org_id, weight, remark)
                SELECT
                    jo.id,
                    ROUND(
                        (CASE
                            WHEN jo.org_no LIKE '%-HD-%' THEN 100
                            ELSE 50
                        END)
                        *
                        (1 + (SELECT COUNT(*) - 1
                              FROM jsh_organization sub
                              WHERE LEFT(sub.org_no, 12) = LEFT(jo.org_no, 12)
                                AND sub.tenant_id = p_tenant_id
                                AND sub.id NOT IN (
                                    SELECT DISTINCT parent_id
                                    FROM jsh_organization
                                    WHERE parent_id IS NOT NULL
                                )
                        ) * 0.15)
                        *
                        (CASE
                            WHEN jo.org_abr LIKE '%旗舰店%' THEN 2.5
                            ELSE 1.0
                        END)
                    ) AS final_weight,
                    jo.org_abr
                FROM jsh_organization jo
                WHERE jo.tenant_id = p_tenant_id
                  AND jo.parent_id IS NOT NULL
                  AND jo.id NOT IN (
                      SELECT DISTINCT parent_id
                      FROM jsh_organization
                      WHERE parent_id IS NOT NULL
                  )
                """, StatementSourceType.PROCEDURE);

        StructuredParseResult result = FullGrammerTokenEventParserFactory.create(
                        DatabaseType.MYSQL,
                        "8.0.36",
                        emptyDelegate(SqlDialect.MYSQL))
                .parser()
                .parseSql(statement, null);

        Set<String> lineages = new TokenEventDataLineageExtractor().extract(statement, result).stream()
                .map(FullGrammerNativeRelationEventsTest::lineageFingerprint)
                .collect(Collectors.toSet());

        assertTrue(lineages.contains("CONTROL:CASE_WHEN:jsh_organization.org_no->jsh_temp_org_pdf.weight"));
        assertTrue(lineages.contains("CONTROL:CASE_WHEN:jsh_organization.org_abr->jsh_temp_org_pdf.weight"));
        assertFalse(lineages.contains("CONTROL:CASE_WHEN:jsh_organization.id->jsh_temp_org_pdf.weight"));
        assertFalse(lineages.contains("CONTROL:CASE_WHEN:jsh_organization.tenant_id->jsh_temp_org_pdf.weight"));
        assertFalse(lineages.contains(
                "CONTROL:CASE_WHEN:jsh_organization.id,jsh_organization.org_abr,jsh_organization.org_no,jsh_organization.tenant_id->jsh_temp_org_pdf.weight"));
    }

    @Test
    void mysqlDirectFkAssignmentProducesNativeRelationPredicate() {
        SqlStatementRecord statement = statement("""
                UPDATE orders AS target
                JOIN accounts AS a ON target.user_id = a.user_id
                SET target.audit_account_id = a.id
                """);

        StructuredParseResult result = FullGrammerTokenEventParserFactory.create(
                        DatabaseType.MYSQL,
                        "8.0.36",
                        emptyDelegate(SqlDialect.MYSQL))
                .parser()
                .parseSql(statement, null);

        assertTrue(hasEvent(result, StructuredParseEventType.UPDATE_ASSIGNMENT,
                "targetColumn", "audit_account_id"));
        assertTrue(hasEvent(result, StructuredParseEventType.PREDICATE_EQUALITY,
                "leftAlias", "target", "rightAlias", "a"));
        assertTrue(hasEvent(result, StructuredParseEventType.PREDICATE_EQUALITY,
                "leftColumn", "audit_account_id", "rightColumn", "id"));
    }

    @Test
    void mysqlDialectEventsDoNotLeakPostgresOnlyRowsets() {
        SqlStatementRecord statement = statement("""
                SELECT *
                FROM ONLY orders TABLESAMPLE SYSTEM (10), ROWS FROM(generate_series(1, 3)) AS g(id)
                """);

        StructuredParseResult result = FullGrammerTokenEventParserFactory.create(
                        DatabaseType.MYSQL,
                        "8.0.36",
                        emptyDelegate(SqlDialect.MYSQL))
                .parser()
                .parseSql(statement, null);

        assertFalse(hasEvent(result, StructuredParseEventType.ROWSET_REFERENCE, "table", "ONLY"));
        assertFalse(hasEvent(result, StructuredParseEventType.ROWSET_REFERENCE, "table", "ROWS"));
    }

    @Test
    void postgresqlDialectEventsDoNotLeakMysqlOnlyPseudoRowsets() {
        SqlStatementRecord statement = statement("""
                SELECT *
                FROM orders PARTITION (p202501) o FORCE INDEX FOR JOIN (idx_orders_user)
                JOIN JSON_TABLE(o.payload, '$[*]' COLUMNS (item_id INT PATH '$.item_id')) jt ON jt.item_id = o.id
                """);

        StructuredParseResult result = FullGrammerTokenEventParserFactory.create(
                        DatabaseType.POSTGRESQL,
                        "16.4",
                        emptyDelegate(SqlDialect.POSTGRES))
                .parser()
                .parseSql(statement, null);

        assertFalse(hasEvent(result, StructuredParseEventType.ROWSET_REFERENCE, "table", "PARTITION"));
        assertFalse(hasEvent(result, StructuredParseEventType.ROWSET_REFERENCE, "table", "JSON_TABLE"));
        assertFalse(hasEvent(result, StructuredParseEventType.ROWSET_REFERENCE, "table", "FORCE"));
    }

    private StructuredSqlParser emptyDelegate(SqlDialect dialect) {
        return (statement, context) -> new StructuredParseResult(
                "EMPTY_DELEGATE",
                dialect.name(),
                statement.sourceName(),
                List.of(),
                List.of(),
                Map.of("eventBuilder", "EMPTY_DELEGATE"));
    }

    private SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, Map.of());
    }

    private SqlStatementRecord statement(String sql, StatementSourceType sourceType) {
        return new SqlStatementRecord(sql, sourceType, "fixture.sql", 1, 1, Map.of());
    }

    private void assertNativeEventTypes(StructuredParseResult result, Set<StructuredParseEventType> expected) {
        Set<String> actual = stringSet(result.attributes().get("fullGrammerNativeEventTypes"));
        assertTrue(actual.containsAll(expected.stream().map(Enum::name).collect(Collectors.toSet())),
                () -> "Expected full-grammer native event types " + expected + " to be contained in " + actual);
        assertTrue(stringSet(result.attributes().get("fullGrammerDelegatedEventTypes")).isEmpty(),
                () -> "full-grammer native events should not rely on production delegate events: "
                        + result.attributes().get("fullGrammerDelegatedEventTypes"));
        assertTrue(stringSet(result.attributes().get("fullGrammerBridgedEventTypes")).isEmpty(),
                () -> "full-grammer native events should not rely on token-span bridge events: "
                        + result.attributes().get("fullGrammerBridgedEventTypes"));
    }

    private Set<String> delegatedEventTypes(StructuredParseResult result) {
        return stringSet(result.attributes().get("fullGrammerDelegatedEventTypes"));
    }

    private static String lineageFingerprint(DataLineageCandidate candidate) {
        String sources = candidate.sources().stream()
                .sorted(Comparator.comparing(Endpoint::normalizedKey))
                .map(endpoint -> endpoint.table().tableName() + "." + endpoint.column().columnName())
                .collect(Collectors.joining(","));
        return candidate.flowKind().name() + ":"
                + candidate.transformType().name() + ":"
                + sources + "->"
                + candidate.target().table().tableName() + "." + candidate.target().column().columnName();
    }

    private static String relationFingerprint(RelationshipCandidate relation) {
        String evidenceType = relation.evidence().isEmpty() ? "NO_EVIDENCE" : relation.evidence().get(0).type().name();
        return relation.relationType() + ":"
                + relation.source().displayName() + "->" + relation.target().displayName()
                + ":" + evidenceType;
    }

    @SuppressWarnings("unchecked")
    private Set<String> stringSet(Object value) {
        return ((List<String>) value).stream().collect(Collectors.toSet());
    }

    private boolean hasEvent(
            StructuredParseResult result,
            StructuredParseEventType type,
            String key1,
            String value1,
            String key2,
            String value2
    ) {
        return result.events().stream()
                .filter(event -> event.type() == type)
                .anyMatch(event -> value1.equals(event.attributes().get(key1))
                        && value2.equals(event.attributes().get(key2)));
    }

    private boolean hasEvent(
            StructuredParseResult result,
            StructuredParseEventType type,
            String key,
            String value
    ) {
        return result.events().stream()
                .filter(event -> event.type() == type)
                .anyMatch(event -> value.equals(event.attributes().get(key)));
    }

    private boolean hasEvent(
            StructuredParseResult result,
            StructuredParseEventType type,
            String key1,
            String value1,
            String key2,
            boolean value2
    ) {
        return result.events().stream()
                .filter(event -> event.type() == type)
                .anyMatch(event -> value1.equals(event.attributes().get(key1))
                        && Boolean.valueOf(value2).equals(event.attributes().get(key2)));
    }

}
