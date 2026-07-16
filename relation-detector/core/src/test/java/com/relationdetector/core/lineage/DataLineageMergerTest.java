package com.relationdetector.core.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.DataLineageEvidence;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.TableId;

class DataLineageMergerTest {
    @Test
    void retainsOnlyConsensusAttributesAcrossObservations() {
        DataLineageCandidate routine = lineage(Map.of(
                "mappingKind", "INSERT_SELECT",
                "sourceObjectType", "ROUTINE",
                "sourceObjectName", "sp_rebuild_sales_fact",
                "sourceFile", "sample-data/mysql/8.0/02-procedures/13.sql"));
        DataLineageCandidate plainSql = lineage(Map.of(
                "mappingKind", "INSERT_SELECT",
                "sourceObjectType", "SQL_WRITE",
                "sourceFile", "sample-data/mysql/8.0/03-data/07.sql",
                "sourceStatementId", "sample-data/mysql/8.0/03-data/07.sql:20-28"));

        DataLineageCandidate merged = new DataLineageMerger().merge(List.of(routine, plainSql)).get(0);

        assertEquals("INSERT_SELECT", merged.attributes().get("mappingKind"));
        assertFalse(merged.attributes().containsKey("sourceObjectType"));
        assertFalse(merged.attributes().containsKey("sourceObjectName"));
        assertFalse(merged.attributes().containsKey("sourceFile"));
        assertFalse(merged.attributes().containsKey("sourceStatementId"));
        assertEquals(2, merged.rawEvidence().size());
    }

    @Test
    void foldsOnlyIdenticalRawObservationsAndRecordsOccurrenceCount() {
        Map<String, Object> attributes = Map.of(
                "mappingKind", "INSERT_SELECT",
                "sourceFile", "sample-data/mysql/8.0/03-data/07.sql",
                "sourceStatementId", "sample-data/mysql/8.0/03-data/07.sql:20-28",
                "sourceLine", 24L);

        Map<String, Object> folded = new LinkedHashMap<>(attributes);
        folded.put("occurrenceCount", 3);
        DataLineageCandidate merged = new DataLineageMerger()
                .merge(List.of(lineage(folded)))
                .get(0);

        assertEquals(1, merged.rawEvidence().size());
        assertEquals(3, merged.rawEvidence().get(0).attributes().get("occurrenceCount"));
        assertEquals(3, merged.evidence().get(0).attributes().get("count"));
    }

    @Test
    void keepsObservationsAtDifferentSourceLinesSeparate() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("mappingKind", "INSERT_SELECT");
        first.put("sourceFile", "sample-data/mysql/8.0/02-procedures/13.sql");
        first.put("sourceLine", 261L);
        Map<String, Object> second = new LinkedHashMap<>(first);
        second.put("sourceLine", 264L);

        DataLineageCandidate merged = new DataLineageMerger()
                .merge(List.of(lineage(first), lineage(second)))
                .get(0);

