package com.relationdetector.core.script;

import java.util.ArrayList;
import java.util.List;

/** Plans MySQL client-script slices, including DELIMITER and fixture terminators. */
final class MySqlScriptSlicePlanner extends ScriptFramingSupport implements ScriptSlicePlanner {
    @Override
    public List<Slice> plan(String text, List<ScriptLexeme> lexemes, List<Slice> markedSlices) {
        if (markedSlices.isEmpty()) {
            return splitMysql(text, lexemes);
        }
        List<Slice> result = new ArrayList<>();
        for (Slice markedSlice : markedSlices) {
            List<Slice> framed = splitMarkedSlice(text, lexemes, markedSlice);
            if (!framed.isEmpty()) {
                result.addAll(framed);
                continue;
            }
            List<ScriptLexeme> significant = significantWithin(lexemes, markedSlice);
            if (significant.isEmpty()) {
                result.add(markedSlice);
                continue;
            }
            ScriptLexeme last = significant.get(significant.size() - 1);
            if ("$$".equals(last.text())) {
                result.add(new Slice(markedSlice.startOffset(), last.startOffset(), true,
                        markedSlice.explicitSource()));
                continue;
            }
            if (significant.size() >= 2) {
                ScriptLexeme previous = significant.get(significant.size() - 2);
                if ("/".equals(previous.text()) && "/".equals(last.text())
                        && previous.endOffset() == last.startOffset()) {
                    result.add(new Slice(markedSlice.startOffset(), previous.startOffset(), true,
                            markedSlice.explicitSource()));
                    continue;
                }
            }
            result.add(markedSlice);
        }
        return result;
    }

    private List<ScriptLexeme> significantWithin(List<ScriptLexeme> lexemes, Slice slice) {
        return lexemes.stream()
                .filter(token -> token.startOffset() >= slice.startOffset()
                        && token.endOffset() <= slice.endOffset())
                .filter(token -> token.kind() != ScriptLexemeKind.WHITESPACE
                        && token.kind() != ScriptLexemeKind.NEWLINE
                        && token.kind() != ScriptLexemeKind.COMMENT)
                .toList();
    }

    private List<Slice> splitMarkedSlice(String text, List<ScriptLexeme> lexemes, Slice markedSlice) {
        String delimiter = delimiterAt(text, lexemes, markedSlice.startOffset());
        if (";".equals(delimiter)) {
            return splitMarkedCustomTerminator(lexemes, markedSlice);
        }
        List<Slice> customDelimited = splitWithDelimiter(text, lexemes,
                markedSlice.startOffset(), markedSlice.endOffset(), delimiter);
        if (customDelimited.stream().noneMatch(Slice::appendSemicolon)) {
            return List.of();
        }
        List<Slice> result = new ArrayList<>();
        for (Slice slice : customDelimited) {
            if (slice.appendSemicolon()) {
                result.add(withExplicitSource(slice, markedSlice.explicitSource()));
                continue;
            }
            splitSemicolon(lexemes, slice.startOffset(), slice.endOffset()).stream()
                    .map(trailing -> withExplicitSource(trailing, markedSlice.explicitSource()))
                    .forEach(result::add);
        }
        return result;
    }

    private List<Slice> splitMarkedCustomTerminator(List<ScriptLexeme> lexemes, Slice markedSlice) {
        ScriptLexeme terminator = lexemes.stream()
                .filter(token -> token.kind() == ScriptLexemeKind.CUSTOM_TERMINATOR)
                .filter(token -> token.startOffset() >= markedSlice.startOffset()
                        && token.endOffset() <= markedSlice.endOffset())
                .findFirst()
                .orElse(null);
        if (terminator == null) {
            return List.of();
        }
        List<Slice> result = new ArrayList<>();
        result.add(new Slice(markedSlice.startOffset(), terminator.startOffset(), true,
                markedSlice.explicitSource()));
        splitSemicolon(lexemes, terminator.endOffset(), markedSlice.endOffset()).stream()
                .map(trailing -> withExplicitSource(trailing, markedSlice.explicitSource()))
                .forEach(result::add);
        return result;
    }

