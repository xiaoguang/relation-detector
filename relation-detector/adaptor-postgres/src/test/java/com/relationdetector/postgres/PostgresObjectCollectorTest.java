package com.relationdetector.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.DatabaseObjectType;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.postgres.objects.PostgresObjectCollector;

class PostgresObjectCollectorTest {
    @Test
    void blankDefinitionProducesSafeWarningAndIsNotCollected() {
        List<com.relationdetector.contracts.model.WarningMessage> warnings = new ArrayList<>();

        var definitions = new PostgresObjectCollector().collect(connectionWithBlankTrigger(),
                new ScanScope(null, "public", List.of(), List.of()), warnings::add);

        assertTrue(definitions.isEmpty());
        assertEquals(1, warnings.size());
        assertEquals(WarningType.LIVE_SOURCE_WARNING, warnings.get(0).type());
        assertEquals("DEFINITION_UNAVAILABLE", warnings.get(0).code());
        assertEquals("Live database object definition unavailable", warnings.get(0).message());
        assertEquals("tr_orders_audit", warnings.get(0).attributes().get("objectName"));
        assertEquals("TRIGGER", warnings.get(0).attributes().get("objectType"));
    }

    @Test
    void collectsCatalogQualifiedUserTriggers() {
        var definitions = new PostgresObjectCollector().collect(connection(),
                new ScanScope(null, "public", List.of(), List.of()));

        assertEquals(1, definitions.size());
        var trigger = definitions.get(0);
        assertEquals(DatabaseObjectType.TRIGGER, trigger.type());
        assertEquals("erp", trigger.catalog());
        assertEquals("public", trigger.schema());
        assertEquals("tr_orders_audit", trigger.name());
        assertEquals("CREATE TRIGGER tr_orders_audit AFTER INSERT ON public.orders EXECUTE FUNCTION audit_order()",
                trigger.sql());
    }

    private Connection connection() {
        return connection(false);
    }

    private Connection connectionWithBlankTrigger() {
        return connection(true);
    }

    private Connection connection(boolean blankTrigger) {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getCatalog" -> "erp";
                    case "prepareStatement" -> statement((String) args[0], blankTrigger);
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private PreparedStatement statement(String sql, boolean blankTrigger) {
        return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {PreparedStatement.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "setString", "close" -> null;
                    case "executeQuery" -> resultSet(sql.toLowerCase(Locale.ROOT).contains("pg_trigger")
                            ? List.of(Map.of("schema_name", "public", "trigger_name", "tr_orders_audit",
                                    "definition", blankTrigger ? " " : "CREATE TRIGGER tr_orders_audit AFTER INSERT ON public.orders EXECUTE FUNCTION audit_order()"))
                            : List.of());
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private ResultSet resultSet(List<Map<String, String>> rows) {
        class Cursor { int index = -1; }
        Cursor cursor = new Cursor();
        return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> ++cursor.index < rows.size();
                    case "getString" -> rows.get(cursor.index).get(String.valueOf(args[0]).toLowerCase(Locale.ROOT));
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
