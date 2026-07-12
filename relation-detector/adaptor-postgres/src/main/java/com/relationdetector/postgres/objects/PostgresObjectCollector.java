package com.relationdetector.postgres.objects;


import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.contracts.spi.Collectors.MetadataCollector;
import com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector;
import com.relationdetector.contracts.spi.Collectors.SqlLogExtractor;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.parse.DatabaseObjectDefinition;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
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
import com.relationdetector.core.relation.StructuredSqlRelationshipParser;
import com.relationdetector.postgres.tokenevent.PostgresTokenEventStructuredDdlParser;
import com.relationdetector.postgres.tokenevent.PostgresTokenEventStructuredSqlParser;

/** PostgreSQL 12+ adaptor implementing the Phase 5 design. */

public final class PostgresObjectCollector implements ObjectDefinitionCollector {
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
        collectFunctions(connection, scope, definitions, warnings);
        collectViews(connection, scope, definitions, warnings);
        collectMaterializedViews(connection, scope, definitions, warnings);
        collectRules(connection, scope, definitions, warnings);
        return definitions;
    }

    private void collectFunctions(
            Connection connection,
            ScanScope scope,
            List<DatabaseObjectDefinition> definitions,
            Consumer<WarningMessage> warnings
    ) {
        String sql = """
                SELECT n.nspname, p.proname, p.prokind, pg_get_functiondef(p.oid) AS definition
                FROM pg_proc p
                JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE n.nspname = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.schema() == null ? "public" : scope.schema());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DatabaseObjectType type = "p".equals(rs.getString("prokind"))
                            ? DatabaseObjectType.PROCEDURE
                            : DatabaseObjectType.FUNCTION;
                    definitions.add(new DatabaseObjectDefinition(type, rs.getString("nspname"),
                            rs.getString("proname"), rs.getString("definition"), "pg_proc"));
                }
            }
        } catch (Exception ex) {
            warnings.accept(DiagnosticWarnings.objectCollectFailed(
                    "POSTGRES_FUNCTION_COLLECT_FAILED", "pg_proc", ex));
        }
    }

    private void collectViews(
            Connection connection,
            ScanScope scope,
            List<DatabaseObjectDefinition> definitions,
            Consumer<WarningMessage> warnings
    ) {
        String sql = """
                SELECT schemaname, viewname, definition
                FROM pg_views
                WHERE schemaname = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.schema() == null ? "public" : scope.schema());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    definitions.add(new DatabaseObjectDefinition(DatabaseObjectType.VIEW, rs.getString("schemaname"),
                            rs.getString("viewname"), rs.getString("definition"), "pg_views"));
                }
            }
        } catch (Exception ex) {
            warnings.accept(DiagnosticWarnings.objectCollectFailed(
                    "POSTGRES_VIEW_COLLECT_FAILED", "pg_views", ex));
        }
    }

    private void collectMaterializedViews(
            Connection connection,
            ScanScope scope,
            List<DatabaseObjectDefinition> definitions,
            Consumer<WarningMessage> warnings
    ) {
        /*
         * PostgreSQL materialized views persist a SELECT definition in pg_matviews.
         * They behave like views for relationship discovery: the definition can
         * contain joins, CTEs, and subqueries, but the object stores data. Keeping
         * the type distinct lets output diagnostics say "materialized view" while
         * scoring joins like VIEW_JOIN.
         */
        String sql = """
                SELECT schemaname, matviewname, definition
                FROM pg_matviews
                WHERE schemaname = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.schema() == null ? "public" : scope.schema());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    definitions.add(new DatabaseObjectDefinition(DatabaseObjectType.MATERIALIZED_VIEW,
                            rs.getString("schemaname"), rs.getString("matviewname"),
                            rs.getString("definition"), "pg_matviews"));
                }
            }
        } catch (Exception ex) {
            warnings.accept(DiagnosticWarnings.objectCollectFailed(
                    "POSTGRES_MATERIALIZED_VIEW_COLLECT_FAILED", "pg_matviews", ex));
        }
    }

    private void collectRules(
            Connection connection,
            ScanScope scope,
            List<DatabaseObjectDefinition> definitions,
            Consumer<WarningMessage> warnings
    ) {
        /*
         * PostgreSQL rules rewrite table operations and can contain SELECT/INSERT/
         * UPDATE/DELETE bodies:
         *
         *   CREATE RULE orders_insert_audit AS
         *   ON INSERT TO orders DO ALSO INSERT INTO audit ... SELECT ...
         *
         * The rule name is qualified with the table name to avoid collisions
         * inside one schema while keeping the original definition text intact.
         */
        String sql = """
                SELECT schemaname, tablename, rulename, definition
                FROM pg_rules
                WHERE schemaname = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.schema() == null ? "public" : scope.schema());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    definitions.add(new DatabaseObjectDefinition(DatabaseObjectType.RULE,
                            rs.getString("schemaname"), rs.getString("tablename") + "." + rs.getString("rulename"),
                            rs.getString("definition"), "pg_rules"));
                }
            }
        } catch (Exception ex) {
            warnings.accept(DiagnosticWarnings.objectCollectFailed(
                    "POSTGRES_RULE_COLLECT_FAILED", "pg_rules", ex));
        }
    }
}
