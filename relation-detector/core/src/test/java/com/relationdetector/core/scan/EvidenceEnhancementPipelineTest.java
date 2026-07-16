package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.DataLineageEvidence;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.AdaptorProfiling;
import com.relationdetector.contracts.spi.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.core.common.CommonDatabaseAdaptor;
import com.relationdetector.core.relation.RelationshipMerger;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EvidenceEnhancementPipelineTest {
    @Test
    void adjustsNewRelationshipAndNamingEvidenceOnceWithoutTouchingLineage() {
        AtomicInteger calls = new AtomicInteger();
        ScanPipelineContext context = context((evidence, ignored) -> {
            calls.incrementAndGet();
            return withScore(evidence, 0.1d);
        });
        DataLineageEvidence lineageBefore = context.lineageCandidates.get(0).evidence().get(0);

        new EvidenceEnhancementPipeline().adjustWeights(context);

        assertEquals(2, calls.get());
        assertEquals(BigDecimal.valueOf(0.5d), context.relationshipCandidates.get(0).evidence().get(0).score());
        assertEquals(BigDecimal.valueOf(0.4d), context.namingEvidencePool.merged().get(0).rawEvidence().get(0).score());
        assertEquals(lineageBefore, context.lineageCandidates.get(0).evidence().get(0));
    }

    @Test
    void builtInNoOpAdjusterLeavesPipelineEvidenceUnchanged() {
        ScanPipelineContext context = context(new CommonDatabaseAdaptor().profiling().evidenceWeightAdjuster());
        Evidence relationshipBefore = context.relationshipCandidates.get(0).evidence().get(0);
        Evidence namingBefore = context.namingEvidencePool.merged().get(0).rawEvidence().get(0);

        new EvidenceEnhancementPipeline().adjustWeights(context);

        assertEquals(relationshipBefore, context.relationshipCandidates.get(0).evidence().get(0));
        assertEquals(namingBefore, context.namingEvidencePool.merged().get(0).rawEvidence().get(0));
    }

    @Test
    void preservesAdjustedRawObservationsThroughFinalRelationshipMerge() {
        AtomicInteger calls = new AtomicInteger();
        ScanPipelineContext context = context((evidence, ignored) -> {
            calls.incrementAndGet();
            return withScore(evidence, 0.1d);
        });
        RelationshipCandidate candidate = context.relationshipCandidates.get(0);
        candidate.rawEvidence().add(evidence(EvidenceType.SQL_LOG_JOIN, 0.4d));
        candidate.rawEvidence().add(evidence(EvidenceType.SQL_LOG_JOIN, 0.4d));

        new EvidenceEnhancementPipeline().adjustWeights(context);
        RelationshipCandidate merged = new RelationshipMerger().merge(context.relationshipCandidates, 0.0d).get(0);

        assertEquals(3, calls.get(), "each relationship raw observation and naming observation is adjusted once");
        assertEquals(List.of(BigDecimal.valueOf(0.5d)),
                merged.rawEvidence().stream().map(Evidence::score).toList());
        assertEquals(List.of(BigDecimal.valueOf(0.5d)),
                merged.evidence().stream()
                        .filter(evidence -> evidence.type() == EvidenceType.SQL_LOG_JOIN)
                        .map(Evidence::score)
                        .toList());
        assertEquals(BigDecimal.valueOf(0.5d).setScale(4), merged.confidence());
    }

    private ScanPipelineContext context(EvidenceWeightAdjuster adjuster) {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.COMMON;
        RelationshipCandidate relationship = relationship();
        relationship.evidence().add(evidence(EvidenceType.SQL_LOG_JOIN, 0.4d));
        DataLineageCandidate lineage = new DataLineageCandidate(
                List.of(relationship.source()), relationship.target(), LineageFlowKind.VALUE, LineageTransformType.DIRECT);
        lineage.evidence().add(new DataLineageEvidence(LineageTransformType.DIRECT, BigDecimal.valueOf(0.7d),
                EvidenceSourceType.PLAIN_SQL, "lineage.sql", "lineage", Map.of()));
        ScanResult result = new ScanResult("common", null, null);
        CommonDatabaseAdaptor base = new CommonDatabaseAdaptor();
        ScanPipelineContext context = new ScanPipelineContext(config.resolve(), new ProfilingAdaptor(base, adjuster),
                new com.relationdetector.contracts.spi.ScanScope(null, null, List.of(), List.of()), result,
                new AdaptorContext(null, Map.of(), result.warnings()::add), new ArrayList<>(List.of(relationship)),
                new ArrayList<>(List.of(lineage)));
        context.namingEvidencePool.add(new NamingEvidenceCandidate(
                relationship.source(), relationship.target(), evidence(EvidenceType.NAMING_MATCH, 0.3d),
                "fk_suffix", true));
        return context;
    }

    private RelationshipCandidate relationship() {
        return new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "customer_id")),
                Endpoint.column(ColumnRef.of(TableId.of(null, "customers"), "id")),
                RelationType.FK_LIKE, RelationSubType.INFERRED_JOIN_FK);
    }

    private Evidence evidence(EvidenceType type, double score) {
        return Evidence.of(type, score, EvidenceSourceType.PLAIN_SQL, "query.sql", type.name());
    }

    private Evidence withScore(Evidence evidence, double delta) {
        return new Evidence(evidence.type(), evidence.score().add(BigDecimal.valueOf(delta)), evidence.sourceType(),
                evidence.source(), evidence.detail(), evidence.attributes());
    }

    private record ProfilingAdaptor(CommonDatabaseAdaptor base, EvidenceWeightAdjuster adjuster)
            implements com.relationdetector.contracts.spi.DatabaseAdaptor {
        @Override public int spiVersion() { return base.spiVersion(); }
        @Override public String id() { return base.id(); }
        @Override public String displayName() { return base.displayName(); }
        @Override public java.util.Set<DatabaseType> supportedDatabaseTypes() { return base.supportedDatabaseTypes(); }
        @Override public java.util.Set<com.relationdetector.contracts.Enums.AdaptorCapability> capabilities() { return base.capabilities(); }
        @Override public com.relationdetector.contracts.spi.IdentifierRules identifierRules() { return base.identifierRules(); }
        @Override public com.relationdetector.contracts.spi.AdaptorCollectors collectors() { return base.collectors(); }
        @Override public com.relationdetector.contracts.spi.AdaptorParsers parsers() { return base.parsers(); }
        @Override public AdaptorProfiling profiling() { return new AdaptorProfiling(base.profiling().dataProfiler(), adjuster); }
    }
}
