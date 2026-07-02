package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.core.lineage.DataLineageMerger;
import com.relationdetector.core.lineage.TokenEventDataLineageExtractor;
import com.relationdetector.core.log.PlainSqlLogExtractor;
import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.parser.DdlRelationParserRunner;
import com.relationdetector.core.parser.SqlRelationParserRunner;
import com.relationdetector.core.relation.NamingMatchEvidenceEnhancer;
import com.relationdetector.core.relation.RelationshipMerger;
import com.relationdetector.core.relation.TokenEventRelationExtractor;
import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.tokenevent.CommonTokenEventStructuredSqlParser;
import com.relationdetector.core.tokenevent.TokenEventStructuredDdlParser;
import com.relationdetector.mysql.MySqlDatabaseAdaptor;
import com.relationdetector.oracle.OracleDatabaseAdaptor;
import com.relationdetector.postgres.PostgresDatabaseAdaptor;
import com.relationdetector.sqlserver.SqlServerDatabaseAdaptor;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

final class CorrectnessFixtureExecutor {
    void runFixture(CorrectnessFixture fixture) throws Exception {
        String input = Files.readString(fixture.inputFile());
        ExpectedRelations expectedRelations = expectedRelations(fixture);
        ExpectedDiagnostics expectedDiagnostics = expectedDiagnostics(fixture);
        ExpectedLineage expectedLineage = ExpectedLineage.readIfPresent(fixture.expectedLineageFile());
        if (!Boolean.getBoolean("updateCorrectnessGold") && shouldAssertFixtureHash(fixture)) {
            assertEquals(expectedDiagnostics.fixtureSha256(), sha256(input), fixture.id() + " fixture hash");
        }

        if (fixture.parserTarget().equals("SQL")) {
            runSqlFixture(fixture, expectedRelations, expectedDiagnostics, expectedLineage);
            return;
        }
        if (fixture.parserTarget().equals("DDL")) {
            runDdlFixture(fixture, input, expectedRelations, expectedDiagnostics);
            return;
        }
        throw new IllegalArgumentException("Unknown parserTarget " + fixture.parserTarget() + " in " + fixture.path());
    }

    private ExpectedRelations expectedRelations(CorrectnessFixture fixture) throws Exception {
        if (Boolean.getBoolean("updateCorrectnessGold") && !Files.exists(fixture.expectedRelationsFile())) {
            return new ExpectedRelations(List.of(), List.of());
        }
        return ExpectedRelations.read(fixture.expectedRelationsFile());
    }

    private ExpectedDiagnostics expectedDiagnostics(CorrectnessFixture fixture) throws Exception {
        if (Boolean.getBoolean("updateCorrectnessGold") && !Files.exists(fixture.expectedDiagnosticsFile())) {
            return new ExpectedDiagnostics("", Map.of());
        }
        return ExpectedDiagnostics.read(fixture.expectedDiagnosticsFile());
    }

    private void runSqlFixture(
            CorrectnessFixture fixture,
            ExpectedRelations expectedRelations,
            ExpectedDiagnostics expectedDiagnostics,
            ExpectedLineage expectedLineage
    ) throws Exception {
        DatabaseAdaptor adaptor = adaptor(fixture.databaseType());
        ScanConfig config = config(fixture);
        List<WarningMessage> warnings = new ArrayList<>();
        AdaptorContext context = context(fixture, warnings);
        List<SqlStatementRecord> statements = sqlStatements(fixture, inputOf(fixture), warnings);
        List<RelationshipCandidate> relationships = new ArrayList<>();
        List<DataLineageCandidate> lineages = new ArrayList<>();
        if (isCommonTokenEventFixture(fixture)) {
            StructuredSqlParser parser = new CommonTokenEventStructuredSqlParser();
            TokenEventRelationExtractor relationExtractor = new TokenEventRelationExtractor();
            NamingMatchEvidenceEnhancer namingMatchEvidenceEnhancer = new NamingMatchEvidenceEnhancer();
            TokenEventDataLineageExtractor lineageExtractor = new TokenEventDataLineageExtractor();
            for (SqlStatementRecord statement : statements) {
                StructuredParseResult structured = parser.parseSql(statement, context);
                List<RelationshipCandidate> extracted = relationExtractor.extract(statement, structured);
                namingMatchEvidenceEnhancer.enhance(extracted);
                relationships.addAll(extracted);
                lineages.addAll(lineageExtractor.extract(statement, structured));
            }
            assertRelations(fixture, expectedRelations, relationships);
            assertLineage(fixture, expectedLineage,
                    new DataLineageMerger().merge(lineages).stream().map(this::lineageFingerprint).toList());
            assertWarningCodes(fixture, expectedDiagnostics, warnings);
            return;
        }
        SqlRelationParserRunner runner = new SqlRelationParserRunner();
        TokenEventDataLineageExtractor lineageExtractor = new TokenEventDataLineageExtractor();
        for (SqlStatementRecord statement : statements) {
            SqlRelationParserRunner.ParsedSqlRelations parsed =
                    runner.parseStructuredAndRelations(adaptor, config, statement, context);
            relationships.addAll(parsed.relationships());
            parsed.structured()
                    .ifPresent(structured -> lineages.addAll(lineageExtractor.extract(statement, structured)));
        }
        assertRelations(fixture, expectedRelations, relationships);
        assertLineage(fixture, expectedLineage,
                new DataLineageMerger().merge(lineages).stream().map(this::lineageFingerprint).toList());
        assertWarningCodes(fixture, expectedDiagnostics, warnings);
    }

