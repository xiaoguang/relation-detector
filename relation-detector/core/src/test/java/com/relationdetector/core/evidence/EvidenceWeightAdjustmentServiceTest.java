package com.relationdetector.core.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.core.naming.NamingEvidencePool;
import com.relationdetector.core.scan.AdaptorContractException;

class EvidenceWeightAdjustmentServiceTest {
    @Test
    void adjustsEachRelationshipAndNamingRawEvidenceExactlyOnce() {
        RelationshipCandidate relationship = relationship(Evidence.of(
                EvidenceType.SQL_LOG_JOIN, 0.4d, EvidenceSourceType.PLAIN_SQL, "query.sql", "join"));
        NamingEvidencePool naming = new NamingEvidencePool();
        naming.add(new NamingEvidenceCandidate(
                relationship.source(), relationship.target(), Evidence.of(
                        EvidenceType.NAMING_MATCH, 0.3d, EvidenceSourceType.NAMING_HEURISTIC, "rule", "name"),
                "fk_suffix", true));
        AtomicInteger calls = new AtomicInteger();

        adjust(List.of(relationship), naming, (evidence, context) -> {
            calls.incrementAndGet();
            return new Evidence(evidence.type(), evidence.score().add(BigDecimal.valueOf(0.1d)),
                    evidence.sourceType(), evidence.source(), evidence.detail(), evidence.attributes());
        });

        assertEquals(2, calls.get());
        assertEquals(0, relationship.evidence().get(0).score().compareTo(BigDecimal.valueOf(0.5d)));
        assertEquals(0, naming.merged().get(0).rawEvidence().get(0).score().compareTo(BigDecimal.valueOf(0.4d)));
    }

    @Test
    void rejectsNullOrOutOfRangeAdjustedEvidence() {
        assertAdjustmentRejected((evidence, context) -> null);
        assertAdjustmentRejected((evidence, context) -> new Evidence(evidence.type(), BigDecimal.valueOf(1.01d),
                evidence.sourceType(), evidence.source(), evidence.detail(), evidence.attributes()));
    }

    @Test
    void rejectsChangesToEvidenceIdentityFields() {
        Evidence original = evidence("join", Map.of("sourceLine", 7));

        assertAdjustmentRejected(original, (evidence, context) -> new Evidence(
                EvidenceType.SQL_LOG_EXISTS, evidence.score(), evidence.sourceType(),
                evidence.source(), evidence.detail(), evidence.attributes()));
        assertAdjustmentRejected(original, (evidence, context) -> new Evidence(
                evidence.type(), evidence.score(), EvidenceSourceType.NATIVE_LOG,
                evidence.source(), evidence.detail(), evidence.attributes()));
        assertAdjustmentRejected(original, (evidence, context) -> new Evidence(
                evidence.type(), evidence.score(), evidence.sourceType(),
                "other.sql", evidence.detail(), evidence.attributes()));
        assertAdjustmentRejected(original, (evidence, context) -> new Evidence(
                evidence.type(), evidence.score(), evidence.sourceType(),
                evidence.source(), "different", evidence.attributes()));
        assertAdjustmentRejected(original, (evidence, context) -> new Evidence(
                evidence.type(), evidence.score(), evidence.sourceType(),
                evidence.source(), evidence.detail(), Map.of("sourceLine", 8)));
    }

    @Test
    void finalRelationshipViolationLeavesEveryRelationshipUnchanged() {
        RelationshipCandidate first = relationship(evidence("first", Map.of()));
        RelationshipCandidate second = relationship(evidence("second", Map.of()));
        AtomicInteger calls = new AtomicInteger();

        assertThrows(AdaptorContractException.class, () -> adjust(
                List.of(first, second), new NamingEvidencePool(), (evidence, context) -> {
                    if (calls.incrementAndGet() == 1) {
                        return withScore(evidence, 0.6d);
                    }
                    return new Evidence(evidence.type(), evidence.score(), evidence.sourceType(),
                            evidence.source(), "changed", evidence.attributes());
                }));

        assertEquals(0, first.evidence().get(0).score().compareTo(BigDecimal.valueOf(0.4d)));
        assertEquals(0, second.evidence().get(0).score().compareTo(BigDecimal.valueOf(0.4d)));
    }

    @Test
    void namingViolationLeavesRelationshipAndNamingScoresUnchanged() {
        RelationshipCandidate relationship = relationship(evidence("join", Map.of()));
        NamingEvidencePool naming = new NamingEvidencePool();
        naming.add(new NamingEvidenceCandidate(
                relationship.source(), relationship.target(), Evidence.of(
                        EvidenceType.NAMING_MATCH, 0.3d, EvidenceSourceType.NAMING_HEURISTIC, "rule", "name"),
                "fk_suffix", true));

        assertThrows(AdaptorContractException.class, () -> adjust(
                List.of(relationship), naming, (evidence, context) -> {
                    if (evidence.type() == EvidenceType.NAMING_MATCH) {
                        return new Evidence(EvidenceType.SQL_LOG_JOIN, evidence.score(), evidence.sourceType(),
                                evidence.source(), evidence.detail(), evidence.attributes());
                    }
                    return withScore(evidence, 0.6d);
                }));

        assertEquals(0, relationship.evidence().get(0).score().compareTo(BigDecimal.valueOf(0.4d)));
        assertEquals(0, naming.merged().get(0).rawEvidence().get(0).score()
                .compareTo(BigDecimal.valueOf(0.3d)));
    }

