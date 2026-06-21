package com.relationdetector.cli;

import com.relationdetector.core.fullgrammer.*;
import com.relationdetector.core.tokenevent.*;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.relationdetector.api.Collectors.StructuredSqlParser;
import com.relationdetector.api.DataLineageCandidate;
import com.relationdetector.api.DatabaseAdaptor;
import com.relationdetector.api.Endpoint;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.WarningMessage;
import com.relationdetector.api.Enums.DatabaseType;
import com.relationdetector.api.Enums.StatementSourceType;
import com.relationdetector.core.fullgrammer.FullGrammerTokenEventParserFactory;
import com.relationdetector.core.fullgrammer.FullGrammerTokenEventShadowComparator;
import com.relationdetector.core.PlainSqlLogExtractor;
import com.relationdetector.mysql.MySqlDatabaseAdaptor;
import com.relationdetector.postgres.PostgresDatabaseAdaptor;

/**
 * Shadow correctness guard for versioned full-grammer parser profiles.
 *
 * <p>The full-grammer parser is not production output yet. This test verifies
 * the shadow entry point can run across SQL correctness fixtures without being
 * weaker than the current token-event parser.
 */
class FullGrammerCorrectnessShadowTest {
    @Test
    void fullGrammerShadowDoesNotMissCurrentSqlFixtureOutput() throws Exception {
        Path root = workspaceRoot().resolve("test-fixtures/correctness");
        List<Fixture> fixtures;
        try (Stream<Path> paths = Files.walk(root)) {
            String fixtureFilter = System.getProperty("correctnessFixtureFilter", "");
            fixtures = paths
                    .filter(path -> path.getFileName().toString().equals("manifest.yml"))
                    .filter(path -> fixtureFilter.isBlank() || path.toString().contains(fixtureFilter))
                    .map(this::readFixture)
                    .filter(fixture -> fixture.parserTarget().equals("SQL"))
                    .toList();
        }

        assertTrue(!fixtures.isEmpty(), "Expected SQL correctness fixtures");
        assertAll("full-grammer SQL shadow parity",
                fixtures.stream().map(fixture -> (Executable) () -> assertFixtureParity(fixture)).toList());
    }

    private void assertFixtureParity(Fixture fixture) throws Exception {
        DatabaseAdaptor adaptor = adaptor(fixture.databaseType());
        StructuredSqlParser current = adaptor.structuredSqlParser()
                .orElseThrow(() -> new IllegalStateException("No structured SQL parser for " + fixture.databaseType()));
        StructuredSqlParser shadow = FullGrammerTokenEventParserFactory.create(
                fixture.databaseType(),
                defaultVersion(fixture.databaseType()),
                current).parser();
        FullGrammerTokenEventShadowComparator comparator = new FullGrammerTokenEventShadowComparator();
        List<String> failures = new ArrayList<>();
        for (SqlStatementRecord statement : statements(fixture)) {
            FullGrammerTokenEventShadowComparator.Comparison comparison = comparator.compare(
                    statement,
                    current,
                    shadow,
                    candidates -> candidates.stream().map(this::relationshipFingerprint).toList(),
                    lineages -> lineages.stream().map(this::lineageFingerprint).toList());
            if (!comparison.missingCurrentRelations().isEmpty()
                    || !comparison.missingCurrentLineages().isEmpty()) {
                failures.add(fixture.id() + " / " + statement.sourceName()
                        + " missingRelations=" + comparison.missingCurrentRelations()
                        + " missingLineages=" + comparison.missingCurrentLineages()
                        + " fullComparison=" + comparison);
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    private List<SqlStatementRecord> statements(Fixture fixture) throws Exception {
        String input = Files.readString(fixture.inputFile());
        if (fixture.statementFormat().equalsIgnoreCase("OBJECT_BLOCKS")) {
            return parseObjectBlockStatements(input, fixture.sourceType(), fixture.inputFile().toString(),
                    fixture.objectSourceFilter());
        }
        List<WarningMessage> warnings = new ArrayList<>();
        return new PlainSqlLogExtractor()
                .extract(fixture.inputFile(), fixture.sourceType(), warnings::add)
                .toList();
    }

    private String relationshipFingerprint(RelationshipCandidate relation) {
        return relation.relationType().name() + ":"
                + endpoint(relation.source()) + "->" + endpoint(relation.target()) + ":"
                + relation.evidence().stream().findFirst().orElseThrow().type().name();
    }

    private String lineageFingerprint(DataLineageCandidate lineage) {
        String sources = lineage.sources().stream()
                .sorted(Comparator.comparing(Endpoint::normalizedKey))
                .map(this::endpoint)
                .collect(Collectors.joining(","));
        return lineage.flowKind().name() + ":"
                + lineage.transformType().name() + ":"
                + sources + "->" + endpoint(lineage.target());
    }

    private String endpoint(Endpoint endpoint) {
        return endpoint.table().tableName() + "."
                + (endpoint.column() == null ? "*" : endpoint.column().columnName());
    }

    private DatabaseAdaptor adaptor(DatabaseType databaseType) {
        return switch (databaseType) {
            case MYSQL -> new MySqlDatabaseAdaptor();
            case POSTGRESQL -> new PostgresDatabaseAdaptor();
            default -> throw new IllegalArgumentException("No correctness adaptor for " + databaseType);
        };
    }

    private String defaultVersion(DatabaseType databaseType) {
        return switch (databaseType) {
            case MYSQL -> "8.0";
            case POSTGRESQL -> "16";
            default -> "";
        };
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
                currentSource = trimmed.substring("-- relation-detector-fixture-source:".length()).trim();
                currentSql.setLength(0);
                startLine = index + 2L;
                continue;
            }
            if (trimmed.equals("-- relation-detector-fixture-end")) {
                if (currentSource != null) {
                    String sql = currentSql.toString().strip();
                    if (!sql.isBlank() && (filter.isBlank() || currentSource.equals(filter))) {
                        statements.add(new SqlStatementRecord(sql, sourceType, currentSource,
                                startLine, index, java.util.Map.of("fixtureObjectSource", currentSource)));
                    }
                }
                currentSource = null;
                currentSql.setLength(0);
                continue;
            }
            if (currentSource != null) {
                currentSql.append(line).append('\n');
            }
        }
        return List.copyOf(statements);
    }

    private Fixture readFixture(Path manifest) {
        try {
            Map<String, String> values = readSimpleManifest(manifest);
            Path root = manifest.getParent();
            return new Fixture(
                    values.get("id"),
                    DatabaseType.valueOf(values.get("databaseType")),
                    values.get("parserTarget"),
                    StatementSourceType.valueOf(values.getOrDefault("sourceType", "PLAIN_SQL")),
                    values.getOrDefault("statementFormat", "SEMICOLON"),
                    root.resolve(values.get("input")).normalize(),
                    values.getOrDefault("objectSourceFilter", ""));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read fixture " + manifest, exception);
        }
    }

    private Map<String, String> readSimpleManifest(Path manifest) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        for (String rawLine : Files.readAllLines(manifest)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            values.put(line.substring(0, colon).trim(), unquote(line.substring(colon + 1).trim()));
        }
        return values;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
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

    private record Fixture(
            String id,
            DatabaseType databaseType,
            String parserTarget,
            StatementSourceType sourceType,
            String statementFormat,
            Path inputFile,
            String objectSourceFilter
    ) {
    }
}
