package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.common.CommonDatabaseAdaptor;

class ScanCapabilityValidatorTest {
    @Test
    void rejectsUnsupportedLiveMetadataBeforeOpeningJdbc() {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.COMMON;
        config.jdbcUrl = "jdbc:must-not-open:metadata";
        config.metadataEnabled = true;

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ScanEngine().scan(config, new CommonDatabaseAdaptor()));

        assertTrue(error.getMessage().contains("common"));
        assertTrue(error.getMessage().contains("METADATA"));
    }

    @Test
    void pureFileScanDoesNotRequireLiveMetadataCapability() {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.COMMON;
        config.metadataEnabled = true;

        new ScanEngine().scan(config, new CommonDatabaseAdaptor());
    }
}
