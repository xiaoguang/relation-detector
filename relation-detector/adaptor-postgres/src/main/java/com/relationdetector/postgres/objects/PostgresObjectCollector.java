package com.relationdetector.postgres.objects;

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
import com.relationdetector.core.diagnostics.DiagnosticWarnings;
import com.relationdetector.postgres.PostgresNamespaceResolver;

/**
 * CN: 采集 PostgreSQL routine、view、materialized view、rule 与用户 trigger。
 *
 * <p>EN: Collects PostgreSQL routines, views, materialized views, rules, and user triggers.
 */
public final class PostgresObjectCollector implements ObjectDefinitionCollector {
    @Override
    public List<DatabaseObjectDefinition> collect(Connection connection, ScanScope scope) {
        return collect(connection, scope, warning -> { });
    }

    @Override
    public List<DatabaseObjectDefinition> collect(Connection connection, ScanScope scope,
            Consumer<WarningMessage> warnings) {
        var namespace = PostgresNamespaceResolver.resolve(connection, scope);
        List<DatabaseObjectDefinition> definitions = new ArrayList<>();
        collectFunctions(connection, namespace.catalog(), namespace.schema(), definitions, warnings);
        collectViews(connection, namespace.catalog(), namespace.schema(), definitions, warnings);
        collectMaterializedViews(connection, namespace.catalog(), namespace.schema(), definitions, warnings);
        collectRules(connection, namespace.catalog(), namespace.schema(), definitions, warnings);
        collectTriggers(connection, namespace.catalog(), namespace.schema(), definitions, warnings);
        definitions.sort(Comparator.comparing((DatabaseObjectDefinition value) -> safe(value.catalog()))
                .thenComparing(value -> safe(value.schema())).thenComparing(value -> value.type().name())
                .thenComparing(DatabaseObjectDefinition::name));
        return definitions;
    }

    private void collectFunctions(Connection connection, String catalog, String schema,
            List<DatabaseObjectDefinition> definitions, Consumer<WarningMessage> warnings) {
        String sql = """
                SELECT n.nspname AS schema_name, p.proname AS object_name, p.prokind,
                       pg_get_function_identity_arguments(p.oid) AS identity_arguments,
                       pg_get_functiondef(p.oid) AS definition
                FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace WHERE n.nspname = ?
                """;
        collect(connection, schema, sql, definitions, warnings, "POSTGRES_FUNCTION_COLLECT_FAILED", "pg_proc",
                rs -> new DatabaseObjectDefinition("p".equals(rs.getString("prokind"))
                        ? DatabaseObjectType.PROCEDURE : DatabaseObjectType.FUNCTION,
                        catalog, rs.getString("schema_name"), routineIdentity(rs),
                        rs.getString("definition"), "pg_proc"));
    }

    private void collectViews(Connection connection, String catalog, String schema,
            List<DatabaseObjectDefinition> definitions, Consumer<WarningMessage> warnings) {
        String sql = "SELECT schemaname AS schema_name, viewname AS object_name, definition FROM pg_views WHERE schemaname = ?";
        collect(connection, schema, sql, definitions, warnings, "POSTGRES_VIEW_COLLECT_FAILED", "pg_views",
                rs -> definition(DatabaseObjectType.VIEW, catalog, rs, "pg_views"));
    }

    private void collectMaterializedViews(Connection connection, String catalog, String schema,
            List<DatabaseObjectDefinition> definitions, Consumer<WarningMessage> warnings) {
        String sql = "SELECT schemaname AS schema_name, matviewname AS object_name, definition FROM pg_matviews WHERE schemaname = ?";
        collect(connection, schema, sql, definitions, warnings, "POSTGRES_MATERIALIZED_VIEW_COLLECT_FAILED",
                "pg_matviews", rs -> definition(DatabaseObjectType.MATERIALIZED_VIEW, catalog, rs, "pg_matviews"));
    }

    private void collectRules(Connection connection, String catalog, String schema,
            List<DatabaseObjectDefinition> definitions, Consumer<WarningMessage> warnings) {
        String sql = "SELECT schemaname AS schema_name, tablename || '.' || rulename AS object_name, definition FROM pg_rules WHERE schemaname = ?";
        collect(connection, schema, sql, definitions, warnings, "POSTGRES_RULE_COLLECT_FAILED", "pg_rules",
                rs -> definition(DatabaseObjectType.RULE, catalog, rs, "pg_rules"));
    }

    private void collectTriggers(Connection connection, String catalog, String schema,
            List<DatabaseObjectDefinition> definitions, Consumer<WarningMessage> warnings) {
        String sql = """
                SELECT n.nspname AS schema_name, t.tgname AS trigger_name,
                       pg_get_triggerdef(t.oid, true) AS definition
                FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND NOT t.tgisinternal ORDER BY t.tgname
                """;
        collect(connection, schema, sql, definitions, warnings, "POSTGRES_TRIGGER_COLLECT_FAILED", "pg_trigger",
                rs -> new DatabaseObjectDefinition(DatabaseObjectType.TRIGGER, catalog, rs.getString("schema_name"),
                        rs.getString("trigger_name"), rs.getString("definition"), "pg_trigger"));
    }

    private void collect(Connection connection, String schema, String sql,
            List<DatabaseObjectDefinition> definitions, Consumer<WarningMessage> warnings,
            String failureCode, String source, RowMapper mapper) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DatabaseObjectDefinition definition = mapper.map(rs);
                    if (definition.sql() == null || definition.sql().isBlank()) {
                        warnings.accept(DiagnosticWarnings.objectDefinitionUnavailable(
                                definition.source(), definition.catalog(), definition.schema(), definition.name(),
                                definition.type().name()));
                    } else {
                        definitions.add(definition);
                    }
                }
            }
        } catch (Exception ex) {
            warnings.accept(DiagnosticWarnings.objectCollectFailed(failureCode, source, ex));
        }
    }

    private DatabaseObjectDefinition definition(DatabaseObjectType type, String catalog, ResultSet rs, String source)
            throws Exception {
        return new DatabaseObjectDefinition(type, catalog, rs.getString("schema_name"),
                rs.getString("object_name"), rs.getString("definition"), source);
    }

    private String routineIdentity(ResultSet resultSet) throws Exception {
        String arguments = resultSet.getString("identity_arguments");
        return resultSet.getString("object_name") + "(" + (arguments == null ? "" : arguments.trim()) + ")";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @FunctionalInterface
    private interface RowMapper {
        DatabaseObjectDefinition map(ResultSet rs) throws Exception;
    }
}
