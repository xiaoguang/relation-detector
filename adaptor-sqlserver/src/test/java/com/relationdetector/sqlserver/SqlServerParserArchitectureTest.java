package com.relationdetector.sqlserver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class SqlServerParserArchitectureTest {
    private static final List<String> VERSIONS = List.of("v2016", "v2017", "v2019", "v2022", "v2025");

    @Test
    void fullGrammerDoesNotDependOnTokenEventParser() throws IOException {
        Path fullGrammerRoot = repoRoot().resolve("adaptor-sqlserver/src/main/java/com/relationdetector/sqlserver/fullgrammer");
        String combined;
        try (Stream<Path> paths = Files.walk(fullGrammerRoot)) {
            combined = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(SqlServerParserArchitectureTest::readString)
                    .reduce("", String::concat);
        }

        assertFalse(combined.contains("SqlServerTokenEventStructuredSqlParser"));
        assertFalse(combined.contains("SqlServerTokenEventStructuredDdlParser"));
        assertFalse(combined.contains("com.relationdetector.sqlserver.tokenevent"));
    }

    @Test
    void tokenEventDoesNotDependOnFullGrammerParserCode() throws IOException {
        Path tokenEventRoot = repoRoot().resolve("adaptor-sqlserver/src/main/java/com/relationdetector/sqlserver/tokenevent");
        String combined;
        try (Stream<Path> paths = Files.walk(tokenEventRoot)) {
            combined = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(SqlServerParserArchitectureTest::readString)
                    .reduce("", String::concat);
        }

        assertFalse(combined.contains("com.relationdetector.sqlserver.fullgrammer"));
        assertFalse(combined.contains("SqlServerParseTreeEventCollector"));
    }

    @Test
    void tokenEventUsesSingleCombinedGrammarFile() {
        Path tokenRoot = repoRoot().resolve("adaptor-sqlserver/src/main/antlr4/com/relationdetector/sqlserver/tokenevent");

        assertTrue(Files.exists(tokenRoot.resolve("SqlServerRelationSql.g4")));
        assertFalse(Files.exists(tokenRoot.resolve("SqlServerRelationSqlLexer.g4")));
        assertFalse(Files.exists(tokenRoot.resolve("SqlServerRelationSqlParser.g4")));
    }

    @Test
    void versionedFullGrammerUsesIndependentGeneratedGrammarFiles() {
        Path antlrRoot = repoRoot().resolve("adaptor-sqlserver/src/main/antlr4/com/relationdetector/sqlserver/fullgrammer");
        for (String version : VERSIONS) {
            Path versionRoot = antlrRoot.resolve(version);
            assertTrue(Files.exists(versionRoot.resolve("SqlServerFullGrammerLexer.g4")), version);
            assertTrue(Files.exists(versionRoot.resolve("SqlServerFullGrammerParser.g4")), version);
        }
    }

    @Test
    void fullGrammerDoesNotUseStatementLevelUnknownFallback() throws IOException {
        Path antlrRoot = repoRoot().resolve("adaptor-sqlserver/src/main/antlr4/com/relationdetector/sqlserver/fullgrammer");
        for (String version : VERSIONS) {
            String parser = Files.readString(antlrRoot.resolve(version).resolve("SqlServerFullGrammerParser.g4"));
            String lexer = Files.readString(antlrRoot.resolve(version).resolve("SqlServerFullGrammerLexer.g4"));

            assertFalse(parser.contains("unknownStatement"), version);
            assertFalse(parser.contains("looseToken"), version);
            assertFalse(lexer.matches("(?s).*\\n\\s*OTHER\\s*:\\s*\\.\\s*;.*"), version);
        }
    }

    @Test
    void tokenEventGrammarIsACompactFallbackGrammar() throws IOException {
        Path tokenRoot = repoRoot().resolve("adaptor-sqlserver/src/main/antlr4/com/relationdetector/sqlserver/tokenevent");
        Path fullRoot = repoRoot().resolve("adaptor-sqlserver/src/main/antlr4/com/relationdetector/sqlserver/fullgrammer/v2022");

        long tokenLines = lineCount(tokenRoot.resolve("SqlServerRelationSql.g4"));
        long fullLines = lineCount(fullRoot.resolve("SqlServerFullGrammerLexer.g4"))
                + lineCount(fullRoot.resolve("SqlServerFullGrammerParser.g4"));

        assertTrue(tokenLines * 4 < fullLines,
                "token-event grammar should remain a compact structural fallback, not a copy of full-grammer");
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + path, exception);
        }
    }

    private static long lineCount(Path path) throws IOException {
        try (Stream<String> lines = Files.lines(path)) {
            return lines.count();
        }
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("adaptor-sqlserver"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }
}
