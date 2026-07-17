package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.parse.DatabaseDdlDefinition;
import com.relationdetector.contracts.spi.AdaptorCollectors;
import com.relationdetector.contracts.spi.AdaptorParsers;
import com.relationdetector.contracts.spi.AdaptorProfiling;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.core.common.CommonDatabaseAdaptor;

class LiveSourceFailureBoundaryTest {
    private static final String JDBC_URL = "jdbc:task9:metadata-boundary";
    private TestDriver driver;

    @BeforeEach
    void registerDriver() throws SQLException {
        driver = new TestDriver();
        DriverManager.registerDriver(driver);
    }

    @AfterEach
    void deregisterDriver() throws SQLException {
        DriverManager.deregisterDriver(driver);
    }

    @Test
    void metadataCollectorFailureIsSanitizedAndDoesNotPreventLiveDdlCollection() {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.COMMON;
        config.jdbcUrl = JDBC_URL;
        config.metadataEnabled = true;
        config.ddlEnabled = true;
        config.ddlFromDatabase = true;

        ScanResult result = new ScanEngine().scan(config, new MetadataFailureAdaptor());

        assertTrue(result.sources().contains("database-ddl"));
        assertTrue(result.warnings().stream().anyMatch(warning ->
                warning.code().equals("METADATA_COLLECT_FAILED")
                        && warning.message().equals("Live database metadata collection failed")));
        assertFalse(result.warnings().stream().anyMatch(warning ->
                warning.message().contains("password=secret") || warning.message().contains(JDBC_URL)));
    }

    private static final class MetadataFailureAdaptor implements DatabaseAdaptor {
        private final CommonDatabaseAdaptor delegate = new CommonDatabaseAdaptor();

        @Override public int spiVersion() { return com.relationdetector.contracts.spi.AdaptorApiVersion.CURRENT; }
        @Override public String id() { return "metadata-failure"; }
        @Override public String displayName() { return "Metadata Failure"; }
        @Override public Set<DatabaseType> supportedDatabaseTypes() { return Set.of(DatabaseType.COMMON); }
        @Override public Set<AdaptorCapability> capabilities() {
            return Set.of(AdaptorCapability.METADATA, AdaptorCapability.DDL_PARSING);
        }
        @Override public IdentifierRules identifierRules() { return delegate.identifierRules(); }
        @Override public AdaptorCollectors collectors() {
            return new AdaptorCollectors(
                    Optional.of((connection, scope) -> {
                        throw new IllegalStateException("jdbc:secret://password=secret");
                    }),
                    Optional.empty(),
                    Optional.of((connection, scope) -> List.of(new DatabaseDdlDefinition(
                            null, "", "orders", "CREATE TABLE orders (id INTEGER);", "live-ddl"))),
                    Optional.empty());
        }
        @Override public AdaptorParsers parsers() { return delegate.parsers(); }
        @Override public AdaptorProfiling profiling() { return delegate.profiling(); }
    }

    private static final class TestDriver implements Driver {
        @Override public Connection connect(String url, Properties info) {
            if (!acceptsURL(url)) return null;
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "close" -> null;
                        case "isClosed" -> false;
                        case "getMetaData" -> throw new SQLException("not needed");
                        case "unwrap" -> null;
                        case "isWrapperFor" -> false;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
        @Override public boolean acceptsURL(String url) { return JDBC_URL.equals(url); }
        @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return new DriverPropertyInfo[0]; }
        @Override public int getMajorVersion() { return 1; }
        @Override public int getMinorVersion() { return 0; }
        @Override public boolean jdbcCompliant() { return false; }
        @Override public Logger getParentLogger() { return Logger.getGlobal(); }
    }
}
