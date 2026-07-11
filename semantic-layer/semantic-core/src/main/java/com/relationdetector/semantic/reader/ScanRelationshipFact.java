package com.relationdetector.semantic.reader;

import com.fasterxml.jackson.databind.JsonNode;

/** Typed direct or derived relationship fact. */
public record ScanRelationshipFact(
        String id,
        String source,
        String target,
        String relationType,
        String relationSubType,
        double confidence,
        boolean derived,
        JsonNode document
) implements ScanFact {
    public ScanRelationshipFact {
        document = immutableDocument(document);
    }

    private static JsonNode immutableDocument(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException("relationship fact must be a JSON object");
        }
        return value.deepCopy();
    }
}
