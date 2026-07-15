package com.relationdetector.core.script;

import java.util.ArrayList;
import java.util.List;

import com.relationdetector.contracts.Enums.StatementSourceType;

/** Plans portable scripts and compound procedure/function/trigger bodies. */
final class CommonScriptSlicePlanner extends ScriptFramingSupport implements ScriptSlicePlanner {
    @Override
    public List<Slice> plan(String text, List<ScriptLexeme> lexemes, List<Slice> markedSlices) {
        if (!markedSlices.isEmpty()) {
            return List.copyOf(markedSlices);
        }
        List<ScriptLexeme> significant = lexemes.stream()
                .filter(token -> token.kind() != ScriptLexemeKind.COMMENT
                        && token.kind() != ScriptLexemeKind.WHITESPACE
                        && token.kind() != ScriptLexemeKind.NEWLINE)
                .toList();
        List<Slice> result = new ArrayList<>();
        int segmentStart = 0;
        int objectStart = -1;
        int beginDepth = 0;
        int caseDepth = 0;
        boolean sawBegin = false;
        boolean awaitingObjectTerminator = false;

        for (ScriptLexeme token : significant) {
            if (objectStart < 0) {
                if (token.kind() != ScriptLexemeKind.CREATE) {
                    continue;
                }
                ObjectDescriptor descriptor = objectDescriptor(lexemes, token.startOffset(), text.length());
                if (!isCompoundObject(descriptor)) {
                    continue;
                }
                result.addAll(splitSemicolon(lexemes, segmentStart, token.startOffset()));
                objectStart = token.startOffset();
                beginDepth = 0;
                caseDepth = 0;
                sawBegin = false;
                awaitingObjectTerminator = false;
                continue;
            }

            if (token.kind() == ScriptLexemeKind.CASE) {
                caseDepth++;
            } else if (token.kind() == ScriptLexemeKind.BEGIN && caseDepth == 0) {
                beginDepth++;
                sawBegin = true;
            } else if (token.kind() == ScriptLexemeKind.END) {
                if (caseDepth > 0) {
                    caseDepth--;
                } else if (beginDepth > 0) {
                    beginDepth--;
                    awaitingObjectTerminator = sawBegin && beginDepth == 0;
                }
            } else if (token.kind() == ScriptLexemeKind.SEMICOLON && awaitingObjectTerminator) {
                result.add(new Slice(objectStart, token.endOffset(), false));
                segmentStart = token.endOffset();
                objectStart = -1;
                awaitingObjectTerminator = false;
            }
        }

        if (objectStart >= 0) {
            result.add(new Slice(objectStart, text.length(), false));
        } else {
            result.addAll(splitSemicolon(lexemes, segmentStart, text.length()));
        }
        return result;
    }

    private boolean isCompoundObject(ObjectDescriptor descriptor) {
        return descriptor.sourceType() == StatementSourceType.PROCEDURE
                || descriptor.sourceType() == StatementSourceType.FUNCTION
                || descriptor.sourceType() == StatementSourceType.TRIGGER;
    }
}
