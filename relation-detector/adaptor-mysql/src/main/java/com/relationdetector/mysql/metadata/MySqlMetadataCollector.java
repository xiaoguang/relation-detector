package com.relationdetector.mysql.metadata;


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
import com.relationdetector.core.relation.TokenEventSqlRelationParser;
import com.relationdetector.mysql.tokenevent.MySqlTokenEventStructuredDdlParser;
import com.relationdetector.mysql.tokenevent.MySqlTokenEventStructuredSqlParser;

/** MySQL 5.7/8.0 adaptor implementing the Phase 4 design. */

public final class MySqlMetadataCollector implements MetadataCollector {
    @Override
    public MetadataSnapshot collect(Connection connection, ScanScope scope) {
        /*
         * MetadataSnapshot is deliberately raw and append-only here. The adaptor
         * should not try to merge duplicate relationships or lower/raise scores;
         * RelationshipMerger owns cross-source reconciliation after metadata,
         * DDL, object definitions, SQL logs, naming, and profiling have all had
         * a chance to contribute evidence.
         */
        MetadataSnapshot snapshot = new MetadataSnapshot();
        collectTables(connection, scope, snapshot);
        collectColumns(connection, scope, snapshot);
        collectForeignKeys(connection, scope, snapshot);
        collectConstraints(connection, scope, snapshot);
        collectIndexes(connection, scope, snapshot);
        return snapshot;
    }

