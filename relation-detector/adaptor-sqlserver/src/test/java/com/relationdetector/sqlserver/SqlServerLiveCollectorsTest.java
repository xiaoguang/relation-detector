package com.relationdetector.sqlserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.contracts.spi.LiveSourceConfigurationException;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.sqlserver.metadata.SqlServerMetadataCollector;
import com.relationdetector.sqlserver.objects.SqlServerObjectCollector;

class SqlServerLiveCollectorsTest {
    @Test
    void adaptorOwnsSqlServerPermissionVendorCodes() {
        assertEquals(java.util.Set.of(229, 916), new SqlServerDatabaseAdaptor().permissionDeniedVendorCodes());
    }

    @Test
    void rejectsExplicitCatalogThatDoesNotMatchConnectionBeforeCatalogQuery() {
        AtomicInteger preparedStatements = new AtomicInteger();
        Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Connection.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getCatalog" -> "ERPDB";
                    case "prepareStatement" -> {
                        preparedStatements.incrementAndGet();
                        throw new AssertionError("catalog SQL must not run");
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });

        assertThrows(LiveSourceConfigurationException.class, () -> new SqlServerMetadataCollector().collect(connection,
                new ScanScope("OTHER", "dbo", List.of(), List.of())));
        assertEquals(0, preparedStatements.get());
    }

    @Test
    void collectsCatalogFactsForeignKeyAndObjectDefinition() throws Exception {
        Connection connection = connection();
        ScanScope scope = new ScanScope("ERPDB", "dbo", List.of(), List.of());

        var metadata = new SqlServerMetadataCollector().collect(connection, scope);
        var objects = new SqlServerObjectCollector().collect(connection, scope);

        assertEquals(1, metadata.tableFacts().size());
        assertEquals("ERPDB", metadata.tableFacts().get(0).catalog());
        assertEquals(1, metadata.columnFacts().size());
        assertEquals(2, metadata.constraintFacts().size());
        assertEquals(1, metadata.indexFacts().size());
        assertEquals("ERPDB.dbo.orders.user_id", metadata.relationships().get(0).source().displayName());
        assertEquals("ERPDB.dbo.users.id", metadata.relationships().get(0).target().displayName());
        assertEquals("ERPDB", objects.get(0).catalog());
        assertTrue(objects.get(0).sql().contains("CREATE PROCEDURE"));
    }

    @Test
    void unavailableModuleDefinitionProducesWarningAndNoShellObject() {
        List<com.relationdetector.contracts.model.WarningMessage> warnings = new java.util.ArrayList<>();
        var objects = new SqlServerObjectCollector().collect(connectionWithNullDefinition(),
                new ScanScope("ERPDB", "dbo", List.of(), List.of()), warnings::add);

        assertTrue(objects.isEmpty());
        assertEquals(WarningType.LIVE_SOURCE_WARNING, warnings.get(0).type());
        assertEquals("DEFINITION_UNAVAILABLE", warnings.get(0).code());
        assertEquals("ERPDB", warnings.get(0).attributes().get("objectCatalog"));
        assertEquals("dbo", warnings.get(0).attributes().get("objectSchema"));
        assertEquals("rebuild_orders", warnings.get(0).attributes().get("objectName"));
    }

    private Connection connection() {
        return connection(false);
    }

    private Connection connectionWithNullDefinition() {
        return connection(true);
    }

    private Connection connection(boolean nullDefinition) {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement")) return statement(String.valueOf(args[0]), nullDefinition);
                    if (method.getName().equals("getCatalog")) return "ERPDB";
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private PreparedStatement statement(String sql, boolean nullDefinition) {
        return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {PreparedStatement.class}, (proxy, method, args) -> {
                    if (method.getName().equals("setString") || method.getName().equals("close")) return null;
                    if (method.getName().equals("executeQuery")) return rows(sql, nullDefinition);
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private java.sql.ResultSet rows(String sql, boolean nullDefinition) throws Exception {
        if (sql.contains("sys.key_constraints")) return row(values(
                "schema_name", "dbo", "table_name", "users", "constraint_name", "PK_USERS",
                "constraint_type", "PK", "column_name", "id", "key_ordinal", 1));
        if (sql.contains("sys.foreign_keys")) return row(values(
                "source_schema", "dbo", "source_table", "orders", "source_column", "user_id",
                "target_schema", "dbo", "target_table", "users", "target_column", "id",
                "constraint_name", "FK_ORDERS_USERS", "constraint_column_id", 1,
                "update_referential_action_desc", "NO_ACTION", "delete_referential_action_desc", "NO_ACTION"));
        if (sql.contains("sys.indexes")) return row(values(
                "schema_name", "dbo", "table_name", "orders", "index_name", "IX_ORDERS_USER",
                "is_unique", false, "is_primary_key", false, "type_desc", "NONCLUSTERED",
                "is_disabled", false, "column_name", "user_id", "key_ordinal", 1, "is_included_column", false));
        if (sql.contains("sys.sql_modules")) return row(values(
                "schema_name", "dbo", "object_name", "rebuild_orders", "object_type", "P",
                "definition", nullDefinition ? null : "CREATE PROCEDURE dbo.rebuild_orders AS SELECT 1"));
        if (sql.contains("sys.tables")) return row(values(
                "schema_name", "dbo", "table_name", "orders", "column_name", "user_id", "data_type", "bigint",
                "max_length", 8, "is_nullable", false, "default_object_id", 0, "column_id", 1));
        return row(Map.of());
    }

    private java.sql.ResultSet row(Map<String, Object> values) throws Exception {
        var rowSet = RowSetProvider.newFactory().createCachedRowSet();
        RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(values.size());
        int column = 1;
        for (var entry : values.entrySet()) {
            metadata.setColumnName(column, entry.getKey());
            metadata.setColumnType(column, entry.getValue() instanceof Number ? Types.INTEGER
                    : entry.getValue() instanceof Boolean ? Types.BOOLEAN : Types.VARCHAR);
            column++;
        }
        rowSet.setMetaData(metadata);
        if (!values.isEmpty()) {
            rowSet.moveToInsertRow();
            column = 1;
            for (Object value : values.values()) rowSet.updateObject(column++, value);
            rowSet.insertRow();
            rowSet.moveToCurrentRow();
        }
        rowSet.beforeFirst();
        return rowSet;
    }

    private Map<String, Object> values(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put(String.valueOf(values[i]), values[i + 1]);
        return result;
    }
}
