package com.relationdetector.core.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.core.relation.RelationshipMerger;

class FinalEvidenceContractTest {
    @Test
    void preservesEveryTypedConditionFromOneStructuralObservationAfterMerge() {
        RelationshipCandidate observation = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, "contracts"), "party_id")),
                Endpoint.column(ColumnRef.of(TableId.of(null, "parties"), "id")),
                RelationType.FK_LIKE,
                RelationSubType.INFERRED_JOIN_FK);
        observation.evidence().add(new Evidence(
                EvidenceType.SQL_LOG_JOIN,
                BigDecimal.valueOf(0.55d),
                EvidenceSourceType.PLAIN_SQL,
                "contracts.sql",
                "contracts.party_id = parties.id",
                Map.of(
                        "conditional", true,
                        "conditions", List.of(
                                Map.of("discriminator", "contracts.party_type", "operator", "=", "value", "CUSTOMER"),
                                Map.of("discriminator", "contracts.region", "operator", "=", "value", "NORTH")))));

        RelationshipCandidate merged = new RelationshipMerger().merge(List.of(observation), 0.0d).get(0);
        Object conditions = merged.attributes().get("conditions");
        int conditionCount = conditions instanceof List<?> list ? list.size() : 0;

        assertEquals(2, conditionCount,
                "a merged relationship must retain both typed conditions from its structural observation");
    }
}
