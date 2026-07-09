package com.relationdetector.semantic.extract;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.SemanticFactIds;
import com.relationdetector.semantic.event.SemanticEventCandidate;
import com.relationdetector.semantic.event.SemanticEventExtractor;
import com.relationdetector.semantic.reader.ScanBundle;

/** Builds compact, evidence-grounded input for LLM semantic extraction. */
public final class SemanticExtractionBundleBuilder {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final SemanticEventExtractor eventExtractor = new SemanticEventExtractor();
    private final ReviewItemCandidateGenerator reviewItemCandidateGenerator = new ReviewItemCandidateGenerator();
    private final TripletCandidateBuilder tripletCandidateBuilder = new TripletCandidateBuilder();

    public ObjectNode build(ScanBundle bundle, String focus, int maxRelationships, int maxLineage, int maxNamingEvidence) {
        if (bundle == null) {
            throw new IllegalArgumentException("scan bundle is required");
        }
        String normalizedFocus = focus == null ? "" : focus.trim();
        Set<String> focusTables = focusTables(bundle, normalizedFocus);
        ObjectNode root = JSON.createObjectNode();
        ObjectNode database = root.putObject("database");
        database.put("type", bundle.databaseType());
        database.put("schema", bundle.schema());
        root.put("focus", normalizedFocus);
        List<SemanticEventCandidate> events = eventExtractor.extract(bundle);
        root.set("inputFiles", strings(bundle.inputFiles().stream().map(Object::toString).toList()));
        root.set("sources", strings(bundle.sources()));
        root.set("tables", strings(new ArrayList<>(focusTables)));
        root.set("relationships", relationships(bundle.relationships(), focusTables, maxRelationships, false));
        root.set("lineage", lineages(bundle.dataLineages(), normalizedFocus, focusTables, maxLineage, false));
        root.set("eventCandidates", eventCandidates(events, focusTables, maxLineage));
        root.set("derivedRelationships", relationships(bundle.derivedRelationships(), focusTables, maxRelationships, true));
        root.set("derivedLineage", lineages(bundle.derivedDataLineages(), normalizedFocus, focusTables, maxLineage, true));
        root.set("namingEvidence", namingEvidence(bundle.namingEvidence(), focusTables, maxNamingEvidence));
        root.set("reviewItemCandidates", reviewItemCandidateGenerator.build(bundle, 0));
        root.set("tripletCandidates", tripletCandidateBuilder.build(bundle, events, focusTables,
                maxRelationships, maxLineage, maxNamingEvidence));
        root.set("diagnostics", diagnostics(bundle.diagnostics(), 0));
        root.putObject("instructions")
                .put("allOutputsMustUseEvidenceRefs", true)
                .put("llmCannotCreateDatabaseFacts", true)
                .put("businessApprovedIsForbidden", true)
                .put("markUncertainItemsReviewNeeded", true);
        return root;
    }

    private Set<String> focusTables(ScanBundle bundle, String focus) {
        Set<String> tables = new LinkedHashSet<>();
        if (!focus.isBlank()) {
            for (JsonNode lineage : bundle.dataLineages()) {
                if (lineageEvidenceMatches(lineage, focus)) {
                    lineage.path("sources").forEach(source -> addTable(tables, source));
                    addTable(tables, lineage.path("target"));
                }
            }
            if (!tables.isEmpty()) {
                return tables;
            }
        }
        for (JsonNode relationship : bundle.relationships()) {
            addTable(tables, relationship.path("source"));
            addTable(tables, relationship.path("target"));
        }
        for (JsonNode relationship : bundle.derivedRelationships()) {
            addTable(tables, relationship.path("source"));
            addTable(tables, relationship.path("target"));
        }
        for (JsonNode lineage : bundle.dataLineages()) {
            lineage.path("sources").forEach(source -> addTable(tables, source));
            addTable(tables, lineage.path("target"));
        }
        for (JsonNode lineage : bundle.derivedDataLineages()) {
            SemanticFactIds.sources(lineage).forEach(source -> {
                String table = tableOf(source);
                if (!table.isBlank()) {
                    tables.add(table);
                }
            });
            addTable(tables, lineage.path("target"));
        }
        return tables;
    }

