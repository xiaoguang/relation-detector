package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.LogFormatHint;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.Enums.WarningSeverity;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.DataLineageEvidence;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.contracts.spi.Collectors.MetadataCollector;
import com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector;
import com.relationdetector.contracts.spi.Collectors.SqlLogExtractor;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.contracts.spi.ProfileOutcome;
import com.relationdetector.contracts.spi.ProfileStatus;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.core.output.JsonResultWriter;

class DataProfilePipelineTest {
    private final DataProfilePipeline pipeline = new DataProfilePipeline();

    @Test
    void namingDiscoveredCandidatesRequireStrongContainmentToCreateRelationship() {
        ScanPipelineContext weak = contextReturning(EvidenceType.VALUE_OVERLAP_HIGH);
        pipeline.profile(connection(), weak);
        assertTrue(weak.relationshipCandidates.isEmpty(),
                "name-only discovery must not create a relationship from weak overlap");

        ScanPipelineContext strong = contextReturning(EvidenceType.VALUE_CONTAINMENT_HIGH);
        pipeline.profile(connection(), strong);
        assertEquals(1, strong.relationshipCandidates.size());
    }

    @Test
    void forwardsProfilerFailuresToScanWarnings() {
        String secret = "jdbc:mysql://db?password=secret SQL=SELECT credential";
        ScanPipelineContext context = contextReturning(new ProfileOutcome(
                ProfileStatus.PERMISSION_DENIED,
                List.of(),
                List.of(new com.relationdetector.contracts.model.WarningMessage(
                        WarningType.PROFILE_WARNING,
                        WarningSeverity.ERROR,
                        "PROFILE_PERMISSION_DENIED",
                        secret,
                        secret,
                        0,
                        Map.of("sql", secret, "password", secret)))));

        pipeline.profile(connection(), context);

        assertEquals(1, context.result.warnings().size());
        var warning = context.result.warnings().get(0);
        assertEquals("PROFILE_PERMISSION_DENIED", warning.code());
        assertEquals(WarningSeverity.WARN, warning.severity());
        assertEquals("Data profiling query permission denied", warning.message());
        assertEquals("data-profile:test", warning.source());
        assertFalse(warning.toString().contains(secret));
        assertFalse(warning.attributes().containsKey("sql"));
        assertFalse(warning.attributes().containsKey("password"));
    }

    @Test
    void validatesEveryOutcomeBeforeApplyingAnyProfileResult() {
        RelationshipCandidate first = relationship(null, null);
        first.evidence().add(evidence(EvidenceType.SQL_LOG_JOIN));
        RelationshipCandidate second = relationshipTables("archive_orders", "archive_customers");
        second.evidence().add(evidence(EvidenceType.SQL_LOG_JOIN));
        AtomicInteger calls = new AtomicInteger();
        ScanPipelineContext context = contextReturningCandidates(List.of(first, second), (connection, request) -> {
            if (calls.incrementAndGet() == 1) {
                return ProfileOutcome.success(List.of(evidence(EvidenceType.VALUE_CONTAINMENT_HIGH)));
            }
            request.candidate().evidence().add(evidence(EvidenceType.VALUE_OVERLAP_HIGH));
            return ProfileOutcome.success(List.of(evidence(EvidenceType.SQL_LOG_JOIN)));
        });

        assertThrows(AdaptorContractException.class, () -> pipeline.profile(connection(), context));

        assertEquals(2, calls.get());
        assertEquals(List.of(EvidenceType.SQL_LOG_JOIN),
                first.evidence().stream().map(Evidence::type).toList());
        assertEquals(List.of(EvidenceType.SQL_LOG_JOIN),
                second.evidence().stream().map(Evidence::type).toList());
        assertTrue(context.result.warnings().isEmpty());
        assertTrue(context.result.sources().isEmpty());
    }

