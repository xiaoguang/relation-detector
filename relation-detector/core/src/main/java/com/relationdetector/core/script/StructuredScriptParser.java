package com.relationdetector.core.script;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.ScriptParseRequest;
import com.relationdetector.contracts.parse.ScriptParseResult;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.log.SourceNameNormalizer;

/** Shared framing engine fed exclusively by dialect-generated script lexer tokens. */
public final class StructuredScriptParser {
    public ScriptParseResult parse(
            ScriptParseRequest request,
            List<ScriptLexeme> sourceLexemes,
            ScriptDialect dialect
    ) {
        if (request.text().isBlank()) {
            return ScriptParseResult.empty();
        }
        List<ScriptLexeme> lexemes = sourceLexemes.stream()
                .filter(token -> token.startOffset() >= 0)
                .sorted(Comparator.comparingInt(ScriptLexeme::startOffset))
                .toList();
        List<Slice> markedSlices = markedObjectSlices(request.text(), request.sourceFile(), lexemes);
        List<Slice> slices = markedSlices.isEmpty() ? switch (dialect) {
            case MYSQL -> splitMysql(request.text(), lexemes);
            case POSTGRESQL -> splitPostgres(request.text(), lexemes, 0, request.text().length());
            case ORACLE -> splitOracle(request.text(), lexemes);
            case SQLSERVER -> splitSqlServer(request.text(), lexemes);
            case COMMON -> splitCommon(request.text(), lexemes);
        } : normalizeMarkedSlices(request.text(), lexemes, markedSlices, dialect);
        LineIndex lines = LineIndex.of(request.text());
        List<String> localTempTables = localTempTables(lexemes);
        List<SqlStatementRecord> statements = new ArrayList<>();
        for (Slice slice : slices) {
            SqlStatementRecord statement = statement(request, lexemes, lines, slice, localTempTables);
            if (statement != null) {
                statements.add(statement);
            }
        }
        return new ScriptParseResult(statements, List.of());
    }

    private List<Slice> normalizeMarkedSlices(
            String text,
            List<ScriptLexeme> lexemes,
            List<Slice> slices,
            ScriptDialect dialect
    ) {
        List<Slice> result = new ArrayList<>();
        for (Slice slice : slices) {
            if (dialect == ScriptDialect.MYSQL) {
                List<Slice> framed = splitMarkedMysqlSlice(text, lexemes, slice);
                if (!framed.isEmpty()) {
                    result.addAll(framed);
                    continue;
                }
            }
            List<ScriptLexeme> significant = lexemes.stream()
                    .filter(token -> token.startOffset() >= slice.startOffset()
                            && token.endOffset() <= slice.endOffset())
                    .filter(token -> token.kind() != ScriptLexemeKind.WHITESPACE
                            && token.kind() != ScriptLexemeKind.NEWLINE
                            && token.kind() != ScriptLexemeKind.COMMENT)
                    .toList();
            if (significant.isEmpty()) {
                result.add(slice);
                continue;
            }
            ScriptLexeme last = significant.get(significant.size() - 1);
            if (dialect == ScriptDialect.MYSQL && "$$".equals(last.text())) {
                result.add(new Slice(slice.startOffset(), last.startOffset(), true, slice.explicitSource()));
                continue;
            }
            if (dialect == ScriptDialect.MYSQL && significant.size() >= 2) {
                ScriptLexeme previous = significant.get(significant.size() - 2);
                if ("/".equals(previous.text()) && "/".equals(last.text())
                        && previous.endOffset() == last.startOffset()) {
                    result.add(new Slice(slice.startOffset(), previous.startOffset(), true, slice.explicitSource()));
                    continue;
                }
            }
            if (dialect == ScriptDialect.ORACLE && "/".equals(last.text()) && onlyTokenOnLine(text, last)) {
                result.add(new Slice(slice.startOffset(), lineStart(text, last.startOffset()), false,
                        slice.explicitSource()));
                continue;
            }
            result.add(slice);
        }
        return result;
    }

