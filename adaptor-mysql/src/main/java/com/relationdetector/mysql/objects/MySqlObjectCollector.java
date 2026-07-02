package com.relationdetector.mysql.objects;


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
        List<DatabaseObjectDefinition> definitions = new ArrayList<>();
        collectRoutines(connection, scope, definitions, warnings);
        collectViews(connection, scope, definitions, warnings);
        collectTriggers(connection, scope, definitions, warnings);
        collectEvents(connection, scope, definitions, warnings);
        return definitions;
    }

    private void collectRoutines(
            Connection connection,
            ScanScope scope,
            List<DatabaseObjectDefinition> definitions,
            Consumer<WarningMessage> warnings
    ) {
        String sql = """
                SELECT ROUTINE_SCHEMA, ROUTINE_NAME, ROUTINE_TYPE, ROUTINE_DEFINITION
                FROM information_schema.ROUTINES
                WHERE ROUTINE_SCHEMA = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.schema());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DatabaseObjectType type = "FUNCTION".equalsIgnoreCase(rs.getString("ROUTINE_TYPE"))
                            ? DatabaseObjectType.FUNCTION
                            : DatabaseObjectType.PROCEDURE;
                    definitions.add(new DatabaseObjectDefinition(type, rs.getString("ROUTINE_SCHEMA"),
                            rs.getString("ROUTINE_NAME"), rs.getString("ROUTINE_DEFINITION"), "information_schema.ROUTINES"));
                }
            }
        } catch (Exception ex) {
            // Missing routine privileges are common in production; keep scanning
            // but expose the loss of procedure/function SQL to operators.
            warnings.accept(DiagnosticWarnings.objectCollectFailed(
                    "MYSQL_ROUTINE_COLLECT_FAILED", "information_schema.ROUTINES", ex));
        }
    }

    private void collectViews(
            Connection connection,
            ScanScope scope,
            List<DatabaseObjectDefinition> definitions,
            Consumer<WarningMessage> warnings
    ) {
        String sql = "SELECT TABLE_SCHEMA, TABLE_NAME, VIEW_DEFINITION FROM information_schema.VIEWS WHERE TABLE_SCHEMA = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.schema());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    definitions.add(new DatabaseObjectDefinition(DatabaseObjectType.VIEW, rs.getString("TABLE_SCHEMA"),
                            rs.getString("TABLE_NAME"), rs.getString("VIEW_DEFINITION"), "information_schema.VIEWS"));
                }
            }
        } catch (Exception ex) {
            warnings.accept(DiagnosticWarnings.objectCollectFailed(
                    "MYSQL_VIEW_COLLECT_FAILED", "information_schema.VIEWS", ex));
        }
    }

    private void collectTriggers(
            Connection connection,
            ScanScope scope,
            List<DatabaseObjectDefinition> definitions,
            Consumer<WarningMessage> warnings
    ) {
        String sql = "SELECT TRIGGER_SCHEMA, TRIGGER_NAME, ACTION_STATEMENT FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.schema());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    definitions.add(new DatabaseObjectDefinition(DatabaseObjectType.TRIGGER, rs.getString("TRIGGER_SCHEMA"),
                            rs.getString("TRIGGER_NAME"), rs.getString("ACTION_STATEMENT"), "information_schema.TRIGGERS"));
                }
            }
        } catch (Exception ex) {
            warnings.accept(DiagnosticWarnings.objectCollectFailed(
                    "MYSQL_TRIGGER_COLLECT_FAILED", "information_schema.TRIGGERS", ex));
        }
    }

    private void collectEvents(
            Connection connection,
            ScanScope scope,
            List<DatabaseObjectDefinition> definitions,
            Consumer<WarningMessage> warnings
    ) {
        /*
         * MySQL scheduled events are persisted database objects whose body can
         * contain relationship-bearing SQL:
         *
         *   CREATE EVENT refresh_order_rollups
         *   DO INSERT INTO rollups SELECT ... FROM orders o JOIN users u ...
         *
         * information_schema.EVENTS exposes the body in EVENT_DEFINITION. We
         * classify it as EVENT so ScanEngine maps it to StatementSourceType.EVENT,
         * which then scores joins like procedure/function evidence.
         */
        String sql = "SELECT EVENT_SCHEMA, EVENT_NAME, EVENT_DEFINITION FROM information_schema.EVENTS WHERE EVENT_SCHEMA = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.schema());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    definitions.add(new DatabaseObjectDefinition(DatabaseObjectType.EVENT, rs.getString("EVENT_SCHEMA"),
                            rs.getString("EVENT_NAME"), rs.getString("EVENT_DEFINITION"), "information_schema.EVENTS"));
                }
            }
        } catch (Exception ex) {
            warnings.accept(DiagnosticWarnings.objectCollectFailed(
                    "MYSQL_EVENT_COLLECT_FAILED", "information_schema.EVENTS", ex));
        }
    }
}
