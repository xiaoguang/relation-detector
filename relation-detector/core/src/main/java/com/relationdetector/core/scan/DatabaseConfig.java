package com.relationdetector.core.scan;

import java.util.List;

import com.relationdetector.contracts.Enums.DatabaseType;

/**
 *
 * Immutable database identity, connection, and scan-scope configuration.
 */
public record DatabaseConfig(
        DatabaseType databaseType,
        String adaptorId,
        String jdbcUrl,
        String username,
        String password,
        String catalog,
        String schema,
        List<String> includeTables,
        List<String> excludeTables
) {
    public DatabaseConfig {
        includeTables = List.copyOf(includeTables == null ? List.of() : includeTables);
        excludeTables = List.copyOf(excludeTables == null ? List.of() : excludeTables);
    }
}
