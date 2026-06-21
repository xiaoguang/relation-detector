package com.relationdetector.core.log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
            List<SqlStatementRecord> statements = new ArrayList<>();
            int line = 1;
            for (String part : splitSqlStatements(text)) {
                String sql = part.trim();
                if (!sql.isBlank()) {
                    statements.add(new SqlStatementRecord(sql, sourceType, file.toString(), line, line + countLines(part), java.util.Map.of()));
                }
                line += countLines(part);
            }
            return statements.stream();
        } catch (IOException ex) {
            warnings.accept(DiagnosticWarnings.sqlFileExtractFailed(file, ex));
            return Stream.empty();
        }
    }

    private int countLines(String text) {
        return (int) text.chars().filter(ch -> ch == '\n').count() + 1;
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
    private List<String> splitSqlStatements(String text) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            current.append(c);
            if (lineComment) {
                if (c == '\n' || c == '\r') {
                    lineComment = false;
                }
                continue;
            }
            if (blockComment) {
                if (c == '*' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                    current.append(text.charAt(++i));
                    blockComment = false;
                }
                continue;
            }
            if (quote != 0) {
                if (c == quote) {
                    if (quote == '\'' && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                        current.append(text.charAt(++i));
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
            if (c == '-' && i + 1 < text.length() && text.charAt(i + 1) == '-') {
                current.append(text.charAt(++i));
                lineComment = true;
                continue;
            }
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                current.append(text.charAt(++i));
                blockComment = true;
                continue;
            }
            if (c == ';') {
                statements.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            statements.add(current.toString());
        }
        return statements;
    }
}
