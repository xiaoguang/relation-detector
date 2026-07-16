package com.relationdetector.core.relation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.core.evidence.EvidenceObservationAggregator;

final class RelationshipObservationPolicy
        implements EvidenceObservationAggregator.ObservationPolicy<Evidence> {
    static boolean repeatedObservationEligible(EvidenceType type) {
        return switch (type) {
            case SQL_LOG_JOIN, SQL_LOG_SUBQUERY_IN, SQL_LOG_EXISTS, SQL_LOG_COLUMN_CO_OCCURRENCE,
                    SQL_LOG_TABLE_CO_OCCURRENCE, PROCEDURE_JOIN, TRIGGER_REFERENCE, VIEW_JOIN -> true;
            default -> false;
        };
    }

    @Override
    public Object exactKey(Evidence evidence) {
        return new ExactIdentity(
                semanticIdentityWithoutConditions(evidence),
                RelationshipConditionAttributes.identity(evidence.attributes()));
    }

    private SemanticIdentity semanticIdentityWithoutConditions(Evidence evidence) {
        Map<String, Object> identity = new LinkedHashMap<>(evidence.attributes());
        identity.keySet().removeAll(List.of(
                "occurrenceCount", "count", "firstDetail", "lastDetail", "sampleDetails", "sampleTruncated",
                "conditional", "polymorphic", "conditions",
                "discriminatorEndpoint", "discriminatorOperator", "discriminatorValue"));
        return new SemanticIdentity(summaryKey(evidence), evidence.detail(), Map.copyOf(identity));
    }

    @Override
    public Object summaryKey(Evidence evidence) {
        return evidence.type() + "|" + evidence.sourceType() + "|"
                + evidence.source() + "|" + evidence.score();
    }

    Object repetitionLocationKey(Evidence evidence) {
        return semanticIdentityWithoutConditions(evidence);
    }

    @Override public int occurrenceCount(Evidence evidence) {
        return EvidenceObservationAggregator.occurrenceCount(evidence.attributes());
    }
    @Override public Map<String, Object> observationAttributes(Evidence evidence) { return evidence.attributes(); }
    @Override public String detail(Evidence evidence) { return evidence.detail(); }

    @Override
    public Evidence withOccurrenceCount(Evidence evidence, int count) {
        if (count <= 1) return evidence;
        Map<String, Object> attributes = new LinkedHashMap<>(evidence.attributes());
        attributes.put("occurrenceCount", count);
        return new Evidence(evidence.type(), evidence.score(), evidence.sourceType(), evidence.source(),
                evidence.detail(), attributes);
    }

    private record SemanticIdentity(Object summaryKey, String detail, Map<String, Object> attributes) {
    }

    private record ExactIdentity(SemanticIdentity semantic, String conditionIdentity) {
    }
}
