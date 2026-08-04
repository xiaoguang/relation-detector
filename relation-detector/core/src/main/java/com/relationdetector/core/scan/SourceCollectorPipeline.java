package com.relationdetector.core.scan;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Stream;

import com.relationdetector.contracts.Enums.DatabaseObjectType;
import com.relationdetector.contracts.Enums.MetadataInventoryStatus;
import com.relationdetector.contracts.Enums.MetadataInventoryBasis;
import com.relationdetector.contracts.Enums.DdlInventoryCoverage;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.DatabaseDdlDefinition;
import com.relationdetector.contracts.parse.DatabaseObjectDefinition;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.LiveSourceConfigurationException;
import com.relationdetector.core.diagnostics.DiagnosticWarnings;
import com.relationdetector.core.diagnostics.LiveDiagnosticSanitizer;
import com.relationdetector.core.parser.runtime.ParserBundle;
import com.relationdetector.core.script.ScriptFileExtractor;
import com.relationdetector.core.adaptor.AdaptorContractException;
import com.relationdetector.core.adaptor.AdaptorParseResultContractValidator;
import com.relationdetector.core.adaptor.AdaptorResultContractValidator;
import com.relationdetector.core.config.SourceConfig;
import com.relationdetector.core.execution.StatementDispatchService;
import com.relationdetector.core.execution.StatementExecutionOutcome;
import com.relationdetector.core.metadata.DdlMetadataInventoryAssembler;
import com.relationdetector.core.result.MetadataInventory;

/**
 * CN: 按 metadata、DDL、object 与 log source 边界编排采集，对 live definition 做脱敏防御后才交给 statement parser。
 * EN: Orchestrates metadata, DDL, object, and log collection and sanitizes live definitions before delegating them to statement parsing.
 */
public final class SourceCollectorPipeline {
    private final ScriptFileExtractor scriptFileExtractor = new ScriptFileExtractor();
    private final StatementDispatchService statementDispatch = new StatementDispatchService();
    private final AdaptorResultContractValidator resultContractValidator =
            new AdaptorResultContractValidator();
    private final AdaptorParseResultContractValidator parseResultContractValidator =
            new AdaptorParseResultContractValidator();
    private final DdlMetadataInventoryAssembler ddlMetadataInventoryAssembler =
            new DdlMetadataInventoryAssembler();
    private final StatementParsePipeline statementParser;

    SourceCollectorPipeline(StatementParsePipeline statementParser) {
        this.statementParser = statementParser;
    }

    /**
     * CN: 从已打开的受信数据库连接中按配置收集 metadata、live DDL、object definition 与 log，
     * 并在任何 parser outcome 合并前完成原子契约校验与 live diagnostic 脱敏。
     * EN: Collects configured metadata, live DDL, object definitions, and logs from an already-open trusted
     * connection, atomically validating outcomes and sanitizing live diagnostics before any merge.
     */
    void collectJdbcSources(Connection connection, ScanPipelineContext ctx) {
        SourceConfig sources = ctx.config.sources();
        if (sources.metadataEnabled() && connection != null) {
            collectMetadata(connection, ctx);
        }

        if (sources.ddlEnabled() && sources.ddlFromDatabase() && connection != null) {
            ctx.result.sources().add("database-ddl");
            ParserBundle parserBundle = parserBundle(ctx);
            execute(ctx, collectDatabaseDdl(connection, ctx).stream()
                    .map(definition -> new ParseTask(
                            context -> statementParser.executeDatabaseDdl(
                                    parserBundle, ctx.adaptor, ctx.parserConfig, definition, context),
                            error -> DiagnosticWarnings.ddlTextParseFailed(
                                    definition.source(), definition.ddl(), error),
                            LiveParserDiagnosticPolicy.databaseDdl(
                                    liveSourceName(definition.catalog(), definition.schema(), definition.name()),
                                    databaseDdlDiagnosticAttributes(definition))))
                    .toList());
        }

        if (sources.objectsEnabled() && sources.objectsFromDatabase() && connection != null) {
            ctx.result.sources().add("database-objects");
            Set<TableId> databaseKnownPhysicalTables =
                    statementParser.knownPhysicalTables(ctx.physicalInventorySnapshot);
            ParserBundle parserBundle = parserBundle(ctx);
            execute(ctx, collectDatabaseObjects(connection, ctx).stream()
                    .map(definition -> {
                        SqlStatementRecord statement = objectStatement(definition);
                        return new ParseTask(
                                context -> statementParser.executeStatement(parserBundle, ctx.adaptor, ctx.parserConfig,
                                        statement, context, databaseKnownPhysicalTables),
                                error -> DiagnosticWarnings.sqlParseFailed(statement, error),
                                LiveParserDiagnosticPolicy.object(
                                        objectSourceName(definition),
                                        objectDiagnosticAttributes(definition)));
                    })
                    .toList());
        }

    }

