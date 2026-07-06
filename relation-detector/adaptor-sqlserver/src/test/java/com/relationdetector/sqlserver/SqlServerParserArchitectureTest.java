package com.relationdetector.sqlserver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.core.fullgrammer.FullGrammerDialectModule;
import com.relationdetector.sqlserver.fullgrammer.v2016.SqlServer2016FullGrammerDialectModule;
import com.relationdetector.sqlserver.fullgrammer.v2017.SqlServer2017FullGrammerDialectModule;
import com.relationdetector.sqlserver.fullgrammer.v2019.SqlServer2019FullGrammerDialectModule;
import com.relationdetector.sqlserver.fullgrammer.v2022.SqlServer2022FullGrammerDialectModule;
import com.relationdetector.sqlserver.fullgrammer.v2025.SqlServer2025FullGrammerDialectModule;

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
    void versionedFullGrammerFilesExposeRealGrammarDifferences() throws IOException {
        Path antlrRoot = repoRoot().resolve("adaptor-sqlserver/src/main/antlr4/com/relationdetector/sqlserver/fullgrammer");

        String parser2016 = Files.readString(antlrRoot.resolve("v2016").resolve("SqlServerFullGrammerParser.g4"));
        String parser2017 = Files.readString(antlrRoot.resolve("v2017").resolve("SqlServerFullGrammerParser.g4"));
        String parser2019 = Files.readString(antlrRoot.resolve("v2019").resolve("SqlServerFullGrammerParser.g4"));
        String parser2022 = Files.readString(antlrRoot.resolve("v2022").resolve("SqlServerFullGrammerParser.g4"));
        String parser2025 = Files.readString(antlrRoot.resolve("v2025").resolve("SqlServerFullGrammerParser.g4"));
        String lexer2022 = Files.readString(antlrRoot.resolve("v2022").resolve("SqlServerFullGrammerLexer.g4"));
        String lexer2025 = Files.readString(antlrRoot.resolve("v2025").resolve("SqlServerFullGrammerLexer.g4"));

        assertNotEquals(parser2016, parser2017, "2017 must add grammar beyond the 2016 baseline");
        assertEquals(parser2017, parser2019, "2019 currently inherits the 2017 syntax surface");
        assertNotEquals(parser2019, parser2022, "2022 must add grammar beyond 2019");
        assertNotEquals(parser2022 + lexer2022, parser2025 + lexer2025, "2025 must add grammar beyond 2022");
    }

    @Test
    void versionedFullGrammerRejectsHighVersionTsqlInLowerVersions() {
        List<FullGrammerDialectModule> modules = List.of(
                new SqlServer2016FullGrammerDialectModule(),
                new SqlServer2017FullGrammerDialectModule(),
                new SqlServer2019FullGrammerDialectModule(),
                new SqlServer2022FullGrammerDialectModule(),
                new SqlServer2025FullGrammerDialectModule());

        String stringAgg = """
                SELECT STRING_AGG(CONVERT(NVARCHAR(MAX), o.order_number), ',') WITHIN GROUP (ORDER BY o.order_number)
                FROM dbo.sales_orders o
                """;
        assertSqlSyntaxErrors(modules.get(0), stringAgg, 1);
        for (int i = 1; i < modules.size(); i++) {
            assertSqlSyntaxErrors(modules.get(i), stringAgg, 0);
        }

        String dateTrunc = "SELECT DATETRUNC(month, o.order_date) FROM dbo.sales_orders o";
        for (int i = 0; i < 3; i++) {
            assertSqlSyntaxErrors(modules.get(i), dateTrunc, 1);
        }
        for (int i = 3; i < modules.size(); i++) {
            assertSqlSyntaxErrors(modules.get(i), dateTrunc, 0);
        }

        String generateSeries = "SELECT value FROM GENERATE_SERIES(1, 3) AS s";
        for (int i = 0; i < 3; i++) {
            assertSqlSyntaxErrors(modules.get(i), generateSeries, 1);
        }
        for (int i = 3; i < modules.size(); i++) {
            assertSqlSyntaxErrors(modules.get(i), generateSeries, 0);
        }

        String vectorDdl = """
                CREATE TABLE dbo.product_embeddings (
                    product_id BIGINT NOT NULL,
                    embedding VECTOR(3) NULL
                )
                """;
        for (int i = 0; i < 4; i++) {
            assertDdlSyntaxErrors(modules.get(i), vectorDdl, 1);
        }
        assertDdlSyntaxErrors(modules.get(4), vectorDdl, 0);
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

    private static void assertSqlSyntaxErrors(FullGrammerDialectModule module, String sql, int expected) {
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL,
                module.profile().id() + "-version-boundary.sql", 1, 1, Map.of());
        StructuredParseResult result = module.sqlParser().parseSql(statement, null);
        int actual = ((Number) result.attributes().getOrDefault("syntaxErrors", 0)).intValue();
        if (expected == 0) {
            assertEquals(0, actual, module.profile().id() + " SQL syntaxErrors");
        } else {
            assertTrue(actual > 0, module.profile().id() + " should reject high-version SQL syntax");
        }
    }

    private static void assertDdlSyntaxErrors(FullGrammerDialectModule module, String ddl, int expected) {
        StructuredParseResult result = module.structuredDdlParser().parseDdl(ddl,
                module.profile().id() + "-version-boundary.sql", null);
        int actual = ((Number) result.attributes().getOrDefault("syntaxErrors", 0)).intValue();
        if (expected == 0) {
            assertEquals(0, actual, module.profile().id() + " DDL syntaxErrors");
        } else {
            assertTrue(actual > 0, module.profile().id() + " should reject high-version DDL syntax");
        }
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
            if (isRelationDetectorRoot(current)) {
                return current;
            }
            Path relationDetector = current.resolve("relation-detector");
            if (isRelationDetectorRoot(relationDetector)) {
                return relationDetector;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }

    private static boolean isRelationDetectorRoot(Path path) {
        return Files.isDirectory(path.resolve("adaptor-sqlserver"))
                && Files.isDirectory(path.resolve("sample-data"))
                && Files.isDirectory(path.resolve("test-fixtures"));
    }
}
