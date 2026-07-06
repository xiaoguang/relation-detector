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
import java.util.function.Consumer;

import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.DatabaseDdlDefinition;
import com.relationdetector.contracts.spi.Collectors.DatabaseDdlCollector;
import com.relationdetector.contracts.spi.ScanScope;

/** Collects SQL Server table DDL from INFORMATION_SCHEMA views. */
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
        List<DatabaseDdlDefinition> definitions = new ArrayList<>();
        for (String tableName : tableNames(connection, scope, schema, warnings)) {
            try {
                definitions.add(new DatabaseDdlDefinition(
                        schema,
                        tableName,
                        buildCreateTable(connection, schema, tableName),
                        "INFORMATION_SCHEMA"));
            } catch (Exception ex) {
                warnings.accept(WarningMessage.warn(WarningType.PERMISSION_WARNING,
                        "SQLSERVER_DATABASE_DDL_TABLE_FAILED", ex.getMessage(), "INFORMATION_SCHEMA", 0,
                        Map.of("objectSchema", schema,
                                "objectName", tableName,
                                "objectType", "TABLE",
                                "exceptionClass", ex.getClass().getSimpleName())));
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
            warnings.accept(WarningMessage.warn(WarningType.PERMISSION_WARNING,
                    "SQLSERVER_DATABASE_DDL_TABLES_FAILED", ex.getMessage(), "INFORMATION_SCHEMA.TABLES", 0));
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
        String sql = """
                SELECT tc.CONSTRAINT_NAME, tc.CONSTRAINT_TYPE, kcu.COLUMN_NAME,
                       ccu.TABLE_SCHEMA AS FOREIGN_TABLE_SCHEMA,
                       ccu.TABLE_NAME AS FOREIGN_TABLE_NAME,
                       ccu.COLUMN_NAME AS FOREIGN_COLUMN_NAME
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                  ON tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA
                 AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
                 AND tc.TABLE_SCHEMA = kcu.TABLE_SCHEMA
                 AND tc.TABLE_NAME = kcu.TABLE_NAME
                LEFT JOIN INFORMATION_SCHEMA.CONSTRAINT_COLUMN_USAGE ccu
                  ON tc.CONSTRAINT_SCHEMA = ccu.CONSTRAINT_SCHEMA
                 AND tc.CONSTRAINT_NAME = ccu.CONSTRAINT_NAME
                WHERE tc.TABLE_SCHEMA = ?
                  AND tc.TABLE_NAME = ?
                  AND tc.CONSTRAINT_TYPE IN ('PRIMARY KEY', 'UNIQUE', 'FOREIGN KEY')
                ORDER BY tc.CONSTRAINT_NAME, kcu.ORDINAL_POSITION
                """;
        Map<String, ConstraintBuilder> byName = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("CONSTRAINT_NAME");
                    String type = rs.getString("CONSTRAINT_TYPE");
                    ConstraintBuilder builder = byName.computeIfAbsent(name,
                            key -> new ConstraintBuilder(name, type));
                    builder.columns.add(rs.getString("COLUMN_NAME"));
                    builder.foreignSchema = rs.getString("FOREIGN_TABLE_SCHEMA");
                    builder.foreignTable = rs.getString("FOREIGN_TABLE_NAME");
                    builder.foreignColumns.add(rs.getString("FOREIGN_COLUMN_NAME"));
                }
            }
        }
        return byName.values().stream()
                .map(ConstraintBuilder::build)
                .sorted(Comparator.comparing(ConstraintDef::name))
                .toList();
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
        private final List<String> columns = new ArrayList<>();
        private final List<String> foreignColumns = new ArrayList<>();
        private String foreignSchema;
        private String foreignTable;

        private ConstraintBuilder(String name, String type) {
            this.name = name;
            this.type = type;
        }

        private ConstraintDef build() {
            String ddl = "CONSTRAINT " + quote(name) + " " + type + " ("
                    + quotedList(columns) + ")";
            if ("FOREIGN KEY".equalsIgnoreCase(type)) {
                ddl += " REFERENCES " + quote(foreignSchema) + "." + quote(foreignTable)
                        + " (" + quotedList(foreignColumns) + ")";
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
