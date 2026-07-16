package com.relationdetector.postgres;

import java.sql.Connection;
import java.util.Locale;

import com.relationdetector.contracts.spi.LiveSourceConfigurationException;
import com.relationdetector.contracts.spi.ScanScope;

/**
 * CN: 为 PostgreSQL live collector 解析统一的 catalog 与 schema。
 *
 * <p>EN: Resolves one catalog/schema namespace shared by PostgreSQL live collectors.
 */
public final class PostgresNamespaceResolver {
    private PostgresNamespaceResolver() {
    }

    /**
     * CN: 优先采用显式 scope，缺省 catalog 来自连接，缺省 schema 为 public。
     *
     * <p>EN: Prefers explicit scope, falls back to the connection catalog and the public schema.
     */
    public static Namespace resolve(Connection connection, ScanScope scope) {
        String requestedCatalog = nonBlank(scope.catalog());
        String connectionCatalog = connectionCatalog(connection, requestedCatalog != null);
        if (requestedCatalog != null && !normalize(requestedCatalog).equals(normalize(connectionCatalog))) {
            throw new LiveSourceConfigurationException(
                    "PostgreSQL database.catalog does not match the JDBC catalog");
        }
        String catalog = requestedCatalog == null ? connectionCatalog : requestedCatalog;
        String schema = nonBlank(scope.schema());
        return new Namespace(catalog, schema == null ? "public" : schema);
    }

    private static String connectionCatalog(Connection connection, boolean required) {
        try {
            String catalog = nonBlank(connection.getCatalog());
            if (required && catalog == null) {
                throw new LiveSourceConfigurationException(
                        "PostgreSQL database.catalog cannot be verified");
            }
            return catalog;
        } catch (LiveSourceConfigurationException ex) {
            throw ex;
        } catch (Exception ex) {
            if (required) {
                throw new LiveSourceConfigurationException(
                        "PostgreSQL database.catalog cannot be verified", ex);
            }
            return null;
        }
    }

    private static String normalize(String identifier) {
        boolean quoted = identifier.startsWith("\"") && identifier.endsWith("\"");
        String value = quoted && identifier.length() >= 2
                ? identifier.substring(1, identifier.length() - 1) : identifier;
        return quoted ? value : value.toLowerCase(Locale.ROOT);
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** CN: PostgreSQL live scan 的规范 namespace。EN: Canonical PostgreSQL live-scan namespace. */
    public record Namespace(String catalog, String schema) {
    }
}
