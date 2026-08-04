package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseObjectType;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.DatabaseDdlDefinition;
import com.relationdetector.contracts.parse.DatabaseObjectDefinition;
import com.relationdetector.contracts.parse.ScriptFrameResult;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.spi.AdaptorApiVersion;
import com.relationdetector.contracts.spi.AdaptorCollectors;
import com.relationdetector.contracts.spi.AdaptorParsers;
import com.relationdetector.contracts.spi.AdaptorProfiling;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.adaptor.AdaptorContractException;
import com.relationdetector.core.config.ScanConfig;
import com.relationdetector.core.output.JsonResultWriter;
import com.relationdetector.core.result.ScanResult;
import com.relationdetector.core.scan.ScanEngine;

class ScanEngineLiveParserDiagnosticBoundaryTest {
    private static final String JDBC_URL = "jdbc:relation-test:live-parser-diagnostics";
    private static final String SECRET =
            "jdbc:mysql://secret-host/private?password=hunter2 SELECT password FROM customer_secret";

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

    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void liveObjectCallbackReturnedAndFailureDiagnosticsAreSanitized(int parallelism) {
        ScanResult result = new ScanEngine().scan(
                objectConfig(parallelism), new LiveObjectStructuredAdaptor(false));

        Map<String, Object> successIdentity = Map.of(
                "objectCatalog", "tenant",
                "objectSchema", "shop",
                "objectName", "safe_proc",
                "objectType", "PROCEDURE");
        assertDiagnostic(result.warnings(), "OBJECT_CALLBACK", "tenant.shop.safe_proc",
                "Live database object parser reported a diagnostic", successIdentity);
        assertDiagnostic(result.warnings(), "OBJECT_RETURNED", "tenant.shop.safe_proc",
                "Live database object parser reported a diagnostic", successIdentity);
        assertDiagnostic(result.warnings(), "SQL_PARSE_FAILED", "tenant.shop.failure_proc",
                "Live database object parser reported a diagnostic", Map.of(
                        "objectCatalog", "tenant",
                        "objectSchema", "shop",
                        "objectName", "failure_proc",
                        "objectType", "PROCEDURE",
                        "exceptionClass", IllegalStateException.class.getName()));
        assertNoCanary(result);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void liveDatabaseDdlCallbackReturnedAndFailureDiagnosticsAreSanitized(int parallelism) {
        ScanResult result = new ScanEngine().scan(
                ddlConfig(parallelism), new LiveDatabaseDdlAdaptor());

        Map<String, Object> successIdentity = Map.of(
                "objectCatalog", "tenant",
                "objectSchema", "shop",
                "objectName", "orders",
                "objectType", "TABLE");
        assertDiagnostic(result.warnings(), "DDL_CALLBACK", "tenant.shop.orders",
                "Live database DDL parser reported a diagnostic", successIdentity);
        assertDiagnostic(result.warnings(), "DDL_RETURNED", "tenant.shop.orders",
                "Live database DDL parser reported a diagnostic", successIdentity);
        assertDiagnostic(result.warnings(), "DDL_PARSE_FAILED", "tenant.shop.customers",
                "Live database DDL parser reported a diagnostic", Map.of(
                        "objectCatalog", "tenant",
                        "objectSchema", "shop",
                        "objectName", "customers",
                        "objectType", "TABLE",
                        "exceptionClass", IllegalArgumentException.class.getName()));
        assertNoCanary(result);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void invalidLiveParserWarningContractFailsAtomically(int parallelism) {
        AdaptorContractException failure = assertThrows(AdaptorContractException.class,
                () -> new ScanEngine().scan(
                        objectConfig(parallelism), new LiveObjectStructuredAdaptor(true)));

        assertFalse(failure.toString().contains(SECRET));
    }

    private ScanConfig objectConfig(int parallelism) {
        ScanConfig config = baseConfig(parallelism);
        config.objectsEnabled = true;
        config.objectsFromDatabase = true;
        config.minConfidence = 0.0d;
        return config;
    }

    private ScanConfig ddlConfig(int parallelism) {
        ScanConfig config = baseConfig(parallelism);
        config.ddlEnabled = true;
        config.ddlFromDatabase = true;
        return config;
    }

    private ScanConfig baseConfig(int parallelism) {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;
        config.jdbcUrl = JDBC_URL;
        config.catalog = "tenant";
        config.schema = "shop";
        config.metadataEnabled = false;
        config.databaseVersion = "8.0";
        config.executionParallelism = parallelism;
        return config;
    }

    private void assertDiagnostic(
            List<WarningMessage> warnings,
            String code,
            String source,
            String message,
            Map<String, Object> attributes
    ) {
        WarningMessage warning = warnings.stream()
                .filter(candidate -> candidate.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing diagnostic " + code + ": " + warnings));
        assertEquals(WarningType.PARSE_WARNING, warning.type());
        assertEquals(message, warning.message());
        assertEquals(source, warning.source());
        assertEquals(0, warning.line());
        assertEquals(attributes, warning.attributes());
    }

    private void assertNoCanary(ScanResult result) {
        String json = new JsonResultWriter().write(result, true, true);
        assertFalse(json.contains(SECRET), json);
        assertFalse(json.contains("secret-host"), json);
        assertFalse(json.contains("hunter2"), json);
        assertFalse(json.contains("customer_secret"), json);
    }

    private abstract static class BaseAdaptor implements DatabaseAdaptor {
        @Override public int spiVersion() { return AdaptorApiVersion.CURRENT; }
        @Override public String displayName() { return "Live Parser Boundary"; }
        @Override public Set<DatabaseType> supportedDatabaseTypes() { return Set.of(DatabaseType.MYSQL); }
        @Override public IdentifierRules identifierRules() {
            return value -> value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
        }
        @Override public AdaptorProfiling profiling() {
            return new AdaptorProfiling(Optional.empty(), (evidence, context) -> evidence);
        }

        AdaptorCollectors collectors(
                Optional<com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector> objects,
                Optional<com.relationdetector.contracts.spi.Collectors.DatabaseDdlCollector> ddl
        ) {
            return new AdaptorCollectors(
                    Optional.of((connection, scope) -> new MetadataSnapshot()),
                    objects,
                    ddl,
                    Optional.of((file, hint) -> Stream.empty()));
        }

        AdaptorParsers parsers(
                SqlRelationParser fallback,
                Optional<StructuredSqlParser> sql,
                Optional<StructuredDdlParser> ddl
        ) {
            return new AdaptorParsers(fallback, sql, ddl, request -> ScriptFrameResult.empty());
        }
    }

    private static final class LiveObjectStructuredAdaptor extends BaseAdaptor {
        private final boolean invalidWarning;

        private LiveObjectStructuredAdaptor(boolean invalidWarning) {
            this.invalidWarning = invalidWarning;
        }

        @Override public String id() { return "live-object-structured"; }
        @Override public Set<AdaptorCapability> capabilities() {
            return Set.of(AdaptorCapability.DATABASE_OBJECTS);
        }
        @Override public AdaptorCollectors collectors() {
            return collectors(Optional.of((connection, scope) -> objectDefinitions()), Optional.empty());
        }
        @Override public AdaptorParsers parsers() {
            StructuredSqlParser parser = (statement, context) -> {
                if (statement.sourceName().endsWith("failure_proc")) {
                    throw new IllegalStateException(SECRET);
                }
                context.warn(parserWarning("OBJECT_CALLBACK", statement, SECRET));
                WarningMessage returned = invalidWarning
                        ? WarningMessage.warn(WarningType.LIVE_SOURCE_WARNING, "INVALID_WARNING",
                                SECRET, statement.sourceName(), statement.startLine())
                        : parserWarning("OBJECT_RETURNED", statement, SECRET);
                return new StructuredParseResult(
                        "malicious", "mysql", statement.sourceName(), List.of(), List.of(returned), Map.of());
            };
            return parsers((statement, context) -> List.of(), Optional.of(parser), Optional.empty());
        }
    }

    private static final class LiveDatabaseDdlAdaptor extends BaseAdaptor {
        @Override public String id() { return "live-database-ddl"; }
        @Override public Set<AdaptorCapability> capabilities() {
            return Set.of(AdaptorCapability.DDL_PARSING);
        }
        @Override public AdaptorCollectors collectors() {
            return collectors(Optional.empty(), Optional.of((connection, scope) -> List.of(
                    new DatabaseDdlDefinition("tenant", "shop", "orders",
                            "CREATE TABLE orders (id BIGINT)", SECRET),
                    new DatabaseDdlDefinition("tenant", "shop", "customers",
                            "CREATE TABLE customers (id BIGINT) /* " + SECRET + " */", SECRET))));
        }
        @Override public AdaptorParsers parsers() {
            StructuredDdlParser parser = (ddl, source, context) -> {
                if (ddl.contains("customers")) {
                    throw new IllegalArgumentException(SECRET);
                }
                context.warn(WarningMessage.warn(
                        WarningType.PARSE_WARNING, "DDL_CALLBACK", SECRET, source, 1,
                        maliciousAttributes()));
                return new StructuredParseResult(
                        "malicious", "mysql", source, List.of(), List.of(WarningMessage.warn(
                                WarningType.PARSE_WARNING, "DDL_RETURNED", SECRET, source, 1,
                                maliciousAttributes())), Map.of());
            };
            return parsers((statement, context) -> List.of(), Optional.empty(), Optional.of(parser));
        }
    }

    private static List<DatabaseObjectDefinition> objectDefinitions() {
        return List.of(
                new DatabaseObjectDefinition(
                        DatabaseObjectType.PROCEDURE, "tenant", "shop", "safe_proc",
                        "CREATE PROCEDURE safe_proc() BEGIN SELECT 1; END /* " + SECRET + " */",
                        SECRET),
                new DatabaseObjectDefinition(
                        DatabaseObjectType.PROCEDURE, "tenant", "shop", "failure_proc",
                        "CREATE PROCEDURE failure_proc() BEGIN SELECT 2; END /* " + SECRET + " */",
                        SECRET));
    }

    private static WarningMessage parserWarning(String code, SqlStatementRecord statement, String message) {
        return WarningMessage.warn(
                WarningType.PARSE_WARNING,
                code,
                message,
                statement.sourceName(),
                statement.startLine(),
                maliciousAttributes());
    }

    private static Map<String, Object> maliciousAttributes() {
        return Map.of(
                "rawStatement", SECRET,
                "objectName", "spoofed-secret-object",
                "exceptionClass", "evil.secret.Driver",
                "sourceEndpoint", SECRET);
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

        @Override public boolean acceptsURL(String url) { return JDBC_URL.equals(url); }
        @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }
        @Override public int getMajorVersion() { return 1; }
        @Override public int getMinorVersion() { return 0; }
        @Override public boolean jdbcCompliant() { return false; }
        @Override public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }
    }
}
