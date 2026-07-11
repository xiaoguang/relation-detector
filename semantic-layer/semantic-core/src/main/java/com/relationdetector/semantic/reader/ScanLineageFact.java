package com.relationdetector.semantic.reader;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/** Typed direct or derived lineage fact. */
public record ScanLineageFact(
        String id,
        List<String> sources,
        String target,
        String flowKind,
        String transformType,
        double confidence,
        boolean derived,
        JsonNode document
) implements ScanFact {
    public ScanLineageFact {
        sources = List.copyOf(sources == null ? List.of() : sources);
        if (document == null || !document.isObject()) {
            throw new IllegalArgumentException("lineage fact must be a JSON object");
        }
        document = document.deepCopy();
    }
}
