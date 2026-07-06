package com.relationdetector.contracts.spi;

import java.util.Optional;

import com.relationdetector.contracts.spi.Collectors.DatabaseDdlCollector;
import com.relationdetector.contracts.spi.Collectors.MetadataCollector;
import com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector;
import com.relationdetector.contracts.spi.Collectors.SqlLogExtractor;

/**
 * Grouped source collection capabilities exposed by a database adaptor.
 *
 * <p>CN: 这是对现有 DatabaseAdaptor collector 方法的兼容分组；旧方法仍保留，
 * 新代码可以逐步转向 collectors()，降低 SPI 表面宽度。
 */
public record AdaptorCollectors(
        MetadataCollector metadata,
        ObjectDefinitionCollector objects,
        Optional<DatabaseDdlCollector> databaseDdl,
        SqlLogExtractor logs
) {
}
