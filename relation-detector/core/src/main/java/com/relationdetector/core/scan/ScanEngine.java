package com.relationdetector.core.scan;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.ScanScope;

/**
 * 默认扫描编排器。
 *
 * <p>CN: ScanEngine 保留对外 scan 入口，内部委托 source collection、statement
 * parsing、evidence enhancement 和 result assembly。它负责 workflow 串联和
 * database connection lifecycle，不承载方言解析细节。
 *
 * <p>EN: Default scan orchestrator. It keeps the public scan entry point and
 * delegates source collection, statement parsing, evidence enhancement, and
 * result assembly to small internal pipeline components.
 */
public final class ScanEngine {
    private final StatementParsePipeline statementParsePipeline = new StatementParsePipeline();
    private final SourceCollectorPipeline sourceCollectorPipeline = new SourceCollectorPipeline(statementParsePipeline);
    private final EvidenceEnhancementPipeline evidenceEnhancementPipeline = new EvidenceEnhancementPipeline();
    private final DataProfilePipeline dataProfilePipeline = new DataProfilePipeline();
    private final ResultAssembly resultAssembly = new ResultAssembly();
    private final ScanCapabilityValidator capabilityValidator = new ScanCapabilityValidator();

    /**
     * 执行一次完整 scan，并返回 relationship、dataLineage、warning 和 source summary。
     *
     * <p>EN: Runs one complete scan and returns relationships, data lineages,
     * warnings, and source summary.
     */
    public ScanResult scan(ScanConfig config, DatabaseAdaptor adaptor) {
        return scan(config.resolve(), adaptor);
    }

    /** Runs a scan from an immutable, fully resolved runtime snapshot. */
    public ScanResult scan(ResolvedScanConfig config, DatabaseAdaptor adaptor) {
        capabilityValidator.validate(config, adaptor);
        DatabaseConfig requestedDatabase = config.database();
        ScanScope scope = adaptor.canonicalizeScope(new ScanScope(
                requestedDatabase.catalog(), requestedDatabase.schema(),
                requestedDatabase.includeTables(), requestedDatabase.excludeTables()));
        ResolvedScanConfig runtimeConfig = config;
        ScanResult result = new ScanResult(config.database().databaseType().name(), config.database().schema());
        Connection connection = null;
        Exception connectionFailure = null;
        try {
            connection = openConnection(config.database());
            runtimeConfig = discoverJdbcDatabaseVersion(config, connection);
        } catch (Exception ex) {
            connectionFailure = ex;
        }

        DatabaseConfig database = runtimeConfig.database();
        AdaptorContext context = new AdaptorContext(scope, java.util.Map.of(), result.warnings()::add);
        List<RelationshipCandidate> candidates = new ArrayList<>();
        List<DataLineageCandidate> dataLineageCandidates = new ArrayList<>();
        ScanPipelineContext pipelineContext = new ScanPipelineContext(
                runtimeConfig,
                adaptor,
                scope,
                result,
                context,
                candidates,
                dataLineageCandidates);

        if (connectionFailure != null) {
            result.warnings().add(WarningMessage.warn(WarningType.PERMISSION_WARNING,
                    "DB_SCAN_FAILED", connectionFailure.getMessage(), database.jdbcUrl(), 0));
        } else {
            try {
                sourceCollectorPipeline.collectJdbcSources(connection, pipelineContext);
            } catch (Exception ex) {
                result.warnings().add(WarningMessage.warn(WarningType.PERMISSION_WARNING,
                        "DB_SCAN_FAILED", ex.getMessage(), database.jdbcUrl(), 0));
            }
        }

        try {
            sourceCollectorPipeline.collectFileSources(pipelineContext);
            evidenceEnhancementPipeline.enhance(pipelineContext);
            List<RelationshipCandidate> profiledCandidates = dataProfilePipeline.profile(connection, pipelineContext);
            evidenceEnhancementPipeline.enhanceProfiledCandidates(pipelineContext, profiledCandidates);
            return resultAssembly.assemble(pipelineContext);
        } finally {
            pipelineContext.close();
            closeQuietly(connection);
        }
    }

    private Connection openConnection(DatabaseConfig config) throws Exception {
        if (config.jdbcUrl() == null || config.jdbcUrl().isBlank()) {
            return null;
        }
        return DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password());
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (Exception ignored) {
        }
    }

    private ResolvedScanConfig discoverJdbcDatabaseVersion(ResolvedScanConfig config, Connection connection) {
        if (connection == null || !config.parser().databaseVersion().isBlank()) {
            return config;
        }
        try {
            var metaData = connection.getMetaData();
            String version = metaData.getDatabaseMajorVersion() + "." + metaData.getDatabaseMinorVersion();
            return config.withJdbcDatabaseVersion(version);
        } catch (Exception ignored) {
            return config;
        }
    }
}
