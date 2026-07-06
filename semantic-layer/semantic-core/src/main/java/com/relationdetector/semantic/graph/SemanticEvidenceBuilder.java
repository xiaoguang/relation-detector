package com.relationdetector.semantic.graph;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.semantic.reader.EndpointRef;
import com.relationdetector.semantic.reader.ScanBundle;

/** Builds a deterministic evidence graph from normalized scan records. */
public final class SemanticEvidenceBuilder {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    public EvidenceGraph build(ScanBundle scanBundle) {
        Map<String, EndpointRef> endpoints = new LinkedHashMap<>();
        Map<String, EvidenceReference> evidenceRefs = new LinkedHashMap<>();
        List<EvidenceGraphFact> facts = new ArrayList<>();

        for (JsonNode relationship : scanBundle.relationships()) {
            EndpointRef source = endpoint(relationship.path("source"));
            EndpointRef target = endpoint(relationship.path("target"));
            String id = "relationship:" + source.displayName() + "->" + target.displayName()
                    + ":" + relationship.path("relationType").asText("UNKNOWN")
                    + ":" + relationship.path("relationSubType").asText("UNKNOWN");
            List<String> refs = evidenceRefs(id, relationship, evidenceRefs);
            addEndpoint(endpoints, source);
            addEndpoint(endpoints, target);
            facts.add(new EvidenceGraphFact(id, "RelationshipFact", source.displayName() + " -> " + target.displayName(),
                    List.of(source, target), refs, confidence(relationship), relationship.deepCopy(),
                    Map.of("relationType", relationship.path("relationType").asText(""),
                            "relationSubType", relationship.path("relationSubType").asText(""))));
        }

        for (JsonNode lineage : scanBundle.dataLineages()) {
            List<EndpointRef> factEndpoints = new ArrayList<>();
            for (JsonNode source : lineage.path("sources")) {
                EndpointRef endpoint = endpoint(source);
                addEndpoint(endpoints, endpoint);
                factEndpoints.add(endpoint);
            }
            EndpointRef target = endpoint(lineage.path("target"));
            addEndpoint(endpoints, target);
            factEndpoints.add(target);
            String sources = factEndpoints.subList(0, factEndpoints.size() - 1).stream()
                    .map(EndpointRef::displayName)
                    .reduce((left, right) -> left + "+" + right)
                    .orElse("unknown");
            String id = "lineage:" + sources + "->" + target.displayName()
                    + ":" + lineage.path("flowKind").asText("UNKNOWN")
                    + ":" + lineage.path("transformType").asText("UNKNOWN");
            facts.add(new EvidenceGraphFact(id, "LineageFact", sources + " -> " + target.displayName(),
                    factEndpoints, evidenceRefs(id, lineage, evidenceRefs), confidence(lineage), lineage.deepCopy(),
                    Map.of("flowKind", lineage.path("flowKind").asText(""),
                            "transformType", lineage.path("transformType").asText(""))));
        }

        for (JsonNode naming : scanBundle.namingEvidence()) {
            EndpointRef source = endpoint(naming.path("source"));
            EndpointRef target = endpoint(naming.path("target"));
            addEndpoint(endpoints, source);
            addEndpoint(endpoints, target);
            String id = "naming:" + naming.path("id").asText(
                    source.displayName() + "->" + target.displayName() + ":" + naming.path("rule").asText("UNKNOWN"));
            facts.add(new EvidenceGraphFact(id, "NamingEvidenceFact", source.displayName() + " -> " + target.displayName(),
                    List.of(source, target), evidenceRefs(id, naming, evidenceRefs), confidence(naming), naming.deepCopy(),
                    Map.of("rule", naming.path("rule").asText(""),
                            "directionHint", naming.path("directionHint").asBoolean(false))));
        }

        for (JsonNode derived : scanBundle.derivedRelationships()) {
            addDerivedFact("DerivedRelationshipFact", derived, endpoints, evidenceRefs, facts);
        }
        for (JsonNode derived : scanBundle.derivedDataLineages()) {
            addDerivedFact("DerivedLineageFact", derived, endpoints, evidenceRefs, facts);
        }
        for (int i = 0; i < scanBundle.diagnostics().size(); i++) {
            JsonNode diagnostic = scanBundle.diagnostics().get(i);
            String id = "diagnostic:" + i + ":" + diagnostic.path("code").asText(diagnostic.path("type").asText("UNKNOWN"));
            EvidenceReference ref = diagnosticEvidenceRef(id, diagnostic);
            evidenceRefs.putIfAbsent(ref.id(), ref);
            facts.add(new EvidenceGraphFact(id, "Diagnostic", diagnostic.path("message").asText(id),
                    List.of(), List.of(ref.id()), BigDecimal.ZERO, diagnostic.deepCopy(),
                    Map.of("code", diagnostic.path("code").asText(""),
                            "severity", diagnostic.path("severity").asText(""))));
        }

