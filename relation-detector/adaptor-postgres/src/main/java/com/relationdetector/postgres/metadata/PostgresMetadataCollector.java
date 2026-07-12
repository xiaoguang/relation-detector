package com.relationdetector.postgres.metadata;


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
import com.relationdetector.core.relation.TokenEventSqlRelationParser;
import com.relationdetector.postgres.tokenevent.PostgresTokenEventStructuredDdlParser;
import com.relationdetector.postgres.tokenevent.PostgresTokenEventStructuredSqlParser;

/** PostgreSQL 12+ adaptor implementing the Phase 5 design. */

public final class PostgresMetadataCollector implements MetadataCollector {
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
