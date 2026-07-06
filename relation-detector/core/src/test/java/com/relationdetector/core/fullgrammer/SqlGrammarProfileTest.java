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
                    "mysql-5.7",
                    DatabaseType.MYSQL,
                    5,
                    7,
                    Set.of("generated_columns", "json_basic", "multi_table_dml", "stored_routines"))),
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
                    Set.of("merge", "materialized_cte", "lateral", "set_returning_functions"))),
            new FakeModule(new SqlGrammarProfile(
                    "postgresql-17",
                    DatabaseType.POSTGRESQL,
                    17,
                    0,
                    Set.of("merge", "merge_returning", "json_table", "sql_json"))),
            new FakeModule(new SqlGrammarProfile(
                    "postgresql-18",
                    DatabaseType.POSTGRESQL,
                    18,
                    0,
                    Set.of("returning_old_new", "virtual_generated_columns", "temporal_constraints"))),
            new FakeModule(new SqlGrammarProfile(
                    "oracle-12c",
                    DatabaseType.ORACLE,
                    12,
                    2,
                    Set.of("plsql", "identity_columns", "sql_json"))),
            new FakeModule(new SqlGrammarProfile(
                    "oracle-19c",
                    DatabaseType.ORACLE,
                    19,
                    0,
                    Set.of("plsql", "sql_json", "listagg_distinct"))),
            new FakeModule(new SqlGrammarProfile(
                    "oracle-21c",
                    DatabaseType.ORACLE,
                    21,
                    0,
                    Set.of("plsql", "sql_macros", "native_json"))),
            new FakeModule(new SqlGrammarProfile(
                    "oracle-26ai",
                    DatabaseType.ORACLE,
                    26,
                    0,
                    Set.of("plsql", "vector", "ai"))));

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
    void selectsMysql57ProfileFromVersionString() {
        SqlGrammarProfileSelection selection = select(DatabaseType.MYSQL, "5.7.44");

        assertEquals("mysql-5.7", selection.profile().id());
        assertEquals(DatabaseType.MYSQL, selection.profile().databaseType());
        assertEquals(5, selection.profile().majorVersion());
        assertEquals(7, selection.profile().minorVersion());
        assertTrue(selection.profile().capabilities().contains("stored_routines"));
        assertFalse(selection.usedFallback());
    }

    @Test
    void configuredMysql57ProfileOverridesVersion() {
        SqlGrammarProfileSelection selection = select(FullGrammerProfileRequest.builder()
                .databaseType(DatabaseType.MYSQL)
                .configuredProfile("mysql/5.7")
                .configuredVersion("8.0.36")
                .build());

        assertEquals("mysql-5.7", selection.profile().id());
        assertEquals("CONFIG", selection.versionSource());
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
                .configuredProfile("postgresql/17")
                .jdbcConnection(connection("PostgreSQL", 18, 1, "PostgreSQL 18.1"))
                .build());

        assertEquals("postgresql-17", selection.profile().id());
        assertEquals("CONFIG", selection.versionSource());
        assertFalse(selection.usedFallback());
    }

    @Test
    void jdbcPostgresMinorUsesSameMajorProfile() {
        SqlGrammarProfileSelection pg17 = select(FullGrammerProfileRequest.builder()
                .databaseType(DatabaseType.POSTGRESQL)
                .jdbcConnection(connection("PostgreSQL", 17, 5, "PostgreSQL 17.5"))
                .build());
        SqlGrammarProfileSelection pg18 = select(FullGrammerProfileRequest.builder()
                .databaseType(DatabaseType.POSTGRESQL)
                .jdbcConnection(connection("PostgreSQL", 18, 1, "PostgreSQL 18.1"))
                .build());

        assertEquals("postgresql-17", pg17.profile().id());
        assertEquals("JDBC", pg17.versionSource());
        assertFalse(pg17.usedFallback());
        assertEquals("postgresql-18", pg18.profile().id());
        assertEquals("JDBC", pg18.versionSource());
        assertFalse(pg18.usedFallback());
    }

    @Test
    void jdbcPostgresOneMajorAheadFallsBackWithDiagnostic() {
        SqlGrammarProfileSelection selection = select(FullGrammerProfileRequest.builder()
                .databaseType(DatabaseType.POSTGRESQL)
                .jdbcConnection(connection("PostgreSQL", 19, 1, "PostgreSQL 19.1"))
                .build());

        assertEquals("postgresql-18", selection.profile().id());
        assertEquals("JDBC", selection.versionSource());
        assertTrue(selection.usedFallback());
        assertTrue(selection.diagnostic().contains("19.1"));
    }

    @Test
    void jdbcPostgresTwoMajorsAheadDoesNotSelectFullGrammer() {
        SqlGrammarProfileSelection selection = select(FullGrammerProfileRequest.builder()
                .databaseType(DatabaseType.POSTGRESQL)
                .jdbcConnection(connection("PostgreSQL", 20, 0, "PostgreSQL 20.0"))
                .build());

        assertNull(selection.profile());
        assertEquals("JDBC", selection.versionSource());
        assertTrue(selection.usedFallback());
        assertTrue(selection.diagnostic().contains("token-event"));
    }

    @Test
    void oracleProfileCanBeSelectedByConfiguredProfileAndVersionString() {
        SqlGrammarProfileSelection configured = select(FullGrammerProfileRequest.builder()
                .databaseType(DatabaseType.ORACLE)
                .configuredProfile("oracle/12c")
                .configuredVersion("26.1")
                .build());
        SqlGrammarProfileSelection v19 = select(DatabaseType.ORACLE, "19.22.0.0.0");
        SqlGrammarProfileSelection v21 = select(DatabaseType.ORACLE, "21.11.0.0.0");
        SqlGrammarProfileSelection v26 = select(DatabaseType.ORACLE, "26.1");

        assertEquals("oracle-12c", configured.profile().id());
        assertEquals(12, configured.profile().majorVersion());
        assertEquals(2, configured.profile().minorVersion());
        assertEquals("CONFIG", configured.versionSource());
        assertEquals("oracle-19c", v19.profile().id());
        assertEquals("oracle-21c", v21.profile().id());
        assertEquals("oracle-26ai", v26.profile().id());
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
