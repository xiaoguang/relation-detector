package com.relationdetector.core.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.ScriptFrameRequest;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.log.SourceNameNormalizer;

/**
 *
 * Statement, object descriptor, and provenance assembly shared by script framing.
 */
abstract class ScriptFramingSupport {
    protected List<Slice> splitSemicolon(List<ScriptLexeme> lexemes, int start, int end) {
        List<Slice> result = new ArrayList<>();
        int statementStart = start;
        for (ScriptLexeme token : lexemes) {
            if (token.kind() == ScriptLexemeKind.SEMICOLON
                    && token.startOffset() >= start && token.startOffset() < end) {
                result.add(new Slice(statementStart, token.endOffset(), false));
                statementStart = token.endOffset();
            }
        }
        if (statementStart < end) result.add(new Slice(statementStart, end, false));
        return result;
    }

    protected SqlStatementRecord statement(ScriptFrameRequest request, List<ScriptLexeme> lexemes,
            LineIndex lines, Slice slice, List<String> localTempTables) {
        int start = trimStart(request.text(), slice.startOffset(), slice.endOffset());
        int end = trimEnd(request.text(), start, slice.endOffset());
        if (start >= end || onlyCommentsAndWhitespace(lexemes, start, end)) return null;
        String sql = request.text().substring(start, end);
        if (slice.appendSemicolon()) sql += ";";
        ObjectDescriptor descriptor = objectDescriptor(lexemes, start, end);
        StatementSourceType sourceType = descriptor.isObject() ? descriptor.sourceType()
                : markerSourceType(slice.explicitSource(), request.defaultSourceType());
        String normalizedFile = SourceNameNormalizer.normalize(request.sourceFile());
        long startLine = lines.lineAt(start);
        long endLine = lines.lineAt(end - 1);
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (!localTempTables.isEmpty()) attributes.put("localTempTables", localTempTables);
        attributes.put("sourceFile", normalizedFile);
        attributes.put("sourceFileLineCount", lines.lineCount());
        String objectType = switch (sourceType) {
            case VIEW, MATERIALIZED_VIEW -> "QUERY";
            case DDL_FILE -> "DDL";
            case NATIVE_LOG, PLAIN_SQL, MIGRATION -> "";
            default -> sourceType.name();
        };
        if (!objectType.isBlank()) attributes.put("sourceObjectType", objectType);
        if (descriptor.routineReturnsTrigger()) {
            attributes.put("sourceObjectType", "FUNCTION");
            attributes.put("routineReturnsTrigger", true);
        }
        String sourceName = normalizedFile;
        String explicitBlock = markerBlockId(slice.explicitSource());
        String objectName = descriptor.isObject() ? descriptor.objectName() : markerObjectName(explicitBlock);
        if (!objectName.isBlank() || !explicitBlock.isBlank()) {
            sourceName = slice.explicitSource().isBlank() ? sourceName(sourceType, objectName) : slice.explicitSource();
            String blockId = explicitBlock.isBlank() ? objectName : explicitBlock;
            attributes.put("sourceBlockId", blockId);
            if (!objectName.isBlank()) {
                attributes.put("sourceObjectName", objectName);
                attributes.put("sourceObjectIdentity", objectName);
            }
            attributes.put("sourceStatementId", blockId);
        } else {
            attributes.put("sourceStatementId", normalizedFile + ":" + startLine + "-" + endLine);
        }
        return new SqlStatementRecord(sql, sourceType, sourceName, startLine, endLine, attributes);
    }

    protected List<String> localTempTables(List<ScriptLexeme> lexemes) {
        List<ScriptLexeme> significant = lexemes.stream()
                .filter(token -> token.kind() != ScriptLexemeKind.COMMENT
                        && token.kind() != ScriptLexemeKind.WHITESPACE
                        && token.kind() != ScriptLexemeKind.NEWLINE).toList();
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        for (int index = 0; index < significant.size(); index++) {
            if (!kindAt(significant, index, ScriptLexemeKind.CREATE)
                    || !(kindAt(significant, index + 1, ScriptLexemeKind.TEMPORARY)
                    || kindAt(significant, index + 1, ScriptLexemeKind.TEMP))
                    || !kindAt(significant, index + 2, ScriptLexemeKind.TABLE)) continue;
            int nameIndex = index + 3;
            if (kindAt(significant, nameIndex, ScriptLexemeKind.IF)
                    && kindAt(significant, nameIndex + 1, ScriptLexemeKind.NOT)
                    && kindAt(significant, nameIndex + 2, ScriptLexemeKind.EXISTS)) nameIndex += 3;
            String table = qualifiedIdentifier(significant, nameIndex);
            if (!table.isBlank()) result.add(table);
        }
        return List.copyOf(result);
    }

    protected boolean firstSignificantOnLine(String text, int offset) {
        for (int cursor = lineStart(text, offset); cursor < offset; cursor++) {
            if (!Character.isWhitespace(text.charAt(cursor))) return false;
        }
        return true;
    }