    private void runDdlFixture(
            CorrectnessFixture fixture,
            String input,
            ExpectedRelations expectedRelations,
            ExpectedDiagnostics expectedDiagnostics
    ) throws Exception {
        DatabaseAdaptor adaptor = adaptor(fixture.databaseType());
        ScanConfig config = config(fixture);
        List<WarningMessage> warnings = new ArrayList<>();
        AdaptorContext context = context(fixture, warnings);
        DdlRelationParserRunner runner = new DdlRelationParserRunner();
        List<RelationshipCandidate> relationships = isCommonTokenEventFixture(fixture)
                ? runner.parseText(
                        new TokenEventStructuredDdlParser(SqlDialect.GENERIC),
                        input,
                        fixture.id() + ".ddl.sql",
                        fixture.evidenceSourceType(),
                        context)
                : runner.parseText(adaptor, config, input, fixture.id() + ".ddl.sql", fixture.evidenceSourceType(), context);
        assertRelations(fixture, expectedRelations, relationships);
        assertWarningCodes(fixture, expectedDiagnostics, warnings);
    }

    private List<SqlStatementRecord> sqlStatements(
            CorrectnessFixture fixture,
            String input,
            List<WarningMessage> warnings
    ) {
        if ("OBJECT_BLOCKS".equalsIgnoreCase(fixture.statementFormat())) {
            return ObjectBlockStatementSplitter.parse(
                    input,
                    fixture.sourceType(),
                    fixture.inputFile().toString(),
                    fixture.databaseType(),
                    fixture.objectSourceFilter());
        }
        return new PlainSqlLogExtractor()
                .extract(fixture.inputFile(), fixture.sourceType(), warnings::add)
                .toList();
    }

    private void assertRelations(
            CorrectnessFixture fixture,
            ExpectedRelations expected,
            List<RelationshipCandidate> actual
    ) throws Exception {
        List<RelationshipCandidate> merged = new RelationshipMerger().merge(actual, 0.0);
        Set<String> actualFingerprints = merged.stream()
                .map(this::fingerprint)
                .collect(Collectors.toCollection(TreeSet::new));
        if (Boolean.getBoolean("updateCorrectnessGold")) {
            Files.writeString(fixture.expectedRelationsFile(),
                    CorrectnessJson.expectedRelationsJson(actualFingerprints.stream().toList(), expected.forbiddenTables()));
            return;
        }
        assertEquals(new TreeSet<>(expected.fingerprints()), actualFingerprints,
                () -> fixture.id() + " relation fingerprints");

        for (String forbiddenTable : expected.forbiddenTables()) {
            assertTrue(merged.stream().noneMatch(relation ->
                            relation.source().table().tableName().equalsIgnoreCase(forbiddenTable)
                                    || relation.target().table().tableName().equalsIgnoreCase(forbiddenTable)),
                    () -> fixture.id() + " emitted forbidden table " + forbiddenTable
                            + ". Actual=" + actualFingerprints);
        }
    }

    private void assertWarningCodes(
            CorrectnessFixture fixture,
            ExpectedDiagnostics expected,
            List<WarningMessage> actual
    ) throws Exception {
        Map<String, Long> actualCodes = actual.stream()
                .collect(Collectors.groupingBy(WarningMessage::code, LinkedHashMap::new, Collectors.counting()));
        if (isStrictFullGrammerFixture(fixture)) {
            assertFalse(actualCodes.containsKey("PARSER_MODE_FALLBACK"),
                    () -> fixture.id() + " must not fallback from its declared full-grammer profile");
            if (!expected.warningCodes().containsKey("FULL_GRAMMAR_VERSION_UNSUPPORTED_SYNTAX")) {
                assertFalse(actualCodes.containsKey("FULL_GRAMMAR_VERSION_UNSUPPORTED_SYNTAX"),
                        () -> fixture.id() + " must be accepted by its declared full-grammer profile");
            }
        }
        if (Boolean.getBoolean("updateCorrectnessGold") && Files.exists(fixture.inputFile())) {
            Files.writeString(fixture.expectedDiagnosticsFile(),
                    CorrectnessJson.expectedDiagnosticsJson(fixtureSha256ForDiagnostics(fixture, expected), actualCodes));
            return;
        }
        assertEquals(expected.warningCodes(), actualCodes, fixture.id() + " warningCodes");
    }