    private void collectMetadata(Connection connection, ScanPipelineContext ctx) {
        ctx.result.sources().add("metadata");
        try {
            var raw = ctx.adaptor.collectors().metadata().orElseThrow()
                    .collect(connection, ctx.scope);
            MetadataSnapshot validated = resultContractValidator.validateMetadata(raw);
            ctx.metadataSnapshot = validated;
            ctx.physicalInventorySnapshot = validated;
            ctx.result.metadataInventory(MetadataInventory.from(
                    validated.warnings().isEmpty()
                            ? MetadataInventoryStatus.COMPLETE
                            : MetadataInventoryStatus.PARTIAL,
                    MetadataInventoryBasis.LIVE_METADATA,
                    ctx.scope,
                    validated));
            ctx.relationshipCandidates.addAll(validated.relationships());
            ctx.result.warnings().addAll(validated.warnings());
        } catch (LiveSourceConfigurationException | AdaptorContractException ex) {
            throw ex;
        } catch (Exception ex) {
            ctx.result.metadataInventory(MetadataInventory.empty(
                    MetadataInventoryStatus.UNAVAILABLE,
                    MetadataInventoryBasis.LIVE_METADATA,
                    ctx.scope));
            ctx.result.warnings().add(LiveDiagnosticSanitizer.jdbcWarning(
                    "METADATA_COLLECT_FAILED", LiveDiagnosticSanitizer.Operation.METADATA,
                    "metadata", ex, java.util.Map.of(), ctx.adaptor.permissionDeniedVendorCodes()));
        }
    }

    /**
     * CN: 对配置中的 DDL、对象和日志文件执行 framing、typed dispatch 与 provenance 汇总，并写入当前 scan context；
     * 本方法不打开 JDBC，也不运行 derived inference。
     * EN: Frames and dispatches configured DDL, object, and log files and adds their provenance to the scan context;
     * it does not open JDBC connections or run derived inference.
     */
    void collectFileSources(ScanPipelineContext ctx) {
        SourceConfig sources = ctx.config.sources();
        if (sources.ddlEnabled()) {
            ctx.result.sources().add("ddl");
            ParserBundle parserBundle = parserBundle(ctx);
            List<ParseTask> ddlTasks = new ArrayList<>();
            List<SqlStatementRecord> queryStatements = new ArrayList<>();
            for (Path file : sources.ddlFiles()) {
                List<SqlStatementRecord> statements = scriptFileExtractor.extract(
                                file, StatementSourceType.DDL_FILE, ctx.adaptor.parsers().scriptFramer(),
                                ctx.result.warnings()::add)
                        .toList();
                StatementDispatchService.DdlFileDispatch dispatch = statementDispatch.dispatchDdlFile(
                        statements, ctx.config.database().databaseType());
                if (!dispatch.ddlStatements().isEmpty()) {
                    ddlTasks.add(new ParseTask(
                            context -> statementParser.executeDdlStatements(
                                    parserBundle, ctx.adaptor, ctx.parserConfig, dispatch.ddlStatements(), context),
                            error -> DiagnosticWarnings.ddlParseFailed(file, error),
                            ddlCoverageImpact(ctx)));
                }
                queryStatements.addAll(dispatch.queryStatements());
            }
            execute(ctx, ddlTasks);
            refreshDdlInventory(ctx);
            Set<TableId> fileKnownPhysicalTables =
                    statementParser.knownPhysicalTables(ctx.physicalInventorySnapshot);
            execute(ctx, queryStatements.stream()
                    .map(query -> new ParseTask(
                            context -> statementParser.executeStatement(
                                    parserBundle, ctx.adaptor, ctx.parserConfig,
                                    query, context, fileKnownPhysicalTables),
                            error -> DiagnosticWarnings.sqlParseFailed(query, error)))
                    .toList());
        }

        if (sources.objectsEnabled()) {
            ctx.result.sources().add("object-files");
            ParserBundle parserBundle = parserBundle(ctx);
            List<SqlStatementRecord> statements = new ArrayList<>();
            for (Path file : sources.objectFiles()) {
                scriptFileExtractor.extract(file, StatementSourceType.PROCEDURE, ctx.adaptor.parsers().scriptFramer(),
                                ctx.result.warnings()::add)
                        .forEach(statements::add);
            }
            collectEmbeddedDdlDeclarations(ctx, parserBundle, statements);
            Set<TableId> fileKnownPhysicalTables =
                    statementParser.knownPhysicalTables(ctx.physicalInventorySnapshot);
            List<ParseTask> tasks = statements.stream()
                    .filter(statement -> statement.sourceType() != StatementSourceType.DDL_FILE)
                    .map(statement -> new ParseTask(
                                context -> statementParser.executeStatement(parserBundle, ctx.adaptor, ctx.parserConfig,
                                        statement, context, fileKnownPhysicalTables),
                                error -> DiagnosticWarnings.sqlParseFailed(statement, error)))
                    .toList();
            execute(ctx, tasks);
        }

        if (sources.logsEnabled()) {
            ctx.result.sources().add("logs");
            ParserBundle parserBundle = parserBundle(ctx);
            List<SqlStatementRecord> statements = new ArrayList<>();
            for (Path file : sources.logFiles()) {
                extractLog(file, ctx).forEach(statements::add);
            }
            collectEmbeddedDdlDeclarations(ctx, parserBundle, statements);
            Set<TableId> logKnownPhysicalTables =
                    statementParser.knownPhysicalTables(ctx.physicalInventorySnapshot);
            List<ParseTask> tasks = statements.stream()
                    .filter(statement -> statement.sourceType() != StatementSourceType.DDL_FILE)
                    .map(statement -> new ParseTask(
                                context -> statementParser.executeStatement(parserBundle, ctx.adaptor, ctx.parserConfig,
                                        statement, context, logKnownPhysicalTables),
                                error -> DiagnosticWarnings.sqlParseFailed(statement, error)))
                    .toList();
            execute(ctx, tasks);
        }
    }

