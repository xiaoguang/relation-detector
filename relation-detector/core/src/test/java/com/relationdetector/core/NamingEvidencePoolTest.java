package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.core.relation.NamingEvidencePool;

class NamingEvidencePoolTest {
    @Test
    void mergesObservationsByStableNamingEvidenceId() {
        NamingEvidencePool pool = new NamingEvidencePool();
        Endpoint source = Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "customer_id"));
        Endpoint target = Endpoint.column(ColumnRef.of(TableId.of(null, "customers"), "id"));

        pool.add(new NamingEvidenceCandidate(source, target,
                evidence("sql line 1"), "TABLE_ID", true));
        pool.add(new NamingEvidenceCandidate(source, target,
                evidence("ddl column inventory"), "TABLE_ID", true));

        List<NamingEvidenceCandidate> merged = pool.merged();

        assertEquals(1, merged.size());
        assertEquals("naming:orders.customer_id->customers.id:TABLE_ID", merged.get(0).id());
        assertEquals(2, merged.get(0).rawEvidence().size());
        assertEquals(2, merged.get(0).evidence().attributes().get("count"));
    }

    private Evidence evidence(String detail) {
        return new Evidence(
                EvidenceType.NAMING_MATCH,
                BigDecimal.valueOf(0.2d),
                EvidenceSourceType.NAMING_HEURISTIC,
                "test",
                detail,
                Map.of("namingRule", "TABLE_ID",
                        "directionHint", true,
                        "suggestedSourceEndpoint", "orders.customer_id",
                        "suggestedTargetEndpoint", "customers.id"));
    }
}
