package com.relationdetector.oracle.ddl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.DatabaseDdlDefinition;
import com.relationdetector.contracts.spi.Collectors.DatabaseDdlCollector;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.oracle.OracleOwnerResolver;
import com.relationdetector.core.diagnostics.DiagnosticWarnings;
import com.relationdetector.core.diagnostics.LiveDiagnosticSanitizer;

/**
 *
 * Collects Oracle table DDL through DBMS_METADATA.
 */
public final class OracleDatabaseDdlCollector implements DatabaseDdlCollector {
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
        String owner = OracleOwnerResolver.resolve(connection, scope);
        List<DatabaseDdlDefinition> definitions = new ArrayList<>();
        for (String tableName : tableNames(connection, scope, owner, warnings)) {
            collectTableDdl(connection, owner, tableName, definitions, warnings);
        }
        return definitions;
    }

    private List<String> tableNames(
            Connection connection,
            ScanScope scope,
            String owner,
            Consumer<WarningMessage> warnings
    ) {
        String sql = """
                SELECT TABLE_NAME
                FROM ALL_TABLES
                WHERE OWNER = ?
                ORDER BY TABLE_NAME
                """;
        List<String> tableNames = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (inScope(scope, tableName)) {
                        tableNames.add(tableName);
                    }
                }
            }
        } catch (Exception ex) {
            warnings.accept(DiagnosticWarnings.databaseDdlCollectFailed(
                    "ORACLE_DATABASE_DDL_TABLES_FAILED", "ALL_TABLES", ex,
                    com.relationdetector.oracle.OracleDatabaseAdaptor.PERMISSION_DENIED_VENDOR_CODES));
        }
        return tableNames;
    }

    private void collectTableDdl(
            Connection connection,
            String owner,
            String tableName,
            List<DatabaseDdlDefinition> definitions,
            Consumer<WarningMessage> warnings
    ) {
        String sql = "SELECT DBMS_METADATA.GET_DDL('TABLE', ?, ?) AS DDL FROM DUAL";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, owner);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String ddl = rs.getString(1);
                    if (ddl == null || ddl.isBlank()) {
                        warnings.accept(DiagnosticWarnings.databaseDdlDefinitionUnavailable(
                                "DBMS_METADATA.GET_DDL", null, owner, tableName));
                    } else {
                        definitions.add(new DatabaseDdlDefinition(null, owner, tableName,
                                ddl, "DBMS_METADATA.GET_DDL"));
                    }
                }
            }
        } catch (Exception ex) {
            warnings.accept(LiveDiagnosticSanitizer.jdbcWarning(
                    "ORACLE_DBMS_METADATA_GET_DDL_FAILED",
                    LiveDiagnosticSanitizer.Operation.DATABASE_DDL, "DBMS_METADATA.GET_DDL", ex,
                    Map.of("objectSchema", owner, "objectName", tableName, "objectType", "TABLE"),
                    com.relationdetector.oracle.OracleDatabaseAdaptor.PERMISSION_DENIED_VENDOR_CODES));
        }
    }

    private boolean inScope(ScanScope scope, String tableName) {
        String normalized = normalize(tableName);
        boolean included = scope.includeTables().isEmpty()
                || scope.includeTables().stream().map(this::normalize).anyMatch(normalized::equals);
        boolean excluded = scope.excludeTables().stream().map(this::normalize).anyMatch(normalized::equals);
        return included && !excluded;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }
}
