package com.relationdetector.postgres.ddl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.DatabaseDdlDefinition;
import com.relationdetector.contracts.spi.Collectors.DatabaseDdlCollector;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.core.diagnostics.LiveDiagnosticSanitizer;
import com.relationdetector.postgres.PostgresConstraintCatalogReader;
import com.relationdetector.postgres.PostgresNamespaceResolver;

/**
 * CN: 为关系解析重建 PostgreSQL 表的结构化 DDL 骨架，不承诺完整可重放 DDL。
 *
 * <p>EN: Reconstructs structural PostgreSQL table DDL for relationship parsing, not full replay fidelity.
 */
public final class PostgresDatabaseDdlCollector implements DatabaseDdlCollector {
    private final PostgresConstraintCatalogReader constraintReader = new PostgresConstraintCatalogReader();

    @Override
    public List<DatabaseDdlDefinition> collect(Connection connection, ScanScope scope) {
        return collect(connection, scope, warning -> { });
    }

    @Override
    public List<DatabaseDdlDefinition> collect(Connection connection, ScanScope scope,
            Consumer<WarningMessage> warnings) {
        var namespace = PostgresNamespaceResolver.resolve(connection, scope);
        List<DatabaseDdlDefinition> definitions = new ArrayList<>();
        for (String tableName : tableNames(connection, scope, namespace.schema(), warnings)) {
            try {
                definitions.add(new DatabaseDdlDefinition(namespace.catalog(), namespace.schema(), tableName,
                        buildCreateTable(connection, scope, namespace.schema(), tableName),
                        "POSTGRES_CATALOG_STRUCTURAL_DDL"));
            } catch (Exception ex) {
                warnings.accept(LiveDiagnosticSanitizer.jdbcWarning(
                        "POSTGRES_DATABASE_DDL_TABLE_FAILED", LiveDiagnosticSanitizer.Operation.DATABASE_DDL,
                        "pg_catalog+information_schema", ex,
                        java.util.Map.of("objectCatalog", namespace.catalog() == null ? "" : namespace.catalog(),
                                "objectSchema", namespace.schema(), "objectName", tableName, "objectType", "TABLE")));
            }
        }
        return definitions;
    }

    private List<String> tableNames(Connection connection, ScanScope scope, String schema,
            Consumer<WarningMessage> warnings) {
        String sql = """
                SELECT TABLE_NAME FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME
                """;
        List<String> tableNames = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (inScope(scope, tableName)) tableNames.add(tableName);
                }
            }
        } catch (Exception ex) {
            warnings.accept(LiveDiagnosticSanitizer.jdbcWarning(
                    "POSTGRES_DATABASE_DDL_TABLES_FAILED", LiveDiagnosticSanitizer.Operation.DATABASE_DDL,
                    "information_schema.TABLES", ex, java.util.Map.of()));
        }
        return tableNames;
    }

    private String buildCreateTable(Connection connection, ScanScope scope, String schema, String tableName)
            throws Exception {
        List<String> lines = new ArrayList<>();
        columns(connection, schema, tableName).forEach(column -> lines.add("  " + column.ddl()));
        constraintReader.read(connection, schema, onlyTable(scope, tableName)).stream()
                .filter(constraint -> tableName.equals(constraint.table()))
                .map(this::constraintDdl)
                .sorted(Comparator.comparing(ConstraintDef::name))
                .forEach(constraint -> lines.add("  " + constraint.ddl()));
        return "CREATE TABLE " + quote(schema) + "." + quote(tableName) + " (\n"
                + String.join(",\n", lines) + "\n);";
    }

    private List<ColumnDef> columns(Connection connection, String schema, String tableName) throws Exception {
        String sql = """
                SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION
                """;
        List<ColumnDef> columns = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String column = rs.getString("COLUMN_NAME");
                    columns.add(new ColumnDef(quote(column) + " " + rs.getString("DATA_TYPE")
                            + ("NO".equalsIgnoreCase(rs.getString("IS_NULLABLE")) ? " NOT NULL" : "")));
                }
            }
        }
        return columns;
    }

    private ConstraintDef constraintDdl(PostgresConstraintCatalogReader.Constraint constraint) {
        String ddl = "CONSTRAINT " + quote(constraint.name()) + " " + constraint.type() + " ("
                + quotedList(constraint.columns()) + ")";
        if ("FOREIGN KEY".equals(constraint.type())) {
            ddl += " REFERENCES " + quote(constraint.referencedSchema()) + "." + quote(constraint.referencedTable())
                    + " (" + quotedList(constraint.referencedColumns()) + ")";
        }
        return new ConstraintDef(constraint.name(), ddl);
    }

    private ScanScope onlyTable(ScanScope scope, String tableName) {
        return new ScanScope(scope.catalog(), scope.schema(), List.of(tableName), scope.excludeTables());
    }

    private boolean inScope(ScanScope scope, String tableName) {
        String normalized = normalize(tableName);
        boolean included = scope.includeTables().isEmpty()
                || scope.includeTables().stream().map(this::normalize).anyMatch(normalized::equals);
        return included && scope.excludeTables().stream().map(this::normalize).noneMatch(normalized::equals);
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private String quotedList(List<String> columns) {
        return columns.stream().map(this::quote).reduce((left, right) -> left + ", " + right).orElse("");
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record ColumnDef(String ddl) { }
    private record ConstraintDef(String name, String ddl) { }
}
