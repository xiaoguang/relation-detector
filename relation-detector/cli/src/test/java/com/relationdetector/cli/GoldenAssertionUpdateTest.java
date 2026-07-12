package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.StatementSourceType;

class GoldenAssertionUpdateTest {
    @TempDir
    Path tempDir;

    @Test
    void updateModeDoesNotRewriteSemanticallyUnchangedGoldenFiles() throws Exception {
        String input = "SELECT 1;\n";
        Path inputFile = tempDir.resolve("input.sql");
        Path relations = tempDir.resolve("expected-relations.json");
        Path lineage = tempDir.resolve("expected-lineage.json");
        Path naming = tempDir.resolve("expected-naming-evidence.json");
        Path diagnostics = tempDir.resolve("expected-diagnostics.json");
        Files.writeString(inputFile, input);
        Map<Path, String> original = Map.of(
                relations, "{ \"fingerprints\" : [ ], \"forbiddenTables\" : [ ] }\n",
                lineage, "{ \"applicable\" : true, \"fingerprints\" : [ ], \"forbiddenSources\" : [ ], \"forbiddenTargets\" : [ ], \"warningCodes\" : { } }\n",
                naming, "{ \"applicable\" : true, \"fingerprints\" : [ ] }\n",
                diagnostics, "{ \"fixtureSha256\" : \"" + sha256(input) + "\", \"warningCodes\" : { } }\n");
        for (var entry : original.entrySet()) {
            Files.writeString(entry.getKey(), entry.getValue());
        }
        CorrectnessFixture fixture = new CorrectnessFixture(
                tempDir.resolve("manifest.yml"), "unchanged", DatabaseType.COMMON, "SQL",
                StatementSourceType.PLAIN_SQL, "SEMICOLON", EvidenceSourceType.PLAIN_SQL,
                "public", inputFile, relations, lineage, naming, diagnostics,
                "", "", "token-event", "", "");
        LoadedFixtureInput loaded = new LoadedFixtureInput(
                input,
                new ExpectedRelations(List.of(), List.of()),
                new ExpectedDiagnostics(sha256(input), Map.of()),
                new ExpectedLineage(true, List.of(), List.of(), List.of(), Map.of()),
                new ExpectedNamingEvidence(true, List.of()));

        String previous = System.getProperty("updateCorrectnessGold");
        System.setProperty("updateCorrectnessGold", "true");
        try {
            new GoldenAssertion().assertFixture(
                    fixture, loaded, new FixtureActualResult(List.of(), List.of(), List.of(), List.of()));
        } finally {
            if (previous == null) {
                System.clearProperty("updateCorrectnessGold");
            } else {
                System.setProperty("updateCorrectnessGold", previous);
            }
        }

        for (var entry : original.entrySet()) {
            assertEquals(entry.getValue(), Files.readString(entry.getKey()), entry.getKey().getFileName().toString());
        }
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
