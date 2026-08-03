package com.relationdetector.core.scan;

import com.relationdetector.core.scan.ScanEngine;

import com.relationdetector.core.result.ScanResult;

import com.relationdetector.core.config.ResolvedScanConfig;

import com.relationdetector.core.config.ScanConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.DdlInventoryCoverage;
import com.relationdetector.contracts.Enums.MetadataInventoryBasis;
import com.relationdetector.contracts.Enums.MetadataInventoryStatus;
import com.relationdetector.contracts.spi.AdaptorCollectors;
import com.relationdetector.contracts.spi.AdaptorParsers;
import com.relationdetector.contracts.spi.AdaptorProfiling;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.core.adaptor.common.CommonDatabaseAdaptor;

class FinalScanContractTest {
    @TempDir
    Path tempDir;

    static java.util.stream.Stream<Consumer<ScanConfig>> configuredPathSources() {
        return java.util.stream.Stream.of(
                config -> {
                    config.ddlEnabled = true;
                    config.ddlFromDatabase = false;
                    config.ddlPaths.add(Path.of("input"));
                },
                config -> {
                    config.objectsEnabled = true;
                    config.objectPaths.add(Path.of("input"));
                },
                config -> {
                    config.logsEnabled = true;
                    config.logPaths.add(Path.of("input"));
                });
    }

    @ParameterizedTest
    @MethodSource("configuredPathSources")
    void resolvesConfiguredPathsAndIncludesForEveryFileSource(Consumer<ScanConfig> configure) throws Exception {
        Path input = Files.createDirectories(tempDir.resolve("input/nested"));
        Path first = Files.writeString(input.resolve("a.sql"), "SELECT 1;").toRealPath();
        Path second = Files.writeString(input.resolve("b.sql"), "SELECT 2;").toRealPath();
        Files.writeString(input.resolve("ignored.txt"), "ignore");

        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.COMMON;
        config.metadataEnabled = false;
        configure.accept(config);
        if (!config.ddlPaths.isEmpty()) config.ddlIncludes.add("**/*.sql");
        if (!config.objectPaths.isEmpty()) config.objectIncludes.add("**/*.sql");
        if (!config.logPaths.isEmpty()) config.logIncludes.add("**/*.sql");

        ResolvedScanConfig resolved = config.resolve(tempDir);

        List<Path> expected = List.of(first, second);
        List<Path> actual = !resolved.sources().ddlFiles().isEmpty() ? resolved.sources().ddlFiles()
                : !resolved.sources().objectFiles().isEmpty() ? resolved.sources().objectFiles()
                : resolved.sources().logFiles();
        assertEquals(expected, actual);
    }