    private ArrayNode relationships(List<JsonNode> relationships, Set<String> focusTables, int limit, boolean derived) {
        ArrayNode result = JSON.createArrayNode();
        int index = 0;
        for (JsonNode relationship : relationships) {
            String source = endpoint(relationship.path("source"));
            String target = endpoint(relationship.path("target"));
            if (!touches(source, target, focusTables)) {
                index++;
                continue;
            }
            ObjectNode item = result.addObject();
            item.put("id", SemanticFactIds.relationship(relationship, derived, index));
            item.put("source", source);
            item.put("target", target);
            item.put("type", relationship.path("relationType").asText(relationship.path("kind").asText("")));
            item.put("subType", relationship.path("relationSubType").asText(""));
            item.put("confidence", relationship.path("confidence").asDouble(0.0));
            item.set("evidenceRefs", evidenceRefs(relationship));
            item.set("evidenceTypes", evidenceTypes(relationship.path("evidence")));
            if (limited(limit) && result.size() >= limit) {
                break;
            }
            index++;
        }
        return result;
    }

    private ArrayNode lineages(List<JsonNode> lineages, String focus, Set<String> focusTables, int limit, boolean derived) {
        ArrayNode result = JSON.createArrayNode();
        int index = 0;
        for (JsonNode lineage : lineages) {
            List<String> sources = new ArrayList<>(SemanticFactIds.sources(lineage));
            String target = endpoint(lineage.path("target"));
            boolean focusMatch = !focus.isBlank() && lineageEvidenceMatches(lineage, focus);
            boolean tableMatch = sources.stream().anyMatch(source -> tableTouches(source, focusTables))
                    || tableTouches(target, focusTables);
            if (!focusMatch && !tableMatch) {
                index++;
                continue;
            }
            ObjectNode item = result.addObject();
            item.put("id", SemanticFactIds.lineage(lineage, derived, index));
            item.set("sources", strings(sources));
            item.put("target", target);
            item.put("flowKind", lineage.path("flowKind").asText(""));
            item.put("transformType", lineage.path("transformType").asText(""));
            item.put("confidence", lineage.path("confidence").asDouble(0.0));
            item.set("evidenceRefs", evidenceRefs(lineage));
            item.set("evidenceSources", evidenceSources(lineage.path("evidence")));
            if (limited(limit) && result.size() >= limit) {
                break;
            }
            index++;
        }
        return result;
    }

