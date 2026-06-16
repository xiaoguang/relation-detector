package com.relationdetector.api;

import java.util.Map;

import com.relationdetector.api.Enums.StatementSourceType;

/** SQL statement plus provenance used by parsers and output evidence. */
public record SqlStatementRecord(
        String sql,
        StatementSourceType sourceType,
        String sourceName,
        long startLine,
        long endLine,
        Map<String, Object> attributes
) {
    public SqlStatementRecord {
        if (sql == null) {
            sql = "";
        }
        if (attributes == null) {
            attributes = Map.of();
        } else {
            attributes = Map.copyOf(attributes);
        }
    }
}
