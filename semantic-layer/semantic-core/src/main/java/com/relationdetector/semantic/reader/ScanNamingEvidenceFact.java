package com.relationdetector.semantic.reader;

import com.fasterxml.jackson.databind.JsonNode;

/** Typed naming evidence fact. */
public record ScanNamingEvidenceFact(
        String id,
        String source,
        String target,
        String rule,
        boolean directionHint,
        double confidence,
        JsonNode document
) implements ScanFact {
    public ScanNamingEvidenceFact {
        if (document == null || !document.isObject()) {
            throw new IllegalArgumentException("naming evidence fact must be a JSON object");
        }
        document = document.deepCopy();
    }
}
