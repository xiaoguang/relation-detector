package com.relationdetector.core.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class GrammarOwnershipArchitectureTest {
    @Test
    void everyGrammarSourceIsOwnedByTheGrammarAggregator() throws IOException {
        Path root = repoRoot();
        List<Path> offenders;
        try (Stream<Path> stream = Files.walk(root)) {
            offenders = stream
                    .filter(path -> path.getFileName().toString().endsWith(".g4"))
                    .filter(path -> !path.startsWith(root.resolve("grammar")))
                    .map(root::relativize)
                    .toList();
        }
        assertTrue(offenders.isEmpty(), "All tracked grammar sources belong under grammar/: " + offenders);
    }

    @Test
    void coreAndAdaptorsDoNotRunAntlrGeneration() throws IOException {
        Path root = repoRoot();
        for (String module : List.of("core", "adaptor-mysql", "adaptor-postgres", "adaptor-oracle",
                "adaptor-sqlserver")) {
            String pom = Files.readString(root.resolve(module).resolve("pom.xml"));
            assertFalse(pom.contains("antlr4-maven-plugin"), module + " must consume generated grammar artifacts");
        }
    }

    @Test
    void grammarModulesDoNotDependOnRuntimeBusinessModules() throws IOException {
        Path grammar = repoRoot().resolve("grammar");
        try (Stream<Path> stream = Files.walk(grammar)) {
            List<Path> offenders = stream
                    .filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .filter(path -> {
                        try {
                            String pom = Files.readString(path);
                            return pom.contains("relation-detector-core")
                                    || pom.contains("relation-detector-cli")
                                    || pom.contains("relation-detector-adaptor-");
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .map(grammar::relativize)
                    .toList();
            assertTrue(offenders.isEmpty(), "Grammar modules must stay dependency leaves: " + offenders);
        }
    }

    @Test
    void scriptFramingUsesSpiV4VocabularyOnly() throws IOException {
        Path contracts = repoRoot().resolve("contracts/src/main/java/com/relationdetector/contracts");
        assertTrue(Files.exists(contracts.resolve("spi/DialectScriptFramer.java")));
        assertTrue(Files.exists(contracts.resolve("parse/ScriptFrameRequest.java")));
        assertTrue(Files.exists(contracts.resolve("parse/ScriptFrameResult.java")));
        assertFalse(Files.exists(contracts.resolve("spi/DialectScript" + "Parser.java")));
        assertFalse(Files.exists(contracts.resolve("parse/Script" + "ParseRequest.java")));
        assertFalse(Files.exists(contracts.resolve("parse/Script" + "ParseResult.java")));

        String parsers = Files.readString(contracts.resolve("spi/AdaptorParsers.java"));
        assertTrue(parsers.contains("DialectScriptFramer scriptFramer"));
        String version = Files.readString(contracts.resolve("spi/AdaptorApiVersion.java"));
        assertTrue(version.contains("CURRENT = 4"));
    }

    @Test
    void postgresRoutineGrammarsAreModeAndVersionOwned() {
        Path grammar = repoRoot().resolve("grammar");
        for (String module : List.of("postgres-plpgsql-token-event", "plpgsql-v16", "plpgsql-v17", "plpgsql-v18")) {
            assertTrue(Files.isDirectory(grammar.resolve(module)), "Missing PostgreSQL routine grammar: " + module);
        }
        assertFalse(Files.exists(grammar.resolve("postgres-" + "routine")),
                "The shared PostgreSQL routine grammar must be removed");
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("core"))
                    && Files.isDirectory(current.resolve("grammar"))
                    && Files.isDirectory(current.resolve("adaptor-postgres"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate relation-detector root");
    }
}