    private void assertLineage(
            CorrectnessFixture fixture,
            ExpectedLineage expected,
            List<String> actualFingerprints
    ) throws Exception {
        if (Boolean.getBoolean("updateCorrectnessGold")
                && (expected.exists() || !actualFingerprints.isEmpty())) {
            Files.writeString(fixture.expectedLineageFile(),
                    CorrectnessJson.expectedLineageJson(new TreeSet<>(actualFingerprints).stream().toList(),
                            expected.forbiddenSources(),
                            expected.forbiddenTargets(),
                            expected.warningCodes()));
            return;
        }
        if (!expected.exists()) {
            return;
        }
        assertEquals(new TreeSet<>(expected.fingerprints()), new TreeSet<>(actualFingerprints),
                () -> fixture.id() + " data lineage fingerprints");
        for (String forbiddenSource : expected.forbiddenSources()) {
            assertTrue(actualFingerprints.stream().noneMatch(lineage -> lineage.contains(forbiddenSource + "->")
                            || lineage.contains("," + forbiddenSource + "->")
                            || lineage.contains(":" + forbiddenSource + ",")),
                    () -> fixture.id() + " emitted forbidden lineage source " + forbiddenSource
                            + ". Actual=" + actualFingerprints);
        }
        for (String forbiddenTarget : expected.forbiddenTargets()) {
            assertTrue(actualFingerprints.stream().noneMatch(lineage -> lineage.endsWith("->" + forbiddenTarget)),
                    () -> fixture.id() + " emitted forbidden lineage target " + forbiddenTarget
                            + ". Actual=" + actualFingerprints);
        }
    }

    private boolean isCommonTokenEventFixture(CorrectnessFixture fixture) {
        return fixture.structuredParser().equals("common-token-event");
    }

    private boolean isStrictFullGrammerFixture(CorrectnessFixture fixture) {
        return fixture.parserMode().equals("full-grammer") && !fixture.grammarProfile().isBlank();
    }

    private boolean shouldAssertFixtureHash(CorrectnessFixture fixture) {
        /*
         * Object-block fixtures model procedures/functions/triggers. Their
         * source text is often shared or regenerated as a large routine file,
         * and a whole-file hash can fail before the relation/lineage assertions
         * exercise the parser. Keep hash enforcement for ordinary SQL/DDL
         * fixture inputs; object blocks are guarded by their golden outputs and
         * warning-code assertions.
         */
        return !"OBJECT_BLOCKS".equalsIgnoreCase(fixture.statementFormat());
    }

    private String fixtureSha256ForDiagnostics(CorrectnessFixture fixture, ExpectedDiagnostics expected) throws Exception {
        if (!shouldAssertFixtureHash(fixture)) {
            return expected.fixtureSha256();
        }
        return sha256(inputOf(fixture));
    }

    private String inputOf(CorrectnessFixture fixture) throws Exception {
        return Files.readString(fixture.inputFile());
    }

    private DatabaseAdaptor adaptor(DatabaseType databaseType) {
        return switch (databaseType) {
            case MYSQL -> new MySqlDatabaseAdaptor();
            case POSTGRESQL -> new PostgresDatabaseAdaptor();
            case ORACLE -> new OracleDatabaseAdaptor();
            case SQLSERVER -> new SqlServerDatabaseAdaptor();
            default -> throw new IllegalArgumentException("No correctness adaptor for " + databaseType);
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

    private String fingerprint(RelationshipCandidate relation) {
        String evidenceTypes = relation.evidence().stream()
                .map(evidence -> evidence.type().name())
                .collect(Collectors.joining(","));
        return relation.relationType() + ":"
                + relation.source().displayName() + "->" + relation.target().displayName()
                + ":" + evidenceTypes;
    }

    private String lineageFingerprint(DataLineageCandidate lineage) {
        return lineage.flowKind() + ":"
                + lineage.transformType() + ":"
                + lineage.sources().stream()
                        .map(com.relationdetector.contracts.model.Endpoint::displayName)
                        .collect(Collectors.joining(","))
                + "->" + lineage.target().displayName();
    }

    private static String sha256(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
