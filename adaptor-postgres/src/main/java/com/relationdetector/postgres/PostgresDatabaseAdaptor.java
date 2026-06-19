package com.relationdetector.postgres;

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

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.ColumnRef;
import com.relationdetector.api.Collectors.DataProfiler;
import com.relationdetector.api.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.api.Collectors.MetadataCollector;
import com.relationdetector.api.Collectors.ObjectDefinitionCollector;
import com.relationdetector.api.Collectors.SqlLogExtractor;
import com.relationdetector.api.Collectors.SqlRelationParser;
import com.relationdetector.api.Collectors.StructuredDdlParser;
import com.relationdetector.api.Collectors.StructuredSqlParser;
import com.relationdetector.api.DatabaseAdaptor;
import com.relationdetector.api.DatabaseObjectDefinition;
import com.relationdetector.api.DefaultEvidenceScores;
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
import com.relationdetector.core.DiagnosticWarnings;
import com.relationdetector.core.PlainSqlLogExtractor;
import com.relationdetector.core.TokenEventSqlRelationParser;

/** PostgreSQL 12+ adaptor implementing the Phase 5 design. */
public final class PostgresDatabaseAdaptor implements DatabaseAdaptor {
    private final IdentifierRules identifierRules = identifier -> {
        if (identifier == null) {
            return null;
        }
        String value = identifierRules().unquote(identifier);
        boolean quoted = identifier.startsWith("\"") && identifier.endsWith("\"");
        return quoted ? value : value.toLowerCase(java.util.Locale.ROOT);
    };

    @Override
    public String id() {
        return "postgresql";
    }

    @Override
    public String displayName() {
        return "PostgreSQL";
    }

    @Override
    public Set<DatabaseType> supportedDatabaseTypes() {
        return Set.of(DatabaseType.POSTGRESQL);
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
        return new PostgresMetadataCollector();
    }

    @Override
    public ObjectDefinitionCollector objectDefinitionCollector() {
        return new PostgresObjectCollector();
    }

    @Override
    public SqlLogExtractor sqlLogExtractor() {
        return new PostgresLogExtractor();
    }

    @Override
    public SqlRelationParser sqlRelationParser() {
        return new TokenEventSqlRelationParser(new PostgresTokenEventStructuredSqlParser());
    }

    @Override
    public Optional<StructuredSqlParser> structuredSqlParser() {
        return Optional.of(new PostgresTokenEventStructuredSqlParser());
    }

    @Override
    public Optional<StructuredDdlParser> structuredDdlParser() {
        return Optional.of(new PostgresTokenEventStructuredDdlParser());
    }

    @Override
    public Optional<DataProfiler> dataProfiler() {
        return Optional.of(new PostgresDataProfiler());
    }

    @Override
    public EvidenceWeightAdjuster evidenceWeightAdjuster() {
        return (evidence, context) -> evidence;
    }

    /**
     * PostgreSQL catalog reader for explicit foreign-key metadata.
     *
     * <p>This is the PostgreSQL implementation of the common
     * {@link MetadataCollector} SPI. It reads only authoritative catalog data.
     * SQL bodies from functions/procedures/views are collected by
     * {@link PostgresObjectCollector}; SQL text relationship inference then happens
     * in the PostgreSQL ANTLR SQL parser.
     *
     * <p>Call relationship:
     * <pre>
     * ScanEngine.scan(...)
     *   -> PostgresDatabaseAdaptor.metadataCollector()
     *   -> PostgresMetadataCollector.collect(...)
     *   -> collectForeignKeys(...)
     *   -> MetadataSnapshot.relationships()
     * </pre>
     *
     * <p>Complete PostgreSQL DDL example this collector is designed to detect:
     * <pre>{@code
     * CREATE TABLE public.users (
     *   id bigint PRIMARY KEY,
     *   email text NOT NULL
     * );
     *
     * CREATE TABLE public.orders (
     *   id bigint PRIMARY KEY,
     *   user_id bigint NOT NULL,
     *   created_at timestamptz NOT NULL,
     *   CONSTRAINT fk_orders_user
     *     FOREIGN KEY (user_id) REFERENCES public.users(id)
     * );
     * }</pre>
     *
     * <p>Expected candidate emitted into the snapshot:
     * <pre>{@code
     * source: public.orders.user_id
     * target: public.users.id
     * relationType: FK_LIKE
     * relationSubType: DECLARED_FK
     * evidenceType: METADATA_FOREIGN_KEY
     * evidenceSource: pg_catalog.pg_constraint
     * sourceDetail: fk_orders_user
     * }</pre>
     */
    static final class PostgresMetadataCollector implements MetadataCollector {
        @Override
        public MetadataSnapshot collect(Connection connection, ScanScope scope) {
            /*
             * Keep this collector focused on raw catalog extraction. It does not
             * deduplicate, score-merge, or reinterpret direction. The core merger
             * later combines these declared-FK candidates with weaker evidence from
             * DDL files, SQL object bodies, logs, naming, and data profiling.
             */
            MetadataSnapshot snapshot = new MetadataSnapshot();
            collectForeignKeys(connection, scope, snapshot);
            return snapshot;
        }

