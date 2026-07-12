package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Shared, immutable SQL-asset checks. Dialect-specific JUnit entry points live in
 * {@code *SqlAssetHygieneTest} classes so a focused scope does not discover every
 * dialect's filesystem scan.
 */
final class DialectSqlAssetHygieneSupport {
    private static final List<ForbiddenSqlPattern> MYSQL_FORBIDDEN = List.of(
            forbidden("PostgreSQL PL/pgSQL language marker", "\\bLANGUAGE\\s+plpgsql\\b"),
            forbidden("PostgreSQL cast operator", "::[A-Za-z_][A-Za-z0-9_]*(?:\\([^)]*\\))?"),
            forbidden("PostgreSQL RETURN QUERY statement", "\\bRETURN\\s+QUERY\\b"),
            forbidden("PostgreSQL RETURNS TABLE signature", "\\bRETURNS\\s+TABLE\\b"),
            forbidden("PostgreSQL date_trunc function", "\\bdate_trunc\\s*\\("),
            forbidden("PostgreSQL string_agg function", "\\bstring_agg\\s*\\("),
            forbidden("PostgreSQL JSONB helper", "\\bjsonb_[a-z0-9_]+\\b"),
            forbidden("PostgreSQL ONLY rowset decorator", "\\b(?:FROM|JOIN)\\s+ONLY\\b"),
            forbidden("PostgreSQL ROWS FROM rowset function", "\\bROWS\\s+FROM\\s*\\("),
            forbidden("PostgreSQL TABLESAMPLE clause", "\\bTABLESAMPLE\\b"),
            forbidden("PostgreSQL WITH ORDINALITY clause", "\\bWITH\\s+ORDINALITY\\b"),
            forbidden("PostgreSQL UNNEST rowset function", "\\bUNNEST\\s*\\("),
            forbidden("Oracle VARCHAR2 type", "\\bVARCHAR2\\b"),
            forbidden("Oracle NVARCHAR2 type", "\\bNVARCHAR2\\b"),
            forbidden("Oracle CLOB type", "\\bCLOB\\b"),
            forbidden("Oracle NUMBER type", "\\bNUMBER\\s*\\("),
            forbidden("Oracle MERGE statement", "\\bMERGE\\s+INTO\\b"),
            forbidden("Oracle SYS_REFCURSOR", "\\bSYS_REFCURSOR\\b"),
            forbidden("Oracle LISTAGG function", "\\bLISTAGG\\s*\\("),
            forbidden("Oracle interval helper", "\\bNUMTODSINTERVAL\\s*\\("),
            forbidden("Oracle CONNECT BY clause", "\\bCONNECT\\s+BY\\b"));