    protected boolean onlyTokenOnLine(String text, ScriptLexeme token) {
        for (int cursor = lineStart(text, token.startOffset()); cursor < token.startOffset(); cursor++) {
            if (!Character.isWhitespace(text.charAt(cursor))) return false;
        }
        int end = lineEnd(text, token.endOffset());
        for (int cursor = token.endOffset(); cursor < end; cursor++) {
            if (!Character.isWhitespace(text.charAt(cursor))) return false;
        }
        return true;
    }

    protected int lineStart(String text, int offset) {
        int cursor = Math.min(offset, text.length());
        while (cursor > 0 && text.charAt(cursor - 1) != '\n' && text.charAt(cursor - 1) != '\r') cursor--;
        return cursor;
    }

    protected int lineEnd(String text, int offset) {
        int cursor = Math.min(offset, text.length());
        while (cursor < text.length() && text.charAt(cursor) != '\n' && text.charAt(cursor) != '\r') cursor++;
        return cursor;
    }

    protected int includeFollowingNewline(String text, int offset) {
        int cursor = offset;
        if (cursor < text.length() && text.charAt(cursor) == '\r') cursor++;
        if (cursor < text.length() && text.charAt(cursor) == '\n') cursor++;
        return cursor;
    }

    protected int skipWhitespace(String text, int start, int end) {
        int cursor = start;
        while (cursor < end && Character.isWhitespace(text.charAt(cursor))) cursor++;
        return cursor;
    }

    protected int trimWhitespaceEnd(String text, int start, int end) {
        int cursor = end;
        while (cursor > start && Character.isWhitespace(text.charAt(cursor - 1))) cursor--;
        return cursor;
    }

    protected boolean equalsIgnoreCase(String left, String right) {
        return left != null && left.equalsIgnoreCase(right);
    }

    protected boolean startsWithIgnoreCase(String value, String prefix) {
        return value != null && prefix != null && value.length() >= prefix.length()
                && value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private StatementSourceType markerSourceType(String source, StatementSourceType fallback) {
        if (startsWithIgnoreCase(source, "TRIGGER:")) return StatementSourceType.TRIGGER;
        if (startsWithIgnoreCase(source, "FUNCTION:")) return StatementSourceType.FUNCTION;
        if (startsWithIgnoreCase(source, "ROUTINE:") || startsWithIgnoreCase(source, "PROCEDURE:")) {
            return StatementSourceType.PROCEDURE;
        }
        return fallback;
    }

    private String markerBlockId(String source) {
        if (source == null) return "";
        for (String prefix : List.of("ROUTINE:", "TRIGGER:", "PROCEDURE:", "FUNCTION:")) {
            if (startsWithIgnoreCase(source, prefix)) return source.substring(prefix.length()).trim();
        }
        return source.trim();
    }

    private String markerObjectName(String sourceBlockId) {
        String value = sourceBlockId == null ? "" : sourceBlockId.trim();
        int separator = value.indexOf('.');
        if (separator > 0) {
            String namespace = value.substring(0, separator).toLowerCase(Locale.ROOT);
            if (List.of("common", "mysql", "oracle", "portable", "postgres", "postgresql", "sqlserver")
                    .contains(namespace)) value = value.substring(separator + 1);
        }
        return value;
    }

    private boolean onlyCommentsAndWhitespace(List<ScriptLexeme> lexemes, int start, int end) {
        return lexemes.stream().filter(token -> token.endOffset() > start && token.startOffset() < end)
                .allMatch(token -> token.kind() == ScriptLexemeKind.COMMENT
                        || token.kind() == ScriptLexemeKind.WHITESPACE
                        || token.kind() == ScriptLexemeKind.NEWLINE);
    }

    protected ObjectDescriptor objectDescriptor(List<ScriptLexeme> lexemes, int start, int end) {
        List<ScriptLexeme> tokens = lexemes.stream()
                .filter(token -> token.startOffset() >= start && token.startOffset() < end)
                .filter(token -> token.kind() != ScriptLexemeKind.COMMENT
                        && token.kind() != ScriptLexemeKind.WHITESPACE
                        && token.kind() != ScriptLexemeKind.NEWLINE).toList();
        int cursor = 0;
        if (!kindAt(tokens, cursor, ScriptLexemeKind.CREATE)) return ObjectDescriptor.none();
        cursor++;
        if (kindAt(tokens, cursor, ScriptLexemeKind.OR)
                && (kindAt(tokens, cursor + 1, ScriptLexemeKind.REPLACE)
                || kindAt(tokens, cursor + 1, ScriptLexemeKind.ALTER))) cursor += 2;
        while (kindAt(tokens, cursor, ScriptLexemeKind.EDITIONABLE)
                || kindAt(tokens, cursor, ScriptLexemeKind.NONEDITIONABLE)) cursor++;
        StatementSourceType type;
        if (kindAt(tokens, cursor, ScriptLexemeKind.MATERIALIZED)
                && kindAt(tokens, cursor + 1, ScriptLexemeKind.VIEW)) {
            type = StatementSourceType.MATERIALIZED_VIEW; cursor += 2;
        } else if (kindAt(tokens, cursor, ScriptLexemeKind.PACKAGE)
                && kindAt(tokens, cursor + 1, ScriptLexemeKind.BODY)) {
            type = StatementSourceType.PACKAGE_BODY; cursor += 2;
        } else {
            type = objectType(tokens, cursor);
            if (type == null) return ObjectDescriptor.none();
            cursor++;
        }
        String objectName = qualifiedIdentifier(tokens, cursor);
        if (objectName.isBlank()) return ObjectDescriptor.none();
        boolean returnsTrigger = type == StatementSourceType.FUNCTION
                && containsKindSequence(tokens, cursor + 1, ScriptLexemeKind.RETURNS, ScriptLexemeKind.TRIGGER);
        return new ObjectDescriptor(type, objectName, returnsTrigger);
    }

    private StatementSourceType objectType(List<ScriptLexeme> tokens, int index) {
        if (kindAt(tokens, index, ScriptLexemeKind.PROCEDURE)) return StatementSourceType.PROCEDURE;
        if (kindAt(tokens, index, ScriptLexemeKind.FUNCTION)) return StatementSourceType.FUNCTION;
        if (kindAt(tokens, index, ScriptLexemeKind.TRIGGER)) return StatementSourceType.TRIGGER;
        if (kindAt(tokens, index, ScriptLexemeKind.PACKAGE)) return StatementSourceType.PACKAGE;
        if (kindAt(tokens, index, ScriptLexemeKind.EVENT)) return StatementSourceType.EVENT;
        if (kindAt(tokens, index, ScriptLexemeKind.VIEW)) return StatementSourceType.VIEW;
        return null;
    }

    private String qualifiedIdentifier(List<ScriptLexeme> tokens, int index) {
        if (!identifierToken(tokens, index)) return "";
        StringBuilder value = new StringBuilder(unquote(tokens.get(index).text()));
        int cursor = index + 1;
        while (cursor + 1 < tokens.size() && tokens.get(cursor).kind() == ScriptLexemeKind.DOT
                && identifierToken(tokens, cursor + 1)) {
            value.append('.').append(unquote(tokens.get(cursor + 1).text())); cursor += 2;
        }
        return value.toString();
    }

    private boolean identifierToken(List<ScriptLexeme> tokens, int index) {
        return index >= 0 && index < tokens.size()
                && (tokens.get(index).kind() == ScriptLexemeKind.WORD
                || tokens.get(index).kind() == ScriptLexemeKind.QUOTED);
    }

    private String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0), last = value.charAt(value.length() - 1);
            if ((first == '[' && last == ']') || (first == '`' && last == '`')
                    || (first == '"' && last == '"')) return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private boolean containsKindSequence(List<ScriptLexeme> tokens, int start,
            ScriptLexemeKind first, ScriptLexemeKind second) {
        for (int index = Math.max(0, start); index + 1 < tokens.size(); index++) {
            if (kindAt(tokens, index, first) && kindAt(tokens, index + 1, second)) return true;
        }
        return false;
    }

