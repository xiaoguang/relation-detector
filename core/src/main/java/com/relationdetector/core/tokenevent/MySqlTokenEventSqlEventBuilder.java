package com.relationdetector.core.tokenevent;


import java.util.Set;

import org.antlr.v4.runtime.Token;

import com.relationdetector.core.antlr.mysql.MySqlRelationSqlLexer;

/**
 * MySQL-owned token-event builder.
 *
 * <p>The class owns MySQL-only token and shallow parse-tree compatibility so
 * rowset, predicate, and procedure-body rules do not leak into PostgreSQL.
 */
public final class MySqlTokenEventSqlEventBuilder extends TokenEventSqlEventBuilder {
    public MySqlTokenEventSqlEventBuilder() {
        super("MySqlTokenEventSqlEventBuilder",
                Set.of(MySqlRelationSqlLexer.IDENTIFIER, MySqlRelationSqlLexer.QUOTED_IDENTIFIER));
    }

    @Override
    protected String cleanIdentifier(String value) {
        if (value.startsWith("@") && value.length() > 1) {
            return value.substring(1);
        }
        if (value.startsWith("`") && value.endsWith("`")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    @Override
    protected int skipDialectTableDecorators(java.util.List<Token> tokens, int index) {
        int cursor = index;
        if (cursor < tokens.size() && tokens.get(cursor).getText().equalsIgnoreCase("partition")
                && cursor + 1 < tokens.size() && tokens.get(cursor + 1).getText().equals("(")) {
            int depth = 0;
            for (int i = cursor + 1; i < tokens.size(); i++) {
                String text = tokens.get(i).getText();
                if (text.equals("(")) {
                    depth++;
                } else if (text.equals(")")) {
                    depth--;
                    if (depth == 0) {
                        cursor = i + 1;
                        break;
                    }
                }
            }
        }
        return cursor;
    }

    @Override
    protected boolean isDialectNonRowsetJoin(java.util.List<Token> tokens, int index) {
        return index >= 3
                && tokens.get(index - 1).getText().equalsIgnoreCase("for")
                && (tokens.get(index - 2).getText().equalsIgnoreCase("index")
                || tokens.get(index - 2).getText().equalsIgnoreCase("key"));
    }

    @Override
    protected boolean shouldExtractJoinUsingEvent(java.util.List<Token> tokens, int usingIndex, int closeParenIndex) {
        return closeParenIndex + 1 >= tokens.size()
                || !tokens.get(closeParenIndex + 1).getText().equalsIgnoreCase("as");
    }
}
