package com.relationdetector.core.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class DialectGrammarArchitectureTest {
    @Test
    void coreAntlrDirectoryContainsOnlyCommonGrammar() throws IOException {
        Path root = repoRoot();
        Path coreAntlr = root.resolve("core/src/main/antlr4/com/relationdetector/core/antlr");

        List<Path> grammars;
        try (Stream<Path> stream = Files.walk(coreAntlr)) {
            grammars = stream
                    .filter(path -> path.getFileName().toString().endsWith(".g4"))
                    .map(coreAntlr::relativize)
                    .toList();
        }

        assertTrue(grammars.contains(Path.of("common/CommonRelationSql.g4")),
                "core must keep the portable common grammar");
        assertFalse(grammars.stream().anyMatch(path -> startsWithDialect(path, "mysql")),
                "MySQL token-event grammar belongs in adaptor-mysql, not core");
        assertFalse(grammars.stream().anyMatch(path -> startsWithDialect(path, "postgres")),
                "PostgreSQL token-event grammar belongs in adaptor-postgres, not core");
        assertFalse(grammars.stream().anyMatch(path -> startsWithDialect(path, "oracle")),
                "Oracle token-event grammar belongs in adaptor-oracle, not core");
    }

    @Test
    void productionCodeDoesNotImportDialectGeneratedClassesFromCore() throws IOException {
        Path root = repoRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> containsDialectCoreGeneratedImport(path))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Dialect generated ANTLR classes must come from adaptor modules, offenders=" + offenders);
        }
    }

    @Test
    void fullGrammerProductionCodeDoesNotImportDialectTokenEventPackages() throws IOException {
        Path root = repoRoot();
        List<String> forbiddenImports = List.of(
                "com.relationdetector.mysql.tokenevent",
                "com.relationdetector.postgres.tokenevent",
                "com.relationdetector.oracle.tokenevent");

        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().contains("/fullgrammer/"))
                    .filter(path -> containsAny(path, forbiddenImports))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "full-grammer modules must not import token-event modules, offenders=" + offenders);
        }
    }

    private static boolean startsWithDialect(Path path, String dialect) {
        return path.getNameCount() > 0
                && path.getName(0).toString().toLowerCase(Locale.ROOT).equals(dialect);
    }

    private static boolean containsDialectCoreGeneratedImport(Path path) {
        try {
            String text = Files.readString(path);
            return text.contains("com.relationdetector.core.antlr.mysql")
                    || text.contains("com.relationdetector.core.antlr.postgres")
                    || text.contains("com.relationdetector.core.antlr.oracle");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }

    private static boolean containsAny(Path path, List<String> needles) {
        try {
            String text = Files.readString(path);
            return needles.stream().anyMatch(text::contains);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("core"))
                    && Files.isDirectory(current.resolve("adaptor-mysql"))
                    && Files.isDirectory(current.resolve("adaptor-postgres"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }
}
