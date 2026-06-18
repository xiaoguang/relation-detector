package com.relationdetector.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.relationdetector.api.AdaptorContext;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.ScanScope;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.WarningMessage;
import com.relationdetector.api.Enums.EvidenceSourceType;
import com.relationdetector.api.Enums.StatementSourceType;
import com.relationdetector.core.DdlParserMode;
import com.relationdetector.core.DdlRelationParserRunner;
import com.relationdetector.core.ScanConfig;
import com.relationdetector.core.ShadowSqlRelationParser;
import com.relationdetector.core.SqlLogNoiseFilter;
import com.relationdetector.core.SqlParserMode;

/**
 * Golden regression tests generated from anonymized MySQL database samples.
 *
 * <p>The fixture files are static test assets. They let parser changes be
 * checked without requiring CI or local test runs to connect back to the live
 * databases that originally produced the samples.
 */
class MySqlBasicCorrectnessGoldenFixtureTest {
    private static final List<BasicCorrectnessCase> CASES = List.of(
            new BasicCorrectnessCase("basic-correctness-case-01", "case-01", "case_01"),
            new BasicCorrectnessCase("basic-correctness-case-02", "case-02", "case_02"),
            new BasicCorrectnessCase("basic-correctness-case-03", "case-03", "case_03"),
            new BasicCorrectnessCase("basic-correctness-case-04", "case-04", "case_04"));
    private static final List<String> FORBIDDEN_SOURCE_TOKENS = List.of(
            "jsh" + "_erp1",
            "jsh" + "_erp",
            "jsh" + "_erp" + "_test",
            "openlynxe" + "_db",
            "MySql" + "Jsh" + "Erp1");

    private final MySqlDatabaseAdaptor adaptor = new MySqlDatabaseAdaptor();

    private static Path fixtureRoot(BasicCorrectnessCase fixtureCase) {
        Path rootRelative = Path.of("test-fixtures/mysql/basic-correctness").resolve(fixtureCase.directory());
        if (Files.exists(rootRelative)) {
            return rootRelative;
        }
        return Path.of("..").resolve(rootRelative).normalize();
    }

    @Test
    void mysqlPassBasicCorrectness() {
        assertAll("MySQL basic correctness",
                Stream.concat(
                        Stream.<Executable>of(this::assertBasicCorrectnessAssetsDoNotExposeSourceSchemaNames),
                        CASES.stream().flatMap(fixtureCase -> Stream.<Executable>of(
                                () -> assertShowCreateDdlMatchesGoldenAndCanRunAntlrPrimaryWithoutFallback(fixtureCase),
                                () -> assertSqlSamplesKeepSimpleBaselineAndStableAntlrExtras(fixtureCase))))
                        .toList());
    }

