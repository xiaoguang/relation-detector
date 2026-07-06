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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.LogFormatHint;
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

    private ScanPipelineContext contextReturning(EvidenceType type) {
        ScanConfig config = new ScanConfig();
        config.dataProfileEnabled = true;
        config.discoverFromNamingEvidence = true;
        config.skipUnindexedLargeTargets = true;

        ScanScope scope = new ScanScope(null, null, List.of(), List.of());
        ScanResult result = new ScanResult("mysql", "test");
        ScanPipelineContext ctx = new ScanPipelineContext(
                config,
                new TestAdaptor((connection, request) -> List.of(evidence(type))),
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

    private MetadataSnapshot metadata() {
        MetadataSnapshot metadata = new MetadataSnapshot();
        metadata.columnFacts().add(new MetadataColumnFact(null, "orders", "customer_id",
                "bigint", "bigint", true, null, "", null, 1));
        metadata.columnFacts().add(new MetadataColumnFact(null, "customers", "id",
                "bigint", "bigint", true, null, "", null, 1));
        metadata.indexFacts().add(new MetadataIndexFact(null, "customers", "PRIMARY", true, true,
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

    private record TestAdaptor(DataProfiler profiler) implements DatabaseAdaptor {
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
            return Set.of(DatabaseType.MYSQL);
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
        public MetadataCollector metadataCollector() {
            return (connection, scope) -> new MetadataSnapshot();
        }

        @Override
        public ObjectDefinitionCollector objectDefinitionCollector() {
            return (connection, scope) -> List.of();
        }

        @Override
        public SqlLogExtractor sqlLogExtractor() {
            return (file, hint) -> Stream.<SqlStatementRecord>empty();
        }

        @Override
        public SqlRelationParser sqlRelationParser() {
            return (statement, context) -> List.<RelationshipCandidate>of();
        }

        @Override
        public Optional<DataProfiler> dataProfiler() {
            return Optional.of(profiler);
        }

        @Override
        public EvidenceWeightAdjuster evidenceWeightAdjuster() {
            return (evidence, context) -> evidence;
        }
    }
}
