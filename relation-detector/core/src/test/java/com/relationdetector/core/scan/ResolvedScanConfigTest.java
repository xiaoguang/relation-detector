package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.contracts.Enums.DatabaseType;

class ResolvedScanConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void snapshotsMutableInputAndKeepsCollectionsImmutable() throws IOException {
        Path queryFile = tempDir.resolve("queries.sql");
        Files.writeString(queryFile, "SELECT 1;\n");
        ScanConfig input = new ScanConfig();
        input.databaseType = DatabaseType.MYSQL;
        input.schema = "shop";
        input.logFiles.add(Path.of("queries.sql"));
        input.includeTables.add("orders");

        ResolvedScanConfig resolved = input.resolve(tempDir);
        input.schema = "changed";
        input.logFiles.add(Path.of("later.sql"));
        input.includeTables.clear();

        assertEquals("shop", resolved.database().schema());
        assertEquals(java.util.List.of(queryFile.toRealPath()), resolved.sources().logFiles());
        assertEquals(java.util.List.of("orders"), resolved.database().includeTables());
        assertThrows(UnsupportedOperationException.class,
                () -> resolved.sources().logFiles().add(Path.of("forbidden.sql")));
    }

    @Test
    void jdbcVersionDiscoveryReturnsNewSnapshotWithoutMutatingInput() {
        ScanConfig input = new ScanConfig();
        input.databaseType = DatabaseType.POSTGRESQL;
        ResolvedScanConfig original = input.resolve();

        ResolvedScanConfig discovered = original.withJdbcDatabaseVersion("18.1");

        assertNotSame(original, discovered);
        assertEquals("", original.parser().databaseVersion());
        assertEquals("UNKNOWN", original.parser().databaseVersionSource());
        assertEquals("18.1", discovered.parser().databaseVersion());
        assertEquals("JDBC", discovered.parser().databaseVersionSource());
        assertEquals("", input.databaseVersion);
        assertEquals("UNKNOWN", input.databaseVersionSource);
    }

    @Test
    void configuredVersionWinsOverJdbcDiscovery() {
        ScanConfig input = new ScanConfig();
        input.databaseType = DatabaseType.SQLSERVER;
        input.databaseVersion = "2022";
        input.databaseVersionSource = "CONFIG";
        ResolvedScanConfig original = input.resolve();

        assertEquals(original, original.withJdbcDatabaseVersion("16.0"));
    }
}