    private void assertBasicCorrectnessAssetsDoNotExposeSourceSchemaNames() throws Exception {
        List<String> violations = new ArrayList<>();
        Path workspaceRoot = workspaceRoot();
        for (Path root : List.of(workspaceRoot.resolve("adaptor-mysql/src/test"), workspaceRoot.resolve("test-fixtures/mysql"))) {
            if (!Files.exists(root)) {
                continue;
            }
            try (var paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> !path.toString().contains("target/"))
                        .forEach(path -> collectSourceTokenViolations(path, violations));
            }
        }
        assertTrue(violations.isEmpty(), () -> "Source schema names leaked into basic correctness assets: " + violations);
    }

    private static void collectSourceTokenViolations(Path path, List<String> violations) {
        String pathText = path.toString();
        for (String token : FORBIDDEN_SOURCE_TOKENS) {
            if (pathText.contains(token)) {
                violations.add(pathText + " contains token in path");
                return;
            }
        }
        try {
            String text = Files.readString(path);
            for (String token : FORBIDDEN_SOURCE_TOKENS) {
                if (text.contains(token)) {
                    violations.add(pathText + " contains token in content");
                    return;
                }
            }
        } catch (Exception ignored) {
            // The scanned roots contain Java sources and text fixtures; unreadable
            // files cannot leak source names through normal code review or test logs.
        }
    }

    private static Path workspaceRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("test-fixtures"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate relation-detector workspace root");
    }

    private void assertShowCreateDdlMatchesGoldenAndCanRunAntlrPrimaryWithoutFallback(
            BasicCorrectnessCase fixtureCase
    ) throws Exception {
        Path root = fixtureRoot(fixtureCase);
        Path ddlFixture = root.resolve("ddl/show-create-tables.sql");
        Path ddlGoldenFile = root.resolve("golden/ddl-parser-comparison.json");
        String ddl = Files.readString(ddlFixture);
        Golden ddlGolden = Golden.read(ddlGoldenFile);
        ScanConfig shadowConfig = config(fixtureCase.schema());
        shadowConfig.ddlParserMode = DdlParserMode.ANTLR_DDL_SHADOW;

        DdlRelationParserRunner.Result comparison = new DdlRelationParserRunner().parseTextWithDiagnostics(
                adaptor,
                shadowConfig,
                ddl,
                fixturePath(fixtureCase, "ddl/show-create-tables.sql"),
                EvidenceSourceType.DATABASE_DDL,
                null);

        assertEquals(fixtureCase.id(), ddlGolden.string("caseId"));
        assertEquals(ddlGolden.string("fixtureSha256"), sha256(ddl));
        assertEquals(ddlGolden.integer("ddlDefinitions"), countDdlDefinitions(ddl));
        assertEquals(ddlGolden.integer("simpleRelations"), comparison.primaryCount());
        assertEquals(ddlGolden.integer("antlrRelations"), comparison.shadowCount());
        assertEquals(ddlGolden.list("missingSimpleDdlRelations"), comparison.missingSimpleDdlRelations());
        assertEquals(ddlGolden.list("extraAntlrDdlRelations"), comparison.extraAntlrDdlRelations());

        ScanConfig primaryConfig = config(fixtureCase.schema());
        primaryConfig.ddlParserMode = DdlParserMode.ANTLR_DDL_PRIMARY;
        primaryConfig.ddlParserFallbackOnFailure = true;
        List<WarningMessage> warnings = new ArrayList<>();
        List<RelationshipCandidate> primaryRelations = new DdlRelationParserRunner().parseText(
                adaptor,
                primaryConfig,
                ddl,
                fixturePath(fixtureCase, "ddl/show-create-tables.sql"),
                EvidenceSourceType.DATABASE_DDL,
                new AdaptorContext(new ScanScope(null, fixtureCase.schema(), List.of(), List.of()), Map.of(), warnings::add));

        assertEquals(comparison.shadowCount(), primaryRelations.size());
        assertFalse(warnings.stream().anyMatch(warning -> warning.code().equals("ANTLR_DDL_PRIMARY_FALLBACK")),
                fixtureCase.id() + " DDL fixture should not trigger ANTLR DDL primary fallback");
    }

    private void assertSqlSamplesKeepSimpleBaselineAndStableAntlrExtras(BasicCorrectnessCase fixtureCase) throws Exception {
        Path root = fixtureRoot(fixtureCase);
        Path sqlFixtureFile = root.resolve("sql/performance-schema-statements.sql");
        Path sqlGoldenFile = root.resolve("golden/sql-parser-comparison.json");
        String sqlFixture = Files.readString(sqlFixtureFile);
        Golden sqlGolden = Golden.read(sqlGoldenFile);
        List<SqlSample> samples = SqlSample.read(sqlFixtureFile);
        ShadowSqlRelationParser parser = (ShadowSqlRelationParser) adaptor.sqlRelationParser();
        ScanConfig config = config(fixtureCase.schema());
        List<WarningMessage> warnings = new ArrayList<>();
        AdaptorContext context = new AdaptorContext(
                new ScanScope(null, fixtureCase.schema(), List.of(), List.of()),
                Map.of(),
                warnings::add);

        int simpleRelations = 0;
        int antlrRelations = 0;
        Set<String> missing = new TreeSet<>();
        Set<String> extra = new TreeSet<>();
        int line = 1;
        for (SqlSample sample : samples) {
            SqlStatementRecord statement = new SqlStatementRecord(
                    sample.sql(),
                    StatementSourceType.NATIVE_LOG,
                    sample.source(),
                    line,
                    line,
                    Map.of());
            if (SqlLogNoiseFilter.shouldSkip(config, statement)) {
                line++;
                continue;
            }
            ShadowSqlRelationParser.Result result = parser.parseWithDiagnostics(statement, context);
            simpleRelations += result.primaryCount();
            antlrRelations += result.shadowCount();
            result.missingSimpleRelations().forEach(value -> missing.add(sample.source() + " :: " + value));
            result.extraAntlrRelations().forEach(value -> extra.add(sample.source() + " :: " + value));
            line++;
        }

        assertEquals(fixtureCase.id(), sqlGolden.string("caseId"));
        assertEquals(sqlGolden.string("fixtureSha256"), sha256(sqlFixture));
        assertEquals(sqlGolden.integer("sqlSamples"), samples.size());
        assertEquals(sqlGolden.integer("simpleRelations"), simpleRelations);
        assertEquals(sqlGolden.integer("antlrRelations"), antlrRelations);
        assertEquals(sqlGolden.list("missingSimpleRelations"), List.copyOf(missing));
        assertEquals(sqlGolden.list("extraAntlrRelations"), List.copyOf(extra));
        assertTrue(warnings.isEmpty(), () -> "Unexpected SQL parser warnings: " + warningCodes(warnings));
    }

    private String fixturePath(BasicCorrectnessCase fixtureCase, String relativePath) {
        return "test-fixtures/mysql/basic-correctness/" + fixtureCase.directory() + "/" + relativePath;
    }

    record BasicCorrectnessCase(String id, String directory, String schema) {
    }

    private ScanConfig config(String schema) {
        ScanConfig config = new ScanConfig();
        config.databaseType = com.relationdetector.api.Enums.DatabaseType.MYSQL;
        config.schema = schema;
        config.sqlParserMode = SqlParserMode.ANTLR_SHADOW;
        config.ddlParserMode = DdlParserMode.ANTLR_DDL_SHADOW;
        config.ddlParserFallbackOnFailure = true;
        return config;
    }

    private int countDdlDefinitions(String ddl) {
        return (int) ddl.lines()
                .filter(line -> line.startsWith("-- relation-detector-fixture-table: "))
                .count();
    }

    private Map<String, Long> warningCodes(List<WarningMessage> warnings) {
        return warnings.stream()
                .collect(Collectors.groupingBy(WarningMessage::code, LinkedHashMap::new, Collectors.counting()));
    }

    static String sha256(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    record SqlSample(String source, String sql) {
        static List<SqlSample> read(Path file) throws Exception {
            List<SqlSample> samples = new ArrayList<>();
            String source = null;
            StringBuilder sql = new StringBuilder();
            for (String line : Files.readAllLines(file)) {
                if (line.startsWith("-- relation-detector-fixture-source: ")) {
                    source = line.substring("-- relation-detector-fixture-source: ".length());
                    sql.setLength(0);
                } else if (line.equals("-- relation-detector-fixture-end")) {
                    samples.add(new SqlSample(source, sql.toString().trim()));
                    source = null;
                    sql.setLength(0);
                } else if (source != null) {
                    sql.append(line).append('\n');
                }
            }
            return samples;
        }
    }

    static final class Golden {
        private final String json;

        private Golden(String json) {
            this.json = json;
        }

        static Golden read(Path path) throws Exception {
            return new Golden(Files.readString(path));
        }

        String string(String key) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
                    .matcher(json);
            assertTrue(matcher.find(), "Missing string golden field: " + key);
            return matcher.group(1);
        }

        int integer(String key) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*(\\d+)")
                    .matcher(json);
            assertTrue(matcher.find(), "Missing integer golden field: " + key);
            return new BigDecimal(matcher.group(1)).intValueExact();
        }

        List<String> list(String key) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\\[(.*?)\\]", java.util.regex.Pattern.DOTALL)
                    .matcher(json);
            assertTrue(matcher.find(), "Missing list golden field: " + key);
            String body = matcher.group(1).trim();
            if (body.isBlank()) {
                return List.of();
            }
            java.util.regex.Matcher itemMatcher = java.util.regex.Pattern
                    .compile("\"((?:\\\\.|[^\"])*)\"")
                    .matcher(body);
            List<String> values = new ArrayList<>();
            while (itemMatcher.find()) {
                values.add(itemMatcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
            }
            return values;
        }
    }
}
