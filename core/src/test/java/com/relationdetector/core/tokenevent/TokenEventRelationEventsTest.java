package com.relationdetector.core.tokenevent;

import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.lineage.*;
import com.relationdetector.core.relation.*;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

class TokenEventRelationEventsTest {
    @Test
    void emitsNativeRelationEventsForJoinUsingRawEqualityExistsAndInPredicates() {
        SqlStatementRecord statement = statement("""
                SELECT *
                FROM orders o
                JOIN users u ON o.user_id = u.id
                JOIN order_tags ot USING (order_id)
                WHERE EXISTS (
                    SELECT 1 FROM accounts a WHERE a.id = u.account_id
                )
                AND o.customer_id IN (
                    SELECT c.id FROM customers c
                )
                AND (o.region_id, o.market_id) IN (
                    SELECT r.id, r.market_id FROM regions r
                )
                """);

        StructuredParseResult result = new TokenEventStructuredSqlParser(SqlDialect.MYSQL).parseSql(statement, null);

        assertTrue(hasEvent(result, StructuredParseEventType.JOIN_USING_COLUMNS));
        assertTrue(hasEvent(result, StructuredParseEventType.PREDICATE_EQUALITY));
        assertTrue(hasEvent(result, StructuredParseEventType.EXISTS_PREDICATE));
        assertTrue(hasEvent(result, StructuredParseEventType.IN_SUBQUERY_PREDICATE));
        assertTrue(hasEvent(result, StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE));
    }

    @Test
    void extractsBasicFkLikeRelationFromTokenEventNativeEventsUsingTokenEventEventsOnly() {
        SqlStatementRecord statement = statement("""
                SELECT *
                FROM orders o
                JOIN users u ON o.user_id = u.id
                """);
        StructuredParseResult full = new TokenEventStructuredSqlParser(SqlDialect.MYSQL).parseSql(statement, null);
        StructuredParseResult tokenEventOnly = new StructuredParseResult(
                full.backend(),
                full.dialect(),
                full.sourceName(),
                full.events().stream()
                        .filter(event -> event.type() == StructuredParseEventType.ROWSET_REFERENCE
                                || event.type() == StructuredParseEventType.PREDICATE_EQUALITY)
                        .toList(),
                full.warnings(),
                full.attributes());

        List<RelationshipCandidate> relationships = new TokenEventRelationExtractor().extract(statement, tokenEventOnly);

        assertEquals(List.of("FK_LIKE:orders.user_id->users.id:SQL_LOG_JOIN"),
                relationships.stream().map(this::relationFingerprint).toList());
    }

    @Test
    void extractsColumnCoOccurrenceForExplicitSelfJoinAliases() {
        SqlStatementRecord employees = statement("""
                SELECT *
                FROM hr_employees e
                LEFT JOIN hr_employees manager ON e.manager_id = manager.emp_id
                """);
        SqlStatementRecord tasks = statement("""
                SELECT *
                FROM workflow_tasks curr
                JOIN workflow_tasks prev ON curr.predecessor_id = prev.task_id
                """);

        assertEquals(List.of(
                        "CO_OCCURRENCE:hr_employees.manager_id->hr_employees.emp_id:SQL_LOG_COLUMN_CO_OCCURRENCE"),
                new TokenEventRelationExtractor().extract(employees,
                                tokenEventRelationOnly(new TokenEventStructuredSqlParser(SqlDialect.POSTGRES).parseSql(employees, null)))
                        .stream()
                        .map(this::relationFingerprint)
                        .sorted()
                        .toList());
        assertEquals(List.of(
                        "CO_OCCURRENCE:workflow_tasks.predecessor_id->workflow_tasks.task_id:SQL_LOG_COLUMN_CO_OCCURRENCE"),
                new TokenEventRelationExtractor().extract(tasks,
                                tokenEventRelationOnly(new TokenEventStructuredSqlParser(SqlDialect.POSTGRES).parseSql(tasks, null)))
                        .stream()
                        .map(this::relationFingerprint)
                        .sorted()
                        .toList());
    }

