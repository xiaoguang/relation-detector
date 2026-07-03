package com.relationdetector.oracle;

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

class OracleDatabaseDdlCollectorTest {
    @Test
    void adaptorCollectsLiveTableDdlFromDbmsMetadata() {
        Connection connection = connection();
        ScanScope scope = new ScanScope(null, "ERP", List.of("ORDERS"), List.of());

        List<DatabaseDdlDefinition> definitions = new OracleDatabaseAdaptor()
                .databaseDdlCollector()
                .orElseThrow()
                .collect(connection, scope);

        assertEquals(1, definitions.size());
        DatabaseDdlDefinition ddl = definitions.get(0);
        assertEquals("ERP", ddl.schema());
        assertEquals("ORDERS", ddl.name());
        assertEquals("DBMS_METADATA.GET_DDL", ddl.source());
        assertTrue(ddl.ddl().contains("CREATE TABLE \"ERP\".\"ORDERS\""));
        assertTrue(ddl.ddl().contains("FOREIGN KEY (\"CUSTOMER_ID\") REFERENCES \"ERP\".\"CUSTOMERS\""));
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
        if (sql.contains("ALL_TABLES")) {
            return List.of(Map.of("TABLE_NAME", "ORDERS"));
        }
        if (sql.contains("DBMS_METADATA.GET_DDL")) {
            return List.of(Map.of("DDL", """
                    CREATE TABLE "ERP"."ORDERS" (
                      "ID" NUMBER NOT NULL,
                      "CUSTOMER_ID" NUMBER NOT NULL,
                      CONSTRAINT "ORDERS_PK" PRIMARY KEY ("ID"),
                      CONSTRAINT "FK_ORDERS_CUSTOMERS"
                        FOREIGN KEY ("CUSTOMER_ID") REFERENCES "ERP"."CUSTOMERS" ("ID")
                    )
                    """));
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
                    case "getString" -> {
                        if (args[0] instanceof Integer) {
                            yield rows.get(cursor.index).get("DDL");
                        }
                        yield rows.get(cursor.index).get(String.valueOf(args[0]).toUpperCase());
                    }
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
