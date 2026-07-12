package com.relationdetector.core.fullgrammer;


import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.StructuredParseEventType;

final class WriteMappingSink {
    private final SourceLocationSupport source;
    private final RowsetScopeSink rowsets;
    private final FullGrammerEventRecorder recorder;
    private final FullGrammerExpressionAnalyzer expressionAnalyzer;
    private final ProjectionEventSink projectionEvents;

    WriteMappingSink(
            SourceLocationSupport source,
            RowsetScopeSink rowsets,
            FullGrammerEventRecorder recorder,
            FullGrammerExpressionAnalyzer expressionAnalyzer,
            ProjectionEventSink projectionEvents
    ) {
        this.source = source;
        this.rowsets = rowsets;
        this.recorder = recorder;
        this.expressionAnalyzer = expressionAnalyzer;
        this.projectionEvents = projectionEvents;
    }

    void updateAssignment(ParserRuleContext ctx, String targetAlias, String targetTable, String targetColumn, ParseTree expression) {
        addWriteMapping(ctx, StructuredParseEventType.UPDATE_ASSIGNMENT, "UPDATE_SET",
                targetAlias, targetTable, targetColumn, expression);
    }

    void mergeUpdate(ParserRuleContext ctx, String targetAlias, String targetTable, String targetColumn, ParseTree expression) {
        addWriteMapping(ctx, StructuredParseEventType.MERGE_WRITE_MAPPING, "MERGE_UPDATE",
                targetAlias, targetTable, targetColumn, expression);
    }

    void mergeInsert(ParserRuleContext ctx, String targetAlias, String targetTable, String targetColumn, ParseTree expression) {
        addWriteMapping(ctx, StructuredParseEventType.MERGE_WRITE_MAPPING, "MERGE_INSERT",
                targetAlias, targetTable, targetColumn, expression);
    }

    void insertSelect(ParserRuleContext ctx, String targetAlias, String targetTable, String targetColumn, ParseTree expression) {
        addWriteMapping(ctx, StructuredParseEventType.INSERT_SELECT_MAPPING, "INSERT_SELECT",
                targetAlias, targetTable, targetColumn, expression);
    }

    void insertValues(ParserRuleContext ctx, String targetAlias, String targetTable, String targetColumn,
            ParseTree expression) {
        addWriteMapping(ctx, StructuredParseEventType.INSERT_SELECT_MAPPING, "INSERT_VALUES",
                targetAlias, targetTable, targetColumn, expression);
    }

    private void addWriteMapping(
            ParserRuleContext ctx,
            StructuredParseEventType type,
            String mappingKind,
            String targetAlias,
            String targetTable,
            String targetColumn,
            ParseTree expression
    ) {
        String cleanColumn = source.clean(targetColumn);
        if (cleanColumn.isBlank()) {
            return;
        }
        if (expressionAnalyzer.prefersDialectWriteAnalyses(expression)) {
            for (FullGrammerExpressionAnalysis writeAnalysis :
                    expressionAnalyzer.writeAnalyses(expression, rowsets.defaultProjectionQualifier())) {
                addMappingEvent(ctx, type, mappingKind, targetAlias, targetTable, cleanColumn, writeAnalysis);
                projectionEvents.expressionSource(ctx, writeAnalysis);
            }
            return;
        }
        FullGrammerExpressionAnalysis analysis = expressionAnalyzer.analyze(expression);
        var caseAnalyses = expressionAnalyzer.caseWriteAnalyses(expression, rowsets.defaultProjectionQualifier());
        if (!caseAnalyses.isEmpty()) {
            for (FullGrammerExpressionAnalysis caseAnalysis : caseAnalyses) {
                addMappingEvent(ctx, type, mappingKind, targetAlias, targetTable, cleanColumn, caseAnalysis);
                projectionEvents.expressionSource(ctx, caseAnalysis);
            }
            return;
        }
        if (isNestedCaseWhen(expression, analysis)) {
            addNestedCaseWhenMappings(ctx, type, mappingKind, targetAlias, targetTable, cleanColumn, expression);
        } else {
            for (FullGrammerExpressionAnalysis writeAnalysis :
                    expressionAnalyzer.writeAnalyses(expression, rowsets.defaultProjectionQualifier())) {
                addMappingEvent(ctx, type, mappingKind, targetAlias, targetTable, cleanColumn, writeAnalysis);
                projectionEvents.expressionSource(ctx, writeAnalysis);
            }
            return;
        }
        projectionEvents.expressionSource(ctx, analysis);
    }

    private void addMappingEvent(
            ParserRuleContext ctx,
            StructuredParseEventType type,
            String mappingKind,
            String targetAlias,
            String targetTable,
            String targetColumn,
            FullGrammerExpressionAnalysis analysis
    ) {
        recorder.write(ctx, type, mappingKind, source.clean(targetAlias),
                source.clean(targetTable), targetColumn, analysis);
    }

    private boolean isNestedCaseWhen(ParseTree expression, FullGrammerExpressionAnalysis analysis) {
        return "CASE_WHEN".equals(analysis.transformType())
                && !expressionAnalyzer.isTopLevelCaseExpression(expression);
    }

    private void addNestedCaseWhenMappings(
            ParserRuleContext ctx,
            StructuredParseEventType type,
            String mappingKind,
            String targetAlias,
            String targetTable,
            String targetColumn,
            ParseTree expression
    ) {
        for (FullGrammerExpressionAnalysis analysis :
                expressionAnalyzer.caseExpressionAnalyses(expression, rowsets.defaultProjectionQualifier())) {
            if (!analysis.hasSources()) {
                continue;
            }
            recorder.write(ctx, type, mappingKind, source.clean(targetAlias),
                    source.clean(targetTable), targetColumn, analysis);
        }
    }
}
