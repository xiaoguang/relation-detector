package com.relationdetector.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.spi.ScanScope;

class MySqlMetadataCollectorFactsTest {
    @Test
    void collectsCatalogFactsAndKeepsDeclaredForeignKeyRelationshipsWithinScope() {
        MetadataSnapshot snapshot = new MySqlDatabaseAdaptor().collectors().metadata().collect(
                catalogConnection(),
                new ScanScope(null, "shop", List.of("orders", "users"), List.of("audit_logs")));

        assertEquals(2, snapshot.tableFacts().size(), "include/exclude scope should filter metadata tables");
        assertTrue(snapshot.tableFacts().stream().anyMatch(table ->
                table.schema().equals("shop")
                        && table.tableName().equals("orders")
                        && table.engine().equals("InnoDB")
                        && table.comment().equals("sales orders")));
        assertFalse(snapshot.tableFacts().stream().anyMatch(table -> table.tableName().equals("audit_logs")));

        assertTrue(snapshot.columnFacts().stream().anyMatch(column ->
                column.schema().equals("shop")
                        && column.tableName().equals("orders")
                        && column.columnName().equals("user_id")
                        && column.dataType().equals("bigint")
                        && column.columnType().equals("bigint")
                        && !column.nullable()
                        && column.ordinalPosition() == 2));

        assertTrue(snapshot.indexFacts().stream().anyMatch(index ->
                index.schema().equals("shop")
                        && index.tableName().equals("orders")
                        && index.indexName().equals("idx_orders_user_id")
                        && !index.unique()
                        && !index.primary()
                        && index.columns().equals(List.of("user_id"))),
                () -> "Actual indexes=" + snapshot.indexFacts() + ", warnings=" + snapshot.warnings());
        assertTrue(snapshot.indexFacts().stream().anyMatch(index ->
                index.schema().equals("shop")
                        && index.tableName().equals("users")
                        && index.indexName().equals("PRIMARY")
                        && index.unique()
                        && index.primary()
                        && index.columns().equals(List.of("id"))));

        assertTrue(snapshot.constraintFacts().stream().anyMatch(constraint ->
                constraint.schema().equals("shop")
                        && constraint.tableName().equals("orders")
                        && constraint.constraintName().equals("fk_orders_users")
                        && constraint.constraintType().equals("FOREIGN KEY")
                        && constraint.columns().equals(List.of("user_id"))
                        && constraint.referencedSchema().equals("shop")
                        && constraint.referencedTable().equals("users")
                        && constraint.referencedColumns().equals(List.of("id"))
                        && constraint.updateRule().equals("CASCADE")
                        && constraint.deleteRule().equals("RESTRICT")));

        assertEquals(1, snapshot.relationships().size(), "declared FK relationship output must stay compatible");
        assertEquals("shop.orders.user_id", snapshot.relationships().get(0).source().displayName());
        assertEquals("shop.users.id", snapshot.relationships().get(0).target().displayName());
    }

