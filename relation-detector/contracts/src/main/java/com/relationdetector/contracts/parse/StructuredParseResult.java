package com.relationdetector.contracts.parse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.WarningMessage;

/**
 * parser frontend 的结构化输出。
 *
 * <p>CN: token-event 和 full-grammar 都返回该对象。最终 JSON relationship 仍基于
 * RelationshipCandidate；这里是事件、warning 和 parser provenance 的桥接层。
 *
 * <p>EN: Structured output from parser frontends. Both token-event and
 * full-grammar return this object. Final JSON relationships still use
 * RelationshipCandidate; this result bridges events, warnings, and parser provenance.
 */
public record StructuredParseResult(
        String backend,
        String dialect,
        String sourceName,
        List<StructuredSqlEvent> events,
        List<WarningMessage> warnings,
        Map<String, Object> attributes
) {
    public StructuredParseResult {
        events = events == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(events));
        warnings = warnings == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(warnings));
        attributes = attributes == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
