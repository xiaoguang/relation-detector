package com.relationdetector.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.postgres.metadata.PostgresMetadataCollector;

class PostgresMetadataCollectorCatalogTest {
    @Test
    void preservesConnectionCatalogOnForeignKeyEndpoints() throws Exception {
        var snapshot = new PostgresMetadataCollector().collect(connection(),
                new ScanScope(null, "public", List.of(), List.of()));

        assertEquals(1, snapshot.relationships().size());
        assertEquals("erp", snapshot.relationships().get(0).source().column().table().catalog());
        assertEquals("erp", snapshot.relationships().get(0).target().column().table().catalog());
        assertEquals("erp.public.orders.customer_id",
                snapshot.relationships().get(0).source().displayName());
    }

    private Connection connection() {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getCatalog")) return "erp";
                    if (method.getName().equals("prepareStatement")) return statement();
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private PreparedStatement statement() {
        return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {PreparedStatement.class}, (proxy, method, args) -> {
                    if (method.getName().equals("setString") || method.getName().equals("close")) return null;
                    if (method.getName().equals("executeQuery")) return rows();
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private java.sql.ResultSet rows() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("source_schema", "public");
        values.put("source_table", "orders");
        values.put("source_column", "customer_id");
        values.put("target_schema", "public");
        values.put("target_table", "customers");
        values.put("target_column", "id");
        values.put("constraint_name", "fk_orders_customers");
        var rowSet = RowSetProvider.newFactory().createCachedRowSet();
        RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(values.size());
        int column = 1;
        for (String name : values.keySet()) {
            metadata.setColumnName(column, name);
            metadata.setColumnType(column++, Types.VARCHAR);
        }
        rowSet.setMetaData(metadata);
        rowSet.moveToInsertRow();
        column = 1;
        for (Object value : values.values()) rowSet.updateObject(column++, value);
        rowSet.insertRow();
        rowSet.moveToCurrentRow();
        rowSet.beforeFirst();
        return rowSet;
    }
}