    @Test
    void processesDdlPathsWhenScanEngineIsCalledDirectly() throws Exception {
        Path ddl = writeSchema();
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

    @Test
    void completeScopeDdlPublishesEvidenceBackedMetadataInventory() throws Exception {
        ScanConfig config = ddlOnlyConfig(writeSchema());
        config.ddlInventoryCoverage = DdlInventoryCoverage.COMPLETE_SCOPE;

        ScanResult result = new ScanEngine().scan(config, new CommonDatabaseAdaptor());

        assertEquals(MetadataInventoryStatus.COMPLETE, result.metadataInventory().status());
        assertEquals(MetadataInventoryBasis.DDL_DECLARATIONS, result.metadataInventory().basis());
        assertEquals(List.of("contracts", "users"), result.metadataInventory().tables().stream()
                .map(com.relationdetector.contracts.metadata.MetadataTableFact::tableName)
                .sorted()
                .toList());
        assertEquals(List.of("contracts.party_id", "users.id"),
                result.metadataInventory().columns().stream()
                        .map(column -> column.tableName() + "." + column.columnName())
                        .sorted()
                        .toList());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void completeScopeDdlParseFailureDowngradesMixedInventoryToPartial(int parallelism) throws Exception {
        Path valid = Files.writeString(tempDir.resolve("01-valid.sql"), """
                CREATE TABLE users (
                  id BIGINT PRIMARY KEY
                );
                """);
        Path invalid = Files.writeString(tempDir.resolve("02-invalid.sql"), """
                CREATE TABLE broken_table (
                  id BIGINT PRIMARY KEY
                );
                """);
        ScanConfig config = ddlOnlyConfig(valid);
        config.ddlFiles.add(invalid);
        config.ddlInventoryCoverage = DdlInventoryCoverage.COMPLETE_SCOPE;
        config.executionParallelism = parallelism;

        ScanResult result = new ScanEngine().scan(
                config,
                faultingDdlAdaptor(ddl -> ddl.contains("broken_table")));

        assertEquals(MetadataInventoryStatus.PARTIAL, result.metadataInventory().status());
        assertEquals(MetadataInventoryBasis.DDL_DECLARATIONS, result.metadataInventory().basis());
        assertEquals(List.of("users"), result.metadataInventory().tables().stream()
                .map(com.relationdetector.contracts.metadata.MetadataTableFact::tableName)
                .toList());
    }

    @Test
    void completeScopeDdlParseFailureWithoutFactsProducesUnavailableInventory() throws Exception {
        Path invalid = Files.writeString(tempDir.resolve("invalid-only.sql"), """
                CREATE TABLE broken_table (
                  id BIGINT PRIMARY KEY
                );
                """);
        ScanConfig config = ddlOnlyConfig(invalid);
        config.ddlInventoryCoverage = DdlInventoryCoverage.COMPLETE_SCOPE;

        ScanResult result = new ScanEngine().scan(
                config,
                faultingDdlAdaptor(ddl -> ddl.contains("broken_table")));

        assertEquals(MetadataInventoryStatus.UNAVAILABLE, result.metadataInventory().status());
        assertEquals(MetadataInventoryBasis.DDL_DECLARATIONS, result.metadataInventory().basis());
    }

    @Test
    void completeScopeIncludesTypedDdlDeclarationsFromMixedLogFilesBeforeSqlParsing() throws Exception {
        Path mixed = Files.writeString(tempDir.resolve("mixed.sql"), """
                CREATE TABLE margin_demo (
                  sku VARCHAR(50),
                  sales_amount DECIMAL(18,2)
                );
                UPDATE margin_demo
                SET sales_amount = sales_amount * 1.05
                WHERE sku = 'SKU-1';
                """);
        ScanConfig config = ddlOnlyConfig(writeSchema());
        config.ddlInventoryCoverage = DdlInventoryCoverage.COMPLETE_SCOPE;
        config.logsEnabled = true;
        config.logFiles.add(mixed);

        ScanResult result = new ScanEngine().scan(config, new CommonDatabaseAdaptor());

        assertEquals(MetadataInventoryStatus.COMPLETE, result.metadataInventory().status());
        assertEquals(List.of("contracts", "margin_demo", "users"),
                result.metadataInventory().tables().stream()
                        .map(com.relationdetector.contracts.metadata.MetadataTableFact::tableName)
                        .sorted()
                        .toList());
        assertTrue(result.dataLineages().stream().anyMatch(lineage ->
                lineage.target().table().tableName().equals("margin_demo")
                        && lineage.sources().stream().anyMatch(source ->
                                source.table().tableName().equals("margin_demo")
                                        && source.column().columnName().equals("sales_amount"))
                        && lineage.target().column().columnName().equals("sales_amount")));
    }

    @Test
    void evidenceOnlyDdlDoesNotClaimCompleteMetadataInventory() throws Exception {
        ScanResult result = new ScanEngine().scan(
                ddlOnlyConfig(writeSchema()),
                new CommonDatabaseAdaptor());

        assertEquals(MetadataInventoryStatus.NOT_REQUESTED, result.metadataInventory().status());
        assertEquals(MetadataInventoryBasis.NONE, result.metadataInventory().basis());
    }

    @Test
    void propagatesJdbcConnectionFailureInsteadOfReturningARecoverableWarning() throws Exception {
        Path log = Files.writeString(tempDir.resolve("query.sql"), "SELECT 1;");
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.COMMON;
        config.jdbcUrl = "jdbc:missing-driver:contains-secret";
        config.metadataEnabled = false;
        config.logsEnabled = true;
        config.logFiles.add(log);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> new ScanEngine().scan(config, new CommonDatabaseAdaptor()));

        assertEquals(SQLException.class, failure.getCause().getClass());
    }

    private ScanConfig ddlOnlyConfig(Path ddl) {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.COMMON;
        config.metadataEnabled = false;
        config.ddlEnabled = true;
        config.ddlFromDatabase = false;
        config.ddlFiles.add(ddl);
        return config;
    }

    private Path writeSchema() throws Exception {
        return Files.writeString(tempDir.resolve("schema.sql"), """
                CREATE TABLE users (
                  id BIGINT PRIMARY KEY
                );
                CREATE TABLE contracts (
                  party_id BIGINT,
                  FOREIGN KEY (party_id) REFERENCES users(id)
                );
                """);
    }

    private DatabaseAdaptor faultingDdlAdaptor(Predicate<String> failure) {
        DatabaseAdaptor delegate = new CommonDatabaseAdaptor();
        AdaptorParsers parsers = delegate.parsers();
        var structuredDdl = parsers.structuredDdl().orElseThrow();
        AdaptorParsers faultingParsers = new AdaptorParsers(
                parsers.sqlRelations(),
                parsers.structuredSql(),
                Optional.of((ddl, source, context) -> {
                    if (failure.test(ddl)) {
                        throw new IllegalStateException("synthetic DDL parse failure");
                    }
                    return structuredDdl.parseDdl(ddl, source, context);
                }),
                parsers.scriptFramer());
        return new DatabaseAdaptor() {
            @Override public int spiVersion() { return delegate.spiVersion(); }
            @Override public String id() { return "faulting-common-ddl"; }
            @Override public String displayName() { return "Faulting Common DDL"; }
            @Override public Set<DatabaseType> supportedDatabaseTypes() {
                return delegate.supportedDatabaseTypes();
            }
            @Override public Set<AdaptorCapability> capabilities() { return delegate.capabilities(); }
            @Override public IdentifierRules identifierRules() { return delegate.identifierRules(); }
            @Override public AdaptorCollectors collectors() { return delegate.collectors(); }
            @Override public AdaptorParsers parsers() { return faultingParsers; }
            @Override public AdaptorProfiling profiling() { return delegate.profiling(); }
        };
    }
}
