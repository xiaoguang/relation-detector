package com.relationdetector.core.diagnostics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.Enums.WarningType;

/**
 * Factory for operator-facing diagnostic warnings.
 *
 * <p>Design mapping: parse/extraction failures are not relationship evidence,
 * but they are essential audit information. Keeping warning construction here
 * gives every input path the same fields:
 *
 * <pre>{@code
 * code: SQL_PARSE_FAILED
 * source: procedures/order_refresh.sql
 * line: 12
 * attributes.rawStatement: CREATE PROCEDURE ...
 * attributes.exceptionClass: IllegalArgumentException
 * }</pre>
 *
 * <p>Callers should prefer these helpers over ad-hoc warning strings so JSON
 * output stays stable for operations tooling and future tests.
 */
public final class DiagnosticWarnings {
    private DiagnosticWarnings() {
    }

    /**
     * Builds a warning for a DDL file that could not be parsed by an adaptor.
     *
     * <p>The raw file text is retained when it can be read. If reading the file
     * itself fails, the exception still becomes a warning and {@code rawStatement}
     * is omitted because there is no original SQL text available to preserve.
     */
    public static WarningMessage ddlParseFailed(Path file, Exception ex) {
        Map<String, Object> attributes = exceptionAttributes(ex);
        readString(file).ifPresent(text -> attributes.put("rawStatement", text));
        return WarningMessage.warn(WarningType.PARSE_WARNING, "DDL_PARSE_FAILED",
                ex.getMessage(), file.toString(), 0, attributes);
    }

    public static WarningMessage ddlTextParseFailed(String sourceName, String ddl, Exception ex) {
        Map<String, Object> attributes = exceptionAttributes(ex);
        attributes.put("rawStatement", ddl);
        return WarningMessage.warn(WarningType.PARSE_WARNING, "DDL_PARSE_FAILED",
                ex.getMessage(), sourceName, 0, attributes);
    }

    /**
     * Builds a warning for one SQL statement from a procedure/function/view,
     * trigger, native log, or plain SQL file that threw during relationship
     * parsing.
     */
    public static WarningMessage sqlParseFailed(SqlStatementRecord statement, Exception ex) {
        Map<String, Object> attributes = exceptionAttributes(ex);
        attributes.put("statementSourceType", statement.sourceType().name());
        attributes.put("endLine", statement.endLine());
        attributes.put("rawStatement", statement.sql());
        if (!statement.attributes().isEmpty()) {
            attributes.putAll(statement.attributes());
        }
        Object sourceFile = attributes.get("sourceFile");
        String source = sourceFile instanceof String text && !text.isBlank()
                ? text
                : statement.sourceName();
        return WarningMessage.warn(WarningType.PARSE_WARNING, "SQL_PARSE_FAILED",
                ex.getMessage(), source, statement.startLine(), attributes);
    }

    /** Builds a warning for a raw SQL/object file that could not be split into statements. */
    public static WarningMessage sqlFileExtractFailed(Path file, Exception ex) {
        Map<String, Object> attributes = exceptionAttributes(ex);
        readString(file).ifPresent(text -> attributes.put("rawStatement", text));
        return WarningMessage.warn(WarningType.PARSE_WARNING, "SQL_FILE_EXTRACT_FAILED",
                ex.getMessage(), file.toString(), 0, attributes);
    }

    /** Builds a warning for a database-native log file that could not be extracted. */
    public static WarningMessage logExtractFailed(Path file, Exception ex) {
        Map<String, Object> attributes = exceptionAttributes(ex);
        return WarningMessage.warn(WarningType.PARSE_WARNING, "LOG_EXTRACT_FAILED",
                ex.getMessage(), file.toString(), 0, attributes);
    }

    /** Builds a warning when database object definition collection partially fails. */
    public static WarningMessage objectCollectFailed(String code, String source, Exception ex) {
        return WarningMessage.warn(WarningType.PERMISSION_WARNING, code,
                ex.getMessage(), source, 0, exceptionAttributes(ex));
    }

    private static Map<String, Object> exceptionAttributes(Exception ex) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("exceptionClass", ex.getClass().getSimpleName());
        return attributes;
    }

    private static java.util.Optional<String> readString(Path file) {
        try {
            return java.util.Optional.of(Files.readString(file));
        } catch (Exception ignored) {
            return java.util.Optional.empty();
        }
    }
}
