package com.relationdetector.mysql;

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

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.ColumnRef;
import com.relationdetector.api.Collectors.DataProfiler;
import com.relationdetector.api.Collectors.DatabaseDdlCollector;
import com.relationdetector.api.Collectors.DdlParser;
import com.relationdetector.api.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.api.Collectors.MetadataCollector;
import com.relationdetector.api.Collectors.ObjectDefinitionCollector;
import com.relationdetector.api.Collectors.SqlLogExtractor;
import com.relationdetector.api.Collectors.SqlRelationParser;
import com.relationdetector.api.Collectors.StructuredDdlParser;
import com.relationdetector.api.Collectors.StructuredSqlParser;
import com.relationdetector.api.DatabaseAdaptor;
import com.relationdetector.api.DatabaseDdlDefinition;
import com.relationdetector.api.DatabaseObjectDefinition;
import com.relationdetector.api.DefaultEvidenceScores;
import com.relationdetector.api.Endpoint;
import com.relationdetector.api.Evidence;
import com.relationdetector.api.IdentifierRules;
import com.relationdetector.api.MetadataColumnFact;
import com.relationdetector.api.MetadataConstraintFact;
import com.relationdetector.api.MetadataIndexFact;
import com.relationdetector.api.MetadataSnapshot;
import com.relationdetector.api.MetadataTableFact;
import com.relationdetector.api.ProfileRequest;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.ScanScope;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.TableId;
import com.relationdetector.api.WarningMessage;
import com.relationdetector.api.Enums.AdaptorCapability;
import com.relationdetector.api.Enums.DatabaseObjectType;
import com.relationdetector.api.Enums.DatabaseType;
import com.relationdetector.api.Enums.EvidenceSourceType;
import com.relationdetector.api.Enums.EvidenceType;
import com.relationdetector.api.Enums.LogFormatHint;
import com.relationdetector.api.Enums.RelationSubType;
import com.relationdetector.api.Enums.RelationType;
import com.relationdetector.api.Enums.StatementSourceType;
import com.relationdetector.api.Enums.WarningType;
import com.relationdetector.core.DiagnosticWarnings;
import com.relationdetector.core.PlainSqlLogExtractor;
import com.relationdetector.core.AntlrSqlRelationParser;

/** MySQL 5.7/8.0 adaptor implementing the Phase 4 design. */
public final class MySqlDatabaseAdaptor implements DatabaseAdaptor {
    private final IdentifierRules identifierRules = identifier -> {
        if (identifier == null) {
            return null;
        }
        String unquoted = identifierRules().unquote(identifier);
        return unquoted;
    };

    @Override
    public String id() {
        return "mysql";
    }

    @Override
    public String displayName() {
        return "MySQL";
    }

    @Override
    public Set<DatabaseType> supportedDatabaseTypes() {
        return Set.of(DatabaseType.MYSQL);
    }

    @Override
    public Set<AdaptorCapability> capabilities() {
        return Set.of(
                AdaptorCapability.METADATA,
                AdaptorCapability.DDL_PARSING,
                AdaptorCapability.DATABASE_OBJECTS,
                AdaptorCapability.NATIVE_LOGS,
                AdaptorCapability.DATA_PROFILING,
                AdaptorCapability.EVIDENCE_WEIGHT_ADJUSTMENT);
    }

    @Override
    public IdentifierRules identifierRules() {
        return identifierRules;
    }

    @Override
    public MetadataCollector metadataCollector() {
        return new MySqlMetadataCollector();
    }

    @Override
    public ObjectDefinitionCollector objectDefinitionCollector() {
        return new MySqlObjectCollector();
    }

    @Override
    public DdlParser ddlParser() {
        return new MySqlDdlParser();
    }

    @Override
    public Optional<DatabaseDdlCollector> databaseDdlCollector() {
        return Optional.of(new MySqlDatabaseDdlCollector());
    }

    @Override
    public SqlLogExtractor sqlLogExtractor() {
        return new MySqlLogExtractor();
    }

    @Override
    public SqlRelationParser sqlRelationParser() {
        return new AntlrSqlRelationParser(new MySqlAntlrSqlParser(), new MySqlRelationExtractionVisitor());
    }

    @Override
    public Optional<StructuredSqlParser> structuredSqlParser() {
        return Optional.of(new MySqlAntlrSqlParser());
    }

    @Override
    public Optional<StructuredDdlParser> structuredDdlParser() {
        return Optional.of(new MySqlAntlrDdlParser());
    }

    @Override
    public Optional<DataProfiler> dataProfiler() {
        return Optional.of(new MySqlDataProfiler());
    }

    @Override
    public EvidenceWeightAdjuster evidenceWeightAdjuster() {
        return (evidence, context) -> evidence;
    }

