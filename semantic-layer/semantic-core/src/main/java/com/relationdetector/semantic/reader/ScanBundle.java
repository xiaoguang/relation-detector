package com.relationdetector.semantic.reader;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/** Normalized relation-detector scan result records for semantic-layer input. */
public record ScanBundle(
        String databaseType,
        String schema,
        String generatedAt,
        List<String> sources,
        List<Path> inputFiles,
        Map<String, Integer> summary,
        List<JsonNode> relationships,
        List<JsonNode> dataLineages,
        List<JsonNode> derivedRelationships,
        List<JsonNode> derivedDataLineages,
        List<JsonNode> namingEvidence,
        List<JsonNode> diagnostics
) {
    public ScanBundle {
        if (databaseType == null || databaseType.isBlank()) {
            throw new IllegalArgumentException("database type is required");
        }
        if (schema == null) {
            schema = "";
        }
        if (generatedAt == null || generatedAt.isBlank()) {
            generatedAt = "";
        }
        sources = List.copyOf(sources == null ? List.of() : sources);
        inputFiles = List.copyOf(inputFiles == null ? List.of() : inputFiles);
        summary = Map.copyOf(summary == null ? Map.of() : summary);
        relationships = List.copyOf(relationships == null ? List.of() : relationships);
        dataLineages = List.copyOf(dataLineages == null ? List.of() : dataLineages);
        derivedRelationships = List.copyOf(derivedRelationships == null ? List.of() : derivedRelationships);
        derivedDataLineages = List.copyOf(derivedDataLineages == null ? List.of() : derivedDataLineages);
        namingEvidence = List.copyOf(namingEvidence == null ? List.of() : namingEvidence);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
