package com.relationdetector.oracle.objects;

import java.sql.Connection;
import java.util.List;

import com.relationdetector.contracts.parse.DatabaseObjectDefinition;
import com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector;
import com.relationdetector.contracts.spi.ScanScope;

/**
 * Conservative Oracle object collector placeholder.
 */
public final class OracleObjectCollector implements ObjectDefinitionCollector {
    @Override
    public List<DatabaseObjectDefinition> collect(Connection connection, ScanScope scope) {
        return List.of();
    }
}
