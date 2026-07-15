package com.relationdetector.postgres.routine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.ScriptFrameRequest;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.core.relation.StructuredRelationshipExtractor;
import com.relationdetector.postgres.fullgrammar.v16.FullGrammarDialectModule;
import com.relationdetector.postgres.plpgsql.tokenevent.TokenEventPlPgSqlBodyParser;
import com.relationdetector.postgres.script.PostgresScriptFramer;
import com.relationdetector.postgres.tokenevent.PostgresTokenEventStructuredSqlParser;
import com.relationdetector.postgres.tokenevent.PostgresRelationSqlLexer;
import com.relationdetector.postgres.tokenevent.PostgresRelationSqlParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

class PostgresRoutineSampleLineageTest {

    @Test
    void beginAtomicBodyUsesTheCurrentSqlParserForEveryProfile() {
        String sql = """
                CREATE FUNCTION apply_account_snapshot() RETURNS void
                BEGIN ATOMIC
                  INSERT INTO account_snapshots (account_id)
                  SELECT a.id
                  FROM accounts a
                  JOIN customers c ON c.id = a.customer_id;
                END;
                """;
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.FUNCTION,
                "ROUTINE:apply_account_snapshot", 20, 27, Map.of(
                        "sourceFile", "sample-data/postgres/routine.sql",
                        "sourceStatementId", "lines:20-27"));

