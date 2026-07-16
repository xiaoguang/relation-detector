package com.relationdetector.core.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import javax.lang.model.element.Modifier;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DialectGrammarArchitectureTest {
    private static final List<String> FORBIDDEN_JAVADOC_TEMPLATES = List.of(
            "生产职责与协作边界",
            "对应的生产操作",
            "Production responsibility and collaboration boundary",
            "Executes the production operation represented by");

    @TempDir
    Path tempDir;

    @Test
    void recordDtoExemptionUsesTheTopLevelAstDeclaration() throws IOException {
        Path ordinaryClass = tempDir.resolve("OversizedAnalyzer.java");
        Files.writeString(ordinaryClass, """
                final class OversizedAnalyzer {
                    // record OversizedAnalyzer(String value) {}
                    String example = "record OversizedAnalyzer(";
                }
                """);
        Path recordDto = tempDir.resolve("EndpointDto.java");
        Files.writeString(recordDto, "record EndpointDto(String value) {}\n");

        assertFalse(isRecordDto(ordinaryClass),
                "Comments and strings must not exempt an ordinary class from the source-size gate");
        assertTrue(isRecordDto(recordDto), "A top-level record DTO remains exempt");
    }

    @Test
    void productionJavadocsDoNotContainGenericTemplates() throws IOException {
        Path root = repoRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            List<String> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> {
                        try {
                            String text = Files.readString(path);
                            return FORBIDDEN_JAVADOC_TEMPLATES.stream().anyMatch(text::contains);
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .map(path -> root.relativize(path).toString())
                    .sorted()
                    .toList();
            assertTrue(offenders.isEmpty(), "Generic production Javadocs remain: " + offenders);
        }
    }

    @Test
    void handwrittenProductionBoundariesHaveConcreteJavadoc() throws Exception {
        Path root = repoRoot();
        List<Path> sources;
        try (Stream<Path> stream = Files.walk(root)) {
            sources = stream.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .filter(path -> !path.getFileName().toString().equals("module-info.java"))
                    .toList();
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "JDK compiler is required for Javadoc architecture checks");
        List<String> offenders = new ArrayList<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.ROOT, null)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sources);
            JavacTask task = (JavacTask) compiler.getTask(null, fileManager, null,
                    List.of("-proc:none", "-Xlint:none"), null, units);
            DocTrees docTrees = DocTrees.instance(task);
            SourcePositions positions = docTrees.getSourcePositions();
            for (CompilationUnitTree unit : task.parse()) {
                new ProductionJavadocScanner(root, unit, docTrees, positions, offenders).scan(unit, null);
            }
        }
        assertTrue(offenders.isEmpty(),
                "Handwritten production boundaries require concrete Javadoc, offenders=" + offenders);
    }

    @Test
    void handwrittenProductionPackagesHaveBilingualPackageDocumentation() throws IOException {
        Path root = repoRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            List<String> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(this::isDocumentedProductionSource)
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .map(Path::getParent)
                    .distinct()
                    .filter(directory -> {
                        Path packageInfo = directory.resolve("package-info.java");
                        if (!Files.isRegularFile(packageInfo)) {
                            return true;
                        }
                        try {
                            String text = Files.readString(packageInfo);
                            return !concretePackageContract(text);
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .map(directory -> root.relativize(directory).toString())
                    .sorted()
                    .toList();
            assertTrue(offenders.isEmpty(),
                    "Handwritten production packages require bilingual package-info.java, offenders=" + offenders);
        }
    }

    private boolean concretePackageContract(String text) {
        return text.contains("CN:")
                && text.contains("EN:")
                && text.length() >= 180
                && containsAny(text, "负责", "职责", "边界", "responsibility", "owns", "boundary", "layer")
                && containsAny(text, "输入", "读取", "接收", "消费", "调用", "input", "reads", "receives", "consumes", "calls")
                && containsAny(text, "输出", "产生", "返回", "转换", "渲染", "output", "emits", "returns", "converts", "renders")
                && containsAny(text, "上游", "下游", "交给", "串成", "upstream", "downstream", "through", "connects", "belongs")
                && containsAny(text, "不", "禁止", "does not", "must not", "not in", "rather than");
    }

    private boolean containsAny(String text, String... fragments) {
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String fragment : fragments) {
            if (normalized.contains(fragment.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private boolean isDocumentedProductionSource(Path path) {
        String value = path.toString();
        return value.contains("/contracts/src/main/java/")
                || value.contains("/core/src/main/java/")
                || value.contains("/cli/src/main/java/")
                || value.contains("/adaptor-mysql/src/main/java/")
                || value.contains("/adaptor-postgres/src/main/java/")
                || value.contains("/adaptor-oracle/src/main/java/")
                || value.contains("/adaptor-sqlserver/src/main/java/");
    }

    @Test
    void coreDoesNotRetainThePreMigrationAntlrSourceTree() {
        Path root = repoRoot();
        Path coreAntlr = root.resolve("core/src/main/antlr4/com/relationdetector/core/antlr");
        assertFalse(Files.exists(coreAntlr),
                "the migrated core ANTLR source tree must be removed; grammar sources belong in grammar modules");
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
    void fullGrammarProductionCodeDoesNotImportDialectTokenEventPackages() throws IOException {
        Path root = repoRoot();
        List<String> forbiddenImports = List.of(
                "com.relationdetector.mysql.tokenevent",
                "com.relationdetector.postgres.tokenevent",
                "com.relationdetector.oracle.tokenevent",
                "com.relationdetector.sqlserver.tokenevent");

        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().contains("/fullgrammar/"))
                    .filter(path -> containsAny(path, forbiddenImports))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "full-grammar modules must not import token-event modules, offenders=" + offenders);
        }
    }

    @Test
    void routineParsersLiveAtDialectLevelNotFullGrammarLevel() throws IOException {
        Path root = repoRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().contains("/src/main/"))
                    .filter(path -> path.toString().contains("/fullgrammar/routine"))
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
    void fullGrammarVisitorsAreNotNamedTokenEventVisitors() throws IOException {
        Path root = repoRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().contains("/fullgrammar/"))
                    .filter(path -> path.getFileName().toString().contains("TokenEventParseTreeVisitor"))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "full-grammar parse-tree visitors should use full-grammar naming, offenders=" + offenders);
        }
    }

    @Test
    void fullGrammarStructuredParserWrapperIsNotNamedTokenEvent() throws IOException {
        Path root = repoRoot();
        Path fullGrammarRoot = root.resolve("core/src/main/java/com/relationdetector/core/fullgrammar");
        String legacyWrapperName = "FullGrammar" + "TokenEventStructuredSqlParser";
        try (Stream<Path> stream = Files.walk(fullGrammarRoot)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.getFileName().toString().equals(legacyWrapperName + ".java")
                            || containsAny(path, List.of(legacyWrapperName,
                                    "FULL_GRAMMAR_TOKEN_EVENT_PRIMARY")))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "full-grammar profile parser wrapper must not mention token-event, offenders=" + offenders);
        }
    }

    @Test
    void fullGrammarSqlParserFactoryIsNotNamedTokenEvent() throws IOException {
        Path root = repoRoot();
        String legacyFactoryName = "FullGrammar" + "TokenEventParserFactory";
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java")
                            || path.toString().endsWith(".md"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.toString().contains("/.git/"))
                    .filter(path -> containsAny(path, List.of(legacyFactoryName)))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "full-grammar SQL parser factory must not use legacy token-event naming, offenders="
                            + offenders);
        }
    }

    @Test
    void mysqlFullGrammarVisitorsDoNotCarryPostgresRowsetSentinels() throws IOException {
        Path root = repoRoot();
        Path mysqlFullGrammar = root.resolve("adaptor-mysql/src/main/java/com/relationdetector/mysql/fullgrammar");
        try (Stream<Path> stream = Files.walk(mysqlFullGrammar)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsAny(path, List.of("PostgresOnly", "isPostgresOnlyRowsetSentinel")))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "MySQL full-grammar must not hide PostgreSQL-only rowset syntax behind sentinel checks, offenders="
                            + offenders);
        }
    }

    @Test
    void oracleFullGrammarVersionVisitorsAreThinBridges() throws IOException {
        Path root = repoRoot();
        Path oracleFullGrammar = root.resolve("adaptor-oracle/src/main/java/com/relationdetector/oracle/fullgrammar");
        try (Stream<Path> stream = Files.walk(oracleFullGrammar)) {
            List<Path> offenders = stream
                    .filter(path -> path.getFileName().toString().equals("OracleFullGrammarParseTreeVisitor.java"))
                    .filter(path -> path.toString().contains("/fullgrammar/v"))
                    .filter(path -> !isThinOracleVersionVisitor(path))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Oracle version visitors should be thin generated-parser bridges over common full-grammar logic, offenders="
                            + offenders);
        }
    }

    @Test
    void mysqlAndPostgresFullGrammarVersionVisitorsKeepSharedStateInCommon() throws IOException {
        Path root = repoRoot();
        List<Path> roots = List.of(
                root.resolve("adaptor-mysql/src/main/java/com/relationdetector/mysql/fullgrammar"),
                root.resolve("adaptor-postgres/src/main/java/com/relationdetector/postgres/fullgrammar"));
        for (Path fullGrammarRoot : roots) {
            try (Stream<Path> stream = Files.walk(fullGrammarRoot)) {
                List<Path> offenders = stream
                        .filter(path -> path.getFileName().toString().endsWith("FullGrammarParseTreeVisitor.java"))
                        .filter(path -> path.toString().contains("/fullgrammar/v"))
                        .filter(path -> containsAny(path, List.of(
                                "existsDepth",
                                "InsertSelectState",
                                "ArrayDeque<InsertSelect",
                                "record ColumnParts")))
                        .map(root::relativize)
                        .toList();

                assertTrue(offenders.isEmpty(),
                        "Version full-grammar visitors should keep reusable state/helpers in fullgrammar/common, offenders="
                                + offenders);
            }
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
    void productionRegexUsageIsAllowlistedAndNonStructural() throws IOException {
        Path root = repoRoot();
        Set<Path> allowed = Set.of(
                Path.of("core/src/main/java/com/relationdetector/core/fullgrammar/SqlGrammarProfileRegistry.java"),
                Path.of("cli/src/main/java/com/relationdetector/cli/BatchManifestLoader.java"));
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> containsAny(path, List.of("Pattern.compile", "java.util.regex")))
                    .map(root::relativize)
                    .filter(path -> !allowed.contains(path))
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Production regex use must stay in version/config/log-noise boundaries, offenders=" + offenders);
        }

        for (Path path : allowed) {
            String text = Files.readString(root.resolve(path));
            assertFalse(text.contains("new RelationshipCandidate") || text.contains("DataLineageCandidate")
                            || text.contains("StructuredSqlEvent"),
                    "Regex allowlist file must not create relationship/lineage/structured events: " + path);
        }
    }

    @Test
    void factExtractionDoesNotResolveGrammarRulesByNameOrReflection() throws IOException {
        Path root = repoRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(this::isFactExtractionPath)
                    .filter(path -> containsAny(path, List.of(
                            "getRuleNames(",
                            "getMethod(methodName)",
                            "getDeclaredMethod(methodName)")))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Fact extraction must use typed generated contexts, not grammar rule names or reflection, offenders="
                            + offenders);
        }
    }

    @Test
    void coreFullGrammarSemanticsDoNotInspectTerminalText() throws IOException {
        Path root = repoRoot();
        Path packageRoot = root.resolve("core/src/main/java/com/relationdetector/core/fullgrammar");
        List<String> semanticFiles = List.of(
                "FullGrammarExpressionAnalyzer.java",
                "DirectColumnTraceSupport.java",
                "SubqueryProjectionTraceSupport.java",
                "PredicateEventSink.java",
                "SourceLocationSupport.java");

        List<Path> offenders = semanticFiles.stream()
                .map(packageRoot::resolve)
                .filter(path -> containsAny(path, List.of(
                        "TerminalNode",
                        ".getText()",
                        "Set.of(\"select\"",
                        "directKeywordIndex(")))
                .map(root::relativize)
                .toList();

        assertTrue(offenders.isEmpty(),
                "Core full-grammar semantics must consume typed adapter views, not terminal text, offenders="
                        + offenders);
    }

    @Test
    void fullGrammarCollectorsTraverseOnlyTypedAdapterChildren() throws IOException {
        Path root = repoRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().contains("/fullgrammar/"))
                    .filter(path -> !path.getFileName().toString().contains("ParseTreeAdapter"))
                    .filter(path -> containsAny(path, List.of(".getChild(", "getChildCount(")))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Full-grammar collectors must recurse through adapter.typedChildren(), offenders=" + offenders);
        }
    }

    @Test
    void factExtractionDoesNotUseStringRegexApis() throws IOException {
        Path root = repoRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(this::isFactExtractionPath)
                    .filter(path -> containsAny(path, List.of(
                            "java.util.regex",
                            "Pattern.compile(",
                            ".replaceAll(\"",
                            ".replaceFirst(\"",
                            ".split(\"\\\\")))
                    .filter(path -> !path.endsWith(Path.of(
                            "core/fullgrammar/SqlGrammarProfileRegistry.java")))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Fact extraction must not infer structure with String regex APIs, offenders=" + offenders);
        }
    }

    @Test
    void dialectScriptFramersAreTheOnlySqlFileFramingPath() {
        Path root = repoRoot();
        assertFalse(Files.exists(root.resolve(
                        "core/src/main/java/com/relationdetector/core/log/PlainSqlLogExtractor.java")),
                "Plain SQL framing must go through DialectScriptFramer");
        assertFalse(Files.exists(root.resolve(
                        "core/src/main/java/com/relationdetector/core/log/ObjectSqlFileExtractor.java")),
                "Object framing must go through DialectScriptFramer");
        assertFalse(Files.exists(root.resolve(
                        "adaptor-mysql/src/main/java/com/relationdetector/mysql/fullgrammar/MySqlFullGrammarSqlNormalizer.java")),
                "MySQL client directives must be removed by the script framer before parser selection");
    }

    @Test
    void ddlFilesAreFramedByTheDialectScriptFramerBeforeDdlGrammar() throws IOException {
        Path root = repoRoot();
        String pipeline = Files.readString(root.resolve(
                "core/src/main/java/com/relationdetector/core/scan/SourceCollectorPipeline.java"));
        String ddlRunner = Files.readString(root.resolve(
                "core/src/main/java/com/relationdetector/core/parser/DdlRelationParserRunner.java"));
        String execution = Files.readString(root.resolve(
                "core/src/main/java/com/relationdetector/core/scan/StatementExecutionService.java"));

        assertTrue(pipeline.contains("StatementSourceType.DDL_FILE")
                        && pipeline.contains("ctx.adaptor.parsers().scriptFramer()"),
                "DDL files must use the selected adaptor script framer");
        assertFalse(pipeline.contains("statementParser.executeDdlFile("),
                "Production DDL file collection must not bypass dialect script framing");
        assertFalse(ddlRunner.contains("Files.readString") || execution.contains("executeDdlFile("),
                "Core must not expose a direct DDL-file-to-grammar bypass");
    }

    @Test
    void nativeLogNoiseClassificationRunsAfterStructuredParsing() throws IOException {
        Path root = repoRoot();
        Path runner = root.resolve("core/src/main/java/com/relationdetector/core/parser/SqlRelationParserRunner.java");
        String runnerText = Files.readString(runner);

        assertFalse(runnerText.contains("SqlLogNoiseFilter"),
                "Raw SQL text must not be filtered before structured parsing");
        assertTrue(Files.exists(root.resolve(
                        "core/src/main/java/com/relationdetector/core/log/TypedLogNoiseClassifier.java")),
                "Native log noise must be classified from typed ROWSET_REFERENCE events");
    }

    @Test
    void nativeLogExtractorsDoNotFilterSqlByKeywordText() throws IOException {
        Path root = repoRoot();
        for (String extractor : List.of(
                "adaptor-mysql/src/main/java/com/relationdetector/mysql/log/MySqlLogExtractor.java",
                "adaptor-postgres/src/main/java/com/relationdetector/postgres/log/PostgresLogExtractor.java",
                "adaptor-oracle/src/main/java/com/relationdetector/oracle/log/OracleLogExtractor.java",
                "adaptor-sqlserver/src/main/java/com/relationdetector/sqlserver/log/SqlServerLogExtractor.java")) {
            String text = Files.readString(root.resolve(extractor)).toLowerCase(Locale.ROOT);
            assertFalse(text.contains("contains(\"select\")") || text.contains("contains(\"join\")")
                            || text.contains("endswith(\";\")"),
                    extractor + " must extract log payloads and delegate SQL framing/classification to typed parsers");
            assertTrue(text.contains("scriptparser") || text.contains("scriptfileextractor"),
                    extractor + " must delegate payload framing to the dialect script framer");
        }
    }

    @Test
    void structuredLineageExtractorDoesNotMergePerStatement() throws IOException {
        Path root = repoRoot();
        Path extractor = root.resolve(
                "core/src/main/java/com/relationdetector/core/lineage/StructuredDataLineageExtractor.java");
        String text = Files.readString(extractor);

        assertFalse(text.contains("DataLineageMerger"),
                "StructuredDataLineageExtractor must emit candidates; merge belongs to result assembly or correctness assertions");
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
    void tokenEventAndFullGrammarDoNotDelegateAcrossModes() throws IOException {
        Path root = repoRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().contains("/tokenevent/")
                            || path.toString().contains("/fullgrammar/"))
                    .filter(path -> delegatesAcrossParserModes(path))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "token-event and full-grammar may share core models, but must not delegate parsers/visitors across modes, offenders="
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
        Path enhancer = root.resolve("core/src/main/java/com/relationdetector/core/naming/NamingMatchEvidenceEnhancer.java");
        String text = Files.readString(enhancer);

        assertTrue(text.contains("NamingEvidencePool namingEvidence"),
                "NamingMatchEvidenceEnhancer must require the top-level naming evidence pool");
        assertFalse(text.contains("NamingRuleEngine"),
                "NamingMatchEvidenceEnhancer must not recompute naming rules locally");
        assertFalse(text.contains("NamingEvidenceExtractor"),
                "NamingMatchEvidenceEnhancer must not create naming evidence locally");
        assertFalse(text.contains("void enhance(List<RelationshipCandidate> candidates)"),
                "NamingMatchEvidenceEnhancer must not expose a no-pool overload");
        assertFalse(text.contains("List<NamingEvidenceCandidate> namingEvidence"),
                "NamingMatchEvidenceEnhancer must not expose a list-based compatibility overload");
        assertFalse(text.contains("addToPool"),
                "Relationship enhancement must not mutate or backfill the naming evidence pool");

        Path sqlRunner = root.resolve("core/src/main/java/com/relationdetector/core/parser/SqlRelationParserRunner.java");
        assertFalse(Files.readString(sqlRunner).contains("NamingMatchEvidenceEnhancer"),
                "Low-level SQL parser runner must not attach NAMING_MATCH outside the scan evidence pool");
    }

    @Test
    void namingRuleEngineIsOnlyCalledByNamingEvidenceExtractor() throws IOException {
        Path root = repoRoot();
        try (Stream<Path> stream = Files.walk(root.resolve("core/src/main/java"))) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsAny(path, List.of("new NamingRuleEngine", "namingRuleEngine.match")))
                    .map(root::relativize)
                    .filter(path -> !path.equals(Path.of(
                            "core/src/main/java/com/relationdetector/core/naming/NamingEvidenceExtractor.java")))
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Only NamingEvidenceExtractor may execute naming rules; relationships consume the evidence pool, offenders="
                            + offenders);
        }
    }

    @Test
    void namingHeuristicsLiveOnlyInCoreNamingPackage() throws IOException {
        Path root = repoRoot();
        Path namingRoot = root.resolve("core/src/main/java/com/relationdetector/core/naming");
        assertTrue(Files.exists(namingRoot.resolve("NamingRuleEngine.java")),
                "Naming rules must have an explicit package boundary");

        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> !path.startsWith(namingRoot))
                    .filter(path -> containsAny(path, List.of(
                            "singularStem(",
                            "endsWith(\"_id\")")))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Suffix/plural naming heuristics may run only inside core.naming, offenders=" + offenders);
        }
    }

    @Test
    void correctnessExecutorReusesStatementExecutionService() throws IOException {
        Path root = repoRoot();
        Path executor = root.resolve("cli/src/test/java/com/relationdetector/cli/CorrectnessFixtureExecutor.java");
        Path executionEngine = root.resolve("cli/src/test/java/com/relationdetector/cli/FixtureExecutionEngine.java");
        String executorText = Files.readString(executor);
        String engineText = Files.readString(executionEngine);

        assertTrue(engineText.contains("StatementExecutionService"),
                "Correctness execution engine must reuse the production statement execution service");
        assertTrue(engineText.contains("EvidenceEnhancementService"),
                "Correctness execution engine must reuse the shared evidence enhancement service for SQL fixtures");
        assertFalse(executorText.contains("StatementExecutionService"),
                "CorrectnessFixtureExecutor should stay a coordinator; execution belongs in FixtureExecutionEngine");
        assertFalse(executorText.contains("EvidenceEnhancementService"),
                "CorrectnessFixtureExecutor should stay a coordinator; enhancement belongs in FixtureExecutionEngine");
        String combinedText = executorText + "\n" + engineText;
        assertFalse(combinedText.contains("new SqlRelationParserRunner"),
                "Correctness executor must not assemble SQL parser runners directly");
        assertFalse(combinedText.contains("new DdlRelationParserRunner"),
                "Correctness executor must not assemble DDL parser runners directly");
        assertFalse(combinedText.contains("new StructuredDataLineageExtractor"),
                "Correctness executor must not extract lineage outside StatementExecutionService");
        assertFalse(combinedText.contains("new NamingMatchEvidenceEnhancer"),
                "Correctness executor must not attach NAMING_MATCH outside EvidenceEnhancementService");
        assertFalse(combinedText.contains("new StructuredRelationshipExtractor"),
                "Correctness executor must not bypass StatementExecutionService for common fixtures");
    }

    @Test
    void correctnessExecutorIsOnlyFixtureCoordinator() throws IOException {
        Path root = repoRoot();
        Path executor = root.resolve("cli/src/test/java/com/relationdetector/cli/CorrectnessFixtureExecutor.java");
        String text = Files.readString(executor);

        for (String collaborator : List.of("FixtureInputLoader", "FixtureExecutionEngine",
                "GoldenAssertion")) {
            assertTrue(text.contains(collaborator),
                    "CorrectnessFixtureExecutor should delegate to " + collaborator);
        }
        assertTrue(Files.exists(root.resolve("cli/src/test/java/com/relationdetector/cli/GoldenWriter.java")),
                "GoldenWriter should own correctness golden write operations");
        assertTrue(Files.readString(root.resolve("cli/src/test/java/com/relationdetector/cli/GoldenAssertion.java"))
                        .contains("GoldenWriter"),
                "GoldenAssertion should delegate updateCorrectnessGold writes to GoldenWriter");
        assertFalse(text.contains("Files.readString"),
                "Fixture input and expected JSON reads belong in FixtureInputLoader");
        assertFalse(text.contains("Files.writeString"),
                "Golden writes belong in GoldenWriter");
        assertFalse(text.contains("new StatementExecutionService"),
                "Statement execution belongs in FixtureExecutionEngine");
        assertFalse(text.contains("new EvidenceEnhancementService"),
                "Evidence enhancement belongs in FixtureExecutionEngine");
        assertFalse(text.contains("assertEquals"),
                "Golden comparisons belong in GoldenAssertion");
    }

    @Test
    void coreUsesGroupedDatabaseAdaptorCapabilities() throws IOException {
        Path root = repoRoot();
        Set<String> oldSpiCalls = Set.of(
                "adaptor.metadataCollector(",
                "adaptor.objectDefinitionCollector(",
                "adaptor.databaseDdlCollector(",
                "adaptor.sqlLogExtractor(",
                "adaptor.sqlRelationParser(",
                "adaptor.structuredSqlParser(",
                "adaptor.structuredDdlParser(",
                "adaptor.dataProfiler(",
                "adaptor.evidenceWeightAdjuster(");
        try (Stream<Path> stream = Files.walk(root.resolve("core/src/main/java"))) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsAny(path, oldSpiCalls.stream().toList()))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Core production code must consume adaptor.collectors()/parsers()/profiling(), offenders="
                            + offenders);
        }
    }

    @Test
    void databaseAdaptorV5ExposesOnlyGroupedCapabilitiesAndScriptFraming() throws IOException {
        Path root = repoRoot();
        String adaptor = Files.readString(root.resolve(
                "contracts/src/main/java/com/relationdetector/contracts/spi/DatabaseAdaptor.java"));

        assertTrue(adaptor.contains("AdaptorCollectors collectors()"));
        assertTrue(adaptor.contains("AdaptorParsers parsers()"));
        assertTrue(adaptor.contains("AdaptorProfiling profiling()"));
        String parsers = Files.readString(root.resolve(
                "contracts/src/main/java/com/relationdetector/contracts/spi/AdaptorParsers.java"));
        assertTrue(parsers.contains("DialectScriptFramer scriptFramer"),
                "SPI v5 parser capabilities must include dialect script framing");
        for (String legacyGetter : List.of(
                "MetadataCollector metadataCollector()",
                "ObjectDefinitionCollector objectDefinitionCollector()",
                "DatabaseDdlCollector databaseDdlCollector()",
                "SqlLogExtractor sqlLogExtractor()",
                "SqlRelationParser sqlRelationParser()",
                "StructuredSqlParser structuredSqlParser()",
                "StructuredDdlParser structuredDdlParser()",
                "DataProfiler dataProfiler()",
                "EvidenceWeightAdjuster evidenceWeightAdjuster()")) {
            assertFalse(adaptor.contains(legacyGetter),
                    "SPI v5 must not restore legacy getter: " + legacyGetter);
        }

        String version = Files.readString(root.resolve(
                "contracts/src/main/java/com/relationdetector/contracts/spi/AdaptorApiVersion.java"));
        assertTrue(version.contains("CURRENT = 5"), "adaptor SPI must expose typed optional collectors as v5");
    }

    @Test
    void structuredParserEventsUseOnlyTheSealedTypedContract() throws IOException {
        Path root = repoRoot();
        Path parseContract = root.resolve("contracts/src/main/java/com/relationdetector/contracts/parse");
        String eventContract = Files.readString(parseContract.resolve("StructuredSqlEvent.java"));

        assertTrue(eventContract.contains("public sealed interface StructuredSqlEvent"),
                "StructuredSqlEvent must remain a sealed typed hierarchy");
        assertFalse(eventContract.contains("attributes()") || eventContract.contains("Map<String, Object>"),
                "StructuredSqlEvent must not restore the legacy attributes map");

        List<String> typedEvents = List.of(
                "RowsetEvent.java", "PredicateEvent.java", "ProjectionEvent.java",
                "WriteEvent.java", "DdlEvent.java", "DynamicSqlEvent.java");
        for (String filename : typedEvents) {
            String text = Files.readString(parseContract.resolve(filename));
            assertFalse(text.contains("Map<String, Object>") || text.contains(" attributes"),
                    filename + " must expose explicit typed fields");
        }

        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> containsAny(path, List.of(
                            "StructuredSqlEvent.attributes()",
                            "event.attributes()",
                            "new StructuredSqlEvent(")))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Production parser events must use typed fields only, offenders=" + offenders);
        }
    }

    @Test
    void fullGrammarTypedSinkDelegatesToFocusedHelpers() throws IOException {
        Path root = repoRoot();
        Path sink = root.resolve("core/src/main/java/com/relationdetector/core/fullgrammar/FullGrammarEventFacade.java");
        String text = Files.readString(sink);

        for (String helper : List.of("RowsetScopeSink", "ProjectionEventSink",
                "PredicateEventSink", "DirectColumnTraceSupport", "SubqueryProjectionTraceSupport",
                "WriteMappingSink", "SourceLocationSupport")) {
            assertTrue(text.contains(helper),
                    "FullGrammarEventFacade should delegate " + helper + " responsibilities");
        }
        assertTrue(effectiveCodeLineCount(sink) <= 400,
                "FullGrammarEventFacade must remain a thin facade");
        assertFalse(text.contains("new StructuredSqlEvent"),
                "StructuredSqlEvent creation belongs in FullGrammarEventRecorder");
        assertFalse(text.contains("eventKeys"),
                "Event de-duplication belongs in FullGrammarEventRecorder");
    }

    @Test
    void parserVisitorsAndCollectorsRemainFocused() throws IOException {
        Path root = repoRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            List<String> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().contains("/fullgrammar/")
                            || path.toString().contains("/tokenevent/")
                            || path.toString().contains("/routine/"))
                    .filter(path -> path.getFileName().toString().contains("Visitor")
                            || path.getFileName().toString().contains("Collector"))
                    .filter(path -> effectiveCodeLineCount(path) > 400)
                    .map(path -> root.relativize(path) + "=" + effectiveCodeLineCount(path))
                    .toList();
            assertTrue(offenders.isEmpty(),
                    "Parser visitors/collectors must delegate focused responsibilities, offenders=" + offenders);
        }
    }

    @Test
    void semanticProductionClassesRemainFocused() throws IOException {
        Path root = repoRoot();
        List<String> boundedSuffixes = List.of(
                "Analyzer.java", "Support.java", "Extractor.java", "Resolver.java",
                "Merger.java", "Framer.java", "Facade.java");
        try (Stream<Path> stream = Files.walk(root)) {
            List<String> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().contains("/core/src/main/java/com/relationdetector/core/")
                            || isAdaptorSemanticSource(path))
                    .filter(path -> boundedSuffixes.stream()
                            .anyMatch(suffix -> path.getFileName().toString().endsWith(suffix)))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .filter(path -> !isRecordDto(path))
                    .filter(path -> effectiveCodeLineCount(path) > 450)
                    .map(path -> root.relativize(path) + "=" + effectiveCodeLineCount(path))
                    .sorted()
                    .toList();
            assertTrue(offenders.isEmpty(),
                    "Semantic production classes must delegate focused responsibilities, offenders=" + offenders);
        }
    }

    @Test
    void structuredScriptFramerDelegatesDialectPlanning() throws IOException {
        Path script = repoRoot().resolve("core/src/main/java/com/relationdetector/core/script");
        Path orchestrator = script.resolve("StructuredScriptFramer.java");
        String text = Files.readString(orchestrator);

        assertTrue(effectiveCodeLineCount(orchestrator) <= 200,
                "StructuredScriptFramer must remain a thin framing orchestrator");
        for (String method : List.of(
                "splitMysql(", "splitPostgres(", "splitOracle(", "splitSqlServer(", "splitCommon(")) {
            assertFalse(text.contains(method),
                    "Dialect slicing belongs in a dedicated planner, found " + method);
        }
        for (String planner : List.of(
                "MySqlScriptSlicePlanner", "PostgresScriptSlicePlanner", "OracleScriptSlicePlanner",
                "SqlServerScriptSlicePlanner", "CommonScriptSlicePlanner")) {
            Path source = script.resolve(planner + ".java");
            assertTrue(Files.isRegularFile(source), "Missing dialect planner " + planner);
            assertTrue(effectiveCodeLineCount(source) <= 250,
                    planner + " must remain focused");
        }
    }

    @Test
    void derivedPathServiceDelegatesToFocusedInferenceComponents() throws IOException {
        Path root = repoRoot();
        Path service = root.resolve(
                "core/src/main/java/com/relationdetector/core/derived/DerivedPathInferenceService.java");
        String text = Files.readString(service);

        for (String component : List.of(
                "DerivedPathGraphBuilder",
                "DerivedRelationshipInference",
                "DerivedLineageInference",
                "DerivedNamingInference")) {
            assertTrue(text.contains(component),
                    "DerivedPathInferenceService should delegate to " + component);
        }
        assertTrue(effectiveCodeLineCount(service) <= 140,
                "DerivedPathInferenceService must remain an orchestration facade");
    }

    @Test
    void evidenceMergersShareObservationAggregationEngine() throws IOException {
        Path root = repoRoot();
        for (String merger : List.of(
                "core/src/main/java/com/relationdetector/core/relation/RelationshipMerger.java",
                "core/src/main/java/com/relationdetector/core/lineage/DataLineageMerger.java",
                "core/src/main/java/com/relationdetector/core/naming/NamingEvidenceMerger.java")) {
            String text = Files.readString(root.resolve(merger));
            assertTrue(text.contains("EvidenceObservationAggregator"),
                    merger + " must use the common observation aggregation engine");
        }
    }

    @Test
    void coreFullGrammarDoesNotInferGrammarStructureFromContextClassNames() throws IOException {
        Path root = repoRoot();
        Path fullGrammar = root.resolve("core/src/main/java/com/relationdetector/core/fullgrammar");

        try (Stream<Path> stream = Files.walk(fullGrammar)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsAny(path, List.of(
                            "getClass().getSimpleName()",
                            "descendantWithClassContaining",
                            "directChildWithClassContaining",
                            "containsContextNamed")))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Core full-grammar semantics must come from a dialect context adapter, offenders=" + offenders);
        }
    }

    @Test
    void coreTokenEventDoesNotInferGrammarStructureFromContextClassNames() throws IOException {
        Path root = repoRoot();
        Path tokenEvent = root.resolve("core/src/main/java/com/relationdetector/core/tokenevent");

        try (Stream<Path> stream = Files.walk(tokenEvent)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsAny(path, List.of(
                            "getClass().getSimpleName()",
                            "getClass().getName()")))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Core token-event structure checks must use typed predicates, offenders=" + offenders);
        }
    }

    @Test
    void tokenEventAndRoutineSemanticsDoNotReconstructStructureFromTerminalText() throws IOException {
        Path root = repoRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().contains("/tokenevent/")
                            || path.toString().contains("/routine/"))
                    .filter(path -> containsAny(path, List.of(
                            "collectLeafText(",
                            "join.joinType().getText()",
                            "joinType.getText()")))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Token-event/routine facts must use typed contexts, not terminal or join-keyword text, offenders="
                            + offenders);
        }
    }

    @Test
    void dialectFullGrammarDoesNotInferGrammarStructureFromContextClassNames() throws IOException {
        Path root = repoRoot().getParent();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().contains("/fullgrammar/"))
                    .filter(path -> {
                        String filename = path.getFileName().toString();
                        return filename.contains("ExpressionAnalyzer")
                                || filename.contains("EventVisitorCore")
                                || filename.contains("ParseTreeEventCollector")
                                || filename.equals("FullGrammarEventFacade.java");
                    })
                    .filter(path -> containsAny(path, List.of(
                            "getClass().getSimpleName()",
                            "getClass().getName()")))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Dialect full-grammar semantics must use typed context adapters, offenders=" + offenders);
        }
    }

    @Test
    void fullGrammarDialectsProvideSemanticContextAdapters() throws IOException {
        Path root = repoRoot();
        List<Path> dialectRoots = List.of(
                root.resolve("adaptor-mysql/src/main/java/com/relationdetector/mysql/fullgrammar"),
                root.resolve("adaptor-postgres/src/main/java/com/relationdetector/postgres/fullgrammar"),
                root.resolve("adaptor-sqlserver/src/main/java/com/relationdetector/sqlserver/fullgrammar"));

        for (Path dialectRoot : dialectRoots) {
            try (Stream<Path> stream = Files.walk(dialectRoot)) {
                assertTrue(stream
                                .filter(path -> path.toString().endsWith(".java"))
                                .anyMatch(path -> containsAny(path, List.of(
                                        "implements FullGrammarParseTreeAdapter",
                                        "extends AbstractFullGrammarParseTreeAdapter"))),
                        "Dialect full-grammar must provide an explicit semantic context adapter: "
                                + root.relativize(dialectRoot));
            }
        }
    }

    @Test
    void scanUsesOneExecutorAndDoesNotRepeatFullEvidenceEnhancement() throws IOException {
        Path root = repoRoot();
        String engine = Files.readString(root.resolve(
                "core/src/main/java/com/relationdetector/core/scan/ScanEngine.java"));
        String collectors = Files.readString(root.resolve(
                "core/src/main/java/com/relationdetector/core/scan/SourceCollectorPipeline.java"));

        assertEquals(1, countOccurrences(engine, "evidenceEnhancementPipeline.enhance("),
                "A scan must not rerun the full naming/metadata enhancement after profiling");
        assertFalse(collectors.contains("Executors.newFixedThreadPool"),
                "Source groups must share the scan-scoped executor");
        assertTrue(engine.contains("pipelineContext.close()"),
                "ScanEngine must close its scan-scoped executor");
    }

    @Test
    void tokenEventVisitorsUseSharedEventEmitter() throws IOException {
        Path root = repoRoot();
        List<Path> visitors = List.of(
                root.resolve("core/src/main/java/com/relationdetector/core/tokenevent/CommonTokenEventParseTreeVisitor.java"),
                root.resolve("adaptor-mysql/src/main/java/com/relationdetector/mysql/tokenevent/MySqlTokenEventParseTreeVisitor.java"),
                root.resolve("adaptor-postgres/src/main/java/com/relationdetector/postgres/tokenevent/PostgresTokenEventParseTreeVisitor.java"),
                root.resolve("adaptor-oracle/src/main/java/com/relationdetector/oracle/tokenevent/OracleTokenEventParseTreeVisitor.java"),
                root.resolve("adaptor-sqlserver/src/main/java/com/relationdetector/sqlserver/tokenevent/SqlServerTokenEventParseTreeVisitor.java"));

        for (Path visitor : visitors) {
            String text = Files.readString(visitor);
            String packageSources;
            try (Stream<Path> stream = Files.list(visitor.getParent())) {
                packageSources = stream
                        .filter(path -> path.toString().endsWith(".java"))
                        .map(path -> {
                            try {
                                return Files.readString(path);
                            } catch (IOException error) {
                                throw new java.io.UncheckedIOException(error);
                            }
                        })
                        .collect(java.util.stream.Collectors.joining("\n"));
            }
            assertTrue(packageSources.contains("TokenEventEventEmitter"),
                    "Token-event visitor package should delegate event/source emission to TokenEventEventEmitter: "
                            + root.relativize(visitor.getParent()));
            assertFalse(text.contains("new StructuredSqlEvent"),
                    "Token-event visitors should not construct StructuredSqlEvent directly: "
                            + root.relativize(visitor));
            assertFalse(text.contains("\"tokenEventNative\""),
                    "tokenEventNative attribute creation belongs in TokenEventEventEmitter: "
                            + root.relativize(visitor));
        }
    }

    @Test
    void dialectDataProfilersDelegateJdbcWorkToCoreTemplate() throws IOException {
        Path root = repoRoot();
        List<Path> profilers = List.of(
                root.resolve("adaptor-mysql/src/main/java/com/relationdetector/mysql/profile/MySqlDataProfiler.java"),
                root.resolve("adaptor-postgres/src/main/java/com/relationdetector/postgres/profile/PostgresDataProfiler.java"),
                root.resolve("adaptor-oracle/src/main/java/com/relationdetector/oracle/profile/OracleDataProfiler.java"),
                root.resolve("adaptor-sqlserver/src/main/java/com/relationdetector/sqlserver/profile/SqlServerDataProfiler.java"));

        for (Path profiler : profilers) {
            String text = Files.readString(profiler);
            assertTrue(text.contains("JdbcDataProfilerTemplate"),
                    "Dialect profilers should delegate JDBC execution and metrics building to the core template: "
                            + root.relativize(profiler));
            assertFalse(text.contains("createStatement(") || text.contains("executeQuery(")
                            || text.contains("DataProfileEvidenceBuilder"),
                    "Dialect profilers should only render dialect SQL and source labels: " + root.relativize(profiler));
        }
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

    @Test
    void productionScanUsesImmutableResolvedConfiguration() throws IOException {
        Path root = repoRoot();
        String engine = Files.readString(root.resolve(
                "core/src/main/java/com/relationdetector/core/scan/ScanEngine.java"));
        String context = Files.readString(root.resolve(
                "core/src/main/java/com/relationdetector/core/scan/ScanPipelineContext.java"));
        String cliRunner = Files.readString(root.resolve(
                "cli/src/main/java/com/relationdetector/cli/SingleScanRunner.java"));

        assertTrue(engine.contains("scan(config.resolve(), adaptor)"),
                "Mutable ScanConfig must be snapshotted at the production scan boundary");
        assertFalse(engine.contains("config.databaseVersion ="),
                "JDBC version discovery must return a new resolved config, not mutate the caller DTO");
        assertTrue(context.contains("final ResolvedScanConfig config"),
                "Scan pipeline state must hold the immutable runtime config");
        int override = cliRunner.indexOf("applyOverrides(config, request)");
        int resolution = cliRunner.indexOf("resolved = config.resolve(configDirectory)");
        assertTrue(override >= 0 && resolution > override,
                "CLI overrides must be complete before base-directory-aware immutable resolution");
    }

    @Test
    void yamlLoaderUsesTypedTransportDto() throws IOException {
        Path root = repoRoot();
        String loader = Files.readString(root.resolve(
                "cli/src/main/java/com/relationdetector/cli/SimpleYamlConfigLoader.java"));

        assertTrue(loader.contains("readValue(file.toFile(), ScanYamlConfigDto.class)"));
        assertFalse(loader.contains("readTree("),
                "Scan YAML must deserialize through the typed transport model");
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

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static long effectiveCodeLineCount(Path path) {
        try {
            long count = 0;
            boolean inBlockComment = false;
            for (String line : Files.readAllLines(path)) {
                String remaining = line.strip();
                while (!remaining.isEmpty()) {
                    if (inBlockComment) {
                        int end = remaining.indexOf("*/");
                        if (end < 0) {
                            remaining = "";
                        } else {
                            inBlockComment = false;
                            remaining = remaining.substring(end + 2).stripLeading();
                        }
                    } else if (remaining.startsWith("//")) {
                        remaining = "";
                    } else if (remaining.startsWith("/*")) {
                        inBlockComment = true;
                        remaining = remaining.substring(2);
                    } else {
                        count++;
                        remaining = "";
                    }
                }
            }
            return count;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to count lines in " + path, exception);
        }
    }

    private static boolean isAdaptorSemanticSource(Path path) {
        String value = path.toString();
        boolean adaptor = value.contains("/adaptor-mysql/src/main/java/")
                || value.contains("/adaptor-postgres/src/main/java/")
                || value.contains("/adaptor-oracle/src/main/java/")
                || value.contains("/adaptor-sqlserver/src/main/java/");
        return adaptor && (value.contains("/fullgrammar/")
                || value.contains("/tokenevent/")
                || value.contains("/routine/")
                || value.contains("/common/"));
    }

    private static boolean isRecordDto(Path path) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is required for record DTO classification");
        }
        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(null, Locale.ROOT, null)) {
            String filename = path.getFileName().toString();
            String typeName = filename.substring(0, filename.length() - ".java".length());
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjects(path);
            JavacTask task = (JavacTask) compiler.getTask(null, fileManager, null,
                    List.of("-proc:none", "-Xlint:none"), null, units);
            for (CompilationUnitTree unit : task.parse()) {
                for (Tree declaration : unit.getTypeDecls()) {
                    if (declaration instanceof ClassTree type
                            && type.getSimpleName().contentEquals(typeName)) {
                        return type.getKind() == Tree.Kind.RECORD;
                    }
                }
            }
            return false;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect " + path, exception);
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
            boolean inFullGrammar = path.toString().contains("/fullgrammar/");
            if (inTokenEvent && text.contains(".fullgrammar.")) {
                return true;
            }
            return inFullGrammar && text.contains(".tokenevent.");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }

    private boolean isFactExtractionPath(Path path) {
        String value = path.toString();
        return value.contains("/core/src/main/java/com/relationdetector/core/relation/")
                || value.contains("/core/src/main/java/com/relationdetector/core/naming/")
                || value.contains("/core/src/main/java/com/relationdetector/core/lineage/")
                || value.contains("/core/src/main/java/com/relationdetector/core/derived/")
                || value.contains("/core/src/main/java/com/relationdetector/core/metadata/")
                || value.contains("/core/src/main/java/com/relationdetector/core/profile/")
                || value.contains("/core/src/main/java/com/relationdetector/core/fullgrammar/")
                || value.contains("/core/src/main/java/com/relationdetector/core/tokenevent/")
                || value.contains("/adaptor-mysql/src/main/java/com/relationdetector/mysql/fullgrammar/")
                || value.contains("/adaptor-mysql/src/main/java/com/relationdetector/mysql/tokenevent/")
                || value.contains("/adaptor-mysql/src/main/java/com/relationdetector/mysql/routine/")
                || value.contains("/adaptor-mysql/src/main/java/com/relationdetector/mysql/ddl/")
                || value.contains("/adaptor-postgres/src/main/java/com/relationdetector/postgres/fullgrammar/")
                || value.contains("/adaptor-postgres/src/main/java/com/relationdetector/postgres/tokenevent/")
                || value.contains("/adaptor-postgres/src/main/java/com/relationdetector/postgres/routine/")
                || value.contains("/adaptor-postgres/src/main/java/com/relationdetector/postgres/ddl/")
                || value.contains("/adaptor-oracle/src/main/java/com/relationdetector/oracle/fullgrammar/")
                || value.contains("/adaptor-oracle/src/main/java/com/relationdetector/oracle/tokenevent/")
                || value.contains("/adaptor-oracle/src/main/java/com/relationdetector/oracle/routine/")
                || value.contains("/adaptor-oracle/src/main/java/com/relationdetector/oracle/ddl/")
                || value.contains("/adaptor-sqlserver/src/main/java/com/relationdetector/sqlserver/fullgrammar/")
                || value.contains("/adaptor-sqlserver/src/main/java/com/relationdetector/sqlserver/tokenevent/")
                || value.contains("/adaptor-sqlserver/src/main/java/com/relationdetector/sqlserver/routine/")
                || value.contains("/adaptor-sqlserver/src/main/java/com/relationdetector/sqlserver/ddl/");
    }

    private static boolean isThinOracleVersionVisitor(Path path) {
        try {
            String text = Files.readString(path);
            long lines = text.lines()
                    .filter(line -> !line.isBlank())
                    .filter(line -> !line.stripLeading().startsWith("*"))
                    .filter(line -> !line.stripLeading().startsWith("//"))
                    .count();
            return lines <= 90 && text.contains("OracleFullGrammarParseTreeEventCollector");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }

    private static final class ProductionJavadocScanner extends TreePathScanner<Void, Void> {
        private static final Set<String> ORCHESTRATION_SUFFIXES = Set.of(
                "Engine", "Pipeline", "Service", "Collector", "Extractor", "Resolver", "Merger",
                "Framer", "Analyzer", "Visitor", "Writer");

        private final Path root;
        private final CompilationUnitTree unit;
        private final DocTrees docTrees;
        private final SourcePositions positions;
        private final List<String> offenders;
        private final List<String> enclosingTypes = new ArrayList<>();

        private ProductionJavadocScanner(Path root, CompilationUnitTree unit, DocTrees docTrees,
                SourcePositions positions, List<String> offenders) {
            this.root = root;
            this.unit = unit;
            this.docTrees = docTrees;
            this.positions = positions;
            this.offenders = offenders;
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            if (!node.getSimpleName().toString().isBlank()) {
                boolean topLevel = enclosingTypes.isEmpty();
                boolean publicBoundary = node.getModifiers().getFlags().contains(Modifier.PUBLIC)
                        || node.getModifiers().getFlags().contains(Modifier.PROTECTED);
                if (topLevel && publicBoundary && node.getKind() != com.sun.source.tree.Tree.Kind.RECORD) {
                    requireConcrete(node.getSimpleName().toString(), getCurrentPath());
                }
                enclosingTypes.add(node.getSimpleName().toString());
                try {
                    return super.visitClass(node, unused);
                } finally {
                    enclosingTypes.remove(enclosingTypes.size() - 1);
                }
            }
            return super.visitClass(node, unused);
        }

        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            boolean criticalHelper = isOrchestrationType() && bodyLines(node) >= 60;
            if (criticalHelper && !isOverride(node)) {
                TreePath path = new TreePath(getCurrentPath(), node);
                requireConcrete(node.getName().toString() + "()", path);
            }
            return super.visitMethod(node, unused);
        }

        private boolean isOrchestrationType() {
            if (enclosingTypes.isEmpty()) return false;
            String name = enclosingTypes.get(enclosingTypes.size() - 1);
            return ORCHESTRATION_SUFFIXES.stream().anyMatch(name::endsWith);
        }

        private boolean isOverride(MethodTree node) {
            return node.getModifiers().getAnnotations().stream()
                    .anyMatch(annotation -> annotation.getAnnotationType().toString().endsWith("Override"));
        }

        private long bodyLines(MethodTree node) {
            if (node.getBody() == null) return 0;
            long start = positions.getStartPosition(unit, node.getBody());
            long end = positions.getEndPosition(unit, node.getBody());
            if (start < 0 || end < 0) return 0;
            return unit.getLineMap().getLineNumber(end) - unit.getLineMap().getLineNumber(start) + 1;
        }

        private void requireBilingual(String symbol, TreePath path) {
            String doc = docText(path);
            if (!validSection(doc, "CN:") || !validSection(doc, "EN:")
                    || doc.contains("TODO") || doc.contains("TBD")) {
                long position = positions.getStartPosition(unit, path.getLeaf());
                long line = position < 0 ? 0 : unit.getLineMap().getLineNumber(position);
                Path file = Path.of(unit.getSourceFile().toUri());
                offenders.add(root.relativize(file) + ":" + line + "#" + symbol);
            }
        }

        private void requireConcrete(String symbol, TreePath path) {
            String doc = docText(path);
            String normalized = doc.replaceAll("[\\s*/<>{}@]", "");
            boolean forbidden = FORBIDDEN_JAVADOC_TEMPLATES.stream().anyMatch(doc::contains);
            if (normalized.length() < 24 || forbidden || doc.contains("TODO") || doc.contains("TBD")) {
                long position = positions.getStartPosition(unit, path.getLeaf());
                long line = position < 0 ? 0 : unit.getLineMap().getLineNumber(position);
                Path file = Path.of(unit.getSourceFile().toUri());
                offenders.add(root.relativize(file) + ":" + line + "#" + symbol);
            }
        }

        private boolean validSection(String doc, String marker) {
            int start = doc.indexOf(marker);
            if (start < 0) return false;
            int other = marker.equals("CN:") ? doc.indexOf("EN:", start + 3) : doc.length();
            String section = doc.substring(start + marker.length(), other < 0 ? doc.length() : other)
                    .replaceAll("[\\s*/<>{}@]", "");
            return section.length() >= 8;
        }

        private String docText(TreePath path) {
            var doc = docTrees.getDocCommentTree(path);
            return doc == null ? "" : doc.toString();
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
