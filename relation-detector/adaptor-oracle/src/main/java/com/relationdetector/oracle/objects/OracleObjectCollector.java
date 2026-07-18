package com.relationdetector.oracle.objects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import com.relationdetector.contracts.Enums.DatabaseObjectType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.DatabaseObjectDefinition;
import com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.oracle.OracleOwnerResolver;
import com.relationdetector.core.diagnostics.DiagnosticWarnings;

/**
 * CN: 从 ALL_OBJECTS 枚举 routine、package、view、materialized view 和 trigger，并用 DBMS_METADATA 获取完整声明；partial success 保留已成功对象，空定义只产生安全 warning。
 * EN: Enumerates routines, packages, views, materialized views, and triggers from ALL_OBJECTS and retrieves complete declarations through DBMS_METADATA. Partial success is preserved and blank definitions yield safe warnings only.
 */
public final class OracleObjectCollector implements ObjectDefinitionCollector {
    @Override
    public List<DatabaseObjectDefinition> collect(Connection connection, ScanScope scope) {
        return collect(connection, scope, ignored -> { });
    }

    @Override
    public List<DatabaseObjectDefinition> collect(
            Connection connection, ScanScope scope, Consumer<WarningMessage> warnings) {
        String owner = OracleOwnerResolver.resolve(connection, scope);
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
                        } else {
                            warnings.accept(DiagnosticWarnings.objectDefinitionUnavailable(
                                    "DBMS_METADATA.GET_DDL", null, objectOwner, name, nativeType));
                        }
                    } catch (Exception ex) {
                        warnings.accept(DiagnosticWarnings.objectCollectFailed(
                                "ORACLE_OBJECT_DDL_FAILED", "DBMS_METADATA.GET_DDL", ex,
                                null, objectOwner, name, type(nativeType),
                                com.relationdetector.oracle.OracleDatabaseAdaptor.PERMISSION_DENIED_VENDOR_CODES));
                    }
                }
            }
        } catch (Exception ex) {
            warnings.accept(DiagnosticWarnings.objectCollectFailed(
                    "ORACLE_OBJECT_LIST_FAILED", "ALL_OBJECTS", ex,
                    com.relationdetector.oracle.OracleDatabaseAdaptor.PERMISSION_DENIED_VENDOR_CODES));
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

}
