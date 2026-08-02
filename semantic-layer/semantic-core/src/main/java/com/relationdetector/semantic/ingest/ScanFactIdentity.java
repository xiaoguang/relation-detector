package com.relationdetector.semantic.ingest;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/** Computes the stable semantic identity for one validated scan fact. */
final class ScanFactIdentity {
    private ScanFactIdentity() {
    }

    static String of(SemanticInputStore.Section section, JsonNode item) {
        return switch (section) {
            case RELATIONSHIPS -> ScanFactFactory.relationships(List.of(item), false).get(0).id();
            case DATA_LINEAGES -> ScanFactFactory.lineages(List.of(item), false).get(0).id();
            case DERIVED_RELATIONSHIPS -> ScanFactFactory.relationships(List.of(item), true).get(0).id();
            case DERIVED_DATA_LINEAGES -> ScanFactFactory.lineages(List.of(item), true).get(0).id();
            case NAMING_EVIDENCE -> ScanFactFactory.naming(List.of(item)).get(0).id();
            case WARNINGS -> ScanFactFactory.diagnostics(List.of(item)).get(0).id();
            default -> throw new ScanResultContractException(
                    "metadata section cannot produce semantic fact id");
        };
    }
}
