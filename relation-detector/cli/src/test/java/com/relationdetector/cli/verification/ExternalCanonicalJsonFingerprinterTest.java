package com.relationdetector.cli.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ExternalCanonicalJsonFingerprinterTest {
    @TempDir
    Path tempDir;

    @Test
    void matchesHistoricalPythonCanonicalAndSemanticFingerprints() throws Exception {
        Path input = tempDir.resolve("input.json");
        Files.writeString(input, """
                {
                  "\uE000": 1,
                  "\uD800\uDC00": 2,
                  "generatedAt": "volatile",
                  "nested": {
                    "parserMode": "full",
                    "v": 1e-5,
                    "x": 1e-4
                  },
                  "array": [3, 2, 1]
                }
                """);
        Path workspace = tempDir.resolve("workspace");
        ExternalCanonicalJsonFingerprinter fingerprinter =
                new ExternalCanonicalJsonFingerprinter(workspace, 1);

        assertEquals(
                "68597929549c88b63012cd72174d111cdf62ec6d471654c0abe7ee9cf214e2fc",
                fingerprinter.fingerprint(input, CanonicalFingerprintMode.CANONICAL));
        assertEquals(
                "c988623572fc3e270e4cfcfc0184101aaff2c15d915d949642464c56e279054e",
                fingerprinter.fingerprint(input, CanonicalFingerprintMode.SEMANTIC));
        assertTrue(Files.notExists(workspace));
    }

    @Test
    void rejectsDuplicateObjectKeysAndCleansTemporaryFiles() throws Exception {
        Path input = tempDir.resolve("duplicate.json");
        Files.writeString(input, "{\"a\":1,\"a\":2}");
        Path workspace = tempDir.resolve("workspace");
        ExternalCanonicalJsonFingerprinter fingerprinter =
                new ExternalCanonicalJsonFingerprinter(workspace, 1);

        assertThrows(ReleaseVerificationException.class,
                () -> fingerprinter.fingerprint(input, CanonicalFingerprintMode.CANONICAL));
        assertTrue(Files.notExists(workspace));
    }

    @Test
    void rejectsDamagedJsonAndCleansTemporaryFiles() throws Exception {
        Path input = tempDir.resolve("damaged.json");
        Files.writeString(input, "{\"a\":[1,2}");
        Path workspace = tempDir.resolve("workspace");

        assertThrows(ReleaseVerificationException.class,
                () -> new ExternalCanonicalJsonFingerprinter(workspace, 2)
                        .fingerprint(input, CanonicalFingerprintMode.CANONICAL));
        assertTrue(Files.notExists(workspace));
    }
}
