package com.relationdetector.postgres.tokenevent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.ScriptParseRequest;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.core.relation.TokenEventSqlRelationParser;
import com.relationdetector.postgres.PostgresDatabaseAdaptor;
import com.relationdetector.postgres.script.PostgresScriptParser;

/**
 * Verifies that PostgreSQL owns PostgreSQL-flavored token-event parser selection.
 */
class PostgresTokenEventDialectBoundaryTest {
    @Test
    void postgresTokenEventSeparatesScalarAggregateValueAndControls() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                UPDATE supplier_products sp
                SET total_order_qty = (
                    SELECT SUM(poi.quantity)
                    FROM purchase_order_items poi
                    JOIN purchase_orders po ON poi.order_id = po.id
                    WHERE poi.product_id = sp.product_id
                      AND po.supplier_id = sp.supplier_id
                );
                """, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, java.util.Map.of());

        var structured = new PostgresTokenEventStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor().extract(statement, structured).stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains(
                        "VALUE:AGGREGATE:purchase_order_items.quantity->supplier_products.total_order_qty"),
                () -> fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.stream().anyMatch(value -> value.startsWith("CONTROL:CASE_WHEN:")
                        && value.contains("purchase_order_items.order_id")
                        && value.contains("purchase_orders.id")
                        && value.contains("supplier_products.product_id")
                        && value.endsWith("->supplier_products.total_order_qty")),
                () -> fingerprints + " events=" + structured.events());
    }

    @Test
    void postgresTokenEventExtractsPlpgsqlRoutineBodyLineage() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                CREATE OR REPLACE PROCEDURE post_stocktake()
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    INSERT INTO inventory_transactions (product_id, before_qty, after_qty)
                    SELECT sti.product_id, i.quantity, sti.counted_quantity
                    FROM stocktake_items sti
                    JOIN inventory i ON i.product_id = sti.product_id;

                    UPDATE inventory i
                    SET quantity = sti.counted_quantity,
                        last_stocktake_date = st.stocktake_date
                    FROM stocktake_items sti
                    JOIN stocktakes st ON st.id = sti.stocktake_id
                    WHERE i.product_id = sti.product_id;
                END;
                $$;
                """, StatementSourceType.PROCEDURE, "PROCEDURE:post_stocktake", 1, 1, java.util.Map.of());

        var result = new PostgresTokenEventStructuredSqlParser().parseSql(statement, null);
        java.util.List<String> fingerprints = new StructuredDataLineageExtractor().extract(statement, result).stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertEquals(java.util.List.of(
                "VALUE:DIRECT:inventory.quantity->inventory_transactions.before_qty",
                "VALUE:DIRECT:stocktake_items.counted_quantity->inventory.quantity",
                "VALUE:DIRECT:stocktake_items.counted_quantity->inventory_transactions.after_qty",
                "VALUE:DIRECT:stocktake_items.product_id->inventory_transactions.product_id",
                "VALUE:DIRECT:stocktakes.stocktake_date->inventory.last_stocktake_date"), fingerprints,
                () -> "PL/pgSQL routine body DML should produce token-event lineage: events="
                        + result.events() + " attrs=" + result.attributes());
    }

    @Test
    void postgresRoutineBodyKeepsCastAndIntervalExpressionsInsideInsertSelectLineage() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                CREATE OR REPLACE PROCEDURE sample_routine()
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    INSERT INTO reconciliation_items (journal_id, transaction_date, description, debit_amount, credit_amount)
                    SELECT
                        cj.id,
                        cj.journal_date,
                        cj.journal_type::TEXT || ' - ' || COALESCE(cj.counterparty, ''),
                        CASE WHEN cj.journal_type IN ('bank_in', 'cash_in') THEN cj.amount ELSE 0 END,
                        CASE WHEN cj.journal_type IN ('bank_out', 'cash_out') THEN cj.amount ELSE 0 END
                    FROM cashier_journals cj;

                    INSERT INTO ar_aging_snapshots (customer_id, order_id, invoice_amount, paid_amount, due_date)
                    SELECT
                        so.customer_id,
                        so.id,
                        so.total_amount,
                        so.paid_amount,
                        so.order_date + c.credit_days * INTERVAL '1 day'
                    FROM sales_orders so
                    JOIN customers c ON so.customer_id = c.id;
                END;
                $$;
                """, StatementSourceType.PROCEDURE, "PROCEDURE:sample_routine", 1, 1, java.util.Map.of());

        var result = new PostgresTokenEventStructuredSqlParser().parseSql(statement, null);
        java.util.List<String> fingerprints = new StructuredDataLineageExtractor().extract(statement, result).stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertEquals(java.util.List.of(
                "CONTROL:CASE_WHEN:cashier_journals.journal_type->reconciliation_items.credit_amount",
                "CONTROL:CASE_WHEN:cashier_journals.journal_type->reconciliation_items.debit_amount",
                "VALUE:ARITHMETIC:sales_orders.order_date,customers.credit_days->ar_aging_snapshots.due_date",
                "VALUE:CASE_WHEN:cashier_journals.amount->reconciliation_items.credit_amount",
                "VALUE:CASE_WHEN:cashier_journals.amount->reconciliation_items.debit_amount",
                "VALUE:CONCAT_FORMAT:cashier_journals.journal_type,cashier_journals.counterparty->reconciliation_items.description",
                "VALUE:DIRECT:cashier_journals.id->reconciliation_items.journal_id",
                "VALUE:DIRECT:cashier_journals.journal_date->reconciliation_items.transaction_date",
                "VALUE:DIRECT:sales_orders.customer_id->ar_aging_snapshots.customer_id",
                "VALUE:DIRECT:sales_orders.id->ar_aging_snapshots.order_id",
                "VALUE:DIRECT:sales_orders.paid_amount->ar_aging_snapshots.paid_amount",
                "VALUE:DIRECT:sales_orders.total_amount->ar_aging_snapshots.invoice_amount"), fingerprints,
                () -> "PostgreSQL routine body grammar must preserve lineage through :: casts and INTERVAL literals: "
                        + fingerprints + " events=" + result.events());
    }

    @Test
    void postgresTokenEventResolvesAggregateProjectionLineageThroughDerivedUpdateFrom() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                UPDATE users u
                SET total_spent = COALESCE(o_summary.actual_total, 0.00),
                    level = CASE
                        WHEN o_summary.actual_total >= 10000 THEN 'VIP'
                        ELSE 'REGULAR'
                    END
                FROM (
                    SELECT user_id, SUM(pay_amount) AS actual_total
                    FROM orders
                    WHERE order_status = 'PAID'
                    GROUP BY user_id
                ) o_summary
                WHERE u.id = o_summary.user_id;
                """, StatementSourceType.PLAIN_SQL, "postgres-derived-aggregate-update.sql", 1, 1, java.util.Map.of());

        var result = new PostgresTokenEventStructuredSqlParser().parseSql(statement, null);
        java.util.List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement, result)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains("VALUE:AGGREGATE:orders.pay_amount->users.total_spent"),
                () -> "Derived aggregate projection should flow into UPDATE target: " + fingerprints
                        + " events=" + result.events() + " attrs=" + result.attributes());
        assertTrue(fingerprints.contains("CONTROL:CASE_WHEN:orders.pay_amount->users.level"),
                () -> "CASE over derived aggregate should control UPDATE target: " + fingerprints
                        + " events=" + result.events() + " attrs=" + result.attributes());
    }

    @Test
    void postgresTokenEventResolvesCteUpdateFromConcatLineage() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                WITH active_users AS (
                    SELECT id, risk_level
                    FROM users
                    WHERE status = 'ACTIVE'
                ),
                fraud_orders AS (
                    SELECT
                        o.id AS order_id,
                        o.user_id,
                        ROW_NUMBER() OVER (PARTITION BY o.user_id ORDER BY o.amount DESC) AS rnk
                    FROM orders o
                    JOIN active_users au ON o.user_id = au.id
                    WHERE o.created_at >= NOW() - INTERVAL '30 days'
                )
                UPDATE order_ledgers l
                SET remarks = 'User risk level: ' || u.risk_level || ' | Order Rank: ' || fo.rnk
                FROM fraud_orders fo, users u
                WHERE l.order_id = fo.order_id
                  AND fo.user_id = u.id
                  AND fo.rnk <= 3;
                """, StatementSourceType.PLAIN_SQL, "postgres-cte-update-concat.sql", 1, 1, java.util.Map.of());

        var result = new PostgresTokenEventStructuredSqlParser().parseSql(statement, null);
        java.util.List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement, result)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains(
                "VALUE:CONCAT_FORMAT:users.risk_level,orders.amount,orders.user_id->order_ledgers.remarks"),
                () -> "CTE UPDATE FROM concat should resolve projection lineage through typed grammar: "
                        + fingerprints + " events=" + result.events() + " attrs=" + result.attributes());
    }

    @Test
    void postgresTokenEventResolvesCteUpdateFromCastArrayFunctionLineage() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                WITH user_financial_snapshot AS (
                    SELECT
                        t.user_id,
                        STRING_AGG(DISTINCT t.merchant_category, '; ' ORDER BY t.merchant_category) AS primary_categories,
                        MAX(t.created_at) AS last_activity_time,
                        SUM(t.amount) AS net_cash_flow
                    FROM transaction_ledgers t
                    WHERE t.status = 'SUCCESS'
                      AND t.created_at >= NOW() - INTERVAL '1 year'
                    GROUP BY t.user_id
                ),
                dormant_risk_scores AS (
                    SELECT
                        u.id AS user_id,
                        u.country_code,
                        EXTRACT(DAY FROM AGE(NOW(), snap.last_activity_time)) AS days_since_last_active,
                        NTILE(10) OVER (PARTITION BY u.country_code ORDER BY snap.net_cash_flow DESC) AS wealth_tile
                    FROM users u
                    JOIN user_financial_snapshot snap ON u.id = snap.user_id
                )
                UPDATE account_balances ab
                SET risk_flags = ARRAY_APPEND(ab.risk_flags, 'POTENTIAL_DORMANT_WEALTH'),
                    compliance_notes = LOWER(FORMAT('Country: %s | Idle Days: %s | Wealth Tier: %s | Cats: %s',
                                             drs.country_code,
                                             drs.days_since_last_active::text,
                                             drs.wealth_tile::text,
                                             snap_main.primary_categories)),
                    adjusted_limit = LEAST(ab.max_credit_limit * 0.8, 50000.00)
                FROM dormant_risk_scores drs, user_financial_snapshot snap_main
                WHERE ab.user_id = drs.user_id
                  AND drs.user_id = snap_main.user_id;
                """, StatementSourceType.PLAIN_SQL, "postgres-cte-update-casts.sql", 1, 1, java.util.Map.of());

        var result = new PostgresTokenEventStructuredSqlParser().parseSql(statement, null);
        java.util.List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement, result)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains(
                "VALUE:ARITHMETIC:account_balances.max_credit_limit->account_balances.adjusted_limit"),
                () -> "Arithmetic target self-source should be preserved: " + fingerprints
                        + " events=" + result.events() + " attrs=" + result.attributes());
        assertTrue(fingerprints.contains(
                "VALUE:FUNCTION_CALL:account_balances.risk_flags->account_balances.risk_flags"),
                () -> "Function self-source should be preserved: " + fingerprints
                        + " events=" + result.events() + " attrs=" + result.attributes());
        assertTrue(fingerprints.contains(
                "VALUE:CONCAT_FORMAT:users.country_code,transaction_ledgers.created_at,transaction_ledgers.amount,transaction_ledgers.merchant_category->account_balances.compliance_notes"),
                () -> "Nested CTE function/cast expression should resolve to physical sources: " + fingerprints
                        + " events=" + result.events() + " attrs=" + result.attributes());
    }

    @Test
    void postgresTokenEventResolvesScalarAggregateSubqueryLineage() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                UPDATE users u
                SET total_spent = COALESCE((
                        SELECT SUM(o.pay_amount)
                        FROM orders o
                        WHERE o.user_id = u.id
                    ), 0.00),
                    level = CASE
                        WHEN COALESCE((
                            SELECT SUM(o.pay_amount)
                            FROM orders o
                            WHERE o.user_id = u.id
                        ), 0.00) >= 10000 THEN 'VIP'
                        ELSE 'REGULAR'
                    END;
                """, StatementSourceType.PLAIN_SQL, "postgres-scalar-aggregate-update.sql", 1, 1, java.util.Map.of());

        var result = new PostgresTokenEventStructuredSqlParser().parseSql(statement, null);
        java.util.List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement, result)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains("VALUE:AGGREGATE:orders.pay_amount->users.total_spent"),
                () -> "Scalar aggregate subquery should flow into UPDATE target: " + fingerprints
                        + " events=" + result.events() + " attrs=" + result.attributes());
        assertTrue(fingerprints.contains("CONTROL:CASE_WHEN:orders.pay_amount->users.level"),
                () -> "CASE over scalar aggregate subquery should control UPDATE target: " + fingerprints
                        + " events=" + result.events() + " attrs=" + result.attributes());
    }

    @Test
    void postgresTokenEventExtractsMergeInsertLineage() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                MERGE INTO target_orders AS t
                USING source_orders AS s
                ON t.source_order_id = s.id
                WHEN MATCHED AND s.cancelled_at IS NULL THEN
                  UPDATE SET synced_at = CURRENT_TIMESTAMP
                WHEN NOT MATCHED THEN
                  INSERT (source_order_id) VALUES (s.id);
                """, StatementSourceType.PLAIN_SQL, "postgres-merge-insert.sql", 1, 1, java.util.Map.of());

        var result = new PostgresTokenEventStructuredSqlParser().parseSql(statement, null);
        java.util.List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement, result)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains("VALUE:DIRECT:source_orders.id->target_orders.source_order_id"),
                () -> "MERGE INSERT value expression should flow into target column: " + fingerprints
                        + " events=" + result.events() + " attrs=" + result.attributes());
        var relationships = new com.relationdetector.core.relation.TokenEventRelationExtractor()
                .extract(statement, result)
                .stream()
                .map(this::relationFingerprint)
                .sorted()
                .toList();
        assertTrue(relationships.contains("CO_OCCURRENCE:source_orders.id->target_orders.source_order_id:SQL_LOG_JOIN")
                        || relationships.contains("CO_OCCURRENCE:target_orders.source_order_id->source_orders.id:SQL_LOG_JOIN"),
                () -> "MERGE ON predicate should remain typed SQL_LOG_JOIN evidence before naming enhancement: " + relationships
                        + " events=" + result.events() + " attrs=" + result.attributes());
    }

    @Test
    void postgresTokenEventTreatsWindowedSumAsCumulativeLineage() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO jsh_temp_mock_plan (mock_timestamp_str)
                SELECT hp.hour_val + SUM(hp.weight) OVER (ORDER BY hp.hour_val)
                FROM jsh_temp_hour_pdf hp;
                """, StatementSourceType.PLAIN_SQL, "postgres-window-lineage.sql", 1, 1, java.util.Map.of());

        var result = new PostgresTokenEventStructuredSqlParser().parseSql(statement, null);
        java.util.List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement, result)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertEquals(java.util.List.of(
                "VALUE:CUMULATIVE:jsh_temp_hour_pdf.hour_val,jsh_temp_hour_pdf.weight->jsh_temp_mock_plan.mock_timestamp_str"),
                fingerprints,
                () -> "PostgreSQL window aggregate should remain a typed cumulative expression: "
                        + fingerprints + " events=" + result.events() + " attrs=" + result.attributes());
    }

    @Test
    void adaptorExposesPostgresTokenEventSqlAndDdlParsers() {
        PostgresDatabaseAdaptor adaptor = new PostgresDatabaseAdaptor();

        assertEquals(PostgresTokenEventStructuredSqlParser.class, adaptor.parsers().structuredSql().orElseThrow().getClass());
        assertEquals(PostgresTokenEventStructuredDdlParser.class, adaptor.parsers().structuredDdl().orElseThrow().getClass());
    }

    @Test
    void postgresTokenEventDdlParserUsesTypedDialectDdlVisitor() {
        var result = new PostgresTokenEventStructuredDdlParser().parseDdl(
                "CREATE TABLE orders(user_id BIGINT REFERENCES users(id));",
                "postgres-ddl.sql",
                null);

        assertEquals("PostgresRelationSql", result.attributes().get("grammar"));
        assertEquals("PostgresTokenEventParseTreeVisitor", result.attributes().get("eventBuilder"));
        assertFalse(result.attributes().containsKey("ddlEventVisitor"));
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.DDL_FOREIGN_KEY
                        && "orders".equals(event.sourceTable())
                        && "user_id".equals(event.sourceColumn())
                        && "users".equals(event.targetTable())
                        && "id".equals(event.targetColumn())));
    }

    @Test
    void postgresTokenEventDdlParserHandlesTypeCastsAndDeferrableForeignKeys() {
        var result = new PostgresTokenEventStructuredDdlParser().parseDdl("""
                CREATE TABLE case_01.xref_p10_deleted (
                  dbid smallint NOT NULL,
                  created integer NOT NULL,
                  last integer NOT NULL,
                  upi character varying(26) NOT NULL,
                  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
                  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
                  CONSTRAINT xref_p10_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id) DEFERRABLE,
                  CONSTRAINT xref_p10_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id) DEFERRABLE,
                  CONSTRAINT xref_p10_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id) DEFERRABLE,
                  CONSTRAINT xref_p10_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi) DEFERRABLE
                );
                CREATE INDEX xref_p10_deleted_upi_like
                  ON case_01.xref_p10_deleted USING btree (upi varchar_pattern_ops);
                """, "postgres-ddl.sql", null);

        assertEquals(0, result.attributes().get("syntaxErrors"), () -> "attrs=" + result.attributes());
        java.util.List<String> fingerprints = result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.DDL_FOREIGN_KEY)
                .map(event -> event.sourceTable() + "."
                        + event.sourceColumn() + "->"
                        + event.targetTable() + "."
                        + event.targetColumn())
                .sorted()
                .toList();

        assertEquals(java.util.List.of(
                "case_01.xref_p10_deleted.created->rnc_release.id",
                "case_01.xref_p10_deleted.dbid->rnc_database.id",
                "case_01.xref_p10_deleted.last->rnc_release.id",
                "case_01.xref_p10_deleted.upi->rna.upi"), fingerprints);
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.DDL_INDEX
                        && "SOURCE_INDEX".equals(event.role())
                        && "case_01.xref_p10_deleted".equals(event.table())
                        && "upi".equals(event.column())));
    }

    @Test
    void postgresTokenEventDdlParserDoesNotTreatMysqlFulltextAsPostgresIndexEvidence() {
        var result = new PostgresTokenEventStructuredDdlParser().parseDdl(
                "CREATE FULLTEXT INDEX users_bio_idx ON users (bio);",
                "mysql-fulltext-index.sql",
                null);

        assertTrue(result.events().stream().noneMatch(event ->
                event.type() == StructuredParseEventType.DDL_INDEX
                        && "users".equals(event.table())
                        && "bio".equals(event.column())),
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
                "orders".equals(event.table())
                        && "o".equals(event.alias())));
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
        assertEquals("PostgresTokenEventParseTreeVisitor", result.attributes().get("eventBuilder"));
        assertFalse(result.attributes().containsKey("legacySupplementBuilder"));
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
                        && ("json_to_recordset".equalsIgnoreCase(event.table())
                        || "generate_series".equalsIgnoreCase(event.table())
                        || "decoded".equalsIgnoreCase(event.table()))),
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
                "orders".equals(event.table())
                        || "users".equals(event.table())));
    }

    @Test
    void postgresAdaptorSqlParserUsesTokenEventRelationParser() {
        SqlRelationParser parser = new PostgresDatabaseAdaptor().parsers().sqlRelations();
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

    @Test
    void postgresTokenEventParsesRecursiveCteUnionAllJoinPredicates() {
        java.util.List<RelationshipCandidate> relations = postgresRelations("""
                WITH RECURSIVE bom_explosion AS (
                    SELECT b.parent_product_id, b.child_product_id
                    FROM boms b
                    JOIN products p_parent ON b.parent_product_id = p_parent.id
                    JOIN products p_child ON b.child_product_id = p_child.id
                    UNION ALL
                    SELECT be.parent_product_id, b2.child_product_id
                    FROM bom_explosion be
                    JOIN boms b2 ON be.child_product_id = b2.parent_product_id
                    JOIN products p_child ON b2.child_product_id = p_child.id
                )
                SELECT *
                FROM bom_explosion be
                JOIN products p ON be.child_product_id = p.id
                LIMIT 20
                """);

        java.util.Set<String> fingerprints = relations.stream()
                .map(relation -> relation.relationType() + ":"
                        + relation.source().displayName() + "->"
                        + relation.target().displayName())
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(fingerprints.contains("CO_OCCURRENCE:boms.parent_product_id->products.id"),
                () -> "Recursive CTE base SELECT join should be parsed: fingerprints=" + fingerprints
                        + " relations=" + relations);
        assertTrue(fingerprints.contains("CO_OCCURRENCE:boms.child_product_id->products.id"),
                () -> "Recursive CTE recursive SELECT join should be parsed: fingerprints=" + fingerprints
                        + " relations=" + relations);
    }

    @Test
    void postgresTokenEventParsesCteWithWindowFunctionJoinPredicates() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                WITH monthly_customer_sales AS (
                    SELECT
                        so.customer_id,
                        c.name AS customer_name,
                        c.membership_level,
                        TO_CHAR(so.order_date, 'YYYY-MM') AS sale_month,
                        COUNT(DISTINCT so.id) AS order_count,
                        SUM(so.total_amount) AS monthly_amount,
                        COUNT(DISTINCT soi.product_id) AS distinct_products
                    FROM sales_orders so
                    JOIN customers c ON so.customer_id = c.id
                    LEFT JOIN sales_order_items soi ON so.id = soi.order_id
                    WHERE so.order_date >= CURRENT_DATE - INTERVAL '6 months'
                      AND so.status NOT IN ('draft', 'cancelled')
                    GROUP BY so.customer_id, c.name, c.membership_level, TO_CHAR(so.order_date, 'YYYY-MM')
                ),
                customer_with_lag AS (
                    SELECT
                        *,
                        LAG(monthly_amount) OVER (PARTITION BY customer_id ORDER BY sale_month) AS prev_month_amount,
                        LAG(sale_month) OVER (PARTITION BY customer_id ORDER BY sale_month) AS prev_month,
                        ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY sale_month DESC) AS month_rank,
                        COUNT(*) OVER (PARTITION BY customer_id) AS active_months
                    FROM monthly_customer_sales
                )
                SELECT
                    customer_id,
                    customer_name,
                    membership_level,
                    sale_month,
                    monthly_amount,
                    prev_month_amount,
                    active_months,
                    CASE
                        WHEN prev_month_amount IS NOT NULL AND prev_month_amount > 0
                        THEN ROUND((monthly_amount - prev_month_amount) / prev_month_amount * 100, 2)
                        ELSE NULL
                    END AS mom_growth_pct,
                    order_count,
                    distinct_products,
                    ROUND(monthly_amount / NULLIF(order_count, 0), 2) AS avg_order_value
                FROM customer_with_lag
                WHERE month_rank <= 3
                ORDER BY customer_id, sale_month DESC
                """, StatementSourceType.PLAIN_SQL, "postgres-cte-window.sql", 1, 1, java.util.Map.of());
        var structured = new PostgresTokenEventStructuredSqlParser().parseSql(statement, null);
        java.util.List<RelationshipCandidate> relations =
                new TokenEventSqlRelationParser(new PostgresTokenEventStructuredSqlParser()).parse(statement);

        java.util.Set<String> fingerprints = relations.stream()
                .map(relation -> relation.relationType() + ":"
                        + relation.source().displayName() + "->"
                        + relation.target().displayName())
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(fingerprints.contains("CO_OCCURRENCE:sales_orders.customer_id->customers.id")
                        || fingerprints.contains("CO_OCCURRENCE:customers.id->sales_orders.customer_id"),
                () -> "CTE member SELECT join should be parsed: fingerprints=" + fingerprints
                        + " relations=" + relations + " attrs=" + structured.attributes()
                        + " events=" + structured.events());
        assertTrue(fingerprints.contains("CO_OCCURRENCE:sales_orders.id->sales_order_items.order_id")
                        || fingerprints.contains("CO_OCCURRENCE:sales_order_items.order_id->sales_orders.id"),
                () -> "LEFT JOIN inside CTE should be parsed: fingerprints=" + fingerprints
                        + " relations=" + relations + " attrs=" + structured.attributes()
                        + " events=" + structured.events());
    }

    @Test
    void postgresTokenEventFindsCteJoinPredicatesInSampleDataComplexQueryFile() throws Exception {
        java.nio.file.Path input = workspaceRoot().resolve(
                "sample-data/postgres/18/04-queries/01-complex-queries.sql");
        java.util.List<RelationshipCandidate> relations = new PostgresScriptParser()
                .parse(new ScriptParseRequest(java.nio.file.Files.readString(input), input.toString(),
                        StatementSourceType.PLAIN_SQL))
                .statements().stream()
                .flatMap(statement -> new TokenEventSqlRelationParser(new PostgresTokenEventStructuredSqlParser())
                        .parse(statement).stream())
                .toList();

        java.util.Set<String> fingerprints = relations.stream()
                .map(relation -> relation.relationType() + ":"
                        + relation.source().displayName() + "->"
                        + relation.target().displayName())
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(fingerprints.contains("CO_OCCURRENCE:sales_orders.customer_id->customers.id")
                        || fingerprints.contains("CO_OCCURRENCE:customers.id->sales_orders.customer_id"),
                () -> "Sample-data q01 should expose sales_orders/customers CTE join: fingerprints="
                        + fingerprints);
        assertTrue(fingerprints.contains("CO_OCCURRENCE:sales_order_items.order_id->sales_orders.id")
                        || fingerprints.contains("CO_OCCURRENCE:sales_orders.id->sales_order_items.order_id"),
                () -> "Sample-data q01 should expose sales_order_items/sales_orders join: fingerprints="
                        + fingerprints);
        assertTrue(fingerprints.contains("CO_OCCURRENCE:promotion_usages.promotion_id->promotions.id")
                        || fingerprints.contains("CO_OCCURRENCE:promotions.id->promotion_usages.promotion_id"),
                () -> "Sample-data q18 should expose promotion_usages/promotions join: fingerprints="
                        + fingerprints);
        assertTrue(fingerprints.contains("CO_OCCURRENCE:promotion_usages.order_id->sales_orders.id")
                        || fingerprints.contains("CO_OCCURRENCE:sales_orders.id->promotion_usages.order_id"),
                () -> "Sample-data q18 should expose promotion_usages/sales_orders join: fingerprints="
                        + fingerprints);
    }

    @Test
    void postgresTokenEventVisitsScalarSubqueriesInPlainSelectList() {
        java.util.List<RelationshipCandidate> relations = postgresRelations("""
                SELECT
                    e.id,
                    d.name,
                    (SELECT esl.new_salary
                     FROM employee_salary_log esl
                     WHERE esl.employee_id = e.id
                     ORDER BY esl.effective_date DESC
                     LIMIT 1) AS latest_salary
                FROM employees e
                JOIN departments d ON e.department_id = d.id
                WHERE e.status IN ('active', 'probation')
                ORDER BY latest_salary DESC
                """);

        java.util.Set<String> fingerprints = relations.stream()
                .map(relation -> relation.relationType() + ":"
                        + relation.source().displayName() + "->"
                        + relation.target().displayName())
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(fingerprints.contains("CO_OCCURRENCE:employee_salary_log.employee_id->employees.id")
                        || fingerprints.contains("CO_OCCURRENCE:employees.id->employee_salary_log.employee_id"),
                () -> "Scalar subquery in SELECT list should expose correlated predicate: fingerprints="
                        + fingerprints + " relations=" + relations);
        assertTrue(fingerprints.contains("CO_OCCURRENCE:employees.department_id->departments.id")
                        || fingerprints.contains("CO_OCCURRENCE:departments.id->employees.department_id"),
                () -> "Outer SELECT joins should still be parsed: fingerprints=" + fingerprints
                        + " relations=" + relations);
    }

    @Test
    void postgresTokenEventParsesCteBeforeCrossJoinWithoutOnClause() {
        java.util.List<RelationshipCandidate> relations = postgresRelations("""
                WITH promo_orders AS (
                    SELECT pu.promotion_id, SUM(so.total_amount) AS promo_total_sales
                    FROM promotion_usages pu
                    JOIN promotions p ON pu.promotion_id = p.id
                    JOIN sales_orders so ON pu.order_id = so.id
                    GROUP BY pu.promotion_id
                ),
                baseline_sales AS (
                    SELECT po.promotion_id, AVG(daily.daily_sales) AS avg_daily_sales
                    FROM promo_orders po
                    CROSS JOIN (
                        SELECT so.order_date, SUM(so.total_amount) AS daily_sales
                        FROM sales_orders so
                        GROUP BY so.order_date
                    ) daily
                    GROUP BY po.promotion_id
                )
                SELECT po.promotion_id, bs.avg_daily_sales
                FROM promo_orders po
                LEFT JOIN baseline_sales bs ON po.promotion_id = bs.promotion_id
                """);

        java.util.Set<String> fingerprints = relationFingerprints(relations);

        assertTrue(fingerprints.contains("CO_OCCURRENCE:promotion_usages.promotion_id->promotions.id")
                        || fingerprints.contains("CO_OCCURRENCE:promotions.id->promotion_usages.promotion_id"),
                () -> "CTE before CROSS JOIN should still expose promotion FK predicate: " + fingerprints);
        assertTrue(fingerprints.contains("CO_OCCURRENCE:promotion_usages.order_id->sales_orders.id")
                        || fingerprints.contains("CO_OCCURRENCE:sales_orders.id->promotion_usages.order_id"),
                () -> "CTE before CROSS JOIN should still expose order FK predicate: " + fingerprints);
    }

    @Test
    void postgresTokenEventParsesNullSafeDistinctJoinPredicate() {
        java.util.List<RelationshipCandidate> relations = postgresRelations("""
                SELECT pt.task_no, e.name
                FROM picking_tasks pt
                JOIN picking_task_items pti ON pti.picking_task_id = pt.id
                LEFT JOIN inventory_location_balances ilb ON ilb.location_id = pti.location_id
                    AND ilb.product_id = pti.product_id
                    AND ilb.batch_id IS NOT DISTINCT FROM pti.batch_id
                LEFT JOIN employees e ON e.id = pt.assigned_to
                """);

        java.util.Set<String> fingerprints = relationFingerprints(relations);

        assertTrue(fingerprints.contains("CO_OCCURRENCE:inventory_location_balances.batch_id->picking_task_items.batch_id")
                        || fingerprints.contains("CO_OCCURRENCE:picking_task_items.batch_id->inventory_location_balances.batch_id"),
                () -> "IS NOT DISTINCT FROM should be treated as a null-safe equality predicate: " + fingerprints);
        assertTrue(fingerprints.contains("CO_OCCURRENCE:employees.id->picking_tasks.assigned_to")
                        || fingerprints.contains("CO_OCCURRENCE:picking_tasks.assigned_to->employees.id"),
                () -> "Later joins after null-safe predicate should still be parsed: " + fingerprints);
    }

    @Test
    void postgresTokenEventResolvesUnqualifiedInSubquerySelectColumn() {
        java.util.List<RelationshipCandidate> relations = postgresRelations("""
                SELECT po.order_no
                FROM purchase_orders po
                WHERE po.purchaser_id IN (
                    SELECT id
                    FROM employees
                    WHERE manager_id = (
                        SELECT manager_id FROM warehouses WHERE id = 1
                    )
                )
                """);

        java.util.Set<String> fingerprints = relationFingerprints(relations);

        assertTrue(fingerprints.contains("CO_OCCURRENCE:purchase_orders.purchaser_id->employees.id")
                        || fingerprints.contains("CO_OCCURRENCE:employees.id->purchase_orders.purchaser_id"),
                () -> "Unqualified single-column IN subquery should resolve to the only subquery rowset: "
                        + fingerprints);
    }

    private java.util.List<RelationshipCandidate> postgresRelations(String sql) {
        SqlStatementRecord statement = new SqlStatementRecord(
                sql, StatementSourceType.PLAIN_SQL, "postgres-dialect-boundary.sql", 1, 1, java.util.Map.of());
        return new TokenEventSqlRelationParser(new PostgresTokenEventStructuredSqlParser()).parse(statement);
    }

    private java.util.Set<String> relationFingerprints(java.util.List<RelationshipCandidate> relations) {
        return relations.stream()
                .map(relation -> relation.relationType() + ":"
                        + relation.source().displayName() + "->"
                        + relation.target().displayName())
                .collect(java.util.stream.Collectors.toSet());
    }

    private String relationFingerprint(RelationshipCandidate relation) {
        return relation.relationType() + ":"
                + relation.source().displayName() + "->"
                + relation.target().displayName() + ":"
                + relation.evidence().stream()
                        .map(evidence -> evidence.type().name())
                        .sorted()
                        .collect(java.util.stream.Collectors.joining(","));
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

    private String lineageFingerprint(DataLineageCandidate lineage) {
        return lineage.flowKind() + ":"
                + lineage.transformType() + ":"
                + lineage.sources().stream()
                        .map(com.relationdetector.contracts.model.Endpoint::displayName)
                        .collect(java.util.stream.Collectors.joining(","))
                + "->" + lineage.target().displayName();
    }

    private java.nio.file.Path workspaceRoot() {
        java.nio.file.Path current = java.nio.file.Path.of("").toAbsolutePath();
        while (current != null) {
            if (isRelationDetectorRoot(current)) {
                return current;
            }
            java.nio.file.Path nested = current.resolve("relation-detector");
            if (isRelationDetectorRoot(nested)) {
                return nested;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate workspace root");
    }

    private boolean isRelationDetectorRoot(java.nio.file.Path path) {
        return java.nio.file.Files.isDirectory(path.resolve("sample-data"))
                && java.nio.file.Files.isDirectory(path.resolve("test-fixtures"));
    }
}
