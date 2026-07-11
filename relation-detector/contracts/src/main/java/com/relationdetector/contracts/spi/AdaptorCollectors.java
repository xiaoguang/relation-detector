package com.relationdetector.contracts.spi;

import java.util.Optional;

import com.relationdetector.contracts.spi.Collectors.DatabaseDdlCollector;
import com.relationdetector.contracts.spi.Collectors.MetadataCollector;
import com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector;
import com.relationdetector.contracts.spi.Collectors.SqlLogExtractor;

/**
 * Grouped source collection capabilities exposed by a database adaptor.
 *
 * <p>This is the only collector entry point in adaptor SPI v2.
 */
public record AdaptorCollectors(
        MetadataCollector metadata,
        ObjectDefinitionCollector objects,
        Optional<DatabaseDdlCollector> databaseDdl,
        SqlLogExtractor logs
) {
}
