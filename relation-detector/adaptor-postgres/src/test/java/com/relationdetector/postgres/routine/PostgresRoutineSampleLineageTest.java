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
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.ScriptParseRequest;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.core.relation.TokenEventRelationExtractor;
import com.relationdetector.postgres.fullgrammer.v16.PostgresFullGrammerDialectModule;
import com.relationdetector.postgres.script.PostgresScriptParser;
import com.relationdetector.postgres.tokenevent.PostgresTokenEventStructuredSqlParser;

class PostgresRoutineSampleLineageTest {

    @Test
    void expressionGrammarConsumesPostgresConcat() {
        PostgresRoutineBodySqlParser parser = new PostgresRoutineBodySqlParser(new CommonTokenStream(
                new PostgresRoutineBodySqlLexer(CharStreams.fromString(
                        "'MRP-' || REPLACE(p.plan_month, '-', '') || '-' || p.id"))));

        PostgresRoutineBodySqlParser.ExpressionContext expression = parser.expression();

        assertEquals(Token.EOF, parser.getCurrentToken().getType(), expression.toStringTree(parser));
        assertTrue(expression.expressionContinuation().CONCAT() != null,
                expression.toStringTree(parser));
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

        PostgresRoutineParseOutcome outcome = PostgresRoutineBodyParser.parse(statement);

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

        PostgresRoutineParseOutcome outcome = PostgresRoutineBodyParser.parse(statement);

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

        PostgresRoutineParseOutcome outcome = PostgresRoutineBodyParser.parse(statement);

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

        PostgresRoutineParseOutcome outcome = PostgresRoutineBodyParser.parse(statement);

        assertEquals(List.of(), outcome.warnings());
        assertTrue(outcome.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.PREDICATE_EQUALITY));
    }

    @Test
    void outerConcatDominatesNestedCoalesceForWriteLineage() {
        String expressionSql = "cj.journal_type::TEXT || ' - ' || COALESCE(cj.counterparty, '')"
                + " || ' ' || COALESCE(cj.remark, '')";
        PostgresRoutineBodySqlParser expressionParser = new PostgresRoutineBodySqlParser(new CommonTokenStream(
                new PostgresRoutineBodySqlLexer(CharStreams.fromString(expressionSql))));
        var expression = expressionParser.expression();
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
        var analyses = new PostgresRoutineBodyParseTreeVisitor(statement).writeAnalyses(expression);
        assertTrue(analyses.stream().anyMatch(analysis ->
                        analysis.transform() == LineageTransformType.CONCAT_FORMAT),
                () -> "Expression analysis lost the outer concat: " + analyses);
        PostgresRoutineBodySqlParser scriptParser = new PostgresRoutineBodySqlParser(new CommonTokenStream(
                new PostgresRoutineBodySqlLexer(CharStreams.fromString(bodySql))));
        var item = scriptParser.script().statement(1).insertSelectStatement().selectStatement()
                .querySpecification().selectList().selectItem(0);
        var itemAnalyses = new PostgresRoutineBodyParseTreeVisitor(statement).selectItemAnalyses(item);
        assertTrue(itemAnalyses.stream().anyMatch(analysis ->
                        analysis.transform() == LineageTransformType.CONCAT_FORMAT),
                () -> "SELECT item analysis lost the outer concat: " + item.toStringTree(scriptParser)
                        + "; analyses=" + itemAnalyses);

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
    void cumulativeWindowDominatesOuterArithmeticForWriteLineage() {
        String sql = """
                CREATE OR REPLACE PROCEDURE test_cumulative() LANGUAGE plpgsql AS $$
                BEGIN
                  INSERT INTO jsh_temp_mock_plan (mock_timestamp_str)
                  SELECT hp.hour_val + SUM(hp.weight) OVER (ORDER BY hp.hour_val)
                  FROM jsh_temp_hour_pdf hp;
                END;
                $$;
                """;
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "ROUTINE:test_cumulative", 1, sql.lines().count(), Map.of());

        for (ParserCase parser : parsers()) {
            var structured = parser.parser().parseSql(statement, null);
            var lineages = new StructuredDataLineageExtractor().extract(statement, structured);
            assertTrue(lineages.stream().anyMatch(lineage ->
                            "jsh_temp_mock_plan.mock_timestamp_str".equals(lineage.target().displayName())
                                    && lineage.transformType() == LineageTransformType.CUMULATIVE),
                    () -> parser.name() + " did not preserve the cumulative window: "
                            + structured.events());
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

        PostgresRoutineParseOutcome outcome = PostgresRoutineBodyParser.parse(statement);

        assertEquals(0, outcome.unsupportedStatementCount(), () -> outcome.warnings().toString());
        assertEquals(List.of(), outcome.warnings());
    }

    @Test
    void routineBodyInsertSelectKeepsRowsetScope() {
        String body = "INSERT INTO category_dim (source_category_id) "
                + "SELECT pc.id FROM product_categories pc;";
        SqlStatementRecord statement = new SqlStatementRecord(
                body, StatementSourceType.PROCEDURE, "ROUTINE:public.test", 1, 1, java.util.Map.of());
        var events = PostgresRoutineBodyParser.extract(statement);

        assertTrue(events.stream().anyMatch(event -> event.type() == StructuredParseEventType.ROWSET_REFERENCE
                        && "product_categories".equals(event.table())),
                () -> "Routine INSERT SELECT must preserve rowset scope: " + events);
    }

    @Test
    void allParsersTraceDeepScenarioRoutineWrites() {
        assertForEveryParser("ROUTINE:public.sp_refresh_semantic_dimensions", Set.of(
                "VALUE:sales_orders.order_date->fiscal_calendar.calendar_date",
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
                        "VALUE:purchase_order_items.received_qty->supplier_products.total_order_qty"));
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
    }

    @Test
    void naturalRoutineFileKeepsContractMilestoneJoin() throws IOException {
        Path path = workspaceRoot().resolve(
                "sample-data/postgres/18/02-procedures/06-third-batch-functions.sql");
        var script = new PostgresScriptParser().parse(new ScriptParseRequest(
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
        var bodyOutcome = PostgresRoutineBodyParser.parse(bodyStatement);
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
            var relationships = new TokenEventRelationExtractor().extract(statement, structured);
            assertTrue(relationships.stream().anyMatch(candidate ->
                            "contract_milestones.contract_id".equals(candidate.source().displayName())
                                    && "contracts.id".equals(candidate.target().displayName())),
                    () -> parser.name() + " silently dropped the routine; warnings="
                            + structured.warnings() + "; attributes=" + structured.attributes()
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
                    + "; events=" + structured.events());
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
                new ParserCase("v16-full", new PostgresFullGrammerDialectModule().sqlParser()),
                new ParserCase("v17-full",
                        new com.relationdetector.postgres.fullgrammer.v17.PostgresFullGrammerDialectModule().sqlParser()),
                new ParserCase("v18-full",
                        new com.relationdetector.postgres.fullgrammer.v18.PostgresFullGrammerDialectModule().sqlParser()));
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

    private Set<String> difference(Set<String> expected, Set<String> actual) {
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        return missing;
    }

    private record ParserCase(String name, StructuredSqlParser parser) {
    }
}
