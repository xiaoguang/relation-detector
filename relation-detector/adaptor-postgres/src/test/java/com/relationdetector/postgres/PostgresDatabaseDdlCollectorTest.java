package com.relationdetector.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.parse.DatabaseDdlDefinition;
import com.relationdetector.contracts.spi.ScanScope;

class PostgresDatabaseDdlCollectorTest {
    @Test
    void adaptorCollectsLiveTableDdlFromInformationSchema() {
        Connection connection = connection();
        ScanScope scope = new ScanScope(null, "public", List.of("orders"), List.of());

        List<DatabaseDdlDefinition> definitions = new PostgresDatabaseAdaptor()
                .collectors().databaseDdl()
                .orElseThrow()
                .collect(connection, scope);

        assertEquals(1, definitions.size());
        DatabaseDdlDefinition ddl = definitions.get(0);
        assertEquals("public", ddl.schema());
        assertEquals("orders", ddl.name());
        assertEquals("INFORMATION_SCHEMA", ddl.source());
        assertTrue(ddl.ddl().contains("CREATE TABLE \"public\".\"orders\""));
        assertTrue(ddl.ddl().contains("CONSTRAINT \"orders_pkey\" PRIMARY KEY (\"id\")"));
        assertTrue(ddl.ddl().contains("CONSTRAINT \"fk_orders_customers\" FOREIGN KEY (\"customer_id\") REFERENCES \"public\".\"customers\" (\"id\")"));
    }

    private Connection connection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "prepareStatement" -> preparedStatement((String) args[0]);
                    case "close" -> null;
                    case "isClosed" -> false;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private PreparedStatement preparedStatement(String sql) {
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setString" -> null;
                    case "executeQuery" -> resultSet(rowsFor(sql));
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private List<Map<String, String>> rowsFor(String sql) {
        if (sql.contains("information_schema.TABLES") || sql.contains("information_schema.tables")) {
            return List.of(Map.of("TABLE_NAME", "orders"));
        }
        if (sql.contains("information_schema.COLUMNS") || sql.contains("information_schema.columns")) {
            return List.of(
                    Map.of("COLUMN_NAME", "id", "DATA_TYPE", "bigint", "IS_NULLABLE", "NO"),
                    Map.of("COLUMN_NAME", "customer_id", "DATA_TYPE", "bigint", "IS_NULLABLE", "NO"));
        }
        if (sql.contains("information_schema.table_constraints")) {
            return List.of(
                    Map.of("CONSTRAINT_NAME", "orders_pkey", "CONSTRAINT_TYPE", "PRIMARY KEY", "COLUMN_NAME", "id"),
                    Map.of("CONSTRAINT_NAME", "fk_orders_customers", "CONSTRAINT_TYPE", "FOREIGN KEY",
                            "COLUMN_NAME", "customer_id", "FOREIGN_TABLE_SCHEMA", "public",
                            "FOREIGN_TABLE_NAME", "customers", "FOREIGN_COLUMN_NAME", "id"));
        }
        return List.of();
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
                    case "getString" -> rows.get(cursor.index).get(String.valueOf(args[0]).toUpperCase());
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
