package com.relationdetector.contracts.parse;

/**
 *
 * A column-shaped source before alias resolution.
 */
public record ExpressionSource(String alias, String column) {
    public static final ExpressionSource EMPTY = new ExpressionSource("", "");

    public ExpressionSource {
        alias = alias == null ? "" : alias;
        column = column == null ? "" : column;
    }
}
