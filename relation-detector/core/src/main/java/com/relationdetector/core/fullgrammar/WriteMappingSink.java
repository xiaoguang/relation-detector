package com.relationdetector.core.fullgrammar;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.StructuredParseEventType;

final class WriteMappingSink {
    private final SourceLocationSupport source;
    private final RowsetScopeSink rowsets;
    private final FullGrammarEventRecorder recorder;
    private final FullGrammarExpressionAnalyzer expressionAnalyzer;
    private final ProjectionEventSink projectionEvents;

    WriteMappingSink(
            SourceLocationSupport source,
            RowsetScopeSink rowsets,
            FullGrammarEventRecorder recorder,
            FullGrammarExpressionAnalyzer expressionAnalyzer,
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

    void updateControl(
            ParserRuleContext ctx,
            String targetAlias,
            String targetTable,
            String targetColumn,
            ParseTree expression
    ) {
        String defaultQualifier = targetAlias == null || targetAlias.isBlank()
                ? targetTable
                : targetAlias;
        FullGrammarExpressionAnalysis analysis = expressionAnalyzer
                .analyze(expression, defaultQualifier)
                .withTransform("DIRECT", "CONTROL");
        addMappingEvent(ctx, StructuredParseEventType.UPDATE_ASSIGNMENT, "UPDATE_LOCATOR",
                targetAlias, targetTable, source.clean(targetColumn), analysis);
    }

    void updateControl(
            ParserRuleContext ctx,
            String targetAlias,
            String targetTable,
            String targetColumn,
            List<? extends ParseTree> expressions
    ) {
        String defaultQualifier = targetAlias == null || targetAlias.isBlank()
                ? targetTable
                : targetAlias;
        FullGrammarExpressionAnalysis analysis = controlAnalysis(expressions, defaultQualifier, "DIRECT");
        addMappingEvent(ctx, StructuredParseEventType.UPDATE_ASSIGNMENT, "UPDATE_LOCATOR",
                targetAlias, targetTable, source.clean(targetColumn), analysis);
    }

    void mergeUpdate(ParserRuleContext ctx, String targetAlias, String targetTable, String targetColumn, ParseTree expression) {
        addWriteMapping(ctx, StructuredParseEventType.MERGE_WRITE_MAPPING, "MERGE_UPDATE",
                targetAlias, targetTable, targetColumn, expression);
    }

    void mergeControl(
            ParserRuleContext ctx,
            String targetAlias,
            String targetTable,
            String targetColumn,
            ParseTree expression
    ) {
        String defaultQualifier = targetAlias == null || targetAlias.isBlank()
                ? targetTable
                : targetAlias;
        FullGrammarExpressionAnalysis analysis = expressionAnalyzer
                .analyze(expression, defaultQualifier)
                .withTransform("DIRECT", "CONTROL");
        addMappingEvent(ctx, StructuredParseEventType.MERGE_WRITE_MAPPING, "MERGE_ON",
                targetAlias, targetTable, source.clean(targetColumn), analysis);
    }

    void mergeInsert(ParserRuleContext ctx, String targetAlias, String targetTable, String targetColumn, ParseTree expression) {
        addWriteMapping(ctx, StructuredParseEventType.MERGE_WRITE_MAPPING, "MERGE_INSERT",
                targetAlias, targetTable, targetColumn, expression);
    }

    void insertSelect(ParserRuleContext ctx, String targetAlias, String targetTable, String targetColumn, ParseTree expression) {
        addWriteMapping(ctx, StructuredParseEventType.INSERT_SELECT_MAPPING, "INSERT_SELECT",
                targetAlias, targetTable, targetColumn, expression);
    }

    void insertSelectControl(
            ParserRuleContext ctx,
            String targetAlias,
            String targetTable,
            String targetColumn,
            ParseTree expression,
            String transformType
    ) {
        FullGrammarExpressionAnalysis analysis = expressionAnalyzer
                .analyze(expression, rowsets.defaultProjectionQualifier())
                .withTransform(transformType, "CONTROL");
        addMappingEvent(ctx, StructuredParseEventType.INSERT_SELECT_MAPPING, "INSERT_CONTROL",
                targetAlias, targetTable, source.clean(targetColumn), analysis);
    }

    void insertSelectControl(
            ParserRuleContext ctx,
            String targetAlias,
            String targetTable,
            String targetColumn,
            List<? extends ParseTree> expressions,
            String transformType
    ) {
        FullGrammarExpressionAnalysis control = controlAnalysis(
                expressions, rowsets.defaultProjectionQualifier(), transformType);
        addMappingEvent(ctx, StructuredParseEventType.INSERT_SELECT_MAPPING, "INSERT_CONTROL",
                targetAlias, targetTable, source.clean(targetColumn), control);
    }

    private FullGrammarExpressionAnalysis controlAnalysis(
            List<? extends ParseTree> expressions,
            String defaultQualifier,
            String transformType
    ) {
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ParseTree expression : expressions) {
            FullGrammarExpressionAnalysis analysis = expressionAnalyzer
                    .analyze(expression, defaultQualifier);
            int count = Math.min(analysis.sourceAliases().size(), analysis.sourceColumns().size());
            for (int index = 0; index < count; index++) {
                String key = analysis.sourceAliases().get(index) + "\u0000" + analysis.sourceColumns().get(index);
                if (seen.add(key)) {
                    aliases.add(analysis.sourceAliases().get(index));
                    columns.add(analysis.sourceColumns().get(index));
                }
            }
        }
        return new FullGrammarExpressionAnalysis(
                aliases, columns, transformType, "CONTROL");
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
            for (FullGrammarExpressionAnalysis writeAnalysis :
                    expressionAnalyzer.writeAnalyses(expression, rowsets.defaultProjectionQualifier())) {
                addMappingEvent(ctx, type, mappingKind, targetAlias, targetTable, cleanColumn, writeAnalysis);
                projectionEvents.expressionSource(ctx, writeAnalysis);
            }
            return;
        }
        FullGrammarExpressionAnalysis analysis = expressionAnalyzer.analyze(expression);
        var caseAnalyses = expressionAnalyzer.caseWriteAnalyses(expression, rowsets.defaultProjectionQualifier());
        if (!caseAnalyses.isEmpty()) {
            for (FullGrammarExpressionAnalysis caseAnalysis : caseAnalyses) {
                addMappingEvent(ctx, type, mappingKind, targetAlias, targetTable, cleanColumn, caseAnalysis);
                projectionEvents.expressionSource(ctx, caseAnalysis);
            }
            return;
        }
        if (isNestedCaseWhen(expression, analysis)) {
            addNestedCaseWhenMappings(ctx, type, mappingKind, targetAlias, targetTable, cleanColumn, expression);
        } else {
            for (FullGrammarExpressionAnalysis writeAnalysis :
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
            FullGrammarExpressionAnalysis analysis
    ) {
        recorder.write(ctx, type, mappingKind, source.clean(targetAlias),
                source.clean(targetTable), targetColumn, analysis);
    }

    private boolean isNestedCaseWhen(ParseTree expression, FullGrammarExpressionAnalysis analysis) {
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
        for (FullGrammarExpressionAnalysis analysis :
                expressionAnalyzer.caseExpressionAnalyses(expression, rowsets.defaultProjectionQualifier())) {
            if (!analysis.hasSources()) {
                continue;
            }
            recorder.write(ctx, type, mappingKind, source.clean(targetAlias),
                    source.clean(targetTable), targetColumn, analysis);
        }
    }
}
