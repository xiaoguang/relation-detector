package com.relationdetector.core.fullgrammer;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.StructuredParseEventType;

final class ProjectionEventSink {
    private final SourceLocationSupport source;
    private final RowsetScopeSink rowsets;
    private final FullGrammerEventRecorder recorder;
    private final FullGrammerExpressionAnalyzer expressionAnalyzer;

    ProjectionEventSink(
            SourceLocationSupport source,
            RowsetScopeSink rowsets,
            FullGrammerEventRecorder recorder,
            FullGrammerExpressionAnalyzer expressionAnalyzer
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
        for (FullGrammerExpressionAnalysis analysis : projectionAnalyses(expression)) {
            addProjection(ctx, cleanOutputAlias, cleanOutputColumn, analysis);
            expressionSource(ctx, analysis);
        }
    }

    private List<FullGrammerExpressionAnalysis> projectionAnalyses(ParseTree expression) {
        String qualifier = rowsets.defaultProjectionQualifier();
        if (expressionAnalyzer.prefersDialectWriteAnalyses(expression)) {
            return expressionAnalyzer.writeAnalyses(expression, qualifier);
        }
        List<FullGrammerExpressionAnalysis> caseAnalyses = expressionAnalyzer.caseWriteAnalyses(expression, qualifier);
        if (!caseAnalyses.isEmpty()) {
            return caseAnalyses;
        }
        FullGrammerExpressionAnalysis analysis = expressionAnalyzer.analyze(expression, qualifier);
        if ("CASE_WHEN".equals(analysis.transformType())
                && !expressionAnalyzer.isTopLevelCaseExpression(expression)) {
            List<FullGrammerExpressionAnalysis> nested =
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
            FullGrammerExpressionAnalysis analysis
    ) {
        recorder.projection(ctx, StructuredParseEventType.PROJECTION_ITEM,
                outputAlias, outputColumn, analysis);
    }

    void expressionSource(ParserRuleContext ctx, FullGrammerExpressionAnalysis analysis) {
        if (!analysis.hasSources()) {
            return;
        }
        recorder.projection(ctx, StructuredParseEventType.EXPRESSION_SOURCE, "", "", analysis);
    }
}
