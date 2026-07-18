package com.relationdetector.core.tokenevent;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.DdlEvent;
import com.relationdetector.contracts.parse.ExpressionSource;
import com.relationdetector.contracts.parse.ExpressionTrace;
import com.relationdetector.contracts.parse.PredicateEvent;
import com.relationdetector.contracts.parse.PredicateGuard;
import com.relationdetector.contracts.parse.ProjectionEvent;
import com.relationdetector.contracts.parse.RowsetEvent;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.parse.WriteEvent;

/**
 * CN: 为 token-event visitors 统一构造 typed events 与 absolute provenance，并在 per-parse guard stack 中保存条件上下文。
 * EN: Constructs typed events and absolute provenance for token-event visitors while retaining predicate guards in per-parse state.
 */
public final class TokenEventEventEmitter {
    private final SqlStatementRecord statement;
    private final Predicate<StructuredParseEventType> typeFilter;
    private final Supplier<String> statementScope;
    private final ArrayDeque<PredicateGuard> predicateGuards = new ArrayDeque<>();

    public TokenEventEventEmitter(SqlStatementRecord statement) {
        this(statement, ignored -> true, () -> "");
    }

    public TokenEventEventEmitter(
            SqlStatementRecord statement,
            Predicate<StructuredParseEventType> typeFilter
    ) {
        this(statement, typeFilter, () -> "");
    }

    public TokenEventEventEmitter(
            SqlStatementRecord statement,
            Predicate<StructuredParseEventType> typeFilter,
            Supplier<String> statementScope
    ) {
        this.statement = statement;
        this.typeFilter = typeFilter == null ? ignored -> true : typeFilter;
        this.statementScope = statementScope == null ? () -> "" : statementScope;
    }

    public void addRowset(List<StructuredSqlEvent> events, ParserRuleContext ctx,
            StructuredParseEventType type, String keyword, String qualifiedTable,
            String table, String alias, String name, String targetTable, String reason) {
        add(events, new RowsetEvent(type, provenance(ctx), keyword, qualifiedTable,
                table, alias, name, targetTable, reason));
    }

    public void addPredicate(List<StructuredSqlEvent> events, ParserRuleContext ctx,
            StructuredParseEventType type, String leftAlias, String leftColumn,
            String rightAlias, String rightColumn, String joinKind) {
        add(events, new PredicateEvent(type, provenance(ctx),
                new ExpressionSource(leftAlias, leftColumn),
                new ExpressionSource(rightAlias, rightColumn),
                List.of(), List.of(), "", joinKind, List.of(), false,
                currentPredicateGuards()));
    }

    public void addJoinUsing(List<StructuredSqlEvent> events, ParserRuleContext ctx,
            String leftAlias, String rightAlias, List<String> columns) {
        add(events, new PredicateEvent(StructuredParseEventType.JOIN_USING_COLUMNS,
                provenance(ctx), new ExpressionSource(leftAlias, ""),
                new ExpressionSource(rightAlias, ""), List.of(), List.of(), "", "",
                columns, false));
    }

    public void addInSubquery(List<StructuredSqlEvent> events, ParserRuleContext ctx,
            StructuredParseEventType type, List<String> outerAliases, List<String> outerColumns,
            List<String> innerAliases, List<String> innerColumns, String innerTable) {
        add(events, new PredicateEvent(type, provenance(ctx),
                sourceAt(outerAliases, outerColumns, 0), sourceAt(innerAliases, innerColumns, 0),
                sources(outerAliases, outerColumns), sources(innerAliases, innerColumns),
                innerTable, "", List.of(), true, currentPredicateGuards()));
    }

    public void withPredicateGuard(PredicateGuard guard, Runnable visitor) {
        if (guard == null || guard.discriminator().column().isBlank()
                || guard.operator().isBlank()) {
            visitor.run();
            return;
        }
        predicateGuards.push(guard);
        try {
            visitor.run();
        } finally {
            predicateGuards.pop();
        }
    }

    public void addProjection(List<StructuredSqlEvent> events, ParserRuleContext ctx,
            StructuredParseEventType type, String outputAlias, String outputColumn,
            List<String> sourceAliases, List<String> sourceColumns,
            LineageTransformType transformType, LineageFlowKind flowKind) {
        add(events, new ProjectionEvent(type, provenance(ctx), outputAlias, outputColumn,
                ExpressionTrace.of(sourceAliases, sourceColumns, flowKind, transformType)));
    }