        assertEquals(2, merged.rawEvidence().size());
        assertFalse(merged.attributes().containsKey("sourceLine"));
    }

    @Test
    void treatsSourceEndpointsAsAnOrderIndependentSet() {
        Endpoint orders = Endpoint.column(ColumnRef.of(TableId.of(null, "sales_orders"), "id"));
        Endpoint customers = Endpoint.column(ColumnRef.of(TableId.of(null, "customers"), "id"));
        Endpoint target = Endpoint.column(ColumnRef.of(TableId.of(null, "sales_fact"), "order_id"));
        DataLineageCandidate first = new DataLineageCandidate(
                List.of(orders, customers), target, LineageFlowKind.VALUE, LineageTransformType.DIRECT);
        DataLineageCandidate second = new DataLineageCandidate(
                List.of(customers, orders), target, LineageFlowKind.VALUE, LineageTransformType.DIRECT);

        List<DataLineageCandidate> merged = new DataLineageMerger().merge(List.of(first, second));

        assertEquals(1, merged.size());
        assertEquals(List.of(customers.normalizedKey(), orders.normalizedKey()),
                merged.get(0).sources().stream().map(Endpoint::normalizedKey).toList());
    }

    @Test
    void mergesEquivalentStructuralEndpointsWithoutTrustingCallerNormalizedName() {
        DataLineageCandidate legacy = lineageWithTableIdentity(
                "catalog_a", "sales", "orders", "legacy.orders");
        DataLineageCandidate canonical = lineageWithTableIdentity(
                "catalog_a", "sales", "orders", "sales.orders");
        DataLineageCandidate otherCatalog = lineageWithTableIdentity(
                "catalog_b", "sales", "orders", "sales.orders");

        List<DataLineageCandidate> merged = new DataLineageMerger()
                .merge(List.of(legacy, canonical, otherCatalog));

        assertEquals(2, merged.size());
        assertEquals(2, merged.stream()
                .filter(candidate -> "catalog_a".equals(candidate.sources().get(0).table().catalog()))
                .findFirst().orElseThrow().rawEvidence().size());
    }

    @Test
    void canonicalSourceSetIdentityDoesNotDependOnDisplaySortOrder() {
        Endpoint firstOrders = endpoint("orders", "z.orders");
        Endpoint firstCustomers = endpoint("customers", "a.customers");
        Endpoint secondOrders = endpoint("orders", "a.orders");
        Endpoint secondCustomers = endpoint("customers", "z.customers");
        Endpoint target = Endpoint.column(ColumnRef.of(
                new TableId("catalog_a", "sales", "sales_fact", "sales.sales_fact"),
                "order_id"));
        DataLineageCandidate first = new DataLineageCandidate(
                List.of(firstOrders, firstCustomers), target,
                LineageFlowKind.VALUE, LineageTransformType.DIRECT);
        DataLineageCandidate second = new DataLineageCandidate(
                List.of(secondOrders, secondCustomers), target,
                LineageFlowKind.VALUE, LineageTransformType.DIRECT);

        List<DataLineageCandidate> merged = new DataLineageMerger().merge(List.of(first, second));

        assertEquals(1, merged.size(),
                "Canonical source-set identity must be independent of public display-key ordering");
    }

    private DataLineageCandidate lineage(Map<String, Object> attributes) {
        DataLineageCandidate candidate = new DataLineageCandidate(
                List.of(Endpoint.column(ColumnRef.of(TableId.of(null, "sales_orders"), "id"))),
                Endpoint.column(ColumnRef.of(TableId.of(null, "sales_fact"), "order_id")),
                LineageFlowKind.VALUE,
                LineageTransformType.DIRECT);
        candidate.confidence(BigDecimal.valueOf(0.8));
        candidate.attributes().putAll(attributes);
        candidate.evidence().add(new DataLineageEvidence(
                LineageTransformType.DIRECT,
                BigDecimal.valueOf(0.8),
                EvidenceSourceType.PLAIN_SQL,
                String.valueOf(attributes.getOrDefault("sourceFile", "fixture.sql")),
                "ANTLR token-event write mapping",
                attributes));
        return candidate;
    }

    private DataLineageCandidate lineageWithTableIdentity(
            String catalog,
            String schema,
            String table,
            String normalizedName
    ) {
        TableId sourceTable = new TableId(catalog, schema, table, normalizedName);
        TableId targetTable = new TableId(catalog, schema, "sales_fact", schema + ".sales_fact");
        DataLineageCandidate candidate = new DataLineageCandidate(
                List.of(Endpoint.column(ColumnRef.of(sourceTable, "id"))),
                Endpoint.column(ColumnRef.of(targetTable, "order_id")),
                LineageFlowKind.VALUE,
                LineageTransformType.DIRECT);
        candidate.evidence().add(new DataLineageEvidence(
                LineageTransformType.DIRECT,
                BigDecimal.ONE,
                EvidenceSourceType.PLAIN_SQL,
                "query.sql",
                normalizedName,
                Map.of("sourceLine", 1L)));
        return candidate;
    }

    private Endpoint endpoint(String table, String normalizedName) {
        return Endpoint.column(ColumnRef.of(
                new TableId("catalog_a", "sales", table, normalizedName),
                "id"));
    }
}
