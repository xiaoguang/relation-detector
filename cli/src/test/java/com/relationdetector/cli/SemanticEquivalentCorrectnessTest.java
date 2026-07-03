package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Cross-parser semantic-equivalence guard.
 *
 * <p>CorrectnessFixtureRunnerTest proves each parser output matches its own
 * golden. This test proves selected business-equivalent fixtures across
 * parser families match the same canonical relationship / lineage contract.
 */
class SemanticEquivalentCorrectnessTest {
    @Test
    void semanticEquivalentScenariosMatchCanonicalGoldens() throws Exception {
        Path root = workspaceRoot().resolve("test-fixtures/semantic-equivalent");
        List<Path> scenarios;
        try (Stream<Path> paths = Files.exists(root) ? Files.list(root) : Stream.empty()) {
            scenarios = paths
                    .filter(Files::isDirectory)
                    .sorted()
                    .toList();
        }

        assertFalse(scenarios.isEmpty(), "Expected at least one semantic-equivalent scenario under " + root);

        List<Executable> checks = new ArrayList<>();
        for (Path scenario : scenarios) {
            checks.addAll(checkScenario(scenario));
        }
        assertAll("semantic-equivalent scenarios", checks);
    }

    private List<Executable> checkScenario(Path scenario) throws Exception {
        Set<String> canonicalRelations = fingerprints(scenario.resolve("expected-relations.json"));
        Set<String> canonicalLineage = fingerprints(scenario.resolve("expected-lineage.json"));
        Path fixturesFile = scenario.resolve("fixtures.txt");
        List<String> fixturePaths = Files.readAllLines(fixturesFile).stream()
                .map(String::trim)
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .sorted()
                .toList();
        List<Executable> checks = new ArrayList<>();
        checks.add(() -> assertFalse(fixturePaths.isEmpty(), scenario + " must list correctness fixtures"));
        for (String fixturePath : fixturePaths) {
            Path fixture = workspaceRoot().resolve(fixturePath).normalize();
            checks.add(() -> assertEquals(canonicalRelations,
                    fingerprints(fixture.resolve("expected-relations.json")),
                    scenario.getFileName() + " relation equivalence for " + fixturePath));
            checks.add(() -> assertEquals(canonicalLineage,
                    fingerprints(fixture.resolve("expected-lineage.json")),
                    scenario.getFileName() + " lineage equivalence for " + fixturePath));
        }
        return checks;
    }

    private static Set<String> fingerprints(Path file) throws Exception {
        if (!Files.exists(file)) {
            return Set.of();
        }
        Matcher matcher = Pattern.compile("\"fingerprints\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL)
                .matcher(Files.readString(file));
        if (!matcher.find()) {
            return Set.of();
        }
        String body = matcher.group(1).trim();
        if (body.isBlank()) {
            return Set.of();
        }
        Matcher item = Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(body);
        Set<String> values = new TreeSet<>();
        while (item.find()) {
            values.add(item.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
        }
        return values;
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
}
