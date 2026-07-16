package com.relationdetector.mysql;

import java.sql.Connection;
import java.util.Objects;

import com.relationdetector.contracts.spi.LiveSourceConfigurationException;
import com.relationdetector.contracts.spi.ScanScope;

/**
 *
 * Canonicalizes MySQL's database namespace onto the catalog axis.
 */
public final class MySqlCatalogScope {
    private MySqlCatalogScope() {
    }

    public static ScanScope canonicalize(ScanScope scope) {
        Objects.requireNonNull(scope, "scope");
        String catalog = clean(scope.catalog());
        String legacySchema = clean(scope.schema());
        if (!catalog.isBlank() && !legacySchema.isBlank() && !catalog.equals(legacySchema)) {
            throw new IllegalArgumentException(
                    "MySQL database.catalog and database.schema must identify the same database");
        }
        String database = catalog.isBlank() ? legacySchema : catalog;
        return new ScanScope(emptyToNull(database), null, scope.includeTables(), scope.excludeTables());
    }

    public static ScanScope resolve(Connection connection, ScanScope scope) {
        ScanScope canonical = canonicalize(scope);
        if (canonical.catalog() != null) {
            return canonical;
        }
        try {
            String catalog = clean(connection.getCatalog());
            if (catalog.isBlank()) {
                throw new LiveSourceConfigurationException(
                        "MySQL live database.catalog cannot be resolved from the JDBC connection");
            }
            return new ScanScope(catalog, null, canonical.includeTables(), canonical.excludeTables());
        } catch (LiveSourceConfigurationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LiveSourceConfigurationException(
                    "MySQL live database.catalog cannot be resolved from the JDBC connection", ex);
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static String emptyToNull(String value) {
        return value.isBlank() ? null : value;
    }
}
