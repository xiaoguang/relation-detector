package com.relationdetector.core.scan;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.LiveSourceConfigurationException;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.core.diagnostics.LiveDiagnosticSanitizer;

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
    private final ResultAssembler resultAssembler = new ResultAssembler();
    private final AdaptorContractValidator adaptorContractValidator = new AdaptorContractValidator();
    private final ScanCapabilityValidator capabilityValidator = new ScanCapabilityValidator();
    private final ScanConfigurationValidator configurationValidator = new ScanConfigurationValidator();

    /**
     *
     * 执行一次完整 scan，并返回 relationship、dataLineage、warning 和 source summary。
     *
     * <p>EN: Runs one complete scan and returns relationships, data lineages,
     * warnings, and source summary.
     */
    public ScanResult scan(ScanConfig config, DatabaseAdaptor adaptor) {
        return scan(config.resolve(), adaptor);
    }

    /**
     * CN: 校验不可变运行配置和 adaptor 契约，解析一次 live namespace，并按 source、
     * evidence、profiling、assembly 的固定顺序执行扫描。返回完整 ScanResult；配置或连接
     * 失败直接抛出，已打开的连接和 pipeline 资源始终在 finally 中关闭。
     *
     * EN: Validates the immutable runtime configuration and adaptor contract,
     * resolves the live namespace once, and executes source collection, evidence,
     * profiling, and assembly in order. It returns the complete ScanResult, fails
     * fast on configuration or connection errors, and always closes owned resources.
     */
    public ScanResult scan(ResolvedScanConfig config, DatabaseAdaptor adaptor) {
        configurationValidator.validate(config);
        adaptorContractValidator.validate(config.database(), adaptor);
        capabilityValidator.validate(config, adaptor);
        DatabaseConfig requestedDatabase = config.database();
        ScanScope scope = adaptor.canonicalizeScope(new ScanScope(
                requestedDatabase.catalog(), requestedDatabase.schema(),
                requestedDatabase.includeTables(), requestedDatabase.excludeTables()));
        ResolvedScanConfig runtimeConfig = config;
        Connection connection = null;
        try {
            connection = openConnection(config.database());
            runtimeConfig = discoverJdbcDatabaseVersion(config, connection);
            if (connection != null) {
                scope = adaptor.resolveLiveScope(connection, scope);
            }
        } catch (LiveSourceConfigurationException ex) {
            closeQuietly(connection);
            throw ex;
        } catch (Exception ex) {
            closeQuietly(connection);
            throw new DatabaseConnectionException(ex);
        }

        ScanResult result = new ScanResult(
                config.database().databaseType().name(), scope.catalog(), scope.schema());

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

        try {
            sourceCollectorPipeline.collectJdbcSources(connection, pipelineContext);
            sourceCollectorPipeline.collectFileSources(pipelineContext);
            evidenceEnhancementPipeline.enhance(pipelineContext);
            List<RelationshipCandidate> profiledCandidates = dataProfilePipeline.profile(connection, pipelineContext);
            evidenceEnhancementPipeline.enhanceProfiledCandidates(pipelineContext, profiledCandidates);
            evidenceEnhancementPipeline.adjustWeights(pipelineContext);
            return resultAssembler.assemble(pipelineContext);
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
