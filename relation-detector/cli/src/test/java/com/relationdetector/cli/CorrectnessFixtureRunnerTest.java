package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.parser.DdlRelationParserRunner;
import com.relationdetector.core.relation.RelationshipMerger;
import com.relationdetector.core.tokenevent.TokenEventStructuredDdlParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Unified file-based correctness suite for parser and relationship behavior.
 */
class CorrectnessFixtureRunnerTest {
    private final CorrectnessFixtureExecutor executor = new CorrectnessFixtureExecutor();
    private final ConcurrentLinkedQueue<FixtureTiming> fixtureTimings = new ConcurrentLinkedQueue<>();

    @Test
    void allCorrectnessFixturesPassGoldenExpectations() throws Exception {
        Path root = workspaceRoot().resolve("test-fixtures/correctness");
        String fixtureFilter = System.getProperty("correctnessFixtureFilter", "");
        String fixtureProfile = System.getProperty(
                "correctnessFixtureProfile",
                fixtureFilter.isBlank() ? "smoke" : "full");
        List<Path> discovered;
        List<Path> manifests;
        try (Stream<Path> paths = Files.walk(root)) {
            discovered = paths
                    .filter(path -> path.getFileName().toString().equals("manifest.yml"))
                    .sorted()
                    .toList();
            manifests = discovered.stream()
                    .filter(path -> CorrectnessFixtureProfileSelector.matches(root, path, fixtureProfile))
                    .filter(path -> matchesFixtureFilter(path, fixtureFilter))
                    .toList();
        }

        assertFalse(manifests.isEmpty(), "Expected at least one correctness manifest under " + root);
        if (isUnfilteredFullProfile(fixtureProfile, fixtureFilter)) {
            assertEquals(discovered.size(), manifests.size(),
                    "full correctness profile must select every discovered fixture");
        }
        List<FixtureExecution> executions = runFixtures(manifests);
        CorrectnessRunSummaryWriter.write(
                workspaceRoot().resolve("target/correctness-run-summary.json"),
                root,
                fixtureProfile,
                discovered.size(),
                manifests.size(),
                executions);
        assertEquals(manifests.size(), executions.size(),
                "every selected correctness fixture must execute exactly once");
        printFixtureTimingSummary();
        List<FixtureExecution> failures = executions.stream().filter(execution -> !execution.passed()).toList();
        assertAll("correctness fixtures",
                failures.stream()
                        .map(execution -> (Executable) () -> {
                            throw new AssertionError(execution.manifest() + " failed", execution.error());
                        })
                        .toList());
    }

    @Test
    void correctnessFixtureProfilesSelectDialectFamilies() {
        Path root = Path.of("test-fixtures/correctness");

        assertTrue(CorrectnessFixtureProfileSelector.matches(root,
                root.resolve("oracle/v26ai/oracle26ai-example/manifest.yml"),
                "oracle"));
        assertTrue(CorrectnessFixtureProfileSelector.matches(root,
                root.resolve("postgres/v18/postgres18-example/manifest.yml"),
                "postgres/v18"));
        assertTrue(CorrectnessFixtureProfileSelector.matches(root,
                root.resolve("mysql/v8_0/mysql80-example/manifest.yml"),
                "mysql,mysql/v8_0"));
        assertTrue(CorrectnessFixtureProfileSelector.matches(root,
                root.resolve("mysql/v5_7/mysql57-example/manifest.yml"),
                "mysql57,mysql/v5_7"));
        assertTrue(CorrectnessFixtureProfileSelector.matches(root,
                root.resolve("common/sql-basic-join/manifest.yml"),
                "smoke"));
        assertFalse(CorrectnessFixtureProfileSelector.matches(root,
                root.resolve("oracle/v12c/oracle12c-example/manifest.yml"),
                "postgres"));
        assertFalse(CorrectnessFixtureProfileSelector.matches(root,
                root.resolve("postgres/postgres-large-example/manifest.yml"),
                "smoke"));
    }

    @Test
    void correctnessFixtureFilterAcceptsMultipleCommaSeparatedFragments() {
        Path manifest = Path.of("test-fixtures/correctness/mysql/v8_0/mysql80-example/manifest.yml");

        assertTrue(matchesFixtureFilter(manifest, "postgres,v8_0"));
        assertTrue(matchesFixtureFilter(manifest, "v5_7,mysql80-example"));
        assertFalse(matchesFixtureFilter(manifest, "postgres,v5_7"));
    }