    @Test
    void rejectsEvidenceFromNonSuccessOutcomeBeforeMutatingScanState() {
        ScanPipelineContext context = contextReturning(new ProfileOutcome(
                ProfileStatus.QUERY_FAILED,
                List.of(evidence(EvidenceType.VALUE_CONTAINMENT_HIGH)),
                List.of(com.relationdetector.contracts.model.WarningMessage.warn(
                        WarningType.PROFILE_WARNING,
                        "PROFILE_QUERY_FAILED",
                        "safe warning",
                        "test-profile",
                        0))));

        assertThrows(AdaptorContractException.class, () -> pipeline.profile(connection(), context));

        assertTrue(context.relationshipCandidates.isEmpty());
        assertTrue(context.result.warnings().isEmpty());
    }

    @Test
    void rejectsNonProfileEvidenceAndNonProfileSourceTypes() {
        ScanPipelineContext structural = contextReturning(ProfileOutcome.success(List.of(
                evidence(EvidenceType.SQL_LOG_JOIN))));
        Evidence wrongSource = new Evidence(
                EvidenceType.VALUE_CONTAINMENT_HIGH,
                BigDecimal.valueOf(0.20d),
                EvidenceSourceType.PLAIN_SQL,
                "test-profile",
                "invalid source",
                Map.of());
        ScanPipelineContext sourceType = contextReturning(ProfileOutcome.success(List.of(wrongSource)));

        assertThrows(AdaptorContractException.class, () -> pipeline.profile(connection(), structural));
        assertThrows(AdaptorContractException.class, () -> pipeline.profile(connection(), sourceType));

        assertTrue(structural.relationshipCandidates.isEmpty());
        assertTrue(sourceType.relationshipCandidates.isEmpty());
    }

    @Test
    void rejectsNegativeEvidenceWhenGuardExistsOnlyOnStructuralEvidence() {
        RelationshipCandidate candidate = declaredCandidate(Map.of(
                "conditional", true,
                "conditions", List.of(Map.of(
                        "discriminator", "orders.party_type",
                        "operator", "EQUALS",
                        "value", "customer"))));
        ScanPipelineContext context = existingProfileContext(candidate,
                ProfileOutcome.success(List.of(negativeEvidence())));

        assertThrows(AdaptorContractException.class, () -> pipeline.profile(connection(), context));

        assertEquals(1, candidate.evidence().size());
    }

