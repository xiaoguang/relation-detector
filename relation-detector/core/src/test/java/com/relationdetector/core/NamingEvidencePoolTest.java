package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
import com.relationdetector.core.naming.NamingEvidencePool;

class NamingEvidencePoolTest {
    @Test
    void groupedNamingEvidenceRetainsOnlyConsensusAttributes() {
        NamingEvidencePool pool = new NamingEvidencePool();
        Endpoint source = Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "customer_id"));
        Endpoint target = Endpoint.column(ColumnRef.of(TableId.of(null, "customers"), "id"));
        Map<String, Object> common = Map.of(
                "namingRule", "TABLE_ID",
                "directionHint", true,
                "match", Map.of("sourceSuffix", "_id", "targetColumn", "id"));

        pool.add(new NamingEvidenceCandidate(source, target,
                evidence("routine observation", common, Map.of(
                        "sourceFile", "routines/rebuild_orders.sql",
                        "sourceLine", 18L,
                        "sourceObjectName", "rebuild_orders")), "TABLE_ID", true));
        pool.add(new NamingEvidenceCandidate(source, target,
                evidence("query observation", common, Map.of(
                        "sourceFile", "queries/orders.sql",
                        "sourceLine", 44L,
                        "sourceObjectName", "orders_query")), "TABLE_ID", true));

        NamingEvidenceCandidate merged = pool.merged().get(0);

        assertEquals("TABLE_ID", merged.evidence().attributes().get("namingRule"));
        assertEquals(common.get("match"), merged.evidence().attributes().get("match"));
        assertFalse(merged.evidence().attributes().containsKey("sourceFile"));
        assertFalse(merged.evidence().attributes().containsKey("sourceLine"));
        assertFalse(merged.evidence().attributes().containsKey("sourceObjectName"));
        assertEquals(2, merged.rawEvidence().size(),
                "conflicting provenance must remain available in complete raw evidence");
    }

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

    @Test
    void foldsIdenticalRawObservationsButRetainsOccurrenceCount() {
        NamingEvidencePool pool = new NamingEvidencePool();
        Endpoint source = Endpoint.column(ColumnRef.of(TableId.of(null, "orders"), "customer_id"));
        Endpoint target = Endpoint.column(ColumnRef.of(TableId.of(null, "customers"), "id"));
        NamingEvidenceCandidate duplicate = new NamingEvidenceCandidate(source, target,
                evidence("same statement"), "TABLE_ID", true);

        pool.add(duplicate);
        pool.add(duplicate);

        NamingEvidenceCandidate merged = pool.merged().get(0);
        assertEquals(1, merged.rawEvidence().size(),
                "Parser duplicates at the same source location should not look like distinct observations");
        assertEquals(2, merged.rawEvidence().get(0).attributes().get("occurrenceCount"));
        assertEquals(2, merged.evidence().attributes().get("count"));
    }

    @Test
    void mergesEquivalentStructuralEndpointsWithoutTrustingPublicFactId() {
        NamingEvidencePool pool = new NamingEvidencePool();
        pool.add(naming("catalog_a", "legacy.orders", "legacy observation"));
        pool.add(naming("catalog_a", "sales.orders", "canonical observation"));
        pool.add(naming("catalog_b", "sales.orders", "other catalog"));

        List<NamingEvidenceCandidate> merged = pool.merged();

        assertEquals(2, merged.size());
        assertEquals(2, merged.stream()
                .filter(candidate -> "catalog_a".equals(candidate.source().table().catalog()))
                .findFirst().orElseThrow().rawEvidence().size());
    }

    private NamingEvidenceCandidate naming(String catalog, String normalizedName, String detail) {
        TableId sourceTable = new TableId(catalog, "sales", "orders", normalizedName);
        TableId targetTable = new TableId(catalog, "sales", "customers", "sales.customers");
        return new NamingEvidenceCandidate(
                Endpoint.column(ColumnRef.of(sourceTable, "customer_id")),
                Endpoint.column(ColumnRef.of(targetTable, "id")),
                evidence(detail),
                "TABLE_ID",
                true);
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

    private Evidence evidence(
            String detail,
            Map<String, Object> common,
            Map<String, Object> provenance
    ) {
        Map<String, Object> attributes = new java.util.LinkedHashMap<>(common);
        attributes.putAll(provenance);
        return new Evidence(
                EvidenceType.NAMING_MATCH,
                BigDecimal.valueOf(0.2d),
                EvidenceSourceType.NAMING_HEURISTIC,
                "test",
                detail,
                attributes);
    }
}
