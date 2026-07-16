package com.relationdetector.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.oracle.metadata.OracleMetadataCollector;
import com.relationdetector.oracle.objects.OracleObjectCollector;

class OracleLiveCollectorsTest {
    @Test
    void adaptorOwnsOraclePermissionVendorCodes() {
        assertEquals(java.util.Set.of(1031), new OracleDatabaseAdaptor().permissionDeniedVendorCodes());
    }

    @Test
    void nullObjectDefinitionProducesSafeWarning() {
        List<com.relationdetector.contracts.model.WarningMessage> warnings = new ArrayList<>();

        var objects = new OracleObjectCollector().collect(connection(true),
                new ScanScope(null, "ERP", List.of(), List.of()), warnings::add);

        assertTrue(objects.isEmpty());
        assertEquals(1, warnings.size());
        assertEquals(WarningType.LIVE_SOURCE_WARNING, warnings.get(0).type());
        assertEquals("DEFINITION_UNAVAILABLE", warnings.get(0).code());
        assertEquals("REBUILD_ORDERS", warnings.get(0).attributes().get("objectName"));
    }

    @Test
    void collectsCatalogFactsForeignKeyAndObjectDefinition() throws Exception {
        Connection connection = connection();
        ScanScope scope = new ScanScope(null, "ERP", List.of(), List.of());

        var metadata = new OracleMetadataCollector().collect(connection, scope);
        var objects = new OracleObjectCollector().collect(connection, scope);

        assertEquals(1, metadata.tableFacts().size());
        assertEquals(1, metadata.columnFacts().size());
        assertEquals(1, metadata.constraintFacts().size());
        assertEquals(1, metadata.indexFacts().size());
        assertEquals("ERP.ORDERS.USER_ID", metadata.relationships().get(0).source().displayName());
        assertEquals("ERP.USERS.ID", metadata.relationships().get(0).target().displayName());
        assertEquals("ERP.REBUILD_ORDERS", objects.get(0).schema() + "." + objects.get(0).name());
        assertTrue(objects.get(0).sql().contains("CREATE PROCEDURE"));
    }

    @Test
    void catalogFamiliesFailIndependently() {
        Connection broken = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement")) throw new java.sql.SQLException("denied");
                    if (method.getName().equals("getSchema")) return "ERP";
                    throw new UnsupportedOperationException(method.getName());
                });

        var snapshot = new OracleMetadataCollector().collect(broken,
                new ScanScope(null, "ERP", List.of(), List.of()));

        assertEquals(4, snapshot.warnings().size());
    }

    private Connection connection() {
        return connection(false);
    }

    private Connection connection(boolean nullObjectDdl) {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement")) {
                        return statement(String.valueOf(args[0]), nullObjectDdl);
                    }
                    if (method.getName().equals("getSchema")) return "ERP";
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private PreparedStatement statement(String sql, boolean nullObjectDdl) {
        return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {PreparedStatement.class}, (proxy, method, args) -> {
                    if (method.getName().equals("setString") || method.getName().equals("close")) return null;
                    if (method.getName().equals("executeQuery")) return rows(sql, nullObjectDdl);
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private java.sql.ResultSet rows(String sql, boolean nullObjectDdl) throws Exception {
        if (sql.contains("ALL_TABLES")) return row(Map.of("OWNER", "ERP", "TABLE_NAME", "ORDERS"));
        if (sql.contains("ALL_TAB_COLUMNS")) return row(values(
                "OWNER", "ERP", "TABLE_NAME", "ORDERS", "COLUMN_NAME", "USER_ID", "DATA_TYPE", "NUMBER",
                "DATA_LENGTH", 22, "NULLABLE", "N", "DATA_DEFAULT", null, "VIRTUAL_COLUMN", "NO", "COLUMN_ID", 1));
        if (sql.contains("ALL_CONSTRAINTS")) return row(values(
                "OWNER", "ERP", "TABLE_NAME", "ORDERS", "CONSTRAINT_NAME", "FK_ORDERS_USERS", "CONSTRAINT_TYPE", "R",
                "COLUMN_NAME", "USER_ID", "POSITION", 1, "REFERENCED_OWNER", "ERP", "REFERENCED_TABLE", "USERS",
                "REFERENCED_COLUMN", "ID", "DELETE_RULE", "NO ACTION"));
        if (sql.contains("ALL_INDEXES")) return row(values(
                "OWNER", "ERP", "TABLE_NAME", "ORDERS", "INDEX_NAME", "IDX_ORDERS_USER", "UNIQUENESS", "NONUNIQUE",
                "INDEX_TYPE", "NORMAL", "COLUMN_NAME", "USER_ID", "COLUMN_POSITION", 1));
        if (sql.contains("ALL_OBJECTS")) return row(values(
                "OWNER", "ERP", "OBJECT_NAME", "REBUILD_ORDERS", "OBJECT_TYPE", "PROCEDURE"));
        if (sql.contains("DBMS_METADATA.GET_DDL")) {
            return row(values("DDL", nullObjectDdl ? null : "CREATE PROCEDURE ERP.REBUILD_ORDERS AS BEGIN NULL; END;"));
        }
        return row(Map.of());
    }

    private java.sql.ResultSet row(Map<String, Object> values) throws Exception {
        var rowSet = RowSetProvider.newFactory().createCachedRowSet();
        RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(values.size());
        int column = 1;
        for (var entry : values.entrySet()) {
            metadata.setColumnName(column, entry.getKey());
            metadata.setColumnType(column, entry.getValue() instanceof Number ? Types.INTEGER : Types.VARCHAR);
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
