package com.relationdetector.sqlserver;

import java.sql.Connection;
import java.util.Locale;

import com.relationdetector.contracts.spi.LiveSourceConfigurationException;
import com.relationdetector.contracts.spi.ScanScope;

/**
 * CN: 为 SQL Server metadata、object 和 database-DDL collector 解析当前 catalog，并在查询前拒绝与 JDBC 连接不一致的显式 catalog；
 * 不执行跨库查询或 namespace 推测。
 *
 * <p>EN: Resolves the current catalog for SQL Server metadata, object, and database-DDL collectors and rejects
 * an explicit catalog that cannot be proven equal to the JDBC connection before querying; it does not perform
 * cross-database reads or infer namespaces.
 */
public final class SqlServerCatalogResolver {
    private SqlServerCatalogResolver() {
    }

    /**
     * CN: 返回经 SQL Server identifier 规则验证的 catalog；显式值不匹配或无法从连接证明时在首条 catalog SQL 前失败。
     *
     * <p>EN: Returns the catalog validated with SQL Server identifier rules, failing before catalog SQL when an
     * explicit value differs from or cannot be verified against the connection.
     */
    public static String resolve(Connection connection, ScanScope scope) {
        String requested = nonBlank(scope.catalog());
        String connected = connectionCatalog(connection, requested != null);
        if (requested != null && !normalize(requested).equals(normalize(connected))) {
            throw new LiveSourceConfigurationException(
                    "SQL Server database.catalog does not match the JDBC catalog");
        }
        return requested == null ? connected : requested;
    }

    private static String connectionCatalog(Connection connection, boolean required) {
        try {
            String catalog = nonBlank(connection.getCatalog());
            if (required && catalog == null) {
                throw new LiveSourceConfigurationException(
                        "SQL Server database.catalog cannot be verified");
            }
            return catalog;
        } catch (LiveSourceConfigurationException ex) {
            throw ex;
        } catch (Exception ex) {
            if (required) {
                throw new LiveSourceConfigurationException(
                        "SQL Server database.catalog cannot be verified", ex);
            }
            return null;
        }
    }

    private static String normalize(String identifier) {
        String value = identifier;
        while (value.startsWith("[") && value.endsWith("]") && value.length() > 1) {
            value = value.substring(1, value.length() - 1);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
