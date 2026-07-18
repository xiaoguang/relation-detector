package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.naming.NamingRuleConfigLoader;

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
        input.metadataEnabled = false;
        input.logsEnabled = true;
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
        input.jdbcUrl = "jdbc:test:resolved-config";
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
        input.jdbcUrl = "jdbc:test:resolved-config";
        input.databaseVersion = "2022";
        input.databaseVersionSource = "CONFIG";
        ResolvedScanConfig original = input.resolve();

        assertEquals(original, original.withJdbcDatabaseVersion("16.0"));
    }

    @Test
    void resolvesNamingRuleFilesInCoreAndDoesNotExposeThemToParserCompatibilityView() throws IOException {
        Path queryFile = tempDir.resolve("queries.sql");
        Files.writeString(queryFile, "SELECT 1;\n");
        Files.writeString(tempDir.resolve("rules.yml"), """
                rules:
                  - id: customer-owner
                    rule: USER_CONFIGURED
                    sourceEndpoint: orders.customer_id
                    targetEndpoint: customers.id
                """);
        ScanConfig input = fileScan(queryFile);
        input.namingMatchSystemRulesEnabled = false;
        input.namingMatchRuleFiles.add(Path.of("rules.yml"));

        ResolvedScanConfig resolved = input.resolve(tempDir);

        assertEquals(1, resolved.evidence().namingMatchRules().size());
        assertEquals("customer-owner", resolved.evidence().namingMatchRules().get(0).id());
        assertEquals(List.of(tempDir.resolve("rules.yml").toAbsolutePath().normalize()),
                resolved.evidence().namingMatchRuleFiles());
        assertEquals(List.of(), resolved.parserCompatibilityView().namingMatchRuleFiles);
        assertEquals(1, resolved.parserCompatibilityView().namingRuleSet().rules().size());
    }

    @Test
    void rejectsDuplicateRuleIdsAcrossFileAndInlineRules() throws IOException {
        Path queryFile = tempDir.resolve("queries.sql");
        Files.writeString(queryFile, "SELECT 1;\n");
        Files.writeString(tempDir.resolve("rules.yml"), """
                rules:
                  - id: duplicate
                    rule: USER_CONFIGURED
                    sourceEndpoint: orders.customer_id
                    targetEndpoint: customers.id
                """);
        ScanConfig input = fileScan(queryFile);
        input.namingMatchSystemRulesEnabled = false;
        input.namingMatchRuleFiles.add(Path.of("rules.yml"));
        input.namingMatchRules.addAll(new NamingRuleConfigLoader().readInlineRules(
                new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                        [{
                          "id":"duplicate",
                          "rule":"USER_CONFIGURED",
                          "sourceEndpoint":"orders.sales_rep_id",
                          "targetEndpoint":"employees.id"
                        }]
                        """)));

        assertThrows(ScanConfigurationException.class, () -> input.resolve(tempDir));
    }

    private ScanConfig fileScan(Path queryFile) {
        ScanConfig input = new ScanConfig();
        input.databaseType = DatabaseType.MYSQL;
        input.metadataEnabled = false;
        input.logsEnabled = true;
        input.logFiles.add(queryFile);
        return input;
    }
}
