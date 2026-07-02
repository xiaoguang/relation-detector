package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.core.parser.DdlRelationParserRunner;
import com.relationdetector.core.lineage.TokenEventDataLineageExtractor;
import com.relationdetector.core.lineage.DataLineageMerger;
import com.relationdetector.core.log.PlainSqlLogExtractor;
import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.log.SqlLogNoiseFilter;
import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.relation.NamingMatchEvidenceEnhancer;
import com.relationdetector.core.relation.RelationshipMerger;
import com.relationdetector.core.parser.SqlRelationParserRunner;
import com.relationdetector.core.relation.TokenEventRelationExtractor;
import com.relationdetector.core.tokenevent.CommonTokenEventStructuredSqlParser;
import com.relationdetector.core.tokenevent.TokenEventStructuredDdlParser;
import com.relationdetector.mysql.MySqlDatabaseAdaptor;
import com.relationdetector.oracle.OracleDatabaseAdaptor;
import com.relationdetector.postgres.PostgresDatabaseAdaptor;

/**
 * Unified file-based correctness suite for parser and relationship behavior.
 *
 * <p>Large SQL/DDL examples and their golden expectations belong under
 * {@code test-fixtures/correctness}. Java tests still cover local behavior such
 * as config parsing, mock JDBC collectors, warning diagnostics, and merger
 * internals; this runner is the shared baseline for input-to-relation
 * correctness.
 */
class CorrectnessFixtureRunnerTest {
    private static final Set<String> SMOKE_FIXTURES = Set.of(
            "common/sql-basic-join",
            "mysql/basic-correctness-case-01-ddl",
            "postgres/postgres-basic-correctness-case-01-ddl",
            "oracle/oracle-sample-data-full-01-schema-01-tables-ddl");

    @Test
    void allCorrectnessFixturesPassGoldenExpectations() throws Exception {
        Path root = workspaceRoot().resolve("test-fixtures/correctness");
        List<Path> manifests;
        try (Stream<Path> paths = Files.walk(root)) {
            String fixtureFilter = System.getProperty("correctnessFixtureFilter", "");
            String fixtureProfile = System.getProperty(
                    "correctnessFixtureProfile",
                    fixtureFilter.isBlank() ? "smoke" : "full");
            manifests = paths
                    .filter(path -> path.getFileName().toString().equals("manifest.yml"))
                    .filter(path -> matchesCorrectnessProfile(root, path, fixtureProfile))
                    .filter(path -> fixtureFilter.isBlank() || path.toString().contains(fixtureFilter))
                    .sorted()
                    .toList();
        }

        assertFalse(manifests.isEmpty(), "Expected at least one correctness manifest under " + root);
        List<FixtureFailure> failures = runFixtures(manifests);
        assertAll("correctness fixtures",
                failures.stream()
                        .map(failure -> (Executable) () -> {
                            throw new AssertionError(failure.manifest() + " failed", failure.error());
                        })
                        .toList());
    }

    @Test
    void correctnessFixtureProfilesSelectDialectFamilies() {
        Path root = Path.of("test-fixtures/correctness");

        assertTrue(matchesCorrectnessProfile(root,
                root.resolve("oracle/v26ai/oracle26ai-example/manifest.yml"),
                "oracle"));
        assertTrue(matchesCorrectnessProfile(root,
                root.resolve("postgres/v18/postgres18-example/manifest.yml"),
                "postgres/v18"));
        assertTrue(matchesCorrectnessProfile(root,
                root.resolve("mysql/v8_0/mysql80-example/manifest.yml"),
                "mysql,mysql/v8_0"));
        assertTrue(matchesCorrectnessProfile(root,
                root.resolve("common/sql-basic-join/manifest.yml"),
                "smoke"));
        assertFalse(matchesCorrectnessProfile(root,
                root.resolve("oracle/v12c/oracle12c-example/manifest.yml"),
                "postgres"));
        assertFalse(matchesCorrectnessProfile(root,
                root.resolve("postgres/postgres-large-example/manifest.yml"),
                "smoke"));
    }

