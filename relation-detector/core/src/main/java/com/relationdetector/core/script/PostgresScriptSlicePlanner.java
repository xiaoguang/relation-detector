package com.relationdetector.core.script;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * Plans PostgreSQL statements while preserving dollar-quoted routine bodies.
 */
final class PostgresScriptSlicePlanner extends ScriptFramingSupport implements ScriptSlicePlanner {
    @Override
    public List<Slice> plan(String text, List<ScriptLexeme> lexemes, List<Slice> markedSlices) {
        if (!markedSlices.isEmpty()) {
            return List.copyOf(markedSlices);
        }
        List<Slice> result = new ArrayList<>();
        int statementStart = 0;
        String dollarTag = null;
        for (ScriptLexeme token : lexemes) {
            if (token.kind() == ScriptLexemeKind.DOLLAR_TAG) {
                if (dollarTag == null) {
                    dollarTag = token.text();
                } else if (dollarTag.equals(token.text())) {
                    dollarTag = null;
                }
            } else if (token.kind() == ScriptLexemeKind.SEMICOLON && dollarTag == null) {
                result.add(new Slice(statementStart, token.endOffset(), false));
                statementStart = token.endOffset();
            }
        }
        if (statementStart < text.length()) {
            result.add(new Slice(statementStart, text.length(), false));
        }
        return result;
    }
}
