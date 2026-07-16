package com.relationdetector.sqlserver.ddl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.DatabaseDdlDefinition;
import com.relationdetector.contracts.spi.Collectors.DatabaseDdlCollector;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.sqlserver.SqlServerCatalogResolver;
import com.relationdetector.core.diagnostics.LiveDiagnosticSanitizer;

/**
 *
 * Collects SQL Server table DDL from INFORMATION_SCHEMA views.
 */
public final class SqlServerDatabaseDdlCollector implements DatabaseDdlCollector {
    @Override
    public List<DatabaseDdlDefinition> collect(Connection connection, ScanScope scope) {
        return collect(connection, scope, warning -> {
        });
    }

    @Override
    public List<DatabaseDdlDefinition> collect(
            Connection connection,
            ScanScope scope,
            Consumer<WarningMessage> warnings
    ) {
        String schema = schema(scope);
        String catalog = SqlServerCatalogResolver.resolve(connection, scope);
        List<DatabaseDdlDefinition> definitions = new ArrayList<>();
        for (String tableName : tableNames(connection, scope, schema, warnings)) {
            try {
                definitions.add(new DatabaseDdlDefinition(catalog,
                        schema,
                        tableName,
                        buildCreateTable(connection, schema, tableName),
                        "INFORMATION_SCHEMA"));
            } catch (Exception ex) {
                warnings.accept(LiveDiagnosticSanitizer.jdbcWarning(
                        "SQLSERVER_DATABASE_DDL_TABLE_FAILED", LiveDiagnosticSanitizer.Operation.DATABASE_DDL,
                        "INFORMATION_SCHEMA", ex,
                        Map.of("objectSchema", schema,
                                "objectName", tableName,
                                "objectType", "TABLE"),
                        com.relationdetector.sqlserver.SqlServerDatabaseAdaptor.PERMISSION_DENIED_VENDOR_CODES));
            }
        }
        return definitions;
    }