        return new EvidenceGraph(scanBundle, List.copyOf(endpoints.values()), facts,
                List.copyOf(evidenceRefs.values()), scanBundle.diagnostics(), scanBundle.summary());
    }

    private void addDerivedFact(
            String type,
            JsonNode derived,
            Map<String, EndpointRef> endpoints,
            Map<String, EvidenceReference> evidenceRefs,
            List<EvidenceGraphFact> facts
    ) {
        EndpointRef source = endpoint(derived.path("source"));
        EndpointRef target = endpoint(derived.path("target"));
        addEndpoint(endpoints, source);
        addEndpoint(endpoints, target);
        List<EndpointRef> factEndpoints = new ArrayList<>();
        for (JsonNode pathNode : derived.path("path")) {
            EndpointRef step = endpoint(pathNode);
            addEndpoint(endpoints, step);
            factEndpoints.add(step);
        }
        if (factEndpoints.isEmpty()) {
            factEndpoints.add(source);
            factEndpoints.add(target);
        }
        String id = ("DerivedRelationshipFact".equals(type) ? "derived-relationship:" : "derived-lineage:")
                + source.displayName() + "->" + target.displayName() + ":" + derived.path("pathLength").asText("0");
        facts.add(new EvidenceGraphFact(id, type, source.displayName() + " -> " + target.displayName(),
                factEndpoints, evidenceRefs(id, derived, evidenceRefs), confidence(derived), derived.deepCopy(),
                Map.of("pathLength", derived.path("pathLength").asInt(0),
                        "kind", derived.path("kind").asText(""))));
    }

    private List<String> evidenceRefs(
            String ownerId,
            JsonNode record,
            Map<String, EvidenceReference> evidenceRefs
    ) {
        JsonNode sourceArray = record.path("rawEvidence").isArray() && !record.path("rawEvidence").isEmpty()
                ? record.path("rawEvidence")
                : record.path("evidence");
        List<String> result = new ArrayList<>();
        if (sourceArray.isArray()) {
            int index = 0;
            for (JsonNode evidence : sourceArray) {
                EvidenceReference ref = evidenceRef(ownerId, index++, evidence);
                evidenceRefs.putIfAbsent(ref.id(), ref);
                result.add(ref.id());
            }
        }
        return result;
    }

    private EvidenceReference evidenceRef(String ownerId, int index, JsonNode evidence) {
        String id = "evidence:" + ownerId + ":" + index;
        String evidenceType = evidence.path("type").asText(evidence.path("transformType").asText("UNKNOWN"));
        return new EvidenceReference(
                id,
                evidenceType,
                evidence.path("sourceType").asText("UNKNOWN"),
                evidence.path("score").decimalValue(),
                evidence.path("source").asText(""),
                evidence.path("detail").asText(""),
                attributes(evidence.path("attributes"))
        );
    }

    private EvidenceReference diagnosticEvidenceRef(String ownerId, JsonNode diagnostic) {
        return new EvidenceReference(
                "evidence:" + ownerId,
                diagnostic.path("type").asText("DIAGNOSTIC"),
                "DIAGNOSTIC",
                BigDecimal.ZERO,
                diagnostic.path("source").asText(""),
                diagnostic.path("message").asText(""),
                attributes(diagnostic.path("attributes"))
        );
    }

    private EndpointRef endpoint(JsonNode node) {
        return EndpointRef.fromJson(node);
    }

    private void addEndpoint(Map<String, EndpointRef> endpoints, EndpointRef endpoint) {
        endpoints.putIfAbsent(endpoint.displayName(), endpoint);
        endpoints.putIfAbsent(endpoint.table(), new EndpointRef(endpoint.table(), null));
    }

    private BigDecimal confidence(JsonNode node) {
        return node.path("confidence").isNumber() ? node.path("confidence").decimalValue() : BigDecimal.ZERO;
    }

    private Map<String, Object> attributes(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        return JSON.convertValue(node, MAP_TYPE);
    }
}
