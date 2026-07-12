package com.relationdetector.cli;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.core.common.CommonDatabaseAdaptor;
import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.parser.ParserBundle;
import com.relationdetector.core.parser.ParserBundleSelector;
import com.relationdetector.core.naming.NamingEvidencePool;
import com.relationdetector.core.scan.EvidenceEnhancementService;
import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.scan.StatementExecutionOutcome;
import com.relationdetector.core.scan.StatementExecutionService;
import com.relationdetector.core.tokenevent.CommonTokenEventStructuredSqlParser;
import com.relationdetector.core.tokenevent.TokenEventStructuredDdlParser;
import com.relationdetector.mysql.MySqlDatabaseAdaptor;
import com.relationdetector.oracle.OracleDatabaseAdaptor;
import com.relationdetector.postgres.PostgresDatabaseAdaptor;
import com.relationdetector.sqlserver.SqlServerDatabaseAdaptor;

final class FixtureExecutionEngine {
    private static final Set<TableId> NO_KNOWN_PHYSICAL_TABLES = Collections.emptySet();

    private final FixtureInputLoader inputLoader;
    /** A worker-local production execution service; parser state remains per statement. */
    private final ThreadLocal<StatementExecutionService> statementExecutionServices =
            ThreadLocal.withInitial(StatementExecutionService::new);
    private final EvidenceEnhancementService evidenceEnhancementService = new EvidenceEnhancementService();
    /**
     * Parser wrappers are immutable: each parse still creates its own generated
     * lexer, parser, visitor, and per-parse state. Reusing the selected wrapper
     * avoids selecting the same profile and rebuilding dialect adaptors for every
     * fixture in a full correctness run.
     */
    private final ConcurrentMap<ParserRuntimeKey, ParserRuntime> parserRuntimes = new ConcurrentHashMap<>();

    FixtureExecutionEngine(FixtureInputLoader inputLoader) {
        this.inputLoader = inputLoader;
    }

    FixtureActualResult execute(CorrectnessFixture fixture, LoadedFixtureInput input) {
        return switch (fixture.parserTarget()) {
            case "SQL" -> executeSqlFixture(fixture, input);
            case "DDL" -> executeDdlFixture(fixture, input);
            default -> throw new IllegalArgumentException(
                    "Unknown parserTarget " + fixture.parserTarget() + " in " + fixture.path());
        };
    }

    private FixtureActualResult executeSqlFixture(CorrectnessFixture fixture, LoadedFixtureInput input) {
        ParserRuntime runtime = parserRuntime(fixture);
        DatabaseAdaptor adaptor = runtime.adaptor();
        ScanConfig config = config(fixture);
        List<WarningMessage> warnings = new ArrayList<>();
        AdaptorContext context = context(fixture, warnings);
        List<SqlStatementRecord> statements = inputLoader.sqlStatements(
                fixture, input.input(), warnings, adaptor.parsers().scripts());
        List<RelationshipCandidate> relationships = new ArrayList<>();
        List<DataLineageCandidate> lineages = new ArrayList<>();
        NamingEvidencePool namingEvidencePool = new NamingEvidencePool();

        StructuredSqlParser commonParser = runtime.commonSqlParser();
        ParserBundle parserBundle = runtime.parserBundle();
        for (SqlTaskResult taskResult : executeSqlStatements(
                fixture, statements, adaptor, config, commonParser, parserBundle)) {
            addSqlOutcome(taskResult.outcome(), relationships, lineages, namingEvidencePool);
            warnings.addAll(taskResult.warnings());
        }

        evidenceEnhancementService.enhance(relationships, namingEvidencePool, null, config);
        return new FixtureActualResult(relationships, lineages, namingEvidencePool.merged(), List.copyOf(warnings));
    }

    private FixtureActualResult executeDdlFixture(CorrectnessFixture fixture, LoadedFixtureInput input) {
        ParserRuntime runtime = parserRuntime(fixture);
        DatabaseAdaptor adaptor = runtime.adaptor();
        ScanConfig config = config(fixture);
        List<WarningMessage> warnings = new ArrayList<>();
        AdaptorContext context = context(fixture, warnings);
        ParserBundle parserBundle = runtime.parserBundle();
        List<SqlStatementRecord> statements = inputLoader.sqlStatements(
                fixture, input.input(), warnings, adaptor.parsers().scripts());
        StatementExecutionOutcome outcome = isCommonTokenEventFixture(fixture)
                ? statementExecutionServices.get().executeDdlStatements(
                        runtime.commonDdlParser(), statements,
                        fixture.evidenceSourceType(),
                        context,
                        config)
                : statementExecutionServices.get().executeDdlStatements(
                        parserBundle, statements, fixture.evidenceSourceType(), context, config);
        NamingEvidencePool namingEvidencePool = new NamingEvidencePool();
        namingEvidencePool.addAll(outcome.namingEvidence());
        return new FixtureActualResult(
                outcome.relationshipCandidates(),
                List.of(),
                namingEvidencePool.merged(),
                List.copyOf(warnings));
    }

    private void addSqlOutcome(
            StatementExecutionOutcome outcome,
            List<RelationshipCandidate> relationships,
            List<DataLineageCandidate> lineages,
            NamingEvidencePool namingEvidencePool
    ) {
        relationships.addAll(outcome.relationshipCandidates());
        lineages.addAll(outcome.lineageCandidates());
        namingEvidencePool.addAll(outcome.namingEvidence());
    }

