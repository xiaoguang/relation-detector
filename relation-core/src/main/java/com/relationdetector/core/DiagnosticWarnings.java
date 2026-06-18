package com.relationdetector.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.WarningMessage;
import com.relationdetector.api.Enums.WarningType;

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
        return WarningMessage.warn(WarningType.PARSE_WARNING, "SQL_PARSE_FAILED",
                ex.getMessage(), statement.sourceName(), statement.startLine(), attributes);
    }

    /**
     * Builds a warning when ANTLR primary mode safely falls back to Simple.
     *
     * <p>This is not a syntax failure. It means the ANTLR extractor ran but did
     * not satisfy the no-loss parity rule for this statement. The raw SQL and
     * missing fingerprints let maintainers reproduce the exact gap and add a
     * focused golden test before trying primary again.
     */
    public static WarningMessage antlrPrimaryFallback(
            SqlStatementRecord statement,
            String reason,
            List<String> missingSimpleRelations
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("statementSourceType", statement.sourceType().name());
        attributes.put("endLine", statement.endLine());
        attributes.put("rawStatement", statement.sql());
        attributes.put("parserMode", SqlParserMode.ANTLR_PRIMARY.name());
        attributes.put("fallbackParser", "SimpleSqlRelationParser");
        attributes.put("missingSimpleRelations", List.copyOf(missingSimpleRelations));
        if (!statement.attributes().isEmpty()) {
            attributes.putAll(statement.attributes());
        }
        return WarningMessage.warn(WarningType.PARSE_WARNING,
                "ANTLR_PRIMARY_FALLBACK",
                reason,
                statement.sourceName(),
                statement.startLine(),
                attributes);
    }

    public static WarningMessage antlrDdlPrimaryFallback(Path file, List<String> missingSimpleDdlRelations) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("parserMode", DdlParserMode.ANTLR_DDL_PRIMARY.name());
        attributes.put("fallbackParser", "DdlParser");
        attributes.put("missingSimpleDdlRelations", List.copyOf(missingSimpleDdlRelations));
        readString(file).ifPresent(text -> attributes.put("rawStatement", text));
        return WarningMessage.warn(WarningType.PARSE_WARNING,
                "ANTLR_DDL_PRIMARY_FALLBACK",
                "ANTLR DDL relation extraction missed simple DDL baseline relationships",
                file.toString(),
                0,
                attributes);
    }

    public static WarningMessage antlrDdlTextPrimaryFallback(
            String sourceName,
            String ddl,
            List<String> missingSimpleDdlRelations
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("parserMode", DdlParserMode.ANTLR_DDL_PRIMARY.name());
        attributes.put("fallbackParser", "DdlParser");
        attributes.put("missingSimpleDdlRelations", List.copyOf(missingSimpleDdlRelations));
        attributes.put("rawStatement", ddl);
        return WarningMessage.warn(WarningType.PARSE_WARNING,
                "ANTLR_DDL_PRIMARY_FALLBACK",
                "ANTLR DDL relation extraction missed simple DDL baseline relationships",
                sourceName,
                0,
                attributes);
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
