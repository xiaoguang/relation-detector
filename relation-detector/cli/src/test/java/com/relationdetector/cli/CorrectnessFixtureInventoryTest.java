package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class CorrectnessFixtureInventoryTest {
    @Test
    void executionIdentityAndInputContentAreUnique() throws Exception {
        Path root = TestWorkspacePaths.relationDetectorRoot().resolve("test-fixtures/correctness");
        List<Path> manifests;
        try (Stream<Path> paths = Files.walk(root)) {
            manifests = paths
                    .filter(path -> path.getFileName().toString().equals("manifest.yml"))
                    .sorted()
                    .toList();
        }

        Map<ExecutionIdentity, List<Path>> fixturesByIdentity = new LinkedHashMap<>();
        for (Path manifest : manifests) {
            CorrectnessFixture fixture = CorrectnessFixture.read(manifest);
            ExecutionIdentity identity = new ExecutionIdentity(
                    fixture.databaseType().name(),
                    fixture.parserTarget(),
                    fixture.sourceType().name(),
                    fixture.statementFormat(),
                    fixture.evidenceSourceType().name(),
                    fixture.schema(),
                    fixture.objectSourceFilter(),
                    fixture.structuredParser(),
                    fixture.parserMode(),
                    fixture.grammarProfile(),
                    fixture.databaseVersion(),
                    sourceAssetIdentity(root, fixture.inputFile()),
                    sha256(fixture.inputFile()));
            fixturesByIdentity.computeIfAbsent(identity, ignored -> new ArrayList<>())
                    .add(root.relativize(manifest));
        }

        List<List<Path>> duplicates = fixturesByIdentity.values().stream()
                .filter(paths -> paths.size() > 1)
                .toList();
        assertTrue(duplicates.isEmpty(),
                "Correctness fixtures with the same execution identity and input must merge assertions: " + duplicates);
    }

    private String sha256(Path input) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input)));
    }

    private String sourceAssetIdentity(Path correctnessRoot, Path input) {
        Path normalizedInput = input.toAbsolutePath().normalize();
        Path normalizedCorrectnessRoot = correctnessRoot.toAbsolutePath().normalize();
        if (normalizedInput.startsWith(normalizedCorrectnessRoot)) {
            return "<fixture-local>";
        }
        Path repositoryRoot = TestWorkspacePaths.repositoryRoot().toAbsolutePath().normalize();
        return repositoryRoot.relativize(normalizedInput).toString().replace('\\', '/');
    }

    private record ExecutionIdentity(
            String databaseType,
            String parserTarget,
            String sourceType,
            String statementFormat,
            String evidenceSourceType,
            String schema,
            String objectSourceFilter,
            String structuredParser,
            String parserMode,
            String grammarProfile,
            String databaseVersion,
            String sourceAsset,
            String inputHash
    ) {
    }
}
