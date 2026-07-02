package com.relationdetector.mysql.ddl;


import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.Collectors.DatabaseDdlCollector;
import com.relationdetector.contracts.spi.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.contracts.spi.Collectors.MetadataCollector;
import com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector;
import com.relationdetector.contracts.spi.Collectors.SqlLogExtractor;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.parse.DatabaseDdlDefinition;
import com.relationdetector.contracts.parse.DatabaseObjectDefinition;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataConstraintFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.metadata.MetadataTableFact;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseObjectType;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.LogFormatHint;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.core.diagnostics.DiagnosticWarnings;
import com.relationdetector.core.log.PlainSqlLogExtractor;
import com.relationdetector.core.relation.TokenEventSqlRelationParser;
import com.relationdetector.mysql.tokenevent.MySqlTokenEventStructuredDdlParser;
import com.relationdetector.mysql.tokenevent.MySqlTokenEventStructuredSqlParser;

/** MySQL 5.7/8.0 adaptor implementing the Phase 4 design. */

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
        List<DatabaseDdlDefinition> definitions = new ArrayList<>();
        for (String tableName : tableNames(connection, scope, warnings)) {
            collectShowCreate(connection, scope.schema(), tableName, definitions, warnings);
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
            ps.setString(1, scope.schema());
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
                    "MYSQL_DATABASE_DDL_TABLES_FAILED", ex.getMessage(), "information_schema.TABLES", 0));
        }
        return tableNames;
    }

    private void collectShowCreate(
            Connection connection,
            String schema,
            String tableName,
            List<DatabaseDdlDefinition> definitions,
            Consumer<WarningMessage> warnings
    ) {
        String sql = "SHOW CREATE TABLE " + quote(schema) + "." + quote(tableName);
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            if (rs.next()) {
                definitions.add(new DatabaseDdlDefinition(schema, tableName, rs.getString(2), "SHOW CREATE TABLE"));
            }
        } catch (Exception ex) {
            warnings.accept(WarningMessage.warn(WarningType.PERMISSION_WARNING,
                    "MYSQL_SHOW_CREATE_TABLE_FAILED", ex.getMessage(), "SHOW CREATE TABLE", 0,
                    java.util.Map.of("objectSchema", schema,
                            "objectName", tableName,
                            "objectType", "TABLE",
                            "rawStatement", sql,
                            "exceptionClass", ex.getClass().getSimpleName())));
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
