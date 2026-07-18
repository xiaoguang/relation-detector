package com.relationdetector.core.scan;

import java.util.List;

import com.relationdetector.contracts.Enums.DatabaseType;

/**
 * CN: 承载不可变的 database identity、JDBC connection 与 table include/exclude scope 配置。
 * EN: Carries immutable database identity, JDBC connection, and table include/exclude scope configuration.
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
