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
    private final ResultAssembly resultAssembly = new ResultAssembly();

    /**
     * 执行一次完整 scan，并返回 relationship、dataLineage、warning 和 source summary。
     *
     * <p>EN: Runs one complete scan and returns relationships, data lineages,
     * warnings, and source summary.
     */
    public ScanResult scan(ScanConfig config, DatabaseAdaptor adaptor) {
        ScanScope scope = new ScanScope(config.catalog, config.schema, config.includeTables, config.excludeTables);
        ScanResult result = new ScanResult(config.databaseType.name(), config.schema);
        AdaptorContext context = new AdaptorContext(scope, java.util.Map.of(), result.warnings()::add);
        List<RelationshipCandidate> candidates = new ArrayList<>();
        List<DataLineageCandidate> dataLineageCandidates = new ArrayList<>();
        ScanPipelineContext pipelineContext = new ScanPipelineContext(
                config,
                adaptor,
                scope,
                result,
                context,
                candidates,
                dataLineageCandidates);

        try (Connection connection = openConnection(config)) {
            populateJdbcDatabaseVersion(config, connection);
            sourceCollectorPipeline.collectJdbcSources(connection, pipelineContext);
        } catch (Exception ex) {
            result.warnings().add(WarningMessage.warn(WarningType.PERMISSION_WARNING,
                    "DB_SCAN_FAILED", ex.getMessage(), config.jdbcUrl, 0));
        }

        sourceCollectorPipeline.collectFileSources(pipelineContext);
        evidenceEnhancementPipeline.enhance(pipelineContext);
        return resultAssembly.assemble(pipelineContext);
    }

    private Connection openConnection(ScanConfig config) throws Exception {
        if (config.jdbcUrl == null || config.jdbcUrl.isBlank()) {
            return null;
        }
        return DriverManager.getConnection(config.jdbcUrl, config.username, config.password);
    }

    private void populateJdbcDatabaseVersion(ScanConfig config, Connection connection) {
        if (connection == null || config == null || config.databaseVersion != null && !config.databaseVersion.isBlank()) {
            return;
        }
        try {
            var metaData = connection.getMetaData();
            config.databaseVersion = metaData.getDatabaseMajorVersion() + "." + metaData.getDatabaseMinorVersion();
            config.databaseVersionSource = "JDBC";
        } catch (Exception ignored) {
            config.databaseVersionSource = "UNKNOWN";
        }
    }
}
