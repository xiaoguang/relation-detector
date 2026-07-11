package com.relationdetector.semantic.reader;

import com.fasterxml.jackson.databind.JsonNode;

/** Typed parser or scan diagnostic. */
public record ScanDiagnosticFact(
        String id,
        String code,
        String severity,
        String message,
        String source,
        int line,
        JsonNode document
) implements ScanFact {
    public ScanDiagnosticFact {
        if (document == null || !document.isObject()) {
            throw new IllegalArgumentException("diagnostic fact must be a JSON object");
        }
        document = document.deepCopy();
    }
}
