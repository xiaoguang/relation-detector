package com.relationdetector.sqlserver.metadata;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataConstraintFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.metadata.MetadataTableFact;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.contracts.spi.Collectors.MetadataCollector;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.sqlserver.SqlServerCatalogResolver;
import com.relationdetector.core.diagnostics.LiveDiagnosticSanitizer;

/**
 *
 * Reads SQL Server sys catalog facts with partial success for each family.
 */
public final class SqlServerMetadataCollector implements MetadataCollector {
    @Override
    public MetadataSnapshot collect(Connection connection, ScanScope scope) {
        MetadataSnapshot snapshot = new MetadataSnapshot();
        String catalog = SqlServerCatalogResolver.resolve(connection, scope);
        String schema = scope.schema() == null || scope.schema().isBlank() ? "dbo" : scope.schema();
        collectTablesAndColumns(connection, scope, catalog, schema, snapshot);
        collectConstraints(connection, scope, catalog, schema, snapshot);
        collectIndexes(connection, scope, catalog, schema, snapshot);
        return snapshot;
    }

    private void collectTablesAndColumns(Connection connection, ScanScope scope, String catalog, String schema,
            MetadataSnapshot snapshot) {
        String sql = """
                SELECT s.name AS schema_name, t.name AS table_name, c.name AS column_name,
                       ty.name AS data_type, c.max_length, c.is_nullable, c.default_object_id, c.column_id
                FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id
                JOIN sys.columns c ON c.object_id=t.object_id
                JOIN sys.types ty ON ty.user_type_id=c.user_type_id
                WHERE s.name=? ORDER BY t.name, c.column_id
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            String lastTable = null;
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String table = rs.getString("table_name");
                    if (!inScope(scope, table)) continue;
                    String rowSchema = rs.getString("schema_name");
                    if (!table.equals(lastTable)) {
                        snapshot.tableFacts().add(new MetadataTableFact(catalog, rowSchema, table, "TABLE", null, null));
                        snapshot.tables().add(table(catalog, rowSchema, table));
                        lastTable = table;
                    }
                    MetadataColumnFact fact = new MetadataColumnFact(catalog, rowSchema, table,
                            rs.getString("column_name"), rs.getString("data_type"),
                            rs.getString("data_type") + "(" + rs.getInt("max_length") + ")",
                            rs.getBoolean("is_nullable"), null, "", null, rs.getInt("column_id"));
                    snapshot.columnFacts().add(fact);
                    snapshot.columns().add(new ColumnRef(table(catalog, rowSchema, table), fact.columnName(),
                            fact.columnName(), fact.dataType(), fact.nullable()));
                }
            }
        } catch (Exception ex) {
            warn(snapshot, "SQLSERVER_METADATA_COLUMNS_FAILED", ex, "sys.tables/sys.columns");
        }
    }

    private void collectConstraints(Connection connection, ScanScope scope, String catalog, String schema,
            MetadataSnapshot snapshot) {
        collectKeyConstraints(connection, scope, catalog, schema, snapshot);
        collectForeignKeys(connection, scope, catalog, schema, snapshot);
    }

    private void collectKeyConstraints(Connection connection, ScanScope scope, String catalog, String schema,
            MetadataSnapshot snapshot) {
        String sql = """
                SELECT s.name AS schema_name, t.name AS table_name, kc.name AS constraint_name,
                       kc.type AS constraint_type, c.name AS column_name, ic.key_ordinal
                FROM sys.key_constraints kc JOIN sys.tables t ON t.object_id=kc.parent_object_id
                JOIN sys.schemas s ON s.schema_id=t.schema_id
                JOIN sys.index_columns ic ON ic.object_id=t.object_id AND ic.index_id=kc.unique_index_id
                JOIN sys.columns c ON c.object_id=t.object_id AND c.column_id=ic.column_id
                WHERE s.name=? ORDER BY t.name, kc.name, ic.key_ordinal
                """;
        Map<String, KeyRows> groups = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String table = rs.getString("table_name");
                    if (!inScope(scope, table)) continue;
                    String key = rs.getString("schema_name") + "|" + table + "|" + rs.getString("constraint_name");
                    groups.computeIfAbsent(key, ignored -> new KeyRows(rsString(rs, "schema_name"), table,
                            rsString(rs, "constraint_name"), rsString(rs, "constraint_type")))
                            .columns.add(rsString(rs, "column_name"));
                }
            }
            for (KeyRows rows : groups.values()) snapshot.constraintFacts().add(new MetadataConstraintFact(
                    catalog, rows.schema, rows.table, rows.name, "PK".equals(rows.type) ? "PRIMARY KEY" : "UNIQUE",
                    rows.columns, null, null, null, List.of(), null, null));
        } catch (Exception ex) {
            warn(snapshot, "SQLSERVER_METADATA_KEYS_FAILED", ex, "sys.key_constraints");
        }
    }

    private void collectForeignKeys(Connection connection, ScanScope scope, String catalog, String schema,
            MetadataSnapshot snapshot) {
        String sql = """
                SELECT cs.name AS source_schema, ct.name AS source_table, cc.name AS source_column,
                       ps.name AS target_schema, pt.name AS target_table, pc.name AS target_column,
                       fk.name AS constraint_name, fkc.constraint_column_id,
                       fk.update_referential_action_desc, fk.delete_referential_action_desc
                FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id
                JOIN sys.tables ct ON ct.object_id=fk.parent_object_id JOIN sys.schemas cs ON cs.schema_id=ct.schema_id
                JOIN sys.columns cc ON cc.object_id=ct.object_id AND cc.column_id=fkc.parent_column_id
                JOIN sys.tables pt ON pt.object_id=fk.referenced_object_id JOIN sys.schemas ps ON ps.schema_id=pt.schema_id
                JOIN sys.columns pc ON pc.object_id=pt.object_id AND pc.column_id=fkc.referenced_column_id
                WHERE cs.name=? ORDER BY ct.name, fk.name, fkc.constraint_column_id
                """;
        Map<String, ForeignKeyRows> groups = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String sourceTable = rs.getString("source_table");
                    if (!inScope(scope, sourceTable)) continue;
                    String key = rs.getString("source_schema") + "|" + sourceTable + "|" + rs.getString("constraint_name");
                    groups.computeIfAbsent(key, ignored -> new ForeignKeyRows(rsString(rs, "source_schema"), sourceTable,
                            rsString(rs, "constraint_name"), rsString(rs, "target_schema"),
                            rsString(rs, "target_table"), rsString(rs, "update_referential_action_desc"),
                            rsString(rs, "delete_referential_action_desc")))
                            .add(rsString(rs, "source_column"), rsString(rs, "target_column"));
                }
            }
            for (ForeignKeyRows rows : groups.values()) addForeignKey(snapshot, catalog, rows);
        } catch (Exception ex) {
            warn(snapshot, "SQLSERVER_METADATA_FK_FAILED", ex, "sys.foreign_keys");
        }
    }

    private void addForeignKey(MetadataSnapshot snapshot, String catalog, ForeignKeyRows rows) {
        snapshot.constraintFacts().add(new MetadataConstraintFact(catalog, rows.sourceSchema, rows.sourceTable,
                rows.name, "FOREIGN KEY", rows.sourceColumns, catalog, rows.targetSchema, rows.targetTable,
                rows.targetColumns, rows.updateRule, rows.deleteRule));
        for (int i = 0; i < Math.min(rows.sourceColumns.size(), rows.targetColumns.size()); i++) {
            RelationshipCandidate candidate = new RelationshipCandidate(
                    Endpoint.column(ColumnRef.of(table(catalog, rows.sourceSchema, rows.sourceTable), rows.sourceColumns.get(i))),
                    Endpoint.column(ColumnRef.of(table(catalog, rows.targetSchema, rows.targetTable), rows.targetColumns.get(i))),
                    RelationType.FK_LIKE, RelationSubType.DECLARED_FK);
            candidate.evidence().add(Evidence.of(EvidenceType.METADATA_FOREIGN_KEY,
                    DefaultEvidenceScores.METADATA_FOREIGN_KEY, EvidenceSourceType.METADATA,
                    "sys.foreign_keys", rows.name));
            snapshot.relationships().add(candidate);
        }
    }

    private void collectIndexes(Connection connection, ScanScope scope, String catalog, String schema,
            MetadataSnapshot snapshot) {
        String sql = """
                SELECT s.name AS schema_name, t.name AS table_name, i.name AS index_name,
                       i.is_unique, i.is_primary_key, i.type_desc, i.is_disabled,
                       c.name AS column_name, ic.key_ordinal, ic.is_included_column
                FROM sys.indexes i JOIN sys.tables t ON t.object_id=i.object_id
                JOIN sys.schemas s ON s.schema_id=t.schema_id
                JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id
                JOIN sys.columns c ON c.object_id=t.object_id AND c.column_id=ic.column_id
                WHERE s.name=? AND i.name IS NOT NULL ORDER BY t.name, i.name, ic.key_ordinal, ic.index_column_id
                """;
        Map<String, IndexRows> groups = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String table = rs.getString("table_name");
                    if (!inScope(scope, table) || rs.getBoolean("is_included_column")) continue;
                    String key = rs.getString("schema_name") + "|" + table + "|" + rs.getString("index_name");
                    groups.computeIfAbsent(key, ignored -> new IndexRows(rsString(rs, "schema_name"), table,
                            rsString(rs, "index_name"), rsBoolean(rs, "is_unique"),
                            rsBoolean(rs, "is_primary_key"), rsString(rs, "type_desc"),
                            !rsBoolean(rs, "is_disabled")))
                            .add(rsString(rs, "column_name"), rs.getInt("key_ordinal"));
                }
            }
            for (IndexRows rows : groups.values()) snapshot.indexFacts().add(new MetadataIndexFact(catalog,
                    rows.schema, rows.table, rows.name, rows.unique, rows.primary, rows.type, rows.visible,
                    rows.columns, List.of(), List.of(), rows.positions));
        } catch (Exception ex) {
            warn(snapshot, "SQLSERVER_METADATA_INDEXES_FAILED", ex, "sys.indexes");
        }
    }

    private TableId table(String catalog, String schema, String name) {
        return new TableId(catalog, schema, name, schema + "." + name);
    }

    private boolean inScope(ScanScope scope, String table) {
        String key = table == null ? "" : table.toLowerCase(Locale.ROOT);
        boolean included = scope.includeTables().isEmpty()
                || scope.includeTables().stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(key::equals);
        return included && scope.excludeTables().stream().map(value -> value.toLowerCase(Locale.ROOT)).noneMatch(key::equals);
    }

    private void warn(MetadataSnapshot snapshot, String code, Exception ex, String source) {
        snapshot.warnings().add(LiveDiagnosticSanitizer.jdbcWarning(
                code, LiveDiagnosticSanitizer.Operation.METADATA, source, ex, Map.of(),
                com.relationdetector.sqlserver.SqlServerDatabaseAdaptor.PERMISSION_DENIED_VENDOR_CODES));
    }

    private String rsString(ResultSet rs, String name) { try { return rs.getString(name); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private boolean rsBoolean(ResultSet rs, String name) { try { return rs.getBoolean(name); } catch (Exception ex) { throw new IllegalStateException(ex); } }

    private static final class KeyRows {
        final String schema, table, name, type; final List<String> columns = new ArrayList<>();
        KeyRows(String schema, String table, String name, String type) { this.schema=schema; this.table=table; this.name=name; this.type=type; }
    }
    private static final class ForeignKeyRows {
        final String sourceSchema, sourceTable, name, targetSchema, targetTable, updateRule, deleteRule;
        final List<String> sourceColumns=new ArrayList<>(), targetColumns=new ArrayList<>();
        ForeignKeyRows(String sourceSchema, String sourceTable, String name, String targetSchema, String targetTable,
                String updateRule, String deleteRule) {
            this.sourceSchema=sourceSchema; this.sourceTable=sourceTable; this.name=name;
            this.targetSchema=targetSchema; this.targetTable=targetTable; this.updateRule=updateRule; this.deleteRule=deleteRule;
        }
        void add(String source, String target) { sourceColumns.add(source); targetColumns.add(target); }
    }
    private static final class IndexRows {
        final String schema, table, name, type; final boolean unique, primary, visible;
        final List<String> columns=new ArrayList<>(); final List<Integer> positions=new ArrayList<>();
        IndexRows(String schema, String table, String name, boolean unique, boolean primary, String type, boolean visible) {
            this.schema=schema; this.table=table; this.name=name; this.unique=unique; this.primary=primary; this.type=type; this.visible=visible;
        }
        void add(String column, int position) { columns.add(column); positions.add(position); }
    }
}