    private boolean kindAt(List<ScriptLexeme> tokens, int index, ScriptLexemeKind kind) {
        return index >= 0 && index < tokens.size() && tokens.get(index).kind() == kind;
    }

    private String sourceName(StatementSourceType type, String objectName) {
        return switch (type) {
            case TRIGGER -> "TRIGGER:" + objectName;
            case PROCEDURE, FUNCTION, PACKAGE, PACKAGE_BODY, EVENT -> "ROUTINE:" + objectName;
            default -> objectName;
        };
    }

    private int trimStart(String text, int start, int end) { return skipWhitespace(text, start, end); }
    private int trimEnd(String text, int start, int end) { return trimWhitespaceEnd(text, start, end); }

    protected record Slice(int startOffset, int endOffset, boolean appendSemicolon, String explicitSource) {
        protected Slice(int startOffset, int endOffset, boolean appendSemicolon) {
            this(startOffset, endOffset, appendSemicolon, "");
        }
    }
    protected record DelimiterDirective(int startOffset, int endOffset, String delimiter) {}
    protected record ObjectDescriptor(StatementSourceType sourceType, String objectName,
            boolean routineReturnsTrigger) {
        static ObjectDescriptor none() { return new ObjectDescriptor(null, "", false); }
        boolean isObject() { return sourceType != null && objectName != null && !objectName.isBlank(); }
    }
    protected record LineIndex(int[] lineStarts, int textLength) {
        static LineIndex of(String text) {
            List<Integer> starts = new ArrayList<>(); starts.add(0);
            for (int offset = 0; offset < text.length(); offset++) {
                char current = text.charAt(offset);
                if (current == '\r') {
                    if (offset + 1 < text.length() && text.charAt(offset + 1) == '\n') offset++;
                    starts.add(offset + 1);
                } else if (current == '\n') starts.add(offset + 1);
            }
            return new LineIndex(starts.stream().mapToInt(Integer::intValue).toArray(), text.length());
        }
        int lineAt(int offset) {
            int position = java.util.Arrays.binarySearch(lineStarts, offset);
            return position >= 0 ? position + 1 : -position - 1;
        }
        int lineCount() {
            int count = lineStarts.length;
            return count > 1 && lineStarts[count - 1] == textLength ? count - 1 : count;
        }
    }
}
