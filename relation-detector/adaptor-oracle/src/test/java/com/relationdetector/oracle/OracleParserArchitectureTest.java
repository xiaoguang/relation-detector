package com.relationdetector.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.tokenevent.CommonTokenEventStructuredSqlParser;
import com.relationdetector.oracle.tokenevent.OracleTokenEventStructuredSqlParser;

class OracleParserArchitectureTest {
    private static final List<ForbiddenOracleSqlPattern> FORBIDDEN_ORACLE_SQL_PATTERNS = List.of(
            new ForbiddenOracleSqlPattern("PostgreSQL PL/pgSQL language marker",
                    Pattern.compile("(?i)\\bLANGUAGE\\s+plpgsql\\b")),
            new ForbiddenOracleSqlPattern("PostgreSQL cast operator",
                    Pattern.compile("::[A-Za-z_][A-Za-z0-9_]*(?:\\([^)]*\\))?")),
            new ForbiddenOracleSqlPattern("PostgreSQL WITH RECURSIVE",
                    Pattern.compile("(?i)\\bWITH\\s+RECURSIVE\\b")),
            new ForbiddenOracleSqlPattern("PostgreSQL/MySQL LIMIT clause",
                    Pattern.compile("(?i)\\bLIMIT\\b")),
            new ForbiddenOracleSqlPattern("PostgreSQL string_agg function",
                    Pattern.compile("(?i)\\bstring_agg\\s*\\(")),
            new ForbiddenOracleSqlPattern("PostgreSQL temporal WITHOUT OVERLAPS",
                    Pattern.compile("(?i)\\bWITHOUT\\s+OVERLAPS\\b")),
            new ForbiddenOracleSqlPattern("PostgreSQL PERIOD foreign-key syntax",
                    Pattern.compile("(?is)\\bFOREIGN\\s+KEY\\s*\\([^;]*\\bPERIOD\\b|\\bREFERENCES\\b[^;]*\\bPERIOD\\b")),
            new ForbiddenOracleSqlPattern("PostgreSQL interval cast",
                    Pattern.compile("(?i)::\\s*INTERVAL\\b")),
            new ForbiddenOracleSqlPattern("MySQL AUTO_INCREMENT",
                    Pattern.compile("(?i)\\bAUTO_INCREMENT\\b")),
            new ForbiddenOracleSqlPattern("MySQL ENGINE table option",
                    Pattern.compile("(?i)\\bENGINE\\s*=")),
            new ForbiddenOracleSqlPattern("MySQL ON DUPLICATE KEY UPDATE",
                    Pattern.compile("(?i)\\bON\\s+DUPLICATE\\s+KEY\\s+UPDATE\\b")),
            new ForbiddenOracleSqlPattern("PostgreSQL JSONB helper",
                    Pattern.compile("(?i)\\bjsonb_[a-z0-9_]+\\b")),
            new ForbiddenOracleSqlPattern("PostgreSQL JSON arrow operator",
                    Pattern.compile("->>?")),
            new ForbiddenOracleSqlPattern("PostgreSQL date_trunc function",
                    Pattern.compile("(?i)\\bdate_trunc\\s*\\(")),
            new ForbiddenOracleSqlPattern("PostgreSQL RETURN QUERY statement",
                    Pattern.compile("(?i)\\bRETURN\\s+QUERY\\b")),
            new ForbiddenOracleSqlPattern("PostgreSQL RETURNS TABLE function signature",
                    Pattern.compile("(?i)\\bRETURNS\\s+TABLE\\b")),
            new ForbiddenOracleSqlPattern("PostgreSQL RETURNS function signature",
                    Pattern.compile("(?i)\\bRETURNS\\b")),
            new ForbiddenOracleSqlPattern("PostgreSQL function volatility marker",
                    Pattern.compile("(?i)\\b(?:STABLE|IMMUTABLE|VOLATILE)\\b")),
            new ForbiddenOracleSqlPattern("PostgreSQL search_path command",
                    Pattern.compile("(?i)\\bSET\\s+search_path\\b")),
            new ForbiddenOracleSqlPattern("PostgreSQL AGE date function",
                    Pattern.compile("(?i)\\bAGE\\s*\\(")),
            new ForbiddenOracleSqlPattern("PostgreSQL make_date function",
                    Pattern.compile("(?i)\\bmake_date\\s*\\(")),
            new ForbiddenOracleSqlPattern("PostgreSQL DOW/ISODOW extract field",
                    Pattern.compile("(?i)\\bEXTRACT\\s*\\(\\s*(?:ISODOW|DOW)\\s+FROM\\b")),
            new ForbiddenOracleSqlPattern("Mechanical migration produced VARCHAR2 pseudo column reference",
                    Pattern.compile("(?i)\\b[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*VARCHAR2\\s*\\(")),
            new ForbiddenOracleSqlPattern("Mechanical migration produced VARCHAR2 pseudo DDL/variable name",
                    Pattern.compile("(?im)^\\s*VARCHAR2\\s*\\(")),
            new ForbiddenOracleSqlPattern("Mechanical migration produced postfix numeric cast artifact",
                    Pattern.compile("\\)\\s*\\(\\s*\\d+(?:\\s*,\\s*\\d+)?\\s*\\)")),
            new ForbiddenOracleSqlPattern("Oracle EXTRACT does not support QUARTER",
                    Pattern.compile("(?i)\\bEXTRACT\\s*\\(\\s*QUARTER\\s+FROM\\b")),
            new ForbiddenOracleSqlPattern("Oracle natural baseline must not project EXTRACT comparison as SQL BOOLEAN",
                    Pattern.compile("(?im)^\\s*EXTRACT\\s*\\([^\\n]+\\)\\s*(?:=|<>|!=|<=|>=|<|>)\\s*[^,;]+,\\s*$")),
            new ForbiddenOracleSqlPattern("Oracle natural baseline must not project OR-combined comparisons as SQL BOOLEAN",
                    Pattern.compile("(?im)^\\s*[A-Za-z_$#][A-Za-z0-9_.$#]*\\s*=\\s*[^,;\\n]+\\s+OR\\s+[^,;\\n]+,\\s*$")));

