package com.relationdetector.postgres.routine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.postgres.fullgrammer.v16.PostgresFullGrammerDialectModule;
import com.relationdetector.postgres.tokenevent.PostgresTokenEventStructuredSqlParser;

class PostgresRoutineSampleLineageTest {
    @Test
    void routineBodyInsertSelectKeepsRowsetScope() {
        String body = "INSERT INTO category_dim (source_category_id) "
                + "SELECT pc.id FROM product_categories pc;";
        SqlStatementRecord statement = new SqlStatementRecord(
                body, StatementSourceType.PROCEDURE, "ROUTINE:public.test", 1, 1, java.util.Map.of());
        var events = PostgresRoutineBodyParser.extract(statement);

        assertTrue(events.stream().anyMatch(event -> event.type() == StructuredParseEventType.ROWSET_REFERENCE
                        && "product_categories".equals(event.attributes().get("table"))),
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