    public void addWrite(List<StructuredSqlEvent> events, ParserRuleContext ctx,
            StructuredParseEventType type, String table, String qualifiedTable, String alias,
            String targetAlias, String targetTable, String targetColumn, String mappingKind,
            List<String> sourceAliases, List<String> sourceColumns,
            LineageTransformType transformType, LineageFlowKind flowKind) {
        add(events, new WriteEvent(type, provenance(ctx), table, qualifiedTable, alias,
                targetAlias, targetTable, targetColumn, mappingKind,
                ExpressionTrace.of(sourceAliases, sourceColumns, flowKind, transformType)));
    }

    public void addForeignKeyEvents(
            List<StructuredSqlEvent> events,
            ParserRuleContext ctx,
            String sourceTable,
            List<String> sourceColumns,
            String targetTable,
            List<String> targetColumns
    ) {
        int count = Math.min(sourceColumns.size(), targetColumns.size());
        for (int index = 0; index < count; index++) {
            add(events, new DdlEvent(StructuredParseEventType.DDL_FOREIGN_KEY,
                    provenance(ctx), sourceTable, sourceColumns.get(index), targetTable,
                    targetColumns.get(index), "", "", "", "", index + 1, count));
        }
    }

    public void addIndexEvent(
            List<StructuredSqlEvent> events,
            ParserRuleContext ctx,
            String table,
            String column,
            String role,
            String kind
    ) {
        addIndexEvents(events, ctx, table, List.of(column), role, kind);
    }

    public void addIndexEvents(
            List<StructuredSqlEvent> events,
            ParserRuleContext ctx,
            String table,
            List<String> columns,
            String role,
            String kind
    ) {
        if (table == null || table.isBlank() || columns == null) {
            return;
        }
        List<String> safeColumns = columns.stream()
                .filter(column -> column != null && !column.isBlank())
                .toList();
        int count = safeColumns.size();
        for (int index = 0; index < count; index++) {
            add(events, new DdlEvent(StructuredParseEventType.DDL_INDEX, provenance(ctx),
                    "", "", "", "", table, safeColumns.get(index), role, kind, index + 1, count));
        }
    }

    public void addDdlColumnEvent(List<StructuredSqlEvent> events, ParserRuleContext ctx,
            String table, String column) {
        if (table == null || table.isBlank() || column == null || column.isBlank()) {
            return;
        }
        add(events, new DdlEvent(StructuredParseEventType.DDL_COLUMN, provenance(ctx),
                "", "", "", "", table, column, "", "", 1, 1));
    }

    public long line(ParserRuleContext ctx) {
        if (ctx == null || ctx.getStart() == null) {
            return statement.startLine();
        }
        return statement.startLine() + Math.max(0, ctx.getStart().getLine() - 1);
    }

    private SourceProvenance provenance(ParserRuleContext ctx) {
        return SourceProvenance.tokenEvent(statement, line(ctx), statementScope.get());
    }

    private void add(List<StructuredSqlEvent> events, StructuredSqlEvent event) {
        if (typeFilter.test(event.type())) {
            events.add(event);
        }
    }

    private List<ExpressionSource> sources(List<String> aliases, List<String> columns) {
        List<ExpressionSource> result = new ArrayList<>();
        int count = Math.min(aliases == null ? 0 : aliases.size(), columns == null ? 0 : columns.size());
        for (int index = 0; index < count; index++) {
            result.add(new ExpressionSource(aliases.get(index), columns.get(index)));
        }
        return List.copyOf(result);
    }

    private ExpressionSource sourceAt(List<String> aliases, List<String> columns, int index) {
        if (aliases == null || columns == null || index >= aliases.size() || index >= columns.size()) {
            return ExpressionSource.EMPTY;
        }
        return new ExpressionSource(aliases.get(index), columns.get(index));
    }

    private List<PredicateGuard> currentPredicateGuards() {
        if (predicateGuards.isEmpty()) {
            return List.of();
        }
        List<PredicateGuard> result = new ArrayList<>(predicateGuards);
        java.util.Collections.reverse(result);
        return List.copyOf(result);
    }
}