    private List<String> tableNames(
            Connection connection,
            ScanScope scope,
            String schema,
            Consumer<WarningMessage> warnings
    ) {
        String sql = """
                SELECT TABLE_NAME
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = ?
                  AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME
                """;
        List<String> tableNames = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (inScope(scope, tableName)) {
                        tableNames.add(tableName);
                    }
                }
            }
        } catch (Exception ex) {
            warnings.accept(LiveDiagnosticSanitizer.jdbcWarning(
                    "SQLSERVER_DATABASE_DDL_TABLES_FAILED", LiveDiagnosticSanitizer.Operation.DATABASE_DDL,
                    "INFORMATION_SCHEMA.TABLES", ex, Map.of(),
                    com.relationdetector.sqlserver.SqlServerDatabaseAdaptor.PERMISSION_DENIED_VENDOR_CODES));
        }
        return tableNames;
    }

    private String buildCreateTable(Connection connection, String schema, String tableName) throws Exception {
        List<String> lines = new ArrayList<>();
        columns(connection, schema, tableName).forEach(column -> lines.add("  " + column.ddl()));
        constraints(connection, schema, tableName).forEach(constraint -> lines.add("  " + constraint.ddl()));
        return "CREATE TABLE " + quote(schema) + "." + quote(tableName) + " (\n"
                + String.join(",\n", lines)
                + "\n);";
    }

    private List<ColumnDef> columns(Connection connection, String schema, String tableName) throws Exception {
        String sql = """
                SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = ?
                  AND TABLE_NAME = ?
                ORDER BY ORDINAL_POSITION
                """;
        List<ColumnDef> columns = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String column = rs.getString("COLUMN_NAME");
                    String type = rs.getString("DATA_TYPE");
                    String nullable = rs.getString("IS_NULLABLE");
                    columns.add(new ColumnDef(column, quote(column) + " " + type
                            + ("NO".equalsIgnoreCase(nullable) ? " NOT NULL" : "")));
                }
            }
        }
        return columns;
    }

    private List<ConstraintDef> constraints(Connection connection, String schema, String tableName) throws Exception {
        List<ConstraintDef> constraints = new ArrayList<>();
        constraints.addAll(keyConstraints(connection, schema, tableName));
        constraints.addAll(foreignKeys(connection, schema, tableName));
        return constraints.stream().sorted(Comparator.comparing(ConstraintDef::name)).toList();
    }

    private List<ConstraintDef> keyConstraints(Connection connection, String schema, String tableName)
            throws Exception {
        String sql = """
                SELECT kc.name AS CONSTRAINT_NAME, kc.type AS CONSTRAINT_TYPE,
                       c.name AS COLUMN_NAME, ic.key_ordinal AS COLUMN_ORDINAL
                FROM sys.key_constraints kc
                JOIN sys.tables t ON t.object_id = kc.parent_object_id
                JOIN sys.schemas s ON s.schema_id = t.schema_id
                JOIN sys.index_columns ic
                  ON ic.object_id = kc.parent_object_id
                 AND ic.index_id = kc.unique_index_id
                JOIN sys.columns c
                  ON c.object_id = ic.object_id
                 AND c.column_id = ic.column_id
                WHERE s.name = ? AND t.name = ?
                ORDER BY kc.name, ic.key_ordinal
                """;
        Map<String, ConstraintBuilder> byName = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("CONSTRAINT_NAME");
                    String type = "PK".equalsIgnoreCase(rs.getString("CONSTRAINT_TYPE"))
                            ? "PRIMARY KEY" : "UNIQUE";
                    ConstraintBuilder builder = byName.computeIfAbsent(name,
                            key -> new ConstraintBuilder(name, type));
                    builder.addColumn(rs.getInt("COLUMN_ORDINAL"), rs.getString("COLUMN_NAME"));
                }
            }
        }
        return byName.values().stream()
                .map(ConstraintBuilder::build)
                .toList();
    }

    private List<ConstraintDef> foreignKeys(Connection connection, String schema, String tableName)
            throws Exception {
        String sql = """
                SELECT fk.name AS CONSTRAINT_NAME,
                       pc.name AS CHILD_COLUMN_NAME,
                       rs.name AS FOREIGN_TABLE_SCHEMA,
                       rt.name AS FOREIGN_TABLE_NAME,
                       rc.name AS FOREIGN_COLUMN_NAME,
                       fkc.constraint_column_id AS COLUMN_ORDINAL
                FROM sys.foreign_keys fk
                JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
                JOIN sys.tables ct ON ct.object_id = fk.parent_object_id
                JOIN sys.schemas cs ON cs.schema_id = ct.schema_id
                JOIN sys.columns pc
                  ON pc.object_id = fkc.parent_object_id
                 AND pc.column_id = fkc.parent_column_id
                JOIN sys.tables rt ON rt.object_id = fk.referenced_object_id
                JOIN sys.schemas rs ON rs.schema_id = rt.schema_id
                JOIN sys.columns rc
                  ON rc.object_id = fkc.referenced_object_id
                 AND rc.column_id = fkc.referenced_column_id
                WHERE cs.name = ? AND ct.name = ?
                ORDER BY fk.name, fkc.constraint_column_id
                """;
        Map<String, ConstraintBuilder> byName = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("CONSTRAINT_NAME");
                    ConstraintBuilder builder = byName.computeIfAbsent(name,
                            key -> new ConstraintBuilder(name, "FOREIGN KEY"));
                    int ordinal = rs.getInt("COLUMN_ORDINAL");
                    builder.addColumn(ordinal, rs.getString("CHILD_COLUMN_NAME"));
                    builder.addForeignColumn(ordinal, rs.getString("FOREIGN_COLUMN_NAME"));
                    builder.foreignSchema = rs.getString("FOREIGN_TABLE_SCHEMA");
                    builder.foreignTable = rs.getString("FOREIGN_TABLE_NAME");
                }
            }
        }
        return byName.values().stream().map(ConstraintBuilder::build).toList();
    }

    private String schema(ScanScope scope) {
        return scope.schema() == null || scope.schema().isBlank() ? "dbo" : scope.schema();
    }

    private boolean inScope(ScanScope scope, String tableName) {
        String normalized = normalize(tableName);
        boolean included = scope.includeTables().isEmpty()
                || scope.includeTables().stream().map(this::normalize).anyMatch(normalized::equals);
        boolean excluded = scope.excludeTables().stream().map(this::normalize).anyMatch(normalized::equals);
        return included && !excluded;
    }

    private String quote(String identifier) {
        return "[" + identifier.replace("]", "]]") + "]";
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record ColumnDef(String name, String ddl) {
    }

    private record ConstraintDef(String name, String ddl) {
    }

    private final class ConstraintBuilder {
        private final String name;
        private final String type;
        private final Map<Integer, String> columns = new TreeMap<>();
        private final Map<Integer, String> foreignColumns = new TreeMap<>();
        private String foreignSchema;
        private String foreignTable;

        private ConstraintBuilder(String name, String type) {
            this.name = name;
            this.type = type;
        }

        private void addColumn(int ordinal, String column) {
            columns.put(ordinal, column);
        }

        private void addForeignColumn(int ordinal, String column) {
            foreignColumns.put(ordinal, column);
        }

        private ConstraintDef build() {
            String ddl = "CONSTRAINT " + quote(name) + " " + type + " ("
                    + quotedList(new ArrayList<>(columns.values())) + ")";
            if ("FOREIGN KEY".equalsIgnoreCase(type)) {
                ddl += " REFERENCES " + quote(foreignSchema) + "." + quote(foreignTable)
                        + " (" + quotedList(new ArrayList<>(foreignColumns.values())) + ")";
            }
            return new ConstraintDef(name, ddl);
        }
    }

    private String quotedList(List<String> columns) {
        return columns.stream()
                .filter(column -> column != null && !column.isBlank())
                .map(this::quote)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }
}
