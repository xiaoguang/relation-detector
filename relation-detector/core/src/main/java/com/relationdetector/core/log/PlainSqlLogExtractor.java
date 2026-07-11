package com.relationdetector.core.log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.core.diagnostics.DiagnosticWarnings;

/** Extracts semicolon-separated statements from a cleaned SQL text file. */
public final class PlainSqlLogExtractor {
    public Stream<SqlStatementRecord> extract(Path file, StatementSourceType sourceType) {
        return extract(file, sourceType, warning -> {
        });
    }

    /**
     * Splits a plain SQL/procedure file into parser-ready statements.
     *
     * <p>Call relationship:
     * ScanEngine.scan() -> PlainSqlLogExtractor.extract(...) ->
     * ScanEngine.safeParseStatement(...).
     *
     * <p>The loop preserves the source file and approximate line range on each
     * statement. If file IO fails, this method records a diagnostic warning with
     * the file path; it returns an empty stream so other scan sources can still
     * run.
     */
    public Stream<SqlStatementRecord> extract(
            Path file,
            StatementSourceType sourceType,
            Consumer<WarningMessage> warnings
    ) {
        try {
            String text = Files.readString(file);
            LineIndex lineIndex = LineIndex.of(text);
            List<String> localTempTables = localTempTablesIn(text);
            List<SqlStatementRecord> statements = new ArrayList<>();
            for (StatementSlice slice : splitSqlStatements(text)) {
                int startOffset = trimStart(text, slice.startOffset(), slice.endOffset());
                int endOffset = trimEnd(text, startOffset, slice.endOffset());
                if (startOffset < endOffset) {
                    String sql = text.substring(startOffset, endOffset);
                    int startLine = lineIndex.lineAt(startOffset);
                    int endLine = lineIndex.lineAt(endOffset - 1);
                    Map<String, Object> attributes = new LinkedHashMap<>();
                    if (!localTempTables.isEmpty()) {
                        attributes.put("localTempTables", localTempTables);
                    }
                    String normalizedFile = SourceNameNormalizer.normalize(file);
                    attributes.put("sourceFile", normalizedFile);
                    attributes.put("sourceStatementId", normalizedFile + ":" + startLine + "-" + endLine);
                    attributes.put("sourceObjectType", "SQL_WRITE");
                    statements.add(new SqlStatementRecord(sql, sourceType, normalizedFile, startLine,
                            endLine, attributes));
                }
            }
            return statements.stream();
        } catch (IOException ex) {
            warnings.accept(DiagnosticWarnings.sqlFileExtractFailed(file, ex));
            return Stream.empty();
        }
    }

    private int trimStart(String text, int startOffset, int endOffset) {
        int offset = startOffset;
        while (offset < endOffset && Character.isWhitespace(text.charAt(offset))) {
            offset++;
        }
        return offset;
    }

    private int trimEnd(String text, int startOffset, int endOffset) {
        int offset = endOffset;
        while (offset > startOffset && Character.isWhitespace(text.charAt(offset - 1))) {
            offset--;
        }
        return offset;
    }