    @Test
    void doesNotEmitColumnCoOccurrenceForSameAliasRowComparison() {
        SqlStatementRecord statement = statement("""
                SELECT *
                FROM accounts a
                WHERE a.left_col = a.right_col
                """);

        List<RelationshipCandidate> relationships = new TokenEventRelationExtractor().extract(statement,
                tokenEventRelationOnly(new TokenEventStructuredSqlParser(SqlDialect.POSTGRES).parseSql(statement, null)));

        assertFalse(relationships.stream()
                .map(this::relationFingerprint)
                .anyMatch(fingerprint -> fingerprint.contains("accounts.left_col->accounts.right_col")));
    }

    @Test
    void extractsExistsAndScalarInRelationsFromTokenEventNativeEventsUsingTokenEventEventsOnly() {
        SqlStatementRecord statement = statement("""
                SELECT *
                FROM orders o
                JOIN users u ON o.user_id = u.id
                WHERE EXISTS (
                    SELECT 1 FROM accounts a WHERE a.id = u.account_id
                )
                AND o.customer_id IN (
                    SELECT c.id FROM customers c
                )
                """);
        StructuredParseResult full = new TokenEventStructuredSqlParser(SqlDialect.MYSQL).parseSql(statement, null);
        StructuredParseResult tokenEventOnly = new StructuredParseResult(
                full.backend(),
                full.dialect(),
                full.sourceName(),
                full.events().stream()
                        .filter(event -> event.type() == StructuredParseEventType.ROWSET_REFERENCE
                                || event.type() == StructuredParseEventType.EXISTS_PREDICATE
                                || event.type() == StructuredParseEventType.IN_SUBQUERY_PREDICATE)
                        .toList(),
                full.warnings(),
                full.attributes());

        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, tokenEventOnly)
                .stream()
                .map(this::relationFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(
                "FK_LIKE:orders.customer_id->customers.id:SQL_LOG_SUBQUERY_IN",
                "FK_LIKE:users.account_id->accounts.id:SQL_LOG_EXISTS"), fingerprints);
    }

    @Test
    void extractsTupleInRelationsFromTokenEventNativeEventsUsingTokenEventEventsOnly() {
        SqlStatementRecord statement = statement("""
                SELECT *
                FROM orders o
                WHERE (o.region_id, o.market_id) IN (
                    SELECT r.id, r.market_id FROM regions r
                )
                """);
        StructuredParseResult full = new TokenEventStructuredSqlParser(SqlDialect.POSTGRES).parseSql(statement, null);
        StructuredParseResult tokenEventOnly = new StructuredParseResult(
                full.backend(),
                full.dialect(),
                full.sourceName(),
                full.events().stream()
                        .filter(event -> event.type() == StructuredParseEventType.ROWSET_REFERENCE
                                || event.type() == StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE)
                        .toList(),
                full.warnings(),
                full.attributes());

        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, tokenEventOnly)
                .stream()
                .map(this::relationFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(
                "FK_LIKE:orders.market_id->regions.market_id:SQL_LOG_SUBQUERY_IN",
                "FK_LIKE:orders.region_id->regions.id:SQL_LOG_SUBQUERY_IN"), fingerprints);
    }

    @Test
    void emitsNativeRowsetScopeEventsForCteTempTableAndTriggerPseudoRows() {
        SqlStatementRecord statement = statement("""
                CREATE TRIGGER trg_orders_audit
                AFTER UPDATE ON orders
                FOR EACH ROW
                BEGIN
                    CREATE TEMPORARY TABLE tmp_changed_users AS
                    SELECT NEW.user_id AS user_id;

                    WITH recent_orders AS (
                        SELECT o.user_id FROM orders o
                    )
                    SELECT *
                    FROM recent_orders ro
                    JOIN users u ON ro.user_id = u.id;
                END
                """);

        StructuredParseResult result = new TokenEventStructuredSqlParser(SqlDialect.MYSQL).parseSql(statement, null);

        assertTrue(hasEvent(result, StructuredParseEventType.CTE_DECLARATION));
        assertTrue(hasEvent(result, StructuredParseEventType.IGNORED_ROWSET));
        assertTrue(hasEvent(result, StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION));
        assertTrue(hasEvent(result, StructuredParseEventType.TRIGGER_TARGET_TABLE));
        assertTrue(hasEvent(result, StructuredParseEventType.TRIGGER_PSEUDO_ROWSET));
    }

    @Test
    void extractsRelationsFromTokenEventNativeRowsetReferencesWithoutCurrentTableEvents() {
        SqlStatementRecord statement = statement("""
                SELECT *
                FROM orders o, users u
                JOIN accounts a ON u.account_id = a.id
                WHERE o.user_id = u.id
                """);
        StructuredParseResult full = new TokenEventStructuredSqlParser(SqlDialect.MYSQL).parseSql(statement, null);
        StructuredParseResult tokenEventOnly = new StructuredParseResult(
                full.backend(),
                full.dialect(),
                full.sourceName(),
                full.events().stream()
                        .filter(event -> event.type() == StructuredParseEventType.ROWSET_REFERENCE
                                || event.type() == StructuredParseEventType.PREDICATE_EQUALITY)
                        .toList(),
                full.warnings(),
                full.attributes());

        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, tokenEventOnly)
                .stream()
                .map(this::relationFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(
                "FK_LIKE:orders.user_id->users.id:SQL_LOG_JOIN",
                "FK_LIKE:users.account_id->accounts.id:SQL_LOG_JOIN"), fingerprints);
    }

    @Test
    void extractsColumnCoOccurrenceForAmbiguousDmlEqualityFromTokenEventNativeEvents() {
        SqlStatementRecord statement = statement("""
                UPDATE warehouse_inventory wi
                JOIN order_items oi ON wi.product_id = oi.product_id
                SET wi.stock_reserved = wi.stock_reserved + oi.quantity
                """);
        StructuredParseResult full = new TokenEventStructuredSqlParser(SqlDialect.MYSQL).parseSql(statement, null);
        StructuredParseResult tokenEventOnly = new StructuredParseResult(
                full.backend(),
                full.dialect(),
                full.sourceName(),
                full.events().stream()
                        .filter(event -> event.type() == StructuredParseEventType.ROWSET_REFERENCE
                                || event.type() == StructuredParseEventType.WRITE_TARGET
                                || event.type() == StructuredParseEventType.PREDICATE_EQUALITY)
                        .toList(),
                full.warnings(),
                full.attributes());

        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, tokenEventOnly)
                .stream()
                .map(this::relationFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(
                "CO_OCCURRENCE:warehouse_inventory.product_id->order_items.product_id:SQL_LOG_COLUMN_CO_OCCURRENCE"),
                fingerprints);
    }

    @Test
    void extractsRelationsThroughDerivedProjectionFromTokenEventNativeEvents() {
        SqlStatementRecord statement = statement("""
                UPDATE users u
                LEFT JOIN (
                    SELECT user_id, SUM(pay_amount) AS actual_total
                    FROM orders
                    GROUP BY user_id
                ) o_summary ON u.id = o_summary.user_id
                SET u.total_spent = COALESCE(o_summary.actual_total, 0)
                """);
        StructuredParseResult full = new TokenEventStructuredSqlParser(SqlDialect.MYSQL).parseSql(statement, null);

        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, tokenEventRelationOnly(full))
                .stream()
                .map(this::relationFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of("FK_LIKE:orders.user_id->users.id:SQL_LOG_JOIN"), fingerprints);
    }

    @Test
    void extractsRelationsThroughCteAliasProjectionFromTokenEventNativeEvents() {
        SqlStatementRecord statement = statement("""
                WITH recent_orders AS (
                    SELECT o.id AS order_id, o.customer_id
                    FROM orders o
                )
                SELECT *
                FROM recent_orders ro
                JOIN customers c ON ro.customer_id = c.id
                WHERE ro.order_id IN (
                    SELECT a.order_id FROM audit_events a
                )
                """);
        StructuredParseResult full = new TokenEventStructuredSqlParser(SqlDialect.POSTGRES).parseSql(statement, null);

        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, tokenEventRelationOnly(full))
                .stream()
                .map(this::relationFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(
                "FK_LIKE:audit_events.order_id->orders.id:SQL_LOG_SUBQUERY_IN",
                "FK_LIKE:orders.customer_id->customers.id:SQL_LOG_JOIN"), fingerprints);
    }

    @Test
    void extractsMysqlOdbcOuterJoinFromTokenEventNativeEvents() {
        SqlStatementRecord statement = statement("""
                SELECT o.id, c.id
                FROM { OJ orders AS o LEFT OUTER JOIN customers AS c ON o.customer_id = c.id }
                """);
        StructuredParseResult full = new TokenEventStructuredSqlParser(SqlDialect.MYSQL).parseSql(statement, null);

        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, tokenEventRelationOnly(full))
                .stream()
                .map(this::relationFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of("FK_LIKE:orders.customer_id->customers.id:SQL_LOG_JOIN"), fingerprints);
    }

    @Test
    void extractsRelationsThroughMultiLayerCteProjectionFromTokenEventNativeEvents() {
        SqlStatementRecord statement = statement("""
                WITH "a" AS (
                  SELECT o.id AS order_id, o.customer_id
                  FROM "public"."orders" o
                  JOIN "public"."customers" c ON o.customer_id = c.id
                ),
                b AS (
                  SELECT a.order_id, c.region_id
                  FROM "a" a
                  JOIN "public"."customers" c ON a.customer_id = c.id
                ),
                c AS (
                  SELECT b.order_id, b.region_id
                  FROM b
                )
                SELECT *
                FROM c
                JOIN "public"."regions" r ON c.region_id = r.id
                JOIN invoices i ON i.order_id = c.order_id
                """);
        StructuredParseResult full = new TokenEventStructuredSqlParser(SqlDialect.POSTGRES).parseSql(statement, null);

        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, tokenEventRelationOnly(full))
                .stream()
                .map(this::relationFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(
                "FK_LIKE:invoices.order_id->public.orders.id:SQL_LOG_JOIN",
                "FK_LIKE:public.customers.region_id->public.regions.id:SQL_LOG_JOIN",
                "FK_LIKE:public.orders.customer_id->public.customers.id:SQL_LOG_JOIN"), fingerprints);
    }

    @Test
    void extractsMysqlAndPostgresDmlRelationsFromTokenEventNativeEvents() {
        SqlStatementRecord mysqlDelete = statement("""
                DELETE o, oi
                FROM orders o, order_items oi, users u
                WHERE o.id = oi.order_id
                  AND o.user_id = u.id
                """);
        StructuredParseResult mysqlFull = new TokenEventStructuredSqlParser(SqlDialect.MYSQL).parseSql(mysqlDelete, null);
        List<String> mysqlFingerprints = new TokenEventRelationExtractor().extract(mysqlDelete, tokenEventRelationOnly(mysqlFull))
                .stream()
                .map(this::relationFingerprint)
                .sorted()
                .toList();

        SqlStatementRecord postgresUpdate = statement("""
                UPDATE products p
                SET is_on_sale = true
                FROM shops s, merchants m
                WHERE p.shop_id = s.id
                  AND s.merchant_id = m.id
                """);
        StructuredParseResult postgresFull = new TokenEventStructuredSqlParser(SqlDialect.POSTGRES).parseSql(postgresUpdate, null);
        List<String> postgresFingerprints = new TokenEventRelationExtractor().extract(postgresUpdate, tokenEventRelationOnly(postgresFull))
                .stream()
                .map(this::relationFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(
                "FK_LIKE:order_items.order_id->orders.id:SQL_LOG_JOIN",
                "FK_LIKE:orders.user_id->users.id:SQL_LOG_JOIN"), mysqlFingerprints);
        assertEquals(List.of(
                "FK_LIKE:products.shop_id->shops.id:SQL_LOG_JOIN",
                "FK_LIKE:shops.merchant_id->merchants.id:SQL_LOG_JOIN"), postgresFingerprints);
    }

    @Test
    void emitsAndExtractsNativeUpdateAssignmentsForTokenEventDataLineage() {
        SqlStatementRecord statement = statement("""
                UPDATE users u
                JOIN orders o ON o.user_id = u.id
                SET u.total_spent = COALESCE(o.pay_amount, u.total_spent),
                    u.risk_level = CASE WHEN o.risk_score > 80 THEN 'HIGH' ELSE u.risk_level END
                """);
        StructuredParseResult full = new TokenEventStructuredSqlParser(SqlDialect.MYSQL).parseSql(statement, null);
        StructuredParseResult tokenEventOnly = tokenEventLineageOnly(full);

        assertTrue(hasEvent(full, StructuredParseEventType.WRITE_TARGET));
        assertTrue(hasEvent(full, StructuredParseEventType.UPDATE_ASSIGNMENT));
        assertTrue(hasEvent(full, StructuredParseEventType.EXPRESSION_SOURCE));

        List<String> fingerprints = new TokenEventDataLineageExtractor().extract(statement, tokenEventOnly)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(
                "CONTROL:CASE_WHEN:orders.risk_score,users.risk_level->users.risk_level",
                "VALUE:COALESCE:orders.pay_amount,users.total_spent->users.total_spent"), fingerprints);
    }

    @Test
    void emitsAndExtractsNativeInsertSelectAndMergeMappingsForTokenEventDataLineage() {
        SqlStatementRecord insert = statement("""
                INSERT INTO order_archive (order_id, user_id)
                SELECT o.id, o.user_id
                FROM orders o
                """);
        StructuredParseResult insertFull = new TokenEventStructuredSqlParser(SqlDialect.POSTGRES).parseSql(insert, null);
        assertTrue(hasEvent(insertFull, StructuredParseEventType.INSERT_SELECT_MAPPING));
        assertTrue(hasEvent(insertFull, StructuredParseEventType.PROJECTION_ITEM));

        List<String> insertFingerprints = new TokenEventDataLineageExtractor().extract(insert, tokenEventLineageOnly(insertFull))
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        SqlStatementRecord merge = statement("""
                MERGE INTO target_orders t
                USING source_orders s ON t.source_order_id = s.id
                WHEN MATCHED THEN UPDATE SET source_order_id = s.id
                WHEN NOT MATCHED THEN INSERT (source_order_id) VALUES (s.id)
                """);
        StructuredParseResult mergeFull = new TokenEventStructuredSqlParser(SqlDialect.POSTGRES).parseSql(merge, null);
        assertTrue(hasEvent(mergeFull, StructuredParseEventType.MERGE_WRITE_MAPPING));

        List<String> mergeFingerprints = new TokenEventDataLineageExtractor().extract(merge, tokenEventLineageOnly(mergeFull))
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(
                "VALUE:DIRECT:orders.id->order_archive.order_id",
                "VALUE:DIRECT:orders.user_id->order_archive.user_id"), insertFingerprints);
        assertEquals(List.of(
                "VALUE:DIRECT:source_orders.id->target_orders.source_order_id"), mergeFingerprints);
    }

    @Test
    void extractsDerivedAggregateLineageFromTokenEventNativeEvents() {
        SqlStatementRecord statement = statement("""
                UPDATE users u
                LEFT JOIN (
                    SELECT user_id, SUM(pay_amount) AS actual_total
                    FROM orders
                    GROUP BY user_id
                ) o_summary ON u.id = o_summary.user_id
                SET u.total_spent = COALESCE(o_summary.actual_total, u.total_spent)
                """);
        StructuredParseResult full = new TokenEventStructuredSqlParser(SqlDialect.MYSQL).parseSql(statement, null);

        assertTrue(hasEvent(full, StructuredParseEventType.PROJECTION_ITEM));

        List<String> fingerprints = new TokenEventDataLineageExtractor().extract(statement, tokenEventLineageOnly(full))
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(
                "VALUE:AGGREGATE:orders.pay_amount,users.total_spent->users.total_spent"), fingerprints);
    }

    @Test
    void filtersExplicitTemporaryTableLineageButKeepsPhysicalTempNamedTable() {
        SqlStatementRecord procedureWithLocalTemp = procedure("""
                BEGIN
                    CREATE TEMPORARY TABLE tmp_orders AS
                    SELECT o.id AS order_id FROM orders o;

                    INSERT INTO tmp_orders (order_id)
                    SELECT o.id FROM orders o;
                END
                """);
        StructuredParseResult localTempFull = new TokenEventStructuredSqlParser(SqlDialect.MYSQL)
                .parseSql(procedureWithLocalTemp, null);

        assertTrue(hasEvent(localTempFull, StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION));
        assertTrue(new TokenEventDataLineageExtractor().extract(procedureWithLocalTemp, tokenEventLineageOnly(localTempFull)).isEmpty());

        SqlStatementRecord physicalTempNamedTable = procedure("""
                BEGIN
                    INSERT INTO jsh_temp_org_pdf (org_id, remark)
                    SELECT o.id, o.org_abr FROM jsh_organization o;
                END
                """);
        StructuredParseResult physicalFull = new TokenEventStructuredSqlParser(SqlDialect.MYSQL)
                .parseSql(physicalTempNamedTable, null);

        List<String> fingerprints = new TokenEventDataLineageExtractor().extract(physicalTempNamedTable, tokenEventLineageOnly(physicalFull))
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(
                "VALUE:DIRECT:jsh_organization.id->jsh_temp_org_pdf.org_id",
                "VALUE:DIRECT:jsh_organization.org_abr->jsh_temp_org_pdf.remark"), fingerprints);
    }

    private boolean hasEvent(StructuredParseResult result, StructuredParseEventType type) {
        return result.events().stream().anyMatch(event -> event.type() == type);
    }

    private StructuredParseResult tokenEventRelationOnly(StructuredParseResult full) {
        return new StructuredParseResult(
                full.backend(),
                full.dialect(),
                full.sourceName(),
                full.events().stream()
                        .filter(event -> event.type() == StructuredParseEventType.ROWSET_REFERENCE
                                || event.type() == StructuredParseEventType.PROJECTION_ITEM
                                || event.type() == StructuredParseEventType.CTE_DECLARATION
                                || event.type() == StructuredParseEventType.IGNORED_ROWSET
                                || event.type() == StructuredParseEventType.PREDICATE_EQUALITY
                                || event.type() == StructuredParseEventType.EXISTS_PREDICATE
                                || event.type() == StructuredParseEventType.IN_SUBQUERY_PREDICATE
                                || event.type() == StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE)
                        .toList(),
                full.warnings(),
                full.attributes());
    }

    private StructuredParseResult tokenEventLineageOnly(StructuredParseResult full) {
        return new StructuredParseResult(
                full.backend(),
                full.dialect(),
                full.sourceName(),
                full.events().stream()
                        .filter(event -> event.type() == StructuredParseEventType.ROWSET_REFERENCE
                                || event.type() == StructuredParseEventType.WRITE_TARGET
                                || event.type() == StructuredParseEventType.UPDATE_ASSIGNMENT
                                || event.type() == StructuredParseEventType.INSERT_SELECT_MAPPING
                                || event.type() == StructuredParseEventType.MERGE_WRITE_MAPPING
                                || event.type() == StructuredParseEventType.PROJECTION_ITEM
                                || event.type() == StructuredParseEventType.EXPRESSION_SOURCE
                                || event.type() == StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION
                                || event.type() == StructuredParseEventType.IGNORED_ROWSET)
                        .toList(),
                full.warnings(),
                full.attributes());
    }

    private SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "token-event-relation-events.sql", 1, 1, Map.of());
    }

    private SqlStatementRecord procedure(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PROCEDURE, "token-event-procedure-events.sql", 1, 1, Map.of());
    }

    private String relationFingerprint(RelationshipCandidate relation) {
        String evidenceType = relation.evidence().isEmpty() ? "NO_EVIDENCE" : relation.evidence().get(0).type().name();
        return relation.relationType() + ":"
                + relation.source().displayName() + "->" + relation.target().displayName()
                + ":" + evidenceType;
    }

    private String lineageFingerprint(DataLineageCandidate lineage) {
        String sources = lineage.sources().stream()
                .map(source -> source.displayName())
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
        return lineage.flowKind() + ":"
                + lineage.transformType() + ":"
                + sources + "->" + lineage.target().displayName();
    }
}
