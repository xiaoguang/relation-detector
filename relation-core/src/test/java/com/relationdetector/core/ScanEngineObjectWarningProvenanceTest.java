package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.relationdetector.api.DatabaseAdaptor;
import com.relationdetector.api.DatabaseObjectDefinition;
import com.relationdetector.api.IdentifierRules;
import com.relationdetector.api.MetadataSnapshot;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.WarningMessage;
import com.relationdetector.api.Collectors.DataProfiler;
import com.relationdetector.api.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.api.Collectors.MetadataCollector;
import com.relationdetector.api.Collectors.ObjectDefinitionCollector;
import com.relationdetector.api.Collectors.SqlLogExtractor;
import com.relationdetector.api.Collectors.SqlRelationParser;
import com.relationdetector.api.Enums.AdaptorCapability;
import com.relationdetector.api.Enums.DatabaseObjectType;
import com.relationdetector.api.Enums.DatabaseType;
import com.relationdetector.api.Enums.LogFormatHint;

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
    }

    private static final class ObjectWarningAdaptor implements DatabaseAdaptor {
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
        public MetadataCollector metadataCollector() {
            return (connection, scope) -> new MetadataSnapshot();
        }

        @Override
        public ObjectDefinitionCollector objectDefinitionCollector() {
            return (connection, scope) -> List.of(new DatabaseObjectDefinition(
                    DatabaseObjectType.PROCEDURE,
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
                    "information_schema.ROUTINES"));
        }

        public SqlLogExtractor sqlLogExtractor() {
            return new SqlLogExtractor() {
                @Override
                public Stream<SqlStatementRecord> extract(Path file, LogFormatHint hint) {
                    return Stream.empty();
                }
            };
        }

        @Override
        public SqlRelationParser sqlRelationParser() {
            return new TokenEventSqlRelationParser(
                    new TokenEventStructuredSqlParser(SqlDialect.MYSQL),
                    new TokenEventRelationExtractor());
        }

        @Override
        public Optional<DataProfiler> dataProfiler() {
            return Optional.empty();
        }

        @Override
        public EvidenceWeightAdjuster evidenceWeightAdjuster() {
            return (evidence, context) -> evidence;
        }
    }

    private static final class TestDriver implements Driver {
        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            if (!acceptsURL(url)) {
                return null;
            }
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "close" -> null;
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
