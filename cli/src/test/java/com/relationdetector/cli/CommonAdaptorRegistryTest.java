package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.spi.DatabaseAdaptor;

class CommonAdaptorRegistryTest {
    @Test
    void serviceLoaderDiscoversCommonPortableAdaptor() throws Exception {
        DatabaseAdaptor adaptor = AdaptorRegistry.load(null).resolve(DatabaseType.COMMON, null);

        assertEquals("common", adaptor.id());
        assertTrue(adaptor.supportedDatabaseTypes().contains(DatabaseType.COMMON));
        assertTrue(adaptor.structuredSqlParser().isPresent());
        assertTrue(adaptor.structuredDdlParser().isPresent());
    }
}
