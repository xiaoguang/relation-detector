package com.relationdetector.mysql.objects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.relationdetector.contracts.Enums.DatabaseObjectType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.DatabaseObjectDefinition;
import com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.core.diagnostics.DiagnosticWarnings;
import com.relationdetector.mysql.MySqlCatalogScope;

/**
 *
 * Collects complete MySQL object declarations through {@code SHOW CREATE}.
 */
public final class MySqlObjectCollector implements ObjectDefinitionCollector {
    @Override
    public List<DatabaseObjectDefinition> collect(Connection connection, ScanScope scope) {
        return collect(connection, scope, warning -> {
        });
    }

    @Override
    public List<DatabaseObjectDefinition> collect(
            Connection connection,
            ScanScope scope,
            Consumer<WarningMessage> warnings
    ) {
        ScanScope canonicalScope = MySqlCatalogScope.canonicalize(scope);
        List<DatabaseObjectDefinition> definitions = new ArrayList<>();
        collectRoutines(connection, canonicalScope, definitions, warnings);
        collectObjects(connection, canonicalScope, definitions, warnings,
                DatabaseObjectType.VIEW,
                "SELECT TABLE_SCHEMA, TABLE_NAME FROM information_schema.VIEWS WHERE TABLE_SCHEMA = ?",
                "TABLE_SCHEMA", "TABLE_NAME", "MYSQL_VIEW_COLLECT_FAILED", "information_schema.VIEWS");
        collectObjects(connection, canonicalScope, definitions, warnings,
                DatabaseObjectType.TRIGGER,
                "SELECT TRIGGER_SCHEMA, TRIGGER_NAME FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = ?",
                "TRIGGER_SCHEMA", "TRIGGER_NAME", "MYSQL_TRIGGER_COLLECT_FAILED", "information_schema.TRIGGERS");
        collectObjects(connection, canonicalScope, definitions, warnings,
                DatabaseObjectType.EVENT,
                "SELECT EVENT_SCHEMA, EVENT_NAME FROM information_schema.EVENTS WHERE EVENT_SCHEMA = ?",
                "EVENT_SCHEMA", "EVENT_NAME", "MYSQL_EVENT_COLLECT_FAILED", "information_schema.EVENTS");
        return definitions;
    }

    private void collectRoutines(
            Connection connection,
            ScanScope scope,
            List<DatabaseObjectDefinition> definitions,
            Consumer<WarningMessage> warnings
    ) {
        String sql = """
                SELECT ROUTINE_SCHEMA, ROUTINE_NAME, ROUTINE_TYPE
                FROM information_schema.ROUTINES
                WHERE ROUTINE_SCHEMA = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.catalog());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DatabaseObjectType type = "FUNCTION".equalsIgnoreCase(rs.getString("ROUTINE_TYPE"))
                            ? DatabaseObjectType.FUNCTION
                            : DatabaseObjectType.PROCEDURE;
                    collectShowCreate(connection, definitions, warnings, type,
                            rs.getString("ROUTINE_SCHEMA"), rs.getString("ROUTINE_NAME"));
                }
            }
        } catch (Exception ex) {
            warnings.accept(DiagnosticWarnings.objectCollectFailed(
                    "MYSQL_ROUTINE_COLLECT_FAILED", "information_schema.ROUTINES", ex));
        }
    }

    private void collectObjects(
            Connection connection,
            ScanScope scope,
            List<DatabaseObjectDefinition> definitions,
            Consumer<WarningMessage> warnings,
            DatabaseObjectType type,
            String sql,
            String catalogColumn,
            String nameColumn,
            String collectFailureCode,
            String source
    ) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.catalog());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    collectShowCreate(connection, definitions, warnings, type,
                            rs.getString(catalogColumn), rs.getString(nameColumn));
                }
            }
        } catch (Exception ex) {
            warnings.accept(DiagnosticWarnings.objectCollectFailed(collectFailureCode, source, ex));
        }
    }

    private void collectShowCreate(
            Connection connection,
            List<DatabaseObjectDefinition> definitions,
            Consumer<WarningMessage> warnings,
            DatabaseObjectType type,
            String catalog,
            String objectName
    ) {
        ShowCreateSpec spec = ShowCreateSpec.forType(type);
        String source = "SHOW CREATE " + spec.keyword();
        String sql = source + " " + quote(catalog) + "." + quote(objectName);
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            if (!rs.next()) {
                throw new IllegalStateException(source + " returned no row");
            }
            String ddl = rs.getString(spec.resultColumn());
            if (ddl == null || ddl.isBlank()) {
                warnings.accept(DiagnosticWarnings.objectDefinitionUnavailable(
                        source, catalog, null, objectName, type.name()));
                return;
            }
            definitions.add(new DatabaseObjectDefinition(type, catalog, null, objectName, ddl, source));
        } catch (Exception ex) {
            warnings.accept(DiagnosticWarnings.objectCollectFailed(spec.failureCode(), "MYSQL_OBJECT_CATALOG", ex,
                    catalog, null, objectName, type));
        }
    }

    private String quote(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private record ShowCreateSpec(String keyword, String resultColumn, String failureCode) {
        private static ShowCreateSpec forType(DatabaseObjectType type) {
            return switch (type) {
                case PROCEDURE -> new ShowCreateSpec(
                        "PROCEDURE", "Create Procedure", "MYSQL_ROUTINE_SHOW_CREATE_FAILED");
                case FUNCTION -> new ShowCreateSpec(
                        "FUNCTION", "Create Function", "MYSQL_ROUTINE_SHOW_CREATE_FAILED");
                case VIEW -> new ShowCreateSpec(
                        "VIEW", "Create View", "MYSQL_VIEW_SHOW_CREATE_FAILED");
                case TRIGGER -> new ShowCreateSpec(
                        "TRIGGER", "SQL Original Statement", "MYSQL_TRIGGER_SHOW_CREATE_FAILED");
                case EVENT -> new ShowCreateSpec(
                        "EVENT", "Create Event", "MYSQL_EVENT_SHOW_CREATE_FAILED");
                default -> throw new IllegalArgumentException("Unsupported MySQL object type: " + type);
            };
        }
    }
}
