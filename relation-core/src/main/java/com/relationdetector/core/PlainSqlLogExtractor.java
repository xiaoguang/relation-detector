package com.relationdetector.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.WarningMessage;
import com.relationdetector.api.Enums.StatementSourceType;

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
            for (String part : text.split(";")) {
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
}
