package com.relationdetector.contracts.parse;

/**
 * CN: 表示 alias resolution 之前的列形表达式来源，本身不声称是物理 endpoint。
 * EN: Represents a column-shaped expression source before alias resolution without claiming physical endpoint identity.
 */
public record ExpressionSource(String alias, String column) {
    public static final ExpressionSource EMPTY = new ExpressionSource("", "");

    public ExpressionSource {
        alias = alias == null ? "" : alias;
        column = column == null ? "" : column;
    }
}
