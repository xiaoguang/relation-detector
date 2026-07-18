package com.relationdetector.contracts.spi;

import java.util.Optional;

import com.relationdetector.contracts.spi.Collectors.DatabaseDdlCollector;
import com.relationdetector.contracts.spi.Collectors.MetadataCollector;
import com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector;
import com.relationdetector.contracts.spi.Collectors.SqlLogExtractor;

/**
 * CN: 组装 database adaptor 可选的 metadata、object、database-DDL 与 log collectors。
 * EN: Groups the optional metadata, object, database-DDL, and log collectors exposed by a database adaptor.
 *
 * <p>This is the only collector entry point in adaptor SPI v6. Optional
 * members make capability declarations executable: a declared live capability
 * must have its corresponding collector present before JDBC is opened.
 */
public record AdaptorCollectors(
        Optional<MetadataCollector> metadata,
        Optional<ObjectDefinitionCollector> objects,
        Optional<DatabaseDdlCollector> databaseDdl,
        Optional<SqlLogExtractor> logs
) {
    public AdaptorCollectors {
        metadata = metadata == null ? Optional.empty() : metadata;
        objects = objects == null ? Optional.empty() : objects;
        databaseDdl = databaseDdl == null ? Optional.empty() : databaseDdl;
        logs = logs == null ? Optional.empty() : logs;
    }
}
