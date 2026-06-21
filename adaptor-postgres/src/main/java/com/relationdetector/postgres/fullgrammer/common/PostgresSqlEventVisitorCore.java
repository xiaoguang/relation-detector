package com.relationdetector.postgres.fullgrammer.common;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.fullgrammer.FullGrammerEventMerger;
import com.relationdetector.core.fullgrammer.FullGrammerNativeEventTypes;
import com.relationdetector.core.fullgrammer.FullGrammerTypedSqlEventSink;

/**
 * Shared state and helpers for PostgreSQL SQL typed visitors.
 *
 * <p>CN: 只保存 visitor 状态和事件 sink 操作，不判断 PostgreSQL SQL 结构。结构判断
 * 必须继续由 v16/v17/v18 generated context override 完成。
 *
 * <p>EN: Shared state and helpers for PostgreSQL SQL typed visitors. It keeps
 * visitor state and sink helpers only; SQL structure decisions remain in
 * v16/v17/v18 generated context overrides.
 */
public final class PostgresSqlEventVisitorCore {
    private final FullGrammerTypedSqlEventSink sink;
    private final List<String> rowsetAliases = new ArrayList<>();
    private String mergeTarget = "";
    private String mergeSource = "";

    public PostgresSqlEventVisitorCore(SqlStatementRecord statement) {
        this.sink = new FullGrammerTypedSqlEventSink(statement, new PostgresExpressionAnalyzer());
    }

    public FullGrammerTypedSqlEventSink sink() {
        return sink;
    }

    public List<StructuredSqlEvent> mergedEvents() {
        return FullGrammerEventMerger.merge(
                sink.events(),
                List.of(),
                FullGrammerNativeEventTypes.POSTGRES_NATIVE_EVENTS);
    }

    public String firstAlias(ParseTree tree) {
        return firstIdentifier(tree);
    }

    public String firstIdentifier(ParseTree tree) {
        List<String> identifiers = sink.identifiers(tree);
        return identifiers.isEmpty() ? "" : identifiers.get(0);
    }

    public String lastIdentifier(ParseTree tree) {
        List<String> identifiers = sink.identifiers(tree);
        return identifiers.isEmpty() ? "" : identifiers.get(identifiers.size() - 1);
    }

    public String projectedColumnName(ParseTree expression) {
        return lastIdentifier(expression);
    }

    public void rememberRowset(String aliasOrTable) {
        String clean = sink.clean(aliasOrTable);
        if (!clean.isBlank()) {
            rowsetAliases.add(clean);
        }
    }

    public String lastRowsetAlias() {
        return rowsetAliases.isEmpty() ? "" : rowsetAliases.get(rowsetAliases.size() - 1);
    }

    public String mergeTarget() {
        return mergeTarget;
    }

    public void mergeTarget(String mergeTarget) {
        this.mergeTarget = mergeTarget;
    }

    public String mergeSource() {
        return mergeSource;
    }

    public void mergeSource(String mergeSource) {
        this.mergeSource = mergeSource;
    }

    public List<ParseTree> expressionChildren(ParseTree tree) {
        List<ParseTree> result = new ArrayList<>();
        collectExpressionChildren(tree, result);
        return result;
    }

    public boolean isExpressionContext(ParserRuleContext ctx) {
        String name = ctx.getClass().getSimpleName();
        return name.contains("A_expr")
                || name.contains("B_expr")
                || name.contains("C_expr")
                || name.contains("Func_expr")
                || name.equals("ColumnrefContext")
                || name.equals("Subquery_OpContext");
    }

    private void collectExpressionChildren(ParseTree tree, List<ParseTree> result) {
        if (tree == null) {
            return;
        }
        String name = tree.getClass().getSimpleName();
        if (name.equals("A_exprContext") || name.endsWith("A_exprContext")) {
            result.add(tree);
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectExpressionChildren(tree.getChild(index), result);
        }
    }
}