    @Test
    void acceptsNegativeEvidenceForUnconditionalDeclaredForeignKey() {
        RelationshipCandidate candidate = declaredCandidate(Map.of());
        ScanPipelineContext context = existingProfileContext(candidate,
                ProfileOutcome.success(List.of(negativeEvidence())));

        pipeline.profile(connection(), context);

        assertEquals(List.of(EvidenceType.DDL_FOREIGN_KEY, EvidenceType.NEGATIVE_VALUE_MISMATCH),
                candidate.evidence().stream().map(Evidence::type).toList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void profilerRequestIsDeeplyDetachedFromTheScanCandidate() {
        List<String> candidateNested = new ArrayList<>(List.of("candidate"));
        List<String> evidenceNested = new ArrayList<>(List.of("evidence"));
        List<String> warningNested = new ArrayList<>(List.of("warning"));
        RelationshipCandidate candidate = relationship(null, null);
        candidate.attributes().put("nested", candidateNested);
        candidate.evidence().add(new Evidence(
                EvidenceType.SQL_LOG_JOIN,
                BigDecimal.valueOf(0.20d),
                EvidenceSourceType.PLAIN_SQL,
                "query.sql",
                "join",
                Map.of("nested", evidenceNested)));
        candidate.warnings().add(new com.relationdetector.contracts.model.WarningMessage(
                WarningType.PARSE_WARNING,
                WarningSeverity.WARN,
                "TEST_WARNING",
                "warning",
                "query.sql",
                1,
                Map.of("nested", warningNested)));
        ScanPipelineContext context = contextReturningCandidates(List.of(candidate), (connection, request) -> {
            assertFalse(request.candidate() == candidate);
            request.candidate().attributes().put("pluginMutation", true);
            request.candidate().evidence().clear();
            request.candidate().warnings().clear();
            assertThrows(UnsupportedOperationException.class,
                    () -> ((List<String>) request.candidate().attributes().get("nested")).add("plugin"));
            return new ProfileOutcome(ProfileStatus.NO_EVIDENCE, List.of(), List.of());
        });

        pipeline.profile(connection(), context);

        assertFalse(candidate.attributes().containsKey("pluginMutation"));
        assertEquals(List.of("candidate"), candidateNested);
        assertEquals(List.of("evidence"), evidenceNested);
        assertEquals(List.of("warning"), warningNested);
        assertEquals(1, candidate.evidence().size());
        assertEquals(1, candidate.warnings().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void profilerResultEvidenceIsDeeplyDetachedBeforeItEntersTheScan() {
        List<String> pluginNested = new ArrayList<>(List.of("validated"));
        Evidence pluginEvidence = new Evidence(
                EvidenceType.VALUE_CONTAINMENT_HIGH,
                BigDecimal.valueOf(0.20d),
                EvidenceSourceType.DATA_PROFILE,
                "test-profile",
                "containment",
                Map.of("nested", pluginNested));
        ScanPipelineContext context = contextReturning(ProfileOutcome.success(List.of(pluginEvidence)));

        pipeline.profile(connection(), context);
        pluginNested.add("late-plugin-mutation");

        Evidence applied = context.relationshipCandidates.get(0).evidence().stream()
                .filter(item -> item.type() == EvidenceType.VALUE_CONTAINMENT_HIGH)
                .findFirst()
                .orElseThrow();
        List<String> appliedNested = (List<String>) applied.attributes().get("nested");
        assertEquals(List.of("validated"), appliedNested);
        assertThrows(UnsupportedOperationException.class, () -> appliedNested.add("scan-mutation"));
    }

    @Test
    void rejectsUnsupportedMutableProfileAttributesWithoutPartialState() {
        Evidence pluginEvidence = new Evidence(
                EvidenceType.VALUE_CONTAINMENT_HIGH,
                BigDecimal.valueOf(0.20d),
                EvidenceSourceType.DATA_PROFILE,
                "test-profile",
                "containment",
                Map.of("mutable", new StringBuilder("plugin-owned")));
        ScanPipelineContext context = contextReturning(ProfileOutcome.success(List.of(pluginEvidence)));

        assertThrows(AdaptorContractException.class, () -> pipeline.profile(connection(), context));

        assertTrue(context.relationshipCandidates.isEmpty());
        assertTrue(context.result.warnings().isEmpty());
        assertTrue(context.result.sources().isEmpty());
    }

    @Test
    void doesNotProfilePostgresCandidateFromAnotherCatalog() {
        AtomicInteger calls = new AtomicInteger();
        ScanPipelineContext context = existingCandidateContext(
                DatabaseType.POSTGRESQL,
                new ScanScope("connected_db", "sales", List.of(), List.of()),
                relationship("other_db", "sales"),
                calls);

        pipeline.profile(connection(), context);

        assertEquals(0, calls.get(),
                "PostgreSQL cannot execute a candidate whose explicit catalog differs from the connected database");
    }

    @Test
    void doesNotProfileOracleCandidateWithCatalog() {
        AtomicInteger calls = new AtomicInteger();
        ScanPipelineContext context = existingCandidateContext(
                DatabaseType.ORACLE,
                new ScanScope(null, "APP", List.of(), List.of()),
                relationship("unsupported_catalog", "APP"),
                calls);

        pipeline.profile(connection(), context);

        assertEquals(0, calls.get(),
                "Oracle live profiling cannot execute catalog-qualified endpoints");
    }

    @Test
    void noOpAdjustmentPreservesCanonicalFinalProfileOutputAndLineage() throws Exception {
        ScanPipelineContext baseline = contextReturning(EvidenceType.VALUE_CONTAINMENT_HIGH);
        ScanPipelineContext adjusted = contextReturning(EvidenceType.VALUE_CONTAINMENT_HIGH);
        DataLineageEvidence lineage = lineageEvidence();
        baseline.lineageCandidates.add(lineageCandidate(lineage));
        adjusted.lineageCandidates.add(lineageCandidate(lineage));
        try {
            ScanResult before = assembleProfiledResult(baseline, false);
            ScanResult after = assembleProfiledResult(adjusted, true);

            assertArrayEquals(canonicalJson(before), canonicalJson(after));
            assertEquals(List.of(lineage), after.dataLineages().get(0).rawEvidence());
            assertEquals(BigDecimal.valueOf(0.7d), after.dataLineages().get(0).evidence().get(0).score());
            assertEquals("lineage", after.dataLineages().get(0).evidence().get(0).detail());
        } finally {
            baseline.close();
            adjusted.close();
        }
    }

    @Test
    void profileGeneratedEvidenceIsAdjustedExactlyOnceBeforeFinalAssembly() {
        AtomicInteger calls = new AtomicInteger();
        ScanPipelineContext context = contextReturning(
                ProfileOutcome.success(List.of(evidence(EvidenceType.VALUE_CONTAINMENT_HIGH))),
                (item, ignored) -> {
                    calls.incrementAndGet();
                    return new Evidence(item.type(), item.score().add(BigDecimal.valueOf(0.1d)),
                            item.sourceType(), item.source(), item.detail(), item.attributes());
                });
        try {
            List<RelationshipCandidate> profiled = pipeline.profile(connection(), context);
            new EvidenceEnhancementPipeline().enhanceProfiledCandidates(context, profiled);
            int expectedCalls = context.relationshipCandidates.stream()
                    .mapToInt(candidate -> candidate.rawEvidence().isEmpty()
                            ? candidate.evidence().size() : candidate.rawEvidence().size())
                    .sum()
                    + context.namingEvidencePool.merged().stream()
                            .mapToInt(candidate -> candidate.rawEvidence().size())
                            .sum();

            new EvidenceEnhancementPipeline().adjustWeights(context);
            ScanResult result = new ResultAssembler().assemble(context);

            assertEquals(expectedCalls, calls.get());
            assertEquals(List.of(BigDecimal.valueOf(0.3d)), result.relationships().get(0).rawEvidence().stream()
                    .filter(item -> item.type() == EvidenceType.VALUE_CONTAINMENT_HIGH)
                    .map(Evidence::score)
                    .toList());
        } finally {
            context.close();
        }
    }

    private ScanResult assembleProfiledResult(ScanPipelineContext context, boolean adjustWeights) {
        List<RelationshipCandidate> profiled = pipeline.profile(connection(), context);
        new EvidenceEnhancementPipeline().enhanceProfiledCandidates(context, profiled);
        if (adjustWeights) {
            new EvidenceEnhancementPipeline().adjustWeights(context);
        }
        return new ResultAssembler().assemble(context);
    }

    private byte[] canonicalJson(ScanResult result) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var document = mapper.readTree(new JsonResultWriter().write(result, true, true, true));
        ((com.fasterxml.jackson.databind.node.ObjectNode) document).remove("generatedAt");
        return mapper.writeValueAsBytes(document);
    }

    private DataLineageCandidate lineageCandidate(DataLineageEvidence evidence) {
        DataLineageCandidate candidate = new DataLineageCandidate(
                List.of(endpoint("orders", "customer_id")), endpoint("audit", "customer_id"),
                LineageFlowKind.VALUE, LineageTransformType.DIRECT);
        candidate.evidence().add(evidence);
        return candidate;
    }

    private DataLineageEvidence lineageEvidence() {
        return new DataLineageEvidence(LineageTransformType.DIRECT, BigDecimal.valueOf(0.7d),
                EvidenceSourceType.PLAIN_SQL, "lineage.sql", "lineage", Map.of());
    }

    private ScanPipelineContext contextReturning(EvidenceType type) {
        return contextReturning(ProfileOutcome.success(List.of(evidence(type))));
    }

    private ScanPipelineContext contextReturning(ProfileOutcome profileOutcome) {
        return contextReturning(profileOutcome, (evidence, ignored) -> evidence);
    }

    private ScanPipelineContext contextReturning(ProfileOutcome profileOutcome, EvidenceWeightAdjuster adjuster) {
        ScanConfig config = new ScanConfig();
        config.databaseType = com.relationdetector.contracts.Enums.DatabaseType.MYSQL;
        config.jdbcUrl = "jdbc:test:data-profile-pipeline";
        config.dataProfileEnabled = true;
        config.discoverFromNamingEvidence = true;
        config.skipUnindexedLargeTargets = true;

        ScanScope scope = new ScanScope(null, null, List.of(), List.of());
        ScanResult result = new ScanResult("mysql", "test");
        ScanPipelineContext ctx = new ScanPipelineContext(
                config.resolve(),
                new TestAdaptor(DatabaseType.MYSQL, (connection, request) -> profileOutcome, adjuster),
                scope,
                result,
                new AdaptorContext(scope, Map.of(), result.warnings()::add),
                new ArrayList<>(),
                new ArrayList<>());
        ctx.metadataSnapshot = metadata();
        ctx.namingEvidencePool.add(new NamingEvidenceCandidate(
                endpoint("orders", "customer_id"),
                endpoint("customers", "id"),
                evidence(EvidenceType.NAMING_MATCH),
                "TABLE_ID",
                true));
        return ctx;
    }

    private ScanPipelineContext existingCandidateContext(DatabaseType databaseType, ScanScope scope,
            RelationshipCandidate candidate, AtomicInteger calls) {
        ScanConfig config = new ScanConfig();
        config.databaseType = databaseType;
        config.jdbcUrl = "jdbc:test:data-profile-pipeline";
        config.dataProfileEnabled = true;
        config.skipUnindexedLargeTargets = false;
        candidate.evidence().add(evidence(EvidenceType.SQL_LOG_JOIN));
        ScanResult result = new ScanResult(databaseType.name(), scope.catalog(), scope.schema());
        ScanPipelineContext ctx = new ScanPipelineContext(
                config.resolve(),
                new TestAdaptor(databaseType, (connection, request) -> {
                    calls.incrementAndGet();
                    return new ProfileOutcome(ProfileStatus.NO_EVIDENCE, List.of(), List.of());
                }, (evidence, ignored) -> evidence),
                scope,
                result,
                new AdaptorContext(scope, Map.of(), result.warnings()::add),
                new ArrayList<>(List.of(candidate)),
                new ArrayList<>());
        ctx.metadataSnapshot = new MetadataSnapshot();
        return ctx;
    }

    private ScanPipelineContext existingProfileContext(RelationshipCandidate candidate, ProfileOutcome outcome) {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;
        config.jdbcUrl = "jdbc:test:data-profile-pipeline";
        config.dataProfileEnabled = true;
        config.verifyDeclaredForeignKeys = true;
        config.skipUnindexedLargeTargets = false;
        ScanScope scope = new ScanScope(null, null, List.of(), List.of());
        ScanResult result = new ScanResult("mysql", null, null);
        ScanPipelineContext ctx = new ScanPipelineContext(
                config.resolve(),
                new TestAdaptor(DatabaseType.MYSQL, (connection, request) -> outcome,
                        (evidence, ignored) -> evidence),
                scope,
                result,
                new AdaptorContext(scope, Map.of(), result.warnings()::add),
                new ArrayList<>(List.of(candidate)),
                new ArrayList<>());
        ctx.metadataSnapshot = metadata();
        return ctx;
    }

    private ScanPipelineContext contextReturningCandidates(
            List<RelationshipCandidate> candidates,
            DataProfiler profiler
    ) {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;
        config.jdbcUrl = "jdbc:test:data-profile-pipeline";
        config.dataProfileEnabled = true;
        config.skipUnindexedLargeTargets = false;
        ScanScope scope = new ScanScope(null, null, List.of(), List.of());
        ScanResult result = new ScanResult("mysql", null, null);
        ScanPipelineContext ctx = new ScanPipelineContext(
                config.resolve(),
                new TestAdaptor(DatabaseType.MYSQL, profiler, (evidence, ignored) -> evidence),
                scope,
                result,
                new AdaptorContext(scope, Map.of(), result.warnings()::add),
                new ArrayList<>(candidates),
                new ArrayList<>());
        ctx.metadataSnapshot = new MetadataSnapshot();
        return ctx;
    }

    private RelationshipCandidate declaredCandidate(Map<String, Object> attributes) {
        RelationshipCandidate candidate = relationship(null, null);
        candidate.evidence().add(new Evidence(
                EvidenceType.DDL_FOREIGN_KEY,
                BigDecimal.valueOf(0.98d),
                EvidenceSourceType.DDL_FILE,
                "schema.sql",
                "declared foreign key",
                attributes));
        return candidate;
    }

    private Evidence negativeEvidence() {
        return new Evidence(
                EvidenceType.NEGATIVE_VALUE_MISMATCH,
                BigDecimal.valueOf(-0.20d),
                EvidenceSourceType.DATA_PROFILE,
                "test-profile",
                "negative mismatch",
                Map.of(
                        "profileMode", "LIVE_DATABASE",
                        "negativePolicy", "DECLARED_FOREIGN_KEY_ONLY"));
    }

    private RelationshipCandidate relationship(String catalog, String schema) {
        TableId source = new TableId(catalog, schema, "orders", "orders");
        TableId target = new TableId(catalog, schema, "customers", "customers");
        return new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(source, "customer_id")),
                Endpoint.column(ColumnRef.of(target, "id")),
                com.relationdetector.contracts.Enums.RelationType.FK_LIKE,
                com.relationdetector.contracts.Enums.RelationSubType.INFERRED_JOIN_FK);
    }

    private RelationshipCandidate relationshipTables(String sourceTable, String targetTable) {
        TableId source = TableId.of(null, sourceTable);
        TableId target = TableId.of(null, targetTable);
        return new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(source, "customer_id")),
                Endpoint.column(ColumnRef.of(target, "id")),
                com.relationdetector.contracts.Enums.RelationType.FK_LIKE,
                com.relationdetector.contracts.Enums.RelationSubType.INFERRED_JOIN_FK);
    }

    private MetadataSnapshot metadata() {
        MetadataSnapshot metadata = new MetadataSnapshot();
        metadata.columnFacts().add(new MetadataColumnFact(null, null, "orders", "customer_id",
                "bigint", "bigint", true, null, "", null, 1));
        metadata.columnFacts().add(new MetadataColumnFact(null, null, "customers", "id",
                "bigint", "bigint", true, null, "", null, 1));
        metadata.indexFacts().add(new MetadataIndexFact(null, null, "customers", "PRIMARY", true, true,
                "BTREE", true, List.of("id"), List.of(), List.of(), List.of(1)));
        return metadata;
    }

    private Endpoint endpoint(String table, String column) {
        return Endpoint.column(ColumnRef.of(TableId.of(null, table), column));
    }

    private Evidence evidence(EvidenceType type) {
        return new Evidence(type, BigDecimal.valueOf(0.20d), EvidenceSourceType.DATA_PROFILE,
                "test-profile", type.name(), Map.of());
    }

    private Connection connection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private record TestAdaptor(DatabaseType databaseType, DataProfiler profiler, EvidenceWeightAdjuster adjuster)
            implements DatabaseAdaptor {
        @Override public int spiVersion() { return com.relationdetector.contracts.spi.AdaptorApiVersion.CURRENT; }
        @Override
        public String id() {
            return "test";
        }

        @Override
        public String displayName() {
            return "test";
        }

        @Override
        public Set<DatabaseType> supportedDatabaseTypes() {
            return Set.of(databaseType);
        }

        @Override
        public Set<AdaptorCapability> capabilities() {
            return Set.of();
        }

        @Override
        public IdentifierRules identifierRules() {
            return identifier -> identifier == null ? null : identifier.toLowerCase(java.util.Locale.ROOT);
        }

        @Override
        public com.relationdetector.contracts.spi.AdaptorCollectors collectors() {
            return new com.relationdetector.contracts.spi.AdaptorCollectors(
                    Optional.of((connection, scope) -> new MetadataSnapshot()),
                    Optional.of((connection, scope) -> List.of()),
                    Optional.empty(),
                    Optional.of((file, hint) -> Stream.<SqlStatementRecord>empty()));
        }

        @Override
        public com.relationdetector.contracts.spi.AdaptorParsers parsers() {
            return new com.relationdetector.contracts.spi.AdaptorParsers(
                    (statement, context) -> List.<RelationshipCandidate>of(),
                    Optional.empty(),
                    Optional.empty(),
                    request -> com.relationdetector.contracts.parse.ScriptFrameResult.empty());
        }

        @Override
        public com.relationdetector.contracts.spi.AdaptorProfiling profiling() {
            return new com.relationdetector.contracts.spi.AdaptorProfiling(
                    Optional.of(profiler),
                    adjuster);
        }
    }
}
