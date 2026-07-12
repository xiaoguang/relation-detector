package com.relationdetector.core.fullgrammar;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.DdlEvent;
import com.relationdetector.contracts.parse.ExpressionSource;
import com.relationdetector.contracts.parse.ExpressionTrace;
import com.relationdetector.contracts.parse.PredicateEvent;
import com.relationdetector.contracts.parse.ProjectionEvent;
import com.relationdetector.contracts.parse.RowsetEvent;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.parse.WriteEvent;

final class FullGrammarEventRecorder {
    private final SourceLocationSupport source;
    private final List<StructuredSqlEvent> events = new ArrayList<>();
    private final Set<String> eventKeys = new LinkedHashSet<>();

    FullGrammarEventRecorder(SqlStatementRecord statement, SourceLocationSupport source) {
        this.source = source;
    }

    List<StructuredSqlEvent> events() {
        return events;
    }

    void rowset(ParserRuleContext ctx, StructuredParseEventType type, String keyword,
            String qualifiedTable, String table, String alias, String name,
            String targetTable, String reason) {
        add(new RowsetEvent(type, source.provenance(ctx), keyword, qualifiedTable,
                table, alias, name, targetTable, reason));
    }

    void predicate(ParserRuleContext ctx, StructuredParseEventType type,
            ExpressionSource left, ExpressionSource right, List<ExpressionSource> outer,
            List<ExpressionSource> inner, String innerTable, String joinKind,
            List<String> usingColumns, boolean verified) {
        add(new PredicateEvent(type, source.provenance(ctx), left, right, outer, inner,
                innerTable, joinKind, usingColumns, verified));
    }

    void projection(ParserRuleContext ctx, StructuredParseEventType type,
            String outputAlias, String outputColumn, FullGrammarExpressionAnalysis analysis) {
        add(new ProjectionEvent(type, source.provenance(ctx), outputAlias, outputColumn, trace(analysis)));
    }

    void projection(ParserRuleContext ctx, StructuredParseEventType type,
            String outputAlias, String outputColumn, ExpressionTrace trace) {
        add(new ProjectionEvent(type, source.provenance(ctx), outputAlias, outputColumn, trace));
    }

    void writeTarget(ParserRuleContext ctx, String table, String qualifiedTable, String alias) {
        add(new WriteEvent(StructuredParseEventType.WRITE_TARGET, source.provenance(ctx),
                table, qualifiedTable, alias, "", "", "", "", ExpressionTrace.empty()));
    }

    void write(ParserRuleContext ctx, StructuredParseEventType type, String mappingKind,
            String targetAlias, String targetTable, String targetColumn,
            FullGrammarExpressionAnalysis analysis) {
        add(new WriteEvent(type, source.provenance(ctx), "", "", "", targetAlias,
                targetTable, targetColumn, mappingKind, trace(analysis)));
    }

    void ddl(DdlEvent event) {
        add(event);
    }

    private ExpressionTrace trace(FullGrammarExpressionAnalysis analysis) {
        return ExpressionTrace.of(analysis.sourceAliases(), analysis.sourceColumns(),
                enumValue(LineageFlowKind.class, analysis.flowKind(), LineageFlowKind.VALUE),
                enumValue(LineageTransformType.class, analysis.transformType(),
                        LineageTransformType.UNKNOWN_EXPRESSION));
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private void add(StructuredSqlEvent event) {
        String key = event.semanticKey();
        if (eventKeys.add(key)) {
            events.add(event);
        }
    }
}
