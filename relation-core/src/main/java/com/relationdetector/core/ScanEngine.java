package com.relationdetector.core;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.DatabaseAdaptor;
import com.relationdetector.api.DatabaseObjectDefinition;
import com.relationdetector.api.ProfileRequest;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.ScanScope;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.WarningMessage;
import com.relationdetector.api.Enums.StatementSourceType;
import com.relationdetector.api.Enums.WarningType;

/**
 * Default scan orchestration.
 *
 * <p>Design mapping: Phase 3 states that adaptors may provide full-chain hooks,
 * while core keeps final merging and scoring. This engine calls adaptor hooks,
 * gathers candidates, optionally profiles them, and then merges via core.
 */
public final class ScanEngine {
    private final RelationshipMerger merger = new RelationshipMerger();
    private final PlainSqlLogExtractor plainExtractor = new PlainSqlLogExtractor();

    public ScanResult scan(ScanConfig config, DatabaseAdaptor adaptor) {
        ScanScope scope = new ScanScope(config.catalog, config.schema, config.includeTables, config.excludeTables);
        AdaptorContext context = new AdaptorContext(scope, java.util.Map.of());
        ScanResult result = new ScanResult(config.databaseType.name(), config.schema);
        List<RelationshipCandidate> candidates = new ArrayList<>();

        try (Connection connection = openConnection(config)) {
            if (config.metadataEnabled && connection != null) {
                result.sources().add("metadata");
                var snapshot = adaptor.metadataCollector().collect(connection, scope);
                candidates.addAll(snapshot.relationships());
                result.warnings().addAll(snapshot.warnings());
            }

            if (config.objectsEnabled && config.objectsFromDatabase && connection != null) {
                result.sources().add("database-objects");
                for (DatabaseObjectDefinition definition : adaptor.objectDefinitionCollector().collect(connection, scope)) {
                    StatementSourceType sourceType = switch (definition.type()) {
                        case VIEW -> StatementSourceType.VIEW;
                        case TRIGGER -> StatementSourceType.TRIGGER;
                        case FUNCTION -> StatementSourceType.FUNCTION;
                        case PROCEDURE -> StatementSourceType.PROCEDURE;
                    };
                    candidates.addAll(adaptor.sqlRelationParser().parse(
                            new SqlStatementRecord(definition.sql(), sourceType, definition.source(), 0, 0, java.util.Map.of()), context));
                }
            }

            if (config.dataProfileEnabled && connection != null) {
                result.sources().add("data-profile");
                int profiled = 0;
                for (RelationshipCandidate candidate : new ArrayList<>(candidates)) {
                    if (profiled++ >= config.maxCandidatePairs) {
                        break;
                    }
                    adaptor.dataProfiler().ifPresent(profiler ->
                            candidate.evidence().addAll(profiler.profile(connection,
                                    new ProfileRequest(candidate, config.sampleRows, config.timeoutSeconds))));
                }
            }
        } catch (Exception ex) {
            result.warnings().add(WarningMessage.warn(WarningType.PERMISSION_WARNING,
                    "DB_SCAN_FAILED", ex.getMessage(), config.jdbcUrl, 0));
        }

        if (config.ddlEnabled) {
            result.sources().add("ddl");
            for (Path file : config.ddlFiles) {
                candidates.addAll(adaptor.ddlParser().parseDdl(file, context));
            }
        }

        if (config.objectsEnabled) {
            result.sources().add("object-files");
            for (Path file : config.objectFiles) {
                plainExtractor.extract(file, StatementSourceType.PROCEDURE)
                        .forEach(statement -> candidates.addAll(adaptor.sqlRelationParser().parse(statement, context)));
            }
        }

        if (config.logsEnabled) {
            result.sources().add("logs");
            for (Path file : config.logFiles) {
                adaptor.sqlLogExtractor().extract(file, config.logFormatHint)
                        .forEach(statement -> candidates.addAll(adaptor.sqlRelationParser().parse(statement, context)));
            }
        }

        result.relationships().addAll(merger.merge(candidates, config.minConfidence));
        return result;
    }

    private Connection openConnection(ScanConfig config) throws Exception {
        if (config.jdbcUrl == null || config.jdbcUrl.isBlank()) {
            return null;
        }
        return DriverManager.getConnection(config.jdbcUrl, config.username, config.password);
    }
}
