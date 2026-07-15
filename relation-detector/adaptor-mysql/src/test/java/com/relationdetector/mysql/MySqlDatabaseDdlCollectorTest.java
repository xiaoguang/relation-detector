package com.relationdetector.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.parse.DatabaseDdlDefinition;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.mysql.ddl.MySqlDatabaseDdlCollector;

/**
 * Unit tests for MySQL's live database DDL collector.
 *
 * <p>The collector should only read table definitions inside the configured
 * scope, and a failed {@code SHOW CREATE TABLE} for one table must not stop the
 * rest of the scan.
 */
class MySqlDatabaseDdlCollectorTest {
    @Test
    void collectsShowCreateTableDdlWithinScopeAndWarnsPerTableFailure() {
        List<String> showCreateSql = new ArrayList<>();
        List<WarningMessage> warnings = new ArrayList<>();
        Connection connection = connection(showCreateSql);
        ScanScope scope = new ScanScope(null, "shop", List.of("orders", "users", "audit_logs"), List.of("audit_logs"));

        List<DatabaseDdlDefinition> definitions = new MySqlDatabaseDdlCollector()
                .collect(connection, scope, warnings::add);

        assertEquals(1, definitions.size(), "orders should be returned even when users SHOW CREATE fails");
        DatabaseDdlDefinition orders = definitions.get(0);
        assertEquals("shop", orders.catalog());
        assertEquals(null, orders.schema());
        assertEquals("orders", orders.name());
        assertEquals("SHOW CREATE TABLE", orders.source());
        assertTrue(orders.ddl().contains("CONSTRAINT `fk_orders_users`"));

        assertTrue(showCreateSql.stream().anyMatch(sql -> sql.equals("SHOW CREATE TABLE `shop`.`orders`")));
        assertTrue(showCreateSql.stream().anyMatch(sql -> sql.equals("SHOW CREATE TABLE `shop`.`users`")));
        assertFalse(showCreateSql.stream().anyMatch(sql -> sql.contains("audit_logs")),
                "excluded tables should not receive SHOW CREATE TABLE calls");

        assertEquals(1, warnings.size());
        WarningMessage warning = warnings.get(0);
        assertEquals("MYSQL_SHOW_CREATE_TABLE_FAILED", warning.code());
        assertEquals("users", warning.attributes().get("objectName"));
        assertEquals("TABLE", warning.attributes().get("objectType"));
        assertTrue(String.valueOf(warning.attributes().get("rawStatement")).contains("`shop`.`users`"));
    }

    private Connection connection(List<String> showCreateSql) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "prepareStatement" -> preparedStatement();
                    case "createStatement" -> statement(showCreateSql);
                    case "close" -> null;
                    case "isClosed" -> false;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private PreparedStatement preparedStatement() {
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setString" -> null;
                    case "executeQuery" -> resultSet(List.of(
                            Map.of("TABLE_NAME", "orders"),
                            Map.of("TABLE_NAME", "users"),
                            Map.of("TABLE_NAME", "audit_logs")));
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private Statement statement(List<String> showCreateSql) {
        return (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "executeQuery" -> {
                        String sql = (String) args[0];
                        showCreateSql.add(sql);
                        if (sql.contains("`users`")) {
                            throw new java.sql.SQLException("SHOW CREATE denied");
                        }
                        yield resultSet(List.of(Map.of(
                                "Table", "orders",
                                "Create Table", """
                                        CREATE TABLE `orders` (
                                          `id` bigint NOT NULL,
                                          `user_id` bigint NOT NULL,
                                          CONSTRAINT `fk_orders_users`
                                            FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
                                        ) ENGINE=InnoDB
                                        """)));
                    }
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private ResultSet resultSet(List<Map<String, String>> rows) {
        class Cursor {
            int index = -1;
        }
        Cursor cursor = new Cursor();
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> ++cursor.index < rows.size();
                    case "getString" -> {
                        if (args[0] instanceof Integer columnIndex) {
                            yield columnIndex == 1
                                    ? rows.get(cursor.index).get("Table")
                                    : rows.get(cursor.index).get("Create Table");
                        }
                        yield rows.get(cursor.index).get(String.valueOf(args[0]));
                    }
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
