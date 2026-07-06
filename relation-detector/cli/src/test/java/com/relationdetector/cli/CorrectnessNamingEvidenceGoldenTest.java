package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorrectnessNamingEvidenceGoldenTest {
    @TempDir
    Path tempDir;

    @Test
    void fixtureRunnerAssertsNamingEvidenceGolden() throws Exception {
        String ddl = """
                CREATE TABLE customers (
                  id INT PRIMARY KEY
                );
                CREATE TABLE orders (
                  id INT PRIMARY KEY,
                  customer_id INT
                );
                """;
        Files.writeString(tempDir.resolve("input.sql"), ddl);
        Files.writeString(tempDir.resolve("expected-relations.json"), """
                {
                  "fingerprints": [],
                  "forbiddenTables": []
                }
                """);
        Files.writeString(tempDir.resolve("expected-diagnostics.json"), """
                {
                  "fixtureSha256": "%s",
                  "warningCodes": {}
                }
                """.formatted(sha256(ddl)));
        Files.writeString(tempDir.resolve("expected-naming-evidence.json"), """
                {
                  "fingerprints": [
                    "TABLE_ID:orders.user_id->customers.id:NAMING_MATCH"
                  ]
                }
                """);
        Files.writeString(tempDir.resolve("manifest.yml"), """
                id: naming-evidence-mismatch
                databaseType: MYSQL
                parserTarget: DDL
                structuredParser: common-token-event
                input: input.sql
                expectedRelations: expected-relations.json
                expectedNamingEvidence: expected-naming-evidence.json
                expectedDiagnostics: expected-diagnostics.json
                """);

        AssertionError error = assertThrows(AssertionError.class,
                () -> new CorrectnessFixtureExecutor().runFixture(CorrectnessFixture.read(tempDir.resolve("manifest.yml"))));

        assertTrue(error.getMessage().contains("naming evidence fingerprints"));
    }

    private String sha256(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
