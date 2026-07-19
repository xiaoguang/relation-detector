package com.relationdetector.semantic.event;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import com.relationdetector.semantic.model.PhysicalEndpointRef;

/**
 * CN: 根据已提取 event kind、source object 和 typed endpoint tables 生成确定性中文 readable/action hints；只改善标签，不改变 candidate identity、事实或 evidence。
 * EN: Produces deterministic readable and action hints from extracted event kind, source object, and typed endpoint tables. It improves labels only and never changes candidate identity, facts, or evidence.
 */
public final class EventReadableNameSuggester {

    public EventNameSuggestion suggest(
            String eventKind,
            String sourceObject,
            Set<String> inputEndpoints,
            Set<String> outputEndpoints
    ) {
        String outputTable = primaryTable(outputEndpoints);
        String inputTables = tableList(inputEndpoints);
        String outputLabel = tableLabel(outputTable);
        String readable = outputTable.isBlank() ? readableSource(sourceObject) : "写入" + outputLabel;
        String action = "从 " + (inputTables.isBlank() ? "上游数据" : inputTables)
                + " 写入/更新 " + (outputTable.isBlank() ? "目标数据" : outputTable);
        String basis = outputTable.isBlank() ? "EVENT_KIND_AND_SOURCE_OBJECT" : "EVENT_KIND_AND_OUTPUT_TABLE";
        return new EventNameSuggestion(readable, action, basis);
    }

    private String primaryTable(Set<String> endpoints) {
        for (String endpoint : endpoints == null ? Set.<String>of() : endpoints) {
            String table = tableOf(endpoint);
            if (!table.isBlank()) {
                return table;
            }
        }
        return "";
    }

    private String tableList(Set<String> endpoints) {
        Set<String> tables = new LinkedHashSet<>();
        for (String endpoint : endpoints == null ? Set.<String>of() : endpoints) {
            String table = tableOf(endpoint);
            if (!table.isBlank()) {
                tables.add(table);
            }
        }
        return String.join(", ", tables);
    }

    private String tableOf(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "";
        }
        return PhysicalEndpointRef.column(endpoint).table();
    }

    private String tableLabel(String table) {
        if (table == null || table.isBlank()) {
            return "数据";
        }
        String lower = table.toLowerCase(Locale.ROOT);
        String bare = PhysicalEndpointRef.table(lower).bareTableName();
        return switch (bare) {
            case "sales_fact" -> "销售事实表";
            case "customer_dim", "customers" -> "客户数据";
            case "region_dim", "regions" -> "地区维度";
            case "fiscal_calendar" -> "财务日历";
            case "cashier_journals" -> "收银流水";
            case "reconciliations", "reconciliation_items" -> "对账数据";
            case "inventory", "inventory_transactions" -> "库存数据";
            case "payments" -> "支付数据";
            default -> table;
        };
    }

    private String readableSource(String sourceObject) {
        if (sourceObject == null || sourceObject.isBlank()) {
            return "处理数据";
        }
        return "执行 " + sourceObject;
    }

    public record EventNameSuggestion(String readableNameHint, String businessActionHint, String eventNameBasis) {
        public EventNameSuggestion {
            readableNameHint = readableNameHint == null ? "" : readableNameHint;
            businessActionHint = businessActionHint == null ? "" : businessActionHint;
            eventNameBasis = eventNameBasis == null ? "" : eventNameBasis;
        }
    }
}