    /**
     * Splits SQL text on statement semicolons while preserving semicolons that
     * are part of string literals or comments.
     *
     * <p>Complete PostgreSQL example that must remain one statement:
     *
     * <pre>{@code
     * SELECT STRING_AGG(category, '; ' ORDER BY category) FROM transactions;
     * }</pre>
     */
    private List<StatementSlice> splitSqlStatements(String text) {
        List<StatementSlice> statements = new ArrayList<>();
        int statementStart = 0;
        char quote = 0;
        boolean lineComment = false;
        boolean blockComment = false;
        String dollarQuote = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (dollarQuote != null) {
                if (c == '$' && text.startsWith(dollarQuote, i)) {
                    i += dollarQuote.length() - 1;
                    dollarQuote = null;
                }
                continue;
            }
            if (lineComment) {
                if (c == '\n' || c == '\r') {
                    lineComment = false;
                }
                continue;
            }
            if (blockComment) {
                if (c == '*' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                    i++;
                    blockComment = false;
                }
                continue;
            }
            if (quote != 0) {
                if (c == quote) {
                    if (quote == '\'' && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                        i++;
                        continue;
                    }
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                continue;
            }
            if (c == '$') {
                String delimiter = dollarQuoteDelimiterAt(text, i);
                if (delimiter != null) {
                    i += delimiter.length() - 1;
                    dollarQuote = delimiter;
                    continue;
                }
            }
            if (c == '-' && i + 1 < text.length() && text.charAt(i + 1) == '-') {
                i++;
                lineComment = true;
                continue;
            }
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                i++;
                blockComment = true;
                continue;
            }
            if (c == ';') {
                statements.add(new StatementSlice(statementStart, i + 1));
                statementStart = i + 1;
            }
        }
        if (statementStart < text.length()) {
            statements.add(new StatementSlice(statementStart, text.length()));
        }
        return statements;
    }

    private record StatementSlice(int startOffset, int endOffset) {
    }

    private record LineIndex(int[] lineStarts) {
        static LineIndex of(String text) {
            List<Integer> starts = new ArrayList<>();
            starts.add(0);
            for (int offset = 0; offset < text.length(); offset++) {
                char current = text.charAt(offset);
                if (current == '\r') {
                    if (offset + 1 < text.length() && text.charAt(offset + 1) == '\n') {
                        offset++;
                    }
                    starts.add(offset + 1);
                } else if (current == '\n') {
                    starts.add(offset + 1);
                }
            }
            return new LineIndex(starts.stream().mapToInt(Integer::intValue).toArray());
        }

        int lineAt(int offset) {
            int position = java.util.Arrays.binarySearch(lineStarts, offset);
            return position >= 0 ? position + 1 : -position - 1;
        }
    }

    private static String dollarQuoteDelimiterAt(String text, int index) {
        if (index >= text.length() || text.charAt(index) != '$') {
            return null;
        }
        int cursor = index + 1;
        if (cursor < text.length() && text.charAt(cursor) == '$') {
            return "$$";
        }
        if (cursor >= text.length() || !isDollarQuoteTagStart(text.charAt(cursor))) {
            return null;
        }
        cursor++;
        while (cursor < text.length() && isDollarQuoteTagPart(text.charAt(cursor))) {
            cursor++;
        }
        if (cursor < text.length() && text.charAt(cursor) == '$') {
            return text.substring(index, cursor + 1);
        }
        return null;
    }

    private static boolean isDollarQuoteTagStart(char character) {
        return character == '_' || Character.isLetter(character);
    }

    private static boolean isDollarQuoteTagPart(char character) {
        return character == '_' || Character.isLetterOrDigit(character);
    }

    public static List<String> localTempTablesIn(String text) {
        List<String> tokens = lexicalTokens(text);
        LinkedHashSet<String> tables = new LinkedHashSet<>();
        for (int index = 0; index < tokens.size(); index++) {
            if (!isToken(tokens, index, "create")
                    || !isToken(tokens, index + 1, "temporary")
                    || !isToken(tokens, index + 2, "table")) {
                continue;
            }
            int tableIndex = index + 3;
            if (isToken(tokens, tableIndex, "if")
                    && isToken(tokens, tableIndex + 1, "not")
                    && isToken(tokens, tableIndex + 2, "exists")) {
                tableIndex += 3;
            }
            if (tableIndex < tokens.size()) {
                tables.add(tokens.get(tableIndex));
            }
        }
        return List.copyOf(tables);
    }

    private static boolean isToken(List<String> tokens, int index, String expected) {
        return index >= 0 && index < tokens.size()
                && tokens.get(index).equalsIgnoreCase(expected);
    }

    private static List<String> lexicalTokens(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (lineComment) {
                if (c == '\n' || c == '\r') {
                    lineComment = false;
                }
                continue;
            }
            if (blockComment) {
                if (c == '*' && index + 1 < text.length() && text.charAt(index + 1) == '/') {
                    index++;
                    blockComment = false;
                }
                continue;
            }
            if (quote != 0) {
                if (c == quote) {
                    if (quote == '\'' && index + 1 < text.length() && text.charAt(index + 1) == '\'') {
                        index++;
                        continue;
                    }
                    quote = 0;
                }
                continue;
            }
            if (c == '-' && index + 1 < text.length() && text.charAt(index + 1) == '-') {
                flushToken(tokens, current);
                index++;
                lineComment = true;
                continue;
            }
            if (c == '/' && index + 1 < text.length() && text.charAt(index + 1) == '*') {
                flushToken(tokens, current);
                index++;
                blockComment = true;
                continue;
            }
            if (c == '\'' || c == '"') {
                flushToken(tokens, current);
                quote = c;
                continue;
            }
            if (c == '`') {
                flushToken(tokens, current);
                StringBuilder quoted = new StringBuilder();
                while (++index < text.length()) {
                    char quotedChar = text.charAt(index);
                    if (quotedChar == '`') {
                        break;
                    }
                    quoted.append(quotedChar);
                }
                if (!quoted.isEmpty()) {
                    tokens.add(quoted.toString().toLowerCase(Locale.ROOT));
                }
                continue;
            }
            if (Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.') {
                current.append(c);
                continue;
            }
            flushToken(tokens, current);
        }
        flushToken(tokens, current);
        return tokens.stream()
                .map(token -> token.toLowerCase(Locale.ROOT))
                .toList();
    }

    private static void flushToken(List<String> tokens, StringBuilder current) {
        if (current.isEmpty()) {
            return;
        }
        tokens.add(current.toString());
        current.setLength(0);
    }
}
