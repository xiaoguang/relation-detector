package com.relationdetector.core.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.parse.ExpressionTrace;
import com.relationdetector.contracts.parse.ProjectionEvent;
import com.relationdetector.contracts.parse.RowsetEvent;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.WriteEvent;
import com.relationdetector.core.identity.AliasSymbolTable;
import com.relationdetector.core.identity.CanonicalIdentifierResolver;
import com.relationdetector.core.identity.NamespaceContext;

class ProjectionTraceResolverTest {
    @Test
    void resolvesAliasedColumnsThroughMultipleStructuredCteLayers() {
        List<StructuredSqlEvent> events = List.of(
                cte("recent_orders"),
                rowset("orders", "o"),
                projection("recent_orders", "customer_id", "o", "customer_id", "DIRECT"),
                cte("regional_orders"),
                rowset("recent_orders", "ro"),
                projection("regional_orders", "customer_id", "ro", "customer_id", "DIRECT"),
                cte("customer_orders"),
                rowset("regional_orders", "regional_orders"),
                rowset("customers", "c"),
                projection("customer_orders", "account_id", "c", "account_id", "DIRECT"),
                projection("customer_orders", "customer_id", "regional_orders", "customer_id", "DIRECT"));

        ProjectionTraceResolver resolver = ProjectionTraceResolver.fromEvents(
                events,
                aliases(events),
                Set.of("recent_orders", "regional_orders", "customer_orders"));

        var customerId = resolver.resolve("customer_orders", "customer_id");
        assertTrue(customerId.isPresent());
        assertEquals("orders.customer_id", customerId.get().sources().get(0).displayName());

        var accountId = resolver.resolve("customer_orders", "account_id");
        assertTrue(accountId.isPresent());
        assertEquals("customers.account_id", accountId.get().sources().get(0).displayName());
    }

    @Test
    void preservesAggregateTransformWhenProjectionFeedsWriteMapping() {
        List<StructuredSqlEvent> events = List.of(
                cte("payment_totals"),
                rowset("payments", "p"),
                projection("payment_totals", "paid_amount", "p", "amount", "AGGREGATE"),
                rowset("payment_totals", "pt"));

        ProjectionTraceResolver resolver = ProjectionTraceResolver.fromEvents(
                events,
                aliases(events),
                Set.of("payment_totals"));

        var trace = resolver.resolve("pt", "paid_amount");
        assertTrue(trace.isPresent());
        assertEquals("payments.amount", trace.get().sources().get(0).displayName());
        assertEquals(LineageTransformType.AGGREGATE, trace.get().transform());
    }

    @Test
    void propagatesKnownColumnsThroughTypedWildcardProjectionLayers() {
        List<StructuredSqlEvent> events = List.of(
                cte("aggregated_changes"),
                rowset("inventory_changes", "source"),
                projection("aggregated_changes", "quantity_delta", "source", "quantity_delta", "AGGREGATE"),
                cte("validated_changes"),
                rowset("aggregated_changes", "aggregated"),
                projection("validated_changes", "*", "aggregated", "*", "DIRECT"),
                cte("risk_assessed"),
                rowset("validated_changes", "validated"),
                projection("risk_assessed", "*", "validated", "*", "DIRECT"),
                rowset("risk_assessed", "merge_source"));

        ProjectionTraceResolver resolver = ProjectionTraceResolver.fromEvents(
                events,
                aliases(events),
                Set.of("aggregated_changes", "validated_changes", "risk_assessed"));

        var trace = resolver.resolve("merge_source", "quantity_delta").orElseThrow();
        assertEquals(List.of("inventory_changes.quantity_delta"),
                trace.sources().stream().map(source -> source.displayName()).toList());
        assertEquals(LineageTransformType.AGGREGATE, trace.transform());
    }

    @Test
    void mergesAllUnionBranchSourcesForTheSameProjection() {
        List<StructuredSqlEvent> events = List.of(
                cte("all_payments"),
                rowset("cash_payments", "cash"),
                rowset("bank_payments", "bank"),
                projection("all_payments", "amount", "cash", "amount", "DIRECT"),
                projection("all_payments", "amount", "bank", "amount", "DIRECT"),
                rowset("all_payments", "ap"));

        ProjectionTraceResolver resolver = ProjectionTraceResolver.fromEvents(
                events,
                aliases(events),
                Set.of("all_payments"));

        var trace = resolver.resolve("ap", "amount").orElseThrow();
        assertEquals(Set.of("cash_payments.amount", "bank_payments.amount"),
                trace.sources().stream().map(source -> source.displayName()).collect(java.util.stream.Collectors.toSet()),
                "A UNION projection must preserve sources from every branch");
    }

