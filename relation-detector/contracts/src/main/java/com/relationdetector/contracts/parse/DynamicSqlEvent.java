package com.relationdetector.contracts.parse;

import com.relationdetector.contracts.Enums.StructuredParseEventType;

/** Dynamic SQL marker that cannot be resolved statically. */
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
