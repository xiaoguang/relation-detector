package com.relationdetector.oracle.metadata;

import java.sql.Connection;

import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.spi.Collectors.MetadataCollector;
import com.relationdetector.contracts.spi.ScanScope;

/**
 * Conservative Oracle metadata collector placeholder.
 */
public final class OracleMetadataCollector implements MetadataCollector {
    @Override
    public MetadataSnapshot collect(Connection connection, ScanScope scope) {
        return new MetadataSnapshot();
    }
}
