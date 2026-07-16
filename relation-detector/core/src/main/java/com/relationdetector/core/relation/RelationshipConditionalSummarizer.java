package com.relationdetector.core.relation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.core.identity.CanonicalEndpointKeyProvider;

/**
 *
 * Summarizes conditional and polymorphic structural observations.
 */
final class RelationshipConditionalSummarizer {
    private final CanonicalEndpointKeyProvider endpointKeys;

    RelationshipConditionalSummarizer(CanonicalEndpointKeyProvider endpointKeys) {
        this.endpointKeys = endpointKeys;
    }

    void summarize(RelationshipCandidate candidate) {
        List<Evidence> structural = candidate.rawEvidence().stream().filter(this::structural).toList();
        candidate.attributes().clear();
        if (structural.isEmpty() || structural.stream().anyMatch(evidence ->
                RelationshipConditionAttributes.conditions(evidence.attributes()).isEmpty())) return;
        List<Map<String, Object>> conditions = structural.stream()
                .flatMap(evidence -> RelationshipConditionAttributes.conditions(evidence.attributes()).stream())
                .distinct()
                .toList();
        if (conditions.isEmpty()) return;
        candidate.attributes().put("conditional", true);
        candidate.attributes().put("polymorphic", false);
        candidate.attributes().put("conditions", conditions);
    }

    void annotatePolymorphic(Collection<RelationshipCandidate> candidates) {
        Map<ConditionGroupKey, List<RelationshipCandidate>> grouped = new LinkedHashMap<>();
        for (RelationshipCandidate candidate : candidates) {
            if (!Boolean.TRUE.equals(candidate.attributes().get("conditional"))) continue;
            for (Map<String, Object> condition : conditions(candidate)) {
                ConditionGroupKey key = new ConditionGroupKey(
                        endpointKeys.factKey(candidate.source()),
                        String.valueOf(condition.get("discriminator")),
                        String.valueOf(condition.get("operator")));
                grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(candidate);
            }
        }
        grouped.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .map(value -> endpointKeys.factKey(value.target())).distinct().count() > 1)
                .filter(entry -> entry.getValue().stream().flatMap(value -> conditions(value).stream())
                        .filter(condition -> entry.getKey().matches(condition))
                        .map(condition -> condition.get("value")).distinct().count() > 1)
                .flatMap(entry -> entry.getValue().stream())
                .forEach(candidate -> candidate.attributes().put("polymorphic", true));
    }

    private record ConditionGroupKey(String source, String discriminator, String operator) {
        boolean matches(Map<String, Object> condition) {
            return discriminator.equals(String.valueOf(condition.get("discriminator")))
                    && operator.equals(String.valueOf(condition.get("operator")));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> conditions(RelationshipCandidate candidate) {
        Object value = candidate.attributes().get("conditions");
        return value instanceof List<?> list
                ? list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList()
                : List.of();
    }

    private boolean structural(Evidence evidence) {
        return switch (evidence.type()) {
            case DDL_FOREIGN_KEY, METADATA_FOREIGN_KEY,
                    SQL_LOG_JOIN, SQL_LOG_SUBQUERY_IN, SQL_LOG_EXISTS, SQL_LOG_COLUMN_CO_OCCURRENCE,
                    VIEW_JOIN, PROCEDURE_JOIN, TRIGGER_REFERENCE -> true;
            default -> false;
        };
    }
}
