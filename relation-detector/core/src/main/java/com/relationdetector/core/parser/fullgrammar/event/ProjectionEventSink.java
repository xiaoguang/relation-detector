package com.relationdetector.core.parser.fullgrammar.event;

import com.relationdetector.core.parser.fullgrammar.event.RowsetScopeSink;

import com.relationdetector.core.parser.fullgrammar.event.FullGrammarEventRecorder;

import com.relationdetector.core.parser.fullgrammar.expression.FullGrammarExpressionAnalyzer;

import com.relationdetector.core.parser.fullgrammar.expression.FullGrammarExpressionAnalysis;

import com.relationdetector.core.parser.fullgrammar.tree.SourceLocationSupport;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.parse.ExpressionTrace;

/**
 * CN: 将typed select/projection context转换为projection与expression-source事件；输入是已绑定rowset和expression
 * analysis，输出交给event recorder。上游是full-grammar facade，下游是lineage extraction；本类不解析statement、
 * 不推断未typed的列，也不直接修改scan结果。
 *
 * <p>EN: Converts typed select/projection contexts into projection and expression-source events using bound rowsets
 * and expression analysis. The full-grammar facade is upstream and lineage extraction is downstream. It does not
 * parse statements, infer untyped columns, or mutate scan results directly.
 */
public final class ProjectionEventSink {
    private final SourceLocationSupport source;
    private final RowsetScopeSink rowsets;
    private final FullGrammarEventRecorder recorder;
    private final FullGrammarExpressionAnalyzer expressionAnalyzer;

    ProjectionEventSink(
            SourceLocationSupport source,
            RowsetScopeSink rowsets,
            FullGrammarEventRecorder recorder,
            FullGrammarExpressionAnalyzer expressionAnalyzer
    ) {
        this.source = source;
        this.rowsets = rowsets;
        this.recorder = recorder;
        this.expressionAnalyzer = expressionAnalyzer;
    }

    void projection(ParserRuleContext ctx, String outputAlias, String outputColumn, ParseTree expression) {
        String cleanOutputAlias = source.clean(outputAlias);
        String cleanOutputColumn = source.clean(outputColumn);
        if (cleanOutputAlias.isBlank() || cleanOutputColumn.isBlank()) {
            return;
        }
        List<FullGrammarExpressionAnalysis> analyses = projectionAnalyses(expression);
        if (analyses.isEmpty()) {
            recorder.projection(ctx, StructuredParseEventType.PROJECTION_ITEM,
                    cleanOutputAlias, cleanOutputColumn, ExpressionTrace.empty());
            return;
        }
        for (FullGrammarExpressionAnalysis analysis : analyses) {
            addProjection(ctx, cleanOutputAlias, cleanOutputColumn, analysis);
            expressionSource(ctx, analysis);
        }
    }

    void wildcardProjection(ParserRuleContext ctx, String outputAlias) {
        String cleanOutputAlias = source.clean(outputAlias);
        String qualifier = source.clean(rowsets.defaultProjectionQualifier());
        if (cleanOutputAlias.isBlank() || qualifier.isBlank()) {
            return;
        }
        recorder.projection(ctx, StructuredParseEventType.PROJECTION_ITEM,
                cleanOutputAlias, "*", ExpressionTrace.of(
                        List.of(qualifier), List.of("*"), LineageFlowKind.VALUE,
                        LineageTransformType.DIRECT));
    }

    private List<FullGrammarExpressionAnalysis> projectionAnalyses(ParseTree expression) {
        String qualifier = rowsets.defaultProjectionQualifier();
        if (expressionAnalyzer.prefersDialectWriteAnalyses(expression)) {
            return expressionAnalyzer.writeAnalyses(expression, qualifier);
        }
        List<FullGrammarExpressionAnalysis> caseAnalyses = expressionAnalyzer.caseWriteAnalyses(expression, qualifier);
        if (!caseAnalyses.isEmpty()) {
            return caseAnalyses;
        }
        FullGrammarExpressionAnalysis analysis = expressionAnalyzer.analyze(expression, qualifier);
        if ("CASE_WHEN".equals(analysis.transformType())
                && !expressionAnalyzer.isTopLevelCaseExpression(expression)) {
            List<FullGrammarExpressionAnalysis> nested =
                    expressionAnalyzer.caseExpressionAnalyses(expression, qualifier);
            if (!nested.isEmpty()) {
                return nested;
            }
        }
        return expressionAnalyzer.writeAnalyses(expression, qualifier);
    }

    private void addProjection(
            ParserRuleContext ctx,
            String outputAlias,
            String outputColumn,
            FullGrammarExpressionAnalysis analysis
    ) {
        recorder.projection(ctx, StructuredParseEventType.PROJECTION_ITEM,
                outputAlias, outputColumn, analysis);
    }

    void expressionSource(ParserRuleContext ctx, FullGrammarExpressionAnalysis analysis) {
        if (!analysis.hasSources()) {
            return;
        }
        recorder.projection(ctx, StructuredParseEventType.EXPRESSION_SOURCE, "", "", analysis);
    }
}