    @Test
    void oracleTokenEventParserUsesOracleGrammarNotCommonParser() {
        assertNotEquals(CommonTokenEventStructuredSqlParser.class,
                OracleTokenEventStructuredSqlParser.class.getSuperclass(),
                "Oracle token-event must use OracleRelationSql generated parser, not CommonRelationSql parser");
    }

    @Test
    void oracleFullGrammerProductionCodeDoesNotDelegateToTokenEventParser() throws IOException {
        Path fullGrammerSource = repoRoot().resolve("adaptor-oracle/src/main/java/com/relationdetector/oracle/fullgrammer");
        try (Stream<Path> stream = Files.walk(fullGrammerSource)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, "OracleTokenEventStructuredSqlParser")
                            || contains(path, "OracleTokenEventStructuredDdlParser"))
                    .map(repoRoot()::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Oracle full-grammer must be generated-parser-backed and cannot delegate to token-event, offenders="
                            + offenders);
        }
    }

    @Test
    void oracleTokenEventProductionCodeDoesNotImportFullGrammerImplementation() throws IOException {
        Path tokenEventSource = repoRoot().resolve("adaptor-oracle/src/main/java/com/relationdetector/oracle/tokenevent");
        try (Stream<Path> stream = Files.walk(tokenEventSource)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, "com.relationdetector.oracle.fullgrammer"))
                    .map(repoRoot()::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Oracle token-event must not import full-grammer implementation packages, offenders="
                            + offenders);
        }
    }

    @Test
    void oracleTokenEventAndFullGrammerVisitorsUseDialectRoutineScope() throws IOException {
        Path mainSource = repoRoot().resolve("adaptor-oracle/src/main/java/com/relationdetector/oracle");
        Path routineScope = mainSource.resolve("routine/OracleRoutineScope.java");
        assertTrue(Files.exists(routineScope), "Oracle routine package must contain a real routine scope helper");

        Path tokenEventPackage = mainSource.resolve("tokenevent");
        String tokenEventSources;
        try (Stream<Path> stream = Files.walk(tokenEventPackage)) {
            tokenEventSources = stream.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (IOException exception) {
                            throw new java.io.UncheckedIOException(exception);
                        }
                    })
                    .collect(java.util.stream.Collectors.joining("\n"));
        }
        Path fullCollector = mainSource.resolve(
                "fullgrammer/common/OracleFullGrammerParseTreeEventCollector.java");
        boolean tokenEventUsesScope = tokenEventSources.contains(
                "com.relationdetector.oracle.routine.OracleRoutineScope")
                && tokenEventSources.contains("routineScope.enterRoutine()")
                && tokenEventSources.contains("routineScope.leaveRoutineEnd(");
        boolean fullGrammerUsesScope = contains(fullCollector,
                "com.relationdetector.oracle.routine.OracleRoutineScope")
                && contains(fullCollector, "routineScope.enterRoutine()")
                && contains(fullCollector, "routineScope.leaveRoutineEnd(");
        assertTrue(tokenEventUsesScope && fullGrammerUsesScope,
                "Oracle token-event visitor and full-grammer common collector must use the dialect-level routine scope, offenders="
                        + (tokenEventUsesScope ? List.of() : List.of(repoRoot().relativize(tokenEventPackage)))
                        + (fullGrammerUsesScope ? List.of() : List.of(repoRoot().relativize(fullCollector))));
    }

    @Test
    void oracleVersionFullGrammarsAreSplitLexerAndParserFiles() throws IOException {
        for (String version : List.of("v12c", "v19c", "v21c", "v26ai")) {
            Path versionRoot = oracleGrammarRoot(version);
            Path combinedGrammar = versionRoot.resolve("OracleFullGrammer.g4");
            Path lexerGrammar = versionRoot.resolve("OracleFullGrammerLexer.g4");
            Path parserGrammar = versionRoot.resolve("OracleFullGrammerParser.g4");

            assertFalse(Files.exists(combinedGrammar),
                    version + " full-grammer must split lexer/parser grammar files");
            assertTrue(Files.exists(lexerGrammar),
                    version + " full-grammer must provide a lexer grammar");
            assertTrue(Files.exists(parserGrammar),
                    version + " full-grammer must provide a parser grammar");

            String lexerText = Files.readString(lexerGrammar);
            String parserText = Files.readString(parserGrammar);
            assertTrue(lexerText.contains("lexer grammar OracleFullGrammerLexer;"),
                    version + " lexer grammar must declare OracleFullGrammerLexer");
            assertTrue(parserText.contains("parser grammar OracleFullGrammerParser;"),
                    version + " parser grammar must declare OracleFullGrammerParser");
            assertTrue(parserText.contains("tokenVocab = OracleFullGrammerLexer"),
                    version + " parser grammar must consume the version lexer tokens");
            assertFalse(parserText.contains("script\n    : token* EOF"),
                    version + " grammar must not accept arbitrary token streams");
            assertFalse(parserText.contains("unknownStatement"),
                    version + " full-grammer must not keep statement-level unknownStatement fallback");
            assertFalse(parserText.contains(": sqlToken+"),
                    version + " full-grammer must not accept arbitrary sqlToken sequences as a statement");
            assertFalse(lexerText.contains("OTHER\n    : ."),
                    version + " grammar must not use catch-all token sink as the full grammar");
        }
    }

    @Test
    void oracleVersionFullGrammarsExposeRealGrammarDifferences() throws IOException {
        String v12c = oracleGrammarText("v12c");
        String v19c = oracleGrammarText("v19c");
        String v21c = oracleGrammarText("v21c");
        String v26ai = oracleGrammarText("v26ai");

        assertNotEquals(v12c, v19c, "Oracle 19c grammar must differ from 12c");
        assertNotEquals(v19c, v21c, "Oracle 21c grammar must differ from 19c");
        assertNotEquals(v21c, v26ai, "Oracle 26ai grammar must differ from 21c");
    }

    @Test
    void oracleFullGrammerDoesNotDeclareNonOracleDialectTokens() throws IOException {
        List<String> forbiddenFragments = List.of(
                "LIMIT:",
                "UNLOGGED:",
                "CONCURRENTLY:",
                "DOUBLE_COLON:",
                "JSON_ARROW_TEXT:",
                "JSON_ARROW:",
                "TABLESAMPLE:",
                "ORDINALITY:",
                "DOLLAR_QUOTED_STRING",
                "IF NOT EXISTS",
                "WITH ORDINALITY",
                "DO NOTHING");

        for (String version : List.of("v12c", "v19c", "v21c", "v26ai")) {
            String text = oracleGrammarText(version);
            List<String> offenders = forbiddenFragments.stream()
                    .filter(text::contains)
                    .toList();
            assertTrue(offenders.isEmpty(),
                    version + " full-grammer must not expose PostgreSQL/MySQL syntax fragments: " + offenders);
        }
    }

    @Test
    void oracleFullGrammerSmokeReportsGeneratedParserSource() {
        var module = new com.relationdetector.oracle.fullgrammer.v26ai.OracleFullGrammerDialectModule();
        var result = module.sqlParser().parseSql(statement("SELECT c.id FROM customers c"), null);

        assertEquals("oracle-26ai", result.attributes().get("fullGrammerProfile"));
        assertEquals("OracleFullGrammerParser", result.attributes().get("parser"));
        assertEquals("OracleFullGrammerLexer", result.attributes().get("lexer"));
        assertEquals("INCOMPLETE_VERSIONED", result.attributes().get("grammarCoverage"));
    }

    @Test
    void runCliClasspathIncludesOracleAdaptorJar() throws IOException {
        String script = Files.readString(repoRoot().resolve("scripts/run-cli.sh"));

        assertTrue(script.contains("adaptor-oracle/target/relation-detector-adaptor-oracle-0.1.0-SNAPSHOT.jar"),
                "scripts/run-cli.sh must include adaptor-oracle so manual Oracle CLI runs can discover OracleDatabaseAdaptor");
    }

    @Test
    void oracleVersionedFullGrammerFixturesUsePrunedRootSampleDataSurface() throws IOException {
        Path correctnessRoot = repoRoot().resolve("test-fixtures/correctness/oracle");
        Set<String> rootSampleFixtures;
        try (Stream<Path> stream = Files.list(correctnessRoot)) {
            rootSampleFixtures = stream
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("oracle-sample-data-full-"))
                    .collect(java.util.stream.Collectors.toSet());
        }
        assertFalse(rootSampleFixtures.isEmpty(), "Oracle root token-event must keep sample-data correctness coverage");

        for (String version : List.of("v12c", "v19c", "v21c", "v26ai")) {
            Path versionRoot = correctnessRoot.resolve(version);
            Set<String> versionSampleFixtures;
            try (Stream<Path> stream = Files.list(versionRoot)) {
                versionSampleFixtures = stream
                        .filter(Files::isDirectory)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.startsWith("oracle" + version.substring(1) + "-sample-data-full-"))
                        .map(name -> "oracle-" + name.substring(("oracle" + version.substring(1) + "-").length()))
                        .collect(java.util.stream.Collectors.toSet());
            }
            assertFalse(versionSampleFixtures.isEmpty(),
                    version + " full-grammer must keep sample-data correctness coverage");
            assertTrue(rootSampleFixtures.containsAll(versionSampleFixtures),
                    version + " full-grammer sample-data fixture surface must stay within the pruned Oracle root surface");
        }
    }

    @Test
    void oracleSqlAssetsDoNotContainPostgresOrMysqlDialectResidue() throws IOException {
        List<Path> scanPaths = allOracleSqlAssetPaths();
        List<String> offenders;
        try (Stream<Path> stream = scanPaths.stream().filter(Files::exists)) {
            offenders = stream
                    .filter(path -> path.toString().endsWith(".sql"))
                    .flatMap(path -> forbiddenOracleSqlFindings(path).stream())
                    .limit(100)
                    .toList();
        }

        assertTrue(offenders.isEmpty(),
                "Oracle SQL assets must be real Oracle dialect, not PostgreSQL/MySQL leftovers. Offenders="
                        + offenders);
    }

    private static List<Path> allOracleSqlAssetPaths() throws IOException {
        List<Path> paths = new ArrayList<>();
        collectSqlAssets(repoRoot().resolve("sample-data/oracle"), paths);
        collectSqlAssets(repoRoot().resolve("test-fixtures/correctness/oracle"), paths);
        return paths;
    }

    private static void collectSqlAssets(Path root, List<Path> paths) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".sql"))
                    .forEach(paths::add);
        }
    }

    private static List<String> forbiddenOracleSqlFindings(Path path) {
        String text;
        try {
            text = Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
        String sqlText = stripSqlStringLiterals(stripSqlComments(text));
        List<String> findings = new ArrayList<>(FORBIDDEN_ORACLE_SQL_PATTERNS.stream()
                .filter(pattern -> pattern.pattern().matcher(sqlText).find())
                .map(pattern -> repoRoot().relativize(path) + " -> " + pattern.description())
                .toList());
        if (containsTopLevelUpdateFrom(sqlText)) {
            findings.add(repoRoot().relativize(path) + " -> PostgreSQL UPDATE FROM statement");
        }
        if (!isOracleVersionOnlyFixture(path) && containsOracleMultivalueInsert(sqlText)) {
            findings.add(repoRoot().relativize(path)
                    + " -> Oracle natural baseline must not use multivalue INSERT VALUES");
        }
        return findings;
    }

    private static boolean isOracleVersionOnlyFixture(Path path) {
        Path parent = path.getParent();
        return parent != null && parent.getFileName().toString().contains("-version-");
    }

    private static boolean containsOracleMultivalueInsert(String sqlText) {
        var valuesMatcher = Pattern.compile("(?i)\\bVALUES\\b").matcher(sqlText);
        while (valuesMatcher.find()) {
            int tupleStart = skipWhitespace(sqlText, valuesMatcher.end());
            if (tupleStart >= sqlText.length() || sqlText.charAt(tupleStart) != '(') {
                continue;
            }
            int tupleEnd = matchingParenthesis(sqlText, tupleStart);
            if (tupleEnd < 0) {
                continue;
            }
            int separator = skipWhitespace(sqlText, tupleEnd + 1);
            if (separator < sqlText.length() && sqlText.charAt(separator) == ',') {
                int nextTuple = skipWhitespace(sqlText, separator + 1);
                if (nextTuple < sqlText.length() && sqlText.charAt(nextTuple) == '(') {
                    return true;
                }
            }
        }
        return false;
    }

    private static int skipWhitespace(String text, int start) {
        int index = start;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int matchingParenthesis(String text, int start) {
        int depth = 0;
        for (int index = start; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '(') {
                depth++;
            } else if (ch == ')' && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static boolean containsTopLevelUpdateFrom(String sqlText) {
        var updateMatcher = Pattern.compile("(?i)\\bUPDATE\\b").matcher(sqlText);
        while (updateMatcher.find()) {
            int statementEnd = sqlText.indexOf(';', updateMatcher.start());
            if (statementEnd < 0) {
                statementEnd = sqlText.length();
            }
            String statement = sqlText.substring(updateMatcher.start(), statementEnd);
            int setIndex = findTopLevelKeyword(statement, "SET", 0);
            if (setIndex < 0) {
                continue;
            }
            if (findTopLevelKeyword(statement, "FROM", setIndex + 3) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static int findTopLevelKeyword(String text, String keyword, int start) {
        int depth = 0;
        for (int i = start; i <= text.length() - keyword.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '(') {
                depth++;
                continue;
            }
            if (ch == ')') {
                depth = Math.max(0, depth - 1);
                continue;
            }
            if (depth == 0
                    && text.regionMatches(true, i, keyword, 0, keyword.length())
                    && isKeywordBoundary(text, i - 1)
                    && isKeywordBoundary(text, i + keyword.length())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isKeywordBoundary(String text, int index) {
        if (index < 0 || index >= text.length()) {
            return true;
        }
        char ch = text.charAt(index);
        return !Character.isLetterOrDigit(ch) && ch != '_';
    }

    private static String stripSqlComments(String text) {
        String withoutBlockComments = Pattern.compile("(?s)/\\*.*?\\*/").matcher(text).replaceAll(" ");
        return Pattern.compile("(?m)--.*$").matcher(withoutBlockComments).replaceAll(" ");
    }

    private static String stripSqlStringLiterals(String text) {
        return Pattern.compile("'(?:''|[^'])*'").matcher(text).replaceAll("''");
    }

    private static boolean contains(Path path, String needle) {
        try {
            return Files.readString(path).contains(needle);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }

    private static SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "oracle-architecture-test.sql", 1, 1,
                java.util.Map.of());
    }

    private static Path oracleGrammarRoot(String version) {
        return repoRoot().resolve("grammar/oracle-" + version)
                .resolve("src/main/antlr4/com/relationdetector/oracle/fullgrammer")
                .resolve(version);
    }

    private static String oracleGrammarText(String version) throws IOException {
        Path root = oracleGrammarRoot(version);
        return Files.readString(root.resolve("OracleFullGrammerParser.g4"))
                + Files.readString(root.resolve("OracleFullGrammerLexer.g4"));
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("core"))
                    && Files.isDirectory(current.resolve("adaptor-oracle"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }

    private record ForbiddenOracleSqlPattern(String description, Pattern pattern) {
    }
}
