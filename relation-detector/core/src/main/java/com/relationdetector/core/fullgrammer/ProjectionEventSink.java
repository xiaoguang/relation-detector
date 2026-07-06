package com.relationdetector.core.fullgrammer;

import java.util.Map;

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
        FullGrammerExpressionAnalysis analysis = expressionAnalyzer.analyze(expression, rowsets.defaultProjectionQualifier());
        Map<String, Object> attributes = source.nativeAttributes();
        attributes.put("outputAlias", cleanOutputAlias);
        attributes.put("outputColumn", cleanOutputColumn);
        attributes.put("sourceAliases", analysis.sourceAliases());
        attributes.put("sourceColumns", analysis.sourceColumns());
        attributes.put("transformType", analysis.transformType());
        attributes.put("flowKind", analysis.flowKind());
        recorder.add(ctx, StructuredParseEventType.PROJECTION_ITEM, attributes);
        expressionSource(ctx, analysis);
    }

    void expressionSource(ParserRuleContext ctx, FullGrammerExpressionAnalysis analysis) {
        if (!analysis.hasSources()) {
            return;
        }
        Map<String, Object> attributes = source.nativeAttributes();
        attributes.put("sourceAliases", analysis.sourceAliases());
        attributes.put("sourceColumns", analysis.sourceColumns());
        attributes.put("transformType", analysis.transformType());
        attributes.put("flowKind", analysis.flowKind());
        recorder.add(ctx, StructuredParseEventType.EXPRESSION_SOURCE, attributes);
    }
}
