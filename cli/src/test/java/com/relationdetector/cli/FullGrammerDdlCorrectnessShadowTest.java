package com.relationdetector.cli;

import com.relationdetector.core.fullgrammer.*;
import com.relationdetector.core.tokenevent.*;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.core.relation.DdlRelationExtractionVisitor;
import com.relationdetector.core.fullgrammer.FullGrammerDdlParserFactory;
import com.relationdetector.mysql.MySqlDatabaseAdaptor;
import com.relationdetector.postgres.PostgresDatabaseAdaptor;

class FullGrammerDdlCorrectnessShadowTest {
    @Test
    void fullGrammerDdlShadowDoesNotMissCurrentDdlFixtureOutput() throws Exception {
        Path root = workspaceRoot().resolve("test-fixtures/correctness");
        List<Fixture> fixtures;
        try (Stream<Path> paths = Files.walk(root)) {
            String fixtureFilter = System.getProperty("correctnessFixtureFilter", "");
            fixtures = paths
                    .filter(path -> path.getFileName().toString().equals("manifest.yml"))
                    .filter(path -> fixtureFilter.isBlank() || path.toString().contains(fixtureFilter))
                    .map(this::readFixture)
                    .filter(fixture -> fixture.parserTarget().equals("DDL"))
                    .filter(fixture -> fixture.grammarProfile().isBlank())
                    .toList();
        }

        if (fixtures.isEmpty() && !System.getProperty("correctnessFixtureFilter", "").isBlank()) {
            return;
        }
        assertTrue(!fixtures.isEmpty(), "Expected DDL correctness fixtures");
        assertAll("full-grammer DDL shadow parity",
                fixtures.stream().map(fixture -> (Executable) () -> assertFixtureParity(fixture)).toList());
    }

    private void assertFixtureParity(Fixture fixture) throws Exception {
        DatabaseAdaptor adaptor = adaptor(fixture.databaseType());
        StructuredDdlParser current = adaptor.structuredDdlParser()
                .orElseThrow(() -> new IllegalStateException("No structured DDL parser for " + fixture.databaseType()));
        StructuredDdlParser shadow = FullGrammerDdlParserFactory.create(
                FullGrammerProfileRequest.builder()
                        .databaseType(fixture.databaseType())
                        .configuredProfile(fixture.grammarProfile())
                        .configuredVersion(versionFor(fixture))
                        .configuredVersionSource(versionFor(fixture).isBlank() ? "UNKNOWN" : "CONFIG")
                        .build(),
                current);
        String ddl = Files.readString(fixture.inputFile());
        DdlRelationExtractionVisitor extractor = new DdlRelationExtractionVisitor();

        StructuredParseResult currentResult = current.parseDdl(ddl, fixture.id() + ".ddl.sql", context());
        StructuredParseResult shadowResult = shadow.parseDdl(ddl, fixture.id() + ".ddl.sql", context());
        TreeSet<String> currentRelations = new TreeSet<>(extractor.extract(ddl, fixture.id(), currentResult)
                .stream()
                .map(this::fingerprint)
                .toList());
        TreeSet<String> shadowRelations = new TreeSet<>(extractor.extract(ddl, fixture.id(), shadowResult)
                .stream()
                .map(this::fingerprint)
                .toList());

        TreeSet<String> missing = new TreeSet<>(currentRelations);
        missing.removeAll(shadowRelations);
        TreeSet<String> extra = new TreeSet<>(shadowRelations);
        extra.removeAll(currentRelations);
        List<String> shadowWarningCodes = shadowResult.warnings().stream()
                .map(WarningMessage::code)
                .sorted()
                .toList();

        // full-grammer DDL parsing is diagnostic-first: recoverable syntax warnings are
        // reported, but shadow parity is about not losing current token-event DDL relations.
        assertTrue(missing.isEmpty(),
                () -> fixture.id()
                        + " missingCurrentDdlRelations=" + missing
                        + " extraFullGrammerDdlRelations=" + extra
                        + " fullGrammerDdlWarningCodes=" + shadowWarningCodes);
    }

    private AdaptorContext context() {
        return new AdaptorContext(new ScanScope(null, null, List.of(), List.of()), Map.of());
    }

    private String fingerprint(RelationshipCandidate relation) {
        return relation.relationType().name() + ":"
                + endpoint(relation.source()) + "->" + endpoint(relation.target()) + ":"
                + relation.evidence().stream().findFirst().orElseThrow().type().name();
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

    private String versionFor(Fixture fixture) {
        if (!fixture.databaseVersion().isBlank() || !fixture.grammarProfile().isBlank()) {
            return fixture.databaseVersion();
        }
        return defaultVersion(fixture.databaseType());
    }

    private String defaultVersion(DatabaseType databaseType) {
        return switch (databaseType) {
            case MYSQL -> "8.0";
            case POSTGRESQL -> "16";
            default -> "";
        };
    }

    private Fixture readFixture(Path manifest) {
        try {
            Map<String, String> values = readSimpleManifest(manifest);
            Path root = manifest.getParent();
            return new Fixture(
                    values.get("id"),
                    DatabaseType.valueOf(values.get("databaseType")),
                    values.get("parserTarget"),
                    root.resolve(values.get("input")).normalize(),
                    values.getOrDefault("grammarProfile", ""),
                    values.getOrDefault("databaseVersion", ""));
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
            Path inputFile,
            String grammarProfile,
            String databaseVersion
    ) {
    }
}
