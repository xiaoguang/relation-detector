package com.relationdetector.mysql.ddl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.DatabaseDdlDefinition;
import com.relationdetector.contracts.spi.Collectors.DatabaseDdlCollector;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.mysql.MySqlCatalogScope;
import com.relationdetector.core.diagnostics.LiveDiagnosticSanitizer;

/**
 * CN: 按 canonical catalog 枚举 in-scope base tables，并用 SHOW CREATE TABLE 读取 parser-grade 声明；输出按表稳定排序，单表失败产生脱敏 warning，不负责解析 DDL。
 * EN: Enumerates in-scope base tables in the canonical catalog and reads parser-grade declarations with SHOW CREATE TABLE. Results are stable, per-table failures are sanitized, and DDL parsing remains downstream.
 */
public final class MySqlDatabaseDdlCollector implements DatabaseDdlCollector {
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
        ScanScope canonicalScope = MySqlCatalogScope.canonicalize(scope);
        List<DatabaseDdlDefinition> definitions = new ArrayList<>();
        for (String tableName : tableNames(connection, canonicalScope, warnings)) {
            collectShowCreate(connection, canonicalScope.catalog(), tableName, definitions, warnings);
        }
        return definitions;
    }

    private List<String> tableNames(Connection connection, ScanScope scope, Consumer<WarningMessage> warnings) {
        String sql = """
                SELECT TABLE_NAME
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ?
                  AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME
                """;
        List<String> tableNames = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.catalog());
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
                    "MYSQL_DATABASE_DDL_TABLES_FAILED", LiveDiagnosticSanitizer.Operation.DATABASE_DDL,
                    "information_schema.TABLES", ex, java.util.Map.of()));
        }
        return tableNames;
    }

    private void collectShowCreate(
            Connection connection,
            String catalog,
            String tableName,
            List<DatabaseDdlDefinition> definitions,
            Consumer<WarningMessage> warnings
    ) {
        String sql = "SHOW CREATE TABLE " + quote(catalog) + "." + quote(tableName);
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            if (rs.next()) {
                String ddl = rs.getString(2);
                if (ddl == null || ddl.isBlank()) {
                    warnings.accept(com.relationdetector.core.diagnostics.DiagnosticWarnings
                            .databaseDdlDefinitionUnavailable("SHOW CREATE TABLE", catalog, null, tableName));
                } else {
                    definitions.add(new DatabaseDdlDefinition(catalog, null, tableName,
                            ddl, "SHOW CREATE TABLE"));
                }
            }
        } catch (Exception ex) {
            warnings.accept(LiveDiagnosticSanitizer.jdbcWarning(
                    "MYSQL_SHOW_CREATE_TABLE_FAILED", LiveDiagnosticSanitizer.Operation.DATABASE_DDL,
                    "SHOW CREATE TABLE", ex,
                    java.util.Map.of("objectCatalog", catalog,
                            "objectName", tableName,
                            "objectType", "TABLE")));
        }
    }

    private boolean inScope(ScanScope scope, String tableName) {
        String normalized = normalize(tableName);
        boolean included = scope.includeTables().isEmpty()
                || scope.includeTables().stream().map(this::normalize).anyMatch(normalized::equals);
        boolean excluded = scope.excludeTables().stream().map(this::normalize).anyMatch(normalized::equals);
        return included && !excluded;
    }

    private String quote(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
