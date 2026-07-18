package com.relationdetector.oracle;

import java.sql.Connection;
import java.util.Locale;

import com.relationdetector.contracts.spi.LiveSourceConfigurationException;
import com.relationdetector.contracts.spi.ScanScope;

/**
 * CN: 为 Oracle metadata、object、DDL 和 profile 统一解析 executable owner；显式 catalog 直接拒绝，schema 缺省时读取 connection schema/user，输出不猜测跨库身份。
 * EN: Resolves one executable Oracle owner for metadata, object, DDL, and profile operations. Explicit catalog is rejected; absent schema falls back to connection schema or user without cross-database inference.
 */
public final class OracleOwnerResolver {
    private OracleOwnerResolver() {
    }

    public static String resolve(Connection connection, ScanScope scope) {
        if (scope.catalog() != null && !scope.catalog().isBlank()) {
            throw new LiveSourceConfigurationException(
                    "Oracle database.catalog is not supported for live queries");
        }
        if (scope.schema() != null && !scope.schema().isBlank()) {
            return normalizeExplicit(scope.schema());
        }
        try {
            String schema = connection.getSchema();
            if (schema != null && !schema.isBlank()) {
                return schema;
            }
        } catch (Exception ignored) {
            // JDBC drivers may not implement getSchema; fall back to the authenticated user.
        }
        try {
            String userName = connection.getMetaData().getUserName();
            return userName == null ? "" : userName;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String normalizeExplicit(String owner) {
        String trimmed = owner.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).replace("\"\"", "\"");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }
}
