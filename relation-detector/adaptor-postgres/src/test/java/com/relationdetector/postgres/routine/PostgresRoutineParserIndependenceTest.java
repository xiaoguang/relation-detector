package com.relationdetector.postgres.routine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class PostgresRoutineParserIndependenceTest {
    @Test
    void tokenEventRoutinePathDoesNotImportVersionedOrFullGrammarParsers() throws IOException {
        Path adaptor = adaptorRoot();
        List<Path> roots = List.of(
                adaptor.resolve("src/main/java/com/relationdetector/postgres/tokenevent"),
                adaptor.resolve("src/main/java/com/relationdetector/postgres/plpgsql/tokenevent"));

        for (Path root : roots) {
            try (Stream<Path> files = Files.walk(root)) {
                List<Path> offenders = files.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> containsAny(path, ".plpgsql.v16", ".plpgsql.v17", ".plpgsql.v18",
                                ".postgres.fullgrammar"))
                        .toList();
                assertTrue(offenders.isEmpty(), "token-event routine path must stay independent: " + offenders);
            }
        }
    }

    @Test
    void eachFullGrammarRoutinePathUsesOnlyItsOwnVersion() throws IOException {
        Path adaptor = adaptorRoot();
        for (String version : List.of("v16", "v17", "v18")) {
            Path visitor = adaptor.resolve("src/main/java/com/relationdetector/postgres/fullgrammar")
                    .resolve(version).resolve("PostgresFullGrammarParseTreeVisitor.java");
            String text = Files.readString(visitor);
            assertTrue(text.contains("postgres.plpgsql." + version),
                    version + " full grammar must use its matching PL/pgSQL parser");
            assertFalse(text.contains("postgres.plpgsql.tokenevent"),
                    version + " full grammar must not use token-event PL/pgSQL");
            for (String other : List.of("v16", "v17", "v18")) {
                if (!other.equals(version)) {
                    assertFalse(text.contains("postgres.plpgsql." + other),
                            version + " full grammar must not use " + other + " PL/pgSQL");
                }
            }
        }
    }

    private boolean containsAny(Path path, String... needles) {
        try {
            String text = Files.readString(path);
            for (String needle : needles) if (text.contains(needle)) return true;
            return false;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Path adaptorRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("relation-detector/adaptor-postgres");
            if (Files.isDirectory(candidate)) return candidate;
            candidate = current.resolve("adaptor-postgres");
            if (Files.isDirectory(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate adaptor-postgres");
    }
}
