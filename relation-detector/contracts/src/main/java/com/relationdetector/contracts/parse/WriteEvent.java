package com.relationdetector.contracts.parse;

import com.relationdetector.contracts.Enums.StructuredParseEventType;

/**
 *
 * Write target or column assignment/mapping event.
 */
public record WriteEvent(
        StructuredParseEventType type,
        SourceProvenance provenance,
        String table,
        String qualifiedTable,
        String alias,
        String targetAlias,
        String targetTable,
        String targetColumn,
        String mappingKind,
        ExpressionTrace expression
) implements StructuredSqlEvent {
    public WriteEvent {
        table = clean(table);
        qualifiedTable = clean(qualifiedTable);
        alias = clean(alias);
        targetAlias = clean(targetAlias);
        targetTable = clean(targetTable);
        targetColumn = clean(targetColumn);
        mappingKind = clean(mappingKind);
        expression = expression == null ? ExpressionTrace.empty() : expression;
    }

    @Override
    public StructuredSqlEvent withProvenance(SourceProvenance value) {
        return new WriteEvent(type, value, table, qualifiedTable, alias, targetAlias,
                targetTable, targetColumn, mappingKind, expression);
    }

    private static String clean(String value) { return value == null ? "" : value; }
}
