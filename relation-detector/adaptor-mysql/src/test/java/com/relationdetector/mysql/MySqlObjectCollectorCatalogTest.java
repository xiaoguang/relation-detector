package com.relationdetector.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.DatabaseObjectType;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.mysql.objects.MySqlObjectCollector;

class MySqlObjectCollectorCatalogTest {
    @Test
    void usesCanonicalCatalogForQueriesAndObjectIdentity() {
        List<String> boundDatabases = new ArrayList<>();

        var definitions = new MySqlObjectCollector().collect(
                connection(boundDatabases),
                new ScanScope(null, "shop", List.of(), List.of()),
                warning -> { });

        assertEquals(List.of("shop", "shop", "shop", "shop"), boundDatabases);
        assertEquals(4, definitions.size());
        definitions.forEach(definition -> {
            assertEquals("shop", definition.catalog());
            assertNull(definition.schema());
        });
        assertEquals(List.of(
                        DatabaseObjectType.PROCEDURE,
                        DatabaseObjectType.VIEW,
                        DatabaseObjectType.TRIGGER,
                        DatabaseObjectType.EVENT),
                definitions.stream().map(definition -> definition.type()).toList());
    }

    private Connection connection(List<String> boundDatabases) {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Connection.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "prepareStatement" -> preparedStatement(String.valueOf(args[0]), boundDatabases);
                    case "close" -> null;
                    case "isClosed" -> false;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private PreparedStatement preparedStatement(String sql, List<String> boundDatabases) {
        return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {PreparedStatement.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "setString" -> {
                        boundDatabases.add(String.valueOf(args[1]));
                        yield null;
                    }
                    case "executeQuery" -> resultSet(row(sql));
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private Map<String, String> row(String sql) {
        if (sql.contains("information_schema.ROUTINES")) {
            return Map.of("ROUTINE_SCHEMA", "shop", "ROUTINE_NAME", "sync_orders",
                    "ROUTINE_TYPE", "PROCEDURE", "ROUTINE_DEFINITION", "BEGIN SELECT 1; END");
        }
        if (sql.contains("information_schema.VIEWS")) {
            return Map.of("TABLE_SCHEMA", "shop", "TABLE_NAME", "active_orders",
                    "VIEW_DEFINITION", "SELECT * FROM orders");
        }
        if (sql.contains("information_schema.TRIGGERS")) {
            return Map.of("TRIGGER_SCHEMA", "shop", "TRIGGER_NAME", "orders_bi",
                    "ACTION_STATEMENT", "SET NEW.created_at = CURRENT_TIMESTAMP");
        }
        return Map.of("EVENT_SCHEMA", "shop", "EVENT_NAME", "purge_orders",
                "EVENT_DEFINITION", "DELETE FROM orders WHERE archived = 1");
    }

    private ResultSet resultSet(Map<String, String> row) {
        boolean[] read = {false};
        return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {ResultSet.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> {
                        boolean next = !read[0];
                        read[0] = true;
                        yield next;
                    }
                    case "getString" -> row.get(String.valueOf(args[0]));
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
