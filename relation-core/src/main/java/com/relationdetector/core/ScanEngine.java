package com.relationdetector.core;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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
        ScanResult result = new ScanResult(config.databaseType.name(), config.schema);
        AdaptorContext context = new AdaptorContext(scope, java.util.Map.of(), result.warnings()::add);
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
                for (DatabaseObjectDefinition definition : collectDatabaseObjects(adaptor, connection, scope, result)) {
                    StatementSourceType sourceType = switch (definition.type()) {
                        case VIEW -> StatementSourceType.VIEW;
                        case MATERIALIZED_VIEW -> StatementSourceType.MATERIALIZED_VIEW;
                        case TRIGGER -> StatementSourceType.TRIGGER;
                        case EVENT -> StatementSourceType.EVENT;
                        case RULE -> StatementSourceType.RULE;
                        case FUNCTION -> StatementSourceType.FUNCTION;
                        case PROCEDURE -> StatementSourceType.PROCEDURE;
                        case PACKAGE -> StatementSourceType.PACKAGE;
                        case PACKAGE_BODY -> StatementSourceType.PACKAGE_BODY;
                    };
                    candidates.addAll(safeParseStatement(adaptor,
                            new SqlStatementRecord(definition.sql(), sourceType, definition.source(), 0, 0,
                                    java.util.Map.of("objectType", definition.type().name(),
                                            "schema", definition.schema(),
                                            "name", definition.name())),
                            context, result));
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
                candidates.addAll(safeParseDdl(adaptor, file, context, result));
            }
        }

        if (config.objectsEnabled) {
            result.sources().add("object-files");
            for (Path file : config.objectFiles) {
                plainExtractor.extract(file, StatementSourceType.PROCEDURE, result.warnings()::add)
                        .forEach(statement -> candidates.addAll(safeParseStatement(adaptor, statement, context, result)));
            }
        }

        if (config.logsEnabled) {
            result.sources().add("logs");
            for (Path file : config.logFiles) {
                safeExtractLog(adaptor, file, config, result)
                        .forEach(statement -> candidates.addAll(safeParseStatement(adaptor, statement, context, result)));
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

    /**
     * Calls the adaptor DDL parser while preserving failures as scan warnings.
     *
     * <p>Some adaptor parsers catch their own exceptions and write warnings via
     * {@link AdaptorContext#warn(WarningMessage)}. This wrapper is the safety net
     * for parsers that throw. It keeps the original DDL file text in
     * {@code warning.attributes.rawStatement} whenever the file can be read.
     */
    private List<RelationshipCandidate> safeParseDdl(
            DatabaseAdaptor adaptor,
            Path file,
            AdaptorContext context,
            ScanResult result
    ) {
        try {
            return adaptor.ddlParser().parseDdl(file, context);
        } catch (Exception ex) {
            result.warnings().add(DiagnosticWarnings.ddlParseFailed(file, ex));
            return List.of();
        }
    }

    /**
     * Calls the SQL relationship parser for one statement.
     *
     * <p>This is intentionally per-statement rather than per-file. A long log or
     * procedure dump can contain many statements; if one throws, the scanner
     * records that exact SQL text and keeps parsing the remaining statements.
     */
    private List<RelationshipCandidate> safeParseStatement(
            DatabaseAdaptor adaptor,
            SqlStatementRecord statement,
            AdaptorContext context,
            ScanResult result
    ) {
        try {
            return adaptor.sqlRelationParser().parse(statement, context);
        } catch (Exception ex) {
            result.warnings().add(DiagnosticWarnings.sqlParseFailed(statement, ex));
            return List.of();
        }
    }

    /**
     * Calls native log extraction with both SPI-level and wrapper-level
     * diagnostics. Extractors that know a dialect can report partial line/file
     * failures through the warning callback; thrown exceptions are converted here
     * into {@code LOG_EXTRACT_FAILED}.
     */
    private Stream<SqlStatementRecord> safeExtractLog(
            DatabaseAdaptor adaptor,
            Path file,
            ScanConfig config,
            ScanResult result
    ) {
        try {
            return adaptor.sqlLogExtractor().extract(file, config.logFormatHint, result.warnings()::add);
        } catch (Exception ex) {
            result.warnings().add(DiagnosticWarnings.logExtractFailed(file, ex));
            return Stream.empty();
        }
    }

    /**
     * Collects database object definitions while preserving partial collection
     * failures as warnings. Adaptor implementations can override the warning-aware
     * SPI method to report routine/view/trigger collection errors independently.
     */
    private List<DatabaseObjectDefinition> collectDatabaseObjects(
            DatabaseAdaptor adaptor,
            Connection connection,
            ScanScope scope,
            ScanResult result
    ) {
        try {
            return adaptor.objectDefinitionCollector().collect(connection, scope, result.warnings()::add);
        } catch (Exception ex) {
            result.warnings().add(DiagnosticWarnings.objectCollectFailed(
                    "OBJECT_DEFINITION_COLLECT_FAILED", "database-objects", ex));
            return List.of();
        }
    }
}