    private static final List<ForbiddenSqlPattern> MYSQL57_FORBIDDEN = List.of(
            forbidden("MySQL 8.0 CTE statement", "^\\s*WITH\\b"),
            forbidden("MySQL 8.0 recursive CTE", "\\bWITH\\s+RECURSIVE\\b"),
            forbidden("MySQL 8.0 window OVER clause", "\\bOVER\\s*\\("),
            forbidden("MySQL 8.0 ROW_NUMBER window function", "\\bROW_NUMBER\\s*\\("),
            forbidden("MySQL 8.0 RANK window function", "\\bRANK\\s*\\("),
            forbidden("MySQL 8.0 DENSE_RANK window function", "\\bDENSE_RANK\\s*\\("),
            forbidden("MySQL 8.0 NTILE window function", "\\bNTILE\\s*\\("),
            forbidden("MySQL 8.0 LAG window function", "\\bLAG\\s*\\("),
            forbidden("MySQL 8.0 LEAD window function", "\\bLEAD\\s*\\("),
            forbidden("MySQL 8.0 FIRST_VALUE window function", "\\bFIRST_VALUE\\s*\\("),
            forbidden("MySQL 8.0 JSON_TABLE rowset", "\\bJSON_TABLE\\s*\\("),
            forbidden("MySQL 8.0 LATERAL derived table", "\\bLATERAL\\b"),
            forbidden("MySQL 8.0 invisible index", "\\bINVISIBLE\\b"),
            forbidden("MySQL 8.0 visible index option", "\\bVISIBLE\\b"));

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
            forbidden("non-Oracle stored generated column", "\\bGENERATED\\s+ALWAYS\\s+AS\\s*\\([^;]+\\)\\s+STORED\\b"),
            forbidden("Oracle zero-parameter routine definition with empty parentheses",
                    "\\bCREATE\\s+OR\\s+REPLACE\\s+(?:PROCEDURE|FUNCTION)\\s+[A-Za-z0-9_.$#]+\\s*\\(\\s*\\)"),
            forbidden("Oracle boolean comparison used directly as a generated expression",
                    "\\bGENERATED\\s+ALWAYS\\s+AS\\s*\\(\\s*[A-Za-z0-9_.$#]+\\s*=\\s*[A-Za-z0-9_.$#]+"),
            forbidden("Mechanical migration produced postfix numeric cast artifact",
                    "\\)\\s*\\(\\s*\\d+(?:\\s*,\\s*\\d+)?\\s*\\)"),
            forbidden("Oracle EXTRACT does not support QUARTER",
                    "\\bEXTRACT\\s*\\(\\s*QUARTER\\s+FROM\\b"),
            forbidden("Oracle natural baseline must not project EXTRACT comparison as SQL BOOLEAN",
                    "^\\s*EXTRACT\\s*\\([^\\n]+\\)\\s*(?:=|<>|!=|<=|>=|<|>)\\s*[^,;]+,\\s*$"),
            forbidden("Oracle natural baseline must not project OR-combined comparisons as SQL BOOLEAN",
                    "^\\s*[A-Za-z_$#][A-Za-z0-9_.$#]*\\s*=\\s*[^,;\\n]+\\s+OR\\s+[^,;\\n]+,\\s*$"),
            forbidden("Mechanical migration produced VARCHAR2 pseudo column reference",
                    "\\b[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*VARCHAR2\\s*\\("),
            forbidden("Mechanical migration produced VARCHAR2 pseudo expression",
                    "\\bVARCHAR2\\s*\\([^)]*\\)\\s*(?:=|IN\\b)"),
            forbidden("Mechanical migration produced VARCHAR2 pseudo DDL/variable name", "^\\s*VARCHAR2\\s*\\("));

    private static final List<ForbiddenSqlPattern> SQLSERVER_FORBIDDEN = List.of(
            forbidden("PostgreSQL PL/pgSQL language marker", "\\bLANGUAGE\\s+plpgsql\\b"),
            forbidden("PostgreSQL cast operator", "::[A-Za-z_][A-Za-z0-9_]*(?:\\([^)]*\\))?"),
            forbidden("PostgreSQL WITH RECURSIVE", "\\bWITH\\s+RECURSIVE\\b"),
            forbidden("PostgreSQL RETURN QUERY statement", "\\bRETURN\\s+QUERY\\b"),
            forbidden("PostgreSQL date_trunc function", "\\bdate_trunc\\s*\\("),
            forbidden("PostgreSQL/MySQL LIMIT clause", "\\bLIMIT\\b"),
            forbidden("MySQL AUTO_INCREMENT", "\\bAUTO_INCREMENT\\b"),
            forbidden("MySQL ENGINE table option", "\\bENGINE\\s*="),
            forbidden("MySQL ON DUPLICATE KEY UPDATE", "\\bON\\s+DUPLICATE\\s+KEY\\s+UPDATE\\b"),
            forbidden("MySQL DELIMITER command", "^\\s*DELIMITER\\b"),
            forbidden("MySQL backtick quoted identifier", "`"),
            forbidden("Oracle VARCHAR2 type", "\\bVARCHAR2\\b"),
            forbidden("Oracle NVARCHAR2 type", "\\bNVARCHAR2\\b"),
            forbidden("Oracle CLOB type", "\\bCLOB\\b"),
            forbidden("Oracle NUMBER type", "\\bNUMBER\\s*\\("),
            forbidden("Oracle SYS_REFCURSOR", "\\bSYS_REFCURSOR\\b"),
            forbidden("Oracle LISTAGG function", "\\bLISTAGG\\s*\\("),
            forbidden("Oracle CONNECT BY clause", "\\bCONNECT\\s+BY\\b"));

    private static final List<ForbiddenSqlPattern> SQLSERVER_CANONICAL_TABLE_REFERENCES = List.of(
            forbidden("unqualified [table] reference mixes SQL Server sample-data canonical table identity",
                    "\\b(?:FROM|JOIN|INTO|UPDATE|REFERENCES)\\s+\\[[^\\]]+\\](?!\\s*\\.)"),
            forbidden("unqualified DDL [table] reference mixes SQL Server sample-data canonical table identity",
                    "\\b(?:CREATE\\s+TABLE|ALTER\\s+TABLE|DELETE\\s+FROM)\\s+\\[[^\\]]+\\](?!\\s*\\.)"));

    static void mysqlSqlAssetsDoNotContainPostgresOrOracleDialectResidue() throws IOException {
        assertNoForbiddenDialectResidue("MySQL", List.of(
                repoRoot().resolve("sample-data/mysql"),
                repoRoot().resolve("test-fixtures/correctness/mysql")), MYSQL_FORBIDDEN);
    }

    static void mysql57SampleDataDoesNotContainMysql80OnlySyntax() throws IOException {
        assertNoForbiddenDialectResidue("MySQL 5.7", List.of(
                repoRoot().resolve("sample-data/mysql/5.7")), MYSQL57_FORBIDDEN);
    }

    static void postgresSqlAssetsDoNotContainMysqlOrOracleDialectResidue() throws IOException {
        assertNoForbiddenDialectResidue("PostgreSQL", List.of(
                repoRoot().resolve("sample-data/postgres"),
                repoRoot().resolve("test-fixtures/correctness/postgres")), POSTGRES_FORBIDDEN);
    }

    static void oracleSqlAssetsDoNotContainPostgresOrMysqlDialectResidue() throws IOException {
        assertNoForbiddenDialectResidue("Oracle", List.of(
                repoRoot().resolve("sample-data/oracle"),
                repoRoot().resolve("test-fixtures/correctness/oracle")), ORACLE_FORBIDDEN);
    }

    static void sqlServerSqlAssetsDoNotContainOtherDialectResidue() throws IOException {
        assertNoForbiddenDialectResidue("SQL Server", List.of(
                repoRoot().resolve("sample-data/sqlserver"),
                repoRoot().resolve("test-fixtures/correctness/sqlserver")), SQLSERVER_FORBIDDEN);
    }

    static void sqlServerSqlAssetsUseCanonicalSchemaQualifiedTableReferences() throws IOException {
        assertNoSqlAssetFindings(
                "SQL Server sample-data/correctness intentionally uses [schema].[table] as the canonical table reference form; unqualified [table] would mix table identity within the same asset set",
                List.of(
                repoRoot().resolve("sample-data/sqlserver"),
                repoRoot().resolve("test-fixtures/correctness/sqlserver")), SQLSERVER_CANONICAL_TABLE_REFERENCES);
    }

    static void sqlServerSampleDataFilesAreCoveredByRootAndVersionedCorrectnessFixtures() throws IOException {
        Path sampleRoot = repoRoot().resolve("sample-data/sqlserver");
        Path correctnessRoot = repoRoot().resolve("test-fixtures/correctness/sqlserver");
        Set<Path> rootFixtureInputs = manifestInputs(correctnessRoot, correctnessRoot);

        List<String> missing = new ArrayList<>();
        Set<Path> expectedRelativeSqlFiles = null;
        try (Stream<Path> versions = Files.list(sampleRoot)) {
            for (Path versionDir : versions.filter(Files::isDirectory).toList()) {
                String version = versionDir.getFileName().toString();
                Path versionFixtureRoot = correctnessRoot.resolve("v" + version);
                Set<Path> versionFixtureInputs = manifestInputs(versionFixtureRoot, correctnessRoot);
                List<Path> versionSqlFiles;
                try (Stream<Path> sqlFiles = Files.walk(versionDir)) {
                    versionSqlFiles = sqlFiles
                            .filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".sql"))
                            .map(path -> path.toAbsolutePath().normalize())
                            .sorted()
                            .toList();
                    if (versionSqlFiles.size() != 38) {
                        missing.add("sqlserver/v" + version + " expected 38 sample-data SQL files, found "
                                + versionSqlFiles.size());
                    }
                    Set<Path> relativeSqlFiles = versionSqlFiles.stream()
                            .map(versionDir.toAbsolutePath().normalize()::relativize)
                            .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
                    if (expectedRelativeSqlFiles == null) {
                        expectedRelativeSqlFiles = relativeSqlFiles;
                    } else if (!expectedRelativeSqlFiles.equals(relativeSqlFiles)) {
                        missing.add("sqlserver/v" + version + " sample-data relative file set differs from baseline");
                    }
                    for (Path sqlFile : versionSqlFiles) {
                        if (!versionFixtureInputs.contains(sqlFile)) {
                            missing.add("sqlserver/v" + version + " missing " + repoRoot().relativize(sqlFile));
                        }
                        if ("2025".equals(version) && !rootFixtureInputs.contains(sqlFile)) {
                            missing.add("sqlserver root token-event missing " + repoRoot().relativize(sqlFile));
                        }
                    }
                }
            }
        }

        assertTrue(missing.isEmpty(), "SQL Server sample-data SQL files must all be correctness-covered: " + missing);
    }

    static void sqlServerSampleDataKeepsComparableErpSemanticDensity() throws IOException {
        Path sampleRoot = repoRoot().resolve("sample-data/sqlserver");
        List<String> findings = new ArrayList<>();
        try (Stream<Path> versions = Files.list(sampleRoot)) {
            for (Path versionDir : versions.filter(Files::isDirectory).toList()) {
                SqlAssetDensity density = sqlAssetDensity(versionDir);
                if (density.createTables() < 120) {
                    findings.add(versionDir.getFileName() + " create table count too low: " + density.createTables());
                }
                if (density.fkReferences() < 400) {
                    findings.add(versionDir.getFileName() + " FK/reference count too low: " + density.fkReferences());
                }
                if (density.procedures() < 16) {
                    findings.add(versionDir.getFileName() + " procedure count too low: " + density.procedures());
                }
                if (density.functions() < 10) {
                    findings.add(versionDir.getFileName() + " function count too low: " + density.functions());
                }
                if (density.triggers() < 8) {
                    findings.add(versionDir.getFileName() + " trigger count too low: " + density.triggers());
                }
                if (density.insertSelects() < 35) {
                    findings.add(versionDir.getFileName() + " business INSERT SELECT count too low: " + density.insertSelects());
                }
            }
        }

        assertTrue(findings.isEmpty(),
                "SQL Server sample-data must stay comparable to the ERP semantic density of the other dialects: "
                        + findings);
    }

    static void sqlServerDeepScenarioIncludesInventoryMrpAndCostFlowProcedures() throws IOException {
        Path sampleRoot = repoRoot().resolve("sample-data/sqlserver");
        List<String> findings = new ArrayList<>();
        List<String> requiredProcedures = List.of(
                "sp_run_mrp_for_plan",
                "sp_calculate_work_order_actual_cost",
                "sp_post_finished_goods_receipt",
                "sp_post_cogs_for_sales_order",
                "sp_generate_picking_task_for_order",
                "sp_issue_repair_order_parts");

        try (Stream<Path> versions = Files.list(sampleRoot)) {
            for (Path versionDir : versions.filter(Files::isDirectory).toList()) {
                Path deepScenario = versionDir.resolve("02-procedures/13-erp-deep-scenario-procedures.sql");
                String sql = Files.exists(deepScenario) ? Files.readString(deepScenario) : "";
                for (String procedure : requiredProcedures) {
                    if (!Pattern.compile("\\b" + procedure + "\\b", Pattern.CASE_INSENSITIVE).matcher(sql).find()) {
                        findings.add(versionDir.getFileName() + " missing " + procedure);
                    }
                }
            }
        }

        assertTrue(findings.isEmpty(),
                "SQL Server deep scenario sample-data must carry the same inventory/MRP/cost VALUE-flow routines as MySQL/PostgreSQL/Oracle: "
                        + findings);
    }

    static void sqlServerSampleDataDoesNotCarryRelationProbeBenchmarkTemplates() throws IOException {
        Path sampleRoot = repoRoot().resolve("sample-data/sqlserver");
        List<String> findings = new ArrayList<>();
        try (Stream<Path> versions = Files.list(sampleRoot)) {
            for (Path versionDir : versions.filter(Files::isDirectory).toList()) {
                int probes = relationProbeTemplates(versionDir);
                if (probes > 0) {
                    findings.add(versionDir.getFileName()
                            + " relation-probe templates belong in semantic-equivalent benchmark, found " + probes);
                }
            }
        }

        assertTrue(findings.isEmpty(),
                "SQL Server sample-data should keep natural ERP SQL, not high-density relation-probe templates: "
                        + findings);
    }

    static void sqlServerDataFilesHaveDistinctBusinessContentsWithinEachVersion() throws IOException {
        Path sampleRoot = repoRoot().resolve("sample-data/sqlserver");
        List<String> findings = new ArrayList<>();
        try (Stream<Path> versions = Files.list(sampleRoot)) {
            for (Path versionDir : versions.filter(Files::isDirectory).sorted().toList()) {
                Map<String, List<Path>> byContent = new java.util.LinkedHashMap<>();
                try (Stream<Path> dataFiles = Files.list(versionDir.resolve("03-data"))) {
                    for (Path file : dataFiles
                            .filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".sql"))
                            .sorted()
                            .toList()) {
                        String normalized = stripSqlComments(Files.readString(file)).replaceAll("\\s+", " ").trim();
                        byContent.computeIfAbsent(normalized, ignored -> new ArrayList<>()).add(file);
                    }
                }
                byContent.values().stream()
                        .filter(files -> files.size() > 1)
                        .forEach(files -> findings.add(versionDir.getFileName() + " duplicate data assets "
                                + files.stream().map(path -> path.getFileName().toString()).toList()));
            }
        }
        assertTrue(findings.isEmpty(), "SQL Server data fixtures must represent distinct business assets: " + findings);
    }

    static void sqlServerSampleDataDoesNotUseNumberedRelationProbeProcedures() throws IOException {
        Path sampleRoot = repoRoot().resolve("sample-data/sqlserver");
        List<String> findings = new ArrayList<>();
        Pattern numberedProbeProcedure = Pattern.compile(
                "(?i)\\bCREATE\\s+OR\\s+ALTER\\s+PROCEDURE\\s+\\[dbo]\\.?\\[sp_[a-z0-9_]+_[0-9]+]");
        Pattern numberedProbeFunction = Pattern.compile(
                "(?i)\\bCREATE\\s+OR\\s+ALTER\\s+FUNCTION\\s+\\[dbo]\\.?\\[fn_(?:relation|relation_extra)_[0-9]+]");
        List<ForbiddenSqlPattern> syntheticProbeResidue = List.of(
                new ForbiddenSqlPattern("synthetic migrated-from relation probe block",
                        Pattern.compile("(?i)migrated\\s+from\\s+sp_[a-z0-9_]+_[0-9]+")),
                new ForbiddenSqlPattern("synthetic mapped_id relation probe projection",
                        Pattern.compile("(?i)\\bmapped_id\\b")),
                new ForbiddenSqlPattern("synthetic mapped_id relation probe fallback",
                        Pattern.compile("(?i)ISNULL\\s*\\(\\s*src\\.\\[mapped_id]\\s*,\\s*src\\.\\[id]\\s*\\)")),
                new ForbiddenSqlPattern("FK-copy probe INSERT SELECT",
                        Pattern.compile("(?is)INSERT\\s+INTO\\s+\\[dbo]\\.?\\[[^]]+]\\s*\\([^)]*\\[[^]]*_id]\\s*\\)\\s*SELECT\\s+p\\.\\[id]\\s+FROM\\s+\\[dbo]\\.?\\[[^]]+]\\s+AS\\s+p\\s+INNER\\s+JOIN\\s+\\[dbo]\\.?\\[[^]]+]\\s+AS\\s+c\\s+ON\\s+c\\.\\[[^]]*_id]\\s*=\\s*p\\.\\[id]")));
        try (Stream<Path> stream = Files.walk(sampleRoot)) {
            for (Path path : stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".sql"))
                    .sorted()
                    .toList()) {
                String sql = stripSqlStringLiterals(stripSqlComments(Files.readString(path)));
                var matcher = numberedProbeProcedure.matcher(sql);
                while (matcher.find()) {
                    findings.add(repoRoot().relativize(path) + " -> " + matcher.group());
                    if (findings.size() >= 20) {
                        break;
                    }
                }
                matcher = numberedProbeFunction.matcher(sql);
                while (matcher.find()) {
                    findings.add(repoRoot().relativize(path) + " -> " + matcher.group());
                    if (findings.size() >= 20) {
                        break;
                    }
                }
                for (ForbiddenSqlPattern pattern : syntheticProbeResidue) {
                    if (pattern.pattern().matcher(sql).find()) {
                        findings.add(repoRoot().relativize(path) + " -> " + pattern.description());
                        break;
                    }
                }
            }
        }

        assertTrue(findings.isEmpty(),
                "SQL Server sample-data should model natural ERP routines; numbered relation-probe procedures belong in semantic-equivalent benchmark: "
                        + findings);
    }

    static void commonNaturalSampleDataDoesNotContainParserCoverageBodies() throws IOException {
        Path naturalRoot = repoRoot().resolve("sample-data/common-natural");
        Path coverageRoot = repoRoot().resolve("sample-data/common-parser-coverage");
        assertTrue(Files.isDirectory(naturalRoot), "Expected common natural sample-data root");
        assertTrue(Files.isDirectory(coverageRoot), "Expected common parser coverage sample-data root");
        assertNoSqlAssetFindings(
                "Common natural sample-data must not contain parser/correctness coverage bodies",
                List.of(naturalRoot),
                List.of(
                        forbidden("parser coverage for-golden file or reference", "for-golden"),
                        forbidden("parser-ready mirror body comment", "parser-ready"),
                        forbidden("correctness fixture source marker", "relation-detector-fixture-source")));
        try (Stream<Path> coverageFiles = Files.walk(coverageRoot)) {
            assertTrue(coverageFiles
                    .filter(Files::isRegularFile)
                    .anyMatch(path -> path.getFileName().toString().endsWith("-for-golden.sql")),
                    "Common parser coverage root should retain for-golden SQL assets");
        }
    }

    static void sampleDataParserCliRunsCommonNaturalRoot() throws IOException {
        String script = Files.readString(repoRoot()
                .resolve("test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh"));
        assertTrue(script.contains("queue_case common-token-event-sample-data COMMON token-event \"\" \"\" \"$RELATION_ROOT/sample-data/common-natural\""),
                "Common sample-data CLI case must use common-natural, not the parser coverage/mixed portable root");
    }

    static void semanticEquivalentContainsRelationProbeBenchmark() throws IOException {
        Path benchmarkRoot = repoRoot().resolve("test-fixtures/semantic-equivalent/relation-probe");
        assertTrue(Files.isDirectory(benchmarkRoot), "Expected semantic-equivalent relation-probe benchmark");
        int probes = relationProbeTemplates(benchmarkRoot);
        assertTrue(probes >= 100,
                "semantic-equivalent relation-probe benchmark should carry the high-density JOIN/EXISTS/IN corpus, found "
                        + probes);
    }

    private static void assertNoForbiddenDialectResidue(
            String dialect,
            List<Path> roots,
            List<ForbiddenSqlPattern> forbiddenPatterns
    ) throws IOException {
        assertNoSqlAssetFindings(
                dialect + " SQL assets must not contain obvious cross-dialect syntax residue",
                roots,
                forbiddenPatterns);
    }

    private static void assertNoSqlAssetFindings(
            String message,
            List<Path> roots,
            List<ForbiddenSqlPattern> forbiddenPatterns
    ) throws IOException {
        List<String> findings = new ArrayList<>();
        for (Path root : roots) {
            findings.addAll(forbiddenDialectFindings(root, forbiddenPatterns));
        }

        assertTrue(findings.isEmpty(),
                message + ". Offenders=" + findings.stream().limit(100).toList());
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
        String sqlText = sanitizeSqlForAssetCheck(text);
        List<String> findings = new ArrayList<>(forbiddenPatterns.stream()
                .filter(pattern -> pattern.pattern().matcher(sqlText).find())
                .map(pattern -> repoRoot().relativize(path) + " -> " + pattern.description())
                .toList());
        if (path.toString().contains("/oracle/") && containsTopLevelUpdateFrom(sqlText)) {
            findings.add(repoRoot().relativize(path) + " -> PostgreSQL UPDATE FROM statement");
        }
        if (path.toString().contains("/oracle/")
                && !isOracleVersionOnlyFixture(path)
                && containsOracleMultivalueInsert(sqlText)) {
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

    private static Set<Path> manifestInputs(Path searchRoot, Path correctnessRoot) throws IOException {
        if (!Files.exists(searchRoot)) {
            return Set.of();
        }
        try (Stream<Path> stream = Files.walk(searchRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("manifest.yml"))
                    .filter(path -> isManifestDirectChildOfSearchRoot(path, searchRoot, correctnessRoot))
                    .map(DialectSqlAssetHygieneSupport::manifestInputPath)
                    .filter(path -> !path.toString().isBlank())
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    private static boolean isManifestDirectChildOfSearchRoot(Path manifest, Path searchRoot, Path correctnessRoot) {
        if (!searchRoot.equals(correctnessRoot)) {
            return true;
        }
        return manifest.getParent().getParent().equals(searchRoot);
    }

    private static Path manifestInputPath(Path manifest) {
        try {
            String input = Files.readAllLines(manifest).stream()
                    .filter(line -> line.startsWith("input:"))
                    .map(line -> line.substring("input:".length()).trim())
                    .findFirst()
                    .orElse("");
            return input.isBlank() ? Path.of("") : manifest.getParent().resolve(input).toAbsolutePath().normalize();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read manifest " + manifest, exception);
        }
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

    static String sanitizeSqlForAssetCheck(String text) {
        return stripSqlStringLiterals(stripSqlComments(text));
    }

    private static String stripSqlStringLiterals(String text) {
        StringBuilder sanitized = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current != '\'') {
                sanitized.append(current);
                continue;
            }
            sanitized.append("''");
            index++;
            while (index < text.length()) {
                current = text.charAt(index);
                if (current == '\n' || current == '\r') {
                    sanitized.append(current);
                }
                if (current == '\'' && index + 1 < text.length() && text.charAt(index + 1) == '\'') {
                    index += 2;
                    continue;
                }
                if (current == '\'') {
                    break;
                }
                index++;
            }
        }
        return sanitized.toString();
    }

    private static String stripSqlComments(String text) {
        StringBuilder sanitized = new StringBuilder(text.length());
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean lineComment = false;
        int blockDepth = 0;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            char next = index + 1 < text.length() ? text.charAt(index + 1) : '\0';
            if (lineComment) {
                if (current == '\n' || current == '\r') {
                    lineComment = false;
                    sanitized.append(current);
                } else {
                    sanitized.append(' ');
                }
                continue;
            }
            if (blockDepth > 0) {
                if (current == '/' && next == '*') {
                    blockDepth++;
                    sanitized.append("  ");
                    index++;
                } else if (current == '*' && next == '/') {
                    blockDepth--;
                    sanitized.append("  ");
                    index++;
                } else {
                    sanitized.append(current == '\n' || current == '\r' ? current : ' ');
                }
                continue;
            }
            if (singleQuoted) {
                sanitized.append(current);
                if (current == '\'' && next == '\'') {
                    sanitized.append(next);
                    index++;
                } else if (current == '\'') {
                    singleQuoted = false;
                }
                continue;
            }
            if (doubleQuoted) {
                sanitized.append(current);
                if (current == '"' && next == '"') {
                    sanitized.append(next);
                    index++;
                } else if (current == '"') {
                    doubleQuoted = false;
                }
                continue;
            }
            if (current == '\'') {
                singleQuoted = true;
                sanitized.append(current);
            } else if (current == '"') {
                doubleQuoted = true;
                sanitized.append(current);
            } else if (current == '-' && next == '-') {
                lineComment = true;
                sanitized.append("  ");
                index++;
            } else if (current == '/' && next == '*') {
                blockDepth = 1;
                sanitized.append("  ");
                index++;
            } else {
                sanitized.append(current);
            }
        }
        return sanitized.toString();
    }

    private static SqlAssetDensity sqlAssetDensity(Path root) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".sql"))
                    .sorted()
                    .toList()) {
                builder.append(Files.readString(path)).append('\n');
            }
        }
        String sql = stripSqlStringLiterals(stripSqlComments(builder.toString()));
        return new SqlAssetDensity(
                count(sql, "\\bCREATE\\s+(?:OR\\s+(?:ALTER|REPLACE)\\s+)?TABLE\\b"),
                count(sql, "\\bFOREIGN\\s+KEY\\b|\\bREFERENCES\\b"),
                count(sql, "\\bCREATE\\s+(?:OR\\s+(?:ALTER|REPLACE)\\s+)?PROCEDURE\\b"),
                count(sql, "\\bCREATE\\s+(?:OR\\s+(?:ALTER|REPLACE)\\s+)?FUNCTION\\b"),
                count(sql, "\\bCREATE\\s+(?:OR\\s+(?:ALTER|REPLACE)\\s+)?TRIGGER\\b"),
                count(sql, "\\bJOIN\\b"),
                count(sql, "\\bINSERT\\b[\\s\\S]{0,300}?\\bSELECT\\b"),
                count(sql, "\\bCASE\\b"),
                count(sql, "\\bCOALESCE\\b|\\bISNULL\\b"),
                count(sql, "\\bOVER\\s*\\("));
    }

    private static int relationProbeTemplates(Path root) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".sql"))
                    .sorted()
                    .toList()) {
                builder.append(Files.readString(path)).append('\n');
            }
        }
        String sql = stripSqlStringLiterals(stripSqlComments(builder.toString()));
        return count(sql, "\\bJOIN\\b[\\s\\S]{0,240}?\\bON\\b[\\s\\S]{0,240}?\\bWHERE\\s+EXISTS\\s*\\("
                + "[\\s\\S]{0,360}?\\bIN\\s*\\(\\s*SELECT\\b");
    }

    private static int count(String text, String regex) {
        int count = 0;
        var matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
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

    private record SqlAssetDensity(
            int createTables,
            int fkReferences,
            int procedures,
            int functions,
            int triggers,
            int joins,
            int insertSelects,
            int cases,
            int nullHandlers,
            int windows
    ) {
    }
}
