package com.relationdetector.semantic.graph;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.SemanticFactIds;
import com.relationdetector.semantic.event.SemanticEventCandidate;
import com.relationdetector.semantic.event.SemanticEventExtractor;
import com.relationdetector.semantic.reader.EndpointRef;
import com.relationdetector.semantic.reader.ScanBundle;

/** Builds a deterministic evidence graph from normalized scan records. */
public final class SemanticEvidenceBuilder {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private final SemanticEventExtractor eventExtractor = new SemanticEventExtractor();

    public EvidenceGraph build(ScanBundle scanBundle) {
        Map<String, EndpointRef> endpoints = new LinkedHashMap<>();
        Map<String, EvidenceReference> evidenceRefs = new LinkedHashMap<>();
        List<EvidenceGraphFact> facts = new ArrayList<>();

        int relationshipIndex = 0;
        for (JsonNode relationship : scanBundle.relationships()) {
            EndpointRef source = endpoint(relationship.path("source"));
            EndpointRef target = endpoint(relationship.path("target"));
            String id = SemanticFactIds.relationship(relationship, false, relationshipIndex++);
            List<String> refs = evidenceRefs(id, relationship, evidenceRefs);
            addEndpoint(endpoints, source);
            addEndpoint(endpoints, target);
            facts.add(new EvidenceGraphFact(id, "RelationshipFact", source.displayName() + " -> " + target.displayName(),
                    List.of(source, target), refs, confidence(relationship), relationship.deepCopy(),
                    Map.of("relationType", relationship.path("relationType").asText(""),
                            "relationSubType", relationship.path("relationSubType").asText(""))));
        }

        int lineageIndex = 0;
        for (JsonNode lineage : scanBundle.dataLineages()) {
            List<EndpointRef> factEndpoints = new ArrayList<>();
            for (String sourceName : SemanticFactIds.sources(lineage)) {
                EndpointRef endpoint = endpoint(sourceName);
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
            String id = SemanticFactIds.lineage(lineage, false, lineageIndex++);
            facts.add(new EvidenceGraphFact(id, "LineageFact", sources + " -> " + target.displayName(),
                    factEndpoints, evidenceRefs(id, lineage, evidenceRefs), confidence(lineage), lineage.deepCopy(),
                    Map.of("flowKind", lineage.path("flowKind").asText(""),
                            "transformType", lineage.path("transformType").asText(""))));
        }

        int namingIndex = 0;
        for (JsonNode naming : scanBundle.namingEvidence()) {
            EndpointRef source = endpoint(naming.path("source"));
            EndpointRef target = endpoint(naming.path("target"));
            addEndpoint(endpoints, source);
            addEndpoint(endpoints, target);
            String id = SemanticFactIds.naming(naming, namingIndex++);
            facts.add(new EvidenceGraphFact(id, "NamingEvidenceFact", source.displayName() + " -> " + target.displayName(),
                    List.of(source, target), evidenceRefs(id, naming, evidenceRefs), confidence(naming), naming.deepCopy(),
                    Map.of("rule", naming.path("rule").asText(""),
                            "directionHint", naming.path("directionHint").asBoolean(false))));
        }

        int derivedRelationshipIndex = 0;
        for (JsonNode derived : scanBundle.derivedRelationships()) {
            addDerivedFact("DerivedRelationshipFact", derived, derivedRelationshipIndex++, endpoints, evidenceRefs, facts);
        }
        int derivedLineageIndex = 0;
        for (JsonNode derived : scanBundle.derivedDataLineages()) {
            addDerivedFact("DerivedLineageFact", derived, derivedLineageIndex++, endpoints, evidenceRefs, facts);
        }

        for (SemanticEventCandidate event : eventExtractor.extract(scanBundle)) {
            addEventFact(event, endpoints, evidenceRefs, facts);
        }

        for (int i = 0; i < scanBundle.diagnostics().size(); i++) {
            JsonNode diagnostic = scanBundle.diagnostics().get(i);
            String id = SemanticFactIds.diagnostic(diagnostic, i);
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

    private void addEventFact(
            SemanticEventCandidate event,
            Map<String, EndpointRef> endpoints,
            Map<String, EvidenceReference> evidenceRefs,
            List<EvidenceGraphFact> facts
    ) {
        List<EndpointRef> factEndpoints = new ArrayList<>();
        for (String endpointName : event.inputEndpoints()) {
            EndpointRef endpoint = endpoint(endpointName);
            addEndpoint(endpoints, endpoint);
            factEndpoints.add(endpoint);
        }
        for (String endpointName : event.outputEndpoints()) {
            EndpointRef endpoint = endpoint(endpointName);
            addEndpoint(endpoints, endpoint);
            factEndpoints.add(endpoint);
        }
        facts.add(new EvidenceGraphFact(event.id(), "SemanticEventCandidate",
                event.readableNameHint().isBlank()
                        ? (event.sourceObject().isBlank() ? event.eventKind() : event.sourceObject())
                        : event.readableNameHint(),
                factEndpoints, event.evidenceRefs(), event.confidence(), eventPayload(event),
                Map.of("eventKind", event.eventKind(),
                        "sourceType", event.sourceType(),
                        "sourceObjectType", event.sourceObjectType(),
                        "sourceObject", event.sourceObject(),
                        "readableNameHint", event.readableNameHint(),
                        "businessActionHint", event.businessActionHint(),
                        "eventNameBasis", event.eventNameBasis(),
                        "inputEndpointCount", event.inputEndpoints().size())));
    }

    private void addDerivedFact(
            String type,
            JsonNode derived,
            int index,
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
        String id = "DerivedRelationshipFact".equals(type)
                ? SemanticFactIds.relationship(derived, true, index)
                : SemanticFactIds.lineage(derived, true, index);
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

    private ObjectNode eventPayload(SemanticEventCandidate event) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("id", event.id());
        payload.put("eventKind", event.eventKind());
        payload.put("sourceType", event.sourceType());
        payload.put("sourceObject", event.sourceObject());
        payload.put("sourceObjectType", event.sourceObjectType());
        payload.put("sourceObjectName", event.sourceObjectName());
        payload.put("sourceFile", event.sourceFile());
        payload.put("sourceStatementId", event.sourceStatementId());
        payload.put("readableNameHint", event.readableNameHint());
        payload.put("businessActionHint", event.businessActionHint());
        payload.put("eventNameBasis", event.eventNameBasis());
        payload.set("operationKinds", strings(event.operationKinds()));
        payload.set("inputEndpoints", strings(event.inputEndpoints()));
        payload.set("outputEndpoints", strings(event.outputEndpoints()));
        payload.set("lineageRefs", strings(event.lineageRefs()));
        payload.set("supportingDerivedLineageRefs", strings(event.supportingDerivedLineageRefs()));
        payload.set("relationshipRefs", strings(event.relationshipRefs()));
        payload.set("evidenceRefs", strings(event.evidenceRefs()));
        payload.put("confidence", event.confidence());
        return payload;
    }

    private ArrayNode strings(List<String> values) {
        ArrayNode result = JSON.createArrayNode();
        for (String value : values) {
            result.add(value);
        }
        return result;
    }

    private EndpointRef endpoint(JsonNode node) {
        return EndpointRef.fromJson(node);
    }

    private EndpointRef endpoint(String value) {
        int index = value == null ? -1 : value.lastIndexOf('.');
        if (index < 0) {
            return new EndpointRef(value == null || value.isBlank() ? "unknown" : value, null);
        }
        return new EndpointRef(value.substring(0, index), value.substring(index + 1));
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