    @Test
    void objectBlockStatementFormatKeepsRoutineBodyTogether() {
        String text = """
                -- relation-detector-fixture-source: ROUTINE:case_01.rebuild_order_rollups
                BEGIN
                  SELECT *
                  FROM orders o
                  JOIN users u ON o.user_id = u.id;
                  SELECT *
                  FROM order_items oi
                  JOIN products p ON oi.product_id = p.id;
                END
                -- relation-detector-fixture-end
                """;

        List<SqlStatementRecord> statements = ObjectBlockStatementSplitter.parse(
                text,
                StatementSourceType.PROCEDURE,
                "routine-fixture.sql",
                DatabaseType.MYSQL);

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).sql().contains("JOIN users u ON o.user_id = u.id;"));
        assertTrue(statements.get(0).sql().contains("JOIN products p ON oi.product_id = p.id;"));
        assertEquals("ROUTINE:case_01.rebuild_order_rollups", statements.get(0).sourceName());
    }

    @Test
    void objectBlockStatementFormatCanFilterOneRoutineSource() {
        String text = """
                -- relation-detector-fixture-source: ROUTINE:case_01.skip_me
                BEGIN
                  SELECT * FROM ignored_orders;
                END
                -- relation-detector-fixture-end
                -- relation-detector-fixture-source: ROUTINE:case_01.keep_me
                BEGIN
                  UPDATE orders o
                  JOIN users u ON o.user_id = u.id
                  SET o.audit_user_id = u.id;
                END
                -- relation-detector-fixture-end
                """;

        List<SqlStatementRecord> statements = ObjectBlockStatementSplitter.parse(
                text,
                StatementSourceType.PROCEDURE,
                "routine-fixture.sql",
                DatabaseType.MYSQL,
                "ROUTINE:case_01.keep_me");

        assertEquals(1, statements.size());
        assertEquals("ROUTINE:case_01.keep_me", statements.get(0).sourceName());
        assertTrue(statements.get(0).sql().contains("SET o.audit_user_id = u.id"));
        assertFalse(statements.get(0).sql().contains("ignored_orders"));
    }

    @Test
    void objectBlockStatementFormatRequiresEndMarker() {
        String text = """
                -- relation-detector-fixture-source: ROUTINE:case_01.rebuild_order_rollups
                BEGIN
                  SELECT * FROM orders o JOIN users u ON o.user_id = u.id;
                END
                """;

        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> ObjectBlockStatementSplitter.parse(text, StatementSourceType.PROCEDURE,
                        "routine-fixture.sql", DatabaseType.MYSQL));

        assertTrue(error.getMessage().contains("Missing relation-detector-fixture-end"));
    }

    @Test
    void objectBlockStatementFormatSplitsUnmarkedOracleSlashTerminatedObjects() {
        String text = """
                CREATE OR REPLACE PROCEDURE sp_one
                AS
                BEGIN
                  SELECT * FROM customers c;
                END;
                /

                CREATE OR REPLACE PROCEDURE sp_two
                AS
                BEGIN
                  SELECT * FROM contracts c;
                END;
                /
                """;

        List<SqlStatementRecord> statements = ObjectBlockStatementSplitter.parse(
                text,
                StatementSourceType.PROCEDURE,
                "oracle-routine-fixture.sql",
                DatabaseType.ORACLE);

        assertEquals(2, statements.size());
        assertEquals("oracle-routine-fixture.sql#sp_one", statements.get(0).sourceName());
        assertEquals("oracle-routine-fixture.sql#sp_two", statements.get(1).sourceName());
        assertTrue(statements.get(0).sql().contains("FROM customers c"));
        assertTrue(statements.get(1).sql().contains("FROM contracts c"));
    }

    @Test
    void commonDdlFixtureUsesCommonParserInsteadOfDialectAdaptorParser() {
        String input = """
                CREATE TABLE "customers" (
                    "id" INT PRIMARY KEY
                );
                CREATE TABLE "orders" (
                    "id" INT PRIMARY KEY,
                    "customer_id" INT,
                    FOREIGN KEY ("customer_id") REFERENCES "customers" ("id")
                );
                """;

        List<RelationshipCandidate> relationships = new DdlRelationParserRunner().parseText(
                new TokenEventStructuredDdlParser(SqlDialect.GENERIC),
                input,
                "in-memory-common-ddl-fixture.ddl.sql",
                EvidenceSourceType.DDL_FILE,
                new AdaptorContext(new ScanScope(null, "portable", List.of(), List.of()), Map.of(), warning -> {
                }));
        Set<String> fingerprints = new RelationshipMerger().merge(relationships, 0.0).stream()
                .map(this::fingerprint)
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(Set.of("FK_LIKE:orders.customer_id->customers.id:DDL_FOREIGN_KEY,TARGET_UNIQUE"),
                fingerprints);
    }

    private List<FixtureExecution> runFixtures(List<Path> manifests) {
        int parallelism = correctnessFixtureParallelism();
        List<Path> executionOrder = manifests.stream()
                .sorted(Comparator.comparingLong(this::fixtureWeight).reversed()
                        .thenComparing(Path::toString))
                .toList();
        if (parallelism <= 1) {
            return executionOrder.stream()
                    .map(this::runFixtureSafely)
                    .sorted(Comparator.comparing(execution -> execution.manifest().toString()))
                    .toList();
        }
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<FixtureExecution>> futures = executionOrder.stream()
                    .map(manifest -> pool.submit(() -> runFixtureSafely(manifest)))
                    .toList();
            return futures.stream()
                    .map(this::getFixtureExecution)
                    .sorted(Comparator.comparing(execution -> execution.manifest().toString()))
                    .toList();
        } finally {
            pool.shutdown();
        }
    }

    private FixtureExecution getFixtureExecution(Future<FixtureExecution> future) {
        try {
            return future.get();
        } catch (Exception error) {
            throw new IllegalStateException("Correctness fixture worker failed unexpectedly", error);
        }
    }

    private long fixtureWeight(Path manifest) {
        try {
            return Files.size(manifest.getParent().resolve("input.sql"));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private FixtureExecution runFixtureSafely(Path manifest) {
        long startNanos = System.nanoTime();
        Throwable failure = null;
        try {
            executor.runFixture(CorrectnessFixture.read(manifest));
        } catch (Throwable error) {
            failure = error;
        } finally {
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            if (Boolean.getBoolean("correctnessFixtureTiming")) {
                fixtureTimings.add(new FixtureTiming(manifest, elapsedMillis));
                if (Boolean.getBoolean("correctnessFixtureTimingVerbose")) {
                    System.out.printf("correctness fixture %s %d ms%n", manifest, elapsedMillis);
                }
            }
            return new FixtureExecution(manifest, failure, elapsedMillis);
        }
    }

    private void printFixtureTimingSummary() {
        if (!Boolean.getBoolean("correctnessFixtureTiming") || fixtureTimings.isEmpty()) {
            return;
        }
        int limit = Integer.getInteger("correctnessFixtureTimingTop", 20);
        System.out.printf("slowest correctness fixtures top %d%n", limit);
        fixtureTimings.stream()
                .sorted(Comparator.comparingLong(FixtureTiming::elapsedMillis).reversed()
                        .thenComparing(timing -> timing.manifest().toString()))
                .limit(limit)
                .forEach(timing -> System.out.printf("slow correctness fixture %s %d ms%n",
                        timing.manifest(), timing.elapsedMillis()));
    }

    private static int correctnessFixtureParallelism() {
        if (Boolean.getBoolean("updateCorrectnessGold")) {
            return 1;
        }
        String configured = System.getProperty("correctnessFixtureParallelism", "").trim();
        if (!configured.isBlank()) {
            return Math.max(1, Integer.parseInt(configured));
        }
        return Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));
    }

    private static boolean isUnfilteredFullProfile(String profile, String fixtureFilter) {
        return fixtureFilter.isBlank()
                && ("full".equalsIgnoreCase(profile) || "all".equalsIgnoreCase(profile));
    }

    private static boolean matchesFixtureFilter(Path manifest, String fixtureFilter) {
        if (fixtureFilter == null || fixtureFilter.isBlank()) {
            return true;
        }
        String value = manifest.toString();
        return Stream.of(fixtureFilter.split(","))
                .map(String::trim)
                .filter(filter -> !filter.isEmpty())
                .anyMatch(value::contains);
    }

    private String fingerprint(RelationshipCandidate relation) {
        String evidenceTypes = relation.evidence().stream()
                .map(evidence -> evidence.type().name())
                .collect(Collectors.joining(","));
        return relation.relationType() + ":"
                + relation.source().displayName() + "->" + relation.target().displayName()
                + ":" + evidenceTypes;
    }

    private static Path workspaceRoot() {
        return TestWorkspacePaths.relationDetectorRoot();
    }

    record FixtureExecution(Path manifest, Throwable error, long elapsedMillis) {
        boolean passed() {
            return error == null;
        }
    }

    private record FixtureTiming(Path manifest, long elapsedMillis) {
    }
}
