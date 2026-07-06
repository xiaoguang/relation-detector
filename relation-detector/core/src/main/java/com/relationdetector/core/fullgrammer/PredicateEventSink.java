package com.relationdetector.core.fullgrammer;

import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.Enums.StructuredParseEventType;

final class PredicateEventSink {
    private final SourceLocationSupport source;
    private final FullGrammerEventRecorder recorder;

    PredicateEventSink(SourceLocationSupport source, FullGrammerEventRecorder recorder) {
        this.source = source;
        this.recorder = recorder;
    }

    void predicateEvent(
            ParserRuleContext ctx,
            StructuredParseEventType eventType,
            String leftAlias,
            String leftColumn,
            String rightAlias,
            String rightColumn,
            String joinKind
    ) {
        if (sameQualifier(leftAlias, rightAlias)) {
            return;
        }
        Map<String, Object> attributes = source.nativeAttributes();
        attributes.put("leftAlias", leftAlias);
        attributes.put("leftColumn", leftColumn);
        attributes.put("rightAlias", rightAlias);
        attributes.put("rightColumn", rightColumn);
        attributes.put("joinKind", source.blankTo(joinKind, "WHERE_OR_UNKNOWN"));
        recorder.add(ctx, eventType, attributes);
    }

    void joinUsing(ParserRuleContext ctx, String leftAlias, String rightAlias, List<String> columns) {
        if (source.clean(leftAlias).isBlank() || source.clean(rightAlias).isBlank() || columns.isEmpty()) {
            return;
        }
        Map<String, Object> attributes = source.nativeAttributes();
        attributes.put("leftAlias", source.clean(leftAlias));
        attributes.put("rightAlias", source.clean(rightAlias));
        attributes.put("usingColumns", columns.stream().map(source::clean).filter(s -> !s.isBlank()).toList());
        recorder.add(ctx, StructuredParseEventType.JOIN_USING_COLUMNS, attributes);
    }

    private boolean sameQualifier(String leftQualifier, String rightQualifier) {
        return !source.clean(leftQualifier).isBlank()
                && source.clean(leftQualifier).equalsIgnoreCase(source.clean(rightQualifier));
    }
}