    private void collectEmbeddedDdlDeclarations(
            ScanPipelineContext ctx,
            ParserBundle parserBundle,
            List<SqlStatementRecord> statements
    ) {
        if (ctx.config.sources().ddlInventoryCoverage() != DdlInventoryCoverage.COMPLETE_SCOPE) {
            return;
        }
        execute(ctx, statements.stream()
                .filter(statement -> statement.sourceType() == StatementSourceType.DDL_FILE)
                .map(statement -> new ParseTask(
                        context -> statementParser.executeDdlStatements(
                                parserBundle, ctx.adaptor, ctx.parserConfig, List.of(statement), context),
                        error -> DiagnosticWarnings.ddlTextParseFailed(
                                statement.sourceName(), statement.sql(), error),
                        DdlCoverageImpact.DDL_DECLARATION))
                .toList());
        refreshDdlInventory(ctx);
    }

    private void refreshDdlInventory(ScanPipelineContext ctx) {
        if (ctx.config.sources().ddlInventoryCoverage() != DdlInventoryCoverage.COMPLETE_SCOPE) {
            return;
        }
        MetadataInventory inventory = ddlMetadataInventoryAssembler.assemble(
                ctx.result.metadataInventory(), ctx.ddlCatalogInventory, ctx.scope);
        ctx.result.metadataInventory(inventory);
        // DDL declarations establish physical identity for parser filtering, but they are
        // not live metadata evidence and must not run through metadata enhancement again.
        ctx.physicalInventorySnapshot = ddlMetadataInventoryAssembler.snapshot(inventory);
    }

    private DdlCoverageImpact ddlCoverageImpact(ScanPipelineContext ctx) {
        return ctx.config.sources().ddlInventoryCoverage() == DdlInventoryCoverage.COMPLETE_SCOPE
                ? DdlCoverageImpact.DDL_DECLARATION
                : DdlCoverageImpact.NONE;
    }

    private ParserBundle parserBundle(ScanPipelineContext ctx) {
        return statementParser.selectedBundle(ctx.adaptor, ctx.parserConfig, ctx.adaptorContext, ctx);
    }

    private void execute(ScanPipelineContext ctx, List<ParseTask> tasks) {
        if (tasks.isEmpty()) {
            return;
        }
        List<TaskResult> results = ctx.config.execution().parallelism() <= 1 || tasks.size() == 1
                ? tasks.stream().map(task -> executeTask(ctx, task)).toList()
                : executeInParallel(ctx, tasks);
        results.forEach(result -> merge(ctx, result));
    }

    private List<TaskResult> executeInParallel(ScanPipelineContext ctx, List<ParseTask> tasks) {
        List<Callable<TaskResult>> calls = tasks.stream()
                .<Callable<TaskResult>>map(task -> () -> executeTask(ctx, task))
                .toList();
        return ctx.taskExecutor.invokeAll(calls);
    }