    @Test
    void preservesTheTypedControlDependencyTransform() {
        assertEquals(LineageTransformType.DIRECT,
                ProjectionTraceResolver.effectiveTransform(
                        "DIRECT", List.of(LineageTransformType.AGGREGATE), LineageFlowKind.CONTROL));
        assertEquals(LineageTransformType.CASE_WHEN,
                ProjectionTraceResolver.effectiveTransform(
                        "CASE_WHEN", List.of(LineageTransformType.ARITHMETIC), LineageFlowKind.CONTROL));
        assertEquals(LineageTransformType.AGGREGATE,
                ProjectionTraceResolver.effectiveTransform(
                        "AGGREGATE", List.of(LineageTransformType.DIRECT), LineageFlowKind.CONTROL));
        assertEquals(LineageTransformType.WINDOW_DERIVED,
                ProjectionTraceResolver.effectiveTransform(
                        "WINDOW_DERIVED", List.of(LineageTransformType.DIRECT), LineageFlowKind.CONTROL));
    }

    @Test
    void projectedControlTransformSurvivesAnOuterValueExpression() {
        assertEquals(LineageTransformType.CASE_WHEN,
                ProjectionTraceResolver.effectiveTransform(
                        "COALESCE", List.of(LineageTransformType.CASE_WHEN),
                        LineageFlowKind.CONTROL, LineageFlowKind.VALUE));
        assertEquals(LineageTransformType.AGGREGATE,
                ProjectionTraceResolver.effectiveTransform(
                        "DIRECT", List.of(LineageTransformType.AGGREGATE),
                        LineageFlowKind.CONTROL, LineageFlowKind.VALUE));
    }

    @Test
    void explicitProjectionTraceWinsOverPartialPhysicalAnchor() {
        List<StructuredSqlEvent> events = List.of(
                cte("rand_tbl"),
                rowset("jsh_orga_user_rel", "emp"),
                projection("rand_tbl", "user_id", "emp", "user_id", "DIRECT"),
                projection(
                        "rand_tbl", "user_id", "o", "org_id",
                        LineageFlowKind.CONTROL, LineageTransformType.CASE_WHEN),
                cte("o"),
                rowset("jsh_temp_org_pdf", "o"),
                projection("o", "org_id", "jsh_temp_org_pdf", "org_id", "DIRECT"));

        AliasSymbolTable aliases = aliases(events);
        Set<String> ignored = Set.of("rand_tbl", "o");
        ProjectionTraceResolver resolver = ProjectionTraceResolver.fromEvents(events, aliases, ignored);
        StructuredSqlEvent write = new WriteEvent(
                StructuredParseEventType.INSERT_SELECT_MAPPING,
                provenance(), "", "", "", "", "target_table", "user_id", "INSERT_SELECT",
                ExpressionTrace.of(
                        List.of("rand_tbl"), List.of("user_id"),
                        LineageFlowKind.VALUE, LineageTransformType.DIRECT));

        List<ProjectionTraceResolver.SourceResolution> resolutions =
                resolver.resolveSources(write, aliases, ignored);

        assertEquals(
                Set.of("jsh_orga_user_rel.user_id", "jsh_temp_org_pdf.org_id"),
                resolutions.stream()
                        .flatMap(resolution -> resolution.sources().stream())
                        .map(source -> source.displayName())
                        .collect(java.util.stream.Collectors.toSet()));
        assertTrue(resolutions.stream().anyMatch(
                resolution -> resolution.flowKind() == LineageFlowKind.CONTROL
                        && resolution.sources().stream().anyMatch(
                        source -> source.displayName().equals("jsh_temp_org_pdf.org_id"))));
    }

