package com.relationdetector.oracle.objects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.relationdetector.contracts.Enums.DatabaseObjectType;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.DatabaseObjectDefinition;
import com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector;
import com.relationdetector.contracts.spi.ScanScope;

/** Collects Oracle stored object declarations through ALL_OBJECTS and DBMS_METADATA. */
public final class OracleObjectCollector implements ObjectDefinitionCollector {
    @Override
    public List<DatabaseObjectDefinition> collect(Connection connection, ScanScope scope) {
        return collect(connection, scope, ignored -> { });
    }

    @Override
    public List<DatabaseObjectDefinition> collect(
            Connection connection, ScanScope scope, Consumer<WarningMessage> warnings) {
        String owner = owner(connection, scope);
        String sql = """
                SELECT OWNER, OBJECT_NAME, OBJECT_TYPE FROM ALL_OBJECTS
                WHERE OWNER=? AND OBJECT_TYPE IN
                  ('PROCEDURE','FUNCTION','PACKAGE','PACKAGE BODY','VIEW','MATERIALIZED VIEW','TRIGGER')
                ORDER BY OBJECT_TYPE, OBJECT_NAME
                """;
        List<DatabaseObjectDefinition> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String objectOwner = rs.getString("OWNER");
                    String name = rs.getString("OBJECT_NAME");
                    String nativeType = rs.getString("OBJECT_TYPE");
                    try {
                        String ddl = ddl(connection, nativeType, name, objectOwner);
                        if (ddl != null && !ddl.isBlank()) {
                            result.add(new DatabaseObjectDefinition(type(nativeType), null, objectOwner, name,
                                    ddl, "DBMS_METADATA.GET_DDL"));
                        }
                    } catch (Exception ex) {
                        warnings.accept(WarningMessage.warn(WarningType.PERMISSION_WARNING,
                                "ORACLE_OBJECT_DDL_FAILED", ex.getMessage(), objectOwner + "." + name, 0));
                    }
                }
            }
        } catch (Exception ex) {
            warnings.accept(WarningMessage.warn(WarningType.PERMISSION_WARNING,
                    "ORACLE_OBJECT_LIST_FAILED", ex.getMessage(), "ALL_OBJECTS", 0));
        }
        return result.stream().sorted(Comparator.comparing(DatabaseObjectDefinition::schema)
                .thenComparing(value -> value.type().name()).thenComparing(DatabaseObjectDefinition::name)).toList();
    }

    private String ddl(Connection connection, String type, String name, String owner) throws Exception {
        String metadataType = type.replace(' ', '_');
        try (PreparedStatement ps = connection.prepareStatement("SELECT DBMS_METADATA.GET_DDL(?, ?, ?) AS DDL FROM DUAL")) {
            ps.setString(1, metadataType);
            ps.setString(2, name);
            ps.setString(3, owner);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString("DDL") : null; }
        }
    }

    private DatabaseObjectType type(String value) {
        return switch (value) {
            case "PROCEDURE" -> DatabaseObjectType.PROCEDURE;
            case "FUNCTION" -> DatabaseObjectType.FUNCTION;
            case "PACKAGE" -> DatabaseObjectType.PACKAGE;
            case "PACKAGE BODY" -> DatabaseObjectType.PACKAGE_BODY;
            case "VIEW" -> DatabaseObjectType.VIEW;
            case "MATERIALIZED VIEW" -> DatabaseObjectType.MATERIALIZED_VIEW;
            case "TRIGGER" -> DatabaseObjectType.TRIGGER;
            default -> throw new IllegalArgumentException("unsupported Oracle object type " + value);
        };
    }

    private String owner(Connection connection, ScanScope scope) {
        if (scope.schema() != null && !scope.schema().isBlank()) return scope.schema().toUpperCase(Locale.ROOT);
        try { if (connection.getSchema() != null) return connection.getSchema().toUpperCase(Locale.ROOT); }
        catch (Exception ignored) { }
        try { return connection.getMetaData().getUserName().toUpperCase(Locale.ROOT); }
        catch (Exception ignored) { return ""; }
    }
}
