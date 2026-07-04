package com.relationdetector.core.scan;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.relationdetector.contracts.Enums.DatabaseObjectType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.DatabaseDdlDefinition;
import com.relationdetector.contracts.parse.DatabaseObjectDefinition;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.core.diagnostics.DiagnosticWarnings;
import com.relationdetector.core.log.ObjectSqlFileExtractor;
import com.relationdetector.core.log.PlainSqlLogExtractor;

final class SourceCollectorPipeline {
    private final PlainSqlLogExtractor plainExtractor = new PlainSqlLogExtractor();
    private final ObjectSqlFileExtractor objectExtractor = new ObjectSqlFileExtractor();
    private final StatementParsePipeline statementParser;

    SourceCollectorPipeline(StatementParsePipeline statementParser) {
        this.statementParser = statementParser;
    }

    void collectJdbcSources(Connection connection, ScanPipelineContext ctx) {
        if (ctx.config.metadataEnabled && connection != null) {
            ctx.result.sources().add("metadata");
            ctx.metadataSnapshot = ctx.adaptor.collectors().metadata().collect(connection, ctx.scope);
            ctx.relationshipCandidates.addAll(ctx.metadataSnapshot.relationships());
            ctx.result.warnings().addAll(ctx.metadataSnapshot.warnings());
        }

        if (ctx.config.ddlEnabled && ctx.config.ddlFromDatabase && connection != null) {
            ctx.result.sources().add("database-ddl");
            for (DatabaseDdlDefinition definition : collectDatabaseDdl(connection, ctx)) {
                ctx.relationshipCandidates.addAll(statementParser.parseDatabaseDdl(
                        ctx.adaptor, ctx.config, definition, ctx.adaptorContext, ctx));
            }
        }

        if (ctx.config.objectsEnabled && ctx.config.objectsFromDatabase && connection != null) {
            ctx.result.sources().add("database-objects");
            Set<TableId> databaseKnownPhysicalTables = statementParser.knownPhysicalTables(ctx.metadataSnapshot);
            for (DatabaseObjectDefinition definition : collectDatabaseObjects(connection, ctx)) {
                ctx.relationshipCandidates.addAll(statementParser.parseStatement(
                        ctx.adaptor,
                        ctx.config,
                        objectStatement(definition),
                        ctx.adaptorContext,
                        ctx.result,
                        ctx,
                        ctx.lineageCandidates,
                        databaseKnownPhysicalTables));
            }
        }

        if (ctx.config.dataProfileEnabled && connection != null) {
            ctx.result.sources().add("data-profile");
            int profiled = 0;
            for (RelationshipCandidate candidate : new java.util.ArrayList<>(ctx.relationshipCandidates)) {
                if (profiled++ >= ctx.config.maxCandidatePairs) {
                    break;
                }
                ctx.adaptor.profiling().dataProfiler().ifPresent(profiler ->
                        candidate.evidence().addAll(profiler.profile(connection,
                                new ProfileRequest(candidate, ctx.config.sampleRows, ctx.config.timeoutSeconds))));
            }
        }
    }

    void collectFileSources(ScanPipelineContext ctx) {
        if (ctx.config.ddlEnabled) {
            ctx.result.sources().add("ddl");
            for (Path file : ctx.config.ddlFiles) {
                ctx.relationshipCandidates.addAll(statementParser.parseDdlFile(
                        ctx.adaptor, ctx.config, file, ctx.adaptorContext, ctx));
            }
        }

        if (ctx.config.objectsEnabled) {
            ctx.result.sources().add("object-files");
            Set<TableId> fileKnownPhysicalTables = statementParser.knownPhysicalTables(ctx.metadataSnapshot);
            for (Path file : ctx.config.objectFiles) {
                objectExtractor.extract(file, StatementSourceType.PROCEDURE, ctx.config.databaseType,
                                ctx.result.warnings()::add)
                        .forEach(statement -> ctx.relationshipCandidates.addAll(statementParser.parseStatement(
                                ctx.adaptor, ctx.config, statement, ctx.adaptorContext, ctx.result,
                                ctx,
                                ctx.lineageCandidates, fileKnownPhysicalTables)));
            }
        }

        if (ctx.config.logsEnabled) {
            ctx.result.sources().add("logs");
            Set<TableId> logKnownPhysicalTables = statementParser.knownPhysicalTables(ctx.metadataSnapshot);
            for (Path file : ctx.config.logFiles) {
                extractLog(file, ctx)
                        .forEach(statement -> ctx.relationshipCandidates.addAll(statementParser.parseStatement(
                                ctx.adaptor, ctx.config, statement, ctx.adaptorContext, ctx.result,
                                ctx,
                                ctx.lineageCandidates, logKnownPhysicalTables)));
            }
        }
    }

    private Stream<SqlStatementRecord> extractLog(Path file, ScanPipelineContext ctx) {
        try {
            return ctx.adaptor.collectors().logs().extract(file, ctx.config.logFormatHint, ctx.result.warnings()::add);
        } catch (Exception ex) {
            ctx.result.warnings().add(DiagnosticWarnings.logExtractFailed(file, ex));
            return Stream.empty();
        }
    }

    private List<DatabaseObjectDefinition> collectDatabaseObjects(Connection connection, ScanPipelineContext ctx) {
        try {
            return ctx.adaptor.collectors().objects().collect(connection, ctx.scope, ctx.result.warnings()::add);
        } catch (Exception ex) {
            ctx.result.warnings().add(DiagnosticWarnings.objectCollectFailed(
                    "OBJECT_DEFINITION_COLLECT_FAILED", "database-objects", ex));
            return List.of();
        }
    }

    private List<DatabaseDdlDefinition> collectDatabaseDdl(Connection connection, ScanPipelineContext ctx) {
        try {
            return ctx.adaptor.collectors().databaseDdl()
                    .map(collector -> collector.collect(connection, ctx.scope, ctx.result.warnings()::add))
                    .orElse(List.of());
        } catch (Exception ex) {
            ctx.result.warnings().add(DiagnosticWarnings.objectCollectFailed(
                    "DATABASE_DDL_COLLECT_FAILED", "database-ddl", ex));
            return List.of();
        }
    }

    private SqlStatementRecord objectStatement(DatabaseObjectDefinition definition) {
        return new SqlStatementRecord(
                definition.sql(),
                objectSourceType(definition.type()),
                objectSourceName(definition),
                0,
                0,
                objectAttributes(definition));
    }

    private StatementSourceType objectSourceType(DatabaseObjectType type) {
        return switch (type) {
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
    }

    private java.util.Map<String, Object> objectAttributes(DatabaseObjectDefinition definition) {
        java.util.Map<String, Object> attributes = new java.util.LinkedHashMap<>();
        attributes.put("objectSchema", definition.schema());
        attributes.put("objectName", definition.name());
        attributes.put("objectType", definition.type().name());
        attributes.put("objectDefinitionSource", definition.source());
        if (definition.type() == DatabaseObjectType.PROCEDURE || definition.type() == DatabaseObjectType.FUNCTION) {
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
}
