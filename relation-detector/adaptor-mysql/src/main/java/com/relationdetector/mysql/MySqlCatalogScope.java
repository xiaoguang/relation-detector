package com.relationdetector.mysql;

import java.util.Objects;

import com.relationdetector.contracts.spi.ScanScope;

/** Canonicalizes MySQL's database namespace onto the catalog axis. */
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

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static String emptyToNull(String value) {
        return value.isBlank() ? null : value;
    }
}
