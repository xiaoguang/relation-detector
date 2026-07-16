package com.relationdetector.sqlserver.objects;

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
import com.relationdetector.sqlserver.SqlServerCatalogResolver;
import com.relationdetector.core.diagnostics.DiagnosticWarnings;

/**
 *
 * Collects SQL Server module declarations from sys.sql_modules.
 */
public final class SqlServerObjectCollector implements ObjectDefinitionCollector {
    @Override
    public List<DatabaseObjectDefinition> collect(Connection connection, ScanScope scope) {
        return collect(connection, scope, ignored -> { });
    }

    @Override
    public List<DatabaseObjectDefinition> collect(
            Connection connection, ScanScope scope, Consumer<WarningMessage> warnings) {
        String schema = scope.schema() == null || scope.schema().isBlank() ? "dbo" : scope.schema();
        String catalog = SqlServerCatalogResolver.resolve(connection, scope);
        String sql = """
                SELECT s.name AS schema_name, o.name AS object_name, o.type AS object_type, m.definition
                FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id
                LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id
                WHERE s.name=? AND o.type IN ('P','PC','FN','IF','TF','V','TR')
                ORDER BY s.name, o.type, o.name
                """;
        List<DatabaseObjectDefinition> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String objectSchema = rs.getString("schema_name");
                    String name = rs.getString("object_name");
                    String definition = rs.getString("definition");
                    if (definition == null || definition.isBlank()) {
                        warnings.accept(DiagnosticWarnings.objectDefinitionUnavailable(
                                "sys.sql_modules", catalog, objectSchema, name,
                                type(rs.getString("object_type")).name()));
                        continue;
                    }
                    result.add(new DatabaseObjectDefinition(type(rs.getString("object_type")), catalog,
                            objectSchema, name, definition, "sys.sql_modules"));
                }
            }
        } catch (Exception ex) {
            warnings.accept(DiagnosticWarnings.objectCollectFailed(
                    "SQLSERVER_OBJECT_LIST_FAILED", "sys.sql_modules", ex,
                    com.relationdetector.sqlserver.SqlServerDatabaseAdaptor.PERMISSION_DENIED_VENDOR_CODES));
        }
        return result.stream().sorted(Comparator.comparing(DatabaseObjectDefinition::schema)
                .thenComparing(value -> value.type().name()).thenComparing(DatabaseObjectDefinition::name)).toList();
    }

    private DatabaseObjectType type(String type) {
        return switch (type) {
            case "P", "PC" -> DatabaseObjectType.PROCEDURE;
            case "FN", "IF", "TF" -> DatabaseObjectType.FUNCTION;
            case "V" -> DatabaseObjectType.VIEW;
            case "TR" -> DatabaseObjectType.TRIGGER;
            default -> throw new IllegalArgumentException("unsupported SQL Server object type " + type);
        };
    }

}
