package com.relationdetector.core.log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.diagnostics.DiagnosticWarnings;

/**
 * Extracts parser-ready database object definitions from SQL files.
 *
 * <p>Unlike native logs, object files often contain procedure/trigger bodies
 * with internal semicolons. The extractor therefore keeps marked object blocks
 * or whole files intact instead of using semicolon splitting.</p>
 */
public final class ObjectSqlFileExtractor {
    public Stream<SqlStatementRecord> extract(
            Path file,
            StatementSourceType sourceType,
            DatabaseType databaseType,
            Consumer<WarningMessage> warnings
    ) {
        try {
            String text = Files.readString(file);
            return extract(text, sourceType, file.toString(), databaseType).stream();
        } catch (IOException ex) {
            warnings.accept(DiagnosticWarnings.sqlFileExtractFailed(file, ex));
            return Stream.empty();
        }
    }

    public List<SqlStatementRecord> extract(
            String text,
            StatementSourceType sourceType,
            String sourceFile,
            DatabaseType databaseType
    ) {
        List<String> localTempTables = PlainSqlLogExtractor.localTempTablesIn(text);
        List<SqlStatementRecord> marked = parseMarkedObjectBlocks(text, sourceType, sourceFile, localTempTables);
        if (!marked.isEmpty()) {
            return marked;
        }
        if (!text.isBlank() && databaseType == DatabaseType.ORACLE) {
            List<SqlStatementRecord> oracleObjects = parseUnmarkedOracleObjectBlocks(
                    text,
                    sourceType,
                    sourceFile,
                    localTempTables);
            if (!oracleObjects.isEmpty()) {
                return oracleObjects;
            }
        }
        if (text.isBlank()) {
            return List.of();
        }
        String[] lines = text.split("\\R", -1);
        return List.of(new SqlStatementRecord(text.strip(), sourceType, sourceFile, 1, lines.length,
                attributes(localTempTables)));
    }

    private static List<SqlStatementRecord> parseMarkedObjectBlocks(
            String text,
            StatementSourceType sourceType,
            String sourceFile,
            List<String> localTempTables
    ) {
        List<SqlStatementRecord> statements = new ArrayList<>();
        String[] lines = text.split("\\R", -1);
        String currentSource = null;
        StringBuilder currentSql = new StringBuilder();
        long startLine = 0;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String trimmed = line.trim();
            if (trimmed.startsWith("-- relation-detector-fixture-source:")) {
                if (currentSource != null) {
                    throw new IllegalArgumentException(
                            "Missing relation-detector-fixture-end before line " + (index + 1) + " in " + sourceFile);
                }
                currentSource = trimmed.substring("-- relation-detector-fixture-source:".length()).trim();
                currentSql.setLength(0);
                startLine = index + 2L;
                continue;
            }
            if (trimmed.equals("-- relation-detector-fixture-end")) {
                if (currentSource == null) {
                    throw new IllegalArgumentException(
                            "Unexpected relation-detector-fixture-end at line " + (index + 1) + " in " + sourceFile);
                }
                String sql = currentSql.toString().strip();
                if (!sql.isBlank()) {
                    statements.add(new SqlStatementRecord(sql, sourceType, currentSource,
                            startLine, index, attributes(localTempTables)));
                }
                currentSource = null;
                currentSql.setLength(0);
                continue;
            }
            if (currentSource != null) {
                currentSql.append(line).append('\n');
            }
        }
        if (currentSource != null) {
            throw new IllegalArgumentException(
                    "Missing relation-detector-fixture-end for " + currentSource + " in " + sourceFile);
        }
        return List.copyOf(statements);
    }

    private static List<SqlStatementRecord> parseUnmarkedOracleObjectBlocks(
            String text,
            StatementSourceType sourceType,
            String sourceFile,
            List<String> localTempTables
    ) {
        List<SqlStatementRecord> statements = new ArrayList<>();
        String[] lines = text.split("\\R", -1);
        StringBuilder currentSql = new StringBuilder();
        String currentName = "";
        long startLine = 0;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String trimmed = line.trim();
            if (currentSql.isEmpty() && startsOracleObject(trimmed)) {
                currentName = oracleObjectName(trimmed);
                startLine = index + 1L;
                currentSql.append(line).append('\n');
                continue;
            }
            if (!currentSql.isEmpty()) {
                currentSql.append(line).append('\n');
                if (trimmed.equals("/")) {
                    String sourceName = currentName.isBlank() ? sourceFile : sourceFile + "#" + currentName;
                    statements.add(new SqlStatementRecord(currentSql.toString().strip(), sourceType, sourceName,
                            startLine, index + 1L, attributes(localTempTables)));
                    currentSql.setLength(0);
                    currentName = "";
                }
            }
        }
        return statements;
    }

    private static Map<String, Object> attributes(List<String> localTempTables) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (!localTempTables.isEmpty()) {
            attributes.put("localTempTables", localTempTables);
        }
        return attributes;
    }

    private static boolean startsOracleObject(String trimmed) {
        String upper = trimmed.toUpperCase(Locale.ROOT);
        return upper.startsWith("CREATE OR REPLACE PROCEDURE ")
                || upper.startsWith("CREATE OR REPLACE FUNCTION ")
                || upper.startsWith("CREATE OR REPLACE TRIGGER ")
                || upper.startsWith("CREATE OR REPLACE PACKAGE ");
    }

    private static String oracleObjectName(String trimmed) {
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 5) {
            return "";
        }
        int nameIndex = parts.length > 5 && parts[3].equalsIgnoreCase("PACKAGE")
                && parts[4].equalsIgnoreCase("BODY")
                        ? 5
                        : 4;
        return parts[nameIndex].replace("\"", "").replace("(", "");
    }
}