    private List<Slice> splitMarkedMysqlSlice(
            String text,
            List<ScriptLexeme> lexemes,
            Slice markedSlice
    ) {
        String delimiter = mysqlDelimiterAt(text, lexemes, markedSlice.startOffset());
        if (";".equals(delimiter)) {
            return splitMarkedMysqlCustomTerminator(lexemes, markedSlice);
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

    private List<Slice> splitMarkedMysqlCustomTerminator(
            List<ScriptLexeme> lexemes,
            Slice markedSlice
    ) {
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

    private String mysqlDelimiterAt(String text, List<ScriptLexeme> lexemes, int offset) {
        String delimiter = ";";
        for (DelimiterDirective directive : mysqlDelimiterDirectives(text, lexemes)) {
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

    private List<Slice> markedObjectSlices(String text, String sourceFile, List<ScriptLexeme> lexemes) {
        List<Slice> result = new ArrayList<>();
        ScriptLexeme opening = null;
        String explicitSource = "";
        for (ScriptLexeme token : lexemes) {
            if (token.kind() != ScriptLexemeKind.COMMENT) {
                continue;
            }
            String trimmed = token.text().trim();
            String prefix = "-- relation-detector-fixture-source:";
            if (startsWithIgnoreCase(trimmed, prefix)) {
                if (opening != null) {
                    throw new IllegalArgumentException("Nested fixture source marker at line " + token.line());
                }
                opening = token;
                explicitSource = trimmed.substring(prefix.length()).trim();
                continue;
            }
            if (equalsIgnoreCase(trimmed, "-- relation-detector-fixture-end")) {
                if (opening == null) {
                    throw new IllegalArgumentException("Fixture end without source marker at line " + token.line());
                }
                result.add(new Slice(includeFollowingNewline(text, lineEnd(text, opening.endOffset())),
                        lineStart(text, token.startOffset()), false, explicitSource));
                opening = null;
                explicitSource = "";
            }
        }
        if (opening != null) {
            throw new IllegalArgumentException("Missing relation-detector-fixture-end for "
                    + explicitSource + " in " + sourceFile);
        }
        return result;
    }

    private List<Slice> splitMysql(String text, List<ScriptLexeme> lexemes) {
        List<DelimiterDirective> directives = mysqlDelimiterDirectives(text, lexemes);
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

    private List<DelimiterDirective> mysqlDelimiterDirectives(String text, List<ScriptLexeme> lexemes) {
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

    private List<Slice> splitPostgres(String text, List<ScriptLexeme> lexemes, int start, int end) {
        List<Slice> result = new ArrayList<>();
        int statementStart = start;
        String dollarTag = null;
        for (ScriptLexeme token : lexemes) {
            if (token.startOffset() < start || token.startOffset() >= end) {
                continue;
            }
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
        if (statementStart < end) {
            result.add(new Slice(statementStart, end, false));
        }
        return result;
    }

    private List<Slice> splitOracle(String text, List<ScriptLexeme> lexemes) {
        List<Slice> result = new ArrayList<>();
        int segmentStart = 0;
        for (ScriptLexeme token : lexemes) {
            if (token.kind() != ScriptLexemeKind.SYMBOL || !"/".equals(token.text())
                    || !onlyTokenOnLine(text, token)) {
                continue;
            }
            appendOracleSegment(result, lexemes, segmentStart, token.startOffset());
            segmentStart = includeFollowingNewline(text, lineEnd(text, token.endOffset()));
        }
        appendOracleSegment(result, lexemes, segmentStart, text.length());
        return result;
    }

    private void appendOracleSegment(List<Slice> result, List<ScriptLexeme> lexemes, int start, int end) {
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

    private List<Slice> splitCommon(String text, List<ScriptLexeme> lexemes) {
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
                if (!isCommonCompoundObject(descriptor)) {
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

    private boolean isCommonCompoundObject(ObjectDescriptor descriptor) {
        return descriptor.sourceType() == StatementSourceType.PROCEDURE
                || descriptor.sourceType() == StatementSourceType.FUNCTION
                || descriptor.sourceType() == StatementSourceType.TRIGGER;
    }

    private List<Slice> splitSqlServer(String text, List<ScriptLexeme> lexemes) {
        List<Slice> result = new ArrayList<>();
        int batchStart = 0;
        for (ScriptLexeme token : lexemes) {
            if (token.kind() != ScriptLexemeKind.GO
                    || !onlyTokenOnLine(text, token)) {
                continue;
            }
            appendSqlServerBatch(result, lexemes, batchStart, token.startOffset());
            batchStart = includeFollowingNewline(text, lineEnd(text, token.endOffset()));
        }
        appendSqlServerBatch(result, lexemes, batchStart, text.length());
        return result;
    }

    private void appendSqlServerBatch(List<Slice> result, List<ScriptLexeme> lexemes, int start, int end) {
        ObjectDescriptor descriptor = objectDescriptor(lexemes, start, end);
        if (descriptor.sourceType() == StatementSourceType.PROCEDURE
                || descriptor.sourceType() == StatementSourceType.FUNCTION
                || descriptor.sourceType() == StatementSourceType.TRIGGER) {
            result.add(new Slice(start, end, false));
        } else {
            result.addAll(splitSemicolon(lexemes, start, end));
        }
    }

    private List<Slice> splitSemicolon(List<ScriptLexeme> lexemes, int start, int end) {
        List<Slice> result = new ArrayList<>();
        int statementStart = start;
        for (ScriptLexeme token : lexemes) {
            if (token.kind() == ScriptLexemeKind.SEMICOLON
                    && token.startOffset() >= start && token.startOffset() < end) {
                result.add(new Slice(statementStart, token.endOffset(), false));
                statementStart = token.endOffset();
            }
        }
        if (statementStart < end) {
            result.add(new Slice(statementStart, end, false));
        }
        return result;
    }

    private SqlStatementRecord statement(
            ScriptParseRequest request,
            List<ScriptLexeme> lexemes,
            LineIndex lines,
            Slice slice,
            List<String> localTempTables
    ) {
        int start = trimStart(request.text(), slice.startOffset(), slice.endOffset());
        int end = trimEnd(request.text(), start, slice.endOffset());
        if (start >= end || onlyCommentsAndWhitespace(lexemes, start, end)) {
            return null;
        }
        String sql = request.text().substring(start, end);
        if (slice.appendSemicolon()) {
            sql += ";";
        }
        ObjectDescriptor descriptor = objectDescriptor(lexemes, start, end);
        StatementSourceType sourceType = descriptor.isObject()
                ? descriptor.sourceType()
                : markerSourceType(slice.explicitSource(), request.defaultSourceType());
        String normalizedFile = SourceNameNormalizer.normalize(request.sourceFile());
        long startLine = lines.lineAt(start);
        long endLine = lines.lineAt(end - 1);
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (!localTempTables.isEmpty()) {
            attributes.put("localTempTables", localTempTables);
        }
        attributes.put("sourceFile", normalizedFile);
        attributes.put("sourceFileLineCount", lines.lineCount());
        attributes.put("sourceObjectType", semanticObjectType(sourceType));
        String sourceName = normalizedFile;
        String explicitBlock = markerBlockId(slice.explicitSource());
        String objectName = descriptor.isObject() ? descriptor.objectName() : markerObjectName(explicitBlock);
        if (!objectName.isBlank() || !explicitBlock.isBlank()) {
            sourceName = slice.explicitSource().isBlank() ? sourceName(sourceType, objectName) : slice.explicitSource();
            String blockId = explicitBlock.isBlank() ? objectName : explicitBlock;
            attributes.put("sourceBlockId", blockId);
            if (!objectName.isBlank()) {
                attributes.put("sourceObjectName", objectName);
            }
            attributes.put("sourceStatementId", blockId);
        } else {
            attributes.put("sourceStatementId", normalizedFile + ":" + startLine + "-" + endLine);
        }
        return new SqlStatementRecord(sql, sourceType, sourceName, startLine, endLine, attributes);
    }

    private StatementSourceType markerSourceType(String source, StatementSourceType fallback) {
        if (startsWithIgnoreCase(source, "TRIGGER:")) return StatementSourceType.TRIGGER;
        if (startsWithIgnoreCase(source, "ROUTINE:") || startsWithIgnoreCase(source, "PROCEDURE:")) {
            return StatementSourceType.PROCEDURE;
        }
        return fallback;
    }

    private String markerBlockId(String source) {
        if (source == null) return "";
        for (String prefix : List.of("ROUTINE:", "TRIGGER:", "PROCEDURE:")) {
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
                    .contains(namespace)) {
                value = value.substring(separator + 1);
            }
        }
        return value;
    }

    private boolean onlyCommentsAndWhitespace(List<ScriptLexeme> lexemes, int start, int end) {
        return lexemes.stream()
                .filter(token -> token.endOffset() > start && token.startOffset() < end)
                .allMatch(token -> token.kind() == ScriptLexemeKind.COMMENT
                        || token.kind() == ScriptLexemeKind.WHITESPACE
                        || token.kind() == ScriptLexemeKind.NEWLINE);
    }

    private ObjectDescriptor objectDescriptor(List<ScriptLexeme> lexemes, int start, int end) {
        List<ScriptLexeme> significant = lexemes.stream()
                .filter(token -> token.startOffset() >= start && token.startOffset() < end)
                .filter(token -> token.kind() != ScriptLexemeKind.COMMENT
                        && token.kind() != ScriptLexemeKind.WHITESPACE
                        && token.kind() != ScriptLexemeKind.NEWLINE)
                .toList();
        int cursor = 0;
        if (!kindAt(significant, cursor, ScriptLexemeKind.CREATE)) {
            return ObjectDescriptor.none();
        }
        cursor++;
        if (kindAt(significant, cursor, ScriptLexemeKind.OR)
                && (kindAt(significant, cursor + 1, ScriptLexemeKind.REPLACE)
                || kindAt(significant, cursor + 1, ScriptLexemeKind.ALTER))) {
            cursor += 2;
        }
        while (kindAt(significant, cursor, ScriptLexemeKind.EDITIONABLE)
                || kindAt(significant, cursor, ScriptLexemeKind.NONEDITIONABLE)) {
            cursor++;
        }
        StatementSourceType type;
        if (kindAt(significant, cursor, ScriptLexemeKind.MATERIALIZED)
                && kindAt(significant, cursor + 1, ScriptLexemeKind.VIEW)) {
            type = StatementSourceType.MATERIALIZED_VIEW;
            cursor += 2;
        } else if (kindAt(significant, cursor, ScriptLexemeKind.PACKAGE)
                && kindAt(significant, cursor + 1, ScriptLexemeKind.BODY)) {
            type = StatementSourceType.PACKAGE_BODY;
            cursor += 2;
        } else {
            type = objectType(significant, cursor);
            if (type == null) {
                return ObjectDescriptor.none();
            }
            cursor++;
        }
        String objectName = qualifiedIdentifier(significant, cursor);
        if (objectName.isBlank()) {
            return ObjectDescriptor.none();
        }
        if (type == StatementSourceType.FUNCTION
                && containsKindSequence(significant, cursor + 1,
                ScriptLexemeKind.RETURNS, ScriptLexemeKind.TRIGGER)) {
            type = StatementSourceType.TRIGGER;
        }
        return new ObjectDescriptor(type, objectName);
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
        if (!identifierToken(tokens, index)) {
            return "";
        }
        StringBuilder value = new StringBuilder(unquote(tokens.get(index).text()));
        int cursor = index + 1;
        while (cursor + 1 < tokens.size()
                && tokens.get(cursor).kind() == ScriptLexemeKind.DOT
                && identifierToken(tokens, cursor + 1)) {
            value.append('.').append(unquote(tokens.get(cursor + 1).text()));
            cursor += 2;
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
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '[' && last == ']') || (first == '`' && last == '`')
                    || (first == '"' && last == '"')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private boolean containsKindSequence(
            List<ScriptLexeme> tokens,
            int start,
            ScriptLexemeKind first,
            ScriptLexemeKind second
    ) {
        for (int index = Math.max(0, start); index + 1 < tokens.size(); index++) {
            if (kindAt(tokens, index, first) && kindAt(tokens, index + 1, second)) {
                return true;
            }
        }
        return false;
    }

    private boolean kindAt(List<ScriptLexeme> tokens, int index, ScriptLexemeKind kind) {
        return index >= 0 && index < tokens.size()
                && tokens.get(index).kind() == kind;
    }

    private List<String> localTempTables(List<ScriptLexeme> lexemes) {
        List<ScriptLexeme> significant = lexemes.stream()
                .filter(token -> token.kind() != ScriptLexemeKind.COMMENT
                        && token.kind() != ScriptLexemeKind.WHITESPACE
                        && token.kind() != ScriptLexemeKind.NEWLINE)
                .toList();
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        for (int index = 0; index < significant.size(); index++) {
            if (!kindAt(significant, index, ScriptLexemeKind.CREATE)
                    || !(kindAt(significant, index + 1, ScriptLexemeKind.TEMPORARY)
                    || kindAt(significant, index + 1, ScriptLexemeKind.TEMP))
                    || !kindAt(significant, index + 2, ScriptLexemeKind.TABLE)) {
                continue;
            }
            int nameIndex = index + 3;
            if (kindAt(significant, nameIndex, ScriptLexemeKind.IF)
                    && kindAt(significant, nameIndex + 1, ScriptLexemeKind.NOT)
                    && kindAt(significant, nameIndex + 2, ScriptLexemeKind.EXISTS)) {
                nameIndex += 3;
            }
            String table = qualifiedIdentifier(significant, nameIndex);
            if (!table.isBlank()) result.add(table);
        }
        return List.copyOf(result);
    }

    private String semanticObjectType(StatementSourceType type) {
        return switch (type) {
            case PROCEDURE, FUNCTION, PACKAGE, PACKAGE_BODY, EVENT -> "ROUTINE";
            case TRIGGER -> "TRIGGER";
            case VIEW, MATERIALIZED_VIEW -> "QUERY";
            case DDL_FILE -> "DDL";
            case RULE -> "RULE";
            case NATIVE_LOG, PLAIN_SQL, MIGRATION -> "SQL_WRITE";
        };
    }

    private String sourceName(StatementSourceType type, String objectName) {
        return switch (type) {
            case TRIGGER -> "TRIGGER:" + objectName;
            case PROCEDURE, FUNCTION, PACKAGE, PACKAGE_BODY, EVENT -> "ROUTINE:" + objectName;
            default -> objectName;
        };
    }

    private boolean firstSignificantOnLine(String text, int offset) {
        for (int cursor = lineStart(text, offset); cursor < offset; cursor++) {
            if (!Character.isWhitespace(text.charAt(cursor))) return false;
        }
        return true;
    }

    private boolean onlyTokenOnLine(String text, ScriptLexeme token) {
        for (int cursor = lineStart(text, token.startOffset()); cursor < token.startOffset(); cursor++) {
            if (!Character.isWhitespace(text.charAt(cursor))) return false;
        }
        int end = lineEnd(text, token.endOffset());
        for (int cursor = token.endOffset(); cursor < end; cursor++) {
            if (!Character.isWhitespace(text.charAt(cursor))) return false;
        }
        return true;
    }

    private int lineStart(String text, int offset) {
        int cursor = Math.min(offset, text.length());
        while (cursor > 0 && text.charAt(cursor - 1) != '\n' && text.charAt(cursor - 1) != '\r') cursor--;
        return cursor;
    }

    private int lineEnd(String text, int offset) {
        int cursor = Math.min(offset, text.length());
        while (cursor < text.length() && text.charAt(cursor) != '\n' && text.charAt(cursor) != '\r') cursor++;
        return cursor;
    }

    private int includeFollowingNewline(String text, int offset) {
        int cursor = offset;
        if (cursor < text.length() && text.charAt(cursor) == '\r') cursor++;
        if (cursor < text.length() && text.charAt(cursor) == '\n') cursor++;
        return cursor;
    }

    private int skipWhitespace(String text, int start, int end) {
        int cursor = start;
        while (cursor < end && Character.isWhitespace(text.charAt(cursor))) cursor++;
        return cursor;
    }

    private int trimWhitespaceEnd(String text, int start, int end) {
        int cursor = end;
        while (cursor > start && Character.isWhitespace(text.charAt(cursor - 1))) cursor--;
        return cursor;
    }

    private int trimStart(String text, int start, int end) {
        return skipWhitespace(text, start, end);
    }

    private int trimEnd(String text, int start, int end) {
        return trimWhitespaceEnd(text, start, end);
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && left.equalsIgnoreCase(right);
    }

    private boolean startsWithIgnoreCase(String value, String prefix) {
        return value != null && prefix != null && value.length() >= prefix.length()
                && value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private record Slice(int startOffset, int endOffset, boolean appendSemicolon, String explicitSource) {
        private Slice(int startOffset, int endOffset, boolean appendSemicolon) {
            this(startOffset, endOffset, appendSemicolon, "");
        }
    }
    private record DelimiterDirective(int startOffset, int endOffset, String delimiter) {}
    private record ObjectDescriptor(StatementSourceType sourceType, String objectName) {
        static ObjectDescriptor none() { return new ObjectDescriptor(null, ""); }
        boolean isObject() { return sourceType != null && objectName != null && !objectName.isBlank(); }
    }

    private record LineIndex(int[] lineStarts, int textLength) {
        static LineIndex of(String text) {
            List<Integer> starts = new ArrayList<>();
            starts.add(0);
            for (int offset = 0; offset < text.length(); offset++) {
                char current = text.charAt(offset);
                if (current == '\r') {
                    if (offset + 1 < text.length() && text.charAt(offset + 1) == '\n') offset++;
                    starts.add(offset + 1);
                } else if (current == '\n') {
                    starts.add(offset + 1);
                }
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
