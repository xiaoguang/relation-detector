package com.relationdetector.core.tokenevent;


import java.util.Set;

import org.antlr.v4.runtime.Token;

import com.relationdetector.core.antlr.postgres.PostgresRelationSqlLexer;

/**
 * PostgreSQL-owned token-event builder.
 *
 * <p>PostgreSQL-specific rowset and expression compatibility belongs here,
 * not in the common token-event builder. This keeps rules for ONLY, LATERAL,
 * ROWS FROM, set-returning functions, and PostgreSQL DML syntax isolated from
 * MySQL.
 */
public final class PostgresTokenEventSqlEventBuilder extends TokenEventSqlEventBuilder {
    public PostgresTokenEventSqlEventBuilder() {
        super("PostgresTokenEventSqlEventBuilder",
                Set.of(PostgresRelationSqlLexer.IDENTIFIER, PostgresRelationSqlLexer.QUOTED_IDENTIFIER));
    }

    @Override
    protected String cleanIdentifier(String value) {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    @Override
    protected int skipDialectTableDecorators(java.util.List<Token> tokens, int index) {
        int cursor = index;
        if (cursor < tokens.size() && tokens.get(cursor).getText().equalsIgnoreCase("tablesample")) {
            cursor++;
            if (cursor < tokens.size()) {
                cursor++;
            }
            if (cursor < tokens.size() && tokens.get(cursor).getText().equals("(")) {
                int depth = 0;
                for (int i = cursor; i < tokens.size(); i++) {
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
        }
        return cursor;
    }
}
