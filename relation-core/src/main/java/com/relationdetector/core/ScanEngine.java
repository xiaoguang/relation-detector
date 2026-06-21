package com.relationdetector.core;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.ColumnRef;
import com.relationdetector.api.DataLineageCandidate;
import com.relationdetector.api.DatabaseAdaptor;
import com.relationdetector.api.DatabaseDdlDefinition;
import com.relationdetector.api.DatabaseObjectDefinition;
import com.relationdetector.api.Endpoint;
import com.relationdetector.api.MetadataTableFact;
import com.relationdetector.api.MetadataSnapshot;
import com.relationdetector.api.ProfileRequest;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.ScanScope;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.TableId;
import com.relationdetector.api.WarningMessage;
import com.relationdetector.core.lineage.DataLineageMerger;
import com.relationdetector.core.lineage.TokenEventDataLineageExtractor;
import com.relationdetector.core.parser.DdlRelationParserRunner;
import com.relationdetector.core.parser.SqlRelationParserRunner;
import com.relationdetector.core.relation.RelationshipMerger;
import com.relationdetector.api.Enums.EvidenceSourceType;
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
    private final SqlRelationParserRunner sqlParserRunner = new SqlRelationParserRunner();
    private final DdlRelationParserRunner ddlParserRunner = new DdlRelationParserRunner();
    private final MetadataEvidenceEnhancer metadataEvidenceEnhancer = new MetadataEvidenceEnhancer();
    private final TokenEventDataLineageExtractor dataLineageExtractor = new TokenEventDataLineageExtractor();
    private final DataLineageMerger dataLineageMerger = new DataLineageMerger();

    public ScanResult scan(ScanConfig config, DatabaseAdaptor adaptor) {
        ScanScope scope = new ScanScope(config.catalog, config.schema, config.includeTables, config.excludeTables);
        ScanResult result = new ScanResult(config.databaseType.name(), config.schema);
        AdaptorContext context = new AdaptorContext(scope, java.util.Map.of(), result.warnings()::add);
        List<RelationshipCandidate> candidates = new ArrayList<>();
        List<DataLineageCandidate> dataLineageCandidates = new ArrayList<>();
        MetadataSnapshot metadataSnapshot = null;

        try (Connection connection = openConnection(config)) {
            populateJdbcDatabaseVersion(config, connection);
            if (config.metadataEnabled && connection != null) {
                result.sources().add("metadata");
                metadataSnapshot = adaptor.metadataCollector().collect(connection, scope);
                candidates.addAll(metadataSnapshot.relationships());
                result.warnings().addAll(metadataSnapshot.warnings());
            }

            if (config.ddlEnabled && config.ddlFromDatabase && connection != null) {
                result.sources().add("database-ddl");
                for (DatabaseDdlDefinition definition : collectDatabaseDdl(adaptor, connection, scope, result)) {
                    candidates.addAll(safeParseDatabaseDdl(adaptor, config, definition, context, result));
                }
            }

            if (config.objectsEnabled && config.objectsFromDatabase && connection != null) {
                result.sources().add("database-objects");
                Set<TableId> databaseKnownPhysicalTables = knownPhysicalTables(metadataSnapshot);
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
                    candidates.addAll(safeParseStatement(adaptor, config,
                            new SqlStatementRecord(definition.sql(), sourceType, objectSourceName(definition), 0, 0,
                                    objectAttributes(definition)),
                            context, result, dataLineageCandidates, databaseKnownPhysicalTables));
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
                candidates.addAll(safeParseDdl(adaptor, config, file, context, result));
            }
        }

        if (config.objectsEnabled) {
            result.sources().add("object-files");
            Set<TableId> fileKnownPhysicalTables = knownPhysicalTables(metadataSnapshot);
            for (Path file : config.objectFiles) {
                plainExtractor.extract(file, StatementSourceType.PROCEDURE, result.warnings()::add)
                        .forEach(statement -> candidates.addAll(safeParseStatement(adaptor, config, statement, context,
                                result, dataLineageCandidates, fileKnownPhysicalTables)));
            }
        }

        if (config.logsEnabled) {
            result.sources().add("logs");
            Set<TableId> logKnownPhysicalTables = knownPhysicalTables(metadataSnapshot);
            for (Path file : config.logFiles) {
                safeExtractLog(adaptor, file, config, result)
                        .forEach(statement -> candidates.addAll(safeParseStatement(adaptor, config, statement, context,
                                result, dataLineageCandidates, logKnownPhysicalTables)));
            }
        }

        if (metadataSnapshot != null) {
            metadataEvidenceEnhancer.enhance(candidates, metadataSnapshot);
        }
        result.relationships().addAll(merger.merge(candidates, config.minConfidence));
        result.dataLineages().addAll(dataLineageMerger.merge(dataLineageCandidates));
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
            ScanConfig config,
            Path file,
            AdaptorContext context,
            ScanResult result
    ) {
        try {
            return ddlParserRunner.parse(adaptor, config, file, context);
        } catch (Exception ex) {
            result.warnings().add(DiagnosticWarnings.ddlParseFailed(file, ex));
            return List.of();
        }
    }

    private List<RelationshipCandidate> safeParseDatabaseDdl(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            DatabaseDdlDefinition definition,
            AdaptorContext context,
            ScanResult result
    ) {
        try {
            List<RelationshipCandidate> parsed = ddlParserRunner.parseText(adaptor, config,
                    definition.ddl(), definition.source(), EvidenceSourceType.DATABASE_DDL, context);
            return qualifyDatabaseDdlCandidates(parsed, definition.schema());
        } catch (Exception ex) {
            result.warnings().add(DiagnosticWarnings.ddlTextParseFailed(definition.source(), definition.ddl(), ex));
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
            ScanConfig config,
            SqlStatementRecord statement,
            AdaptorContext context,
            ScanResult result,
            List<DataLineageCandidate> dataLineageCandidates,
            Set<TableId> knownPhysicalTables
    ) {
        try {
            if (!SqlLogNoiseFilter.shouldSkip(config, statement)) {
                sqlParserRunner.parseStructured(adaptor, config, statement, context).ifPresent(structured -> {
                    dataLineageCandidates.addAll(dataLineageExtractor.extract(statement, structured, knownPhysicalTables));
                });
            }
            return sqlParserRunner.parse(adaptor, config, statement, context);
        } catch (Exception ex) {
            result.warnings().add(DiagnosticWarnings.sqlParseFailed(statement, ex));
            return List.of();
        }
    }

    private Set<TableId> knownPhysicalTables(MetadataSnapshot metadataSnapshot) {
        if (metadataSnapshot == null) {
            return Set.of();
        }
        Set<TableId> tables = new LinkedHashSet<>(metadataSnapshot.tables());
        for (MetadataTableFact fact : metadataSnapshot.tableFacts()) {
            tables.add(TableId.of(fact.schema(), fact.tableName()));
        }
        return tables;
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

    private List<DatabaseDdlDefinition> collectDatabaseDdl(
            DatabaseAdaptor adaptor,
            Connection connection,
            ScanScope scope,
            ScanResult result
    ) {
        try {
            return adaptor.databaseDdlCollector()
                    .map(collector -> collector.collect(connection, scope, result.warnings()::add))
                    .orElse(List.of());
        } catch (Exception ex) {
            result.warnings().add(DiagnosticWarnings.objectCollectFailed(
                    "DATABASE_DDL_COLLECT_FAILED", "database-ddl", ex));
            return List.of();
        }
    }

    private java.util.Map<String, Object> objectAttributes(DatabaseObjectDefinition definition) {
        java.util.Map<String, Object> attributes = new java.util.LinkedHashMap<>();
        attributes.put("objectSchema", definition.schema());
        attributes.put("objectName", definition.name());
        attributes.put("objectType", definition.type().name());
        attributes.put("objectDefinitionSource", definition.source());
        if (definition.type() == com.relationdetector.api.Enums.DatabaseObjectType.PROCEDURE
                || definition.type() == com.relationdetector.api.Enums.DatabaseObjectType.FUNCTION) {
            attributes.put("routineSchema", definition.schema());
            attributes.put("routineName", definition.name());
            attributes.put("routineType", definition.type().name());
        }
        return attributes;
    }

    private String objectSourceName(DatabaseObjectDefinition definition) {
        if (definition.schema() == null || definition.schema().isBlank()) {
            return definition.name();
        }
        return definition.schema() + "." + definition.name();
    }

    private List<RelationshipCandidate> qualifyDatabaseDdlCandidates(
            List<RelationshipCandidate> candidates,
            String schema
    ) {
        if (schema == null || schema.isBlank()) {
            return candidates;
        }
        return candidates.stream()
                .map(candidate -> qualifyDatabaseDdlCandidate(candidate, schema))
                .toList();
    }

    private RelationshipCandidate qualifyDatabaseDdlCandidate(RelationshipCandidate candidate, String schema) {
        RelationshipCandidate qualified = new RelationshipCandidate(
                qualifyEndpoint(candidate.source(), schema),
                qualifyEndpoint(candidate.target(), schema),
                candidate.relationType(),
                candidate.relationSubType());
        qualified.confidence(candidate.confidence());
        qualified.evidence().addAll(candidate.evidence());
        qualified.rawEvidence().addAll(candidate.rawEvidence());
        qualified.warnings().addAll(candidate.warnings());
        return qualified;
    }

    private Endpoint qualifyEndpoint(Endpoint endpoint, String schema) {
        if (endpoint.table().schema() != null && !endpoint.table().schema().isBlank()) {
            return endpoint;
        }
        TableId table = TableId.of(schema, endpoint.table().tableName());
        if (!endpoint.isColumnLevel()) {
            return Endpoint.table(table);
        }
        ColumnRef column = endpoint.column();
        return Endpoint.column(new ColumnRef(table, column.columnName(), column.normalizedName(),
                column.dataType(), column.nullable()));
    }
}
