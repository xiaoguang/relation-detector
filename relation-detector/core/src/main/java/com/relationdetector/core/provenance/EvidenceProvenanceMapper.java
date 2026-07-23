package com.relationdetector.core.provenance;

import java.util.Map;

import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * CN: 把 typed event 的文件、statement、行号和 object provenance 复制到 evidence attributes，不改变语义 fact key。
 * EN: Copies typed event file, statement, line, and object provenance into evidence attributes without changing semantic fact identity.
 */
public final class EvidenceProvenanceMapper {
    private EvidenceProvenanceMapper() {
    }

    public static SqlStatementRecord withParserProvenance(
            SqlStatementRecord statement,
            StructuredParseResult structured
    ) {
        Object identity = structured.attributes().get("sourceObjectIdentity");
        if (!(identity instanceof String value) || value.isBlank()) {
            return statement;
        }
        Map<String, Object> attributes = new java.util.LinkedHashMap<>(statement.attributes());
        attributes.put("sourceObjectIdentity", value);
        return new SqlStatementRecord(
                statement.sql(), statement.sourceType(), statement.sourceName(),
                statement.startLine(), statement.endLine(), attributes);
    }

    public static void copy(
            SqlStatementRecord statement,
            StructuredSqlEvent event,
            Map<String, Object> attributes
    ) {
        copyStatement(statement, attributes);
        SourceProvenance provenance = event.provenance();
        put(attributes, "sourceFile", provenance.sourceFile());
        put(attributes, "sourceStatementId", provenance.sourceStatementId());
        put(attributes, "sourceBlockId", provenance.sourceBlockId());
        put(attributes, "sourceObjectType", provenance.sourceObjectType());
        put(attributes, "sourceObjectName", provenance.sourceObjectName());
        attributes.put("sourceLine", event.line());
        if (provenance.tokenEventNative()) {
            attributes.put("tokenEventNative", true);
        }
        if (provenance.fullGrammarNative()) {
            attributes.put("fullGrammarNative", true);
        }
        put(attributes, "fullGrammarContextSource", provenance.fullGrammarContextSource());
    }

    private static void copyStatement(SqlStatementRecord statement, Map<String, Object> attributes) {
        for (String key : new String[]{"sourceFile", "sourceStatementId", "sourceBlockId",
                "sourceObjectType", "sourceObjectName", "sourceObjectIdentity", "sourceObjectKind"}) {
            Object value = statement.attributes().get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                attributes.put(key, value);
            }
        }
    }

    private static void put(Map<String, Object> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }

}
