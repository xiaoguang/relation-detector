package com.relationdetector.core.diagnostics;

import java.sql.SQLException;
import java.sql.SQLInvalidAuthorizationSpecException;
import java.sql.SQLTimeoutException;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * CN: 仅依据 JDBC 异常类型、SQLState 和已配置 vendor code 分类 live 失败，不读取驱动消息。
 *
 * <p>EN: Classifies live failures from JDBC type, SQLState, and configured vendor codes without reading messages.
 */
public final class JdbcExceptionClassifier {
    private JdbcExceptionClassifier() {
    }

    /** CN: 仅使用 JDBC 可移植异常类型和 SQLState 分类。EN: Uses only portable JDBC types and SQLState. */
    public static JdbcFailureKind classify(SQLException failure) {
        return classify(failure, java.util.Set.of());
    }

    /** CN: 将额外 vendor code 合并到标准规则。EN: Adds dialect-specific vendor codes to standard rules. */
    public static JdbcFailureKind classify(SQLException failure, int... permissionVendorCodes) {
        java.util.Set<Integer> codes = Arrays.stream(permissionVendorCodes == null ? new int[0] : permissionVendorCodes)
                .boxed()
                .collect(Collectors.toSet());
        return classify(failure, codes);
    }

    /** CN: 使用调用方所属方言明确提供的 vendor code 分类。EN: Uses vendor codes explicitly supplied by a dialect. */
    public static JdbcFailureKind classify(SQLException failure, java.util.Set<Integer> permissionVendorCodes) {
        java.util.Set<Integer> codes = permissionVendorCodes == null ? java.util.Set.of() : permissionVendorCodes;
        if (failure instanceof SQLTimeoutException) {
            return JdbcFailureKind.TIMEOUT;
        }
        if (failure instanceof SQLInvalidAuthorizationSpecException
                || permissionSqlState(failure.getSQLState())
                || codes.contains(failure.getErrorCode())) {
            return JdbcFailureKind.PERMISSION;
        }
        return JdbcFailureKind.QUERY_FAILED;
    }

    private static boolean permissionSqlState(String sqlState) {
        return sqlState != null && (sqlState.startsWith("28") || sqlState.equals("42501"));
    }
}