    private TaskResult executeTask(ScanPipelineContext ctx, ParseTask task) {
        List<WarningMessage> warnings = new ArrayList<>();
        AdaptorContext context = new AdaptorContext(ctx.scope, ctx.adaptorContext.options(), warnings::add);
        try {
            StatementExecutionOutcome outcome = task.action().execute(context);
            if (task.diagnosticPolicy() == null) {
                return new TaskResult(outcome, warnings);
            }
            return new TaskResult(
                    task.diagnosticPolicy().sanitizeOutcome(outcome),
                    task.diagnosticPolicy().sanitizeWarnings(warnings));
        } catch (AdaptorContractException ex) {
            throw ex;
        } catch (Exception ex) {
            WarningMessage failure = task.failureWarning().apply(ex);
            if (task.diagnosticPolicy() == null) {
                warnings.add(failure);
            } else {
                List<WarningMessage> sanitized = new ArrayList<>(
                        task.diagnosticPolicy().sanitizeWarnings(warnings));
                sanitized.add(task.diagnosticPolicy().sanitizeFailure(failure, ex));
                warnings = sanitized;
            }
            StatementExecutionOutcome outcome = StatementExecutionOutcome.empty();
            if (task.coverageImpact() == DdlCoverageImpact.DDL_DECLARATION) {
                outcome.ddlCatalogInventory().recordDeclarationParseFailure();
            }
            return new TaskResult(outcome, warnings);
        }
    }

    private void merge(ScanPipelineContext ctx, TaskResult result) {
        StatementExecutionOutcome outcome = result.outcome();
        ctx.relationshipCandidates.addAll(outcome.relationshipCandidates());
        ctx.lineageCandidates.addAll(outcome.lineageCandidates());
        ctx.namingEvidencePool.addAll(outcome.namingEvidence());
        ctx.ddlEvidenceInventory.merge(outcome.ddlEvidenceInventory());
        ctx.ddlCatalogInventory.merge(outcome.ddlCatalogInventory());
        ctx.result.warnings().addAll(outcome.warnings());
        ctx.result.warnings().addAll(result.warnings());
    }

    private Stream<SqlStatementRecord> extractLog(Path file, ScanPipelineContext ctx) {
        try {
            List<WarningMessage> callbackWarnings = new ArrayList<>();
            Stream<SqlStatementRecord> raw = ctx.adaptor.collectors().logs().orElseThrow()
                    .extract(file, ctx.config.sources().logFormatHint(), callbackWarnings::add);
            if (raw == null) {
                throw new AdaptorContractException("adaptor parse-result contract violation: log stream is null");
            }
            List<SqlStatementRecord> statements;
            try (raw) {
                statements = raw.toList();
            }
            var validated = parseResultContractValidator.validateLog(file, statements, callbackWarnings);
            ctx.result.warnings().addAll(validated.warnings());
            return validated.statements().stream();
        } catch (AdaptorContractException ex) {
            throw ex;
        } catch (Exception ex) {
            ctx.result.warnings().add(DiagnosticWarnings.logExtractFailed(file, ex));
            return Stream.empty();
        }
    }

    private List<DatabaseObjectDefinition> collectDatabaseObjects(Connection connection, ScanPipelineContext ctx) {
        try {
            List<WarningMessage> callbackWarnings = new ArrayList<>();
            List<DatabaseObjectDefinition> definitions = ctx.adaptor.collectors().objects().orElseThrow()
                    .collect(connection, ctx.scope, callbackWarnings::add);
            var validated = resultContractValidator.validateObjects(definitions, callbackWarnings);
            ctx.result.warnings().addAll(validated.warnings());
            return validated.definitions();
        } catch (LiveSourceConfigurationException | AdaptorContractException ex) {
            throw ex;
        } catch (Exception ex) {
            ctx.result.warnings().add(DiagnosticWarnings.objectCollectFailed(
                    "OBJECT_DEFINITION_COLLECT_FAILED", "database-objects", ex,
                    ctx.adaptor.permissionDeniedVendorCodes()));
            return List.of();
        }
    }

