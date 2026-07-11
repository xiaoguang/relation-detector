package com.relationdetector.core.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
                "com.relationdetector.oracle.tokenevent",
                "com.relationdetector.sqlserver.tokenevent");

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
    void fullGrammerStructuredParserWrapperIsNotNamedTokenEvent() throws IOException {
        Path root = repoRoot();
        Path fullGrammerRoot = root.resolve("core/src/main/java/com/relationdetector/core/fullgrammer");
        try (Stream<Path> stream = Files.walk(fullGrammerRoot)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.getFileName().toString().equals("FullGrammerTokenEventStructuredSqlParser.java")
                            || containsAny(path, List.of("FullGrammerTokenEventStructuredSqlParser",
                                    "FULL_GRAMMAR_TOKEN_EVENT_PRIMARY")))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "full-grammer profile parser wrapper must not mention token-event, offenders=" + offenders);
        }
    }

    @Test
    void fullGrammerSqlParserFactoryIsNotNamedTokenEvent() throws IOException {
        Path root = repoRoot();
        String legacyFactoryName = "FullGrammer" + "TokenEventParserFactory";
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
                    "full-grammer SQL parser factory must not use legacy token-event naming, offenders="
                            + offenders);
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
    void mysqlAndPostgresFullGrammerVersionVisitorsKeepSharedStateInCommon() throws IOException {
        Path root = repoRoot();
        List<Path> roots = List.of(
                root.resolve("adaptor-mysql/src/main/java/com/relationdetector/mysql/fullgrammer"),
                root.resolve("adaptor-postgres/src/main/java/com/relationdetector/postgres/fullgrammer"));
        for (Path fullGrammerRoot : roots) {
            try (Stream<Path> stream = Files.walk(fullGrammerRoot)) {
                List<Path> offenders = stream
                        .filter(path -> path.getFileName().toString().endsWith("FullGrammerParseTreeVisitor.java"))
                        .filter(path -> path.toString().contains("/fullgrammer/v"))
                        .filter(path -> containsAny(path, List.of(
                                "existsDepth",
                                "InsertSelectState",
                                "ArrayDeque<InsertSelect",
                                "record ColumnParts")))
                        .map(root::relativize)
                        .toList();

                assertTrue(offenders.isEmpty(),
                        "Version full-grammer visitors should keep reusable state/helpers in fullgrammer/common, offenders="
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
                Path.of("core/src/main/java/com/relationdetector/core/fullgrammer/SqlGrammarProfileRegistry.java"),
                Path.of("core/src/main/java/com/relationdetector/core/log/SqlLogNoiseFilter.java"),
                Path.of("adaptor-postgres/src/main/java/com/relationdetector/postgres/fullgrammer/PostgresFullGrammerVersionSyntaxGuard.java"),
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
                            "core/src/main/java/com/relationdetector/core/relation/NamingEvidenceExtractor.java")))
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Only NamingEvidenceExtractor may execute naming rules; relationships consume the evidence pool, offenders="
                            + offenders);
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
        assertFalse(combinedText.contains("new TokenEventRelationExtractor"),
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
    void databaseAdaptorV2ExposesOnlyGroupedCapabilities() throws IOException {
        Path root = repoRoot();
        String adaptor = Files.readString(root.resolve(
                "contracts/src/main/java/com/relationdetector/contracts/spi/DatabaseAdaptor.java"));

        assertTrue(adaptor.contains("AdaptorCollectors collectors()"));
        assertTrue(adaptor.contains("AdaptorParsers parsers()"));
        assertTrue(adaptor.contains("AdaptorProfiling profiling()"));
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
                    "SPI v2 must not restore legacy getter: " + legacyGetter);
        }

        String version = Files.readString(root.resolve(
                "contracts/src/main/java/com/relationdetector/contracts/spi/AdaptorApiVersion.java"));
        assertTrue(version.contains("CURRENT = 2"), "adaptor SPI must remain on v2");
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
    void fullGrammerTypedSinkDelegatesToFocusedHelpers() throws IOException {
        Path root = repoRoot();
        Path sink = root.resolve("core/src/main/java/com/relationdetector/core/fullgrammer/FullGrammerTypedSqlEventSink.java");
        String text = Files.readString(sink);

        for (String helper : List.of("RowsetScopeSink", "ProjectionEventSink",
                "PredicateEventSink", "DirectColumnTraceSupport", "SubqueryProjectionTraceSupport",
                "WriteMappingSink", "SourceLocationSupport")) {
            assertTrue(text.contains(helper),
                    "FullGrammerTypedSqlEventSink should delegate " + helper + " responsibilities");
        }
        assertTrue(Files.readAllLines(sink).size() <= 400,
                "FullGrammerTypedSqlEventSink must remain a thin facade");
        assertFalse(text.contains("new StructuredSqlEvent"),
                "StructuredSqlEvent creation belongs in FullGrammerEventRecorder");
        assertFalse(text.contains("eventKeys"),
                "Event de-duplication belongs in FullGrammerEventRecorder");
    }

    @Test
    void parserVisitorsAndCollectorsRemainFocused() throws IOException {
        Path root = repoRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            List<String> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().contains("/fullgrammer/")
                            || path.toString().contains("/tokenevent/")
                            || path.toString().contains("/routine/"))
                    .filter(path -> path.getFileName().toString().contains("Visitor")
                            || path.getFileName().toString().contains("Collector"))
                    .filter(path -> {
                        try {
                            return Files.readAllLines(path).size() > 400;
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .map(path -> root.relativize(path) + "=" + lineCount(path))
                    .toList();
            assertTrue(offenders.isEmpty(),
                    "Parser visitors/collectors must delegate focused responsibilities, offenders=" + offenders);
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
        assertTrue(Files.readAllLines(service).size() <= 140,
                "DerivedPathInferenceService must remain an orchestration facade");
    }

    @Test
    void evidenceMergersShareObservationAggregationEngine() throws IOException {
        Path root = repoRoot();
        for (String merger : List.of(
                "core/src/main/java/com/relationdetector/core/relation/RelationshipMerger.java",
                "core/src/main/java/com/relationdetector/core/lineage/DataLineageMerger.java",
                "core/src/main/java/com/relationdetector/core/relation/NamingEvidenceMerger.java")) {
            String text = Files.readString(root.resolve(merger));
            assertTrue(text.contains("EvidenceObservationAggregator"),
                    merger + " must use the common observation aggregation engine");
        }
    }

    @Test
    void coreFullGrammerDoesNotInferGrammarStructureFromContextClassNames() throws IOException {
        Path root = repoRoot();
        Path fullGrammer = root.resolve("core/src/main/java/com/relationdetector/core/fullgrammer");

        try (Stream<Path> stream = Files.walk(fullGrammer)) {
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
                    "Core full-grammer semantics must come from a dialect context adapter, offenders=" + offenders);
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
    void dialectFullGrammerDoesNotInferGrammarStructureFromContextClassNames() throws IOException {
        Path root = repoRoot().getParent();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().contains("/fullgrammer/"))
                    .filter(path -> {
                        String filename = path.getFileName().toString();
                        return filename.contains("ExpressionAnalyzer")
                                || filename.contains("EventVisitorCore")
                                || filename.contains("ParseTreeEventCollector")
                                || filename.equals("FullGrammerTypedSqlEventSink.java");
                    })
                    .filter(path -> containsAny(path, List.of(
                            "getClass().getSimpleName()",
                            "getClass().getName()")))
                    .map(root::relativize)
                    .toList();

            assertTrue(offenders.isEmpty(),
                    "Dialect full-grammer semantics must use typed context adapters, offenders=" + offenders);
        }
    }

    @Test
    void fullGrammerDialectsProvideSemanticContextAdapters() throws IOException {
        Path root = repoRoot();
        List<Path> dialectRoots = List.of(
                root.resolve("adaptor-mysql/src/main/java/com/relationdetector/mysql/fullgrammer"),
                root.resolve("adaptor-postgres/src/main/java/com/relationdetector/postgres/fullgrammer"),
                root.resolve("adaptor-sqlserver/src/main/java/com/relationdetector/sqlserver/fullgrammer"));

        for (Path dialectRoot : dialectRoots) {
            try (Stream<Path> stream = Files.walk(dialectRoot)) {
                assertTrue(stream
                                .filter(path -> path.toString().endsWith(".java"))
                                .anyMatch(path -> containsAny(path, List.of(
                                        "implements FullGrammerParseTreeAdapter",
                                        "extends AbstractFullGrammerParseTreeAdapter"))),
                        "Dialect full-grammer must provide an explicit semantic context adapter: "
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
        assertTrue(cliRunner.contains("ResolvedScanConfig resolved = config.resolve()"),
                "CLI overrides must be complete before immutable resolution");
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

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static long lineCount(Path path) {
        try {
            return Files.readAllLines(path).size();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to count lines in " + path, exception);
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
