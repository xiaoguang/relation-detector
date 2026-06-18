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
import com.relationdetector.api.DatabaseDdlDefinition;
import com.relationdetector.api.IdentifierRules;
import com.relationdetector.api.MetadataSnapshot;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.Collectors.DataProfiler;
import com.relationdetector.api.Collectors.DatabaseDdlCollector;
import com.relationdetector.api.Collectors.DdlParser;
import com.relationdetector.api.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.api.Collectors.MetadataCollector;
import com.relationdetector.api.Collectors.ObjectDefinitionCollector;
import com.relationdetector.api.Collectors.SqlLogExtractor;
import com.relationdetector.api.Collectors.SqlRelationParser;
import com.relationdetector.api.Enums.AdaptorCapability;
import com.relationdetector.api.Enums.DatabaseType;
import com.relationdetector.api.Enums.EvidenceSourceType;
import com.relationdetector.api.Enums.EvidenceType;
import com.relationdetector.api.Enums.LogFormatHint;

/**
 * Verifies that DDL read from the live database follows the same runner path as
 * file DDL while remaining distinguishable in evidence provenance.
 */
class ScanEngineDatabaseDdlSourceTest {
    private static final String JDBC_URL = "jdbc:relation-test:database-ddl";

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
    void parsesShowCreateTableDdlAsDatabaseDdlEvidence() {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;
        config.jdbcUrl = JDBC_URL;
        config.schema = "shop";
        config.metadataEnabled = false;
        config.ddlEnabled = true;
        config.ddlFromDatabase = true;
        config.minConfidence = 0.0d;

        ScanResult result = new ScanEngine().scan(config, new DatabaseDdlAdaptor());

        assertTrue(result.sources().contains("database-ddl"),
                "database DDL should be reported as an active scan source");
        RelationshipCandidate relation = result.relationships().stream()
                .filter(candidate -> candidate.source().displayName().equals("shop.orders.user_id"))
                .filter(candidate -> candidate.target().displayName().equals("shop.users.id"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected SHOW CREATE TABLE FK relation"));
        assertTrue(relation.evidence().stream().anyMatch(evidence ->
                        evidence.type() == EvidenceType.DDL_FOREIGN_KEY
                                && evidence.sourceType() == EvidenceSourceType.DATABASE_DDL
                                && evidence.source().equals("SHOW CREATE TABLE")),
                "SHOW CREATE TABLE evidence should use DATABASE_DDL, not DDL_FILE");
    }

    private static final class DatabaseDdlAdaptor implements DatabaseAdaptor {
        private final SimpleDdlParser simpleDdlParser = new SimpleDdlParser();

        @Override
        public String id() {
            return "test-database-ddl";
        }

        @Override
        public String displayName() {
            return "Test Database DDL";
        }

        @Override
        public Set<DatabaseType> supportedDatabaseTypes() {
            return Set.of(DatabaseType.MYSQL);
        }

        @Override
        public Set<AdaptorCapability> capabilities() {
            return Set.of(AdaptorCapability.DDL_PARSING);
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
        public Optional<DatabaseDdlCollector> databaseDdlCollector() {
            return Optional.of((connection, scope) -> List.of(new DatabaseDdlDefinition(
                    "shop",
                    "orders",
                    """
                            CREATE TABLE `orders` (
                              `id` bigint NOT NULL,
                              `user_id` bigint NOT NULL,
                              KEY `idx_orders_user_id` (`user_id`),
                              CONSTRAINT `fk_orders_users`
                                FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
                            ) ENGINE=InnoDB
                            """,
                    "SHOW CREATE TABLE")));
        }

        @Override
        public ObjectDefinitionCollector objectDefinitionCollector() {
            return (connection, scope) -> List.of();
        }

        @Override
        public DdlParser ddlParser() {
            return new DdlParser() {
                @Override
                public List<RelationshipCandidate> parseDdl(Path file, com.relationdetector.api.AdaptorContext context) {
                    return simpleDdlParser.parse(file);
                }

                @Override
                public List<RelationshipCandidate> parseDdlText(
                        String ddl,
                        String sourceName,
                        com.relationdetector.api.AdaptorContext context
                ) {
                    return simpleDdlParser.parseText(ddl, sourceName);
                }
            };
        }

        @Override
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
            return (statement, context) -> List.of();
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
