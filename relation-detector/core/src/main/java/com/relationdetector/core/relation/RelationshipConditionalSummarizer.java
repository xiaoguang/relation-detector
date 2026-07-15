package com.relationdetector.core.relation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;

/** Summarizes conditional and polymorphic structural observations. */
final class RelationshipConditionalSummarizer {
    void summarize(RelationshipCandidate candidate) {
        List<Evidence> structural = candidate.rawEvidence().stream().filter(this::structural).toList();
        candidate.attributes().clear();
        if (structural.isEmpty() || structural.stream().anyMatch(evidence ->
                !Boolean.TRUE.equals(evidence.attributes().get("conditional")))) return;
        List<Map<String, Object>> conditions = structural.stream()
                .map(this::condition).filter(value -> !value.isEmpty()).distinct()
                .sorted(Comparator.comparing((Map<String, Object> value) ->
                                String.valueOf(value.get("discriminator")))
                        .thenComparing(value -> String.valueOf(value.get("operator")))
                        .thenComparing(value -> String.valueOf(value.get("value"))))
                .toList();
        if (conditions.isEmpty()) return;
        candidate.attributes().put("conditional", true);
        candidate.attributes().put("polymorphic", false);
        candidate.attributes().put("conditions", conditions);
    }

    void annotatePolymorphic(Collection<RelationshipCandidate> candidates) {
        Map<String, List<RelationshipCandidate>> grouped = new LinkedHashMap<>();
        for (RelationshipCandidate candidate : candidates) {
            if (!Boolean.TRUE.equals(candidate.attributes().get("conditional"))) continue;
            for (Map<String, Object> condition : conditions(candidate)) {
                String key = candidate.source().normalizedKey() + "|" + condition.get("discriminator");
                grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(candidate);
            }
        }
        grouped.values().stream()
                .filter(group -> group.stream().map(value -> value.target().normalizedKey()).distinct().count() > 1)
                .filter(group -> group.stream().flatMap(value -> conditions(value).stream())
                        .map(value -> value.get("value")).distinct().count() > 1)
                .flatMap(List::stream)
                .forEach(candidate -> candidate.attributes().put("polymorphic", true));
    }

    private Map<String, Object> condition(Evidence evidence) {
        Object discriminator = evidence.attributes().get("discriminatorEndpoint");
        Object operator = evidence.attributes().get("discriminatorOperator");
        Object value = evidence.attributes().get("discriminatorValue");
        if (discriminator == null || operator == null || value == null) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("discriminator", discriminator);
        result.put("operator", operator);
        result.put("value", value);
        return Map.copyOf(result);
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
