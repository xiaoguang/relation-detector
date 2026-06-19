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

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.DatabaseAdaptor;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.ScanScope;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.WarningMessage;
import com.relationdetector.api.Enums.DatabaseType;
import com.relationdetector.api.Enums.EvidenceSourceType;
import com.relationdetector.api.Enums.StatementSourceType;
import com.relationdetector.core.DdlRelationParserRunner;
import com.relationdetector.core.PlainSqlLogExtractor;
import com.relationdetector.core.ScanConfig;
import com.relationdetector.core.SqlLogNoiseFilter;
import com.relationdetector.core.SqlRelationParserRunner;
import com.relationdetector.mysql.MySqlDatabaseAdaptor;
import com.relationdetector.postgres.PostgresDatabaseAdaptor;

/**
 * Unified file-based correctness suite for parser and relationship behavior.
 *
 * <p>Large SQL/DDL examples and their golden expectations belong under
 * {@code test-fixtures/correctness}. Java tests still cover local behavior such
 * as config parsing, mock JDBC collectors, fallback branches, and merger
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

    private void runFixture(CorrectnessFixture fixture) throws Exception {
        String input = Files.readString(fixture.inputFile());
        ExpectedRelations expectedRelations = ExpectedRelations.read(fixture.expectedRelationsFile());
        ExpectedDiagnostics expectedDiagnostics = ExpectedDiagnostics.read(fixture.expectedDiagnosticsFile());
        assertEquals(expectedDiagnostics.fixtureSha256(), sha256(input), fixture.id() + " fixture hash");

        if (fixture.parserTarget().equals("SQL")) {
            runSqlFixture(fixture, expectedRelations, expectedDiagnostics);
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
            ExpectedDiagnostics expectedDiagnostics
    ) {
        DatabaseAdaptor adaptor = adaptor(fixture.databaseType());
        ScanConfig config = config(fixture);
        List<WarningMessage> warnings = new ArrayList<>();
        AdaptorContext context = context(fixture, warnings);
        List<SqlStatementRecord> statements = new PlainSqlLogExtractor()
                .extract(fixture.inputFile(), fixture.sourceType(), warnings::add)
                .toList();
        List<RelationshipCandidate> relationships = new ArrayList<>();
        SqlRelationParserRunner runner = new SqlRelationParserRunner();
        for (SqlStatementRecord statement : statements) {
            relationships.addAll(runner.parse(adaptor, config, statement, context));
        }
        assertRelations(fixture, expectedRelations, relationships);
        assertWarningCodes(fixture, expectedDiagnostics, warnings);
    }

    private void runDdlFixture(
            CorrectnessFixture fixture,
            String input,
            ExpectedRelations expectedRelations,
            ExpectedDiagnostics expectedDiagnostics
    ) {
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
    ) {
        Set<String> actualFingerprints = actual.stream()
                .map(this::fingerprint)
                .collect(Collectors.toCollection(TreeSet::new));
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

    private String fingerprint(RelationshipCandidate relation) {
        String evidenceTypes = relation.evidence().stream()
                .map(evidence -> evidence.type().name())
                .collect(Collectors.joining(","));
        return relation.relationType() + ":"
                + relation.source().displayName() + "->" + relation.target().displayName()
                + ":" + evidenceTypes;
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

    private record CorrectnessFixture(
            Path path,
            String id,
            DatabaseType databaseType,
            String parserTarget,
            StatementSourceType sourceType,
            EvidenceSourceType evidenceSourceType,
            String schema,
            Path inputFile,
            Path expectedRelationsFile,
            Path expectedDiagnosticsFile
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
                    EvidenceSourceType.valueOf(values.getOrDefault("evidenceSourceType", "DDL_FILE")),
                    values.getOrDefault("schema", "public"),
                    root.resolve(required(values, "input", manifest)).normalize(),
                    root.resolve(required(values, "expectedRelations", manifest)).normalize(),
                    root.resolve(required(values, "expectedDiagnostics", manifest)).normalize());
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
