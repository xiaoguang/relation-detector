package com.relationdetector.contracts.parse;

import java.util.Map;

import com.relationdetector.contracts.Enums.StatementSourceType;

/**
 * SQL statement 与来源信息。
 *
 * <p>CN: parser、relationship evidence、lineage evidence 都通过该记录获得 SQL 文本、
 * source type、source name、行号和附加属性。
 *
 * <p>EN: SQL statement plus provenance. Parsers and evidence builders use it
 * for SQL text, source type, source name, line numbers, and attributes.
 */
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
