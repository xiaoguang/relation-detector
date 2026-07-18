package com.relationdetector.mysql.metadata;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.metadata.MetadataConstraintFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.contracts.spi.ScanScope;

/**
 * CN: 从 MySQL constraint catalog 读取声明 FK 关系及完整 PK/UNIQUE/FK 列组；输入是规范 scope，输出 column-pair relationship 和 ordinal-safe constraint facts，禁止把组合唯一键成员当作单列唯一。
 * EN: Reads declared FK relationships and complete PK, UNIQUE, and FK column groups from MySQL catalogs; composite members never become standalone unique evidence.
 */
final class MySqlConstraintMetadataReader {
    void readRelationships(Connection connection, ScanScope scope, MetadataSnapshot snapshot) {
        String sql = """
                SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME,
                       REFERENCED_TABLE_SCHEMA, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME,
                       CONSTRAINT_NAME
                FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = ?
                  AND REFERENCED_TABLE_NAME IS NOT NULL
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.catalog());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TableId sourceTable = MySqlMetadataReaderSupport.table(
                            rs.getString("TABLE_SCHEMA"), rs.getString("TABLE_NAME"));
                    TableId targetTable = MySqlMetadataReaderSupport.table(
                            rs.getString("REFERENCED_TABLE_SCHEMA"), rs.getString("REFERENCED_TABLE_NAME"));
                    if (!MySqlMetadataReaderSupport.inScope(scope, sourceTable.tableName())
                            || !MySqlMetadataReaderSupport.inScope(scope, targetTable.tableName())) {
                        continue;
                    }
                    RelationshipCandidate candidate = new RelationshipCandidate(
                            Endpoint.column(ColumnRef.of(sourceTable, rs.getString("COLUMN_NAME"))),
                            Endpoint.column(ColumnRef.of(targetTable, rs.getString("REFERENCED_COLUMN_NAME"))),
                            RelationType.FK_LIKE,
                            RelationSubType.DECLARED_FK);
                    candidate.evidence().add(Evidence.of(EvidenceType.METADATA_FOREIGN_KEY,
                            DefaultEvidenceScores.METADATA_FOREIGN_KEY, EvidenceSourceType.METADATA,
                            "information_schema.KEY_COLUMN_USAGE", rs.getString("CONSTRAINT_NAME")));
                    snapshot.relationships().add(candidate);
                }
            }
        } catch (Exception ex) {
            MySqlMetadataReaderSupport.warn(snapshot, "MYSQL_METADATA_FK_FAILED", ex,
                    "information_schema.KEY_COLUMN_USAGE");
        }
    }

    void readFacts(Connection connection, ScanScope scope, MetadataSnapshot snapshot) {
        String constraintsSql = """
                SELECT CONSTRAINT_SCHEMA, TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE
                FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = ?
                """;
        String keyUsageSql = """
                SELECT CONSTRAINT_SCHEMA, TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, CONSTRAINT_NAME,
                       ORDINAL_POSITION, POSITION_IN_UNIQUE_CONSTRAINT,
                       REFERENCED_TABLE_SCHEMA, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
                FROM information_schema.KEY_COLUMN_USAGE
                WHERE CONSTRAINT_SCHEMA = ?
                ORDER BY CONSTRAINT_SCHEMA, TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION
                """;
        String refsSql = """
                SELECT CONSTRAINT_SCHEMA, CONSTRAINT_NAME, UNIQUE_CONSTRAINT_SCHEMA,
                       REFERENCED_TABLE_NAME, UPDATE_RULE, DELETE_RULE
                FROM information_schema.REFERENTIAL_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = ?
                """;
        try {
            Map<String, ConstraintBuilder> builders = new LinkedHashMap<>();
            readConstraintHeaders(connection, scope, constraintsSql, builders);
            readConstraintColumns(connection, scope, keyUsageSql, builders);
            readReferentialRules(connection, scope, refsSql, builders);
            builders.values().forEach(builder -> snapshot.constraintFacts().add(builder.toFact()));
        } catch (Exception ex) {
            MySqlMetadataReaderSupport.warn(snapshot, "MYSQL_METADATA_CONSTRAINTS_FAILED", ex,
                    "information_schema.TABLE_CONSTRAINTS");
        }
    }

    private void readConstraintHeaders(Connection connection, ScanScope scope, String sql,
            Map<String, ConstraintBuilder> builders) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.catalog());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (!MySqlMetadataReaderSupport.inScope(scope, tableName)) continue;
                    ConstraintBuilder builder = builder(builders, rs.getString("CONSTRAINT_SCHEMA"), tableName,
                            rs.getString("CONSTRAINT_NAME"));
                    builder.type = rs.getString("CONSTRAINT_TYPE");
                }
            }
        }
    }

    private void readConstraintColumns(Connection connection, ScanScope scope, String sql,
            Map<String, ConstraintBuilder> builders) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.catalog());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (!MySqlMetadataReaderSupport.inScope(scope, tableName)) continue;
                    ConstraintBuilder builder = builder(builders, rs.getString("CONSTRAINT_SCHEMA"), tableName,
                            rs.getString("CONSTRAINT_NAME"));
                    builder.columns.add(rs.getString("COLUMN_NAME"));
                    if (rs.getString("REFERENCED_TABLE_NAME") != null) {
                        builder.referencedCatalog = rs.getString("REFERENCED_TABLE_SCHEMA");
                        builder.referencedTable = rs.getString("REFERENCED_TABLE_NAME");
                        builder.referencedColumns.add(rs.getString("REFERENCED_COLUMN_NAME"));
                    }
                }
            }
        }
    }

    private void readReferentialRules(Connection connection, ScanScope scope, String sql,
            Map<String, ConstraintBuilder> builders) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.catalog());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    for (ConstraintBuilder builder : builders.values()) {
                        if (MySqlMetadataReaderSupport.equalsIgnoreCase(builder.catalog,
                                rs.getString("CONSTRAINT_SCHEMA"))
                                && MySqlMetadataReaderSupport.equalsIgnoreCase(builder.name,
                                        rs.getString("CONSTRAINT_NAME"))) {
                            builder.updateRule = rs.getString("UPDATE_RULE");
                            builder.deleteRule = rs.getString("DELETE_RULE");
                        }
                    }
                }
            }
        }
    }

    private ConstraintBuilder builder(Map<String, ConstraintBuilder> builders, String catalog, String table,
            String name) {
        return builders.computeIfAbsent(catalog + "|" + table + "|" + name,
                ignored -> new ConstraintBuilder(catalog, table, name));
    }

    private static final class ConstraintBuilder {
        private final String catalog;
        private final String table;
        private final String name;
        private String type;
        private final List<String> columns = new ArrayList<>();
        private String referencedCatalog;
        private String referencedTable;
        private final List<String> referencedColumns = new ArrayList<>();
        private String updateRule;
        private String deleteRule;

        private ConstraintBuilder(String catalog, String table, String name) {
            this.catalog = catalog;
            this.table = table;
            this.name = name;
        }

        private MetadataConstraintFact toFact() {
            return new MetadataConstraintFact(catalog, null, table, name, type, columns,
                    referencedCatalog, null, referencedTable, referencedColumns, updateRule, deleteRule);
        }
    }
}
