package com.relationdetector.semantic.reader;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.SemanticFactIds;
import com.relationdetector.semantic.model.PhysicalEndpointRef;

/**
 * CN: 在 ScanResult reader 边界将已验证 JSON section 一次性转换为 typed relationship、lineage、naming 与
 * diagnostic facts；下游 semantic graph 只消费 typed model，本类不推断缺失 endpoint、业务语义或 evidence。
 * EN: Converts validated ScanResult JSON sections exactly once into typed relationship, lineage, naming, and
 * diagnostic facts at the reader boundary. Downstream semantic graphs consume only typed models; this factory never
 * infers missing endpoints, business meaning, or evidence.
 */
final class ScanFactFactory {
    private ScanFactFactory() {
    }

    static List<ScanRelationshipFact> relationships(List<?> values, boolean derived) {
        List<ScanRelationshipFact> result = new ArrayList<>();
        for (Object value : safe(values)) {
            JsonNode node = document(value, "relationship");
            result.add(new ScanRelationshipFact(
                    SemanticFactIds.relationship(node, derived),
                    PhysicalEndpointJsonReader.read(node.path("source")),
                    PhysicalEndpointJsonReader.read(node.path("target")),
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
        for (Object value : safe(values)) {
            JsonNode node = document(value, "lineage");
            result.add(new ScanLineageFact(
                    SemanticFactIds.lineage(node, derived),
                    endpoints(node),
                    PhysicalEndpointJsonReader.read(node.path("target")),
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
        for (Object value : safe(values)) {
            JsonNode node = document(value, "naming evidence");
            result.add(new ScanNamingEvidenceFact(
                    SemanticFactIds.naming(node),
                    PhysicalEndpointJsonReader.read(node.path("source")),
                    PhysicalEndpointJsonReader.read(node.path("target")),
                    node.path("rule").asText(""),
                    node.path("directionHint").asBoolean(false),
                    node.path("confidence").asDouble(0.0d),
                    node));
        }
        return List.copyOf(result);
    }

    static List<ScanDiagnosticFact> diagnostics(List<?> values) {
        List<ScanDiagnosticFact> result = new ArrayList<>();
        for (Object value : safe(values)) {
            JsonNode node = document(value, "diagnostic");
            result.add(new ScanDiagnosticFact(
                    SemanticFactIds.diagnostic(node),
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

    private static List<PhysicalEndpointRef> endpoints(JsonNode lineage) {
        List<PhysicalEndpointRef> result = new ArrayList<>();
        JsonNode sources = lineage.path("sources");
        if (sources.isArray()) {
            sources.forEach(source -> result.add(PhysicalEndpointJsonReader.read(source)));
        }
        if (result.isEmpty() && lineage.path("source").isObject()) {
            result.add(PhysicalEndpointJsonReader.read(lineage.path("source")));
        }
        return List.copyOf(result);
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
