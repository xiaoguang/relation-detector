package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class DialectSqlAssetHygieneTest {
    private static final List<ForbiddenSqlPattern> MYSQL_FORBIDDEN = List.of(
            forbidden("PostgreSQL PL/pgSQL language marker", "\\bLANGUAGE\\s+plpgsql\\b"),
            forbidden("PostgreSQL cast operator", "::[A-Za-z_][A-Za-z0-9_]*(?:\\([^)]*\\))?"),
            forbidden("PostgreSQL RETURN QUERY statement", "\\bRETURN\\s+QUERY\\b"),
            forbidden("PostgreSQL RETURNS TABLE signature", "\\bRETURNS\\s+TABLE\\b"),
            forbidden("PostgreSQL date_trunc function", "\\bdate_trunc\\s*\\("),
            forbidden("PostgreSQL string_agg function", "\\bstring_agg\\s*\\("),
            forbidden("PostgreSQL JSONB helper", "\\bjsonb_[a-z0-9_]+\\b"),
            forbidden("Oracle VARCHAR2 type", "\\bVARCHAR2\\b"),
            forbidden("Oracle NVARCHAR2 type", "\\bNVARCHAR2\\b"),
            forbidden("Oracle CLOB type", "\\bCLOB\\b"),
            forbidden("Oracle NUMBER type", "\\bNUMBER\\s*\\("),
            forbidden("Oracle MERGE statement", "\\bMERGE\\s+INTO\\b"),
            forbidden("Oracle SYS_REFCURSOR", "\\bSYS_REFCURSOR\\b"),
            forbidden("Oracle LISTAGG function", "\\bLISTAGG\\s*\\("),
            forbidden("Oracle interval helper", "\\bNUMTODSINTERVAL\\s*\\("),
            forbidden("Oracle CONNECT BY clause", "\\bCONNECT\\s+BY\\b"));

    private static final List<ForbiddenSqlPattern> POSTGRES_FORBIDDEN = List.of(
            forbidden("MySQL AUTO_INCREMENT", "\\bAUTO_INCREMENT\\b"),
            forbidden("MySQL ENGINE table option", "\\bENGINE\\s*="),
            forbidden("MySQL ON DUPLICATE KEY UPDATE", "\\bON\\s+DUPLICATE\\s+KEY\\s+UPDATE\\b"),
            forbidden("MySQL DELIMITER command", "^\\s*DELIMITER\\b"),
            forbidden("MySQL backtick quoted identifier", "`"),
            forbidden("MySQL IFNULL function", "\\bIFNULL\\s*\\("),
            forbidden("Oracle VARCHAR2 type", "\\bVARCHAR2\\b"),
            forbidden("Oracle NVARCHAR2 type", "\\bNVARCHAR2\\b"),
            forbidden("Oracle CLOB type", "\\bCLOB\\b"),
            forbidden("Oracle NUMBER type", "\\bNUMBER\\s*\\("),
            forbidden("Oracle SYS_REFCURSOR", "\\bSYS_REFCURSOR\\b"),
            forbidden("Oracle LISTAGG function", "\\bLISTAGG\\s*\\("),
            forbidden("Oracle interval helper", "\\bNUMTODSINTERVAL\\s*\\("),
            forbidden("Oracle CONNECT BY clause", "\\bCONNECT\\s+BY\\b"));

    private static final List<ForbiddenSqlPattern> ORACLE_FORBIDDEN = List.of(
            forbidden("PostgreSQL PL/pgSQL language marker", "\\bLANGUAGE\\s+plpgsql\\b"),
            forbidden("PostgreSQL cast operator", "::[A-Za-z_][A-Za-z0-9_]*(?:\\([^)]*\\))?"),
            forbidden("PostgreSQL WITH RECURSIVE", "\\bWITH\\s+RECURSIVE\\b"),
            forbidden("PostgreSQL/MySQL LIMIT clause", "\\bLIMIT\\b"),
            forbidden("PostgreSQL string_agg function", "\\bstring_agg\\s*\\("),
            forbidden("PostgreSQL temporal WITHOUT OVERLAPS", "\\bWITHOUT\\s+OVERLAPS\\b"),
            forbidden("PostgreSQL interval cast", "::\\s*INTERVAL\\b"),
            forbidden("PostgreSQL JSONB helper", "\\bjsonb_[a-z0-9_]+\\b"),
            forbidden("PostgreSQL JSON arrow operator", "->>?"),
            forbidden("PostgreSQL date_trunc function", "\\bdate_trunc\\s*\\("),
            forbidden("PostgreSQL RETURN QUERY statement", "\\bRETURN\\s+QUERY\\b"),
            forbidden("PostgreSQL RETURNS TABLE function signature", "\\bRETURNS\\s+TABLE\\b"),
            forbidden("PostgreSQL RETURNS function signature", "\\bRETURNS\\b"),
            forbidden("PostgreSQL search_path command", "\\bSET\\s+search_path\\b"),
            forbidden("PostgreSQL AGE date function", "\\bAGE\\s*\\("),
            forbidden("PostgreSQL make_date function", "\\bmake_date\\s*\\("),
            forbidden("PostgreSQL DOW/ISODOW extract field", "\\bEXTRACT\\s*\\(\\s*(?:ISODOW|DOW)\\s+FROM\\b"),
            forbidden("MySQL AUTO_INCREMENT", "\\bAUTO_INCREMENT\\b"),
            forbidden("MySQL ENGINE table option", "\\bENGINE\\s*="),
            forbidden("MySQL ON DUPLICATE KEY UPDATE", "\\bON\\s+DUPLICATE\\s+KEY\\s+UPDATE\\b"),
            forbidden("MySQL DELIMITER command", "^\\s*DELIMITER\\b"),
            forbidden("MySQL backtick quoted identifier", "`"),
            forbidden("Mechanical migration produced VARCHAR2 pseudo column reference",
                    "\\b[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*VARCHAR2\\s*\\("),
            forbidden("Mechanical migration produced VARCHAR2 pseudo DDL/variable name", "^\\s*VARCHAR2\\s*\\("));

    @Test
    void mysqlSqlAssetsDoNotContainPostgresOrOracleDialectResidue() throws IOException {
        assertNoForbiddenDialectResidue("MySQL", List.of(
                repoRoot().resolve("sample-data/mysql"),
                repoRoot().resolve("test-fixtures/correctness/mysql")), MYSQL_FORBIDDEN);
    }

    @Test
    void postgresSqlAssetsDoNotContainMysqlOrOracleDialectResidue() throws IOException {
        assertNoForbiddenDialectResidue("PostgreSQL", List.of(
                repoRoot().resolve("sample-data/postgres"),
                repoRoot().resolve("test-fixtures/correctness/postgres")), POSTGRES_FORBIDDEN);
    }

    @Test
    void oracleSqlAssetsDoNotContainPostgresOrMysqlDialectResidue() throws IOException {
        assertNoForbiddenDialectResidue("Oracle", List.of(
                repoRoot().resolve("sample-data/oracle"),
                repoRoot().resolve("test-fixtures/correctness/oracle")), ORACLE_FORBIDDEN);
    }

    private static void assertNoForbiddenDialectResidue(
            String dialect,
            List<Path> roots,
            List<ForbiddenSqlPattern> forbiddenPatterns
    ) throws IOException {
        List<String> findings = new ArrayList<>();
        for (Path root : roots) {
            findings.addAll(forbiddenDialectFindings(root, forbiddenPatterns));
        }

        assertTrue(findings.isEmpty(),
                dialect + " SQL assets must not contain obvious cross-dialect syntax residue. Offenders="
                        + findings.stream().limit(100).toList());
    }

    private static List<String> forbiddenDialectFindings(
            Path root,
            List<ForbiddenSqlPattern> forbiddenPatterns
    ) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".sql"))
                    .flatMap(path -> forbiddenDialectFindingsInFile(path, forbiddenPatterns).stream())
                    .toList();
        }
    }

    private static List<String> forbiddenDialectFindingsInFile(
            Path path,
            List<ForbiddenSqlPattern> forbiddenPatterns
    ) {
        String text;
        try {
            text = Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + path, exception);
        }
        String sqlText = stripSqlStringLiterals(stripSqlComments(text));
        List<String> findings = new ArrayList<>(forbiddenPatterns.stream()
                .filter(pattern -> pattern.pattern().matcher(sqlText).find())
                .map(pattern -> repoRoot().relativize(path) + " -> " + pattern.description())
                .toList());
        if (path.toString().contains("/oracle/") && containsTopLevelUpdateFrom(sqlText)) {
            findings.add(repoRoot().relativize(path) + " -> PostgreSQL UPDATE FROM statement");
        }
        return findings;
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

    private static ForbiddenSqlPattern forbidden(String description, String pattern) {
        return new ForbiddenSqlPattern(description, Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE));
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("sample-data"))
                    && Files.isDirectory(current.resolve("test-fixtures"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }

    private record ForbiddenSqlPattern(String description, Pattern pattern) {
    }
}
