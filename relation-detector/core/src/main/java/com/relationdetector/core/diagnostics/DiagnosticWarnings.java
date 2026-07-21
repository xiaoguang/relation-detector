package com.relationdetector.core.diagnostics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.Enums.DatabaseObjectType;

/**
 * CN: 统一构造 parser、file 与 live-source failures 的 operator-facing warnings。parser/file warning
 * 为审计可保留原始 SQL 与异常文本；live JDBC warning 必须委托 {@link LiveDiagnosticSanitizer} 脱敏。
 * EN: Builds operator-facing warnings for parser, file, and live-source failures. Parser/file warnings may
 * retain raw SQL and exception text for audit, while live JDBC warnings must delegate sanitization to
 * {@link LiveDiagnosticSanitizer}.
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
 * output stays stable. This class standardizes warning structure; it does not
 * apply one sensitivity policy to every source family.
 */
public final class DiagnosticWarnings {
    private DiagnosticWarnings() {
    }

    /**
     *
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
     *
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

    /**
     *
     * Builds a warning for a raw SQL/object file that could not be split into statements.
     */
    public static WarningMessage sqlFileExtractFailed(Path file, Exception ex) {
        Map<String, Object> attributes = exceptionAttributes(ex);
        readString(file).ifPresent(text -> attributes.put("rawStatement", text));
        return WarningMessage.warn(WarningType.PARSE_WARNING, "SQL_FILE_EXTRACT_FAILED",
                ex.getMessage(), file.toString(), 0, attributes);
    }

    /**
     *
     * Builds a warning for a database-native log file that could not be extracted.
     */
    public static WarningMessage logExtractFailed(Path file, Exception ex) {
        Map<String, Object> attributes = exceptionAttributes(ex);
        return WarningMessage.warn(WarningType.PARSE_WARNING, "LOG_EXTRACT_FAILED",
                ex.getMessage(), file.toString(), 0, attributes);
    }

    /**
     *
     * Builds a warning when database object definition collection partially fails.
     */
    public static WarningMessage objectCollectFailed(String code, String source, Exception ex) {
        return objectCollectFailed(code, source, ex, java.util.Set.of());
    }

    public static WarningMessage objectCollectFailed(
            String code, String source, Exception ex, java.util.Set<Integer> permissionVendorCodes) {
        return LiveDiagnosticSanitizer.jdbcWarning(code,
                LiveDiagnosticSanitizer.Operation.OBJECT, source, ex, Map.of(), permissionVendorCodes);
    }

    /**
     * CN: 构造带完整对象身份且不暴露 SQL 或驱动消息的 live object warning。
     *
     * <p>EN: Builds a live-object warning with object identity and without SQL or driver-message leakage.
     */
    public static WarningMessage objectCollectFailed(
            String code,
            String source,
            Exception ex,
            String catalog,
            String schema,
            String objectName,
            DatabaseObjectType objectType
    ) {
        return objectCollectFailed(code, source, ex, catalog, schema, objectName, objectType, java.util.Set.of());
    }

    public static WarningMessage objectCollectFailed(
            String code,
            String source,
            Exception ex,
            String catalog,
            String schema,
            String objectName,
            DatabaseObjectType objectType,
            java.util.Set<Integer> permissionVendorCodes
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        putIfPresent(context, "objectCatalog", catalog);
        putIfPresent(context, "objectSchema", schema);
        putIfPresent(context, "objectName", objectName);
        if (objectType != null) {
            context.put("objectType", objectType.name());
        }
        return LiveDiagnosticSanitizer.jdbcWarning(code,
                LiveDiagnosticSanitizer.Operation.OBJECT, source, ex, context, permissionVendorCodes);
    }

    /** CN: 为缺少 SQL 声明的 live 对象构造统一 warning。EN: Warns when a live object has no declaration. */
    public static WarningMessage objectDefinitionUnavailable(
            String source,
            String catalog,
            String schema,
            String objectName,
            String objectType
    ) {
        return LiveDiagnosticSanitizer.definitionUnavailable(
                LiveDiagnosticSanitizer.Operation.OBJECT, source,
                objectContext(catalog, schema, objectName, objectType));
    }

    /**
     *
     * Builds a sanitized warning for live database DDL collection.
     */
    public static WarningMessage databaseDdlCollectFailed(String code, String source, Exception ex) {
        return databaseDdlCollectFailed(code, source, ex, java.util.Set.of());
    }

    public static WarningMessage databaseDdlCollectFailed(
            String code, String source, Exception ex, java.util.Set<Integer> permissionVendorCodes) {
        return LiveDiagnosticSanitizer.jdbcWarning(code,
                LiveDiagnosticSanitizer.Operation.DATABASE_DDL, source, ex, Map.of(), permissionVendorCodes);
    }

    /** CN: 为缺少表 DDL 的 live catalog 项构造统一 warning。EN: Warns when a live table has no DDL. */
    public static WarningMessage databaseDdlDefinitionUnavailable(
            String source,
            String catalog,
            String schema,
            String objectName
    ) {
        return LiveDiagnosticSanitizer.definitionUnavailable(
                LiveDiagnosticSanitizer.Operation.DATABASE_DDL, source,
                objectContext(catalog, schema, objectName, "TABLE"));
    }

    private static Map<String, Object> objectContext(
            String catalog,
            String schema,
            String objectName,
            String objectType
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        putIfPresent(context, "objectCatalog", catalog);
        putIfPresent(context, "objectSchema", schema);
        putIfPresent(context, "objectName", objectName);
        putIfPresent(context, "objectType", objectType);
        return context;
    }

    private static Map<String, Object> exceptionAttributes(Exception ex) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("exceptionClass", ex.getClass().getSimpleName());
        return attributes;
    }

    private static void putIfPresent(Map<String, Object> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }

    private static java.util.Optional<String> readString(Path file) {
        try {
            return java.util.Optional.of(Files.readString(file));
        } catch (Exception ignored) {
            return java.util.Optional.empty();
        }
    }
}
