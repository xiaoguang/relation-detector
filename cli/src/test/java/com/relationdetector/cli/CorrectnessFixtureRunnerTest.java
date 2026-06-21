package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
import com.relationdetector.core.parser.SqlRelationParserRunner;
import com.relationdetector.mysql.MySqlDatabaseAdaptor;
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
    @Test
    void allCorrectnessFixturesPassGoldenExpectations() throws Exception {
        Path root = workspaceRoot().resolve("test-fixtures/correctness");
        List<Path> manifests;
        try (Stream<Path> paths = Files.walk(root)) {
            String fixtureFilter = System.getProperty("correctnessFixtureFilter", "");
            manifests = paths
                    .filter(path -> path.getFileName().toString().equals("manifest.yml"))
                    .filter(path -> fixtureFilter.isBlank() || path.toString().contains(fixtureFilter))
                    .sorted()
                    .toList();
        }

        assertFalse(manifests.isEmpty(), "Expected at least one correctness manifest under " + root);
        assertAll("correctness fixtures",
                manifests.stream()
                        .map(path -> (Executable) () -> runFixture(CorrectnessFixture.read(path)))
                        .toList());
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
                "routine-fixture.sql");

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
                () -> parseObjectBlockStatements(text, StatementSourceType.PROCEDURE, "routine-fixture.sql"));

        assertTrue(error.getMessage().contains("Missing relation-detector-fixture-end"));
    }

    private void runFixture(CorrectnessFixture fixture) throws Exception {
        String input = Files.readString(fixture.inputFile());
        ExpectedRelations expectedRelations = ExpectedRelations.read(fixture.expectedRelationsFile());
        ExpectedDiagnostics expectedDiagnostics = ExpectedDiagnostics.read(fixture.expectedDiagnosticsFile());
        ExpectedLineage expectedLineage = ExpectedLineage.readIfPresent(fixture.expectedLineageFile());
        assertEquals(expectedDiagnostics.fixtureSha256(), sha256(input), fixture.id() + " fixture hash");

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
        SqlRelationParserRunner runner = new SqlRelationParserRunner();
        TokenEventDataLineageExtractor lineageExtractor = new TokenEventDataLineageExtractor();
        StructuredSqlParser structuredSqlParser = adaptor.structuredSqlParser()
                .orElseThrow(() -> new IllegalStateException("No structured SQL parser for " + fixture.databaseType()));
        for (SqlStatementRecord statement : statements) {
            relationships.addAll(runner.parse(adaptor, config, statement, context));
            /*
             * Relationship parsing already goes through the adaptor SQL parser.
             * Data Lineage consumes the same token-event model directly; parse
             * with a null context so fixture warning assertions are not polluted by
             * this second, lineage-only structural parse.
             */
            StructuredParseResult structured = structuredSqlParser.parseSql(statement, null);
            lineages.addAll(lineageExtractor.extract(statement, structured));
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
        List<RelationshipCandidate> relationships = new DdlRelationParserRunner()
                .parseText(adaptor, config, input, fixture.id() + ".ddl.sql", fixture.evidenceSourceType(), context);
        assertRelations(fixture, expectedRelations, relationships);
        assertWarningCodes(fixture, expectedDiagnostics, warnings);
    }

    private void assertRelations(
            CorrectnessFixture fixture,
            ExpectedRelations expected,
            List<RelationshipCandidate> actual
    ) throws Exception {
        Set<String> actualFingerprints = actual.stream()
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
            assertTrue(actual.stream().noneMatch(relation ->
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
    ) {
        Map<String, Long> actualCodes = actual.stream()
                .collect(Collectors.groupingBy(WarningMessage::code, LinkedHashMap::new, Collectors.counting()));
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
            default -> throw new IllegalArgumentException("No correctness adaptor for " + databaseType);
        };
    }

    private ScanConfig config(CorrectnessFixture fixture) {
        ScanConfig config = new ScanConfig();
        config.databaseType = fixture.databaseType();
        config.schema = fixture.schema();
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
            String sourceFile
    ) {
        return parseObjectBlockStatements(text, sourceType, sourceFile, "");
    }

    private static List<SqlStatementRecord> parseObjectBlockStatements(
            String text,
            StatementSourceType sourceType,
            String sourceFile,
            String objectSourceFilter
    ) {
        List<SqlStatementRecord> statements = new ArrayList<>();
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
                    statements.add(new SqlStatementRecord(sql, sourceType, currentSource,
                            startLine, index, java.util.Map.of("fixtureObjectSource", currentSource)));
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
        return List.copyOf(statements);
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
            String objectSourceFilter
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
                    values.getOrDefault("objectSourceFilter", ""));
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
