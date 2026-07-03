package com.relationdetector.core.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

    @Test
    void routineParsersLiveAtDialectLevelNotFullGrammerLevel() throws IOException {
        Path root = repoRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().contains("/src/main/"))
                    .filter(path -> path.toString().contains("/fullgrammer/routine"))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Routine body parsers belong at dialect level, offenders=" + offenders);
        }

        assertTrue(Files.isDirectory(root.resolve("adaptor-postgres/src/main/java/com/relationdetector/postgres/routine")),
                "PostgreSQL must expose a dialect-level routine package");
        assertTrue(Files.isDirectory(root.resolve("adaptor-oracle/src/main/java/com/relationdetector/oracle/routine")),
                "Oracle must expose a dialect-level routine package");
        assertTrue(Files.isDirectory(root.resolve("adaptor-mysql/src/main/java/com/relationdetector/mysql/routine")),
                "MySQL must expose a dialect-level routine package");
    }

    @Test
    void fullGrammerVisitorsAreNotNamedTokenEventVisitors() throws IOException {
        Path root = repoRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().contains("/fullgrammer/"))
                    .filter(path -> path.getFileName().toString().contains("TokenEventParseTreeVisitor"))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "full-grammer parse-tree visitors should use full-grammer naming, offenders=" + offenders);
        }
    }

    @Test
    void mysqlFullGrammerVisitorsDoNotCarryPostgresRowsetSentinels() throws IOException {
        Path root = repoRoot();
        Path mysqlFullGrammer = root.resolve("adaptor-mysql/src/main/java/com/relationdetector/mysql/fullgrammer");
        try (Stream<Path> stream = Files.walk(mysqlFullGrammer)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsAny(path, List.of("PostgresOnly", "isPostgresOnlyRowsetSentinel")))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "MySQL full-grammer must not hide PostgreSQL-only rowset syntax behind sentinel checks, offenders="
                            + offenders);
        }
    }

    @Test
    void oracleFullGrammerVersionVisitorsAreThinBridges() throws IOException {
        Path root = repoRoot();
        Path oracleFullGrammer = root.resolve("adaptor-oracle/src/main/java/com/relationdetector/oracle/fullgrammer");
        try (Stream<Path> stream = Files.walk(oracleFullGrammer)) {
            List<Path> offenders = stream
                    .filter(path -> path.getFileName().toString().equals("OracleFullGrammerParseTreeVisitor.java"))
                    .filter(path -> path.toString().contains("/fullgrammer/v"))
                    .filter(path -> !isThinOracleVersionVisitor(path))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Oracle version visitors should be thin generated-parser bridges over common full-grammer logic, offenders="
                            + offenders);
        }
    }

    @Test
    void productionLineageDoesNotKeepRegexSqlLineageResolver() throws IOException {
        Path root = repoRoot();
        assertFalse(Files.exists(root.resolve("core/src/main/java/com/relationdetector/core/lineage/SqlLineageResolver.java")),
                "Lineage projection tracing should come from StructuredSqlEvent, not the legacy regex SqlLineageResolver");
    }

    @Test
    void productionLineageDoesNotUseTextRegexFallbacks() throws IOException {
        Path root = repoRoot();
        Path lineageRoot = root.resolve("core/src/main/java/com/relationdetector/core/lineage");
        try (Stream<Path> stream = Files.walk(lineageRoot)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsAny(path, List.of("Pattern.compile", "java.util.regex")))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Lineage must use StructuredSqlEvent / ProjectionTrace, not regex or text fallback, offenders="
                            + offenders);
        }
    }

    @Test
    void jsonResultWriterUsesJacksonObjectModel() throws IOException {
        Path root = repoRoot();
        Path writer = root.resolve("core/src/main/java/com/relationdetector/core/output/JsonResultWriter.java");
        String text = Files.readString(writer);

        assertTrue(text.contains("ObjectMapper") && text.contains("ObjectNode"),
                "JsonResultWriter should build JSON with Jackson ObjectMapper/ObjectNode");
        assertFalse(text.contains("new StringBuilder(4096)"),
                "JsonResultWriter should not hand-roll the top-level JSON document");
    }

    @Test
    void visitorsAndCollectorsDoNotUseStaticMutableState() throws IOException {
        Path root = repoRoot();
        Set<String> mutableTypes = Set.of(
                "List", "Map", "Set", "Deque", "Queue",
                "ArrayList", "LinkedList", "HashMap", "LinkedHashMap", "HashSet", "LinkedHashSet");

        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(this::isVisitorOrCollector)
                    .filter(path -> hasStaticMutableField(path, mutableTypes))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Visitors/collectors must keep parse state per instance and avoid static mutable fields, offenders="
                            + offenders);
        }
    }

    @Test
    void tokenEventAndFullGrammerDoNotDelegateAcrossModes() throws IOException {
        Path root = repoRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().contains("/tokenevent/")
                            || path.toString().contains("/fullgrammer/"))
                    .filter(path -> delegatesAcrossParserModes(path))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "token-event and full-grammer may share core models, but must not delegate parsers/visitors across modes, offenders="
                            + offenders);
        }
    }

    @Test
    void semanticEquivalentIsTheOnlyParityTestSurface() throws IOException {
        Path root = repoRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/test/java/"))
                    .filter(path -> path.getFileName().toString().contains("Parity"))
                    .filter(path -> !path.getFileName().toString().equals("SemanticEquivalentCorrectnessTest.java"))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Cross-parser equality tests must live in SemanticEquivalentCorrectnessTest, offenders="
                            + offenders);
        }
    }

    @Test
    void productionCodeDoesNotExposeShadowParityEntrypoints() throws IOException {
        Path root = repoRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> containsAny(path, List.of("shadow parity", "shadow/parity", "ShadowParity")))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Production code should not expose old shadow/parity mechanisms, offenders=" + offenders);
        }
    }

    @Test
    void namingMatchEnhancerConsumesOnlyTopLevelNamingEvidencePool() throws IOException {
        Path root = repoRoot();
        Path enhancer = root.resolve("core/src/main/java/com/relationdetector/core/relation/NamingMatchEvidenceEnhancer.java");
        String text = Files.readString(enhancer);

        assertTrue(text.contains("List<NamingEvidenceCandidate> namingEvidence"),
                "NamingMatchEvidenceEnhancer must require the top-level naming evidence pool");
        assertFalse(text.contains("NamingMatchRules"),
                "NamingMatchEvidenceEnhancer must not recompute naming rules locally");
        assertFalse(text.contains("NamingEvidenceExtractor"),
                "NamingMatchEvidenceEnhancer must not create naming evidence locally");
        assertFalse(text.contains("void enhance(List<RelationshipCandidate> candidates)"),
                "NamingMatchEvidenceEnhancer must not expose a no-pool overload");
        assertFalse(text.contains("addToPool"),
                "Relationship enhancement must not mutate or backfill the naming evidence pool");

        Path sqlRunner = root.resolve("core/src/main/java/com/relationdetector/core/parser/SqlRelationParserRunner.java");
        assertFalse(Files.readString(sqlRunner).contains("NamingMatchEvidenceEnhancer"),
                "Low-level SQL parser runner must not attach NAMING_MATCH outside the scan evidence pool");
    }

    @Test
    void databaseAdaptorMainClassesDoNotOwnCollectorImplementations() throws IOException {
        Path root = repoRoot();
        List<Path> adaptorMainClasses = List.of(
                root.resolve("adaptor-mysql/src/main/java/com/relationdetector/mysql/MySqlDatabaseAdaptor.java"),
                root.resolve("adaptor-postgres/src/main/java/com/relationdetector/postgres/PostgresDatabaseAdaptor.java"),
                root.resolve("adaptor-oracle/src/main/java/com/relationdetector/oracle/OracleDatabaseAdaptor.java"));

        List<Path> offenders = adaptorMainClasses.stream()
                .filter(path -> {
                    try {
                        String text = Files.readString(path);
                        return text.contains(" static final class ")
                                || text.contains(" private static final class ");
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to read " + path, e);
                    }
                })
                .map(root::relativize)
                .toList();

        assertTrue(offenders.isEmpty(),
                "Database adaptor main classes should only assemble capabilities, offenders=" + offenders);
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

    private boolean isVisitorOrCollector(Path path) {
        String filename = path.getFileName().toString();
        return filename.endsWith("Visitor.java") || filename.endsWith("Collector.java");
    }

    private boolean hasStaticMutableField(Path path, Set<String> mutableTypes) {
        try {
            String text = Files.readString(path);
            return text.lines()
                    .map(String::strip)
                    .filter(line -> line.contains(" static "))
                    .filter(line -> !line.startsWith("//"))
                    .anyMatch(line -> mutableTypes.stream().anyMatch(type ->
                            line.contains(" " + type + "<")
                                    || line.contains(" " + type + " ")
                                    || line.contains(" " + type + "[")));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }

    private boolean delegatesAcrossParserModes(Path path) {
        try {
            String text = Files.readString(path);
            boolean inTokenEvent = path.toString().contains("/tokenevent/");
            boolean inFullGrammer = path.toString().contains("/fullgrammer/");
            if (inTokenEvent && text.contains(".fullgrammer.")) {
                return true;
            }
            return inFullGrammer && text.contains(".tokenevent.");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }

    private static boolean isThinOracleVersionVisitor(Path path) {
        try {
            String text = Files.readString(path);
            long lines = text.lines()
                    .filter(line -> !line.isBlank())
                    .filter(line -> !line.stripLeading().startsWith("*"))
                    .filter(line -> !line.stripLeading().startsWith("//"))
                    .count();
            return lines <= 90 && text.contains("OracleFullGrammerParseTreeEventCollector");
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
