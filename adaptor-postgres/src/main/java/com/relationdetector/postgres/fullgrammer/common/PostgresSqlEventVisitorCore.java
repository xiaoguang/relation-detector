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
    private final SqlStatementRecord statement;
    private final FullGrammerTypedSqlEventSink sink;
    private final List<String> rowsetAliases = new ArrayList<>();
    private String mergeTarget = "";
    private String mergeSource = "";

    public PostgresSqlEventVisitorCore(SqlStatementRecord statement) {
        this.statement = statement;
        this.sink = new FullGrammerTypedSqlEventSink(statement, new PostgresExpressionAnalyzer());
    }

    public FullGrammerTypedSqlEventSink sink() {
        return sink;
    }

    public List<StructuredSqlEvent> mergedEvents() {
        return FullGrammerEventMerger.merge(
                sink.events(),
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

    public void routineBody(ParserRuleContext ctx, String quotedBody, RoutineBodyEventExtractor extractor) {
        String body = unquoteRoutineBody(quotedBody);
        if (body.isBlank()) {
            return;
        }
        long line = ctx == null || ctx.getStart() == null
                ? statement.startLine()
                : statement.startLine() + Math.max(0, ctx.getStart().getLine() - 1);
        SqlStatementRecord nested = new SqlStatementRecord(body,
                statement.sourceType(),
                statement.sourceName(),
                line,
                line + body.lines().count(),
                statement.attributes());
        sink.events().addAll(extractor.extract(nested));
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

    private String unquoteRoutineBody(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.trim();
        if (text.startsWith("$")) {
            int endTagStart = text.indexOf('$', 1);
            if (endTagStart < 0) {
                return "";
            }
            String tag = text.substring(0, endTagStart + 1);
            if (!text.endsWith(tag) || text.length() < tag.length() * 2) {
                return "";
            }
            return text.substring(tag.length(), text.length() - tag.length());
        }
        if (text.length() >= 2 && text.startsWith("'") && text.endsWith("'")) {
            return text.substring(1, text.length() - 1).replace("''", "'");
        }
        return "";
    }

    @FunctionalInterface
    public interface RoutineBodyEventExtractor {
        List<StructuredSqlEvent> extract(SqlStatementRecord nestedStatement);
    }
}