    @Test
    void adjusterCannotEmitWarningsAsASideEffect() {
        List<WarningMessage> scanWarnings = new ArrayList<>();
        RelationshipCandidate relationship = relationship(evidence("join", Map.of()));
        AdaptorContext context = new AdaptorContext(null, Map.of(), scanWarnings::add);

        assertThrows(AdaptorContractException.class, () -> new EvidenceWeightAdjustmentService().adjust(
                List.of(relationship), new NamingEvidencePool(), (evidence, detached) -> {
                    detached.warn(WarningMessage.warn(
                            com.relationdetector.contracts.Enums.WarningType.PARSE_WARNING,
                            "HOOK_WARNING", "must not escape", "plugin", 1));
                    return withScore(evidence, 0.6d);
                }, context));

        assertTrue(scanWarnings.isEmpty());
        assertEquals(0, relationship.evidence().get(0).score().compareTo(BigDecimal.valueOf(0.4d)));
    }

    @Test
    void adjusterCannotMutateNestedEvidenceAttributes() {
        List<String> nested = new ArrayList<>(List.of("original"));
        RelationshipCandidate relationship = relationship(evidence("join", Map.of("nested", nested)));

        assertThrows(AdaptorContractException.class, () -> adjust(
                List.of(relationship), new NamingEvidencePool(), (evidence, context) -> {
                    @SuppressWarnings("unchecked")
                    List<String> values = (List<String>) evidence.attributes().get("nested");
                    values.add("plugin");
                    return withScore(evidence, 0.6d);
                }));

        assertEquals(List.of("original"), nested);
        assertEquals(List.of("original"), relationship.evidence().get(0).attributes().get("nested"));
    }

    @Test
    void adjusterCannotMutateNestedContextOptions() {
        List<String> optionValues = new ArrayList<>(List.of("safe"));
        RelationshipCandidate relationship = relationship(evidence("join", Map.of()));
        AdaptorContext context = new AdaptorContext(null, Map.of("nested", optionValues), warning -> { });

        assertThrows(AdaptorContractException.class, () -> new EvidenceWeightAdjustmentService().adjust(
                List.of(relationship), new NamingEvidencePool(), (evidence, detached) -> {
                    @SuppressWarnings("unchecked")
                    List<String> values = (List<String>) detached.options().get("nested");
                    values.add("plugin");
                    return withScore(evidence, 0.6d);
                }, context));

        assertEquals(List.of("safe"), optionValues);
        assertEquals(0, relationship.evidence().get(0).score().compareTo(BigDecimal.valueOf(0.4d)));
    }

    @Test
    void appliedEvidenceDoesNotRetainPluginNestedContainers() {
        List<String> baselineNested = new ArrayList<>(List.of("safe"));
        List<String> pluginNested = new ArrayList<>(List.of("safe"));
        RelationshipCandidate relationship = relationship(
                evidence("join", Map.of("nested", baselineNested)));

        adjust(List.of(relationship), new NamingEvidencePool(), (evidence, context) -> new Evidence(
                evidence.type(), BigDecimal.valueOf(0.6d), evidence.sourceType(),
                evidence.source(), evidence.detail(), Map.of("nested", pluginNested)));
        pluginNested.add("late-mutation");

        assertEquals(List.of("safe"), relationship.evidence().get(0).attributes().get("nested"));
        assertEquals(0, relationship.evidence().get(0).score().compareTo(BigDecimal.valueOf(0.6d)));
    }

    private void assertAdjustmentRejected(EvidenceWeightAdjuster adjuster) {
        assertAdjustmentRejected(evidence("join", Map.of()), adjuster);
    }

    private void assertAdjustmentRejected(Evidence evidence, EvidenceWeightAdjuster adjuster) {
        assertThrows(AdaptorContractException.class,
                () -> adjust(List.of(relationship(evidence)), new NamingEvidencePool(), adjuster));
    }

    private void adjust(
            List<RelationshipCandidate> relationships,
            NamingEvidencePool naming,
            EvidenceWeightAdjuster adjuster
    ) {
        new EvidenceWeightAdjustmentService().adjust(relationships, naming, adjuster,
                new AdaptorContext(null, java.util.Map.of(), warning -> { }));
    }

    private Evidence evidence(String detail, Map<String, Object> attributes) {
        return new Evidence(EvidenceType.SQL_LOG_JOIN, BigDecimal.valueOf(0.4d),
                EvidenceSourceType.PLAIN_SQL, "query.sql", detail, attributes);
    }

    private Evidence withScore(Evidence evidence, double score) {
        return new Evidence(evidence.type(), BigDecimal.valueOf(score), evidence.sourceType(),
                evidence.source(), evidence.detail(), evidence.attributes());
    }

    private RelationshipCandidate relationship(Evidence evidence) {
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of("", "orders"), "customer_id")),
                Endpoint.column(ColumnRef.of(TableId.of("", "customers"), "id")),
                RelationType.FK_LIKE, RelationSubType.INFERRED_JOIN_FK);
        candidate.evidence().add(evidence);
        return candidate;
    }
}
