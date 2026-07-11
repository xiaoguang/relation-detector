package com.relationdetector.semantic.reader;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.SemanticFactIds;

/** Converts raw JSON exactly once at the scan-result reader boundary. */
final class ScanFactFactory {
    private ScanFactFactory() {
    }

    static List<ScanRelationshipFact> relationships(List<?> values, boolean derived) {
        List<ScanRelationshipFact> result = new ArrayList<>();
        int index = 0;
        for (Object value : safe(values)) {
            JsonNode node = document(value, "relationship");
            result.add(new ScanRelationshipFact(
                    SemanticFactIds.relationship(node, derived, index++),
                    SemanticFactIds.endpoint(node.path("source")),
                    SemanticFactIds.endpoint(node.path("target")),
                    node.path("relationType").asText(node.path("kind").asText("")),
                    node.path("relationSubType").asText(""),
                    node.path("confidence").asDouble(0.0d),
                    derived,
                    node));
        }
        return List.copyOf(result);
    }

    static List<ScanLineageFact> lineages(List<?> values, boolean derived) {
        List<ScanLineageFact> result = new ArrayList<>();
        int index = 0;
        for (Object value : safe(values)) {
            JsonNode node = document(value, "lineage");
            result.add(new ScanLineageFact(
                    SemanticFactIds.lineage(node, derived, index++),
                    SemanticFactIds.sources(node),
                    SemanticFactIds.endpoint(node.path("target")),
                    node.path("flowKind").asText(""),
                    node.path("transformType").asText(""),
                    node.path("confidence").asDouble(0.0d),
                    derived,
                    node));
        }
        return List.copyOf(result);
    }

    static List<ScanNamingEvidenceFact> naming(List<?> values) {
        List<ScanNamingEvidenceFact> result = new ArrayList<>();
        int index = 0;
        for (Object value : safe(values)) {
            JsonNode node = document(value, "naming evidence");
            result.add(new ScanNamingEvidenceFact(
                    SemanticFactIds.naming(node, index++),
                    SemanticFactIds.endpoint(node.path("source")),
                    SemanticFactIds.endpoint(node.path("target")),
                    node.path("rule").asText(""),
                    node.path("directionHint").asBoolean(false),
                    node.path("confidence").asDouble(0.0d),
                    node));
        }
        return List.copyOf(result);
    }

    static List<ScanDiagnosticFact> diagnostics(List<?> values) {
        List<ScanDiagnosticFact> result = new ArrayList<>();
        int index = 0;
        for (Object value : safe(values)) {
            JsonNode node = document(value, "diagnostic");
            result.add(new ScanDiagnosticFact(
                    SemanticFactIds.diagnostic(node, index++),
                    node.path("code").asText(""),
                    node.path("severity").asText(""),
                    node.path("message").asText(""),
                    node.path("source").asText(""),
                    node.path("line").asInt(0),
                    node));
        }
        return List.copyOf(result);
    }

    private static List<?> safe(List<?> values) {
        return values == null ? List.of() : values;
    }

    private static JsonNode document(Object value, String kind) {
        if (value instanceof ScanFact fact) {
            return fact.document();
        }
        if (value instanceof JsonNode node && node.isObject()) {
            return node;
        }
        throw new IllegalArgumentException(kind + " entry must be a JSON object or typed scan fact");
    }
}