    private List<DatabaseDdlDefinition> collectDatabaseDdl(Connection connection, ScanPipelineContext ctx) {
        try {
            var collector = ctx.adaptor.collectors().databaseDdl();
            if (collector.isEmpty()) {
                return List.of();
            }
            List<WarningMessage> callbackWarnings = new ArrayList<>();
            List<DatabaseDdlDefinition> definitions = collector.orElseThrow()
                    .collect(connection, ctx.scope, callbackWarnings::add);
            var validated = resultContractValidator.validateDatabaseDdl(definitions, callbackWarnings);
            ctx.result.warnings().addAll(validated.warnings());
            return validated.definitions();
        } catch (LiveSourceConfigurationException | AdaptorContractException ex) {
            throw ex;
        } catch (Exception ex) {
            ctx.result.warnings().add(DiagnosticWarnings.databaseDdlCollectFailed(
                    "DATABASE_DDL_COLLECT_FAILED", "database-ddl", ex,
                    ctx.adaptor.permissionDeniedVendorCodes()));
            return List.of();
        }
    }

    private SqlStatementRecord objectStatement(DatabaseObjectDefinition definition) {
        long lineCount = Math.max(1L, definition.sql().lines().count());
        return new SqlStatementRecord(
                definition.sql(),
                objectSourceType(definition.type()),
                objectSourceName(definition),
                1,
                lineCount,
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
        if (definition.catalog() != null && !definition.catalog().isBlank()) {
            attributes.put("objectCatalog", definition.catalog());
            attributes.put("sourceCatalog", definition.catalog());
        }
        attributes.put("objectSchema", definition.schema());
        attributes.put("objectName", definition.name());
        attributes.put("objectType", definition.type().name());
        attributes.put("objectDefinitionSource", definition.source());
        attributes.put("sourceObjectType", switch (definition.type()) {
            case VIEW, MATERIALIZED_VIEW -> "QUERY";
            default -> definition.type().name();
        });
        attributes.put("sourceObjectName", objectSourceName(definition));
        attributes.put("sourceObjectIdentity", objectSourceName(definition));
        attributes.put("sourceStatementId", objectSourceName(definition));
        if (definition.type() == DatabaseObjectType.PROCEDURE || definition.type() == DatabaseObjectType.FUNCTION) {
            attributes.put("routineSchema", definition.schema());
            attributes.put("routineName", definition.name());
            attributes.put("routineType", definition.type().name());
        }
        return attributes;
    }

    private java.util.Map<String, Object> objectDiagnosticAttributes(DatabaseObjectDefinition definition) {
        java.util.Map<String, Object> attributes = new java.util.LinkedHashMap<>();
        putIfPresent(attributes, "objectCatalog", definition.catalog());
        putIfPresent(attributes, "objectSchema", definition.schema());
        putIfPresent(attributes, "objectName", definition.name());
        attributes.put("objectType", definition.type().name());
        return attributes;
    }

    private java.util.Map<String, Object> databaseDdlDiagnosticAttributes(DatabaseDdlDefinition definition) {
        java.util.Map<String, Object> attributes = new java.util.LinkedHashMap<>();
        putIfPresent(attributes, "objectCatalog", definition.catalog());
        putIfPresent(attributes, "objectSchema", definition.schema());
        putIfPresent(attributes, "objectName", definition.name());
        attributes.put("objectType", "TABLE");
        return attributes;
    }

    private void putIfPresent(java.util.Map<String, Object> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }

    private String objectSourceName(DatabaseObjectDefinition definition) {
        return liveSourceName(definition.catalog(), definition.schema(), definition.name());
    }

    private String liveSourceName(String catalog, String schema, String name) {
        String schemaName = schema == null || schema.isBlank() ? name : schema + "." + name;
        return catalog == null || catalog.isBlank() ? schemaName : catalog + "." + schemaName;
    }

    @FunctionalInterface
    private interface ParseAction {
        StatementExecutionOutcome execute(AdaptorContext context);
    }

    private record ParseTask(
            ParseAction action,
            Function<Exception, WarningMessage> failureWarning,
            DdlCoverageImpact coverageImpact,
            LiveParserDiagnosticPolicy diagnosticPolicy
    ) {
        private ParseTask(ParseAction action, Function<Exception, WarningMessage> failureWarning) {
            this(action, failureWarning, DdlCoverageImpact.NONE, null);
        }

        private ParseTask(
                ParseAction action,
                Function<Exception, WarningMessage> failureWarning,
                LiveParserDiagnosticPolicy diagnosticPolicy
        ) {
            this(action, failureWarning, DdlCoverageImpact.NONE, diagnosticPolicy);
        }

        private ParseTask(
                ParseAction action,
                Function<Exception, WarningMessage> failureWarning,
                DdlCoverageImpact coverageImpact
        ) {
            this(action, failureWarning, coverageImpact, null);
        }
    }

    private enum DdlCoverageImpact {
        NONE,
        DDL_DECLARATION
    }

    private record TaskResult(StatementExecutionOutcome outcome, List<WarningMessage> warnings) {
    }
}
