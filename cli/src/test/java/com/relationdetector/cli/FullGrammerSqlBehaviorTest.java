package com.relationdetector.cli;

import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.fullgrammer.*;
import com.relationdetector.core.lineage.*;
import com.relationdetector.core.relation.*;
import com.relationdetector.core.tokenevent.*;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

class FullGrammerSqlBehaviorTest {
    @Test
    void mysqlProducesRelationPredicateEventsForCommonSqlForms() {
        SqlStatementRecord statement = statement("""
                SELECT *
                FROM orders o
                JOIN order_tags ot USING (order_id)
                WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = o.user_id)
                  AND o.customer_id IN (SELECT c.id FROM customers c)
                  AND (o.store_id, o.region_id) IN (SELECT s.id, s.region_id FROM stores s)
                """);

        StructuredParseResult result = parse(DatabaseType.MYSQL, "8.0.36", SqlDialect.MYSQL, statement);

        assertTrue(hasEvent(result, StructuredParseEventType.EXISTS_PREDICATE, "leftAlias", "u", "rightAlias", "o"));
        assertTrue(hasEvent(result, StructuredParseEventType.JOIN_USING_COLUMNS, "leftAlias", "o", "rightAlias", "ot"));
        assertTrue(hasEvent(result, StructuredParseEventType.IN_SUBQUERY_PREDICATE, "outerAlias", "o", "innerTable", "customers"));
        assertTrue(hasEvent(result, StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE, "innerTable", "stores", "tokenEventNative", true));

        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, result).stream()
                .map(FullGrammerSqlBehaviorTest::relationFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains("FK_LIKE:orders.user_id->users.id:SQL_LOG_EXISTS"));
    }

    @Test
    void postgresqlProducesRelationPredicateEventsForCommonSqlForms() {
        SqlStatementRecord statement = statement("""
                SELECT *
                FROM orders o
                JOIN order_tags ot USING (order_id)
                WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = o.user_id)
                  AND o.customer_id IN (SELECT c.id FROM customers c)
                  AND (o.store_id, o.region_id) IN (SELECT s.id, s.region_id FROM stores s)
                """);

        StructuredParseResult result = parse(DatabaseType.POSTGRESQL, "16.4", SqlDialect.POSTGRES, statement);

        assertTrue(hasEvent(result, StructuredParseEventType.EXISTS_PREDICATE, "leftAlias", "u", "rightAlias", "o"));
        assertTrue(hasEvent(result, StructuredParseEventType.JOIN_USING_COLUMNS, "leftAlias", "o", "rightAlias", "ot"));
        assertTrue(hasEvent(result, StructuredParseEventType.IN_SUBQUERY_PREDICATE, "outerAlias", "o", "innerTable", "customers"));
        assertTrue(hasEvent(result, StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE, "innerTable", "stores", "tokenEventNative", true));
    }

    @Test
    void postgresqlSelfJoinColumnCoOccurrenceIsExtracted() {
        SqlStatementRecord statement = statement("""
                SELECT *
                FROM workflow_tasks curr
                JOIN workflow_tasks prev ON curr.predecessor_id = prev.task_id
                """);

        StructuredParseResult result = parse(DatabaseType.POSTGRESQL, "16.4", SqlDialect.POSTGRES, statement);
        assertTrue(hasEvent(result, StructuredParseEventType.PREDICATE_EQUALITY,
                "leftAlias", "curr", "rightAlias", "prev"));

        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, result).stream()
                .map(FullGrammerSqlBehaviorTest::relationFingerprint)
                .toList();

        assertEquals(List.of(
                "CO_OCCURRENCE:workflow_tasks.predecessor_id->workflow_tasks.task_id:SQL_LOG_COLUMN_CO_OCCURRENCE"),
                fingerprints);
    }

    @Test
    void postgresqlExistsPredicatesUseExistsEvidenceWithoutDuplicateJoinEvidence() {
        SqlStatementRecord statement = statement("""
                SELECT *
                FROM orders o
                JOIN customers c ON c.id = o.customer_id
                WHERE EXISTS (
                    SELECT 1
                    FROM payments p
                    WHERE p.order_id = o.id
                )
                  AND NOT EXISTS (
                    SELECT 1
                    FROM refunds r
                    WHERE r.order_id = o.id
                )
                  AND EXISTS (
                    SELECT 1
                    FROM LATERAL (
                        SELECT ae.order_id
                        FROM audit_events ae
                        WHERE ae.order_id = o.id
                    ) audit_probe
                  )
                """);

        StructuredParseResult result = parse(DatabaseType.POSTGRESQL, "16.4", SqlDialect.POSTGRES, statement);

        assertTrue(hasEvent(result, StructuredParseEventType.PREDICATE_EQUALITY,
                "leftAlias", "c", "rightAlias", "o"));
        assertFalse(hasEvent(result, StructuredParseEventType.PREDICATE_EQUALITY,
                "leftAlias", "p", "rightAlias", "o"));
        assertFalse(hasEvent(result, StructuredParseEventType.PREDICATE_EQUALITY,
                "leftAlias", "r", "rightAlias", "o"));
        assertFalse(hasEvent(result, StructuredParseEventType.PREDICATE_EQUALITY,
                "leftAlias", "ae", "rightAlias", "o"));

        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, result).stream()
                .map(FullGrammerSqlBehaviorTest::relationFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(
                "FK_LIKE:audit_events.order_id->orders.id:SQL_LOG_EXISTS",
                "FK_LIKE:orders.customer_id->customers.id:SQL_LOG_JOIN",
                "FK_LIKE:payments.order_id->orders.id:SQL_LOG_EXISTS",
                "FK_LIKE:refunds.order_id->orders.id:SQL_LOG_EXISTS"),
                fingerprints);
    }

    @Test
    void postgresqlExistsAmbiguousEqualityStillProducesColumnCoOccurrence() {
        SqlStatementRecord statement = statement("""
                SELECT *
                FROM pg10_accounts a
                WHERE EXISTS (
                    SELECT 1
                    FROM pg10_ledger l
                    WHERE l.account_id = a.account_id
                )
                """);

        StructuredParseResult result = parse(DatabaseType.POSTGRESQL, "16.4", SqlDialect.POSTGRES, statement);

        assertFalse(hasEvent(result, StructuredParseEventType.PREDICATE_EQUALITY,
                "leftAlias", "l", "rightAlias", "a"));

        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, result).stream()
                .map(FullGrammerSqlBehaviorTest::relationFingerprint)
                .toList();

        assertEquals(List.of(
                "CO_OCCURRENCE:pg10_ledger.account_id->pg10_accounts.account_id:SQL_LOG_COLUMN_CO_OCCURRENCE"),
                fingerprints);
    }

    @Test
    void postgresqlInSubqueryUsesDefaultOuterAndInnerRowsetForSimpleColumns() {
        SqlStatementRecord statement = statement("""
                SELECT id, doc_title, tags
                FROM documents
                WHERE id IN (
                    SELECT doc_id
                    FROM revisions
                    WHERE change_summary NOT LIKE '%draft%'
                )
                """);

        StructuredParseResult result = parse(DatabaseType.POSTGRESQL, "16.4", SqlDialect.POSTGRES, statement);

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
    void postgresqlInSubqueryIgnoresExpressionProjection() {
        SqlStatementRecord statement = statement("""
                SELECT u.user_id, u.account_status
                FROM application_users u
                WHERE u.account_status IN (
                    SELECT 'STATUS_' || status_label
                    FROM structural_statuses
                    WHERE system_domain = 'USER_ACCOUNTS'
                )
                """);

        StructuredParseResult result = parse(DatabaseType.POSTGRESQL, "16.4", SqlDialect.POSTGRES, statement);

        assertFalse(hasEvent(result,
                StructuredParseEventType.IN_SUBQUERY_PREDICATE,
                "innerTable",
                "structural_statuses",
                "innerColumn",
                "status_label"));
    }

    @Test
    void postgresqlDoesNotCreateRelationsFromLiteralFiltersLikePredicatesOrExpressionInSubqueries() {
        SqlStatementRecord statement = statement("""
                SELECT d.id, d.doc_title
                FROM documents d, revisions r
                WHERE d.doc_title LIKE 'ARCHIVE%'
                  AND r.change_summary NOT LIKE '%draft%'
                  AND d.status = 'ACTIVE'
                  AND r.is_approved = true
                  AND d.channel_type IN ('WEB', 'MOBILE_APP')
                  AND 'VER_' || d.major_version IN (
                      SELECT 'VER_' || b.minor_version
                      FROM beta_builds b
                  )
                  AND d.id IN (
                      SELECT SUM(soi.quantity_sold)
                      FROM sales_order_items soi
                      WHERE soi.archive_status = 'PENDING'
                      HAVING SUM(soi.quantity_sold) > 10
                  )
                """);

        StructuredParseResult result = parse(DatabaseType.POSTGRESQL, "16.4", SqlDialect.POSTGRES, statement);

        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, result)
                .stream()
                .map(FullGrammerSqlBehaviorTest::relationFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(), fingerprints);
    }

    @Test
    void postgresqlDoesNotTreatProcedureParametersAsPhysicalColumns() {
        SqlStatementRecord statement = statement("""
                CREATE OR REPLACE FUNCTION report_room_usage(
                    p_room_id int,
                    p_start_date date,
                    p_end_date date
                ) RETURNS void
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    PERFORM count(*)
                    FROM pg14_room_bookings rb
                    WHERE rb.room_id = p_room_id
                      AND rb.booked_during && tsrange(p_start_date::timestamp, p_end_date::timestamp);
                END;
                $$;
                """, StatementSourceType.FUNCTION);

        StructuredParseResult result = parse(DatabaseType.POSTGRESQL, "16.4", SqlDialect.POSTGRES, statement);

        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, result).stream()
                .map(FullGrammerSqlBehaviorTest::relationFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.stream().noneMatch(fingerprint -> fingerprint.contains("p_room_id")),
                () -> "Procedure parameters must not be emitted as relationship endpoints: " + fingerprints);
    }

    @Test
    void postgresqlCteProjectionRelationResolvesToPhysicalSourceColumn() {
        SqlStatementRecord statement = statement("""
                WITH user_aggregation AS (
                    SELECT ae.user_id, count(*) AS event_count
                    FROM pg16_analytics_events ae
                    GROUP BY ae.user_id
                ),
                enriched_users AS (
                    SELECT ua.user_id, up.created_at
                    FROM user_aggregation ua
                    JOIN pg16_user_profiles up ON ua.user_id = up.user_id
                )
                SELECT * FROM enriched_users;
                """);

        StructuredParseResult result = parse(DatabaseType.POSTGRESQL, "16.4", SqlDialect.POSTGRES, statement);

        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, result).stream()
                .map(FullGrammerSqlBehaviorTest::relationFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains(
                "CO_OCCURRENCE:pg16_analytics_events.user_id->pg16_user_profiles.user_id:SQL_LOG_COLUMN_CO_OCCURRENCE"),
                () -> "CTE projection alias ua.user_id must resolve back to pg16_analytics_events.user_id: " + fingerprints);
    }

    @Test
    void postgresqlMergeUsingCteResolvesRelationshipAndInsertLineage() {
        SqlStatementRecord statement = statement("""
                WITH update_batch AS (
                    SELECT
                        pu.sku,
                        pu.new_price,
                        pu.stock_adjustment,
                        pu.attribute_updates,
                        pu.approver
                    FROM pg17_price_updates pu
                    WHERE pu.processed = false
                )
                MERGE INTO pg17_product_catalog pc
                USING update_batch ub
                ON pc.sku = ub.sku
                WHEN MATCHED THEN
                    UPDATE SET
                        current_price = ub.new_price,
                        stock_level = pc.stock_level + COALESCE(ub.stock_adjustment, 0),
                        attributes = pc.attributes || COALESCE(ub.attribute_updates, '{}'::jsonb),
                        version = pc.version + 1
                WHEN NOT MATCHED AND ub.approver IS NOT NULL THEN
                    INSERT (sku, current_price, stock_level, attributes, updated_by)
                    VALUES (ub.sku, ub.new_price, COALESCE(ub.stock_adjustment, 0),
                            COALESCE(ub.attribute_updates, '{}'::jsonb), ub.approver)
                RETURNING pc.sku;
                """);

        StructuredParseResult result = parse(DatabaseType.POSTGRESQL, "17.5", SqlDialect.POSTGRES, statement);

        List<String> relationships = new TokenEventRelationExtractor().extract(statement, result).stream()
                .map(FullGrammerSqlBehaviorTest::relationFingerprint)
                .sorted()
                .toList();
        Set<String> lineages = new TokenEventDataLineageExtractor().extract(statement, result).stream()
                .map(FullGrammerSqlBehaviorTest::lineageFingerprint)
                .collect(Collectors.toSet());

        assertTrue(relationships.contains(
                "CO_OCCURRENCE:pg17_product_catalog.sku->pg17_price_updates.sku:SQL_LOG_COLUMN_CO_OCCURRENCE"),
                () -> "MERGE USING CTE should resolve ub.sku back to pg17_price_updates.sku: " + relationships);
        assertTrue(lineages.contains(
                "VALUE:DIRECT:pg17_price_updates.sku->pg17_product_catalog.sku"), () -> lineages.toString());
        assertTrue(lineages.contains(
                "VALUE:DIRECT:pg17_price_updates.new_price->pg17_product_catalog.current_price"), () -> lineages.toString());
        assertTrue(lineages.contains(
                "VALUE:COALESCE:pg17_price_updates.stock_adjustment,pg17_product_catalog.stock_level->pg17_product_catalog.stock_level"),
                () -> lineages.toString());
        assertTrue(lineages.contains(
                "VALUE:COALESCE:pg17_price_updates.attribute_updates,pg17_product_catalog.attributes->pg17_product_catalog.attributes"),
                () -> lineages.toString());
        assertTrue(lineages.contains(
                "VALUE:DIRECT:pg17_price_updates.approver->pg17_product_catalog.updated_by"), () -> lineages.toString());
    }

    @Test
    void postgresqlDataModifyingMergeCteResolvesRelationship() {
        SqlStatementRecord statement = statement("""
                WITH update_batch AS (
                    SELECT pu.sku, pu.new_price
                    FROM pg17_price_updates pu
                    WHERE pu.processed = false
                ),
                merge_results AS (
                    MERGE INTO pg17_product_catalog pc
                    USING update_batch ub
                    ON pc.sku = ub.sku
                    WHEN MATCHED THEN
                        UPDATE SET current_price = ub.new_price
                    RETURNING pc.sku
                )
                SELECT mr.sku
                FROM merge_results mr;
                """);

        StructuredParseResult result = parse(DatabaseType.POSTGRESQL, "17.5", SqlDialect.POSTGRES, statement);

        List<String> relationships = new TokenEventRelationExtractor().extract(statement, result).stream()
                .map(FullGrammerSqlBehaviorTest::relationFingerprint)
                .sorted()
                .toList();

        assertTrue(relationships.contains(
                "CO_OCCURRENCE:pg17_product_catalog.sku->pg17_price_updates.sku:SQL_LOG_COLUMN_CO_OCCURRENCE"),
                () -> "MERGE inside data-modifying CTE should resolve ub.sku back to pg17_price_updates.sku: "
                        + relationships + " events=" + result.events()
                        + " warnings=" + result.warnings()
                        + " attributes=" + result.attributes());
    }

    @Test
    void mysqlTriggerProcedureScopeAndLineageEventsAreExtracted() {
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

        StructuredParseResult result = parse(DatabaseType.MYSQL, "8.0.36", SqlDialect.MYSQL, statement);

        assertTrue(hasEvent(result, StructuredParseEventType.ROWSET_REFERENCE, "alias", "o", "table", "orders"));
        assertTrue(hasEvent(result, StructuredParseEventType.CTE_DECLARATION, "name", "paid_orders"));
        assertTrue(hasEvent(result, StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION, "table", "tmp_orders"));
        assertTrue(hasEvent(result, StructuredParseEventType.TRIGGER_TARGET_TABLE, "table", "orders"));
        assertTrue(hasEvent(result, StructuredParseEventType.TRIGGER_PSEUDO_ROWSET, "name", "NEW"));
        assertTrue(hasEvent(result, StructuredParseEventType.UPDATE_ASSIGNMENT, "targetColumn", "total_spent"));
        assertTrue(hasEvent(result, StructuredParseEventType.PROJECTION_ITEM, "outputAlias", "s", "outputColumn", "total"));
    }

    @Test
    void postgresqlMergeUsingProducesRelationshipAndWriteMappingEvents() {
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

        StructuredParseResult result = parse(DatabaseType.POSTGRESQL, "16.4", SqlDialect.POSTGRES, statement);

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

        StructuredParseResult result = parse(DatabaseType.POSTGRESQL, "16.4", SqlDialect.POSTGRES, statement);

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

        StructuredParseResult result = parse(DatabaseType.MYSQL, "8.0.36", SqlDialect.MYSQL, statement);

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

        StructuredParseResult result = parse(DatabaseType.MYSQL, "8.0.36", SqlDialect.MYSQL, statement);

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

        StructuredParseResult result = parse(DatabaseType.MYSQL, "8.0.36", SqlDialect.MYSQL, statement);

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

        StructuredParseResult result = parse(DatabaseType.MYSQL, "8.0.36", SqlDialect.MYSQL, statement);

        Set<String> lineages = new TokenEventDataLineageExtractor().extract(statement, result).stream()
                .map(FullGrammerSqlBehaviorTest::lineageFingerprint)
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

        StructuredParseResult result = parse(DatabaseType.MYSQL, "8.0.36", SqlDialect.MYSQL, statement);

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

        StructuredParseResult result = parse(DatabaseType.MYSQL, "8.0.36", SqlDialect.MYSQL, statement);

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

        StructuredParseResult result = parse(DatabaseType.POSTGRESQL, "16.4", SqlDialect.POSTGRES, statement);

        assertFalse(hasEvent(result, StructuredParseEventType.ROWSET_REFERENCE, "table", "PARTITION"));
        assertFalse(hasEvent(result, StructuredParseEventType.ROWSET_REFERENCE, "table", "JSON_TABLE"));
        assertFalse(hasEvent(result, StructuredParseEventType.ROWSET_REFERENCE, "table", "FORCE"));
    }

    private StructuredParseResult parse(
            DatabaseType databaseType,
            String version,
            SqlDialect dialect,
            SqlStatementRecord statement
    ) {
        return FullGrammerTokenEventParserFactory.create(
                        databaseType,
                        version,
                        new TokenEventStructuredSqlParser(dialect))
                .parser()
                .parseSql(statement, null);
    }

    private SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, Map.of());
    }

    private SqlStatementRecord statement(String sql, StatementSourceType sourceType) {
        return new SqlStatementRecord(sql, sourceType, "fixture.sql", 1, 1, Map.of());
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
