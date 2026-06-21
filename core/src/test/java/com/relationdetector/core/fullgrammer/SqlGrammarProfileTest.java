package com.relationdetector.core.fullgrammer;

import com.relationdetector.core.tokenevent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.Enums.DatabaseType;

class SqlGrammarProfileTest {
    private static final List<FullGrammerDialectModule> MODULES = List.of(
            new FakeModule(new SqlGrammarProfile(
                    "mysql-8.0",
                    DatabaseType.MYSQL,
                    8,
                    0,
                    Set.of("cte", "json_table", "window_functions", "multi_table_dml"))),
            new FakeModule(new SqlGrammarProfile(
                    "postgresql-16",
                    DatabaseType.POSTGRESQL,
                    16,
                    0,
                    Set.of("merge", "materialized_cte", "lateral", "set_returning_functions"))));

    @Test
    void selectsKnownMysqlProfileFromVersionString() {
        SqlGrammarProfileSelection selection = select(DatabaseType.MYSQL, "8.0.36");

        assertEquals("mysql-8.0", selection.profile().id());
        assertEquals(DatabaseType.MYSQL, selection.profile().databaseType());
        assertEquals(8, selection.profile().majorVersion());
        assertEquals(0, selection.profile().minorVersion());
        assertTrue(selection.profile().capabilities().contains("json_table"));
        assertFalse(selection.usedFallback());
    }

    @Test
    void mysqlMinorUsesSameMajorProfile() {
        SqlGrammarProfileSelection selection = select(DatabaseType.MYSQL, "8.4.1");

        assertEquals("mysql-8.0", selection.profile().id());
        assertFalse(selection.usedFallback());
    }

    @Test
    void selectsPostgresCurrentMajorProfile() {
        SqlGrammarProfileSelection selection = select(DatabaseType.POSTGRESQL, "16.4");

        assertEquals("postgresql-16", selection.profile().id());
        assertEquals(DatabaseType.POSTGRESQL, selection.profile().databaseType());
        assertTrue(selection.profile().capabilities().contains("materialized_cte"));
        assertFalse(selection.usedFallback());
    }

    @Test
    void configProfileOverridesJdbcVersion() {
        SqlGrammarProfileSelection selection = select(FullGrammerProfileRequest.builder()
                .databaseType(DatabaseType.POSTGRESQL)
                .configuredProfile("postgresql/16")
                .jdbcConnection(connection("PostgreSQL", 17, 2, "PostgreSQL 17.2"))
                .build());

        assertEquals("postgresql-16", selection.profile().id());
        assertEquals("CONFIG", selection.versionSource());
        assertFalse(selection.usedFallback());
    }

    @Test
    void jdbcPostgresMinorUsesSameMajorProfile() {
        SqlGrammarProfileSelection selection = select(FullGrammerProfileRequest.builder()
                .databaseType(DatabaseType.POSTGRESQL)
                .jdbcConnection(connection("PostgreSQL", 16, 5, "PostgreSQL 16.5"))
                .build());

        assertEquals("postgresql-16", selection.profile().id());
        assertEquals("JDBC", selection.versionSource());
        assertFalse(selection.usedFallback());
    }

    @Test
    void jdbcPostgresOneMajorAheadFallsBackWithDiagnostic() {
        SqlGrammarProfileSelection selection = select(FullGrammerProfileRequest.builder()
                .databaseType(DatabaseType.POSTGRESQL)
                .jdbcConnection(connection("PostgreSQL", 17, 1, "PostgreSQL 17.1"))
                .build());

        assertEquals("postgresql-16", selection.profile().id());
        assertEquals("JDBC", selection.versionSource());
        assertTrue(selection.usedFallback());
        assertTrue(selection.diagnostic().contains("17.1"));
    }

    @Test
    void jdbcPostgresTwoMajorsAheadDoesNotSelectFullGrammer() {
        SqlGrammarProfileSelection selection = select(FullGrammerProfileRequest.builder()
                .databaseType(DatabaseType.POSTGRESQL)
                .jdbcConnection(connection("PostgreSQL", 18, 0, "PostgreSQL 18.0"))
                .build());

        assertNull(selection.profile());
        assertEquals("JDBC", selection.versionSource());
        assertTrue(selection.usedFallback());
        assertTrue(selection.diagnostic().contains("token-event"));
    }

    @Test
    void missingDialectOrVersionDoesNotSelectFullGrammer() {
        SqlGrammarProfileSelection selection = select(FullGrammerProfileRequest.builder().build());

        assertNull(selection.profile());
        assertEquals("UNKNOWN", selection.versionSource());
        assertTrue(selection.usedFallback());
    }

    private Connection connection(String product, int major, int minor, String version) {
        DatabaseMetaData metadata = (DatabaseMetaData) Proxy.newProxyInstance(
                DatabaseMetaData.class.getClassLoader(),
                new Class<?>[] {DatabaseMetaData.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getDatabaseProductName" -> product;
                    case "getDatabaseMajorVersion" -> major;
                    case "getDatabaseMinorVersion" -> minor;
                    case "getDatabaseProductVersion" -> version;
                    default -> defaultValue(method.getReturnType());
                });
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> method.getName().equals("getMetaData")
                        ? metadata
                        : defaultValue(method.getReturnType()));
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == void.class) {
            return null;
        }
        return 0;
    }

    private SqlGrammarProfileSelection select(DatabaseType databaseType, String version) {
        return SqlGrammarProfileRegistry.select(FullGrammerProfileRequest.builder()
                .databaseType(databaseType)
                .configuredVersion(version)
                .build(), MODULES);
    }

    private SqlGrammarProfileSelection select(FullGrammerProfileRequest request) {
        return SqlGrammarProfileRegistry.select(request, MODULES);
    }

    private record FakeModule(SqlGrammarProfile profile) implements FullGrammerDialectModule {
        @Override
        public String implementationName() {
            return "FAKE";
        }

        @Override
        public StructuredSqlParser sqlParser() {
            return (statement, context) -> new com.relationdetector.contracts.parse.StructuredParseResult("FAKE", "MYSQL", statement.sourceName(), java.util.List.of(), java.util.List.of(), java.util.Map.of());
        }

        @Override
        public StructuredDdlParser structuredDdlParser() {
            return (ddl, sourceName, context) -> new com.relationdetector.contracts.parse.StructuredParseResult(
                    "FAKE",
                    profile.databaseType().name(),
                    sourceName,
                    List.of(),
                    List.of(),
                    java.util.Map.of());
        }
    }
}