    private List<SqlTaskResult> executeSqlStatements(
            CorrectnessFixture fixture,
            List<SqlStatementRecord> statements,
            DatabaseAdaptor adaptor,
            ScanConfig config,
            StructuredSqlParser commonParser,
            ParserBundle parserBundle
    ) {
        int parallelism = Math.min(statementParallelism(), statements.size());
        if (parallelism <= 1) {
            return statements.stream()
                    .map(statement -> executeSqlStatement(
                            fixture, statement, adaptor, config, commonParser, parserBundle))
                    .toList();
        }
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<SqlTaskResult>> futures = statements.stream()
                    .map(statement -> executor.submit(() -> executeSqlStatement(
                            fixture, statement, adaptor, config, commonParser, parserBundle)))
                    .toList();
            return futures.stream().map(this::getSqlTaskResult).toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private SqlTaskResult executeSqlStatement(
            CorrectnessFixture fixture,
            SqlStatementRecord statement,
            DatabaseAdaptor adaptor,
            ScanConfig config,
            StructuredSqlParser commonParser,
            ParserBundle parserBundle
    ) {
        List<WarningMessage> warnings = new ArrayList<>();
        AdaptorContext statementContext = context(fixture, warnings);
        StatementExecutionService service = statementExecutionServices.get();
        StatementExecutionOutcome outcome = commonParser != null
                ? service.executeSql(commonParser, statement, statementContext, NO_KNOWN_PHYSICAL_TABLES, config)
                : service.executeSql(adaptor, config, statement, statementContext, NO_KNOWN_PHYSICAL_TABLES, parserBundle);
        return new SqlTaskResult(outcome, warnings);
    }

    private SqlTaskResult getSqlTaskResult(Future<SqlTaskResult> future) {
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while executing correctness statement", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Correctness statement execution failed", ex.getCause());
        }
    }

    private int statementParallelism() {
        if (Boolean.getBoolean("updateCorrectnessGold")) {
            return 1;
        }
        return Math.max(1, Integer.getInteger("correctnessStatementParallelism", 1));
    }

    private boolean isCommonTokenEventFixture(CorrectnessFixture fixture) {
        return fixture.structuredParser().equals("common-token-event");
    }

    private ParserRuntime parserRuntime(CorrectnessFixture fixture) {
        ParserRuntimeKey key = new ParserRuntimeKey(
                fixture.databaseType(),
                fixture.parserMode(),
                fixture.grammarProfile(),
                fixture.databaseVersion(),
                fixture.structuredParser());
        return parserRuntimes.computeIfAbsent(key, ignored -> createParserRuntime(fixture));
    }

    private ParserRuntime createParserRuntime(CorrectnessFixture fixture) {
        DatabaseAdaptor adaptor = adaptor(fixture.databaseType());
        if (isCommonTokenEventFixture(fixture)) {
            return new ParserRuntime(
                    adaptor,
                    new CommonTokenEventStructuredSqlParser(),
                    new TokenEventStructuredDdlParser(SqlDialect.GENERIC),
                    null);
        }
        List<WarningMessage> selectionWarnings = new ArrayList<>();
        ParserBundle bundle = new ParserBundleSelector().select(
                adaptor,
                config(fixture),
                context(fixture, selectionWarnings));
        if (!selectionWarnings.isEmpty()) {
            throw new IllegalStateException("Correctness parser selection emitted warnings for "
                    + fixture.path() + ": " + selectionWarnings);
        }
        return new ParserRuntime(adaptor, null, null, bundle);
    }

    private DatabaseAdaptor adaptor(DatabaseType databaseType) {
        return switch (databaseType) {
            case COMMON -> new CommonDatabaseAdaptor();
            case MYSQL -> new MySqlDatabaseAdaptor();
            case POSTGRESQL -> new PostgresDatabaseAdaptor();
            case ORACLE -> new OracleDatabaseAdaptor();
            case SQLSERVER -> new SqlServerDatabaseAdaptor();
        };
    }

    private ScanConfig config(CorrectnessFixture fixture) {
        ScanConfig config = new ScanConfig();
        config.databaseType = fixture.databaseType();
        config.schema = fixture.schema();
        config.parserMode = fixture.parserMode();
        config.grammarProfile = fixture.grammarProfile();
        config.databaseVersion = fixture.databaseVersion();
        config.databaseVersionSource = fixture.databaseVersion().isBlank() ? "UNKNOWN" : "CONFIG";
        return config;
    }

    private AdaptorContext context(CorrectnessFixture fixture, List<WarningMessage> warnings) {
        return new AdaptorContext(
                new ScanScope(null, fixture.schema(), List.of(), List.of()),
                Map.of(),
                warnings::add);
    }

    private record SqlTaskResult(StatementExecutionOutcome outcome, List<WarningMessage> warnings) {
    }

    private record ParserRuntime(
            DatabaseAdaptor adaptor,
            StructuredSqlParser commonSqlParser,
            com.relationdetector.contracts.spi.Collectors.StructuredDdlParser commonDdlParser,
            ParserBundle parserBundle
    ) {
    }

    private record ParserRuntimeKey(
            DatabaseType databaseType,
            String parserMode,
            String grammarProfile,
            String databaseVersion,
            String structuredParser
    ) {
    }
}