    /**
     * MySQL catalog reader for explicit foreign-key metadata.
     *
     * <p>This class is the adaptor-side implementation of the common
     * {@link MetadataCollector} SPI. It only reads database-owned metadata; SQL text
     * from procedures/views/triggers is handled by {@link MySqlObjectCollector} and
     * then parsed by the MySQL ANTLR SQL parser.
     *
     * <p>Call relationship:
     * <pre>
     * ScanEngine.scan(...)
     *   -> MySqlDatabaseAdaptor.metadataCollector()
     *   -> MySqlMetadataCollector.collect(...)
     *   -> collectForeignKeys(...)
     *   -> MetadataSnapshot.relationships()
     * </pre>
     *
     * <p>Complete MySQL DDL example this collector is designed to detect:
     * <pre>{@code
     * CREATE TABLE crm.users (
     *   id BIGINT NOT NULL,
     *   email VARCHAR(255) NOT NULL,
     *   PRIMARY KEY (id)
     * );
     *
     * CREATE TABLE crm.orders (
     *   id BIGINT NOT NULL,
     *   user_id BIGINT NOT NULL,
     *   created_at DATETIME NOT NULL,
     *   PRIMARY KEY (id),
     *   CONSTRAINT fk_orders_user
     *     FOREIGN KEY (user_id) REFERENCES crm.users(id)
     * );
     * }</pre>
     *
     * <p>Expected candidate emitted into the snapshot:
     * <pre>{@code
     * source: crm.orders.user_id
     * target: crm.users.id
     * relationType: FK_LIKE
     * relationSubType: DECLARED_FK
     * evidenceType: METADATA_FOREIGN_KEY
     * evidenceSource: information_schema.KEY_COLUMN_USAGE
     * sourceDetail: fk_orders_user
     * }</pre>
     */
    static final class MySqlMetadataCollector implements MetadataCollector {
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

    /**
     * Reads real table DDL from MySQL using {@code SHOW CREATE TABLE}.
     *
     * <p>Design mapping: database-owned DDL is a separate source from checked-in
     * DDL files, but it should still use the same DDL parser runner. This
     * collector therefore only returns raw DDL text; it does not extract
     * relationships itself.
     *
     * <p>Call relationship:
     * <pre>
     * ScanEngine.scan(...)
     *   -> MySqlDatabaseAdaptor.databaseDdlCollector()
     *   -> MySqlDatabaseDdlCollector.collect(...)
     *   -> DdlRelationParserRunner.parseText(...)
     * </pre>
     *
     * <p>Complete SQL shape generated by this collector:
     * <pre>{@code
     * SHOW CREATE TABLE `shop`.`orders`
     * }</pre>
     *
     * <p>The returned {@link DatabaseDdlDefinition#source()} is
     * {@code SHOW CREATE TABLE}; the core runner later rewrites DDL evidence to
     * {@code EvidenceSourceType.DATABASE_DDL} so operators can see the evidence
     * came from the live database definition.
     */
    static final class MySqlDatabaseDdlCollector implements DatabaseDdlCollector {
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

    static final class MySqlObjectCollector implements ObjectDefinitionCollector {
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

    static final class MySqlLogExtractor implements SqlLogExtractor {
        @Override
        public Stream<SqlStatementRecord> extract(Path file, LogFormatHint hint) {
            return extract(file, hint, warning -> {
            });
        }

        @Override
        public Stream<SqlStatementRecord> extract(
                Path file,
                LogFormatHint hint,
                Consumer<WarningMessage> warnings
        ) {
            if (hint == LogFormatHint.PLAIN_SQL) {
                return new PlainSqlLogExtractor().extract(file, StatementSourceType.PLAIN_SQL, warnings);
            }
            try {
                List<SqlStatementRecord> records = new ArrayList<>();
                List<String> lines = Files.readAllLines(file);
                StringBuilder current = new StringBuilder();
                long startLine = 1;
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.startsWith("#") || line.startsWith("SET timestamp")) {
                        continue;
                    }
                    int queryIndex = line.indexOf(" Query ");
                    String sql = queryIndex >= 0 ? line.substring(queryIndex + " Query ".length()) : line;
                    if (sql.toLowerCase().contains("select") || sql.toLowerCase().contains("join")) {
                        if (current.isEmpty()) {
                            startLine = i + 1L;
                        }
                        current.append(sql).append('\n');
                        if (sql.trim().endsWith(";")) {
                            records.add(new SqlStatementRecord(current.toString(), StatementSourceType.NATIVE_LOG,
                                    file.toString(), startLine, i + 1L, java.util.Map.of()));
                            current.setLength(0);
                        }
                    }
                }
                if (!current.isEmpty()) {
                    records.add(new SqlStatementRecord(current.toString(), StatementSourceType.NATIVE_LOG,
                            file.toString(), startLine, lines.size(), java.util.Map.of()));
                }
                return records.stream();
            } catch (Exception ex) {
                warnings.accept(DiagnosticWarnings.logExtractFailed(file, ex));
                return Stream.empty();
            }
        }
    }

    static final class MySqlDataProfiler implements DataProfiler {
        @Override
        public List<Evidence> profile(Connection connection, ProfileRequest request) {
            RelationshipCandidate c = request.candidate();
            if (!c.source().isColumnLevel() || !c.target().isColumnLevel()) {
                return List.of();
            }
            String sql = "SELECT COUNT(*) FROM (SELECT DISTINCT " + c.source().column().columnName()
                    + " AS v FROM " + c.source().table().displayName()
                    + " WHERE " + c.source().column().columnName() + " IS NOT NULL LIMIT " + request.sampleRows()
                    + ") s JOIN " + c.target().table().displayName()
                    + " t ON s.v = t." + c.target().column().columnName();
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(request.timeoutSeconds());
                try (ResultSet rs = statement.executeQuery(sql)) {
                    if (rs.next() && rs.getLong(1) > 0) {
                        Evidence evidence = new Evidence(EvidenceType.VALUE_OVERLAP_HIGH, new BigDecimal("0.20"),
                                EvidenceSourceType.DATA_PROFILE, "mysql-data-profile",
                                "sampled source values matched target values",
                                java.util.Map.of("matched", rs.getLong(1), "sampleRows", request.sampleRows()));
                        return List.of(evidence);
                    }
                }
            } catch (Exception ignored) {
            }
            return List.of();
        }
    }
}
