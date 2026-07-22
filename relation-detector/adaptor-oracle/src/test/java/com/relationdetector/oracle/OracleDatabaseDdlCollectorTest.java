package com.relationdetector.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.parse.DatabaseDdlDefinition;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.oracle.ddl.OracleDatabaseDdlCollector;

class OracleDatabaseDdlCollectorTest {
    @Test
    void nullTableDdlProducesSafeWarningAndIsNotCollected() {
        List<com.relationdetector.contracts.model.WarningMessage> warnings = new ArrayList<>();

        var definitions = new OracleDatabaseDdlCollector().collect(
                connection("ERP", true),
                new ScanScope(null, "ERP", List.of("ORDERS"), List.of()),
                warnings::add);

        assertTrue(definitions.isEmpty());
        assertEquals(1, warnings.size());
        assertEquals(WarningType.LIVE_SOURCE_WARNING, warnings.get(0).type());
        assertEquals("DEFINITION_UNAVAILABLE", warnings.get(0).code());
        assertEquals("ERP", warnings.get(0).attributes().get("objectSchema"));
        assertEquals("ORDERS", warnings.get(0).attributes().get("objectName"));
        assertEquals("TABLE", warnings.get(0).attributes().get("objectType"));
    }

    @Test
    void zeroRowTableDdlProducesSafeWarningAndIsNotCollected() {
        List<com.relationdetector.contracts.model.WarningMessage> warnings = new ArrayList<>();

        var definitions = new OracleDatabaseDdlCollector().collect(
                connection("ERP", false, true),
                new ScanScope(null, "ERP", List.of("ORDERS"), List.of()),
                warnings::add);

        assertTrue(definitions.isEmpty());
        assertEquals(1, warnings.size());
        assertEquals("DEFINITION_UNAVAILABLE", warnings.get(0).code());
        assertEquals("ERP", warnings.get(0).attributes().get("objectSchema"));
        assertEquals("ORDERS", warnings.get(0).attributes().get("objectName"));
    }

    @Test
    void adaptorCollectsLiveTableDdlFromDbmsMetadata() {
        Connection connection = connection("IGNORED");
        ScanScope scope = new ScanScope(null, "ERP", List.of("ORDERS"), List.of());

        List<DatabaseDdlDefinition> definitions = new OracleDatabaseAdaptor()
                .collectors().databaseDdl()
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

    @Test
    void fallsBackToCurrentConnectionOwnerWhenScopeSchemaIsMissing() {
        List<DatabaseDdlDefinition> definitions = new OracleDatabaseDdlCollector().collect(
                connection("ERP"),
                new ScanScope(null, null, List.of("ORDERS"), List.of()));

        assertEquals(1, definitions.size());
        assertEquals("ERP", definitions.get(0).schema());
    }

    private Connection connection(String currentSchema) {
        return connection(currentSchema, false, false);
    }

    private Connection connection(String currentSchema, boolean nullDdl) {
        return connection(currentSchema, nullDdl, false);
    }

    private Connection connection(String currentSchema, boolean nullDdl, boolean emptyDdl) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "prepareStatement" -> preparedStatement((String) args[0], nullDdl, emptyDdl);
                    case "getSchema" -> currentSchema;
                    case "close" -> null;
                    case "isClosed" -> false;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private PreparedStatement preparedStatement(String sql, boolean nullDdl, boolean emptyDdl) {
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setString" -> null;
                    case "executeQuery" -> resultSet(rowsFor(sql, nullDdl, emptyDdl));
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private List<Map<String, String>> rowsFor(String sql, boolean nullDdl, boolean emptyDdl) {
        if (sql.contains("ALL_TABLES")) {
            return List.of(Map.of("TABLE_NAME", "ORDERS"));
        }
        if (sql.contains("DBMS_METADATA.GET_DDL")) {
            if (emptyDdl) {
                return List.of();
            }
            if (nullDdl) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("DDL", null);
                return List.of(row);
            }
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
