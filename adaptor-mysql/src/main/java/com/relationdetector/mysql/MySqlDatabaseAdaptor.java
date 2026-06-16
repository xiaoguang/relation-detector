package com.relationdetector.mysql;

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
import java.util.stream.Stream;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.ColumnRef;
import com.relationdetector.api.Collectors.DataProfiler;
import com.relationdetector.api.Collectors.DdlParser;
import com.relationdetector.api.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.api.Collectors.MetadataCollector;
import com.relationdetector.api.Collectors.ObjectDefinitionCollector;
import com.relationdetector.api.Collectors.SqlLogExtractor;
import com.relationdetector.api.Collectors.SqlRelationParser;
import com.relationdetector.api.DatabaseAdaptor;
import com.relationdetector.api.DatabaseObjectDefinition;
import com.relationdetector.api.Endpoint;
import com.relationdetector.api.Evidence;
import com.relationdetector.api.IdentifierRules;
import com.relationdetector.api.MetadataSnapshot;
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
import com.relationdetector.core.PlainSqlLogExtractor;
import com.relationdetector.core.SimpleDdlParser;
import com.relationdetector.core.SimpleSqlRelationParser;

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
        return (file, context) -> new SimpleDdlParser().parse(file);
    }

    @Override
    public SqlLogExtractor sqlLogExtractor() {
        return new MySqlLogExtractor();
    }

    @Override
    public SqlRelationParser sqlRelationParser() {
        SimpleSqlRelationParser parser = new SimpleSqlRelationParser();
        return (statement, context) -> parser.parse(statement);
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
     * then parsed by {@link SimpleSqlRelationParser}.
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
            collectForeignKeys(connection, scope, snapshot);
            return snapshot;
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
                        TableId targetTable = TableId.of(rs.getString("REFERENCED_TABLE_SCHEMA"), rs.getString("REFERENCED_TABLE_NAME"));
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
                        candidate.evidence().add(Evidence.of(EvidenceType.METADATA_FOREIGN_KEY, 0.98d,
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
    }

    static final class MySqlObjectCollector implements ObjectDefinitionCollector {
        @Override
        public List<DatabaseObjectDefinition> collect(Connection connection, ScanScope scope) {
            List<DatabaseObjectDefinition> definitions = new ArrayList<>();
            collectRoutines(connection, scope, definitions);
            collectViews(connection, scope, definitions);
            collectTriggers(connection, scope, definitions);
            return definitions;
        }

        private void collectRoutines(Connection connection, ScanScope scope, List<DatabaseObjectDefinition> definitions) {
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
            } catch (Exception ignored) {
                // Missing routine privileges are common in production; metadata scan can continue.
            }
        }

        private void collectViews(Connection connection, ScanScope scope, List<DatabaseObjectDefinition> definitions) {
            String sql = "SELECT TABLE_SCHEMA, TABLE_NAME, VIEW_DEFINITION FROM information_schema.VIEWS WHERE TABLE_SCHEMA = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, scope.schema());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        definitions.add(new DatabaseObjectDefinition(DatabaseObjectType.VIEW, rs.getString("TABLE_SCHEMA"),
                                rs.getString("TABLE_NAME"), rs.getString("VIEW_DEFINITION"), "information_schema.VIEWS"));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        private void collectTriggers(Connection connection, ScanScope scope, List<DatabaseObjectDefinition> definitions) {
            String sql = "SELECT TRIGGER_SCHEMA, TRIGGER_NAME, ACTION_STATEMENT FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, scope.schema());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        definitions.add(new DatabaseObjectDefinition(DatabaseObjectType.TRIGGER, rs.getString("TRIGGER_SCHEMA"),
                                rs.getString("TRIGGER_NAME"), rs.getString("ACTION_STATEMENT"), "information_schema.TRIGGERS"));
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    static final class MySqlLogExtractor implements SqlLogExtractor {
        @Override
        public Stream<SqlStatementRecord> extract(Path file, LogFormatHint hint) {
            if (hint == LogFormatHint.PLAIN_SQL) {
                return new PlainSqlLogExtractor().extract(file, StatementSourceType.PLAIN_SQL);
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