    private Connection catalogConnection() {
        Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
        rows.put("TABLES", List.of(
                row("TABLE_SCHEMA", "shop", "TABLE_NAME", "orders", "TABLE_TYPE", "BASE TABLE", "ENGINE", "InnoDB", "TABLE_COMMENT", "sales orders"),
                row("TABLE_SCHEMA", "shop", "TABLE_NAME", "users", "TABLE_TYPE", "BASE TABLE", "ENGINE", "InnoDB", "TABLE_COMMENT", "system users"),
                row("TABLE_SCHEMA", "shop", "TABLE_NAME", "audit_logs", "TABLE_TYPE", "BASE TABLE", "ENGINE", "InnoDB", "TABLE_COMMENT", "excluded")));
        rows.put("COLUMNS", List.of(
                row("TABLE_SCHEMA", "shop", "TABLE_NAME", "orders", "COLUMN_NAME", "id", "DATA_TYPE", "bigint", "COLUMN_TYPE", "bigint", "IS_NULLABLE", "NO", "COLUMN_DEFAULT", null, "EXTRA", "auto_increment", "GENERATION_EXPRESSION", "", "ORDINAL_POSITION", 1),
                row("TABLE_SCHEMA", "shop", "TABLE_NAME", "orders", "COLUMN_NAME", "user_id", "DATA_TYPE", "bigint", "COLUMN_TYPE", "bigint", "IS_NULLABLE", "NO", "COLUMN_DEFAULT", null, "EXTRA", "", "GENERATION_EXPRESSION", "", "ORDINAL_POSITION", 2),
                row("TABLE_SCHEMA", "shop", "TABLE_NAME", "users", "COLUMN_NAME", "id", "DATA_TYPE", "bigint", "COLUMN_TYPE", "bigint", "IS_NULLABLE", "NO", "COLUMN_DEFAULT", null, "EXTRA", "auto_increment", "GENERATION_EXPRESSION", "", "ORDINAL_POSITION", 1)));
        rows.put("FK_ONLY", List.of(
                row("TABLE_SCHEMA", "shop", "TABLE_NAME", "orders", "COLUMN_NAME", "user_id", "REFERENCED_TABLE_SCHEMA", "shop", "REFERENCED_TABLE_NAME", "users", "REFERENCED_COLUMN_NAME", "id", "CONSTRAINT_NAME", "fk_orders_users")));
        rows.put("TABLE_CONSTRAINTS", List.of(
                row("CONSTRAINT_SCHEMA", "shop", "TABLE_NAME", "users", "CONSTRAINT_NAME", "PRIMARY", "CONSTRAINT_TYPE", "PRIMARY KEY"),
                row("CONSTRAINT_SCHEMA", "shop", "TABLE_NAME", "users", "CONSTRAINT_NAME", "uq_users_email", "CONSTRAINT_TYPE", "UNIQUE"),
                row("CONSTRAINT_SCHEMA", "shop", "TABLE_NAME", "orders", "CONSTRAINT_NAME", "fk_orders_users", "CONSTRAINT_TYPE", "FOREIGN KEY")));
        rows.put("KEY_COLUMN_USAGE_ALL", List.of(
                row("CONSTRAINT_SCHEMA", "shop", "TABLE_SCHEMA", "shop", "TABLE_NAME", "users", "COLUMN_NAME", "id", "CONSTRAINT_NAME", "PRIMARY", "ORDINAL_POSITION", 1, "POSITION_IN_UNIQUE_CONSTRAINT", null, "REFERENCED_TABLE_SCHEMA", null, "REFERENCED_TABLE_NAME", null, "REFERENCED_COLUMN_NAME", null),
                row("CONSTRAINT_SCHEMA", "shop", "TABLE_SCHEMA", "shop", "TABLE_NAME", "orders", "COLUMN_NAME", "user_id", "CONSTRAINT_NAME", "fk_orders_users", "ORDINAL_POSITION", 1, "POSITION_IN_UNIQUE_CONSTRAINT", 1, "REFERENCED_TABLE_SCHEMA", "shop", "REFERENCED_TABLE_NAME", "users", "REFERENCED_COLUMN_NAME", "id")));
        rows.put("REFERENTIAL_CONSTRAINTS", List.of(
                row("CONSTRAINT_SCHEMA", "shop", "CONSTRAINT_NAME", "fk_orders_users", "UNIQUE_CONSTRAINT_SCHEMA", "shop", "REFERENCED_TABLE_NAME", "users", "UPDATE_RULE", "CASCADE", "DELETE_RULE", "RESTRICT")));
        rows.put("STATISTICS", List.of(
                row("TABLE_SCHEMA", "shop", "TABLE_NAME", "orders", "INDEX_NAME", "idx_orders_user_id", "NON_UNIQUE", 1, "SEQ_IN_INDEX", 1, "COLUMN_NAME", "user_id", "INDEX_TYPE", "BTREE", "SUB_PART", null, "EXPRESSION", null, "IS_VISIBLE", "YES"),
                row("TABLE_SCHEMA", "shop", "TABLE_NAME", "users", "INDEX_NAME", "PRIMARY", "NON_UNIQUE", 0, "SEQ_IN_INDEX", 1, "COLUMN_NAME", "id", "INDEX_TYPE", "BTREE", "SUB_PART", null, "EXPRESSION", null, "IS_VISIBLE", "YES")));
        return JdbcFake.connection(rows);
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            row.put(String.valueOf(values[i]), values[i + 1]);
        }
        return row;
    }

    private static final class JdbcFake {
        static Connection connection(Map<String, List<Map<String, Object>>> rows) {
            return (Connection) Proxy.newProxyInstance(JdbcFake.class.getClassLoader(), new Class<?>[] { Connection.class },
                    (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement")) {
                            return preparedStatement(String.valueOf(args[0]), rows);
                        }
                        if (method.getName().equals("close")) {
                            return null;
                        }
                        if (method.getName().equals("isClosed")) {
                            return false;
                        }
                        throw new UnsupportedOperationException("Connection." + method.getName());
                    });
        }

        static PreparedStatement preparedStatement(String sql, Map<String, List<Map<String, Object>>> rows) {
            List<Object> parameters = new ArrayList<>();
            return (PreparedStatement) Proxy.newProxyInstance(JdbcFake.class.getClassLoader(), new Class<?>[] { PreparedStatement.class },
                    (proxy, method, args) -> {
                        if (method.getName().equals("setString")) {
                            while (parameters.size() < (int) args[0]) {
                                parameters.add(null);
                            }
                            parameters.set((int) args[0] - 1, args[1]);
                            return null;
                        }
                        if (method.getName().equals("executeQuery")) {
                            return resultSet(rowsFor(sql, rows));
                        }
                        if (method.getName().equals("close")) {
                            return null;
                        }
                        throw new UnsupportedOperationException("PreparedStatement." + method.getName());
                    });
        }

        static List<Map<String, Object>> rowsFor(String sql, Map<String, List<Map<String, Object>>> rows) {
            if (sql.contains("information_schema.TABLES")) {
                return rows.get("TABLES");
            }
            if (sql.contains("information_schema.COLUMNS")) {
                return rows.get("COLUMNS");
            }
            if (sql.contains("information_schema.TABLE_CONSTRAINTS")) {
                return rows.get("TABLE_CONSTRAINTS");
            }
            if (sql.contains("information_schema.KEY_COLUMN_USAGE") && sql.contains("REFERENCED_TABLE_NAME IS NOT NULL")) {
                return rows.get("FK_ONLY");
            }
            if (sql.contains("information_schema.KEY_COLUMN_USAGE")) {
                return rows.get("KEY_COLUMN_USAGE_ALL");
            }
            if (sql.contains("information_schema.REFERENTIAL_CONSTRAINTS")) {
                return rows.get("REFERENTIAL_CONSTRAINTS");
            }
            if (sql.contains("information_schema.STATISTICS")) {
                return rows.get("STATISTICS");
            }
            return List.of();
        }

        static ResultSet resultSet(List<Map<String, Object>> rows) {
            List<Map<String, Object>> safeRows = rows == null ? List.of() : rows;
            int[] index = { -1 };
            return (ResultSet) Proxy.newProxyInstance(JdbcFake.class.getClassLoader(), new Class<?>[] { ResultSet.class },
                    (proxy, method, args) -> {
                        if (method.getName().equals("next")) {
                            index[0]++;
                            return index[0] < safeRows.size();
                        }
                        if (method.getName().equals("getString")) {
                            Object value = safeRows.get(index[0]).get(String.valueOf(args[0]));
                            return value == null ? null : String.valueOf(value);
                        }
                        if (method.getName().equals("getInt")) {
                            Object value = safeRows.get(index[0]).get(String.valueOf(args[0]));
                            return value == null ? 0 : ((Number) value).intValue();
                        }
                        if (method.getName().equals("getObject")) {
                            return safeRows.get(index[0]).get(String.valueOf(args[0]));
                        }
                        if (method.getName().equals("close")) {
                            return null;
                        }
                        throw new UnsupportedOperationException("ResultSet." + method.getName());
                    });
        }
    }
}
