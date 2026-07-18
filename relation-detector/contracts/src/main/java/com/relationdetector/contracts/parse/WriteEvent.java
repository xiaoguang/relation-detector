package com.relationdetector.contracts.parse;

import com.relationdetector.contracts.Enums.StructuredParseEventType;

/**
 * CN: 表示 typed SQL 中的写入目标、列赋值或写入映射，并携带表达式依赖。
 * EN: Represents a typed write target, column assignment, or write mapping together with its expression dependencies.
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
