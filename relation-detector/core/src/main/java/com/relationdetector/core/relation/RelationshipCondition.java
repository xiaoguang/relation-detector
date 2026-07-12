package com.relationdetector.core.relation;

import java.util.LinkedHashMap;
import java.util.Map;

import com.relationdetector.contracts.model.Endpoint;

/** Canonical column-to-literal guard attached to a direct structural relationship. */
record RelationshipCondition(Endpoint discriminator, String operator, String literalValue) {
    RelationshipCondition {
        if (discriminator == null || !discriminator.isColumnLevel()) {
            throw new IllegalArgumentException("column-level discriminator is required");
        }
        operator = operator == null ? "" : operator;
        literalValue = literalValue == null ? "" : literalValue;
    }

    Map<String, Object> attributes() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("discriminator", discriminator.normalizedKey());
        result.put("operator", operator);
        result.put("value", literalValue);
        return Map.copyOf(result);
    }
}
