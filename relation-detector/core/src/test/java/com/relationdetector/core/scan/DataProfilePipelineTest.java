package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.relationdetector.contracts.Enums.LogFormatHint;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
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
        ScanPipelineContext context = contextReturning(new ProfileOutcome(
                ProfileStatus.PERMISSION_DENIED,
                List.of(),
                List.of(com.relationdetector.contracts.model.WarningMessage.warn(
                        WarningType.PROFILE_WARNING,
                        "PROFILE_PERMISSION_DENIED",
                        "denied",
                        "test-profile",
                        0))));

        pipeline.profile(connection(), context);

        assertEquals(1, context.result.warnings().size());
        assertEquals("PROFILE_PERMISSION_DENIED", context.result.warnings().get(0).code());
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

    private ScanPipelineContext contextReturning(EvidenceType type) {
        return contextReturning(ProfileOutcome.success(List.of(evidence(type))));
    }

    private ScanPipelineContext contextReturning(ProfileOutcome profileOutcome) {
        ScanConfig config = new ScanConfig();
        config.databaseType = com.relationdetector.contracts.Enums.DatabaseType.MYSQL;
        config.dataProfileEnabled = true;
        config.discoverFromNamingEvidence = true;
        config.skipUnindexedLargeTargets = true;

        ScanScope scope = new ScanScope(null, null, List.of(), List.of());
        ScanResult result = new ScanResult("mysql", "test");
        ScanPipelineContext ctx = new ScanPipelineContext(
                config.resolve(),
                new TestAdaptor(DatabaseType.MYSQL, (connection, request) -> profileOutcome),
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
        config.dataProfileEnabled = true;
        config.skipUnindexedLargeTargets = false;
        candidate.evidence().add(evidence(EvidenceType.SQL_LOG_JOIN));
        ScanResult result = new ScanResult(databaseType.name(), scope.catalog(), scope.schema());
        ScanPipelineContext ctx = new ScanPipelineContext(
                config.resolve(),
                new TestAdaptor(databaseType, (connection, request) -> {
                    calls.incrementAndGet();
                    return new ProfileOutcome(ProfileStatus.NO_EVIDENCE, List.of(), List.of());
                }),
                scope,
                result,
                new AdaptorContext(scope, Map.of(), result.warnings()::add),
                new ArrayList<>(List.of(candidate)),
                new ArrayList<>());
        ctx.metadataSnapshot = new MetadataSnapshot();
        return ctx;
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

    private record TestAdaptor(DatabaseType databaseType, DataProfiler profiler) implements DatabaseAdaptor {
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
                    (evidence, context) -> evidence);
        }
    }
}
