package com.relationdetector.core.script;

import java.util.ArrayList;
import java.util.List;

import com.relationdetector.contracts.Enums.StatementSourceType;

/**
 *
 * Plans SQL Server GO batches and keeps routine/trigger bodies atomic.
 */
final class SqlServerScriptSlicePlanner extends ScriptFramingSupport implements ScriptSlicePlanner {
    @Override
    public List<Slice> plan(String text, List<ScriptLexeme> lexemes, List<Slice> markedSlices) {
        if (!markedSlices.isEmpty()) {
            return List.copyOf(markedSlices);
        }
        List<Slice> result = new ArrayList<>();
        int batchStart = 0;
        for (ScriptLexeme token : lexemes) {
            if (token.kind() != ScriptLexemeKind.GO || !onlyTokenOnLine(text, token)) {
                continue;
            }
            appendBatch(result, lexemes, batchStart, token.startOffset());
            batchStart = includeFollowingNewline(text, lineEnd(text, token.endOffset()));
        }
        appendBatch(result, lexemes, batchStart, text.length());
        return result;
    }

    private void appendBatch(List<Slice> result, List<ScriptLexeme> lexemes, int start, int end) {
        ObjectDescriptor descriptor = objectDescriptor(lexemes, start, end);
        if (descriptor.sourceType() == StatementSourceType.PROCEDURE
                || descriptor.sourceType() == StatementSourceType.FUNCTION
                || descriptor.sourceType() == StatementSourceType.TRIGGER) {
            result.add(new Slice(start, end, false));
        } else {
            result.addAll(splitSemicolon(lexemes, start, end));
        }
    }
}