        for (ParserCase parser : parsers()) {
            var structured = parser.parser().parseSql(statement, null);
            var relationships = new StructuredRelationshipExtractor().extract(statement, structured);
            assertTrue(structured.warnings().isEmpty(),
                    () -> parser.name() + " failed BEGIN ATOMIC: " + structured.warnings());
            assertTrue(relationships.stream().anyMatch(candidate ->
                            "accounts.customer_id".equals(candidate.source().displayName())
                                    && "customers.id".equals(candidate.target().displayName())),
                    () -> parser.name() + " did not dispatch atomic SQL: " + structured.events());
        }
    }

    @Test
    void stringBodyWithoutLanguageIsRejectedForEveryProfile() {
        String sql = """
                CREATE FUNCTION read_account() RETURNS bigint AS $$
                  SELECT a.id FROM accounts a;
                $$;
                """;
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.FUNCTION,
                "ROUTINE:read_account", 1, 3, Map.of());

        for (ParserCase parser : parsers()) {
            var structured = parser.parser().parseSql(statement, null);
            assertTrue(structured.events().isEmpty(),
                    () -> parser.name() + " guessed a language for a string body: " + structured.events());
            assertTrue(structured.warnings().stream().anyMatch(warning ->
                            "POSTGRES_ROUTINE_LANGUAGE_MISSING".equals(warning.code())),
                    () -> parser.name() + " did not report missing LANGUAGE: " + structured.warnings());
        }
    }

    @Test
    void foreachBodyKeepsStaticSqlForEveryProfile() {
        String sql = """
                CREATE PROCEDURE refresh_accounts(p_ids bigint[]) LANGUAGE plpgsql AS $$
                DECLARE
                  v_id bigint;
                BEGIN
                  FOREACH v_id SLICE 0 IN ARRAY p_ids LOOP
                    UPDATE accounts a
                    SET active = true
                    FROM customers c
                    WHERE c.id = a.customer_id;
                  END LOOP;
                END;
                $$;
                """;
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "ROUTINE:refresh_accounts", 1, 12, Map.of());

        for (ParserCase parser : parsers()) {
            var structured = parser.parser().parseSql(statement, null);
            var relationships = new StructuredRelationshipExtractor().extract(statement, structured);
            assertTrue(structured.warnings().isEmpty(),
                    () -> parser.name() + " did not type FOREACH: " + structured.warnings());
            assertTrue(relationships.stream().anyMatch(candidate ->
                            "accounts.customer_id".equals(candidate.source().displayName())
                                    && "customers.id".equals(candidate.target().displayName())),
                    () -> parser.name() + " lost SQL inside FOREACH: " + structured.events());
        }
    }

    @Test
    void legalProceduralStatementsDoNotBecomeUnsupportedDiagnostics() {
        String sql = """
                DECLARE
                    v_amount NUMERIC(12,2) DEFAULT 0;
                BEGIN
                    v_amount := v_amount + 1;
                    IF v_amount > 0 THEN
                        RAISE NOTICE 'amount: %', v_amount;
                    ELSIF v_amount = 0 THEN
                        RETURN 0;
                    END IF;
                    RETURN v_amount;
                END;
                """;
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.FUNCTION,
                "ROUTINE:test_function", 10, 21, Map.of(
                        "sourceFile", "sample-data/postgres/test.sql",
                        "sourceStatementId", "test_function",
                        "sourceObjectType", "ROUTINE",
                        "sourceObjectName", "test_function"));

        PlPgSqlParseOutcome outcome = parseBody(statement);

        assertEquals(0, outcome.unsupportedStatementCount(), () -> outcome.warnings().toString());
        assertEquals(List.of(), outcome.warnings());
    }

    @Test
    void mixedRoutineReportsEachUnsupportedStatementWithoutDroppingSupportedEvents() {
        String sql = """
                SELECT o.id FROM orders o JOIN customers c ON o.customer_id = c.id;
                @unsupported;
                SELECT p.id FROM products p JOIN categories pc ON p.category_id = pc.id;
                """;
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "ROUTINE:test_mixed", 100, 102, Map.of(
                        "sourceFile", "sample-data/postgres/test.sql",
                        "sourceStatementId", "test_mixed",
                        "sourceBlockId", "test_mixed",
                        "sourceObjectType", "ROUTINE",
                        "sourceObjectName", "test_mixed"));

        PlPgSqlParseOutcome outcome = parseBody(statement);

        assertEquals(1, outcome.unsupportedStatementCount());
        assertTrue(outcome.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.PREDICATE_EQUALITY));
        assertEquals(1, outcome.warnings().stream()
                .filter(warning -> warning.code().equals("POSTGRES_ROUTINE_UNSUPPORTED_STATEMENT"))
                .count());
        assertEquals(101, outcome.warnings().stream()
                .filter(warning -> warning.code().equals("POSTGRES_ROUTINE_UNSUPPORTED_STATEMENT"))
                .findFirst().orElseThrow().line());
    }

    @Test
    void tempTableUnnestAndPostgresRangePredicatesAreSupported() {
        String sql = """
                BEGIN
                  CREATE TEMP TABLE IF NOT EXISTS temp_inputs (
                    id INT GENERATED ALWAYS AS IDENTITY,
                    source_id INT
                  ) ON COMMIT DROP;
                  INSERT INTO temp_inputs (source_id)
                  SELECT source_id FROM UNNEST(p_source_ids) AS src(source_id);
                  SELECT rb.id FROM room_bookings rb
                  WHERE rb.booked_during && tsrange(p_start, p_end);
                END;
                """;
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "ROUTINE:test_postgres_features", 20, 30, Map.of());

        PlPgSqlParseOutcome outcome = parseBody(statement);

        assertEquals(0, outcome.unsupportedStatementCount(), () -> outcome.warnings().toString());
        assertEquals(List.of(), outcome.warnings());
    }

    @Test
    void naturalRoutinePredicateAndExpressionFormsAreSupported() {
        String sql = """
                BEGIN
                  SELECT 'MRP-' || REPLACE(p.plan_month, '-', '') || '-' || p.id
                  FROM production_plans p
                  CROSS JOIN warehouses w
                  WHERE p.batch_id IS NOT DISTINCT FROM w.batch_id
                    AND p.expiry_date <= CURRENT_DATE + ('30' || ' days')::INTERVAL;
                END;
                """;
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "ROUTINE:test_postgres_natural_forms", 40, 48, Map.of());

        PlPgSqlParseOutcome outcome = parseBody(statement);

        assertEquals(List.of(), outcome.warnings());
        assertTrue(outcome.events().stream().anyMatch(event ->
                        event.type() == StructuredParseEventType.PREDICATE_EQUALITY),
                () -> outcome.events().toString());
    }

    @Test
    void outerConcatDominatesNestedCoalesceForWriteLineage() {
        String bodySql = """
                BEGIN
                  INSERT INTO reconciliation_items (description)
                  SELECT cj.journal_type::TEXT || ' - ' || COALESCE(cj.counterparty, '')
                      || ' ' || COALESCE(cj.remark, '')
                  FROM cashier_journals cj;
                END;
                """;
        String sql = "CREATE OR REPLACE PROCEDURE test_concat() LANGUAGE plpgsql AS $$\n"
                + bodySql + "$$;";
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "ROUTINE:test_concat", 1, sql.lines().count(), Map.of());
        for (ParserCase parser : parsers()) {
            var structured = parser.parser().parseSql(statement, null);
            var lineages = new StructuredDataLineageExtractor().extract(statement, structured);
            assertTrue(lineages.stream().anyMatch(lineage ->
                            "reconciliation_items.description".equals(lineage.target().displayName())
                                    && lineage.transformType() == LineageTransformType.CONCAT_FORMAT),
                    () -> parser.name() + " did not preserve the outer concat: lineages=" + lineages.stream()
                            .map(lineage -> lineage.transformType() + ":" + lineage.sources())
                            .toList() + "; events=" + structured.events());
        }
    }

    @Test
    void arithmeticDominatesNestedCoalesceInsideFunctionForEveryParser() {
        String sql = """
                CREATE OR REPLACE PROCEDURE test_commission() LANGUAGE plpgsql AS $$
                BEGIN
                  INSERT INTO sales_commissions (commission_amount)
                  SELECT ROUND(soi.amount * COALESCE(cr.commission_rate, 0.02), 2)
                  FROM sales_order_items soi
                  LEFT JOIN commission_rules cr ON soi.amount >= cr.min_amount;
                END;
                $$;
                """;
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "ROUTINE:test_commission", 1, sql.lines().count(), Map.of());

        for (ParserCase parser : parsers()) {
            var structured = parser.parser().parseSql(statement, null);
            var lineage = new StructuredDataLineageExtractor().extract(statement, structured).stream()
                    .filter(candidate -> "sales_commissions.commission_amount"
                            .equals(candidate.target().displayName()))
                    .filter(candidate -> candidate.flowKind()
                            == com.relationdetector.contracts.Enums.LineageFlowKind.VALUE)
                    .findFirst().orElseThrow();
            assertEquals(LineageTransformType.ARITHMETIC, lineage.transformType(),
                    () -> parser.name() + " allowed nested COALESCE to override arithmetic: "
                            + structured.events());
        }
    }

    @Test
    void booleanProjectionIsValueFunctionForEveryParser() {
        String sql = """
                CREATE OR REPLACE PROCEDURE test_boolean_projection() LANGUAGE plpgsql AS $$
                BEGIN
                  INSERT INTO category_dim (is_womenwear)
                  SELECT pc.name = '女装' OR parent.name = '女装'
                  FROM product_categories pc
                  LEFT JOIN product_categories parent ON parent.id = pc.parent_id;
                END;
                $$;
                """;
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "ROUTINE:test_boolean_projection", 1, sql.lines().count(), Map.of());

        for (ParserCase parser : parsers()) {
            var structured = parser.parser().parseSql(statement, null);
            var lineage = new StructuredDataLineageExtractor().extract(statement, structured).stream()
                    .filter(candidate -> "category_dim.is_womenwear".equals(candidate.target().displayName()))
                    .filter(candidate -> candidate.flowKind()
                            == com.relationdetector.contracts.Enums.LineageFlowKind.VALUE)
                    .findFirst().orElseThrow();
            assertEquals(LineageTransformType.FUNCTION_CALL, lineage.transformType(),
                    () -> parser.name() + " classified a boolean projection as a direct column: "
                            + structured.events());
        }
    }

    @Test
    void cumulativeWindowDominatesOuterArithmeticForWriteLineage() {
        String sql = """
                CREATE OR REPLACE PROCEDURE test_cumulative() LANGUAGE plpgsql AS $$
                BEGIN
                  INSERT INTO jsh_temp_mock_plan (mock_timestamp_str)
                  SELECT hp.hour_val + SUM(hp.weight) OVER (
                    PARTITION BY hp.org_id ORDER BY hp.hour_val
                  )
                  FROM jsh_temp_hour_pdf hp;
                END;
                $$;
                """;
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "ROUTINE:test_cumulative", 1, sql.lines().count(), Map.of());

        for (ParserCase parser : parsers()) {
            var structured = parser.parser().parseSql(statement, null);
            var lineages = new StructuredDataLineageExtractor().extract(statement, structured);
            var cumulative = lineages.stream()
                    .filter(lineage -> "jsh_temp_mock_plan.mock_timestamp_str"
                            .equals(lineage.target().displayName()))
                    .filter(lineage -> lineage.transformType() == LineageTransformType.CUMULATIVE)
                    .findFirst().orElseThrow(() -> new AssertionError(
                            parser.name() + " did not preserve the cumulative window: "
                                    + structured.events()));
            assertEquals(Set.of("jsh_temp_hour_pdf.hour_val", "jsh_temp_hour_pdf.weight"),
                    cumulative.sources().stream()
                            .map(com.relationdetector.contracts.model.Endpoint::displayName)
                            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)),
                    () -> parser.name() + " treated the window ORDER BY as a VALUE source: "
                            + structured.events());
            var windowControl = lineages.stream()
                    .filter(lineage -> "jsh_temp_mock_plan.mock_timestamp_str"
                            .equals(lineage.target().displayName()))
                    .filter(lineage -> lineage.flowKind()
                            == com.relationdetector.contracts.Enums.LineageFlowKind.CONTROL)
                    .filter(lineage -> lineage.transformType() == LineageTransformType.WINDOW_DERIVED)
                    .findFirst().orElseThrow();
            assertEquals(Set.of("jsh_temp_hour_pdf.org_id", "jsh_temp_hour_pdf.hour_val"),
                    windowControl.sources().stream()
                            .map(com.relationdetector.contracts.model.Endpoint::displayName)
                            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)),
                    () -> parser.name() + " lost window controls: " + structured.events());
        }
    }

    @Test
    void scalarAggregateInCaseConditionIsControlForEveryParser() {
        String sql = """
                CREATE OR REPLACE PROCEDURE test_scalar_case() LANGUAGE plpgsql AS $$
                BEGIN
                  UPDATE users u
                  SET total_spent = COALESCE((
                        SELECT SUM(o.pay_amount)
                        FROM orders o
                        WHERE o.user_id = u.id
                          AND o.order_status = 'PAID'
                      ), 0.00),
                      level = CASE
                        WHEN COALESCE((
                          SELECT SUM(o.pay_amount)
                          FROM orders o
                          WHERE o.user_id = u.id
                            AND o.order_status = 'PAID'
                        ), 0.00) >= 10000 THEN 'VIP'
                        ELSE 'REGULAR'
                      END;
                END;
                $$;
                """;
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "ROUTINE:test_scalar_case", 1, sql.lines().count(), Map.of());

        for (ParserCase parser : parsers()) {
            var structured = parser.parser().parseSql(statement, null);
            Set<String> fingerprints = new StructuredDataLineageExtractor().extract(statement, structured)
                    .stream()
                    .map(this::lineageFingerprint)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            assertTrue(fingerprints.contains(
                            "VALUE:AGGREGATE:orders.pay_amount->users.total_spent"),
                    () -> parser.name() + " lost the scalar projection VALUE: " + fingerprints);
            assertTrue(fingerprints.contains(
                            "CONTROL:CASE_WHEN:orders.pay_amount->users.level"),
                    () -> parser.name() + " did not classify the CASE scalar condition as CONTROL: "
                            + fingerprints);
            assertTrue(fingerprints.contains(
                            "CONTROL:DIRECT:orders.user_id,users.id,orders.order_status->users.level"),
                    () -> parser.name() + " did not preserve scalar-subquery locators as CONTROL: "
                            + fingerprints);
            assertTrue(fingerprints.stream().noneMatch(value ->
                            value.startsWith("VALUE:") && value.endsWith("->users.level")),
                    () -> parser.name() + " emitted CASE condition sources as VALUE: " + fingerprints);
        }
    }

    @Test
    void labeledExceptionBlocksAreSupportedProceduralStructure() {
        String sql = """
                BEGIN
                  <<retry_block>>
                  LOOP
                    BEGIN
                      PERFORM do_work();
                    EXCEPTION
                      WHEN OTHERS THEN
                        EXIT retry_block;
                    END;
                  END LOOP retry_block;
                END;
                """;
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "ROUTINE:test_exception", 40, 50, Map.of());

        PlPgSqlParseOutcome outcome = parseBody(statement);

        assertEquals(0, outcome.unsupportedStatementCount(), () -> outcome.warnings().toString());
        assertEquals(List.of(), outcome.warnings());
    }

    @Test
    void routineBodyInsertSelectKeepsRowsetScope() {
        String body = "INSERT INTO category_dim (source_category_id) "
                + "SELECT pc.id FROM product_categories pc;";
        SqlStatementRecord statement = new SqlStatementRecord(
                body, StatementSourceType.PROCEDURE, "ROUTINE:public.test", 1, 1, java.util.Map.of());
        var events = parseBody(statement).events();

        assertTrue(events.stream().anyMatch(event -> event.type() == StructuredParseEventType.ROWSET_REFERENCE
                        && "product_categories".equals(event.table())),
                () -> "Routine INSERT SELECT must preserve rowset scope: " + events);
    }

    @Test
    void declaredLocalVariableNeverBecomesPhysicalLineageSource() {
        String sql = """
                CREATE OR REPLACE PROCEDURE test_local_source(IN p_order_id BIGINT) LANGUAGE plpgsql AS $$
                DECLARE
                  v_recon_id BIGINT;
                BEGIN
                  INSERT INTO reconciliation_items (reconciliation_id, source_id)
                  SELECT v_recon_id, p_order_id FROM cashier_journals cj;
                END;
                $$;
                """;
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "ROUTINE:test_local_source", 1, sql.lines().count(), Map.of());

        for (ParserCase parser : parsers()) {
            var structured = parser.parser().parseSql(statement, null);
            var lineages = new StructuredDataLineageExtractor().extract(statement, structured);
            assertTrue(lineages.stream().noneMatch(lineage -> lineage.sources().stream().anyMatch(source ->
                            source.displayName().endsWith(".v_recon_id")
                                    || source.displayName().endsWith(".p_order_id"))),
                    () -> parser.name() + " treated a typed PL/pgSQL local/parameter as a physical column: "
                            + lineages);
        }
    }

    @Test
    void allParsersTraceDeepScenarioRoutineWrites() {
        assertForEveryParser("ROUTINE:public.sp_refresh_semantic_dimensions", Set.of(
                "VALUE:sales_orders.order_date->fiscal_calendar.calendar_date",
                "VALUE:payment_receipts.receipt_date->fiscal_calendar.calendar_date",
                "VALUE:sales_returns.return_date->fiscal_calendar.calendar_date",
                "VALUE:product_categories.id->category_dim.source_category_id"));
        assertForEveryParser("ROUTINE:public.sp_onboard_employee_full", Set.of(
                "VALUE:departments.id->employees.department_id",
                "VALUE:positions.id->employees.position_id"));
        assertForEveryParser("ROUTINE:public.sp_run_mrp_for_plan", Set.of(
                "VALUE:supplier_products.supplier_id->mrp_run_items.suggested_supplier_id"));
        assertForEveryParser("ROUTINE:public.sp_refresh_budget_usage", Set.of(
                "VALUE:voucher_items.amount->budget_items.used_amount"));
        assertForEveryParser("ROUTINE:public.sp_issue_repair_order_parts", Set.of(
                "VALUE:repair_order_parts.quantity->inventory_transactions.quantity_change"));
        assertForEveryParser("ROUTINE:public.sp_rebuild_sales_fact", Set.of(
                "VALUE:payments.amount->sales_fact.paid_amount",
                "VALUE:sales_returns.refund_amount->sales_fact.refund_amount"));
    }

    @Test
    void explicitSetProjectionLayoutPreservesEveryPhysicalUnionBranch() {
        String sql = """
                INSERT INTO fiscal_calendar (calendar_date)
                WITH dates(calendar_date) AS (
                    SELECT so.order_date FROM sales_orders so
                    UNION ALL
                    SELECT pr.receipt_date FROM payment_receipts pr
                    UNION ALL
                    SELECT sr.return_date FROM sales_returns sr
                    UNION ALL
                    SELECT DATE '2026-01-01'
                )
                SELECT d.calendar_date FROM dates d;
                """;
        assertSqlForEveryParser("postgres-union.sql", StatementSourceType.PLAIN_SQL, sql, Set.of(
                "VALUE:sales_orders.order_date->fiscal_calendar.calendar_date",
                "VALUE:payment_receipts.receipt_date->fiscal_calendar.calendar_date",
                "VALUE:sales_returns.return_date->fiscal_calendar.calendar_date"));
    }

    @Test
    void directInsertUnionMapsEveryBranchByOrdinal() {
        String sql = """
                INSERT INTO fiscal_calendar (calendar_date)
                SELECT so.order_date FROM sales_orders so
                UNION ALL
                SELECT pr.receipt_date FROM payment_receipts pr
                UNION ALL
                SELECT sr.return_date FROM sales_returns sr;
                """;
        assertSqlForEveryParser("postgres-direct-union.sql", StatementSourceType.PLAIN_SQL, sql, Set.of(
                "VALUE:sales_orders.order_date->fiscal_calendar.calendar_date",
                "VALUE:payment_receipts.receipt_date->fiscal_calendar.calendar_date",
                "VALUE:sales_returns.return_date->fiscal_calendar.calendar_date"));
    }

    @Test
    void nestedUnionMapsEveryLeafBranchWithoutArityDiagnostic() {
        String sql = """
                INSERT INTO fiscal_calendar (calendar_date)
                WITH dates(calendar_date) AS (
                    SELECT so.order_date FROM sales_orders so
                    UNION ALL
                    (SELECT pr.receipt_date FROM payment_receipts pr
                     UNION ALL
                     SELECT sr.return_date FROM sales_returns sr)
                )
                SELECT d.calendar_date FROM dates d;
                """;
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL,
                "postgres-nested-union.sql", 1, sql.lines().count(), Map.of());
        for (ParserCase parser : parsers()) {
            var structured = parser.parser().parseSql(statement, null);
            var actual = new StructuredDataLineageExtractor().extract(statement, structured).stream()
                    .flatMap(lineage -> lineage.sources().stream().map(source -> lineage.flowKind() + ":"
                            + source.displayName() + "->" + lineage.target().displayName()))
                    .collect(java.util.stream.Collectors.toSet());
            assertTrue(actual.containsAll(Set.of(
                            "VALUE:sales_orders.order_date->fiscal_calendar.calendar_date",
                            "VALUE:payment_receipts.receipt_date->fiscal_calendar.calendar_date",
                            "VALUE:sales_returns.return_date->fiscal_calendar.calendar_date")),
                    () -> parser.name() + " lost a nested UNION branch: " + actual);
            assertTrue(structured.warnings().stream().noneMatch(warning ->
                            "POSTGRES_SET_OPERATION_ARITY_MISMATCH".equals(warning.code())),
                    () -> parser.name() + " misdiagnosed valid nested UNION arity: "
                            + structured.warnings());
        }
    }

    @Test
    void wildcardSetBranchHasUnknownArityInsteadOfOneColumn() {
        String sql = """
                WITH left_rows(id, name, city) AS (
                    SELECT c.id, c.name, c.city FROM customers c
                ), right_rows(id, name, city) AS (
                    SELECT s.id, s.name, s.city FROM suppliers s
                ), combined AS (
                    SELECT * FROM left_rows
                    UNION ALL
                    SELECT * FROM right_rows
                    UNION ALL
                    (SELECT c.id, c.name, c.city FROM customers c)
                )
                SELECT * FROM combined;
                """;
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL,
                "postgres-union-wildcard-arity.sql", 1, sql.lines().count(), Map.of());
        for (ParserCase parser : parsers()) {
            var structured = parser.parser().parseSql(statement, null);
            assertTrue(structured.warnings().stream().noneMatch(warning ->
                            "POSTGRES_SET_OPERATION_ARITY_MISMATCH".equals(warning.code())),
                    () -> parser.name() + " treated SELECT * as one projected column: "
                            + structured.warnings());
        }
    }

    @Test
    void setProjectionArityMismatchIsDiagnosedForEveryParser() {
        String sql = """
                WITH mismatched(a) AS (
                    SELECT so.order_date FROM sales_orders so
                    UNION ALL
                    SELECT pr.receipt_date, pr.id FROM payment_receipts pr
                )
                SELECT a FROM mismatched;
                """;
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL,
                "postgres-union-arity.sql", 1, sql.lines().count(), Map.of());
        for (ParserCase parser : parsers()) {
            var structured = parser.parser().parseSql(statement, null);
            assertTrue(structured.warnings().stream().anyMatch(warning ->
                            "POSTGRES_SET_OPERATION_ARITY_MISMATCH".equals(warning.code())),
                    () -> parser.name() + " did not diagnose set-operation arity: "
                            + structured.warnings());
        }
    }

    @Test
    void derivedAggregateDominatesOuterCoalesceForEveryParser() {
        String sql = fixtureObject("ROUTINE:public.sp_refresh_budget_usage");
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "ROUTINE:public.sp_refresh_budget_usage", 1, sql.lines().count(), Map.of());
        for (ParserCase parser : parsers()) {
            var structured = parser.parser().parseSql(statement, null);
            var lineage = new StructuredDataLineageExtractor().extract(statement, structured).stream()
                    .filter(candidate -> "budget_items.used_amount".equals(candidate.target().displayName()))
                    .filter(candidate -> candidate.flowKind()
                            == com.relationdetector.contracts.Enums.LineageFlowKind.VALUE)
                    .findFirst().orElseThrow();
            assertEquals(LineageTransformType.AGGREGATE, lineage.transformType(),
                    () -> parser.name() + " lost the derived aggregate through COALESCE: "
                            + structured.events());
        }
    }

    @Test
    void allParsersTraceNaturalReconciliationProcedure() {
        String sql = sqlObject(
                "sample-data/postgres/18/02-procedures/01-procedures.sql",
                "CREATE OR REPLACE PROCEDURE sp_create_reconciliation",
                "$$;");
        var outerParser = new PostgresRelationSqlParser(new CommonTokenStream(
                new PostgresRelationSqlLexer(CharStreams.fromString(sql))));
        var outerRoot = outerParser.script();
        assertTrue(outerRoot.statement().stream().anyMatch(statement ->
                        statement.routineDeclarationStatement() != null),
                () -> outerRoot.toStringTree(outerParser));
        assertSqlForEveryParser("ROUTINE:public.sp_create_reconciliation",
                StatementSourceType.PROCEDURE, sql, Set.of(
                        "VALUE:cashier_journals.id->reconciliation_items.journal_id",
                        "VALUE:cashier_journals.journal_date->reconciliation_items.transaction_date",
                        "VALUE:cashier_journals.amount->reconciliation_items.debit_amount"));
    }

    @Test
    void allParsersTraceSupplierMetricRoutine() {
        String sql = sqlObject(
                "sample-data/postgres/18/02-procedures/10-supplier-geo-procedures.sql",
                "CREATE OR REPLACE PROCEDURE sp_update_supplier_metrics",
                "$$;");
        assertSqlForEveryParser("ROUTINE:public.sp_update_supplier_metrics", StatementSourceType.PROCEDURE, sql,
                Set.of(
                        "VALUE:purchase_return_items.return_qty->supplier_products.return_rate",
                        "CONTROL:purchase_orders.supplier_id->supplier_products.return_rate",
                        "CONTROL:inspection_reports.inspection_result->supplier_products.quality_score",
                        "VALUE:purchase_orders.id->supplier_products.total_order_count",
                        "VALUE:purchase_order_items.received_qty->supplier_products.total_order_qty"));
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "ROUTINE:public.sp_update_supplier_metrics", 1, sql.lines().count(), Map.of());
        for (ParserCase parser : parsers()) {
            var structured = parser.parser().parseSql(statement, null);
            var returnRate = new StructuredDataLineageExtractor().extract(statement, structured).stream()
                    .filter(lineage -> "supplier_products.return_rate".equals(lineage.target().displayName()))
                    .filter(lineage -> lineage.flowKind()
                            == com.relationdetector.contracts.Enums.LineageFlowKind.VALUE)
                    .findFirst().orElseThrow();
            assertEquals(Set.of("purchase_return_items.return_qty", "purchase_order_items.received_qty"),
                    returnRate.sources().stream()
                            .map(com.relationdetector.contracts.model.Endpoint::displayName)
                            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)),
                    () -> parser.name() + " mixed scalar-subquery predicate columns into VALUE; events="
                            + structured.events());
            assertTrue(new StructuredDataLineageExtractor().extract(statement, structured).stream()
                            .filter(lineage -> "supplier_products.quality_score"
                                    .equals(lineage.target().displayName()))
                            .filter(lineage -> lineage.flowKind()
                                    == com.relationdetector.contracts.Enums.LineageFlowKind.VALUE)
                            .noneMatch(lineage -> lineage.sources().stream().anyMatch(source ->
                                    "inspection_reports.inspection_result".equals(source.displayName()))),
                    () -> parser.name() + " treated CASE predicate as metric VALUE; events="
                            + structured.events());
        }
    }

    @Test
    void allParsersTraceInventoryAndSalesTriggerBodies() {
        String inventoryTrigger = sqlObject(
                "sample-data/postgres/18/01-schema/03-triggers.sql",
                "CREATE OR REPLACE FUNCTION trg_inventory_update_batch",
                "$$ LANGUAGE plpgsql;");
        assertSqlForEveryParser("TRIGGER:public.trg_inventory_update_batch", StatementSourceType.TRIGGER,
                inventoryTrigger, Set.of(
                        "VALUE:inventory.quantity->product_batches.current_qty"));

        String salesTrigger = sqlObject(
                "sample-data/postgres/18/01-schema/03-triggers.sql",
                "CREATE OR REPLACE FUNCTION trg_sales_order_delivered",
                "$$ LANGUAGE plpgsql;");
        assertSqlForEveryParser("TRIGGER:public.trg_sales_order_delivered", StatementSourceType.TRIGGER,
                salesTrigger, Set.of(
                        "VALUE:sales_order_items.product_id->inventory_transactions.product_id",
                        "VALUE:sales_order_items.quantity->inventory_transactions.quantity_change",
                        "VALUE:inventory.quantity->inventory_transactions.before_qty"));
        SqlStatementRecord statement = new SqlStatementRecord(salesTrigger, StatementSourceType.TRIGGER,
                "TRIGGER:public.trg_sales_order_delivered", 1, salesTrigger.lines().count(), Map.of());
        for (ParserCase parser : parsers()) {
            var structured = parser.parser().parseSql(statement, null);
            var lineages = new StructuredDataLineageExtractor().extract(statement, structured);
            assertLineageSources(parser.name(), lineages, "inventory_transactions.before_qty",
                    com.relationdetector.contracts.Enums.LineageFlowKind.VALUE,
                    Set.of("inventory.quantity"));
            assertLineageSources(parser.name(), lineages, "inventory_transactions.before_qty",
                    com.relationdetector.contracts.Enums.LineageFlowKind.CONTROL,
                    Set.of("inventory.product_id", "sales_order_items.product_id",
                            "inventory.warehouse_id", "inventory.batch_id", "sales_order_items.batch_id"));
            assertLineageSources(parser.name(), lineages, "inventory_transactions.after_qty",
                    com.relationdetector.contracts.Enums.LineageFlowKind.VALUE,
                    Set.of("inventory.quantity", "sales_order_items.quantity"));
        }
    }

    @Test
    void naturalRoutineFileKeepsContractMilestoneJoin() throws IOException {
        Path path = workspaceRoot().resolve(
                "sample-data/postgres/18/02-procedures/06-third-batch-functions.sql");
        var script = new PostgresScriptFramer().frame(new ScriptFrameRequest(
                Files.readString(path), path.toString(), StatementSourceType.PROCEDURE));
        SqlStatementRecord statement = script.statements().stream()
                .filter(candidate -> "fn_get_project_completion_pct".equals(
                        candidate.attributes().get("sourceObjectName")))
                .findFirst()
                .orElseThrow();
        assertTrue(statement.sql().contains("JOIN contracts c ON cm.contract_id = c.id"),
                () -> "Script parser truncated function body: " + statement.sql());
        int bodyStart = statement.sql().indexOf("$$") + 2;
        int bodyEnd = statement.sql().lastIndexOf("$$");
        SqlStatementRecord bodyStatement = new SqlStatementRecord(
                statement.sql().substring(bodyStart, bodyEnd), statement.sourceType(),
                statement.sourceName(), statement.startLine(), statement.endLine(), statement.attributes());
        var bodyOutcome = parseBody(bodyStatement);
        assertTrue(bodyOutcome.events().stream().anyMatch(event ->
                        event.type() == StructuredParseEventType.PREDICATE_EQUALITY
                                && "cm".equals(event.left().alias())
                                && "contract_id".equals(event.left().column())
                                && "c".equals(event.right().alias())
                                && "id".equals(event.right().column())),
                () -> "Routine body recovery missed contract join; warnings=" + bodyOutcome.warnings()
                        + "; events=" + bodyOutcome.events());

        for (ParserCase parser : parsers()) {
            var structured = parser.parser().parseSql(statement, null);
            var relationships = new StructuredRelationshipExtractor().extract(statement, structured);
            assertTrue(relationships.stream().anyMatch(candidate ->
                            "contract_milestones.contract_id".equals(candidate.source().displayName())
                                    && "contracts.id".equals(candidate.target().displayName())),
                    () -> parser.name() + " silently dropped the routine; warnings="
                            + structured.warnings() + "; attributes=" + structured.attributes()
                            + "; events=" + structured.events());
        }
    }

    @Test
    void allParsersKeepNullSafeEqualityInsideRoutineUpdate() {
        String sql = """
                CREATE OR REPLACE PROCEDURE test_null_safe_update() LANGUAGE plpgsql AS $$
                DECLARE
                  v_task_id BIGINT;
                BEGIN
                  UPDATE inventory_location_balances ilb
                  SET locked_quantity = ilb.locked_quantity + pti.required_qty
                  FROM picking_task_items pti
                  JOIN picking_tasks pt ON pt.id = pti.picking_task_id
                  WHERE pti.location_id = ilb.location_id
                    AND pti.product_id = ilb.product_id
                    AND pti.batch_id IS NOT DISTINCT FROM ilb.batch_id
                    AND pt.id = v_task_id;
                END;
                $$;
                """;
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "ROUTINE:test_null_safe_update", 1, sql.lines().count(), Map.of());
        for (ParserCase parser : parsers()) {
            var structured = parser.parser().parseSql(statement, null);
            var relationships = new StructuredRelationshipExtractor().extract(statement, structured);
            assertTrue(relationships.stream().anyMatch(candidate ->
                            "inventory_location_balances.batch_id".equals(candidate.source().displayName())
                                    && "picking_task_items.batch_id".equals(candidate.target().displayName())
                            || "picking_task_items.batch_id".equals(candidate.source().displayName())
                                    && "inventory_location_balances.batch_id".equals(candidate.target().displayName())),
                    () -> parser.name() + " lost routine null-safe equality; events=" + structured.events());
        }
    }

    @Test
    void naturalPickingRoutineKeepsNullSafeEqualityForEveryParser() {
        String sql = sqlObject(
                "sample-data/postgres/18/02-procedures/13-erp-deep-scenario-procedures.sql",
                "CREATE OR REPLACE PROCEDURE sp_generate_picking_task_for_order",
                "$$;");
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "ROUTINE:public.sp_generate_picking_task_for_order", 1, sql.lines().count(), Map.of());

        for (ParserCase parser : parsers()) {
            var structured = parser.parser().parseSql(statement, null);
            var relationships = new StructuredRelationshipExtractor().extract(statement, structured);
            assertTrue(relationships.stream().anyMatch(candidate ->
                            "inventory_location_balances.batch_id".equals(candidate.source().displayName())
                                    && "picking_task_items.batch_id".equals(candidate.target().displayName())
                            || "picking_task_items.batch_id".equals(candidate.source().displayName())
                                    && "inventory_location_balances.batch_id"
                                    .equals(candidate.target().displayName())),
                    () -> parser.name() + " lost the natural routine null-safe equality; warnings="
                            + structured.warnings() + "; relationships=" + relationships.stream()
                            .map(candidate -> candidate.source().displayName() + "->"
                                    + candidate.target().displayName())
                            .toList());
        }
    }

    @Test
    void naturalForecastRoutineKeepsDerivedCteJoinObservationForEveryParser() {
        String sql = sqlObject(
                "sample-data/postgres/18/02-procedures/07-store-customer-procedures.sql",
                "CREATE OR REPLACE FUNCTION sp_store_sales_forecast",
                "$$;");
        String marker = "JOIN warehouses w ON lm.warehouse_id = w.id";
        int markerOffset = sql.indexOf(marker);
        assertTrue(markerOffset >= 0);
        long expectedLine = sql.substring(0, markerOffset).lines().count();
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.FUNCTION,
                "ROUTINE:sp_store_sales_forecast", 1, sql.lines().count(), Map.of());

        for (ParserCase parser : parsers()) {
            var structured = parser.parser().parseSql(statement, null);
            var relationships = new StructuredRelationshipExtractor().extract(statement, structured);
            assertTrue(relationships.stream().anyMatch(candidate ->
                            "sales_orders.warehouse_id".equals(candidate.source().displayName())
                                    && "warehouses.id".equals(candidate.target().displayName())
                                    && candidate.evidence().stream().anyMatch(evidence ->
                                    Long.valueOf(expectedLine).equals(asLong(
                                            evidence.attributes().get("sourceLine"))))),
                    () -> parser.name() + " lost the derived CTE join at line " + expectedLine
                            + "; relationships=" + relationships.stream()
                            .map(candidate -> candidate.source().displayName() + "->"
                                    + candidate.target().displayName() + "@"
                                    + candidate.evidence().stream()
                                    .map(evidence -> evidence.attributes().get("sourceLine"))
                                    .toList())
                            .toList()
                            + "; warnings=" + structured.warnings()
                            + "; events=" + structured.events());
        }
    }

    private void assertForEveryParser(String sourceObject, Set<String> expected) {
        String sql = fixtureObject(sourceObject);
        assertSqlForEveryParser(sourceObject, StatementSourceType.PROCEDURE, sql, expected);
    }

    private void assertSqlForEveryParser(
            String sourceObject,
            StatementSourceType sourceType,
            String sql,
            Set<String> expected
    ) {
        SqlStatementRecord statement = new SqlStatementRecord(
                sql, sourceType, sourceObject, 1, sql.lines().count(), java.util.Map.of());
        for (ParserCase parser : parsers()) {
            var structured = parser.parser().parseSql(statement, null);
            Set<String> actual = new LinkedHashSet<>();
            new StructuredDataLineageExtractor().extract(statement, structured).forEach(lineage ->
                    lineage.sources().forEach(source -> actual.add(lineage.flowKind().name() + ":"
                            + endpoint(source.displayName()) + "->" + endpoint(lineage.target().displayName()))));
            assertTrue(actual.containsAll(expected), () -> parser.name() + " missing "
                    + difference(expected, actual) + " for " + sourceObject + "; actual=" + actual
                    + "; warnings=" + structured.warnings() + "; events=" + structured.events());
        }
    }

    private String sqlObject(String relativePath, String startMarker, String terminator) {
        Path path = workspaceRoot().resolve(relativePath);
        try {
            String text = Files.readString(path);
            int start = text.indexOf(startMarker);
            if (start < 0) {
                throw new IllegalStateException("Missing " + startMarker + " in " + path);
            }
            int end = text.indexOf(terminator, start);
            if (end < 0) {
                throw new IllegalStateException("Missing " + terminator + " after " + startMarker);
            }
            return text.substring(start, end + terminator.length()).strip();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + path, exception);
        }
    }

    private List<ParserCase> parsers() {
        return List.of(
                new ParserCase("token-event", new PostgresTokenEventStructuredSqlParser()),
                new ParserCase("v16-full", new FullGrammarDialectModule().sqlParser()),
                new ParserCase("v17-full",
                        new com.relationdetector.postgres.fullgrammar.v17.FullGrammarDialectModule().sqlParser()),
                new ParserCase("v18-full",
                        new com.relationdetector.postgres.fullgrammar.v18.FullGrammarDialectModule().sqlParser()));
    }

    private PlPgSqlParseOutcome parseBody(SqlStatementRecord statement) {
        return new TokenEventPlPgSqlBodyParser().parse(
                statement, null, new PostgresTokenEventStructuredSqlParser());
    }

    private String fixtureObject(String sourceObject) {
        Path path = workspaceRoot().resolve("sample-data/postgres/18/02-procedures/13-erp-deep-scenario-procedures.sql");
        try {
            String text = Files.readString(path);
            String marker = "-- relation-detector-fixture-source: " + sourceObject;
            int markerStart = text.indexOf(marker);
            if (markerStart < 0) {
                throw new IllegalStateException("Missing fixture marker " + marker);
            }
            int start = text.indexOf('\n', markerStart) + 1;
            int end = text.indexOf("-- relation-detector-fixture-end", start);
            if (end < 0) {
                throw new IllegalStateException("Missing fixture end for " + sourceObject);
            }
            return text.substring(start, end).strip();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + path, exception);
        }
    }

    private Path workspaceRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("sample-data/postgres"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate relation-detector module root");
    }

    private String endpoint(String displayName) {
        return displayName.startsWith("public.") ? displayName.substring("public.".length()) : displayName;
    }

    private void assertLineageSources(
            String parser,
            List<com.relationdetector.contracts.model.DataLineageCandidate> lineages,
            String target,
            com.relationdetector.contracts.Enums.LineageFlowKind flowKind,
            Set<String> expected
    ) {
        var lineage = lineages.stream()
                .filter(candidate -> target.equals(candidate.target().displayName()))
                .filter(candidate -> candidate.flowKind() == flowKind)
                .findFirst().orElseThrow(() -> new AssertionError(
                        parser + " missing " + flowKind + " lineage for " + target + ": " + lineages));
        assertEquals(expected, lineage.sources().stream()
                        .map(com.relationdetector.contracts.model.Endpoint::displayName)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)),
                () -> parser + " resolved the wrong scalar-subquery scope for " + target + ": " + lineages);
    }

    private Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String lineageFingerprint(com.relationdetector.contracts.model.DataLineageCandidate lineage) {
        return lineage.flowKind().name() + ":" + lineage.transformType().name() + ":"
                + lineage.sources().stream()
                        .map(source -> endpoint(source.displayName()))
                        .collect(java.util.stream.Collectors.joining(","))
                + "->" + endpoint(lineage.target().displayName());
    }

    private Set<String> difference(Set<String> expected, Set<String> actual) {
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        return missing;
    }

    private record ParserCase(String name, StructuredSqlParser parser) {
    }
}
