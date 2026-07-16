package com.relationdetector.core;

import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.scan.ScanEngine;
import com.relationdetector.core.scan.ScanResult;
import com.relationdetector.core.lineage.*;
import com.relationdetector.core.parser.*;
import com.relationdetector.core.relation.*;

import com.relationdetector.core.tokenevent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.LiveSourceConfigurationException;
import com.relationdetector.contracts.parse.DatabaseObjectDefinition;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.contracts.spi.Collectors.MetadataCollector;
import com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector;
import com.relationdetector.contracts.spi.Collectors.SqlLogExtractor;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseObjectType;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.LogFormatHint;
import com.relationdetector.contracts.Enums.WarningType;

/**
 * Database object warnings must identify the object that supplied the SQL, not
 * just the file/source category. This is critical for routine-heavy schemas.
 */
class ScanEngineObjectWarningProvenanceTest {
    private static final String JDBC_URL = "jdbc:relation-test:object-warning";

    private TestDriver driver;

    @BeforeEach
    void registerDriver() throws Exception {
        driver = new TestDriver();
        DriverManager.registerDriver(driver);
    }

    @AfterEach
    void deregisterDriver() throws Exception {
        DriverManager.deregisterDriver(driver);
    }

    @Test
    void dynamicSqlWarningCarriesObjectAndRoutineIdentity() {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;
        config.jdbcUrl = JDBC_URL;
        config.schema = "shop";
        config.metadataEnabled = false;
        config.objectsEnabled = true;
        config.objectsFromDatabase = true;

        ScanResult result = new ScanEngine().scan(config, new ObjectWarningAdaptor());

        WarningMessage warning = result.warnings().stream()
                .filter(candidate -> candidate.code().equals("DYNAMIC_SQL_UNRESOLVED"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected unresolved dynamic SQL warning"));
        assertEquals("shop.rebuild_orders", warning.source());
        assertEquals("shop", warning.attributes().get("objectSchema"));
        assertEquals("rebuild_orders", warning.attributes().get("objectName"));
        assertEquals("PROCEDURE", warning.attributes().get("objectType"));
        assertEquals("shop", warning.attributes().get("routineSchema"));
        assertEquals("rebuild_orders", warning.attributes().get("routineName"));
        assertEquals("PROCEDURE", warning.attributes().get("routineType"));
        assertTrue(String.valueOf(warning.attributes().get("rawStatement")).contains("PREPARE stmt FROM @sql"));
        assertTrue(result.warnings().stream().noneMatch(candidate ->
                        candidate.code().equals("SOURCE_LINE_OUTSIDE_STATEMENT")),
                "database-object statements must cover every line in their collected SQL text");
    }

    @Test
    void thirdPartyBlankObjectDefinitionIsRejectedBeforeParsing() {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;
        config.jdbcUrl = JDBC_URL;
        config.schema = "shop";
        config.metadataEnabled = false;
        config.objectsEnabled = true;
        config.objectsFromDatabase = true;

        ScanResult result = new ScanEngine().scan(config, new BlankObjectAdaptor());

        WarningMessage warning = result.warnings().stream()
                .filter(candidate -> candidate.code().equals("DEFINITION_UNAVAILABLE"))
                .findFirst().orElseThrow();
        assertEquals(WarningType.LIVE_SOURCE_WARNING, warning.type());
        assertEquals("rebuild_orders", warning.attributes().get("objectName"));
        assertTrue(result.relationships().isEmpty());
        assertTrue(result.dataLineages().isEmpty());
    }

    @Test
    void liveConfigurationFailureEscapesScanAndClosesConnection() {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;
        config.jdbcUrl = JDBC_URL;
        config.metadataEnabled = false;
        config.objectsEnabled = true;
        config.objectsFromDatabase = true;

        assertThrows(LiveSourceConfigurationException.class,
                () -> new ScanEngine().scan(config, new ConfigurationFailureAdaptor()));
        assertTrue(driver.closed.get(), "fail-fast must still close the JDBC connection");
    }

    @Test
    void nullObjectDefinitionListProducesDefinitionUnavailable() {
        ScanResult result = scanObjects(new NullObjectListAdaptor());

        assertDefinitionUnavailableOnly(result);
    }

    @Test
    void nullObjectDefinitionElementProducesDefinitionUnavailable() {
        ScanResult result = scanObjects(new NullObjectElementAdaptor());

        assertDefinitionUnavailableOnly(result);
    }

    private ScanResult scanObjects(DatabaseAdaptor adaptor) {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;
        config.jdbcUrl = JDBC_URL;
        config.metadataEnabled = false;
        config.objectsEnabled = true;
        config.objectsFromDatabase = true;
        return new ScanEngine().scan(config, adaptor);
    }

    private void assertDefinitionUnavailableOnly(ScanResult result) {
        assertEquals(1, result.warnings().stream()
                .filter(warning -> warning.code().equals("DEFINITION_UNAVAILABLE")).count());
        assertTrue(result.warnings().stream().noneMatch(warning ->
                warning.code().equals("OBJECT_DEFINITION_COLLECT_FAILED")));
        assertTrue(result.relationships().isEmpty());
        assertTrue(result.dataLineages().isEmpty());
    }

    private static class ObjectWarningAdaptor implements DatabaseAdaptor {
        @Override
        public String id() {
            return "test-object-warning";
        }

        @Override
        public String displayName() {
            return "Test Object Warning";
        }

        @Override
        public Set<DatabaseType> supportedDatabaseTypes() {
            return Set.of(DatabaseType.MYSQL);
        }

        @Override
        public Set<AdaptorCapability> capabilities() {
            return Set.of(AdaptorCapability.DATABASE_OBJECTS);
        }

        @Override
        public IdentifierRules identifierRules() {
            return identifier -> identifier;
        }

        @Override
        public com.relationdetector.contracts.spi.AdaptorCollectors collectors() {
            return new com.relationdetector.contracts.spi.AdaptorCollectors(
                    Optional.of((connection, scope) -> new MetadataSnapshot()),
                    Optional.of((connection, scope) -> List.of(new DatabaseObjectDefinition(
                            DatabaseObjectType.PROCEDURE,
                            null,
                            "shop",
                            "rebuild_orders",
                            """
                                    CREATE PROCEDURE rebuild_orders()
                                    BEGIN
                                      SET @sql = 'SELECT * FROM orders';
                                      PREPARE stmt FROM @sql;
                                      EXECUTE stmt;
                                    END
                                    """,
                            "information_schema.ROUTINES"))),
                    Optional.empty(),
                    Optional.of((file, hint) -> Stream.empty()));
        }

        @Override
        public com.relationdetector.contracts.spi.AdaptorParsers parsers() {
            TokenEventStructuredSqlParser structured = new TokenEventStructuredSqlParser(SqlDialect.MYSQL);
            return new com.relationdetector.contracts.spi.AdaptorParsers(
                    new StructuredSqlRelationshipParser(
                            structured,
                            new StructuredRelationshipExtractor()),
                    Optional.of(structured),
                    Optional.empty(),
                    request -> com.relationdetector.contracts.parse.ScriptFrameResult.empty());
        }

        @Override
        public com.relationdetector.contracts.spi.AdaptorProfiling profiling() {
            return new com.relationdetector.contracts.spi.AdaptorProfiling(
                    Optional.empty(),
                    (evidence, context) -> evidence);
        }
    }

    private static final class BlankObjectAdaptor extends ObjectWarningAdaptor {
        @Override
        public com.relationdetector.contracts.spi.AdaptorCollectors collectors() {
            return new com.relationdetector.contracts.spi.AdaptorCollectors(
                    Optional.of((connection, scope) -> new MetadataSnapshot()),
                    Optional.of((connection, scope) -> List.of(new DatabaseObjectDefinition(
                            DatabaseObjectType.PROCEDURE, null, "shop", "rebuild_orders", " ", "test-catalog"))),
                    Optional.empty(), Optional.of((file, hint) -> Stream.empty()));
        }
    }

    private static final class ConfigurationFailureAdaptor extends ObjectWarningAdaptor {
        @Override
        public com.relationdetector.contracts.spi.AdaptorCollectors collectors() {
            return new com.relationdetector.contracts.spi.AdaptorCollectors(
                    Optional.of((connection, scope) -> new MetadataSnapshot()),
                    Optional.of((connection, scope) -> {
                        throw new LiveSourceConfigurationException("database.catalog cannot be verified");
                    }),
                    Optional.empty(), Optional.of((file, hint) -> Stream.empty()));
        }
    }

    private static final class NullObjectListAdaptor extends ObjectWarningAdaptor {
        @Override
        public com.relationdetector.contracts.spi.AdaptorCollectors collectors() {
            return new com.relationdetector.contracts.spi.AdaptorCollectors(
                    Optional.of((connection, scope) -> new MetadataSnapshot()),
                    Optional.of((connection, scope) -> null),
                    Optional.empty(), Optional.of((file, hint) -> Stream.empty()));
        }
    }

    private static final class NullObjectElementAdaptor extends ObjectWarningAdaptor {
        @Override
        public com.relationdetector.contracts.spi.AdaptorCollectors collectors() {
            return new com.relationdetector.contracts.spi.AdaptorCollectors(
                    Optional.of((connection, scope) -> new MetadataSnapshot()),
                    Optional.of((connection, scope) -> java.util.Arrays.asList((DatabaseObjectDefinition) null)),
                    Optional.empty(), Optional.of((file, hint) -> Stream.empty()));
        }
    }

    private static final class TestDriver implements Driver {
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            if (!acceptsURL(url)) {
                return null;
            }
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "close" -> {
                            closed.set(true);
                            yield null;
                        }
                        case "isClosed" -> false;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        @Override
        public boolean acceptsURL(String url) {
            return JDBC_URL.equals(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }
    }
}