    private ArrayNode eventCandidates(List<SemanticEventCandidate> events, Set<String> focusTables, int limit) {
        ArrayNode result = JSON.createArrayNode();
        for (SemanticEventCandidate event : events) {
            boolean touches = event.inputEndpoints().stream().anyMatch(endpoint -> tableTouches(endpoint, focusTables))
                    || event.outputEndpoints().stream().anyMatch(endpoint -> tableTouches(endpoint, focusTables));
            if (!touches) {
                continue;
            }
            ObjectNode item = result.addObject();
            item.put("id", event.id());
            item.put("eventKind", event.eventKind());
            item.put("sourceType", event.sourceType());
            item.put("sourceObject", event.sourceObject());
            item.put("sourceObjectType", event.sourceObjectType());
            item.put("sourceObjectName", event.sourceObjectName());
            item.put("sourceFile", event.sourceFile());
            item.put("sourceStatementId", event.sourceStatementId());
            item.put("readableNameHint", event.readableNameHint());
            item.put("businessActionHint", event.businessActionHint());
            item.put("eventNameBasis", event.eventNameBasis());
            item.set("operationKinds", strings(event.operationKinds()));
            item.set("inputEndpoints", strings(event.inputEndpoints()));
            item.set("outputEndpoints", strings(event.outputEndpoints()));
            item.set("lineageRefs", strings(event.lineageRefs()));
            item.set("supportingDerivedLineageRefs", strings(event.supportingDerivedLineageRefs()));
            item.set("relationshipRefs", strings(event.relationshipRefs()));
            item.set("evidenceRefs", strings(event.evidenceRefs()));
            item.set("attributes", JSON.valueToTree(event.attributes()));
            item.put("confidence", event.confidence().doubleValue());
            if (limited(limit) && result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private ArrayNode namingEvidence(List<JsonNode> namingEvidence, Set<String> focusTables, int limit) {
        ArrayNode result = JSON.createArrayNode();
        int index = 0;
        for (JsonNode naming : namingEvidence) {
            String source = endpoint(naming.path("source"));
            String target = endpoint(naming.path("target"));
            if (!touches(source, target, focusTables)) {
                index++;
                continue;
            }
            ObjectNode item = result.addObject();
            item.put("id", SemanticFactIds.naming(naming, index));
            item.put("source", source);
            item.put("target", target);
            item.put("rule", naming.path("rule").asText(""));
            item.put("directionHint", naming.path("directionHint").asBoolean(false));
            item.set("evidenceRefs", evidenceRefs(naming));
            if (limited(limit) && result.size() >= limit) {
                break;
            }
            index++;
        }
        return result;
    }

    private ArrayNode diagnostics(List<JsonNode> diagnostics, int limit) {
        ArrayNode result = JSON.createArrayNode();
        int index = 0;
        for (JsonNode diagnostic : diagnostics) {
            ObjectNode item = result.addObject();
            item.put("id", SemanticFactIds.diagnostic(diagnostic, index));
            item.put("code", diagnostic.path("code").asText(""));
            item.put("severity", diagnostic.path("severity").asText(""));
            item.put("message", diagnostic.path("message").asText(""));
            item.put("source", diagnostic.path("source").asText(""));
            if (limited(limit) && result.size() >= limit) {
                break;
            }
            index++;
        }
        return result;
    }

    private boolean lineageEvidenceMatches(JsonNode lineage, String focus) {
        String lowerFocus = focus.toLowerCase();
        for (JsonNode evidence : lineage.path("evidence")) {
            if (evidence.path("source").asText("").toLowerCase().contains(lowerFocus)) {
                return true;
            }
        }
        for (JsonNode evidence : lineage.path("rawEvidence")) {
            if (evidence.path("source").asText("").toLowerCase().contains(lowerFocus)) {
                return true;
            }
        }
        return false;
    }

    private boolean limited(int limit) {
        return limit > 0;
    }

    private boolean touches(String source, String target, Set<String> focusTables) {
        return tableTouches(source, focusTables) || tableTouches(target, focusTables);
    }

    private boolean tableTouches(String endpoint, Set<String> focusTables) {
        String table = tableOf(endpoint);
        return !table.isBlank() && focusTables.contains(table);
    }

    private void addTable(Set<String> tables, JsonNode endpoint) {
        String table = table(endpoint);
        if (!table.isBlank()) {
            tables.add(table);
        }
    }

    private String table(JsonNode endpoint) {
        if (endpoint == null || !endpoint.isObject()) {
            return "";
        }
        String schema = endpoint.path("schema").asText("");
        String table = endpoint.path("table").asText("");
        if (table.isBlank()) {
            return "";
        }
        return schema.isBlank() ? table : schema + "." + table;
    }

    private String endpoint(JsonNode endpoint) {
        if (endpoint == null || !endpoint.isObject()) {
            return "";
        }
        String table = table(endpoint);
        String column = endpoint.path("column").asText("");
        if (table.isBlank()) {
            return "";
        }
        return column.isBlank() ? table : table + "." + column;
    }

    private String tableOf(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "";
        }
        int lastDot = endpoint.lastIndexOf('.');
        return lastDot < 0 ? endpoint : endpoint.substring(0, lastDot);
    }

    private ArrayNode evidenceRefs(JsonNode node) {
        ArrayNode result = JSON.createArrayNode();
        for (JsonNode evidence : node.path("rawEvidence")) {
            addEvidenceRef(result, evidence);
        }
        if (result.isEmpty()) {
            for (JsonNode evidence : node.path("evidence")) {
                addEvidenceRef(result, evidence);
            }
        }
        return result;
    }

    private void addEvidenceRef(ArrayNode result, JsonNode evidence) {
        String source = evidence.path("source").asText("");
        String detail = evidence.path("detail").asText("");
        String type = evidence.path("type").asText(evidence.path("transformType").asText(""));
        if (!source.isBlank() || !detail.isBlank() || !type.isBlank()) {
            ObjectNode ref = result.addObject();
            ref.put("source", source);
            ref.put("type", type);
            ref.put("detail", detail);
        }
    }

    private ArrayNode evidenceTypes(JsonNode evidenceArray) {
        ArrayNode result = JSON.createArrayNode();
        for (JsonNode evidence : evidenceArray) {
            String type = evidence.path("type").asText("");
            if (!type.isBlank()) {
                result.add(type);
            }
        }
        return result;
    }

    private ArrayNode evidenceSources(JsonNode evidenceArray) {
        ArrayNode result = JSON.createArrayNode();
        for (JsonNode evidence : evidenceArray) {
            String source = evidence.path("source").asText("");
            if (!source.isBlank()) {
                result.add(source);
            }
        }
        return result;
    }

    private ArrayNode strings(List<String> values) {
        ArrayNode result = JSON.createArrayNode();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value);
            }
        }
        return result;
    }
}
