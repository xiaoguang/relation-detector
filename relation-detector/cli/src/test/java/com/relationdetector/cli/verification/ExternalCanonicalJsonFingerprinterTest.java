package com.relationdetector.cli.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

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

    @Test
    void fingerprintsWideObjectsUnderABoundedFileDescriptorLimit() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase().contains("windows"));
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c",
                "ulimit -n 96; exec \"$JAVA_BIN\" -cp \"$TEST_CP\" "
                        + CanonicalFingerprintFileDescriptorProbe.class.getName() + " \"$PROBE_DIR\" 32")
                .redirectErrorStream(true)
                .redirectOutput(tempDir.resolve("probe.log").toFile());
        builder.environment().put("JAVA_BIN", java);
        builder.environment().put("TEST_CP", classPath);
        builder.environment().put("PROBE_DIR", tempDir.resolve("probe").toString());

        Process process = builder.start();
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "wide-object probe timed out");
        assertEquals(0, process.exitValue(), Files.readString(tempDir.resolve("probe.log")));

        ProcessBuilder constrainedBuilder = new ProcessBuilder("/bin/bash", "-c",
                "ulimit -n 64; exec \"$JAVA_BIN\" -cp \"$TEST_CP\" "
                        + CanonicalFingerprintFileDescriptorProbe.class.getName() + " \"$PROBE_DIR\" 4")
                .redirectErrorStream(true)
                .redirectOutput(tempDir.resolve("constrained-probe.log").toFile());
        constrainedBuilder.environment().put("JAVA_BIN", java);
        constrainedBuilder.environment().put("TEST_CP", classPath);
        constrainedBuilder.environment().put("PROBE_DIR", tempDir.resolve("constrained-probe").toString());

        Process constrained = constrainedBuilder.start();
        assertTrue(constrained.waitFor(30, TimeUnit.SECONDS), "constrained wide-object probe timed out");
        assertEquals(0, constrained.exitValue(), Files.readString(tempDir.resolve("constrained-probe.log")));
    }
}
