package com.relationdetector.oracle.metadata;

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

/** Reads Oracle catalog facts with partial success for each catalog family. */
public final class OracleMetadataCollector implements MetadataCollector {
    @Override
    public MetadataSnapshot collect(Connection connection, ScanScope scope) {
        MetadataSnapshot snapshot = new MetadataSnapshot();
        String owner = owner(connection, scope);
        collectTables(connection, scope, owner, snapshot);
        collectColumns(connection, scope, owner, snapshot);
        collectConstraints(connection, scope, owner, snapshot);
        collectIndexes(connection, scope, owner, snapshot);
        return snapshot;
    }

    private void collectTables(Connection connection, ScanScope scope, String owner, MetadataSnapshot snapshot) {
        String sql = "SELECT OWNER, TABLE_NAME FROM ALL_TABLES WHERE OWNER = ? ORDER BY TABLE_NAME";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String table = rs.getString("TABLE_NAME");
                    if (!inScope(scope, table)) continue;
                    TableId id = TableId.of(rs.getString("OWNER"), table);
                    snapshot.tables().add(id);
                    snapshot.tableFacts().add(new MetadataTableFact(null, id.schema(), table, "TABLE", null, null));
                }
            }
        } catch (Exception ex) {
            warn(snapshot, "ORACLE_METADATA_TABLES_FAILED", ex, "ALL_TABLES");
        }
    }

    private void collectColumns(Connection connection, ScanScope scope, String owner, MetadataSnapshot snapshot) {
        String sql = """
                SELECT OWNER, TABLE_NAME, COLUMN_NAME, DATA_TYPE, DATA_LENGTH, NULLABLE,
                       DATA_DEFAULT, VIRTUAL_COLUMN, COLUMN_ID
                FROM ALL_TAB_COLUMNS WHERE OWNER = ? ORDER BY TABLE_NAME, COLUMN_ID
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String table = rs.getString("TABLE_NAME");
                    if (!inScope(scope, table)) continue;
                    String dataType = rs.getString("DATA_TYPE");
                    String columnType = dataType + "(" + rs.getInt("DATA_LENGTH") + ")";
                    MetadataColumnFact fact = new MetadataColumnFact(null, rs.getString("OWNER"), table,
                            rs.getString("COLUMN_NAME"), dataType, columnType,
                            "Y".equalsIgnoreCase(rs.getString("NULLABLE")), rs.getString("DATA_DEFAULT"),
                            "YES".equalsIgnoreCase(rs.getString("VIRTUAL_COLUMN")) ? "VIRTUAL" : "",
                            null, rs.getInt("COLUMN_ID"));
                    snapshot.columnFacts().add(fact);
                    snapshot.columns().add(new ColumnRef(TableId.of(fact.schema(), table), fact.columnName(),
                            fact.columnName(), fact.dataType(), fact.nullable()));
                }
            }
        } catch (Exception ex) {
            warn(snapshot, "ORACLE_METADATA_COLUMNS_FAILED", ex, "ALL_TAB_COLUMNS");
        }
    }

    private void collectConstraints(Connection connection, ScanScope scope, String owner, MetadataSnapshot snapshot) {
        String sql = """
                SELECT c.OWNER, c.TABLE_NAME, c.CONSTRAINT_NAME, c.CONSTRAINT_TYPE,
                       cc.COLUMN_NAME, cc.POSITION,
                       rc.OWNER AS REFERENCED_OWNER, rc.TABLE_NAME AS REFERENCED_TABLE,
                       rcc.COLUMN_NAME AS REFERENCED_COLUMN,
                       c.DELETE_RULE
                FROM ALL_CONSTRAINTS c
                JOIN ALL_CONS_COLUMNS cc ON cc.OWNER=c.OWNER AND cc.CONSTRAINT_NAME=c.CONSTRAINT_NAME
                LEFT JOIN ALL_CONSTRAINTS rc ON rc.OWNER=c.R_OWNER AND rc.CONSTRAINT_NAME=c.R_CONSTRAINT_NAME
                LEFT JOIN ALL_CONS_COLUMNS rcc ON rcc.OWNER=rc.OWNER AND rcc.CONSTRAINT_NAME=rc.CONSTRAINT_NAME
                    AND rcc.POSITION=cc.POSITION
                WHERE c.OWNER=? AND c.CONSTRAINT_TYPE IN ('P','U','R')
                ORDER BY c.TABLE_NAME, c.CONSTRAINT_NAME, cc.POSITION
                """;
        Map<String, ConstraintRows> groups = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String table = rs.getString("TABLE_NAME");
                    if (!inScope(scope, table)) continue;
                    String key = rs.getString("OWNER") + "|" + table + "|" + rs.getString("CONSTRAINT_NAME");
                    groups.computeIfAbsent(key, ignored -> new ConstraintRows(
                            rsString(rs, "OWNER"), table, rsString(rs, "CONSTRAINT_NAME"),
                            rsString(rs, "CONSTRAINT_TYPE"), rsString(rs, "REFERENCED_OWNER"),
                            rsString(rs, "REFERENCED_TABLE"), rsString(rs, "DELETE_RULE")))
                            .add(rsString(rs, "COLUMN_NAME"), rsString(rs, "REFERENCED_COLUMN"));
                }
            }
            for (ConstraintRows rows : groups.values()) addConstraint(snapshot, rows);
        } catch (Exception ex) {
            warn(snapshot, "ORACLE_METADATA_CONSTRAINTS_FAILED", ex, "ALL_CONSTRAINTS");
        }
    }

    private void addConstraint(MetadataSnapshot snapshot, ConstraintRows rows) {
        String type = switch (rows.type) { case "P" -> "PRIMARY KEY"; case "U" -> "UNIQUE"; default -> "FOREIGN KEY"; };
        snapshot.constraintFacts().add(new MetadataConstraintFact(null, rows.owner, rows.table, rows.name, type,
                rows.columns, null, rows.referencedOwner, rows.referencedTable, rows.referencedColumns,
                null, rows.deleteRule));
        if (!"R".equals(rows.type)) return;
        int count = Math.min(rows.columns.size(), rows.referencedColumns.size());
        for (int i = 0; i < count; i++) {
            RelationshipCandidate candidate = new RelationshipCandidate(
                    Endpoint.column(ColumnRef.of(TableId.of(rows.owner, rows.table), rows.columns.get(i))),
                    Endpoint.column(ColumnRef.of(TableId.of(rows.referencedOwner, rows.referencedTable), rows.referencedColumns.get(i))),
                    RelationType.FK_LIKE, RelationSubType.DECLARED_FK);
            candidate.evidence().add(Evidence.of(EvidenceType.METADATA_FOREIGN_KEY,
                    DefaultEvidenceScores.METADATA_FOREIGN_KEY, EvidenceSourceType.METADATA,
                    "ALL_CONSTRAINTS", rows.name));
            snapshot.relationships().add(candidate);
        }
    }

    private void collectIndexes(Connection connection, ScanScope scope, String owner, MetadataSnapshot snapshot) {
        String sql = """
                SELECT i.OWNER, i.TABLE_NAME, i.INDEX_NAME, i.UNIQUENESS, i.INDEX_TYPE,
                       ic.COLUMN_NAME, ic.COLUMN_POSITION
                FROM ALL_INDEXES i JOIN ALL_IND_COLUMNS ic
                  ON ic.INDEX_OWNER=i.OWNER AND ic.INDEX_NAME=i.INDEX_NAME
                WHERE i.OWNER=? ORDER BY i.TABLE_NAME, i.INDEX_NAME, ic.COLUMN_POSITION
                """;
        Map<String, IndexRows> groups = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String table = rs.getString("TABLE_NAME");
                    if (!inScope(scope, table)) continue;
                    String key = rs.getString("OWNER") + "|" + table + "|" + rs.getString("INDEX_NAME");
                    groups.computeIfAbsent(key, ignored -> new IndexRows(rsString(rs, "OWNER"), table,
                            rsString(rs, "INDEX_NAME"), "UNIQUE".equalsIgnoreCase(rsString(rs, "UNIQUENESS")),
                            rsString(rs, "INDEX_TYPE")))
                            .add(rsString(rs, "COLUMN_NAME"), rs.getInt("COLUMN_POSITION"));
                }
            }
            for (IndexRows rows : groups.values()) snapshot.indexFacts().add(new MetadataIndexFact(null,
                    rows.owner, rows.table, rows.name, rows.unique, false, rows.type, true,
                    rows.columns, List.of(), List.of(), rows.positions));
        } catch (Exception ex) {
            warn(snapshot, "ORACLE_METADATA_INDEXES_FAILED", ex, "ALL_INDEXES");
        }
    }

    private String owner(Connection connection, ScanScope scope) {
        if (scope.schema() != null && !scope.schema().isBlank()) return scope.schema().toUpperCase(Locale.ROOT);
        try { if (connection.getSchema() != null) return connection.getSchema().toUpperCase(Locale.ROOT); }
        catch (Exception ignored) { }
        try { return connection.getMetaData().getUserName().toUpperCase(Locale.ROOT); }
        catch (Exception ignored) { return ""; }
    }

    private boolean inScope(ScanScope scope, String table) {
        String key = table == null ? "" : table.toLowerCase(Locale.ROOT);
        boolean included = scope.includeTables().isEmpty()
                || scope.includeTables().stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(key::equals);
        return included && scope.excludeTables().stream().map(value -> value.toLowerCase(Locale.ROOT)).noneMatch(key::equals);
    }

    private void warn(MetadataSnapshot snapshot, String code, Exception ex, String source) {
        snapshot.warnings().add(WarningMessage.warn(WarningType.PERMISSION_WARNING, code, ex.getMessage(), source, 0));
    }

    private String rsString(ResultSet rs, String name) {
        try { return rs.getString(name); } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private static final class ConstraintRows {
        final String owner, table, name, type, referencedOwner, referencedTable, deleteRule;
        final List<String> columns = new ArrayList<>(), referencedColumns = new ArrayList<>();
        ConstraintRows(String owner, String table, String name, String type, String referencedOwner,
                String referencedTable, String deleteRule) {
            this.owner=owner; this.table=table; this.name=name; this.type=type;
            this.referencedOwner=referencedOwner; this.referencedTable=referencedTable; this.deleteRule=deleteRule;
        }
        void add(String column, String referenced) { columns.add(column); if (referenced != null) referencedColumns.add(referenced); }
    }

    private static final class IndexRows {
        final String owner, table, name, type; final boolean unique;
        final List<String> columns = new ArrayList<>(); final List<Integer> positions = new ArrayList<>();
        IndexRows(String owner, String table, String name, boolean unique, String type) {
            this.owner=owner; this.table=table; this.name=name; this.unique=unique; this.type=type;
        }
        void add(String column, int position) { columns.add(column); positions.add(position); }
    }
}
