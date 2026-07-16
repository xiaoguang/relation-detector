package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.common.CommonDatabaseAdaptor;

class FinalScanContractTest {
    @TempDir
    Path tempDir;

    @Test
    void processesDdlPathsWhenScanEngineIsCalledDirectly() throws Exception {
        Path ddl = tempDir.resolve("schema.sql");
        Files.writeString(ddl, """
                CREATE TABLE users (
                  id BIGINT PRIMARY KEY
                );
                CREATE TABLE contracts (
                  party_id BIGINT,
                  FOREIGN KEY (party_id) REFERENCES users(id)
                );
                """);
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.COMMON;
        config.metadataEnabled = false;
        config.ddlEnabled = true;
        config.ddlFromDatabase = false;
        config.ddlPaths.add(ddl);

        ScanResult result = new ScanEngine().scan(config, new CommonDatabaseAdaptor());

        assertEquals(1, result.relationships().size(),
                "direct ScanEngine use must process configured ddlPaths instead of silently ignoring them");
    }
}
