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

import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.parse.DatabaseDdlDefinition;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.Collectors.DatabaseDdlCollector;
import com.relationdetector.contracts.spi.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.contracts.spi.Collectors.MetadataCollector;
import com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector;
import com.relationdetector.contracts.spi.Collectors.SqlLogExtractor;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.LogFormatHint;

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
        public com.relationdetector.contracts.spi.AdaptorCollectors collectors() {
            return new com.relationdetector.contracts.spi.AdaptorCollectors(
                    (connection, scope) -> new MetadataSnapshot(),
                    (connection, scope) -> List.of(),
                    Optional.of((connection, scope) -> List.of(new DatabaseDdlDefinition(
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
                            "SHOW CREATE TABLE"))),
                    (file, hint) -> Stream.empty());
        }

        @Override
        public com.relationdetector.contracts.spi.AdaptorParsers parsers() {
            return new com.relationdetector.contracts.spi.AdaptorParsers(
                    (statement, context) -> List.of(),
                    Optional.empty(),
                    Optional.of(new TokenEventStructuredDdlParser(SqlDialect.MYSQL)));
        }

        @Override
        public com.relationdetector.contracts.spi.AdaptorProfiling profiling() {
            return new com.relationdetector.contracts.spi.AdaptorProfiling(
                    Optional.empty(),
                    (evidence, context) -> evidence);
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
