package com.relationdetector.contracts.parse;

import com.relationdetector.contracts.Enums.DatabaseObjectType;

/** Procedure/function/view/trigger SQL definition collected from DB or files. */
public record DatabaseObjectDefinition(
        DatabaseObjectType type,
        String schema,
        String name,
        String sql,
        String source
) {
}