    @Test
    void mergeSourceAliasMayShadowInnerPhysicalAliasAcrossWildcardCtes() {
        List<StructuredSqlEvent> events = List.of(
                cte("s"),
                rowset("inventory_target", "t"),
                rowset("s", "s"),
                cte("aggregated"),
                projection("aggregated", "total_qty_delta", "s", "quantity_delta", "AGGREGATE"),
                rowset("inventory_source", "s"),
                cte("validated"),
                projection("validated", "*", "aggregated", "*", "DIRECT"),
                rowset("aggregated", "a"),
                cte("risk_assessed"),
                projection("risk_assessed", "*", "validated", "*", "DIRECT"),
                rowset("validated", "v"),
                projection("s", "*", "risk_assessed", "*", "DIRECT"),
                rowset("risk_assessed", "risk_assessed"));

        AliasSymbolTable aliases = aliases(events);
        Set<String> ignored = Set.of("s", "aggregated", "validated", "risk_assessed");
        ProjectionTraceResolver resolver = ProjectionTraceResolver.fromEvents(events, aliases, ignored);
        StructuredSqlEvent write = new WriteEvent(
                StructuredParseEventType.MERGE_WRITE_MAPPING,
                provenance(), "", "", "", "", "inventory_target", "quantity", "MERGE_UPDATE",
                ExpressionTrace.of(
                        List.of("t", "s"), List.of("quantity", "total_qty_delta"),
                        LineageFlowKind.VALUE, LineageTransformType.ARITHMETIC));

        Set<String> sources = resolver.resolveSources(write, aliases, ignored).stream()
                .flatMap(resolution -> resolution.sources().stream())
                .map(source -> source.displayName())
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(Set.of("inventory_target.quantity", "inventory_source.total_qty_delta"), sources);
    }

    @Test
    void explicitProjectionWithoutColumnSourcesBlocksPhysicalAnchorFallback() {
        List<StructuredSqlEvent> events = List.of(
                cte("staffing"),
                rowset("employees", "e"),
                projection("staffing", "department_id", "e", "department_id", "DIRECT"),
                new ProjectionEvent(
                        StructuredParseEventType.PROJECTION_ITEM,
                        provenance(), "staffing", "active_headcount", ExpressionTrace.empty()),
                rowset("staffing", "staffing"));

        AliasSymbolTable aliases = aliases(events);
        Set<String> ignored = Set.of("staffing");
        ProjectionTraceResolver resolver = ProjectionTraceResolver.fromEvents(events, aliases, ignored);
        StructuredSqlEvent write = new WriteEvent(
                StructuredParseEventType.UPDATE_ASSIGNMENT,
                provenance(), "", "", "", "", "departments", "status", "UPDATE_SET",
                ExpressionTrace.of(
                        List.of("staffing"), List.of("active_headcount"),
                        LineageFlowKind.CONTROL, LineageTransformType.CASE_WHEN));

        assertTrue(resolver.resolveSources(write, aliases, ignored).isEmpty(),
                "COUNT(*) or literal projections have no physical column source and must not become employees.active_headcount");
    }

    private static StructuredSqlEvent cte(String name) {
        return new RowsetEvent(StructuredParseEventType.CTE_DECLARATION, provenance(),
                "", name, name, "", name, "", "");
    }

    private static StructuredSqlEvent rowset(String table, String alias) {
        return new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance(),
                "FROM", table, table, alias, "", "", "");
    }

    private static StructuredSqlEvent projection(
            String outputAlias,
            String outputColumn,
            String sourceAlias,
            String sourceColumn,
            String transform
    ) {
        return projection(
                outputAlias, outputColumn, sourceAlias, sourceColumn,
                LineageFlowKind.VALUE, LineageTransformType.valueOf(transform));
    }

    private static StructuredSqlEvent projection(
            String outputAlias,
            String outputColumn,
            String sourceAlias,
            String sourceColumn,
            LineageFlowKind flowKind,
            LineageTransformType transform
    ) {
        return new ProjectionEvent(StructuredParseEventType.PROJECTION_ITEM, provenance(),
                outputAlias, outputColumn, ExpressionTrace.of(
                        List.of(sourceAlias), List.of(sourceColumn), flowKind, transform));
    }

    private static SourceProvenance provenance() {
        return SourceProvenance.source("projection-trace-test.sql", 1);
    }

    private static AliasSymbolTable aliases(List<StructuredSqlEvent> events) {
        AliasSymbolTable aliases = new AliasSymbolTable(
                new CanonicalIdentifierResolver(value -> value == null ? "" : value.toLowerCase()),
                NamespaceContext.empty());
        for (StructuredSqlEvent event : events) {
            if (event.type() != StructuredParseEventType.ROWSET_REFERENCE) {
                continue;
            }
            String table = event.qualifiedTable();
            TableId tableId = TableId.of(null, table);
            aliases.bind(table, tableId);
            String alias = event.alias();
            if (!alias.isBlank()) {
                aliases.bind(alias, tableId);
            }
        }
        return aliases;
    }
}
