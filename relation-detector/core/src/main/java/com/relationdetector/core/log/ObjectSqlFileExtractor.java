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
        List<SqlStatementRecord> objectBlocks = parseUnmarkedObjectBlocks(
                text,
                sourceType,
                sourceFile,
                localTempTables);
        if (!objectBlocks.isEmpty()) {
            return objectBlocks;
        }
        if (text.isBlank()) {
            return List.of();
        }
        String[] lines = text.split("\\R", -1);
        StatementSourceType inferred = inferSourceType(sourceType, "", text.strip());
        return List.of(new SqlStatementRecord(text.strip(), inferred, sourceName(inferred, "", sourceFile), 1,
                lines.length, attributes(localTempTables, sourceFile, "", inferred, text.strip(), 1, lines.length)));
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
                    StatementSourceType inferred = inferSourceType(sourceType, currentSource, sql);
                    statements.add(new SqlStatementRecord(sql, inferred, currentSource,
                            startLine, index, attributes(localTempTables, sourceFile, currentSource, inferred, sql,
                                    startLine, index)));
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

    private static List<SqlStatementRecord> parseUnmarkedObjectBlocks(
            String text,
            StatementSourceType sourceType,
            String sourceFile,
            List<String> localTempTables
    ) {
        List<SqlStatementRecord> statements = new ArrayList<>();
        String[] lines = text.split("\\R", -1);
        String delimiter = ";";
        StringBuilder currentSql = new StringBuilder();
        long startLine = 0;
        StatementSourceType currentType = sourceType;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String trimmed = line.trim();
            if (currentSql.isEmpty()) {
                String delimiterValue = delimiterDirective(trimmed);
                if (!delimiterValue.isBlank()) {
                    delimiter = delimiterValue;
                    continue;
                }
                if (!startsObject(trimmed)) {
                    continue;
                }
                currentType = inferSourceType(sourceType, "", trimmed);
                startLine = index + 1L;
                currentSql.append(line).append('\n');
                if (endsObject(trimmed, delimiter, currentType, currentSql.toString())) {
                    addUnmarkedObjectStatement(statements, currentSql, sourceFile, localTempTables, sourceType,
                            currentType, startLine, index + 1L);
                    currentSql.setLength(0);
                }
                continue;
            }
            currentSql.append(line).append('\n');
            if (endsObject(trimmed, delimiter, currentType, currentSql.toString())) {
                addUnmarkedObjectStatement(statements, currentSql, sourceFile, localTempTables, sourceType,
                        currentType, startLine, index + 1L);
                currentSql.setLength(0);
            }
        }
        return List.copyOf(statements);
    }

    private static void addUnmarkedObjectStatement(
            List<SqlStatementRecord> statements,
            StringBuilder currentSql,
            String sourceFile,
            List<String> localTempTables,
            StatementSourceType fallbackType,
            StatementSourceType detectedType,
            long startLine,
            long endLine
    ) {
        String sql = currentSql.toString().strip();
        if (sql.isBlank()) {
            return;
        }
        StatementSourceType inferred = inferSourceType(detectedType == null ? fallbackType : detectedType, "", sql);
        String objectName = objectName(sql);
        String sourceName = sourceName(inferred, objectName, sourceFile);
        statements.add(new SqlStatementRecord(sql, inferred, sourceName, startLine, endLine,
                attributes(localTempTables, sourceFile, sourceName, inferred, sql, startLine, endLine)));
    }

    private static String delimiterDirective(String trimmed) {
        if (trimmed == null || trimmed.length() <= "DELIMITER ".length()) {
            return "";
        }
        if (!trimmed.regionMatches(true, 0, "DELIMITER ", 0, "DELIMITER ".length())) {
            return "";
        }
        return trimmed.substring("DELIMITER ".length()).trim();
    }

    private static boolean startsObject(String trimmed) {
        String upper = trimmed.toUpperCase(Locale.ROOT);
        return upper.startsWith("CREATE PROCEDURE ")
                || upper.startsWith("CREATE FUNCTION ")
                || upper.startsWith("CREATE TRIGGER ")
                || upper.startsWith("CREATE EVENT ")
                || upper.startsWith("CREATE VIEW ")
                || upper.startsWith("CREATE OR REPLACE PROCEDURE ")
                || upper.startsWith("CREATE OR REPLACE FUNCTION ")
                || upper.startsWith("CREATE OR REPLACE TRIGGER ")
                || upper.startsWith("CREATE OR REPLACE VIEW ")
                || upper.startsWith("CREATE OR ALTER PROCEDURE ")
                || upper.startsWith("CREATE OR ALTER FUNCTION ")
                || upper.startsWith("CREATE OR ALTER TRIGGER ");
    }

    private static boolean endsObject(
            String trimmed,
            String delimiter,
            StatementSourceType sourceType,
            String currentSql
    ) {
        if (trimmed == null || trimmed.isBlank()) {
            return false;
        }
        if (delimiter != null && !delimiter.isBlank() && !";".equals(delimiter)) {
            return trimmed.endsWith(delimiter);
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (sourceType == StatementSourceType.VIEW || sourceType == StatementSourceType.MATERIALIZED_VIEW) {
            return trimmed.endsWith(";");
        }
        if (currentSql != null && currentSql.contains("$$")) {
            return trimmed.equals("$$;")
                    || upper.endsWith("$$ LANGUAGE PLPGSQL;")
                    || upper.contains("$$ LANGUAGE PLPGSQL;");
        }
        return trimmed.equals("$$;")
                || upper.endsWith("$$ LANGUAGE PLPGSQL;")
                || upper.contains("$$ LANGUAGE PLPGSQL;")
                || upper.endsWith("END;");
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
                    StatementSourceType inferred = inferSourceType(sourceType, currentName, currentSql.toString());
                    statements.add(new SqlStatementRecord(currentSql.toString().strip(), inferred, sourceName,
                            startLine, index + 1L, attributes(localTempTables, sourceFile, currentName, inferred,
                                    currentSql.toString().strip(), startLine, index + 1L)));
                    currentSql.setLength(0);
                    currentName = "";
                }
            }
        }
        return statements;
    }

    private static Map<String, Object> attributes(
            List<String> localTempTables,
            String sourceFile,
            String sourceBlockId,
            StatementSourceType sourceType,
            String sql,
            long startLine,
            long endLine
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (!localTempTables.isEmpty()) {
            attributes.put("localTempTables", localTempTables);
        }
        String normalizedFile = normalizePath(sourceFile);
        if (!normalizedFile.isBlank()) {
            attributes.put("sourceFile", normalizedFile);
        }
        if (sourceBlockId != null && !sourceBlockId.isBlank()) {
            attributes.put("sourceBlockId", sourceBlockId);
        }
        attributes.put("sourceStatementId", statementId(normalizedFile, sourceBlockId, startLine, endLine));
        attributes.put("sourceObjectType", semanticSourceObjectType(sourceType));
        attributes.put("sourceObjectKind", sourceType.name());
        String objectName = objectName(sql);
        if (objectName.isBlank()) {
            objectName = objectName(sourceBlockId);
        }
        if (!objectName.isBlank()) {
            attributes.put("sourceObjectName", objectName);
        }
        return attributes;
    }

    private static String semanticSourceObjectType(StatementSourceType sourceType) {
        return switch (sourceType) {
            case PROCEDURE, FUNCTION, PACKAGE, PACKAGE_BODY, EVENT -> "ROUTINE";
            case TRIGGER -> "TRIGGER";
            case VIEW, MATERIALIZED_VIEW -> "QUERY";
            case DDL_FILE -> "DDL";
            case NATIVE_LOG, PLAIN_SQL, MIGRATION -> "SQL_WRITE";
            case RULE -> "RULE";
        };
    }

    private static StatementSourceType inferSourceType(
            StatementSourceType fallback,
            String sourceName,
            String sql
    ) {
        String sourceUpper = sourceName == null ? "" : sourceName.toUpperCase(Locale.ROOT);
        if (sourceUpper.startsWith("TRIGGER:")) {
            return StatementSourceType.TRIGGER;
        }
        if (sourceUpper.startsWith("ROUTINE:")) {
            return StatementSourceType.PROCEDURE;
        }
        String firstObjectType = firstObjectType(sql);
        return switch (firstObjectType) {
            case "TRIGGER" -> StatementSourceType.TRIGGER;
            case "FUNCTION" -> StatementSourceType.FUNCTION;
            case "EVENT" -> StatementSourceType.EVENT;
            case "VIEW" -> StatementSourceType.VIEW;
            case "PACKAGE" -> StatementSourceType.PACKAGE;
            case "PROCEDURE" -> StatementSourceType.PROCEDURE;
            default -> fallback;
        };
    }

    private static String sourceName(StatementSourceType sourceType, String objectName, String sourceFile) {
        if (objectName == null || objectName.isBlank()) {
            return normalizePath(sourceFile);
        }
        if (sourceType == StatementSourceType.TRIGGER) {
            return "TRIGGER:" + objectName;
        }
        if (sourceType == StatementSourceType.PROCEDURE
                || sourceType == StatementSourceType.FUNCTION
                || sourceType == StatementSourceType.PACKAGE
                || sourceType == StatementSourceType.PACKAGE_BODY
                || sourceType == StatementSourceType.EVENT) {
            return "ROUTINE:" + objectName;
        }
        return objectName;
    }

    private static String statementId(String sourceFile, String sourceBlockId, long startLine, long endLine) {
        if (sourceBlockId != null && !sourceBlockId.isBlank()) {
            return sourceBlockId;
        }
        String base = sourceFile == null || sourceFile.isBlank() ? "statement" : sourceFile;
        return base + ":" + startLine + "-" + endLine;
    }

    private static String normalizePath(String path) {
        return SourceNameNormalizer.normalize(path);
    }

    private static String objectName(String sqlOrSource) {
        if (sqlOrSource == null || sqlOrSource.isBlank()) {
            return "";
        }
        String normalized = sqlOrSource
                .replace("[", "")
                .replace("]", "")
                .replace("`", "")
                .replace("\"", "")
                .replace('(', ' ')
                .replace(';', ' ')
                .replace(',', ' ');
        String[] tokens = normalized.split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].toUpperCase(Locale.ROOT);
            if (!token.equals("PROCEDURE") && !token.equals("FUNCTION") && !token.equals("TRIGGER")
                    && !token.equals("PACKAGE") && !token.equals("VIEW")) {
                continue;
            }
            if (i + 1 >= tokens.length) {
                continue;
            }
            String next = tokens[i + 1];
            if (next.equalsIgnoreCase("BODY") && i + 2 < tokens.length) {
                next = tokens[i + 2];
            }
            return normalizeObjectName(next);
        }
        return "";
    }

    private static String firstObjectType(String sqlOrSource) {
        if (sqlOrSource == null || sqlOrSource.isBlank()) {
            return "";
        }
        String normalized = sqlOrSource
                .replace("[", "")
                .replace("]", "")
                .replace("`", "")
                .replace("\"", "")
                .replace('(', ' ')
                .replace(';', ' ')
                .replace(',', ' ');
        String[] tokens = normalized.split("\\s+");
        for (String token : tokens) {
            String upper = token.toUpperCase(Locale.ROOT);
            if (upper.equals("PROCEDURE") || upper.equals("FUNCTION") || upper.equals("TRIGGER")
                    || upper.equals("PACKAGE") || upper.equals("VIEW") || upper.equals("EVENT")) {
                return upper;
            }
        }
        return "";
    }

    private static String normalizeObjectName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace(" . ", ".")
                .replace("..", ".")
                .replaceAll("^\\.+|\\.+$", "");
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
