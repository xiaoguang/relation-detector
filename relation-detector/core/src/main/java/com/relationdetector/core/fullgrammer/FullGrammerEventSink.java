package com.relationdetector.core.fullgrammer;


import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * Normalizes events emitted by a full-grammer typed visitor.
 *
 * <p>CN: sink 统一补 sourceName、line 和 full-grammer 诊断 attributes，使 MySQL/Postgres
 * visitor 不重复拼装事件外壳。它不解析 SQL，也不判断关系语义。</p>
 *
 * <p>EN: The sink attaches source metadata and full-grammer diagnostic
 * attributes for dialect visitors. It does not parse SQL or infer semantics.</p>
 */
final class FullGrammerEventSink {
    private final SqlStatementRecord statement;
    private final String contextSource;
    private final java.util.List<StructuredSqlEvent> events = new java.util.ArrayList<>();

    FullGrammerEventSink(SqlStatementRecord statement, String contextSource) {
        this.statement = statement;
        this.contextSource = contextSource;
    }

    /**
     * Adds a visitor event and marks it as full-grammer native.
     *
     * <p>CN: 该标记用于诊断和 semantic-equivalent benchmark；正式输出仍只看
     * relationship/lineage candidates。</p>
     */
    void add(StructuredSqlEvent event) {
        events.add(event.withProvenance(event.provenance()
                .asFullGrammer(statement.sourceName(), contextSource)));
    }

    boolean hasType(StructuredParseEventType type) {
        return events.stream().anyMatch(event -> event.type() == type);
    }

    java.util.List<StructuredSqlEvent> events() {
        return java.util.List.copyOf(events);
    }
}
