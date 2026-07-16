package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.common.CommonDatabaseAdaptor;

class FinalScanContractTest {
    @TempDir
    Path tempDir;

    static java.util.stream.Stream<Consumer<ScanConfig>> configuredPathSources() {
        return java.util.stream.Stream.of(
                config -> config.ddlPaths.add(Path.of("input")),
                config -> config.objectPaths.add(Path.of("input")),
                config -> config.logPaths.add(Path.of("input")));
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

    @Test
    void propagatesJdbcConnectionFailureInsteadOfReturningARecoverableWarning() {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.COMMON;
        config.jdbcUrl = "jdbc:missing-driver:contains-secret";
        config.metadataEnabled = false;

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> new ScanEngine().scan(config, new CommonDatabaseAdaptor()));

        assertEquals(SQLException.class, failure.getCause().getClass());
    }
}
