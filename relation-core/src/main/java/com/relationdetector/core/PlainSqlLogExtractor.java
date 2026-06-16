package com.relationdetector.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.Enums.StatementSourceType;

/** Extracts semicolon-separated statements from a cleaned SQL text file. */
public final class PlainSqlLogExtractor {
    public Stream<SqlStatementRecord> extract(Path file, StatementSourceType sourceType) {
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
            return Stream.empty();
        }
    }

    private int countLines(String text) {
        return (int) text.chars().filter(ch -> ch == '\n').count() + 1;
    }
}
