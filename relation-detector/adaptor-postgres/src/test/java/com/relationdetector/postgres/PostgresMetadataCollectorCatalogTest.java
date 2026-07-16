package com.relationdetector.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.contracts.spi.LiveSourceConfigurationException;
import com.relationdetector.postgres.metadata.PostgresMetadataCollector;

class PostgresMetadataCollectorCatalogTest {
    @Test
    void rejectsExplicitCatalogThatDoesNotMatchConnectionBeforeCatalogQuery() {
        AtomicInteger preparedStatements = new AtomicInteger();
        Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Connection.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getCatalog" -> "erp";
                    case "prepareStatement" -> {
                        preparedStatements.incrementAndGet();
                        throw new AssertionError("catalog SQL must not run");
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });

        assertThrows(LiveSourceConfigurationException.class, () -> new PostgresMetadataCollector().collect(connection,
                new ScanScope("other", "public", List.of(), List.of())));
        assertEquals(0, preparedStatements.get());
    }

    @Test
    void collectsCatalogQualifiedInventoryAndOrdinalCompositeForeignKey() {
        var snapshot = new PostgresMetadataCollector().collect(connection(),
                new ScanScope(null, "public", List.of("orders"), List.of()));

        assertEquals(1, snapshot.tableFacts().size());
        assertEquals("erp", snapshot.tableFacts().get(0).catalog());
        assertEquals(2, snapshot.columnFacts().size());
        assertEquals(List.of("tenant_id", "customer_id"), snapshot.columnFacts().stream()
                .map(fact -> fact.columnName()).toList());
        assertEquals(2, snapshot.constraintFacts().size());
        assertNull(snapshot.constraintFacts().get(0).referencedCatalog());
        assertEquals("erp", snapshot.constraintFacts().get(1).referencedCatalog());
        assertEquals(List.of("tenant_id", "customer_id"), snapshot.constraintFacts().get(1).columns());
        assertEquals(List.of("tenant_id", "id"), snapshot.constraintFacts().get(1).referencedColumns());
        assertEquals(1, snapshot.indexFacts().size());
        assertEquals(List.of("tenant_id", "customer_id"), snapshot.indexFacts().get(0).columns());
        assertEquals(List.of(1, 2), snapshot.indexFacts().get(0).seqInIndex());
        assertEquals(2, snapshot.relationships().size());
        assertEquals("erp.public.orders.tenant_id", snapshot.relationships().get(0).source().displayName());
        assertEquals("erp.public.customers.tenant_id", snapshot.relationships().get(0).target().displayName());
        assertEquals("erp.public.orders.customer_id", snapshot.relationships().get(1).source().displayName());
        assertEquals("erp.public.customers.id", snapshot.relationships().get(1).target().displayName());
        assertTrue(snapshot.warnings().isEmpty());
    }

    private Connection connection() {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getCatalog" -> "erp";
                    case "prepareStatement" -> statement((String) args[0]);
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private PreparedStatement statement(String sql) {
        return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {PreparedStatement.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "setString", "close" -> null;
                    case "executeQuery" -> rowsFor(sql);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private ResultSet rowsFor(String sql) {
        String normalized = sql.toLowerCase(Locale.ROOT);
        if (normalized.contains("metadata_tables")) {
            return resultSet(List.of(Map.of(
                    "schema_name", "public", "table_name", "orders", "table_type", "TABLE")));
        }
        if (normalized.contains("metadata_columns")) {
            return resultSet(List.of(
                    Map.of("schema_name", "public", "table_name", "orders", "column_name", "tenant_id",
                            "data_type", "int8", "column_type", "bigint", "nullable", false,
                            "default_value", "", "identity_kind", "", "generated_kind", "", "ordinal_position", 1),
                    Map.of("schema_name", "public", "table_name", "orders", "column_name", "customer_id",
                            "data_type", "int8", "column_type", "bigint", "nullable", false,
                            "default_value", "", "identity_kind", "", "generated_kind", "", "ordinal_position", 2)));
        }
        if (normalized.contains("metadata_constraints")) {
            return resultSet(List.of(
                    constraintRow("orders_pkey", "PRIMARY KEY", 1, "tenant_id", null, null, null),
                    constraintRow("fk_orders_customers", "FOREIGN KEY", 2, "customer_id", "public", "customers", "id"),
                    constraintRow("fk_orders_customers", "FOREIGN KEY", 1, "tenant_id", "public", "customers", "tenant_id")));
        }
        if (normalized.contains("metadata_indexes")) {
            return resultSet(List.of(
                    indexRow(2, "customer_id"),
                    indexRow(1, "tenant_id")));
        }
        throw new AssertionError("Unexpected SQL: " + sql);
    }

    private Map<String, Object> constraintRow(
            String name,
            String type,
            int position,
            String column,
            String referencedSchema,
            String referencedTable,
            String referencedColumn
    ) {
        return Map.ofEntries(
                Map.entry("schema_name", "public"), Map.entry("table_name", "orders"),
                Map.entry("constraint_name", name), Map.entry("constraint_type", type),
                Map.entry("position", position), Map.entry("column_name", column),
                Map.entry("referenced_schema", referencedSchema == null ? "" : referencedSchema),
                Map.entry("referenced_table", referencedTable == null ? "" : referencedTable),
                Map.entry("referenced_column", referencedColumn == null ? "" : referencedColumn),
                Map.entry("update_rule", "NO ACTION"), Map.entry("delete_rule", "NO ACTION"));
    }

    private Map<String, Object> indexRow(int position, String column) {
        return Map.ofEntries(
                Map.entry("schema_name", "public"), Map.entry("table_name", "orders"),
                Map.entry("index_name", "idx_orders_customer"), Map.entry("is_unique", false),
                Map.entry("is_primary", false), Map.entry("index_type", "btree"),
                Map.entry("position", position), Map.entry("column_name", column),
                Map.entry("expression", ""));
    }

    private ResultSet resultSet(List<Map<String, Object>> rows) {
        class Cursor { int index = -1; }
        Cursor cursor = new Cursor();
        return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> ++cursor.index < rows.size();
                    case "getString" -> String.valueOf(value(rows, cursor.index, args[0]));
                    case "getInt" -> ((Number) value(rows, cursor.index, args[0])).intValue();
                    case "getBoolean" -> (Boolean) value(rows, cursor.index, args[0]);
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private Object value(List<Map<String, Object>> rows, int index, Object key) {
        return rows.get(index).get(String.valueOf(key).toLowerCase(Locale.ROOT));
    }
}
