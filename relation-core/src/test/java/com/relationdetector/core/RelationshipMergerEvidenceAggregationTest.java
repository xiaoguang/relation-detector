package com.relationdetector.core;

import com.relationdetector.core.ddl.*;
import com.relationdetector.core.lineage.*;
import com.relationdetector.core.parser.*;
import com.relationdetector.core.relation.*;

import com.relationdetector.core.tokenevent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.api.ColumnRef;
import com.relationdetector.api.DefaultEvidenceScores;
import com.relationdetector.api.Endpoint;
import com.relationdetector.api.Evidence;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.TableId;
import com.relationdetector.api.Enums.EvidenceSourceType;
import com.relationdetector.api.Enums.EvidenceType;
import com.relationdetector.api.Enums.RelationSubType;
import com.relationdetector.api.Enums.RelationType;

/**
 * Tests evidence aggregation in RelationshipMerger.
 *
 * <p>Repeated observations should make the evidence trail easier to read, but
 * they must not inflate confidence by repeatedly applying the same base score.
 */
class RelationshipMergerEvidenceAggregationTest {
    private final RelationshipMerger merger = new RelationshipMerger();

    @Test
    void aggregatesRepeatedEvidenceTypeAndSourceWithoutInflatingConfidence() {
        RelationshipCandidate first = sqlLogJoin("app.log", "line 10: o.user_id = u.id");
        RelationshipCandidate second = sqlLogJoin("app.log", "line 38: o.user_id = u.id");
        RelationshipCandidate third = sqlLogJoin("app.log", "line 91: o.user_id = u.id");

        RelationshipCandidate merged = merger.merge(List.of(first, second, third), 0.0d).get(0);

        assertEquals(new BigDecimal("0.5800"), merged.confidence(),
                "Repeated SQL_LOG_JOIN observations should get capped diminishing gain, not full repeated 0.55");
        assertEquals(3, merged.rawEvidence().size(),
                "Raw evidence should preserve every original observation");
        assertEquals(2, merged.evidence().size(),
                "Summary evidence should contain grouped SQL_LOG_JOIN plus REPEATED_OBSERVATION");

        Evidence evidence = evidence(merged, EvidenceType.SQL_LOG_JOIN);
        assertEquals(EvidenceType.SQL_LOG_JOIN, evidence.type());
        assertEquals(3, evidence.attributes().get("count"));
        assertEquals("line 10: o.user_id = u.id", evidence.attributes().get("firstDetail"));
        assertEquals("line 91: o.user_id = u.id", evidence.attributes().get("lastDetail"));
        assertEquals(List.of(
                "line 10: o.user_id = u.id",
                "line 38: o.user_id = u.id",
                "line 91: o.user_id = u.id"), evidence.attributes().get("sampleDetails"));
        assertEquals(false, evidence.attributes().get("sampleTruncated"));

        Evidence repeated = evidence(merged, EvidenceType.REPEATED_OBSERVATION);
        assertEquals(3, repeated.attributes().get("count"));
        assertEquals("0.10", repeated.attributes().get("maxScore"));
    }

    @Test
    void keepsDifferentEvidenceTypesSeparateAndCountsEachType() {
        RelationshipCandidate first = sqlLogJoin("app.log", "line 10: o.user_id = u.id");
        RelationshipCandidate second = sqlLogJoin("app.log", "line 38: o.user_id = u.id");
        RelationshipCandidate unique = baseRelation();
        unique.evidence().add(Evidence.of(EvidenceType.TARGET_UNIQUE, DefaultEvidenceScores.TARGET_UNIQUE,
                EvidenceSourceType.METADATA, "metadata", "users.id is primary key"));

        RelationshipCandidate merged = merger.merge(List.of(first, second, unique), 0.0d).get(0);

        assertEquals(new BigDecimal("0.6495"), merged.confidence());
        assertEquals(3, merged.rawEvidence().size());
        assertEquals(3, merged.evidence().size());
        assertEvidenceCount(merged, EvidenceType.SQL_LOG_JOIN, 2);
        assertEvidenceCount(merged, EvidenceType.TARGET_UNIQUE, 1);
        assertEvidenceCount(merged, EvidenceType.REPEATED_OBSERVATION, 2);
    }

    @Test
    void repeatedObservationBonusApproachesAbsoluteCap() {
        List<RelationshipCandidate> observations = java.util.stream.IntStream.rangeClosed(1, 100)
                .mapToObj(i -> sqlLogJoin("app.log", "line " + i + ": o.user_id = u.id"))
                .toList();

        RelationshipCandidate merged = merger.merge(observations, 0.0d).get(0);

        Evidence repeated = evidence(merged, EvidenceType.REPEATED_OBSERVATION);
        assertTrue(repeated.score().compareTo(new BigDecimal("0.10")) < 0,
                "Diminishing repeated-observation score should approach but not reach the cap");
        assertEquals(100, repeated.attributes().get("count"));
        assertEquals(true, evidence(merged, EvidenceType.SQL_LOG_JOIN).attributes().get("sampleTruncated"));
    }

    private RelationshipCandidate sqlLogJoin(String source, String detail) {
        RelationshipCandidate candidate = baseRelation();
        candidate.evidence().add(Evidence.of(EvidenceType.SQL_LOG_JOIN, DefaultEvidenceScores.SQL_LOG_JOIN,
                EvidenceSourceType.NATIVE_LOG, source, detail));
        return candidate;
    }

    private RelationshipCandidate baseRelation() {
        return new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "user_id")),
                Endpoint.column(ColumnRef.of(TableId.of(null, "users"), "id")),
                RelationType.FK_LIKE,
                RelationSubType.INFERRED_JOIN_FK);
    }

    private void assertEvidenceCount(RelationshipCandidate relation, EvidenceType type, int count) {
        assertEquals(count, evidence(relation, type).attributes().get("count"));
    }

    private Evidence evidence(RelationshipCandidate relation, EvidenceType type) {
        Evidence evidence = relation.evidence().stream()
                .filter(e -> e.type() == type)
                .findFirst()
                .orElse(null);
        assertNotNull(evidence, () -> "Missing evidence " + type);
        return evidence;
    }
}