        /**
         * Reads explicit PostgreSQL foreign keys from pg_catalog.
         *
         * <p>Why the query looks more complex than MySQL:
         * PostgreSQL stores FK column numbers as arrays on {@code pg_constraint}:
         * {@code con.conkey} contains source/child column attnums and
         * {@code con.confkey} contains target/parent column attnums. The query uses
         * {@code unnest(... ) WITH ORDINALITY} on both arrays and joins by
         * {@code ord} so the first source column maps to the first target column,
         * the second source column maps to the second target column, and so on.
         * {@code pg_attribute} then translates each attnum into a column name.
         *
         * <p>Complete composite-key example:
         * <pre>{@code
         * CREATE TABLE public.accounts (
         *   tenant_id bigint NOT NULL,
         *   account_id bigint NOT NULL,
         *   PRIMARY KEY (tenant_id, account_id)
         * );
         *
         * CREATE TABLE public.invoices (
         *   tenant_id bigint NOT NULL,
         *   account_id bigint NOT NULL,
         *   invoice_id bigint NOT NULL,
         *   PRIMARY KEY (tenant_id, invoice_id),
         *   CONSTRAINT fk_invoice_account
         *     FOREIGN KEY (tenant_id, account_id)
         *     REFERENCES public.accounts(tenant_id, account_id)
         * );
         * }</pre>
         *
         * <p>The result-set loop emits two candidates:
         * <pre>{@code
         * public.invoices.tenant_id  -> public.accounts.tenant_id
         * public.invoices.account_id -> public.accounts.account_id
         * }</pre>
         *
         * <p>Scope behavior: if the scan request does not provide a schema, this
         * implementation defaults to {@code public}, matching the common PostgreSQL
         * deployment convention. Future adaptors can expand this to multiple schemas
         * without changing the common {@link MetadataCollector} contract.
         *
         * <p>Permission/version behavior: if catalog access fails, the exception is
         * recorded as a warning and scanning continues with other evidence sources.
         */
        private void collectForeignKeys(Connection connection, ScanScope scope, MetadataSnapshot snapshot) {
            String sql = """
                    SELECT
                      ns.nspname AS source_schema,
                      source_table.relname AS source_table,
                      source_col.attname AS source_column,
                      target_ns.nspname AS target_schema,
                      target_table.relname AS target_table,
                      target_col.attname AS target_column,
                      con.conname AS constraint_name
                    FROM pg_constraint con
                    JOIN pg_class source_table ON source_table.oid = con.conrelid
                    JOIN pg_namespace ns ON ns.oid = source_table.relnamespace
                    JOIN pg_class target_table ON target_table.oid = con.confrelid
                    JOIN pg_namespace target_ns ON target_ns.oid = target_table.relnamespace
                    JOIN unnest(con.conkey) WITH ORDINALITY AS source_cols(attnum, ord) ON true
                    JOIN unnest(con.confkey) WITH ORDINALITY AS target_cols(attnum, ord) ON target_cols.ord = source_cols.ord
                    JOIN pg_attribute source_col ON source_col.attrelid = source_table.oid AND source_col.attnum = source_cols.attnum
                    JOIN pg_attribute target_col ON target_col.attrelid = target_table.oid AND target_col.attnum = target_cols.attnum
                    WHERE con.contype = 'f'
                      AND ns.nspname = ?
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                // PostgreSQL deployments often rely on "public"; use it as the
                // adaptor-level default when the CLI config does not specify schema.
                ps.setString(1, scope.schema() == null ? "public" : scope.schema());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        /*
                         * The SQL has already paired source and target columns by
                         * ordinal position. At this point each row is one
                         * child-column -> parent-column mapping. Direction is not
                         * inferred from naming; it comes directly from
                         * pg_constraint.conrelid (source) and confrelid (target).
                         */
                        TableId sourceTable = TableId.of(rs.getString("source_schema"), rs.getString("source_table"));
                        TableId targetTable = TableId.of(rs.getString("target_schema"), rs.getString("target_table"));
                        RelationshipCandidate candidate = new RelationshipCandidate(
                                Endpoint.column(ColumnRef.of(sourceTable, rs.getString("source_column"))),
                                Endpoint.column(ColumnRef.of(targetTable, rs.getString("target_column"))),
                                RelationType.FK_LIKE,
                                RelationSubType.DECLARED_FK);
                        /*
                         * Declared FK metadata is the strongest evidence currently
                         * emitted by adaptors. It remains below 1.0 so future product
                         * policy can distinguish "declared in catalog" from
                         * "declared and independently verified by data/profile".
                         */
                        candidate.evidence().add(Evidence.of(EvidenceType.METADATA_FOREIGN_KEY, DefaultEvidenceScores.METADATA_FOREIGN_KEY,
                                EvidenceSourceType.METADATA, "pg_catalog.pg_constraint",
                                rs.getString("constraint_name")));
                        snapshot.relationships().add(candidate);
                    }
                }
            } catch (Exception ex) {
                snapshot.warnings().add(WarningMessage.warn(WarningType.PERMISSION_WARNING,
                        "POSTGRES_METADATA_FK_FAILED", ex.getMessage(), "pg_catalog.pg_constraint", 0));
            }
        }
    }

    static final class PostgresObjectCollector implements ObjectDefinitionCollector {
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

    static final class PostgresLogExtractor implements SqlLogExtractor {
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
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    int statement = line.indexOf("statement:");
                    int execute = line.indexOf("execute ");
                    String sql = null;
                    if (statement >= 0) {
                        sql = line.substring(statement + "statement:".length()).trim();
                    } else if (execute >= 0 && line.contains(":")) {
                        sql = line.substring(line.indexOf(':', execute) + 1).trim();
                    }
                    if (sql != null && !sql.isBlank()) {
                        records.add(new SqlStatementRecord(sql, StatementSourceType.NATIVE_LOG,
                                file.toString(), i + 1L, i + 1L, java.util.Map.of()));
                    }
                }
                return records.stream();
            } catch (Exception ex) {
                warnings.accept(DiagnosticWarnings.logExtractFailed(file, ex));
                return Stream.empty();
            }
        }
    }

    static final class PostgresDataProfiler implements DataProfiler {
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
                                EvidenceSourceType.DATA_PROFILE, "postgres-data-profile",
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
