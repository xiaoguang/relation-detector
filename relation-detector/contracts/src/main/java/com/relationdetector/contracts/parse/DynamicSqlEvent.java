package com.relationdetector.contracts.parse;

import com.relationdetector.contracts.Enums.StructuredParseEventType;

/**
 * CN: 标记无法静态解析的 dynamic SQL，使下游产生诊断而不扫描字符串内容。
 * EN: Marks dynamic SQL that cannot be resolved statically so downstream code diagnoses it without scanning string contents.
 */
public record DynamicSqlEvent(
        StructuredParseEventType type,
        SourceProvenance provenance,
        String reason
) implements StructuredSqlEvent {
    public DynamicSqlEvent {
        reason = reason == null ? "" : reason;
    }

    @Override
    public StructuredSqlEvent withProvenance(SourceProvenance value) {
        return new DynamicSqlEvent(type, value, reason);
    }
}
