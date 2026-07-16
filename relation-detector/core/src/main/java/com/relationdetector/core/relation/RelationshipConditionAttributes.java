package com.relationdetector.core.relation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Normalizes the complete typed guard array used by relationship observations.
 * It preserves first-seen output order while producing an order-independent
 * identity. Legacy flattened fields are read only as a single-condition fallback.
 */
public final class RelationshipConditionAttributes {
    private static final Comparator<Map<String, Object>> ORDER = Comparator
            .comparing((Map<String, Object> value) -> text(value.get("discriminator")))
            .thenComparing(value -> text(value.get("operator")))
            .thenComparing(value -> text(value.get("value")));

    private RelationshipConditionAttributes() {
    }

    public static List<Map<String, Object>> conditions(Map<String, Object> attributes) {
        List<Map<String, Object>> result = new ArrayList<>();
        Object raw = attributes.get("conditions");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> condition = condition(map);
                    if (!condition.isEmpty() && !result.contains(condition)) {
                        result.add(condition);
                    }
                }
            }
        }
        if (!result.isEmpty()) {
            return List.copyOf(result);
        }
        Map<String, Object> legacy = legacyCondition(attributes);
        return legacy.isEmpty() ? List.of() : List.of(legacy);
    }

    public static String identity(Map<String, Object> attributes) {
        return conditions(attributes).stream()
                .sorted(ORDER)
                .map(RelationshipConditionAttributes::conditionIdentity)
                .distinct()
                .reduce((left, right) -> left + ";" + right)
                .orElse("");
    }

    public static void write(Map<String, Object> attributes, List<Map<String, Object>> conditions) {
        attributes.remove("discriminatorEndpoint");
        attributes.remove("discriminatorOperator");
        attributes.remove("discriminatorValue");
        List<Map<String, Object>> unique = new ArrayList<>(new LinkedHashSet<>(conditions));
        if (unique.isEmpty()) {
            attributes.remove("conditional");
            attributes.remove("conditions");
            return;
        }
        attributes.put("conditional", true);
        attributes.put("conditions", List.copyOf(unique));
        if (unique.size() == 1) {
            Map<String, Object> condition = unique.get(0);
            attributes.put("discriminatorEndpoint", condition.get("discriminator"));
            attributes.put("discriminatorOperator", condition.get("operator"));
            attributes.put("discriminatorValue", condition.get("value"));
        }
    }

    private static Map<String, Object> condition(Map<?, ?> source) {
        Object discriminator = source.get("discriminator");
        Object operator = source.get("operator");
        Object value = source.get("value");
        if (discriminator == null || operator == null || value == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("discriminator", discriminator);
        result.put("operator", operator);
        result.put("value", value);
        return Map.copyOf(result);
    }

    private static Map<String, Object> legacyCondition(Map<String, Object> attributes) {
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("discriminator", attributes.get("discriminatorEndpoint"));
        legacy.put("operator", attributes.get("discriminatorOperator"));
        legacy.put("value", attributes.get("discriminatorValue"));
        return legacy.values().stream().anyMatch(java.util.Objects::isNull) ? Map.of() : Map.copyOf(legacy);
    }

    private static String conditionIdentity(Map<String, Object> condition) {
        return encoded(condition.get("discriminator")) + "|"
                + encoded(condition.get("operator")) + "|"
                + encoded(condition.get("value"));
    }

    private static String encoded(Object value) {
        String text = text(value);
        return text.length() + "#" + text;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
