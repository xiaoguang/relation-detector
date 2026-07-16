package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.spi.AdaptorApiVersion;
import com.relationdetector.contracts.spi.AdaptorCollectors;
import com.relationdetector.contracts.spi.AdaptorParsers;
import com.relationdetector.contracts.spi.AdaptorProfiling;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.core.common.CommonDatabaseAdaptor;

class ScanCapabilityValidatorTest {
    private final RecordingDriver driver = new RecordingDriver();

    @AfterEach
    void unregisterDriver() throws SQLException {
        DriverManager.deregisterDriver(driver);
    }
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

    @Test
    void rejectsWrongSpiBeforeOpeningJdbc() throws Exception {
        assertRejectedBeforeConnection(new TestAdaptor(AdaptorApiVersion.CURRENT - 1, "common",
                Set.of(DatabaseType.COMMON), Set.of(), new AdaptorCollectors(null, null, null, null),
                new CommonDatabaseAdaptor().parsers()), "SPI");
    }

    @Test
    void rejectsUnsupportedDatabaseTypeBeforeOpeningJdbc() throws Exception {
        assertRejectedBeforeConnection(new TestAdaptor(AdaptorApiVersion.CURRENT, "common",
                Set.of(DatabaseType.MYSQL), Set.of(), new AdaptorCollectors(null, null, null, null),
                new CommonDatabaseAdaptor().parsers()), "database type");
    }

    @Test
    void rejectsWrongExplicitAdaptorIdBeforeOpeningJdbc() throws Exception {
        ScanConfig config = liveConfig();
        config.adaptorId = "configured";

        DriverManager.registerDriver(driver);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ScanEngine().scan(config, new CommonDatabaseAdaptor()));

        assertTrue(error.getMessage().contains("adaptor id"));
        assertTrue(driver.connections.get() == 0);
    }

    @Test
    void rejectsLiveDdlWithoutStructuredDdlParserBeforeOpeningJdbc() throws Exception {
        AdaptorParsers parsers = new AdaptorParsers(new CommonDatabaseAdaptor().parsers().sqlRelations(),
                new CommonDatabaseAdaptor().parsers().structuredSql(), Optional.empty(),
                new CommonDatabaseAdaptor().parsers().scriptFramer());
        assertRejectedBeforeConnection(new TestAdaptor(AdaptorApiVersion.CURRENT, "common", Set.of(DatabaseType.COMMON),
                Set.of(AdaptorCapability.DDL_PARSING), new AdaptorCollectors(null, null,
                        Optional.of((connection, scope) -> java.util.List.of()), null), parsers), "structured DDL parser");
    }

    @Test
    void rejectsLiveObjectsWithoutStructuredSqlParserBeforeOpeningJdbc() throws Exception {
        AdaptorParsers parsers = new AdaptorParsers(new CommonDatabaseAdaptor().parsers().sqlRelations(),
                Optional.empty(), new CommonDatabaseAdaptor().parsers().structuredDdl(),
                new CommonDatabaseAdaptor().parsers().scriptFramer());
        ScanConfig config = liveConfig();
        config.ddlEnabled = false;
        config.objectsEnabled = true;
        config.objectsFromDatabase = true;
        DriverManager.registerDriver(driver);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ScanEngine().scan(config, new TestAdaptor(AdaptorApiVersion.CURRENT, "common",
                        Set.of(DatabaseType.COMMON), Set.of(AdaptorCapability.DATABASE_OBJECTS),
                        new AdaptorCollectors(null, Optional.of((connection, scope) -> java.util.List.of()), null, null), parsers)));

        assertTrue(error.getMessage().contains("structured SQL parser"));
        assertTrue(driver.connections.get() == 0);
    }

    private void assertRejectedBeforeConnection(DatabaseAdaptor adaptor, String expectedMessage) throws Exception {
        DriverManager.registerDriver(driver);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ScanEngine().scan(liveConfig(), adaptor));

        assertTrue(error.getMessage().contains(expectedMessage));
        assertTrue(driver.connections.get() == 0);
    }

    private ScanConfig liveConfig() {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.COMMON;
        config.jdbcUrl = RecordingDriver.URL;
        config.metadataEnabled = false;
        config.ddlEnabled = true;
        config.ddlFromDatabase = true;
        return config;
    }

    private record TestAdaptor(
            int spiVersion,
            String id,
            Set<DatabaseType> supportedDatabaseTypes,
            Set<AdaptorCapability> capabilities,
            AdaptorCollectors collectors,
            AdaptorParsers parsers
    ) implements DatabaseAdaptor {
        @Override public String displayName() { return id; }
        @Override public IdentifierRules identifierRules() { return value -> value; }
        @Override public AdaptorProfiling profiling() { return new AdaptorProfiling(Optional.empty(), (evidence, context) -> evidence); }
    }

    private static final class RecordingDriver implements Driver {
        static final String URL = "jdbc:recording-contract:test";
        private final AtomicInteger connections = new AtomicInteger();

        @Override public Connection connect(String url, Properties info) throws SQLException {
            if (!acceptsURL(url)) return null;
            connections.incrementAndGet();
            throw new SQLException("connection should not be opened");
        }
        @Override public boolean acceptsURL(String url) { return URL.equals(url); }
        @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return new DriverPropertyInfo[0]; }
        @Override public int getMajorVersion() { return 1; }
        @Override public int getMinorVersion() { return 0; }
        @Override public boolean jdbcCompliant() { return false; }
        @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }
    }
}
