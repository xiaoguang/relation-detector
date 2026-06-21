package com.relationdetector.contracts.parse;

import java.util.Map;

import com.relationdetector.contracts.Enums.StructuredParseEventType;

/**
 * relationship / lineage 评分前的一条 parser-level fact。
 *
 * <p>CN: 这不是 relationship。它是 token-event 与 full-grammer 共享的中间事件流，
 * 用于描述 rowset、predicate、DDL clause、dynamic SQL marker 和写入表达式，
 * 避免 grammar 层耦合 confidence 计算。
 *
 * <p>EN: Parser-level fact extracted before relationship or lineage scoring.
 * It is not a relationship; it is the shared intermediate event stream used by
 * token-event and full-grammer to describe rowsets, predicates, DDL clauses,
 * dynamic SQL markers, and write expressions.
 */
public record StructuredSqlEvent(
        StructuredParseEventType type,
        String sourceName,
        long line,
        Map<String, Object> attributes
) {
    public StructuredSqlEvent {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
