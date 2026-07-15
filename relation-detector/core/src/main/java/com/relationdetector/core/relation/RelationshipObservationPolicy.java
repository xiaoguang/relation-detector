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
        Map<String, Object> identity = new LinkedHashMap<>(evidence.attributes());
        identity.keySet().removeAll(List.of(
                "occurrenceCount", "count", "firstDetail", "lastDetail", "sampleDetails", "sampleTruncated"));
        return summaryKey(evidence) + "|" + evidence.detail() + "|" + identity;
    }

    @Override
    public Object summaryKey(Evidence evidence) {
        return evidence.type() + "|" + evidence.sourceType() + "|"
                + evidence.source() + "|" + evidence.score();
    }

    @Override public int occurrenceCount(Evidence evidence) { return 1; }
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
}
