package com.relationdetector.cli;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.relationdetector.core.relation.NamingEvidencePool;
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
    private final StatementExecutionService statementExecutionService = new StatementExecutionService();
    private final EvidenceEnhancementService evidenceEnhancementService = new EvidenceEnhancementService();

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
        DatabaseAdaptor adaptor = adaptor(fixture.databaseType());
        ScanConfig config = config(fixture);
        List<WarningMessage> warnings = new ArrayList<>();
        AdaptorContext context = context(fixture, warnings);
        List<SqlStatementRecord> statements = inputLoader.sqlStatements(fixture, input.input(), warnings);
        List<RelationshipCandidate> relationships = new ArrayList<>();
        List<DataLineageCandidate> lineages = new ArrayList<>();
        NamingEvidencePool namingEvidencePool = new NamingEvidencePool();

        if (isCommonTokenEventFixture(fixture)) {
            StructuredSqlParser parser = new CommonTokenEventStructuredSqlParser();
            for (SqlStatementRecord statement : statements) {
                addSqlOutcome(statementExecutionService.executeSql(
                        parser, statement, context, NO_KNOWN_PHYSICAL_TABLES, config),
                        relationships,
                        lineages,
                        namingEvidencePool);
            }
        } else {
            for (SqlStatementRecord statement : statements) {
                addSqlOutcome(statementExecutionService.executeSql(
                        adaptor, config, statement, context, NO_KNOWN_PHYSICAL_TABLES),
                        relationships,
                        lineages,
                        namingEvidencePool);
            }
        }

        evidenceEnhancementService.enhance(relationships, namingEvidencePool, null, config);
        return new FixtureActualResult(relationships, lineages, namingEvidencePool.merged(), List.copyOf(warnings));
    }

    private FixtureActualResult executeDdlFixture(CorrectnessFixture fixture, LoadedFixtureInput input) {
        DatabaseAdaptor adaptor = adaptor(fixture.databaseType());
        ScanConfig config = config(fixture);
        List<WarningMessage> warnings = new ArrayList<>();
        AdaptorContext context = context(fixture, warnings);
        StatementExecutionOutcome outcome = isCommonTokenEventFixture(fixture)
                ? statementExecutionService.executeDdlText(
                        new TokenEventStructuredDdlParser(SqlDialect.GENERIC),
                        input.input(),
                        fixture.id() + ".ddl.sql",
                        fixture.evidenceSourceType(),
                        context,
                        config)
                : statementExecutionService.executeDdlText(adaptor, config, input.input(), fixture.id() + ".ddl.sql",
                        fixture.evidenceSourceType(), context);
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

    private boolean isCommonTokenEventFixture(CorrectnessFixture fixture) {
        return fixture.structuredParser().equals("common-token-event");
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
}