    @Test
    void objectBlockStatementFormatKeepsRoutineBodyTogether() {
        String text = """
                -- relation-detector-fixture-source: ROUTINE:case_01.rebuild_order_rollups
                BEGIN
                  SELECT *
                  FROM orders o
                  JOIN users u ON o.user_id = u.id;
                  SELECT *
                  FROM order_items oi
                  JOIN products p ON oi.product_id = p.id;
                END
                -- relation-detector-fixture-end
                """;

        List<SqlStatementRecord> statements = parseObjectBlockStatements(
                text,
                StatementSourceType.PROCEDURE,
                "routine-fixture.sql",
                DatabaseType.MYSQL);

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).sql().contains("JOIN users u ON o.user_id = u.id;"));
        assertTrue(statements.get(0).sql().contains("JOIN products p ON oi.product_id = p.id;"));
        assertEquals("ROUTINE:case_01.rebuild_order_rollups", statements.get(0).sourceName());
    }

    @Test
    void objectBlockStatementFormatCanFilterOneRoutineSource() {
        String text = """
                -- relation-detector-fixture-source: ROUTINE:case_01.skip_me
                BEGIN
                  SELECT * FROM ignored_orders;
                END
                -- relation-detector-fixture-end
                -- relation-detector-fixture-source: ROUTINE:case_01.keep_me
                BEGIN
                  UPDATE orders o
                  JOIN users u ON o.user_id = u.id
                  SET o.audit_user_id = u.id;
                END
                -- relation-detector-fixture-end
                """;

        List<SqlStatementRecord> statements = parseObjectBlockStatements(
                text,
                StatementSourceType.PROCEDURE,
                "routine-fixture.sql",
                DatabaseType.MYSQL,
                "ROUTINE:case_01.keep_me");

        assertEquals(1, statements.size());
        assertEquals("ROUTINE:case_01.keep_me", statements.get(0).sourceName());
        assertTrue(statements.get(0).sql().contains("SET o.audit_user_id = u.id"));
        assertFalse(statements.get(0).sql().contains("ignored_orders"));
    }

    @Test
    void objectBlockStatementFormatRequiresEndMarker() {
        String text = """
                -- relation-detector-fixture-source: ROUTINE:case_01.rebuild_order_rollups
                BEGIN
                  SELECT * FROM orders o JOIN users u ON o.user_id = u.id;
                END
                """;

        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> parseObjectBlockStatements(text, StatementSourceType.PROCEDURE, "routine-fixture.sql", DatabaseType.MYSQL));

        assertTrue(error.getMessage().contains("Missing relation-detector-fixture-end"));
    }

    @Test
    void objectBlockStatementFormatSplitsUnmarkedOracleSlashTerminatedObjects() {
        String text = """
                CREATE OR REPLACE PROCEDURE sp_one
                AS
                BEGIN
                  SELECT * FROM customers c;
                END;
                /

                CREATE OR REPLACE PROCEDURE sp_two
                AS
                BEGIN
                  SELECT * FROM contracts c;
                END;
                /
                """;

        List<SqlStatementRecord> statements = parseObjectBlockStatements(
                text,
                StatementSourceType.PROCEDURE,
                "oracle-routine-fixture.sql",
                DatabaseType.ORACLE);

        assertEquals(2, statements.size());
        assertEquals("oracle-routine-fixture.sql#sp_one", statements.get(0).sourceName());
        assertEquals("oracle-routine-fixture.sql#sp_two", statements.get(1).sourceName());
        assertTrue(statements.get(0).sql().contains("FROM customers c"));
        assertTrue(statements.get(1).sql().contains("FROM contracts c"));
    }

    @Test
    void commonDdlFixtureUsesCommonParserInsteadOfDialectAdaptorParser() throws Exception {
        String input = """
                CREATE TABLE "customers" (
                    "id" INT PRIMARY KEY
                );
                CREATE TABLE "orders" (
                    "id" INT PRIMARY KEY,
                    "customer_id" INT,
                    FOREIGN KEY ("customer_id") REFERENCES "customers" ("id")
                );
                """;
        CorrectnessFixture fixture = new CorrectnessFixture(
                Path.of("in-memory-common-ddl-fixture.yml"),
                "in-memory-common-ddl-fixture",
                DatabaseType.MYSQL,
                "DDL",
                StatementSourceType.DDL_FILE,
                "SEMICOLON",
                EvidenceSourceType.DDL_FILE,
                "portable",
                Path.of("in-memory-common-ddl.sql"),
                Path.of("expected-relations.json"),
                Path.of("expected-lineage.json"),
                Path.of("expected-diagnostics.json"),
                "",
                "common-token-event",
                "token-event",
                "",
                "");

        runDdlFixture(
                fixture,
                input,
                new ExpectedRelations(
                        List.of("FK_LIKE:orders.customer_id->customers.id:DDL_FOREIGN_KEY,TARGET_UNIQUE"),
                        List.of()),
                new ExpectedDiagnostics("", Map.of()));
    }

    private void runFixture(CorrectnessFixture fixture) throws Exception {
        String input = Files.readString(fixture.inputFile());
        ExpectedRelations expectedRelations = ExpectedRelations.read(fixture.expectedRelationsFile());
        ExpectedDiagnostics expectedDiagnostics = ExpectedDiagnostics.read(fixture.expectedDiagnosticsFile());
        ExpectedLineage expectedLineage = ExpectedLineage.readIfPresent(fixture.expectedLineageFile());
        if (!Boolean.getBoolean("updateCorrectnessGold")) {
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

    private List<FixtureFailure> runFixtures(List<Path> manifests) {
        int parallelism = correctnessFixtureParallelism();
        if (parallelism <= 1) {
            return manifests.stream()
                    .map(this::runFixtureSafely)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(failure -> failure.manifest().toString()))
                    .toList();
        }
        ForkJoinPool pool = new ForkJoinPool(parallelism);
        try {
            return pool.submit(() -> manifests.parallelStream()
                    .map(this::runFixtureSafely)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(failure -> failure.manifest().toString()))
                    .toList()).join();
        } finally {
            pool.shutdown();
        }
    }

    private FixtureFailure runFixtureSafely(Path manifest) {
        try {
            runFixture(CorrectnessFixture.read(manifest));
            return null;
        } catch (Throwable error) {
            return new FixtureFailure(manifest, error);
        }
    }

    private static int correctnessFixtureParallelism() {
        if (Boolean.getBoolean("updateCorrectnessGold")) {
            return 1;
        }
        String configured = System.getProperty("correctnessFixtureParallelism", "").trim();
        if (!configured.isBlank()) {
            return Math.max(1, Integer.parseInt(configured));
        }
        return Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));
    }

    private static boolean matchesCorrectnessProfile(Path correctnessRoot, Path manifest, String profile) {
        Set<String> profiles = Arrays.stream(profile.split("[,|]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (profiles.isEmpty()) {
            profiles = Set.of("smoke");
        }
        Path fixtureDir = manifest.getParent();
        Path relative = correctnessRoot.relativize(fixtureDir);
        String fixtureKey = relative.toString().replace('\\', '/');
        String dialect = relative.getName(0).toString().toLowerCase(Locale.ROOT);
        String version = relative.getNameCount() > 1 ? relative.getName(1).toString().toLowerCase(Locale.ROOT) : "";
        for (String rawProfile : profiles) {
            String requested = rawProfile.toLowerCase(Locale.ROOT);
            if (requested.equals("full") || requested.equals("all")) {
                return true;
            }
            if (requested.equals("smoke")) {
                return SMOKE_FIXTURES.contains(fixtureKey);
            }
            if (requested.equals(dialect)) {
                return true;
            }
            if (requested.equals(dialect + "-root")) {
                return !version.startsWith("v");
            }
            if (requested.equals(dialect + "/" + version) || requested.equals(dialect + "-" + version)) {
                return true;
            }
            if (requested.equals("mysql80") || requested.equals("mysql8") || requested.equals("mysql-v8_0")) {
                return dialect.equals("mysql") && version.equals("v8_0");
            }
        }
        return false;
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
            relationships.addAll(runner.parse(adaptor, config, statement, context));
            /*
             * Relationship parsing and Data Lineage must use the same parser-selection policy.
             * Parse with a null context so fixture warning assertions are not polluted by
             * this second, lineage-only structural parse.
             */
            runner.parseStructured(adaptor, config, statement, null)
                    .ifPresent(structured -> lineages.addAll(lineageExtractor.extract(statement, structured)));
        }
        assertRelations(fixture, expectedRelations, relationships);
        assertLineage(fixture, expectedLineage,
                new DataLineageMerger().merge(lineages).stream().map(this::lineageFingerprint).toList());
        assertWarningCodes(fixture, expectedDiagnostics, warnings);
    }

    private String inputOf(CorrectnessFixture fixture) throws Exception {
        return Files.readString(fixture.inputFile());
    }

    private List<SqlStatementRecord> sqlStatements(
            CorrectnessFixture fixture,
            String input,
            List<WarningMessage> warnings
    ) {
        if ("OBJECT_BLOCKS".equalsIgnoreCase(fixture.statementFormat())) {
            return parseObjectBlockStatements(
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

    private boolean isCommonTokenEventFixture(CorrectnessFixture fixture) {
        return fixture.structuredParser().equals("common-token-event");
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
                    expectedRelationsJson(actualFingerprints.stream().toList(), expected.forbiddenTables()));
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
            try {
                Files.writeString(fixture.expectedDiagnosticsFile(),
                        expectedDiagnosticsJson(sha256(inputOf(fixture)), actualCodes));
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot update diagnostics gold for " + fixture.id(), exception);
            }
            return;
        }
        assertEquals(expected.warningCodes(), actualCodes, fixture.id() + " warningCodes");
    }

    private boolean isStrictFullGrammerFixture(CorrectnessFixture fixture) {
        return fixture.parserMode().equals("full-grammer") && !fixture.grammarProfile().isBlank();
    }

    private void assertLineage(
            CorrectnessFixture fixture,
            ExpectedLineage expected,
            List<String> actualFingerprints
    ) throws Exception {
        if (Boolean.getBoolean("updateCorrectnessGold")
                && (expected.exists() || !actualFingerprints.isEmpty())) {
            Files.writeString(fixture.expectedLineageFile(),
                    expectedLineageJson(new TreeSet<>(actualFingerprints).stream().toList(),
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

    private static String expectedRelationsJson(List<String> fingerprints, List<String> forbiddenTables) {
        return "{\n"
                + "  \"fingerprints\": " + stringArrayJson(fingerprints) + ",\n"
                + "  \"forbiddenTables\": " + stringArrayJson(forbiddenTables) + "\n"
                + "}\n";
    }

    private static String expectedLineageJson(
            List<String> fingerprints,
            List<String> forbiddenSources,
            List<String> forbiddenTargets,
            Map<String, Long> warningCodes
    ) {
        return "{\n"
                + "  \"fingerprints\": " + stringArrayJson(fingerprints) + ",\n"
                + "  \"forbiddenSources\": " + stringArrayJson(forbiddenSources) + ",\n"
                + "  \"forbiddenTargets\": " + stringArrayJson(forbiddenTargets) + ",\n"
                + "  \"warningCodes\": " + longMapJson(warningCodes) + "\n"
                + "}\n";
    }

    private static String expectedDiagnosticsJson(String fixtureSha256, Map<String, Long> warningCodes) {
        return "{\n"
                + "  \"fixtureSha256\": \"" + escapeJson(fixtureSha256) + "\",\n"
                + "  \"warningCodes\": " + longMapJson(warningCodes) + "\n"
                + "}\n";
    }

    private static String stringArrayJson(List<String> values) {
        if (values.isEmpty()) {
            return "[]";
        }
        return values.stream()
                .map(value -> "    \"" + escapeJson(value) + "\"")
                .collect(Collectors.joining(",\n", "[\n", "\n  ]"));
    }

    private static String longMapJson(Map<String, Long> values) {
        if (values.isEmpty()) {
            return "{}";
        }
        return values.entrySet().stream()
                .map(entry -> "    \"" + escapeJson(entry.getKey()) + "\": " + entry.getValue())
                .collect(Collectors.joining(",\n", "{\n", "\n  }"));
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private DatabaseAdaptor adaptor(DatabaseType databaseType) {
        return switch (databaseType) {
            case MYSQL -> new MySqlDatabaseAdaptor();
            case POSTGRESQL -> new PostgresDatabaseAdaptor();
            case ORACLE -> new OracleDatabaseAdaptor();
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

    private static String sha256(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static Path workspaceRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("test-fixtures"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate relation-detector workspace root");
    }

    private static List<SqlStatementRecord> parseObjectBlockStatements(
            String text,
            StatementSourceType sourceType,
            String sourceFile,
            DatabaseType databaseType
    ) {
        return parseObjectBlockStatements(text, sourceType, sourceFile, databaseType, "");
    }

    private static List<SqlStatementRecord> parseObjectBlockStatements(
            String text,
            StatementSourceType sourceType,
            String sourceFile,
            DatabaseType databaseType,
            String objectSourceFilter
    ) {
        List<SqlStatementRecord> statements = new ArrayList<>();
        List<String> localTempTables = PlainSqlLogExtractor.localTempTablesIn(text);
        String[] lines = text.split("\\R", -1);
        String currentSource = null;
        StringBuilder currentSql = new StringBuilder();
        long startLine = 0;
        String filter = objectSourceFilter == null ? "" : objectSourceFilter.trim();
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String trimmed = line.trim();
            if (trimmed.startsWith("-- relation-detector-fixture-source:")) {
                if (currentSource != null) {
                    throw new IllegalArgumentException(
                            "Missing relation-detector-fixture-end before line " + (index + 1) + " in " + sourceFile);
                }
                currentSource = trimmed.substring("-- relation-detector-fixture-source:".length()).trim();
                currentSql.setLength(0);
                startLine = index + 2L;
                continue;
            }
            if (trimmed.equals("-- relation-detector-fixture-end")) {
                if (currentSource == null) {
                    throw new IllegalArgumentException(
                            "Unexpected relation-detector-fixture-end at line " + (index + 1) + " in " + sourceFile);
                }
                String sql = currentSql.toString().strip();
                if (!sql.isBlank() && (filter.isBlank() || currentSource.equals(filter))) {
                    Map<String, Object> attributes = new LinkedHashMap<>();
                    attributes.put("fixtureObjectSource", currentSource);
                    if (!localTempTables.isEmpty()) {
                        attributes.put("localTempTables", localTempTables);
                    }
                    statements.add(new SqlStatementRecord(sql, sourceType, currentSource,
                            startLine, index, attributes));
                }
                currentSource = null;
                currentSql.setLength(0);
                continue;
            }
            if (currentSource != null) {
                currentSql.append(line).append('\n');
            }
        }
        if (currentSource != null) {
            throw new IllegalArgumentException(
                    "Missing relation-detector-fixture-end for " + currentSource + " in " + sourceFile);
        }
        if (!filter.isBlank() && statements.isEmpty()) {
            throw new IllegalArgumentException(
                    "No relation-detector-fixture-source matched objectSourceFilter "
                            + filter + " in " + sourceFile);
        }
        if (statements.isEmpty() && !text.isBlank() && databaseType == DatabaseType.ORACLE) {
            List<SqlStatementRecord> oracleObjects = parseUnmarkedOracleObjectBlocks(
                    text,
                    sourceType,
                    sourceFile,
                    localTempTables);
            if (!oracleObjects.isEmpty()) {
                return List.copyOf(oracleObjects);
            }
        }
        if (statements.isEmpty() && !text.isBlank()) {
            Map<String, Object> attributes = new LinkedHashMap<>();
            if (!localTempTables.isEmpty()) {
                attributes.put("localTempTables", localTempTables);
            }
            statements.add(new SqlStatementRecord(text.strip(), sourceType, sourceFile, 1, lines.length, attributes));
        }
        return List.copyOf(statements);
    }

    private static List<SqlStatementRecord> parseUnmarkedOracleObjectBlocks(
            String text,
            StatementSourceType sourceType,
            String sourceFile,
            List<String> localTempTables
    ) {
        List<SqlStatementRecord> statements = new ArrayList<>();
        String[] lines = text.split("\\R", -1);
        StringBuilder currentSql = new StringBuilder();
        String currentName = "";
        long startLine = 0;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String trimmed = line.trim();
            if (currentSql.isEmpty() && startsOracleObject(trimmed)) {
                currentName = oracleObjectName(trimmed);
                startLine = index + 1L;
                currentSql.append(line).append('\n');
                continue;
            }
            if (!currentSql.isEmpty()) {
                currentSql.append(line).append('\n');
                if (trimmed.equals("/")) {
                    Map<String, Object> attributes = new LinkedHashMap<>();
                    if (!localTempTables.isEmpty()) {
                        attributes.put("localTempTables", localTempTables);
                    }
                    String sourceName = currentName.isBlank() ? sourceFile : sourceFile + "#" + currentName;
                    statements.add(new SqlStatementRecord(currentSql.toString().strip(), sourceType, sourceName,
                            startLine, index + 1L, attributes));
                    currentSql.setLength(0);
                    currentName = "";
                }
            }
        }
        return statements;
    }

    private static boolean startsOracleObject(String trimmed) {
        String upper = trimmed.toUpperCase(Locale.ROOT);
        return upper.startsWith("CREATE OR REPLACE PROCEDURE ")
                || upper.startsWith("CREATE OR REPLACE FUNCTION ")
                || upper.startsWith("CREATE OR REPLACE TRIGGER ")
                || upper.startsWith("CREATE OR REPLACE PACKAGE ");
    }

    private static String oracleObjectName(String trimmed) {
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 5) {
            return "";
        }
        int nameIndex = parts.length > 5 && parts[3].equalsIgnoreCase("PACKAGE")
                && parts[4].equalsIgnoreCase("BODY")
                        ? 5
                        : 4;
        return parts[nameIndex].replace("\"", "").replace("(", "");
    }

    private record CorrectnessFixture(
            Path path,
            String id,
            DatabaseType databaseType,
            String parserTarget,
            StatementSourceType sourceType,
            String statementFormat,
            EvidenceSourceType evidenceSourceType,
            String schema,
            Path inputFile,
            Path expectedRelationsFile,
            Path expectedLineageFile,
            Path expectedDiagnosticsFile,
            String objectSourceFilter,
            String structuredParser,
            String parserMode,
            String grammarProfile,
            String databaseVersion
    ) {
        static CorrectnessFixture read(Path manifest) throws Exception {
            Map<String, String> values = readSimpleManifest(manifest);
            Path root = manifest.getParent();
            return new CorrectnessFixture(
                    manifest,
                    required(values, "id", manifest),
                    DatabaseType.valueOf(required(values, "databaseType", manifest)),
                    required(values, "parserTarget", manifest),
                    StatementSourceType.valueOf(values.getOrDefault("sourceType", "PLAIN_SQL")),
                    values.getOrDefault("statementFormat", "SEMICOLON"),
                    EvidenceSourceType.valueOf(values.getOrDefault("evidenceSourceType", "DDL_FILE")),
                    values.getOrDefault("schema", "public"),
                    root.resolve(required(values, "input", manifest)).normalize(),
                    root.resolve(required(values, "expectedRelations", manifest)).normalize(),
                    root.resolve(values.getOrDefault("expectedLineage", "expected-lineage.json")).normalize(),
                    root.resolve(required(values, "expectedDiagnostics", manifest)).normalize(),
                    values.getOrDefault("objectSourceFilter", ""),
                    values.getOrDefault("structuredParser", ""),
                    values.getOrDefault("parserMode", "auto"),
                    values.getOrDefault("grammarProfile", ""),
                    values.getOrDefault("databaseVersion", ""));
        }

        private static Map<String, String> readSimpleManifest(Path manifest) throws Exception {
            Map<String, String> values = new LinkedHashMap<>();
            for (String rawLine : Files.readAllLines(manifest)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon < 0) {
                    throw new IllegalArgumentException("Invalid manifest line in " + manifest + ": " + rawLine);
                }
                values.put(line.substring(0, colon).trim(), unquote(line.substring(colon + 1).trim()));
            }
            return values;
        }

        private static String required(Map<String, String> values, String key, Path manifest) {
            String value = values.get(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing manifest key " + key + " in " + manifest);
            }
            return value;
        }

    }

    private record FixtureFailure(Path manifest, Throwable error) {
    }

    private record ExpectedRelations(
            List<String> fingerprints,
            List<String> forbiddenTables
    ) {
        static ExpectedRelations read(Path file) throws Exception {
            String text = Files.readString(file);
            return new ExpectedRelations(
                    stringArray(text, "fingerprints"),
                    stringArray(text, "forbiddenTables"));
        }
    }

    private record ExpectedDiagnostics(
            String fixtureSha256,
            Map<String, Long> warningCodes
    ) {
        static ExpectedDiagnostics read(Path file) throws Exception {
            String text = Files.readString(file);
            return new ExpectedDiagnostics(
                    stringField(text, "fixtureSha256"),
                    objectLongs(text, "warningCodes"));
        }
    }

    private record ExpectedLineage(
            boolean exists,
            List<String> fingerprints,
            List<String> forbiddenSources,
            List<String> forbiddenTargets,
            Map<String, Long> warningCodes
    ) {
        static ExpectedLineage readIfPresent(Path file) throws Exception {
            if (!Files.exists(file)) {
                return new ExpectedLineage(false, List.of(), List.of(), List.of(), Map.of());
            }
            String text = Files.readString(file);
            return new ExpectedLineage(true,
                    stringArray(text, "fingerprints"),
                    stringArray(text, "forbiddenSources"),
                    stringArray(text, "forbiddenTargets"),
                    objectLongs(text, "warningCodes"));
        }
    }

    private static String unquote(String text) {
        if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private static String stringField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing string field " + field);
        }
        return matcher.group(1);
    }

    private static List<String> stringArray(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL)
                .matcher(json);
        if (!matcher.find()) {
            return List.of();
        }
        return stringArrayFromBody(matcher.group(1));
    }

    private static List<String> stringArrayFromBody(String bodyText) {
        String body = bodyText.trim();
        if (body.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Matcher item = Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(body);
        while (item.find()) {
            values.add(item.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
        }
        return List.copyOf(values);
    }

    private static Map<String, List<String>> objectStringArrays(String json, String field) {
        String body = objectBody(json, field);
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        Map<String, List<String>> values = new LinkedHashMap<>();
        Matcher entry = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(body);
        while (entry.find()) {
            values.put(entry.group(1), stringArrayFromBody(entry.group(2)));
        }
        return values;
    }

    private static Map<String, Long> objectLongs(String json, String field) {
        String body = objectBody(json, field);
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        return objectLongsFromBody(body);
    }

    private static Map<String, Map<String, Long>> objectObjectLongs(String json, String field) {
        String body = objectBody(json, field);
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        Map<String, Map<String, Long>> values = new LinkedHashMap<>();
        Matcher entry = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\{(.*?)\\}", Pattern.DOTALL).matcher(body);
        while (entry.find()) {
            values.put(entry.group(1), objectLongsFromBody(entry.group(2)));
        }
        return values;
    }

    private static Map<String, Long> objectLongsFromBody(String body) {
        if (body.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> values = new LinkedHashMap<>();
        Matcher entry = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\\d+)").matcher(body);
        while (entry.find()) {
            values.put(entry.group(1), Long.parseLong(entry.group(2)));
        }
        return values;
    }

    private static String objectBody(String json, String field) {
        Matcher fieldMatcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:").matcher(json);
        if (!fieldMatcher.find()) {
            return null;
        }
        int start = json.indexOf('{', fieldMatcher.end());
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(start + 1, i).trim();
                }
            }
        }
        throw new IllegalArgumentException("Unclosed object field " + field);
    }
}