    private void collectTables(Connection connection, ScanScope scope, MetadataSnapshot snapshot) {
        String sql = """
                SELECT TABLE_SCHEMA, TABLE_NAME, TABLE_TYPE, ENGINE, TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.schema());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (!inScope(scope, tableName)) {
                        continue;
                    }
                    snapshot.tableFacts().add(new MetadataTableFact(
                            rs.getString("TABLE_SCHEMA"),
                            tableName,
                            rs.getString("TABLE_TYPE"),
                            rs.getString("ENGINE"),
                            rs.getString("TABLE_COMMENT")));
                    snapshot.tables().add(TableId.of(rs.getString("TABLE_SCHEMA"), tableName));
                }
            }
        } catch (Exception ex) {
            snapshot.warnings().add(WarningMessage.warn(WarningType.PERMISSION_WARNING,
                    "MYSQL_METADATA_TABLES_FAILED", ex.getMessage(), "information_schema.TABLES", 0));
        }
    }

    private void collectColumns(Connection connection, ScanScope scope, MetadataSnapshot snapshot) {
        String sql = """
                SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, DATA_TYPE, COLUMN_TYPE,
                       IS_NULLABLE, COLUMN_DEFAULT, EXTRA, GENERATION_EXPRESSION, ORDINAL_POSITION
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = ?
                ORDER BY TABLE_SCHEMA, TABLE_NAME, ORDINAL_POSITION
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.schema());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (!inScope(scope, tableName)) {
                        continue;
                    }
                    boolean nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
                    MetadataColumnFact fact = new MetadataColumnFact(
                            rs.getString("TABLE_SCHEMA"),
                            tableName,
                            rs.getString("COLUMN_NAME"),
                            rs.getString("DATA_TYPE"),
                            rs.getString("COLUMN_TYPE"),
                            nullable,
                            stringOrNull(rs.getObject("COLUMN_DEFAULT")),
                            rs.getString("EXTRA"),
                            rs.getString("GENERATION_EXPRESSION"),
                            rs.getInt("ORDINAL_POSITION"));
                    snapshot.columnFacts().add(fact);
                    snapshot.columns().add(new ColumnRef(
                            TableId.of(fact.schema(), fact.tableName()),
                            fact.columnName(),
                            fact.columnName(),
                            fact.dataType(),
                            fact.nullable()));
                }
            }
        } catch (Exception ex) {
            snapshot.warnings().add(WarningMessage.warn(WarningType.PERMISSION_WARNING,
                    "MYSQL_METADATA_COLUMNS_FAILED", ex.getMessage(), "information_schema.COLUMNS", 0));
        }
    }

    /**
     * Reads explicit MySQL foreign keys from information_schema.
     *
     * <p>Why {@code KEY_COLUMN_USAGE}: MySQL records one row per constrained
     * column in {@code information_schema.KEY_COLUMN_USAGE}. Rows with a non-null
     * {@code REFERENCED_TABLE_NAME} are FK rows. For a composite FK, MySQL returns
     * one row per column pair; this method intentionally emits one
     * column-level {@link RelationshipCandidate} per row, which keeps the core
     * model simple and lets the merger/grouping layer reason about related
     * evidence later.
     *
     * <p>Complete composite-key example:
     * <pre>{@code
     * CREATE TABLE crm.accounts (
     *   tenant_id BIGINT NOT NULL,
     *   account_id BIGINT NOT NULL,
     *   PRIMARY KEY (tenant_id, account_id)
     * );
     *
     * CREATE TABLE crm.invoices (
     *   tenant_id BIGINT NOT NULL,
     *   account_id BIGINT NOT NULL,
     *   invoice_id BIGINT NOT NULL,
     *   PRIMARY KEY (tenant_id, invoice_id),
     *   CONSTRAINT fk_invoice_account
     *     FOREIGN KEY (tenant_id, account_id)
     *     REFERENCES crm.accounts(tenant_id, account_id)
     * );
     * }</pre>
     *
     * <p>The result-set loop emits two candidates:
     * <pre>{@code
     * crm.invoices.tenant_id  -> crm.accounts.tenant_id
     * crm.invoices.account_id -> crm.accounts.account_id
     * }</pre>
     *
     * <p>Permission/version behavior: production read-only accounts often miss
     * some catalog privileges. A failure here is converted to a metadata warning
     * so the scan can still use DDL files, object definitions, SQL logs, and data
     * profiling evidence.
     */
    private void collectForeignKeys(Connection connection, ScanScope scope, MetadataSnapshot snapshot) {
        String sql = """
                SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME,
                       REFERENCED_TABLE_SCHEMA, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME,
                       CONSTRAINT_NAME
                FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = ?
                  AND REFERENCED_TABLE_NAME IS NOT NULL
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            // Scope is schema-level for now. Table-level filters can be added here
            // later without changing the MetadataCollector SPI contract.
            ps.setString(1, scope.schema());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    /*
                     * Each result-set row is already a source-column to
                     * target-column mapping produced by MySQL's catalog. We do
                     * not infer direction here: TABLE_SCHEMA/TABLE_NAME/COLUMN_NAME
                     * is the child/reference-holding side, while
                     * REFERENCED_* is the parent/referenced side.
                     */
                    TableId sourceTable = TableId.of(rs.getString("TABLE_SCHEMA"), rs.getString("TABLE_NAME"));
                    if (!inScope(scope, sourceTable.tableName())) {
                        continue;
                    }
                    TableId targetTable = TableId.of(rs.getString("REFERENCED_TABLE_SCHEMA"), rs.getString("REFERENCED_TABLE_NAME"));
                    if (!inScope(scope, targetTable.tableName())) {
                        continue;
                    }
                    RelationshipCandidate candidate = new RelationshipCandidate(
                            Endpoint.column(ColumnRef.of(sourceTable, rs.getString("COLUMN_NAME"))),
                            Endpoint.column(ColumnRef.of(targetTable, rs.getString("REFERENCED_COLUMN_NAME"))),
                            RelationType.FK_LIKE,
                            RelationSubType.DECLARED_FK);
                    /*
                     * 0.98 is intentionally very high because this is declared
                     * database metadata, but still below 1.0 so downstream policy
                     * can reserve absolute certainty for verified/locked sources
                     * if the product later needs that distinction.
                     */
                    candidate.evidence().add(Evidence.of(EvidenceType.METADATA_FOREIGN_KEY, DefaultEvidenceScores.METADATA_FOREIGN_KEY,
                            EvidenceSourceType.METADATA, "information_schema.KEY_COLUMN_USAGE",
                            rs.getString("CONSTRAINT_NAME")));
                    snapshot.relationships().add(candidate);
                }
            }
        } catch (Exception ex) {
            snapshot.warnings().add(WarningMessage.warn(WarningType.PERMISSION_WARNING,
                    "MYSQL_METADATA_FK_FAILED", ex.getMessage(), "information_schema.KEY_COLUMN_USAGE", 0));
        }
    }

    private void collectConstraints(Connection connection, ScanScope scope, MetadataSnapshot snapshot) {
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
            try (PreparedStatement ps = connection.prepareStatement(constraintsSql)) {
                ps.setString(1, scope.schema());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        if (!inScope(scope, tableName)) {
                            continue;
                        }
                        ConstraintBuilder builder = builder(builders, rs.getString("CONSTRAINT_SCHEMA"), tableName,
                                rs.getString("CONSTRAINT_NAME"));
                        builder.type = rs.getString("CONSTRAINT_TYPE");
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(keyUsageSql)) {
                ps.setString(1, scope.schema());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        if (!inScope(scope, tableName)) {
                            continue;
                        }
                        ConstraintBuilder builder = builder(builders, rs.getString("CONSTRAINT_SCHEMA"), tableName,
                                rs.getString("CONSTRAINT_NAME"));
                        builder.columns.add(rs.getString("COLUMN_NAME"));
                        if (rs.getString("REFERENCED_TABLE_NAME") != null) {
                            builder.referencedSchema = rs.getString("REFERENCED_TABLE_SCHEMA");
                            builder.referencedTable = rs.getString("REFERENCED_TABLE_NAME");
                            builder.referencedColumns.add(rs.getString("REFERENCED_COLUMN_NAME"));
                        }
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(refsSql)) {
                ps.setString(1, scope.schema());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        for (ConstraintBuilder builder : builders.values()) {
                            if (equalsIgnoreCase(builder.schema, rs.getString("CONSTRAINT_SCHEMA"))
                                    && equalsIgnoreCase(builder.name, rs.getString("CONSTRAINT_NAME"))) {
                                builder.updateRule = rs.getString("UPDATE_RULE");
                                builder.deleteRule = rs.getString("DELETE_RULE");
                            }
                        }
                    }
                }
            }
            builders.values().forEach(builder -> snapshot.constraintFacts().add(builder.toFact()));
        } catch (Exception ex) {
            snapshot.warnings().add(WarningMessage.warn(WarningType.PERMISSION_WARNING,
                    "MYSQL_METADATA_CONSTRAINTS_FAILED", ex.getMessage(), "information_schema.TABLE_CONSTRAINTS", 0));
        }
    }

    private void collectIndexes(Connection connection, ScanScope scope, MetadataSnapshot snapshot) {
        String sql = """
                SELECT TABLE_SCHEMA, TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX,
                       COLUMN_NAME, INDEX_TYPE, SUB_PART
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = ?
                ORDER BY TABLE_SCHEMA, TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, scope.schema());
            Map<String, IndexBuilder> indexes = new LinkedHashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (!inScope(scope, tableName)) {
                        continue;
                    }
                    String schema = rs.getString("TABLE_SCHEMA");
                    String indexName = rs.getString("INDEX_NAME");
                    int nonUnique = rs.getInt("NON_UNIQUE");
                    String indexType = rs.getString("INDEX_TYPE");
                    IndexBuilder builder = indexes.computeIfAbsent(
                            schema + "|" + tableName + "|" + indexName,
                            ignored -> new IndexBuilder(schema, tableName, indexName, nonUnique == 0,
                                    "PRIMARY".equalsIgnoreCase(indexName), indexType, true));
                    builder.entries.add(new IndexEntry(rs.getInt("SEQ_IN_INDEX"), rs.getString("COLUMN_NAME"),
                            null, stringOrNull(rs.getObject("SUB_PART"))));
                }
            }
            indexes.values().forEach(builder -> snapshot.indexFacts().add(builder.toFact()));
        } catch (Exception ex) {
            snapshot.warnings().add(WarningMessage.warn(WarningType.PERMISSION_WARNING,
                    "MYSQL_METADATA_INDEXES_FAILED", ex.getMessage(), "information_schema.STATISTICS", 0));
        }
    }

    private ConstraintBuilder builder(Map<String, ConstraintBuilder> builders, String schema, String table, String name) {
        return builders.computeIfAbsent(schema + "|" + table + "|" + name,
                ignored -> new ConstraintBuilder(schema, table, name));
    }

    private boolean inScope(ScanScope scope, String tableName) {
        String normalized = normalize(tableName);
        boolean included = scope.includeTables().isEmpty()
                || scope.includeTables().stream().map(this::normalize).anyMatch(normalized::equals);
        boolean excluded = scope.excludeTables().stream().map(this::normalize).anyMatch(normalized::equals);
        return included && !excluded;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static final class ConstraintBuilder {
        private final String schema;
        private final String table;
        private final String name;
        private String type;
        private final List<String> columns = new ArrayList<>();
        private String referencedSchema;
        private String referencedTable;
        private final List<String> referencedColumns = new ArrayList<>();
        private String updateRule;
        private String deleteRule;

        private ConstraintBuilder(String schema, String table, String name) {
            this.schema = schema;
            this.table = table;
            this.name = name;
        }

        private MetadataConstraintFact toFact() {
            return new MetadataConstraintFact(schema, table, name, type, columns,
                    referencedSchema, referencedTable, referencedColumns, updateRule, deleteRule);
        }
    }

    private static final class IndexBuilder {
        private final String schema;
        private final String table;
        private final String name;
        private final boolean unique;
        private final boolean primary;
        private final String type;
        private final boolean visible;
        private final List<IndexEntry> entries = new ArrayList<>();

        private IndexBuilder(String schema, String table, String name, boolean unique, boolean primary, String type, boolean visible) {
            this.schema = schema;
            this.table = table;
            this.name = name;
            this.unique = unique;
            this.primary = primary;
            this.type = type;
            this.visible = visible;
        }

        private MetadataIndexFact toFact() {
            entries.sort(Comparator.comparingInt(IndexEntry::seq));
            return new MetadataIndexFact(schema, table, name, unique, primary, type, visible,
                    entries.stream().map(IndexEntry::column).filter(value -> value != null && !value.isBlank()).toList(),
                    entries.stream().map(IndexEntry::expression).filter(value -> value != null && !value.isBlank()).toList(),
                    entries.stream().map(entry -> entry.subPart() == null ? "" : entry.subPart()).toList(),
                    entries.stream().map(IndexEntry::seq).toList());
        }
    }

    private record IndexEntry(int seq, String column, String expression, String subPart) {
    }
}
