package com.relationdetector.core.parser;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class GrammarTerminologyArchitectureTest {
    @Test
    void trackedSourceTreeUsesCorrectGrammarTerminology() throws IOException {
        String forbidden = "gram" + "mer";
        Path root = workspaceRoot();
        List<String> offenders = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(candidate -> !candidate.toString().contains("/.git/"))
                    .filter(candidate -> !candidate.toString().contains("/target/"))
                    .filter(candidate -> !candidate.toString().contains("/.mvn/build-cache/"))
                    .toList()) {
                Path relative = root.relativize(path);
                if (relative.toString().toLowerCase().contains(forbidden)) {
                    offenders.add(relative.toString());
                    continue;
                }
                try {
                    String text = Files.readString(path, StandardCharsets.UTF_8);
                    if (text.toLowerCase().contains(forbidden)) offenders.add(relative.toString());
                } catch (java.nio.charset.MalformedInputException ignored) {
                    // Binary artifacts are not source terminology.
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Legacy grammar spelling remains: " + offenders);
    }

    private Path workspaceRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("relation-detector"))
                    && Files.isDirectory(current.resolve("docs"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate workspace root");
    }
}
