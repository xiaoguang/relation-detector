package com.relationdetector.core.diagnostics;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;

/**
 * CN: 将 live JDBC 异常和缺失 definition 转换为固定消息、允许属性和结构化异常元数据；
 * 不保留 driver message、JDBC URL、SQL 或业务参数。
 *
 * <p>EN: Converts live JDBC failures and missing definitions into fixed messages, approved context, and
 * structured exception metadata; it never retains driver messages, JDBC URLs, SQL, or business values.
 */
public final class LiveDiagnosticSanitizer {
    private static final Set<String> ALLOWED_ATTRIBUTES = Set.of(
            "sourceEndpoint", "targetEndpoint", "profilerSource",
            "objectCatalog", "objectSchema", "objectName", "objectType");

    private LiveDiagnosticSanitizer() {
    }

    /**
     * CN: 根据 JDBC 失败种类选择公共 warning 类型，并只保留允许的结构化上下文。
     *
     * <p>EN: Maps a JDBC failure to a public warning type while retaining only approved context.
     */
    public static WarningMessage jdbcWarning(
            String code,
            Operation operation,
            String source,
            Throwable failure,
            Map<String, ?> context
    ) {
        return jdbcWarning(code, operation, source, failure, context, Set.of());
    }

    /**
     * CN: 使用当前 adaptor 明确声明的 vendor code 对 JDBC 失败分类。
     *
     * <p>EN: Classifies a JDBC failure with vendor codes explicitly declared by the active adaptor.
     */
    public static WarningMessage jdbcWarning(
            String code,
            Operation operation,
            String source,
            Throwable failure,
            Map<String, ?> context,
            Set<Integer> permissionVendorCodes
    ) {
        JdbcFailureKind kind = failureKind(failure, permissionVendorCodes);
        WarningType type = kind == JdbcFailureKind.PERMISSION
                ? WarningType.PERMISSION_WARNING : WarningType.LIVE_SOURCE_WARNING;
        return warning(type, code, operation.message(kind), source, failure, context);
    }

    /**
     * CN: 为无异常但缺少对象或表 DDL 的情况生成统一安全 warning。
     *
     * <p>EN: Produces the common safe warning for an unavailable object or table definition.
     */
    public static WarningMessage definitionUnavailable(Operation operation, String source, Map<String, ?> context) {
        return warning(WarningType.LIVE_SOURCE_WARNING, "DEFINITION_UNAVAILABLE",
                operation.definitionUnavailableMessage(), source, null, context);
    }

    /**
     * CN: 以 core 固定消息和固定 source 重建已通过结构校验的外部 adaptor warning；输入中的
     * message、source 与 line 均不会进入输出，本方法也不负责判断 warning envelope 是否合规。
     *
     * <p>EN: Rebuilds a structurally validated external-adaptor warning with a core-owned message and source.
     * Plugin message, source, and line are never retained; envelope validation remains the caller's responsibility.
     */
    public static WarningMessage rebuildAdaptorWarning(
            WarningType type,
            String code,
            Operation operation,
            String source,
            Map<String, ?> context
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        copyAllowedContext(context, attributes);
        attributes.put("sqlState", stringValue(context, "sqlState"));
        attributes.put("vendorCode", intValue(context, "vendorCode"));
        attributes.put("exceptionClass", stringValue(context, "exceptionClass"));
        String message = "DEFINITION_UNAVAILABLE".equals(code)
                ? operation.definitionUnavailableMessage()
                : type == WarningType.PERMISSION_WARNING
                        ? operation.message(JdbcFailureKind.PERMISSION)
                        : operation.message();
        return WarningMessage.warn(type, code, message, safeSource(source), 0, attributes);
    }

    public static WarningMessage warning(
            WarningType type,
            String code,
            Operation operation,
            String source,
            Throwable failure
    ) {
        return warning(type, code, operation, source, failure, Map.of());
    }

    /**
     * CN: 使用调用方给出的可允许对象上下文生成安全 warning，并过滤其他属性。
     *
     * <p>EN: Builds a safe warning from caller-supplied approved object context and drops all other attributes.
     */
    public static WarningMessage warning(
            WarningType type,
            String code,
            Operation operation,
            String source,
            Throwable failure,
            Map<String, ?> context
    ) {
        return warning(type, code, operation.message(), source, failure, context);
    }

    private static WarningMessage warning(
            WarningType type,
            String code,
            String message,
            String source,
            Throwable failure,
            Map<String, ?> context
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        copyAllowedContext(context, attributes);
        SQLException sqlFailure = findSqlException(failure);
        attributes.put("sqlState", sqlFailure == null || sqlFailure.getSQLState() == null
                ? "" : sqlFailure.getSQLState());
        attributes.put("vendorCode", sqlFailure == null ? 0 : sqlFailure.getErrorCode());
        attributes.put("exceptionClass", failure == null ? "" : failure.getClass().getName());
        return WarningMessage.warn(type, code, message, safeSource(source), 0, attributes);
    }

    private static void copyAllowedContext(Map<String, ?> context, Map<String, Object> target) {
        if (context == null) {
            return;
        }
        context.forEach((key, value) -> {
            if (ALLOWED_ATTRIBUTES.contains(key) && value != null) {
                target.put(key, value);
            }
        });
    }

    private static String stringValue(Map<String, ?> context, String key) {
        Object value = context == null ? null : context.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static int intValue(Map<String, ?> context, String key) {
        Object value = context == null ? null : context.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static SQLException findSqlException(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            current = current.getCause();
        }
        return null;
    }

    private static JdbcFailureKind failureKind(Throwable failure, Set<Integer> permissionVendorCodes) {
        SQLException sqlFailure = findSqlException(failure);
        return sqlFailure == null ? JdbcFailureKind.QUERY_FAILED
                : JdbcExceptionClassifier.classify(sqlFailure, permissionVendorCodes);
    }

    private static String safeSource(String source) {
        if (source == null || source.isBlank() || source.regionMatches(true, 0, "jdbc:", 0, 5)) {
            return "database";
        }
        return source;
    }

    public enum Operation {
        CONNECTION("Live database connection failed"),
        METADATA("Live database metadata collection failed"),
        OBJECT("Live database object collection failed"),
        DATABASE_DDL("Live database DDL collection failed"),
        PROFILE_TIMEOUT("Data profiling query timed out"),
        PROFILE_PERMISSION("Data profiling query permission denied"),
        PROFILE_QUERY("Data profiling query failed");

        private final String message;

        Operation(String message) {
            this.message = message;
        }

        private String message() {
            return message;
        }

        private String message(JdbcFailureKind kind) {
            if (this == PROFILE_TIMEOUT || this == PROFILE_PERMISSION || this == PROFILE_QUERY) {
                return switch (kind) {
                    case PERMISSION -> PROFILE_PERMISSION.message;
                    case TIMEOUT -> PROFILE_TIMEOUT.message;
                    case QUERY_FAILED -> PROFILE_QUERY.message;
                };
            }
            String stem = message.endsWith(" failed")
                    ? message.substring(0, message.length() - " failed".length()) : message;
            return switch (kind) {
                case PERMISSION -> stem + " permission denied";
                case TIMEOUT -> stem + " timed out";
                case QUERY_FAILED -> message;
            };
        }

        private String definitionUnavailableMessage() {
            return switch (this) {
                case OBJECT -> "Live database object definition unavailable";
                case DATABASE_DDL -> "Live database DDL definition unavailable";
                default -> "Live database definition unavailable";
            };
        }
    }
}
