package com.relationdetector.core;

import java.util.Set;

import com.relationdetector.core.antlr.mysql.MySqlRelationSqlLexer;

/**
 * MySQL structural event visitor for ANTLR shadow mode.
 *
 * <p>It treats MySQL backtick tokens as quoted identifiers. Double-quoted
 * identifiers are intentionally not accepted in this first pass because MySQL
 * only treats them as identifiers when ANSI_QUOTES is enabled; that should be a
 * future parser-profile capability flag rather than a hidden default.
 */
public final class MySqlStructuredSqlEventVisitor extends StructuredSqlEventVisitor {
    public MySqlStructuredSqlEventVisitor() {
        super("MySqlStructuredSqlEventVisitor",
                Set.of(MySqlRelationSqlLexer.IDENTIFIER, MySqlRelationSqlLexer.QUOTED_IDENTIFIER));
    }

    @Override
    protected String cleanIdentifier(String value) {
        if (value.startsWith("`") && value.endsWith("`")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
