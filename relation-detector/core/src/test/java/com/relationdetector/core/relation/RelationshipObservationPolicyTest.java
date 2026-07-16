package com.relationdetector.core.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.model.Evidence;
import org.junit.jupiter.api.Test;

class RelationshipObservationPolicyTest {
    @Test
    void repetitionKeyIgnoresOnlyGuardVariantsAtOneTypedLocation() {
        RelationshipObservationPolicy policy = new RelationshipObservationPolicy();

        assertEquals(
                policy.repetitionLocationKey(evidence(Map.of())),
                policy.repetitionLocationKey(evidence(Map.of(
                        "conditional", true,
                        "conditions", List.of(Map.of(
                                "discriminator", "warehouses.status",
                                "operator", "EQUALS",
                                "value", "active"))))));
    }

    private Evidence evidence(Map<String, Object> semanticAttributes) {
        Map<String, Object> attributes = new LinkedHashMap<>(semanticAttributes);
        attributes.put("sourceFile", "routine.sql");
        attributes.put("sourceStatementId", "routine.sql:50-60");
        attributes.put("sourceLine", 56);
        return new Evidence(
                EvidenceType.SQL_LOG_JOIN,
                BigDecimal.valueOf(0.55d),
                EvidenceSourceType.PLAIN_SQL,
                "routine.sql",
                "typed column equality",
                attributes);
    }
}
