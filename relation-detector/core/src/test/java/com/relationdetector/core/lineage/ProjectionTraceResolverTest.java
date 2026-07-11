package com.relationdetector.core.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

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
    void fixesEveryControlTraceAtCaseWhenRegardlessOfNestedValueTransform() {
        assertEquals(LineageTransformType.CASE_WHEN,
                ProjectionTraceResolver.effectiveTransform(
                        "AGGREGATE", List.of(LineageTransformType.AGGREGATE), LineageFlowKind.CONTROL));
        assertEquals(LineageTransformType.CASE_WHEN,
                ProjectionTraceResolver.effectiveTransform(
                        "ARITHMETIC", List.of(LineageTransformType.ARITHMETIC), LineageFlowKind.CONTROL));
    }

    private static StructuredSqlEvent cte(String name) {
        return event(StructuredParseEventType.CTE_DECLARATION, Map.of(
                "name", name,
                "table", name,
                "qualifiedTable", name));
    }

    private static StructuredSqlEvent rowset(String table, String alias) {
        return event(StructuredParseEventType.ROWSET_REFERENCE, Map.of(
                "table", table,
                "qualifiedTable", table,
                "alias", alias));
    }

    private static StructuredSqlEvent projection(
            String outputAlias,
            String outputColumn,
            String sourceAlias,
            String sourceColumn,
            String transform
    ) {
        return event(StructuredParseEventType.PROJECTION_ITEM, Map.of(
                "outputAlias", outputAlias,
                "outputColumn", outputColumn,
                "sourceAliases", List.of(sourceAlias),
                "sourceColumns", List.of(sourceColumn),
                "transformType", transform));
    }

    private static StructuredSqlEvent event(StructuredParseEventType type, Map<String, Object> attrs) {
        return new StructuredSqlEvent(type, "projection-trace-test.sql", 1, attrs);
    }

    private static Map<String, TableId> aliases(List<StructuredSqlEvent> events) {
        Map<String, TableId> aliases = new LinkedHashMap<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() != StructuredParseEventType.ROWSET_REFERENCE) {
                continue;
            }
            String table = String.valueOf(event.attributes().get("qualifiedTable"));
            TableId tableId = TableId.of(null, table);
            aliases.put(table.toLowerCase(), tableId);
            String alias = String.valueOf(event.attributes().get("alias"));
            if (!alias.isBlank()) {
                aliases.put(alias.toLowerCase(), tableId);
            }
        }
        return aliases;
    }
}
