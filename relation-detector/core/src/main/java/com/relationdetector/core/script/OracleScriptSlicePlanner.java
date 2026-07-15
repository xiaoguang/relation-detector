package com.relationdetector.core.script;

import java.util.ArrayList;
import java.util.List;

/** Plans Oracle slash-terminated object blocks and ordinary statements. */
final class OracleScriptSlicePlanner extends ScriptFramingSupport implements ScriptSlicePlanner {
    @Override
    public List<Slice> plan(String text, List<ScriptLexeme> lexemes, List<Slice> markedSlices) {
        if (!markedSlices.isEmpty()) {
            return normalizeMarked(text, lexemes, markedSlices);
        }
        List<Slice> result = new ArrayList<>();
        int segmentStart = 0;
        for (ScriptLexeme token : lexemes) {
            if (token.kind() != ScriptLexemeKind.SYMBOL || !"/".equals(token.text())
                    || !onlyTokenOnLine(text, token)) {
                continue;
            }
            appendSegment(result, lexemes, segmentStart, token.startOffset());
            segmentStart = includeFollowingNewline(text, lineEnd(text, token.endOffset()));
        }
        appendSegment(result, lexemes, segmentStart, text.length());
        return result;
    }

    private List<Slice> normalizeMarked(String text, List<ScriptLexeme> lexemes, List<Slice> markedSlices) {
        List<Slice> result = new ArrayList<>();
        for (Slice slice : markedSlices) {
            List<ScriptLexeme> significant = lexemes.stream()
                    .filter(token -> token.startOffset() >= slice.startOffset()
                            && token.endOffset() <= slice.endOffset())
                    .filter(token -> token.kind() != ScriptLexemeKind.WHITESPACE
                            && token.kind() != ScriptLexemeKind.NEWLINE
                            && token.kind() != ScriptLexemeKind.COMMENT)
                    .toList();
            if (!significant.isEmpty()) {
                ScriptLexeme last = significant.get(significant.size() - 1);
                if ("/".equals(last.text()) && onlyTokenOnLine(text, last)) {
                    result.add(new Slice(slice.startOffset(), lineStart(text, last.startOffset()), false,
                            slice.explicitSource()));
                    continue;
                }
            }
            result.add(slice);
        }
        return result;
    }

    private void appendSegment(List<Slice> result, List<ScriptLexeme> lexemes, int start, int end) {
        if (objectDescriptor(lexemes, start, end).isObject()) {
            result.add(new Slice(start, end, false));
            return;
        }
        ScriptLexeme objectStart = firstObjectDeclaration(lexemes, start, end);
        if (objectStart != null) {
            result.addAll(splitSemicolon(lexemes, start, objectStart.startOffset()));
            result.add(new Slice(objectStart.startOffset(), end, false));
            return;
        }
        result.addAll(splitSemicolon(lexemes, start, end));
    }

    private ScriptLexeme firstObjectDeclaration(List<ScriptLexeme> lexemes, int start, int end) {
        for (ScriptLexeme token : lexemes) {
            if (token.kind() == ScriptLexemeKind.CREATE
                    && token.startOffset() >= start && token.startOffset() < end
                    && objectDescriptor(lexemes, token.startOffset(), end).isObject()) {
                return token;
            }
        }
        return null;
    }
}
