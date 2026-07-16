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
        assertEquals("erp", ddl.catalog());
        assertEquals("public", ddl.schema());
        assertEquals("orders", ddl.name());
        assertEquals("POSTGRES_CATALOG_STRUCTURAL_DDL", ddl.source());
        assertTrue(ddl.ddl().contains("CREATE TABLE \"public\".\"orders\""));
        assertTrue(ddl.ddl().contains("CONSTRAINT \"orders_pkey\" PRIMARY KEY (\"id\")"));
        assertTrue(ddl.ddl().contains("CONSTRAINT \"fk_orders_customers\" FOREIGN KEY (\"tenant_id\", \"customer_id\") REFERENCES \"public\".\"customers\" (\"tenant_id\", \"id\")"));
    }

    private Connection connection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "prepareStatement" -> preparedStatement((String) args[0]);
                    case "getCatalog" -> "erp";
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
        if (sql.contains("metadata_constraints")) {
            return List.of(
                    constraintRow("orders_pkey", "PRIMARY KEY", "1", "id", "", "", ""),
                    constraintRow("fk_orders_customers", "FOREIGN KEY", "2", "customer_id", "public", "customers", "id"),
                    constraintRow("fk_orders_customers", "FOREIGN KEY", "1", "tenant_id", "public", "customers", "tenant_id"));
        }
        return List.of();
    }

    private Map<String, String> constraintRow(String name, String type, String position, String column,
            String referencedSchema, String referencedTable, String referencedColumn) {
        return Map.ofEntries(
                Map.entry("SCHEMA_NAME", "public"), Map.entry("TABLE_NAME", "orders"),
                Map.entry("CONSTRAINT_NAME", name), Map.entry("CONSTRAINT_TYPE", type),
                Map.entry("POSITION", position), Map.entry("COLUMN_NAME", column),
                Map.entry("REFERENCED_SCHEMA", referencedSchema), Map.entry("REFERENCED_TABLE", referencedTable),
                Map.entry("REFERENCED_COLUMN", referencedColumn), Map.entry("UPDATE_RULE", "NO ACTION"),
                Map.entry("DELETE_RULE", "NO ACTION"));
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
                    case "getInt" -> Integer.parseInt(rows.get(cursor.index).get(String.valueOf(args[0]).toUpperCase()));
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
