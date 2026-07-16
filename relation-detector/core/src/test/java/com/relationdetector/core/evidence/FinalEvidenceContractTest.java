package com.relationdetector.core.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.relationdetector.contracts.parse.ExpressionSource;
import com.relationdetector.contracts.parse.PredicateEvent;
import com.relationdetector.contracts.parse.PredicateGuard;
import com.relationdetector.contracts.parse.RowsetEvent;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.core.relation.RelationshipMerger;
import com.relationdetector.core.relation.StructuredRelationshipExtractor;

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
        assertEquals(List.of(
                Map.of("discriminator", "contracts.party_type", "operator", "=", "value", "CUSTOMER"),
                Map.of("discriminator", "contracts.region", "operator", "=", "value", "NORTH")),
                merged.attributes().get("conditions"),
                "a merged relationship must retain each typed condition exactly once with its discriminator, operator, and value");
    }

    @Test
    void extractorPreservesSameLocationObservationsWithDifferentGuardSets() {
        SourceProvenance provenance = new SourceProvenance(
                "guards.sql", 12, "scope", "guards.sql", "guards.sql:12-12",
                "", "QUERY", "", true, false, "");
        List<com.relationdetector.contracts.parse.StructuredSqlEvent> events = List.of(
                new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance,
                        "FROM", "contracts", "contracts", "c", "c", "", ""),
                new RowsetEvent(StructuredParseEventType.ROWSET_REFERENCE, provenance,
                        "JOIN", "customers", "customers", "cu", "cu", "", ""),
                predicate(provenance, "customer"),
                predicate(provenance, "supplier"));
        SqlStatementRecord statement = new SqlStatementRecord(
                "typed events", StatementSourceType.PLAIN_SQL, "guards.sql", 12, 12,
                Map.of("sourceFile", "guards.sql", "sourceStatementId", "guards.sql:12-12"));

        List<RelationshipCandidate> candidates = new StructuredRelationshipExtractor()
                .extract(statement, new StructuredParseResult(
                        "token-event", "common", "guards.sql", events, List.of(), Map.of()));

        assertEquals(2, candidates.size(),
                "same-position observations with different complete guard sets must not be deduplicated");
    }

    @Test
    void unguardedObservationMakesSummaryUnconditionalButKeepsGuardedRawEvidence() {
        RelationshipCandidate guarded = relationship("customers");
        guarded.evidence().add(evidence(List.of(condition("party_type", "customer"))));
        RelationshipCandidate unguarded = relationship("customers");
        unguarded.evidence().add(new Evidence(EvidenceType.SQL_LOG_JOIN, BigDecimal.valueOf(0.55d),
                EvidenceSourceType.PLAIN_SQL, "contracts.sql", "unguarded equality", Map.of()));

        RelationshipCandidate merged = new RelationshipMerger().merge(List.of(guarded, unguarded), 0.0d).get(0);

        assertFalse(Boolean.TRUE.equals(merged.attributes().get("conditional")));
        assertEquals(2, merged.rawEvidence().size());
        assertTrue(merged.rawEvidence().stream().anyMatch(item ->
                Boolean.TRUE.equals(item.attributes().get("conditional"))));
    }

    @Test
    void polymorphicSummaryUsesEveryConditionInsteadOfOnlyTheFlattenedFirstGuard() {
        RelationshipCandidate customer = relationship("customers");
        customer.evidence().add(evidence(List.of(
                condition("party_type", "customer"), condition("region", "north"))));
        RelationshipCandidate supplier = relationship("suppliers");
        supplier.evidence().add(evidence(List.of(
                condition("party_type", "supplier"), condition("region", "north"))));

        List<RelationshipCandidate> merged = new RelationshipMerger()
                .merge(List.of(customer, supplier), 0.0d);

        assertEquals(2, merged.size());
        merged.forEach(candidate -> {
            assertEquals(true, candidate.attributes().get("conditional"));
            assertEquals(true, candidate.attributes().get("polymorphic"));
            assertEquals(2, ((List<?>) candidate.attributes().get("conditions")).size());
        });
    }

    @Test
    void polymorphicSummaryDoesNotMixValuesFromUnrelatedDiscriminators() {
        RelationshipCandidate customer = relationship("customers");
        customer.evidence().add(evidence(List.of(
                condition("party_type", "customer"), condition("region", "north"))));
        RelationshipCandidate supplier = relationship("suppliers");
        supplier.evidence().add(evidence(List.of(
                condition("contract_kind", "vendor"), condition("region", "north"))));

        List<RelationshipCandidate> merged = new RelationshipMerger()
                .merge(List.of(customer, supplier), 0.0d);

        assertEquals(2, merged.size());
        merged.forEach(candidate -> assertFalse(Boolean.TRUE.equals(
                candidate.attributes().get("polymorphic"))));
    }

    private PredicateEvent predicate(SourceProvenance provenance, String value) {
        return new PredicateEvent(StructuredParseEventType.PREDICATE_EQUALITY,
                provenance,
                new ExpressionSource("c", "party_id"),
                new ExpressionSource("cu", "id"),
                List.of(), List.of(), "", "JOIN", List.of(), false,
                List.of(new PredicateGuard(new ExpressionSource("c", "party_type"), "EQUALS", value)));
    }

    private RelationshipCandidate relationship(String targetTable) {
        return new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, "contracts"), "party_id")),
                Endpoint.column(ColumnRef.of(TableId.of(null, targetTable), "id")),
                RelationType.CO_OCCURRENCE,
                RelationSubType.COLUMN_CO_OCCURRENCE);
    }

    private Evidence evidence(List<Map<String, Object>> conditions) {
        return new Evidence(EvidenceType.SQL_LOG_JOIN, BigDecimal.valueOf(0.55d),
                EvidenceSourceType.PLAIN_SQL, "contracts.sql", "guarded equality",
                Map.of("conditional", true, "conditions", conditions));
    }

    private Map<String, Object> condition(String discriminatorColumn, String value) {
        return Map.of(
                "discriminator", "contracts." + discriminatorColumn,
                "operator", "EQUALS",
                "value", value);
    }
}
