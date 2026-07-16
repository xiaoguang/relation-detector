package com.relationdetector.core.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
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
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.core.naming.NamingEvidencePool;

class EvidenceWeightAdjustmentServiceTest {
    @Test
    void adjustsEachRelationshipAndNamingRawEvidenceExactlyOnce() throws Exception {
        RelationshipCandidate relationship = relationship(Evidence.of(
                EvidenceType.SQL_LOG_JOIN, 0.4d, EvidenceSourceType.PLAIN_SQL, "query.sql", "join"));
        NamingEvidencePool naming = new NamingEvidencePool();
        naming.add(new NamingEvidenceCandidate(
                relationship.source(), relationship.target(), Evidence.of(
                        EvidenceType.NAMING_MATCH, 0.3d, EvidenceSourceType.NAMING_HEURISTIC, "rule", "name"),
                "fk_suffix", true));
        AtomicInteger calls = new AtomicInteger();

        invoke(List.of(relationship), naming, (evidence, context) -> {
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

    private void assertAdjustmentRejected(EvidenceWeightAdjuster adjuster) {
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> invoke(List.of(relationship(Evidence.of(EvidenceType.SQL_LOG_JOIN, 0.4d,
                        EvidenceSourceType.PLAIN_SQL, "query.sql", "join"))), new NamingEvidencePool(), adjuster));
        assertTrue(failure.getCause() instanceof IllegalArgumentException);
    }

    private void invoke(
            List<RelationshipCandidate> relationships,
            NamingEvidencePool naming,
            EvidenceWeightAdjuster adjuster
    ) throws Exception {
        Class<?> type;
        try {
            type = Class.forName("com.relationdetector.core.evidence.EvidenceWeightAdjustmentService");
        } catch (ClassNotFoundException missing) {
            assertFalse(true, "EvidenceWeightAdjustmentService must be the sole adjustment consumer");
            return;
        }
        Method adjust = type.getDeclaredMethod("adjust", List.class, NamingEvidencePool.class,
                EvidenceWeightAdjuster.class, AdaptorContext.class);
        adjust.invoke(type.getConstructor().newInstance(), relationships, naming, adjuster,
                new AdaptorContext(null, java.util.Map.of(), warning -> { }));
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
