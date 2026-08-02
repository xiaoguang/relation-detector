package com.relationdetector.semantic.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

final class ProductionReachabilityAuditTest {
    @Test
    void handwrittenProductionClassesAreReachableFromExecutableOrSpiRoots() throws Exception {
        Path root = repositoryRoot();
        Process process = new ProcessBuilder(
                "bash",
                root.resolve("relation-detector/scripts/audit/audit-java-reachability.sh").toString(),
                "--check")
                .directory(root.toFile())
                .redirectErrorStream(true)
                .start();

        assertTrue(process.waitFor(3, TimeUnit.MINUTES), "production reachability audit timed out");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
    }

    private Path repositoryRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("docs"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IOException("repository root not found");
        }
        return current;
    }
}
