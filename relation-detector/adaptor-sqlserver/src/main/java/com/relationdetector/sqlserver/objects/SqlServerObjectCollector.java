package com.relationdetector.sqlserver.objects;

import java.sql.Connection;
import java.util.List;

import com.relationdetector.contracts.parse.DatabaseObjectDefinition;
import com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector;
import com.relationdetector.contracts.spi.ScanScope;

/** Conservative SQL Server object collector placeholder. */
public final class SqlServerObjectCollector implements ObjectDefinitionCollector {
    @Override
    public List<DatabaseObjectDefinition> collect(Connection connection, ScanScope scope) {
        return List.of();
    }
}
