package com.relationdetector.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.DatabaseObjectType;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.mysql.objects.MySqlObjectCollector;

class MySqlObjectCollectorCatalogTest {
    @Test
    void blankShowCreateDefinitionUsesCommonUnavailableWarning() {
        List<com.relationdetector.contracts.model.WarningMessage> warnings = new ArrayList<>();

        var definitions = new MySqlObjectCollector().collect(
                connection(new ArrayList<>(), new ArrayList<>(), false, true),
                new ScanScope("shop", null, List.of(), List.of()),
                warnings::add);

        assertEquals(3, definitions.size());
        assertEquals(1, warnings.size());
        assertEquals(WarningType.LIVE_SOURCE_WARNING, warnings.get(0).type());
        assertEquals("DEFINITION_UNAVAILABLE", warnings.get(0).code());
        assertEquals("orders_bi", warnings.get(0).attributes().get("objectName"));
    }

    @Test
    void usesCanonicalCatalogForQueriesAndObjectIdentity() {
        List<String> boundDatabases = new ArrayList<>();
        List<String> showCreateStatements = new ArrayList<>();

        var definitions = new MySqlObjectCollector().collect(
                connection(boundDatabases, showCreateStatements, false),
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
        assertEquals(List.of(
                "SHOW CREATE PROCEDURE `shop`.`sync_orders`",
                "SHOW CREATE VIEW `shop`.`active_orders`",
                "SHOW CREATE TRIGGER `shop`.`orders_bi`",
                "SHOW CREATE EVENT `shop`.`purge_orders`"), showCreateStatements);
        definitions.forEach(definition -> {
            assertTrue(definition.sql().startsWith("CREATE "));
            assertTrue(definition.source().startsWith("SHOW CREATE "));
        });
    }

    @Test
    void skipsObjectWhenShowCreateIsUnavailableInsteadOfFallingBackToFragment() {
        List<com.relationdetector.contracts.model.WarningMessage> warnings = new ArrayList<>();

        var definitions = new MySqlObjectCollector().collect(
                connection(new ArrayList<>(), new ArrayList<>(), true),
                new ScanScope("shop", null, List.of(), List.of()),
                warnings::add);

        assertEquals(3, definitions.size());
        assertTrue(definitions.stream().noneMatch(definition -> definition.name().equals("orders_bi")));
        assertEquals(List.of("MYSQL_TRIGGER_SHOW_CREATE_FAILED"),
                warnings.stream().map(warning -> warning.code()).toList());
        var warning = warnings.get(0);
        assertEquals("shop", warning.attributes().get("objectCatalog"));
        assertEquals("orders_bi", warning.attributes().get("objectName"));
        assertEquals("TRIGGER", warning.attributes().get("objectType"));
        assertTrue(!warning.toString().contains("SHOW CREATE TRIGGER"));
        assertTrue(!warning.toString().contains("denied"));
    }

    private Connection connection(
            List<String> boundDatabases,
            List<String> showCreateStatements,
            boolean failTrigger
    ) {
        return connection(boundDatabases, showCreateStatements, failTrigger, false);
    }

    private Connection connection(
            List<String> boundDatabases,
            List<String> showCreateStatements,
            boolean failTrigger,
            boolean blankTrigger
    ) {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Connection.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "prepareStatement" -> preparedStatement(String.valueOf(args[0]), boundDatabases);
                    case "createStatement" -> statement(showCreateStatements, failTrigger, blankTrigger);
                    case "close" -> null;
                    case "isClosed" -> false;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private Statement statement(List<String> statements, boolean failTrigger, boolean blankTrigger) {
        return (Statement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Statement.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "executeQuery" -> {
                        String sql = String.valueOf(args[0]);
                        statements.add(sql);
                        if (failTrigger && sql.startsWith("SHOW CREATE TRIGGER")) {
                            throw new SQLException("denied", "42000", 1142);
                        }
                        yield resultSet(showCreateRow(sql, blankTrigger));
                    }
                    case "close" -> null;
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

    private Map<String, String> showCreateRow(String sql, boolean blankTrigger) {
        if (sql.startsWith("SHOW CREATE PROCEDURE")) {
            return Map.of("Create Procedure", "CREATE PROCEDURE `shop`.`sync_orders`() SELECT 1");
        }
        if (sql.startsWith("SHOW CREATE VIEW")) {
            return Map.of("Create View", "CREATE VIEW `shop`.`active_orders` AS SELECT * FROM `orders`");
        }
        if (sql.startsWith("SHOW CREATE TRIGGER")) {
            return Map.of("SQL Original Statement", blankTrigger ? " "
                    : "CREATE TRIGGER `shop`.`orders_bi` BEFORE INSERT ON `orders` FOR EACH ROW SET NEW.created_at = CURRENT_TIMESTAMP");
        }
        return Map.of("Create Event", "CREATE EVENT `shop`.`purge_orders` DO DELETE FROM `orders` WHERE archived = 1");
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