    private String delimiterAt(String text, List<ScriptLexeme> lexemes, int offset) {
        String delimiter = ";";
        for (DelimiterDirective directive : delimiterDirectives(text, lexemes)) {
            if (directive.endOffset() > offset) {
                break;
            }
            delimiter = directive.delimiter();
        }
        return delimiter;
    }

    private Slice withExplicitSource(Slice slice, String explicitSource) {
        return new Slice(slice.startOffset(), slice.endOffset(), slice.appendSemicolon(), explicitSource);
    }

    private List<Slice> splitMysql(String text, List<ScriptLexeme> lexemes) {
        List<DelimiterDirective> directives = delimiterDirectives(text, lexemes);
        List<Slice> result = new ArrayList<>();
        int start = 0;
        String delimiter = ";";
        for (DelimiterDirective directive : directives) {
            result.addAll(splitWithDelimiter(text, lexemes, start, directive.startOffset(), delimiter));
            delimiter = directive.delimiter();
            start = directive.endOffset();
        }
        result.addAll(splitWithDelimiter(text, lexemes, start, text.length(), delimiter));
        return result;
    }

    private List<DelimiterDirective> delimiterDirectives(String text, List<ScriptLexeme> lexemes) {
        List<DelimiterDirective> result = new ArrayList<>();
        for (ScriptLexeme token : lexemes) {
            if (token.kind() != ScriptLexemeKind.DELIMITER
                    || !firstSignificantOnLine(text, token.startOffset())) {
                continue;
            }
            int lineEnd = lineEnd(text, token.endOffset());
            int valueStart = skipWhitespace(text, token.endOffset(), lineEnd);
            int valueEnd = trimWhitespaceEnd(text, valueStart, lineEnd);
            if (valueStart < valueEnd) {
                result.add(new DelimiterDirective(lineStart(text, token.startOffset()),
                        includeFollowingNewline(text, lineEnd), text.substring(valueStart, valueEnd)));
            }
        }
        return result;
    }

    private List<Slice> splitWithDelimiter(
            String text,
            List<ScriptLexeme> lexemes,
            int regionStart,
            int regionEnd,
            String delimiter
    ) {
        if (delimiter == null || delimiter.isEmpty()) {
            return List.of(new Slice(regionStart, regionEnd, false));
        }
        List<ScriptLexeme> protectedLexemes = lexemes.stream()
                .filter(token -> token.endOffset() > regionStart && token.startOffset() < regionEnd)
                .filter(this::protectsDelimiter)
                .toList();
        List<Slice> slices = new ArrayList<>();
        int start = regionStart;
        int protectedIndex = 0;
        for (int cursor = regionStart; cursor < regionEnd;) {
            while (protectedIndex < protectedLexemes.size()
                    && protectedLexemes.get(protectedIndex).endOffset() <= cursor) {
                protectedIndex++;
            }
            if (protectedIndex < protectedLexemes.size()) {
                ScriptLexeme protectedToken = protectedLexemes.get(protectedIndex);
                if (cursor >= protectedToken.startOffset() && cursor < protectedToken.endOffset()) {
                    cursor = protectedToken.endOffset();
                    continue;
                }
            }
            if (cursor + delimiter.length() <= regionEnd && text.startsWith(delimiter, cursor)) {
                boolean keepDelimiter = ";".equals(delimiter);
                slices.add(new Slice(start, keepDelimiter ? cursor + 1 : cursor, !keepDelimiter));
                cursor += delimiter.length();
                start = cursor;
                continue;
            }
            cursor++;
        }
        if (start < regionEnd) {
            slices.add(new Slice(start, regionEnd, false));
        }
        return slices;
    }

    private boolean protectsDelimiter(ScriptLexeme token) {
        return token.kind() == ScriptLexemeKind.QUOTED || token.kind() == ScriptLexemeKind.COMMENT;
    }
}
